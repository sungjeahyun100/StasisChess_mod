package nand.modid.chess.dsl.chessembly;

import java.util.*;

/**
 * Interpreter — Chessembly 토큰 리스트를 실행하여 Activation 목록을 생성한다.
 * Rust의 Interpreter::execute()를 1:1 포팅.
 *
 * 핵심 동작:
 * - 앵커(Anchor): 식 연쇄 내 누적 오프셋. 세미콜론(;)에서 초기화.
 * - 종료(Termination): 일반 식이 false → 현재 체인 스킵.
 * - 스코프({ }): 앵커 저장/복원 + 실패 격리.
 * - 제어식(while, jmp, jne, not, label): false여도 체인 종료하지 않음.
 */
public final class Interpreter {

    private List<AST.Token> tokens = new ArrayList<>();
    private boolean debug;

    public Interpreter() {}

    public void setDebug(boolean enabled) {
        this.debug = enabled;
    }

    /** 스크립트 파싱 (토큰화) */
    public void parse(String input) {
        tokens = Parser.parse(input);
    }

    /** 행마법 계산 실행 */
    public List<AST.Activation> execute(BuiltinOps.BoardState board) {
        List<AST.Activation> activations = new ArrayList<>();
        int pc = 0;

        // ── 라벨 사전 처리: 체인 인덱스 → (라벨명 → pc) ──
        Map<Integer, Map<String, Integer>> labels = new HashMap<>();
        {
            int chainIdx = 0;
            int i = 0;
            while (i < tokens.size()) {
                AST.Token t = tokens.get(i);
                i++;
                if (t.type == AST.TokenType.SEMICOLON) {
                    chainIdx++;
                } else if (t.type == AST.TokenType.LABEL) {
                    labels.computeIfAbsent(chainIdx, k -> new HashMap<>())
                          .put(t.strArg, i);
                }
            }
        }

        int chainIndex = 0;
        int numOpenBrace = 0;

        // 앵커 (기물 위치 기준 누적 오프셋)
        int anchorX = 0, anchorY = 0;

        // 실행 상태
        boolean lastValue = true;

        // 펜딩 액션 태그
        List<AST.ActionTag> pendingTags = new ArrayList<>();

        // do...while 시작 위치
        int doIndex = -1;

        // { } 스코프 스택: [anchorX, anchorY]
        Deque<int[]> scopeStack = new ArrayDeque<>();

        // 마지막 take 위치 (jump용)
        int[] lastTakePos = null; // [dx, dy]

        while (pc < tokens.size()) {
            AST.Token token = tokens.get(pc);

            if (debug) {
                System.out.printf("  [PC:%d] %s | Anchor(%d,%d) | last=%b%n",
                        pc, token, anchorX, anchorY, lastValue);
            }

            pc++;

            // ── 종료 규칙: 일반 식이 false면 체인 스킵 ──
            boolean isExempt;
            switch (token.type) {
                case WHILE:
                case JMP:
                case JNE:
                case NOT:
                case LABEL:
                case SEMICOLON:
                case CLOSE_BRACE:
                    isExempt = true;
                    break;
                default:
                    isExempt = false;
                    break;
            }

            if (!lastValue && !isExempt) {
                // 현재 체인(;) 또는 스코프(}) 까지 스킵
                while (pc < tokens.size()) {
                    AST.TokenType tt = tokens.get(pc).type;
                    if (tt == AST.TokenType.SEMICOLON) {
                        anchorX = 0; anchorY = 0;
                        pendingTags.clear();
                        doIndex = -1;
                        lastTakePos = null;
                        pc++;
                        chainIndex++;
                        break;
                    } else if (tt == AST.TokenType.CLOSE_BRACE) {
                        if (numOpenBrace > 0) {
                            numOpenBrace--;
                            pc++;
                            continue;
                        }
                        if (!scopeStack.isEmpty()) {
                            int[] saved = scopeStack.pop();
                            anchorX = saved[0]; anchorY = saved[1];
                        }
                        pc++;
                        break;
                    } else if (tt == AST.TokenType.OPEN_BRACE) {
                        numOpenBrace++;
                        pc++;
                    } else {
                        pc++;
                    }
                }
                lastValue = true;
                continue;
            }

            switch (token.type) {

                // ── 구조 ──────────────────────────────
                case SEMICOLON: {
                    anchorX = 0; anchorY = 0;
                    lastValue = true;
                    pendingTags.clear();
                    doIndex = -1;
                    lastTakePos = null;
                    chainIndex++;
                    break;
                }

                case OPEN_BRACE: {
                    scopeStack.push(new int[]{ anchorX, anchorY });
                    lastValue = true;
                    break;
                }

                case CLOSE_BRACE: {
                    if (!scopeStack.isEmpty()) {
                        int[] saved = scopeStack.pop();
                        anchorX = saved[0]; anchorY = saved[1];
                    }
                    lastValue = true;
                    break;
                }

                // ── 행마식: TAKE_MOVE ─────────────────
                case TAKE_MOVE: {
                    int tx = board.pieceX + anchorX + token.dx;
                    int ty = board.pieceY + anchorY + token.dy;
                    if (!board.inBounds(tx, ty) || board.hasFriendly(tx, ty)) {
                        lastValue = false;
                    } else if (board.hasEnemy(tx, ty)) {
                        addActivation(activations, anchorX + token.dx, anchorY + token.dy,
                                AST.MoveType.TAKE_MOVE, pendingTags, null);
                        anchorX += token.dx; anchorY += token.dy;
                        lastValue = false; // 적 잡으면 체인 중단 신호
                    } else {
                        addActivation(activations, anchorX + token.dx, anchorY + token.dy,
                                AST.MoveType.TAKE_MOVE, pendingTags, null);
                        anchorX += token.dx; anchorY += token.dy;
                        lastValue = true;
                    }
                    break;
                }

                // ── MOVE ──────────────────────────────
                case MOVE: {
                    int tx = board.pieceX + anchorX + token.dx;
                    int ty = board.pieceY + anchorY + token.dy;
                    if (board.isEmpty(tx, ty)) {
                        addActivation(activations, anchorX + token.dx, anchorY + token.dy,
                                AST.MoveType.MOVE, pendingTags, null);
                        anchorX += token.dx; anchorY += token.dy;
                        lastValue = true;
                    } else {
                        lastValue = false;
                    }
                    break;
                }

                // ── TAKE ──────────────────────────────
                case TAKE: {
                    int tx = board.pieceX + anchorX + token.dx;
                    int ty = board.pieceY + anchorY + token.dy;
                    if (board.hasEnemy(tx, ty)) {
                        lastTakePos = new int[]{ anchorX + token.dx, anchorY + token.dy };
                        addActivation(activations, anchorX + token.dx, anchorY + token.dy,
                                AST.MoveType.TAKE, pendingTags, null);
                        anchorX += token.dx; anchorY += token.dy;
                        lastValue = true;
                    } else {
                        // 적이 없으면 앵커만 이동
                        if (board.inBounds(tx, ty) && !board.hasFriendly(tx, ty)) {
                            anchorX += token.dx; anchorY += token.dy;
                            lastValue = true;
                        } else {
                            lastValue = false;
                        }
                    }
                    break;
                }

                // ── JUMP ──────────────────────────────
                case JUMP: {
                    // 앞의 take가 있으면 pop
                    if (!activations.isEmpty()
                            && activations.get(activations.size() - 1).moveType == AST.MoveType.TAKE) {
                        activations.remove(activations.size() - 1);
                    }
                    if (lastTakePos != null) {
                        int tx = board.pieceX + anchorX + token.dx;
                        int ty = board.pieceY + anchorY + token.dy;
                        if (board.isEmpty(tx, ty)) {
                            addActivation(activations, anchorX + token.dx, anchorY + token.dy,
                                    AST.MoveType.JUMP, pendingTags, lastTakePos);
                            anchorX += token.dx; anchorY += token.dy;
                            lastValue = true;
                        } else {
                            lastValue = false;
                        }
                    } else {
                        lastValue = false;
                    }
                    break;
                }

                // ── CATCH ─────────────────────────────
                case CATCH: {
                    int tx = board.pieceX + anchorX + token.dx;
                    int ty = board.pieceY + anchorY + token.dy;
                    if (board.hasEnemy(tx, ty)) {
                        addActivation(activations, anchorX + token.dx, anchorY + token.dy,
                                AST.MoveType.CATCH, pendingTags, null);
                        lastValue = true;
                    } else {
                        lastValue = false;
                    }
                    // catch는 앵커를 이동하지 않음
                    break;
                }

                // ── SHIFT ─────────────────────────────
                case SHIFT: {
                    int tx = board.pieceX + anchorX + token.dx;
                    int ty = board.pieceY + anchorY + token.dy;
                    if (board.inBounds(tx, ty) && !board.isEmpty(tx, ty)) {
                        addActivation(activations, anchorX + token.dx, anchorY + token.dy,
                                AST.MoveType.SHIFT, pendingTags, null);
                        anchorX += token.dx; anchorY += token.dy;
                        lastValue = true;
                    } else {
                        lastValue = false;
                    }
                    break;
                }

                // ── ANCHOR ────────────────────────────
                case ANCHOR: {
                    anchorX += token.dx;
                    anchorY += token.dy;
                    lastValue = true;
                    break;
                }

                // ── 조건식 ────────────────────────────
                case OBSERVE: {
                    int tx = board.pieceX + anchorX + token.dx;
                    int ty = board.pieceY + anchorY + token.dy;
                    lastValue = board.isEmpty(tx, ty);
                    break;
                }

                case PEEK: {
                    int tx = board.pieceX + anchorX + token.dx;
                    int ty = board.pieceY + anchorY + token.dy;
                    if (!board.inBounds(tx, ty)) {
                        lastValue = false;
                    } else {
                        // peek: 비어있든 기물이 있든 앵커 이동, 비어있으면 true
                        anchorX += token.dx;
                        anchorY += token.dy;
                        lastValue = board.isEmpty(tx, ty);
                    }
                    break;
                }

                case ENEMY: {
                    int tx = board.pieceX + anchorX + token.dx;
                    int ty = board.pieceY + anchorY + token.dy;
                    lastValue = board.hasEnemy(tx, ty);
                    break;
                }

                case FRIENDLY: {
                    int tx = board.pieceX + anchorX + token.dx;
                    int ty = board.pieceY + anchorY + token.dy;
                    lastValue = board.hasFriendly(tx, ty);
                    break;
                }

                case PIECE_ON: {
                    int tx = board.pieceX + anchorX + token.dx;
                    int ty = board.pieceY + anchorY + token.dy;
                    lastValue = board.hasPiece(tx, ty, token.strArg);
                    break;
                }

                case DANGER: {
                    int tx = board.pieceX + anchorX + token.dx;
                    int ty = board.pieceY + anchorY + token.dy;
                    lastValue = board.isDanger(tx, ty);
                    break;
                }

                case CHECK:
                    lastValue = board.inCheck;
                    break;

                case BOUND: {
                    int tx = board.pieceX + anchorX + token.dx;
                    int ty = board.pieceY + anchorY + token.dy;
                    lastValue = !board.inBounds(tx, ty);
                    break;
                }

                case EDGE: {
                    int tx = board.pieceX + anchorX + token.dx;
                    int ty = board.pieceY + anchorY + token.dy;
                    lastValue = tx < 0 || tx >= board.boardWidth || ty < 0 || ty >= board.boardHeight;
                    break;
                }

                case EDGE_TOP: {
                    int ty = board.pieceY + anchorY + token.dy;
                    lastValue = ty >= board.boardHeight;
                    break;
                }

                case EDGE_BOTTOM: {
                    int ty = board.pieceY + anchorY + token.dy;
                    lastValue = ty < 0;
                    break;
                }

                case EDGE_LEFT: {
                    int tx = board.pieceX + anchorX + token.dx;
                    lastValue = tx < 0;
                    break;
                }

                case EDGE_RIGHT: {
                    int tx = board.pieceX + anchorX + token.dx;
                    lastValue = tx >= board.boardWidth;
                    break;
                }

                case CORNER: {
                    int tx = board.pieceX + anchorX + token.dx;
                    int ty = board.pieceY + anchorY + token.dy;
                    lastValue = (tx < 0 || tx >= board.boardWidth)
                             && (ty < 0 || ty >= board.boardHeight);
                    break;
                }

                case CORNER_TOP_LEFT: {
                    int tx = board.pieceX + anchorX + token.dx;
                    int ty = board.pieceY + anchorY + token.dy;
                    lastValue = tx < 0 && ty >= board.boardHeight;
                    break;
                }

                case CORNER_TOP_RIGHT: {
                    int tx = board.pieceX + anchorX + token.dx;
                    int ty = board.pieceY + anchorY + token.dy;
                    lastValue = tx >= board.boardWidth && ty >= board.boardHeight;
                    break;
                }

                case CORNER_BOTTOM_LEFT: {
                    int tx = board.pieceX + anchorX + token.dx;
                    int ty = board.pieceY + anchorY + token.dy;
                    lastValue = tx < 0 && ty < 0;
                    break;
                }

                case CORNER_BOTTOM_RIGHT: {
                    int tx = board.pieceX + anchorX + token.dx;
                    int ty = board.pieceY + anchorY + token.dy;
                    lastValue = tx >= board.boardWidth && ty < 0;
                    break;
                }

                // ── 상태 ──────────────────────────────
                case PIECE:
                    lastValue = board.pieceName.equals(token.strArg);
                    break;

                case IF_STATE: {
                    int actual = board.getState(token.strArg);
                    lastValue = actual == token.intArg;
                    break;
                }

                case SET_STATE: {
                    pendingTags.add(new AST.ActionTag(
                            AST.ActionTagType.SET_STATE, token.strArg, token.intArg, null));
                    lastValue = true;
                    break;
                }

                case SET_STATE_RESET: {
                    if (!pendingTags.isEmpty()) pendingTags.remove(pendingTags.size() - 1);
                    lastValue = true;
                    break;
                }

                case TRANSITION: {
                    pendingTags.add(new AST.ActionTag(
                            AST.ActionTagType.TRANSITION, "", 0, token.strArg));
                    lastValue = true;
                    break;
                }

                // ── 제어 ──────────────────────────────
                case REPEAT: {
                    int n = token.intArg;
                    if (lastValue && n > 0) {
                        int target = pc > n ? pc - n - 1 : 0;
                        pc = target;
                    }
                    // repeat은 lastValue 그대로 전달
                    break;
                }

                case DO: {
                    if (lastValue) {
                        doIndex = pc;
                    }
                    break;
                }

                case WHILE: {
                    if (lastValue && doIndex >= 0) {
                        pc = doIndex;
                    }
                    lastValue = true;
                    break;
                }

                case JMP: {
                    if (lastValue) {
                        Map<String, Integer> chainLabels = labels.get(chainIndex);
                        if (chainLabels != null && chainLabels.containsKey(token.strArg)) {
                            pc = chainLabels.get(token.strArg);
                        }
                    }
                    lastValue = true;
                    break;
                }

                case JNE: {
                    if (!lastValue) {
                        Map<String, Integer> chainLabels = labels.get(chainIndex);
                        if (chainLabels != null && chainLabels.containsKey(token.strArg)) {
                            pc = chainLabels.get(token.strArg);
                        }
                    }
                    lastValue = true;
                    break;
                }

                case LABEL:
                    /* 투명 — lastValue 유지 */
                    break;

                case NOT:
                    lastValue = !lastValue;
                    break;

                case END:
                    lastValue = false;
                    break;

                default:
                    break;
            }
        }

        return activations;
    }

    // ── 유틸 ──────────────────────────────────────────

    private void addActivation(List<AST.Activation> list,
                               int dx, int dy, AST.MoveType moveType,
                               List<AST.ActionTag> tags, int[] catchTo) {
        if (debug) {
            System.out.printf("    → Activation(%d, %d) %s%n", dx, dy, moveType);
        }
        list.add(new AST.Activation(dx, dy, moveType, tags, catchTo));
    }
}
