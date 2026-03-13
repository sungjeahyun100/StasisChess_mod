package nand.modid.chess.core;

import nand.modid.chess.dsl.chessembly.*;
import nand.modid.chess.movegen.MoveGenerator;

import java.util.*;

/**
 * GameState — 전체 게임 상태 관리.
 * Rust의 GameState 구조체를 1:1 포팅.
 *
 * 포함: 보드, 포켓, 기물 맵, 턴 관리, 이동/착수/캡처/계승/위장/스턴,
 *       행마법 계산(chessembly 연동), 승리 조건 확인.
 */
public final class GameState {

    // ── 필드 ──────────────────────────────────────────

    private final Board board = new Board();
    private final Map<Integer, List<Piece.PieceSpec>> pockets = new HashMap<>();
    private final Map<String, Piece.PieceData> pieces = new HashMap<>();
    private int turn;
    private final Map<String, Integer> globalState = new HashMap<>();
    private String activePiece;   // 현재 턴에 이동 중인 기물 ID
    private boolean actionTaken;  // 이번 턴에 행동 여부
    private final List<Move.Action> turnActions = new ArrayList<>();
    private boolean debugMode;
    private java.util.function.Consumer<String> debugLogger = null;
    private int nextPieceId;

    // ── 생성자 ────────────────────────────────────────

    public GameState(int startingPlayer) {
        this.turn = startingPlayer;
        setupInitialKings();
    }

    public static GameState newDefault() {
        return new GameState(0);
    }

    // ── 초기화 ────────────────────────────────────────

    private void setupInitialKings() {
        // 백 킹 (e1)
        Piece.PieceData wk = createPiece(Piece.PieceKind.KING, 0);
        pieces.put(wk.id, wk);
        placeKing(wk.id, new Move.Square(4, 0));

        // 흑 킹 (e8)
        Piece.PieceData bk = createPiece(Piece.PieceKind.KING, 1);
        pieces.put(bk.id, bk);
        placeKing(bk.id, new Move.Square(4, 7));
    }

    private void placeKing(String pieceId, Move.Square square) {
        Piece.PieceData p = pieces.get(pieceId);
        if (p == null) return;
        p.pos = square;
        p.isRoyal = true;
        p.stun = 0;
        p.moveStack = 3; // 킹 초기 이동 스택
        board.put(square, pieceId);
    }

    private Piece.PieceData createPiece(Piece.PieceKind kind, int owner) {
        String id = "piece_" + nextPieceId++;
        return new Piece.PieceData(id, kind, owner);
    }

    /**
     * 중립기물(gray piece)을 보드에 직접 배치한다.
     * 중립기물은 포켓에 넣을 수 없으므로 이 메서드를 통해서만 생성된다.
     *
     * <p>배치할 {@code kind}는 반드시 {@link Piece.PieceKind#isNeutral()} == true 이어야 한다.
     * 이동 가능 여부는 Chessembly 스크립트가 결정한다.</p>
     *
     * @param kind    중립기물로 선언된 PieceKind (kind.isNeutral() == true 이어야 함)
     * @param target  배치할 좌표
     * @return 생성된 기물 ID
     * @throws IllegalArgumentException kind 가 중립기물로 선언되지 않은 경우
     * @throws IllegalStateException    해당 칸에 이미 기물이 있는 경우
     */
    public String placeNeutralPiece(Piece.PieceKind kind, Move.Square target) {
        if (!kind.isNeutral()) {
            throw new IllegalArgumentException(
                kind.scriptName() + " 은(는) 중립기물로 선언되지 않았습니다. " +
                "PieceKind 정의 시 isNeutral=true 로 선언하세요.");
        }
        if (board.contains(target)) {
            throw new IllegalStateException("해당 칸에 이미 기물이 있습니다");
        }
        // owner = -1 : 중립 소유권 없음
        String id = "piece_" + nextPieceId++;
        Piece.PieceData piece = new Piece.PieceData(id, kind, -1);
        piece.spec = new Piece.PieceSpec(kind);
        piece.pos = target;
        piece.stun = 0;
        piece.moveStack = RuleSet.initialMoveStack(piece.score());

        pieces.put(id, piece);
        board.put(target, id);
        return id;
    }

    // ── 포켓 ──────────────────────────────────────────

    /** 초기 포지션 설정 (킹 + 기본 포켓) */
    public void setupInitialPosition() {
        List<Piece.PieceSpec> wPocket = Arrays.asList(
            new Piece.PieceSpec(Piece.PieceKind.QUEEN),
            new Piece.PieceSpec(Piece.PieceKind.ROOK),
            new Piece.PieceSpec(Piece.PieceKind.ROOK),
            new Piece.PieceSpec(Piece.PieceKind.BISHOP),
            new Piece.PieceSpec(Piece.PieceKind.BISHOP),
            new Piece.PieceSpec(Piece.PieceKind.KNIGHT),
            new Piece.PieceSpec(Piece.PieceKind.KNIGHT),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN)
        );
        setupPocket(0, new ArrayList<>(wPocket));

        List<Piece.PieceSpec> bPocket = Arrays.asList(
            new Piece.PieceSpec(Piece.PieceKind.QUEEN),
            new Piece.PieceSpec(Piece.PieceKind.ROOK),
            new Piece.PieceSpec(Piece.PieceKind.ROOK),
            new Piece.PieceSpec(Piece.PieceKind.BISHOP),
            new Piece.PieceSpec(Piece.PieceKind.BISHOP),
            new Piece.PieceSpec(Piece.PieceKind.KNIGHT),
            new Piece.PieceSpec(Piece.PieceKind.KNIGHT),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN)
        );
        setupPocket(1, new ArrayList<>(bPocket));
    }

    /** 포켓 설정 (점수 합계 검증) */
    public void setupPocket(int player, List<Piece.PieceSpec> specs) {
        int total = specs.stream().mapToInt(Piece.PieceSpec::score).sum();
        if (total > RuleSet.MAX_POCKET_SCORE) {
            throw new IllegalArgumentException(
                "포켓 점수 " + total + "점이 제한 " + RuleSet.MAX_POCKET_SCORE + "점을 초과합니다");
        }
        pockets.put(player, new ArrayList<>(specs));
    }

    /** 포켓 설정 (점수 제한 없음, 실험용) */
    public void setupPocketUnchecked(int player, List<Piece.PieceSpec> specs) {
        pockets.put(player, new ArrayList<>(specs));
    }

    /** 실험용 포켓 설정 */
    public void setupExperimentalPocket() {
        List<Piece.PieceSpec> pocket = Arrays.asList(
            new Piece.PieceSpec(Piece.PieceKind.AMAZON),
            new Piece.PieceSpec(Piece.PieceKind.GRASSHOPPER),
            new Piece.PieceSpec(Piece.PieceKind.KNIGHTRIDER),
            new Piece.PieceSpec(Piece.PieceKind.ARCHBISHOP),
            new Piece.PieceSpec(Piece.PieceKind.DABBABA),
            new Piece.PieceSpec(Piece.PieceKind.ALFIL),
            new Piece.PieceSpec(Piece.PieceKind.FERZ),
            new Piece.PieceSpec(Piece.PieceKind.CENTAUR),
            new Piece.PieceSpec(Piece.PieceKind.CAMEL),
            new Piece.PieceSpec(Piece.PieceKind.TEMPEST_ROOK),
            new Piece.PieceSpec(Piece.PieceKind.CANNON),
            new Piece.PieceSpec(Piece.PieceKind.BOUNCING_BISHOP),
            new Piece.PieceSpec(Piece.PieceKind.EXPERIMENT)
        );
        setupPocketUnchecked(0, new ArrayList<>(pocket));
        setupPocketUnchecked(1, new ArrayList<>(pocket));
    }

    /** 실험용 기물 배치 설정 (모든 기물을 포켓에 넣음) */
    public void setupExperimentalPosition() {
        // 보드는 킹만 남겨두고 (생성자에서 이미 배치됨) 모든 기물을 포켓에 구성합니다.
        List<Piece.PieceSpec> megaPocket = new ArrayList<>(Arrays.asList(
            // 기본 기물들
            new Piece.PieceSpec(Piece.PieceKind.QUEEN),
            new Piece.PieceSpec(Piece.PieceKind.ROOK),
            new Piece.PieceSpec(Piece.PieceKind.ROOK),
            new Piece.PieceSpec(Piece.PieceKind.BISHOP),
            new Piece.PieceSpec(Piece.PieceKind.BISHOP),
            new Piece.PieceSpec(Piece.PieceKind.KNIGHT),
            new Piece.PieceSpec(Piece.PieceKind.KNIGHT),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),
            new Piece.PieceSpec(Piece.PieceKind.PAWN),

            // 변형 기물들
            new Piece.PieceSpec(Piece.PieceKind.AMAZON),
            new Piece.PieceSpec(Piece.PieceKind.GRASSHOPPER),
            new Piece.PieceSpec(Piece.PieceKind.KNIGHTRIDER),
            new Piece.PieceSpec(Piece.PieceKind.ARCHBISHOP),
            new Piece.PieceSpec(Piece.PieceKind.DABBABA),
            new Piece.PieceSpec(Piece.PieceKind.ALFIL),
            new Piece.PieceSpec(Piece.PieceKind.FERZ),
            new Piece.PieceSpec(Piece.PieceKind.CENTAUR),
            new Piece.PieceSpec(Piece.PieceKind.CAMEL),
            new Piece.PieceSpec(Piece.PieceKind.TEMPEST_ROOK),
            new Piece.PieceSpec(Piece.PieceKind.CANNON),
            new Piece.PieceSpec(Piece.PieceKind.BOUNCING_BISHOP),
            new Piece.PieceSpec(Piece.PieceKind.EXPERIMENT)
        ));

        setupPocketUnchecked(0, new ArrayList<>(megaPocket));
        setupPocketUnchecked(1, new ArrayList<>(megaPocket));
    }

    /** 포켓에 기물 추가 */
    public void addPieceToPocket(int player, Piece.PieceKind kind) {
        List<Piece.PieceSpec> pocket = pockets.computeIfAbsent(player, k -> new ArrayList<>());
        pocket.add(new Piece.PieceSpec(kind));
    }

    /** 포켓에서 기물 제거 */
    public boolean removePieceFromPocket(int player, Piece.PieceKind kind) {
        List<Piece.PieceSpec> pocket = pockets.get(player);
        if (pocket == null) return false;
        for (int i = 0; i < pocket.size(); i++) {
            if (pocket.get(i).kind == kind) {
                pocket.remove(i);
                return true;
            }
        }
        return false;
    }

    // ── 착수 ──────────────────────────────────────────

    /** 착수 가능 여부 확인 */
    public void canPlace(int player, Piece.PieceKind kind, Move.Square target) {
        if (turn != player) throw new IllegalStateException("자신의 턴이 아닙니다");
        if (actionTaken) throw new IllegalStateException("이번 턴에 이미 행동했습니다");
        if (activePiece != null) throw new IllegalStateException("이동 중인 기물이 있습니다");
        if (board.contains(target)) throw new IllegalStateException("해당 칸에 이미 기물이 있습니다");

        boolean isWhite = player == 0;
        if (kind.isPromotionSquare(target, isWhite)) {
            throw new IllegalStateException("프로모션 기물은 프로모션 칸에 착수할 수 없습니다");
        }

        List<Piece.PieceSpec> pocket = pockets.get(player);
        if (pocket == null || pocket.stream().noneMatch(s -> s.kind == kind)) {
            throw new IllegalStateException("포켓에 해당 기물이 없습니다");
        }
    }

    /** 착수 실행 */
    public String placePiece(int player, Piece.PieceKind kind, Move.Square target) {
        canPlace(player, kind, target);

        // 포켓에서 제거
        List<Piece.PieceSpec> pocket = pockets.get(player);
        if (pocket != null) {
            for (int i = 0; i < pocket.size(); i++) {
                if (pocket.get(i).kind == kind) {
                    pocket.remove(i);
                    break;
                }
            }
        }

        // 기물 생성 및 배치
        Piece.PieceData piece = createPiece(kind, player);
        piece.stun = RuleSet.calculatePlacementStun(piece, target);
        piece.moveStack = RuleSet.initialMoveStack(piece.score());
        piece.pos = target;

        pieces.put(piece.id, piece);
        board.put(target, piece.id);
        actionTaken = true;
        turnActions.add(Move.Action.place(piece.id, target));

        return piece.id;
    }

    // ── 이동 검증 ─────────────────────────────────────

    /** 이동 가능 여부 확인 */
    public void canMovePiece(int player, String pieceId, Move.Square from, Move.Square to,
                              AST.MoveType moveType) {
        if (turn != player) throw new IllegalStateException("자신의 턴이 아닙니다");
        if (actionTaken) throw new IllegalStateException("이번 턴에 이미 다른 행동을 했습니다");

        if (activePiece != null && !activePiece.equals(pieceId)) {
            throw new IllegalStateException("다른 기물이 이동 중입니다");
        }

        Piece.PieceData piece = pieces.get(pieceId);
        if (piece == null) throw new IllegalStateException("기물을 찾을 수 없습니다");
        // 중립기물은 어느 플레이어도 사용 가능하다
        if (!piece.isNeutral() && piece.owner != player)
            throw new IllegalStateException("자신의 기물이 아닙니다");

        if (!piece.canMove()) {
            if (piece.stun > 0)
                throw new IllegalStateException("스턴 상태입니다 (스턴: " + piece.stun + ")");
            throw new IllegalStateException("이동 스택이 없습니다");
        }

        boolean targetEmpty = !board.contains(to);
        boolean hasEnemy = false, hasFriendly = false;
        String targetPid = board.get(to);
        if (targetPid != null) {
            Piece.PieceData tp = pieces.get(targetPid);
            if (tp != null) {
                if (tp.isNeutral()) {
                    // 중립기물은 어느 플레이어에게도 아군으로 취급된다 (포획 불가, Shift 가능)
                    hasEnemy = false;
                    hasFriendly = true;
                } else {
                    hasEnemy = tp.owner != player;
                    hasFriendly = tp.owner == player;
                }
            }
        }

        switch (moveType) {
            case MOVE:
                if (!targetEmpty) throw new IllegalStateException("Move는 빈 칸으로만 이동할 수 있습니다");
                break;
            case TAKE:
                if (!hasEnemy) throw new IllegalStateException("Take는 적이 있는 칸으로만 이동할 수 있습니다");
                break;
            case CATCH:
                if (!hasEnemy) throw new IllegalStateException("Catch는 적이 있는 칸만 선택할 수 있습니다");
                break;
            case SHIFT:
                if (targetEmpty) throw new IllegalStateException("Shift는 다른 기물이 있는 칸만 선택할 수 있습니다");
                break;
            case TAKE_MOVE:
                if (hasFriendly) throw new IllegalStateException("아군 기물이 있는 칸으로 이동할 수 없습니다");
                break;
            case JUMP:
                if (!targetEmpty) throw new IllegalStateException("Jump는 빈 칸으로만 이동할 수 있습니다");
                break;
        }
    }

    // ── 이동 실행 ─────────────────────────────────────

    /** LegalMove 기반 이동 실행 */
    public String movePieceByLegalMove(Move.LegalMove mv) {
        Move.Square from = mv.from;
        Move.Square to = mv.to;

        String pieceId = board.get(from);
        if (pieceId == null) throw new IllegalStateException("출발 위치에 기물이 없습니다");

        Piece.PieceData piece = pieces.get(pieceId);
        if (piece == null) throw new IllegalStateException("기물을 찾을 수 없습니다");

        canMovePiece(piece.owner, pieceId, from, to, mv.moveType);

        String capturedId = null;

        switch (mv.moveType) {
            case MOVE: {
                board.remove(from);
                board.put(to, pieceId);
                piece.pos = to;
                piece.moveStack--;
                break;
            }
            case TAKE:
            case TAKE_MOVE: {
                String victimId = board.get(to);
                if (victimId != null) {
                    capturedId = victimId;
                    capture(pieceId, victimId);
                }
                board.remove(from);
                board.put(to, pieceId);
                piece.pos = to;
                if (capturedId == null) piece.moveStack--;
                break;
            }
            case CATCH: {
                String victimId = board.get(to);
                if (victimId == null) throw new IllegalStateException("Catch 대상이 없습니다");
                capturedId = victimId;
                capture(pieceId, victimId);
                break;
            }
            case SHIFT: {
                String targetPid = board.get(to);
                if (targetPid == null) throw new IllegalStateException("Shift 대상이 없습니다");
                board.remove(from);
                board.remove(to);
                board.put(from, targetPid);
                board.put(to, pieceId);
                piece.pos = to;
                piece.moveStack--;
                Piece.PieceData tp = pieces.get(targetPid);
                if (tp != null) tp.pos = from;
                break;
            }
            case JUMP: {
                board.remove(from);
                board.put(to, pieceId);
                piece.pos = to;
                piece.moveStack--;

                if (mv.catchTo != null && mv.catchTo.isValid()) {
                    String victimId = board.get(mv.catchTo);
                    if (victimId != null) {
                        capturedId = victimId;
                        capture(pieceId, victimId);
                    }
                }
                break;
            }
        }

        activePiece = pieceId;
        applyActionTags(pieceId, mv.tags);
        turnActions.add(Move.Action.move(pieceId, from, to));

        return capturedId;
    }

    /** 캡처 처리 (스택 이전) */
    public void capture(String attackerId, String victimId) {
        Piece.PieceData victim = pieces.get(victimId);
        if (victim == null) throw new IllegalStateException("피해자를 찾을 수 없습니다");

        Piece.PieceData attacker = pieces.get(attackerId);

        if (attacker != null) {
            attacker.moveStack = attacker.moveStack - 1 + victim.moveStack;
            attacker.stun += victim.stun;
        }

        if (victim.pos != null) board.remove(victim.pos);
        pieces.remove(victimId);
    }

    /** 액션 태그 적용 */
    private void applyActionTags(String pieceId, List<AST.ActionTag> tags) {
        if (tags == null) return;
        for (AST.ActionTag tag : tags) {
            switch (tag.tagType) {
                case TRANSITION: {
                    if (tag.pieceName != null) {
                        Piece.PieceData p = pieces.get(pieceId);
                        if (p != null) {
                            Piece.PieceKind newKind = Piece.PieceKind.fromString(tag.pieceName);
                            p.kind = newKind;
                            p.moveStack = RuleSet.initialMoveStack(newKind.score());
                        }
                    }
                    break;
                }
                case SET_STATE:
                    globalState.put(tag.key, tag.value);
                    break;
                case USEING_STACK:
                    String actor = board.get(tag.where_stack_is_comming);
                    Piece.PieceData p = pieces.get(actor);
                    p.moveStack--;
            }
        }
    }

    // ── 계승 / 위장 / 스턴 ────────────────────────────

    public void crownPiece(int player, String pieceId) {
        if (turn != player) throw new IllegalStateException("자신의 턴이 아닙니다");
        if (actionTaken || activePiece != null) throw new IllegalStateException("이번 턴에 이미 행동했습니다");

        Piece.PieceData p = pieces.get(pieceId);
        if (p == null) throw new IllegalStateException("기물을 찾을 수 없습니다");
        if (p.owner != player) throw new IllegalStateException("자신의 기물이 아닙니다");
        if (p.pos == null) throw new IllegalStateException("보드 위의 기물만 계승할 수 있습니다");

        p.isRoyal = true;
        actionTaken = true;
        turnActions.add(Move.Action.crown(pieceId));
    }

    public void disguisePiece(int player, String pieceId, Piece.PieceKind asKind) {
        if (turn != player) throw new IllegalStateException("자신의 턴이 아닙니다");
        if (actionTaken || activePiece != null) throw new IllegalStateException("이번 턴에 이미 행동했습니다");

        Piece.PieceData p = pieces.get(pieceId);
        if (p == null) throw new IllegalStateException("기물을 찾을 수 없습니다");
        if (p.owner != player) throw new IllegalStateException("자신의 기물이 아닙니다");
        if (!p.isRoyal) throw new IllegalStateException("로얄 피스만 위장할 수 있습니다");

        p.moveStack = RuleSet.initialMoveStack(asKind.score());
        p.disguise = asKind;
        actionTaken = true;
        turnActions.add(Move.Action.disguise(pieceId, asKind.name()));
    }

    public void stunPiece(String pieceId, int amount) {
        Piece.PieceData p = pieces.get(pieceId);
        if (p == null) throw new IllegalStateException("기물을 찾을 수 없습니다");

        // 중립기물은 어느 플레이어에게나 아군으로 취급 → 아군 스턴 규칙(1~3) 적용
        boolean isAlly = p.isNeutral() || (p.owner == turn);
        if (isAlly) {
            if (amount < 1 || amount > 3)
                throw new IllegalArgumentException("아군에게는 1~3 스턴만 부여할 수 있습니다");
        } else {
            if (amount != 1)
                throw new IllegalArgumentException("적에게는 1 스턴만 부여할 수 있습니다");
        }

        p.stun += amount;
        actionTaken = true;
        turnActions.add(Move.Action.stun(pieceId, amount));
    }

    // ── 프로모션 ──────────────────────────────────────

    public void promote(String pieceId, Piece.PieceKind toKind) {
        Piece.PieceData p = pieces.get(pieceId);
        if (p == null) throw new IllegalStateException("기물을 찾을 수 없습니다");
        if (!p.kind.canPromote()) throw new IllegalStateException("프로모션할 수 없는 기물입니다");
        if (!p.kind.promotionTargets().contains(toKind))
            throw new IllegalStateException("유효하지 않은 프로모션 대상입니다");
        if (p.pos == null) throw new IllegalStateException("보드 위에 없는 기물입니다");
        if (!p.kind.isPromotionSquare(p.pos, p.isWhite()))
            throw new IllegalStateException("프로모션 칸에 있지 않습니다");

        p.kind = toKind;
        // 스택은 유지 (promotion.md)
    }

    // ── 턴 ────────────────────────────────────────────

    public void endTurn() {
        // 현재 턴 기물 스턴 감소
        // 중립기물은 능동·수동 모두 매 반턴마다 스턴을 감소시킨다
        for (Piece.PieceData p : pieces.values()) {
            if (p.isNeutral() || p.owner == turn) {
                p.stun = Math.max(p.stun - 1, 0);
            }
        }

        // 다음 플레이어
        turn = 1 - turn;

        // 다음 턴 기물들 이동 스택 초기화
        // 중립기물은 매 반턴마다 스택을 초기화한다 (양측 모두 접근 가능)
        for (Piece.PieceData p : pieces.values()) {
            if (p.pos == null) continue;
            if (p.owner == turn || p.isNeutral()) {
                p.moveStack = RuleSet.initialMoveStack(p.score());
            }
        }

        activePiece = null;
        actionTaken = false;
        turnActions.clear();
    }

    // ── 승리 조건 ─────────────────────────────────────

    public Move.GameResult checkVictory() {
        boolean whiteHasRoyal = false, blackHasRoyal = false;
        for (Piece.PieceData p : pieces.values()) {
            if (p.isRoyal) {
                if (p.owner == 0) whiteHasRoyal = true;
                else blackHasRoyal = true;
            }
        }
        if (!whiteHasRoyal) return Move.GameResult.BLACK_WINS;
        if (!blackHasRoyal) return Move.GameResult.WHITE_WINS;
        return Move.GameResult.ONGOING;
    }

    // ── 행마법 계산 ───────────────────────────────────

    /** Chessembly 보드 상태 생성 */
    public BuiltinOps.BoardState toChessemblyBoard(String pieceId) {
        Piece.PieceData piece = pieces.get(pieceId);
        if (piece == null || piece.pos == null) return null;

        // 중립기물의 색 관점은 현재 턴 플레이어를 기준으로 결정된다
        boolean pieceIsWhite = piece.isNeutral() ? (turn == 0) : piece.isWhite();

        BuiltinOps.BoardState bs = new BuiltinOps.BoardState(
                RuleSet.BOARD_WIDTH, RuleSet.BOARD_HEIGHT,
                piece.pos.x, piece.pos.y,
                piece.effectiveKind().scriptName(),
                pieceIsWhite
        );

        // 보드 위 모든 기물 등록
        for (Map.Entry<Move.Square, String> entry : board.entries()) {
            Move.Square sq = entry.getKey();
            Piece.PieceData p = pieces.get(entry.getValue());
            if (p != null) {
                // 중립기물은 현재 기물(및 현재 플레이어)과 같은 색으로 등록 → 아군 취급
                boolean pIsWhite = p.isNeutral() ? pieceIsWhite : p.isWhite();
                bs.putPiece(sq.x, sq.y, p.effectiveKind().scriptName(), pIsWhite, p.stun, p.moveStack);
            }
        }

        // 전역 상태 복사
        bs.state.putAll(globalState);

        return bs;
    }

    /** 특정 기물의 합법 수 목록 계산 */
    public List<Move.LegalMove> getLegalMoves(String pieceId) {
        return MoveGenerator.generateLegalMoves(this, pieceId);
    }

    /** 특정 위치의 기물 합법 수 */
    public List<Move.LegalMove> getLegalMovesAt(Move.Square square) {
        String pid = board.get(square);
        if (pid == null) return Collections.emptyList();
        return getLegalMoves(pid);
    }

    /** 이동 유효성 확인 */
    public boolean isValidMove(String pieceId, Move.Square from, Move.Square to) {
        return getLegalMoves(pieceId).stream()
                .anyMatch(m -> m.from.equals(from) && m.to.equals(to));
    }

    public boolean isValidMoveAt(Move.Square from, Move.Square to) {
        String pid = board.get(from);
        if (pid == null) return false;
        return isValidMove(pid, from, to);
    }

    // ── 액션 적용 ─────────────────────────────────────

    public void applyAction(Move.Action action) {
        switch (action.type) {
            case PLACE: {
                Piece.PieceData p = pieces.get(action.pieceId);
                if (p != null) {
                    placePiece(turn, p.kind, action.to);
                }
                break;
            }
            case MOVE: {
                List<Move.LegalMove> moves = getLegalMovesAt(action.from);
                for (Move.LegalMove lm : moves) {
                    if (lm.to.equals(action.to)) {
                        movePieceByLegalMove(lm);
                        break;
                    }
                }
                break;
            }
            case STUN:
                stunPiece(action.pieceId, action.stunAmount);
                break;
            case CROWN: {
                Piece.PieceData p = pieces.get(action.pieceId);
                if (p != null) p.isRoyal = true;
                break;
            }
            case DISGUISE: {
                Piece.PieceData p = pieces.get(action.pieceId);
                if (p != null) p.disguise = Piece.PieceKind.fromString(action.asKind);
                break;
            }
        }
    }

    // ── 접근자 ────────────────────────────────────────

    public Board getBoard()             { return board; }
    public int getTurn()                { return turn; }
    public String getActivePiece()      { return activePiece; }
    public boolean isActionTaken()      { return actionTaken; }
    public List<Move.Action> getTurnActions() { return Collections.unmodifiableList(turnActions); }
    public boolean isDebugMode()        { return debugMode; }
    public void setDebugMode(boolean d) { debugMode = d; }
    public java.util.function.Consumer<String> getDebugLogger() { return debugLogger; }
    public void setDebugLogger(java.util.function.Consumer<String> logger) { this.debugLogger = logger; }

    public Piece.PieceData getPieceAt(Move.Square sq) {
        String id = board.get(sq);
        return id != null ? pieces.get(id) : null;
    }

    public Piece.PieceData getPiece(String id) {
        return pieces.get(id);
    }

    public Map<String, Piece.PieceData> getAllPieces() {
        return Collections.unmodifiableMap(pieces);
    }

    public List<Piece.PieceSpec> getPocket(int player) {
        return pockets.getOrDefault(player, Collections.emptyList());
    }

    public Map<String, Integer> getGlobalState() {
        return Collections.unmodifiableMap(globalState);
    }

    /** 보드 위 모든 기물 정보 반환 */
    public List<Piece.PieceData> getBoardPieces() {
        List<Piece.PieceData> result = new ArrayList<>();
        for (Piece.PieceData p : pieces.values()) {
            if (p.pos != null) result.add(p);
        }
        return result;
    }
}
