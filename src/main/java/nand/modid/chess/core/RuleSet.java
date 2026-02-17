package nand.modid.chess.core;

/**
 * RuleSet — 게임 규칙 상수 및 설정.
 */
public final class RuleSet {

    private RuleSet() {}

    /** 보드 크기 */
    public static final int BOARD_WIDTH = 8;
    public static final int BOARD_HEIGHT = 8;

    /** 포켓 점수 제한 */
    public static final int MAX_POCKET_SCORE = 39;

    /** 점수 기반 이동 스택 계산 (stack.md) */
    public static int initialMoveStack(int score) {
        if (score >= 1 && score <= 2) return 5;
        if (score >= 3 && score <= 5) return 3;
        if (score >= 6 && score <= 7) return 2;
        if (score >= 8) return 1;
        return 1;
    }

    /** 착수 시 스턴 스택 계산 */
    public static int calculatePlacementStun(Piece.PieceData piece, Move.Square square) {
        Piece.PieceKind kind = piece.kind;
        if (kind.canPromote()) {
            int distance = kind.distanceToPromotion(square, piece.isWhite());
            int maxStun = kind.maxPromotionStun();
            int maxDistance = 7;
            return maxStun - (maxStun * distance / maxDistance);
        }
        return piece.score();
    }

    /** 백 플레이어 ID */
    public static final int WHITE = 0;
    /** 흑 플레이어 ID */
    public static final int BLACK = 1;
}
