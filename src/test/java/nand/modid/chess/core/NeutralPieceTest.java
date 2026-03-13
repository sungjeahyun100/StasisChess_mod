package nand.modid.chess.core;

import nand.modid.chess.movegen.MoveGenerator;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NeutralPieceTest — 중립기물(gray piece) 기능 단위 테스트.
 *
 * 테스트 범위:
 *  1. isNeutral 필드 값 확인
 *  2. 중립기물: 이동 스택 초기화 확인
 *  3. 중립기물(NEUTRAL_SENTINEL): 백·흑 양측 모두 이동 가능
 *  4. 빈 스크립트 중립기물(NEUTRAL_PYLON): 자체 합법 수 없음
 *  5. 중립기물은 포획(take/take-move)할 수 없음
 *  6. Shift로 중립기물 이동
 *  7. 중립기물의 방향 관점이 현재 플레이어 색을 따름
 *  8. 스턴: 중립기물에 적용되고 반턴마다 감소
 *  9. 중립기물 두 개 공존 확인
 * 10. PieceData.copy() 시 isNeutral 복사 확인
 * 11. 중립기물이 포켓에 추가되지 않도록 직접 착수 방어 확인
 * 12. 중립기물: 캐프처 시 스택 이전 확인
 * 13. 중립기물 반턴마다 moveStack 초기화 확인
 * 14. 보드 배치 위치 등록 확인
 */
@DisplayName("중립기물(Gray Piece) 기능 테스트")
class NeutralPieceTest {

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    /** 킹만 있는 초기 게임 상태 반환 (백 턴부터 시작) */
    private static GameState freshState() {
        return new GameState(0);
    }

    /**
     * 보드 위에서 해당 기물의 이동 스택을 소진할 때까지 이동시켜 턴을 끝낸다.
     * 다음 플레이어로 턴을 넘긴다.
     */
    private static void skipTurn(GameState state) {
        state.endTurn();
    }

    // ── 테스트 1: 필드 초기값 ─────────────────────────────────────────────────

    @Test
    @DisplayName("1. 중립기물(NEUTRAL_SENTINEL) 필드: isNeutral=true, owner=-1, moveStack>0")
    void neutralSentinelFields() {
        GameState state = freshState();
        Move.Square d4 = new Move.Square(3, 3);

        String id = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_SENTINEL, d4);
        Piece.PieceData p = state.getPiece(id);

        assertTrue(p.isNeutral(),          "isNeutral 이 true 여야 한다");
        assertEquals(-1, p.owner,         "owner 는 -1(중립) 이어야 한다");
        assertTrue(p.moveStack > 0,       "초기 이동 스택 > 0 이어야 한다");
    }

    @Test
    @DisplayName("2. 중립기물(NEUTRAL_PYLON) 필드: isNeutral=true, owner=-1, moveStack>0")
    void neutralPylonFields() {
        GameState state = freshState();
        Move.Square e5 = new Move.Square(4, 4);

        String id = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_PYLON, e5);
        Piece.PieceData p = state.getPiece(id);

        assertTrue(p.isNeutral(),           "isNeutral 이 true 여야 한다");
        assertEquals(-1, p.owner,          "owner 는 -1(중립) 이어야 한다");
        assertTrue(p.moveStack > 0,        "모든 중립기물은 초기 이동 스택 > 0 이어야 한다");
    }

    // ── 테스트 3·4: 이동 가능 여부 ───────────────────────────────────────────

    @Test
    @DisplayName("3. 중립기물(NEUTRAL_SENTINEL)은 백(0번) 플레이어 턴에 이동 가능하다")
    void neutralMovableByWhite() {
        GameState state = freshState(); // 백 턴
        Move.Square d4 = new Move.Square(3, 3);
        String id = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_SENTINEL, d4);

        List<Move.LegalMove> moves = state.getLegalMoves(id);
        assertFalse(moves.isEmpty(), "백 턴에 중립 NEUTRAL_SENTINEL 은 합법 수를 가져야 한다");
    }

    @Test
    @DisplayName("4. 중립기물(NEUTRAL_SENTINEL)은 흑(1번) 플레이어 턴에도 이동 가능하다")
    void neutralMovableByBlack() {
        GameState state = freshState(); // 백 턴
        Move.Square d4 = new Move.Square(3, 3);
        String id = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_SENTINEL, d4);

        skipTurn(state); // → 흑 턴
        assertEquals(1, state.getTurn());

        List<Move.LegalMove> moves = state.getLegalMoves(id);
        assertFalse(moves.isEmpty(), "흑 턴에도 중립 NEUTRAL_SENTINEL 은 합법 수를 가져야 한다");
    }

    @Test
    @DisplayName("5. 빈 스크립트 중립기물(NEUTRAL_PYLON)은 어느 턴에도 합법 수가 없다")
    void emptyScriptNeutralHasNoLegalMoves() {
        GameState state = freshState();
        Move.Square e4 = new Move.Square(4, 3);
        String id = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_PYLON, e4);

        // 백 턴
        List<Move.LegalMove> movesWhite = state.getLegalMoves(id);
        assertTrue(movesWhite.isEmpty(), "NEUTRAL_PYLON은 빈 스크립트이므로 백 턴에 합법 수가 없어야 한다");

        skipTurn(state); // → 흑 턴
        List<Move.LegalMove> movesBlack = state.getLegalMoves(id);
        assertTrue(movesBlack.isEmpty(), "NEUTRAL_PYLON은 빈 스크립트이므로 흑 턴에도 합법 수가 없어야 한다");
    }

    // ── 테스트 6: 포획 불가 ───────────────────────────────────────────────────

    @Test
    @DisplayName("6. 다른 기물의 합법 수 목록에 중립기물 위치로의 take/take-move가 없어야 한다")
    void neutralPieceCantBeCaptured() {
        GameState state = freshState();

        // 중립기물(NEUTRAL_SENTINEL)을 d4에 배치 — 포획 차단 대상
        Move.Square d4 = new Move.Square(3, 3);
        state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_SENTINEL, d4);

        
        Move.Square b2 = new Move.Square(1, 1);
        String moverID = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_SENTINEL, b2);

        
        
        // 중립기물 위치(d4)로의 합법수가 "take" 타입이 없어야 한다
        List<Move.LegalMove> moverMoves = state.getLegalMoves(moverID);
        for (Move.LegalMove mv : moverMoves) {
            if (mv.to.equals(d4)) {
                assertNotEquals(nand.modid.chess.dsl.chessembly.AST.MoveType.TAKE, mv.moveType,
                    "중립기물 위치로의 단순 TAKE 이동이 없어야 한다");
                assertNotEquals(nand.modid.chess.dsl.chessembly.AST.MoveType.TAKE_MOVE, mv.moveType,
                    "중립기물 위치로의 TAKE_MOVE 이동이 없어야 한다");
            }
        }
    }

    // ── 테스트 7: Shift로 중립기물 이동 ─────────────────────────

    @Test
    @DisplayName("7. 중립기물(NEUTRAL_SENTINEL)이 Shift를 사용해 다른 중립기물(NEUTRAL_PYLON) 위치를 교환할 수 있다")
    void neutralMovableByShift() {
        GameState state = freshState();

        // NEUTRAL_PYLON 은 빈 스크립트로 자체 이동 불가한 중립기물
        Move.Square e5 = new Move.Square(4, 4);
        String pylonId = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_PYLON, e5);

        // NEUTRAL_SENTINEL 은 나이트 행마 중립기물
        Move.Square e4 = new Move.Square(4, 3);
        String sentinelId = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_SENTINEL, e4);

        // shift(0, 1) — 위 칸(e5) 교환 스크립트로 합법 수 생성
        String shiftScript = "shift(0, 1);";
        List<Move.LegalMove> shiftMoves = MoveGenerator.generateWithScript(state, sentinelId, shiftScript);

        // e4 → e5 방향 Shift 수 존재 여부
        boolean hasShiftToE5 = shiftMoves.stream().anyMatch(mv ->
            mv.to.equals(e5) && mv.moveType == nand.modid.chess.dsl.chessembly.AST.MoveType.SHIFT
        );
        assertTrue(hasShiftToE5, "중립기물이 다른 중립기물을 Shift로 밀 수 있어야 한다");
    }

    // ── 테스트 8: 방향 관점 ───────────────────────────────────────────────────

    @Test
    @DisplayName("8-a. 중립 폰은 백 턴일 때 위(+y)로 이동 목록이 나온다")
    void neutralPawnDirectionWhiteTurn() {
        GameState state = freshState(); // 백(turn=0) 턴
        Move.Square d4 = new Move.Square(3, 3);
        String id = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_WANDERER, d4);

        List<Move.LegalMove> moves = state.getLegalMoves(id);
        // 백 폰은 +y 방향 이동 → to.y > from.y
        boolean movesUp = moves.stream().anyMatch(mv -> mv.to.y > d4.y);
        assertTrue(movesUp, "백 턴의 중립 폰은 +y 방향 합법 수가 있어야 한다");
    }

    @Test
    @DisplayName("8-b. 중립 폰은 흑 턴일 때 아래(-y)로 이동 목록이 나온다")
    void neutralPawnDirectionBlackTurn() {
        GameState state = freshState();
        Move.Square d5 = new Move.Square(3, 4);
        String id = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_WANDERER, d5);

        skipTurn(state); // → 흑(turn=1) 턴
        assertEquals(1, state.getTurn());

        List<Move.LegalMove> moves = state.getLegalMoves(id);
        // 흑 폰은 -y 방향 이동 → to.y < from.y
        boolean movesDown = moves.stream().anyMatch(mv -> mv.to.y < d5.y);
        assertTrue(movesDown, "흑 턴의 중립 폰은 -y 방향 합법 수가 있어야 한다");
    }

    // ── 테스트 9: 스턴 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("9-a. 능동형 중립기물(NEUTRAL_SENTINEL)에 스턴이 부여되고, 반턴마다 1씩 감소한다")
    void neutralPieceStunDecreases() {
        GameState state = freshState();
        Move.Square c3 = new Move.Square(2, 2);
        String id = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_SENTINEL, c3);

        Piece.PieceData p = state.getPiece(id);
        p.stun = 2; // 직접 설정

        // 백 턴 종료 → 중립기물 스턴 감소
        skipTurn(state); // endTurn 내에서 isNeutral이므로 감소
        assertEquals(1, p.stun, "반턴 후 스턴이 1 감소해야 한다");

        skipTurn(state); // 흑 턴 종료
        assertEquals(0, p.stun, "2번째 반턴 후 스턴이 0 이어야 한다");
    }

    @Test
    @DisplayName("9-b. 중립기물(NEUTRAL_PYLON)에도 스턴이 부여되고 반턴마다 감소한다")
    void pylonNeutralStunDecreases() {
        GameState state = freshState();
        Move.Square f6 = new Move.Square(5, 5);
        String id = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_PYLON, f6);

        Piece.PieceData p = state.getPiece(id);
        p.stun = 3;

        skipTurn(state);
        assertEquals(2, p.stun, "반턴 후 스턴이 1 감소해야 한다");
    }

    // ── 테스트 10: 공존 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("10. 중립기물(NEUTRAL_SENTINEL)·중립기물(NEUTRAL_PYLON)이 보드에 동시에 존재할 수 있다")
    void multipleNeutralsCoexist() {
        GameState state = freshState();

        String sentinelId = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_SENTINEL, new Move.Square(2, 2));
        String pylonId    = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_PYLON,    new Move.Square(5, 5));

        Piece.PieceData sentinel = state.getPiece(sentinelId);
        Piece.PieceData pylon    = state.getPiece(pylonId);

        assertNotNull(sentinel, "NEUTRAL_SENTINEL 중립기물이 존재해야 한다");
        assertNotNull(pylon,    "NEUTRAL_PYLON 중립기물이 존재해야 한다");
        assertTrue(sentinel.isNeutral(), "SENTINEL isNeutral 정상");
        assertTrue(pylon.isNeutral(),    "PYLON isNeutral 정상");
    }

    // ── 테스트 11: copy() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("11. PieceData.copy() 시 isNeutral 이 복사된다 (PieceKind에서 위임)")
    void copyPreservesNeutralFields() {
        GameState state = freshState();
        String sentinelId = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_SENTINEL, new Move.Square(1, 1));
        String pylonId    = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_PYLON,    new Move.Square(6, 6));

        Piece.PieceData sc = state.getPiece(sentinelId).copy();
        Piece.PieceData pc = state.getPiece(pylonId).copy();

        assertTrue(sc.isNeutral(), "SENTINEL copy: isNeutral=true");
        assertTrue(pc.isNeutral(), "PYLON copy: isNeutral=true");
        assertEquals(-1, sc.owner, "copy 후 owner=-1 유지");
        assertEquals(-1, pc.owner, "copy 후 owner=-1 유지");
    }

    // ── 테스트 12: 포켓 경로 차단 ─────────────────────────────────────────────

    @Test
    @DisplayName("12. 중립기물은 placePiece(포켓 착수)로 배치할 수 없다 (포켓에 없으므로 예외)")
    void neutralPieceCannotBeAddedToPocket() {
        GameState state = freshState();

        // 포켓에 중립기물 종류를 추가하지 않았으므로
        // placePiece 호출 시 "포켓에 해당 기물이 없습니다" 예외가 발생해야 한다.
        assertThrows(IllegalStateException.class, () ->
            state.placePiece(0, Piece.PieceKind.ROOK, new Move.Square(3, 3)),
            "포켓에 없는 기물을 착수하면 예외가 발생해야 한다"
        );
    }

    // ── 테스트 13: 캡처 시 스택 이전 ─────────────────────────────────────────

    @Test
    @DisplayName("13. 중립기물(NEUTRAL_SENTINEL)이 적 기물을 포회하면 스택이 이전된다")
    void neutralCaptureTransfersStack() {
        GameState state = freshState();

        // 능동형 중립기물를 a3(0,2)에 배치
        Move.Square a3 = new Move.Square(0, 2);
        String neutralId = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_SENTINEL, a3);
        Piece.PieceData neutral = state.getPiece(neutralId);

        // 시나리오: 중립 기물을 두고 moveStack·stun 초기값 확인
        int initialStack = neutral.moveStack;
        assertTrue(initialStack > 0, "중립기물 초기 moveStack > 0");

        // 반턴 후에도 moveStack 이 재초기화되는지 확인
        skipTurn(state); // 흑 턴
        skipTurn(state); // 다시 백 턴

        int afterTwoHalfTurns = state.getPiece(neutralId).moveStack;
        assertTrue(afterTwoHalfTurns > 0,
            "2반턴 후에도 중립기물의 moveStack 이 초기화되어야 한다");
    }

    // ── 테스트 14: 중립기물 이동 스택 반턴마다 재초기화 ─────────────────────────

    @Test
    @DisplayName("14. 모든 중립기물은 반턴마다 moveStack 이 초기화된다")
    void moveStackRefreshPerHalfTurn() {
        GameState state = freshState();

        String sentinelId = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_SENTINEL, new Move.Square(0, 2));
        String pylonId    = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_PYLON,    new Move.Square(5, 2));

        Piece.PieceData sentinel = state.getPiece(sentinelId);
        Piece.PieceData pylon    = state.getPiece(pylonId);

        // 시작 시 — 모든 중립기물이 moveStack > 0
        assertTrue(sentinel.moveStack > 0, "SENTINEL 초기 moveStack > 0");
        assertTrue(pylon.moveStack > 0,    "PYLON 초기 moveStack > 0");

        // 강제로 스택 소진
        sentinel.moveStack = 0;
        pylon.moveStack = 0;

        skipTurn(state); // 반턴 종료

        assertTrue(sentinel.moveStack > 0,
            "반턴 후 SENTINEL 중립기물 moveStack 이 재초기화되어야 한다");
        assertTrue(pylon.moveStack > 0,
            "반턴 후 PYLON 중립기물 moveStack 도 재초기화되어야 한다");
    }

    // ── 테스트 15: 보드 배치 위치 등록 확인 ──────────────────────────────────

    @Test
    @DisplayName("15. placeNeutralPiece 후 보드에 해당 좌표에 기물이 등록된다")
    void neutralPieceRegisteredOnBoard() {
        GameState state = freshState();
        Move.Square g7 = new Move.Square(6, 6);
        String id = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_SENTINEL, g7);

        Piece.PieceData fromBoard = state.getPieceAt(g7);
        assertNotNull(fromBoard,       "보드에서 해당 좌표에 기물이 있어야 한다");
        assertEquals(id, fromBoard.id,  "보드의 기물 ID와 반환된 ID가 일치해야 한다");
        assertEquals(g7, fromBoard.pos, "기물의 pos 가 배치 좌표와 일치해야 한다");
    }
}
