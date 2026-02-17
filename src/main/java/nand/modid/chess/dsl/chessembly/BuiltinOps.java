package nand.modid.chess.dsl.chessembly;

import java.util.*;

/**
 * BuiltinOps — Chessembly 실행에 필요한 보드 상태(BoardState) 및 유틸리티.
 * Rust의 BoardState 구조체를 1:1 포팅.
 */
public final class BuiltinOps {

    private BuiltinOps() {}

    /** 보드 위 기물 정보 */
    public static final class PieceInfo {
        public final String name;
        public final boolean isWhite;

        public PieceInfo(String name, boolean isWhite) {
            this.name = name;
            this.isWhite = isWhite;
        }
    }

    /** 보드 상태 — 인터프리터가 행마법을 계산할 때 참조하는 외부 상태 */
    public static final class BoardState {
        public int boardWidth;
        public int boardHeight;
        public int pieceX;
        public int pieceY;
        public String pieceName;
        public boolean isWhite;
        /** (x,y) → PieceInfo */
        public final Map<Long, PieceInfo> pieces = new HashMap<>();
        /** 전역 상태 */
        public final Map<String, Integer> state = new HashMap<>();
        /** 위협 칸 */
        public final Set<Long> dangerSquares = new HashSet<>();
        /** 체크 상태 */
        public boolean inCheck;

        public BoardState(int boardWidth, int boardHeight, int pieceX, int pieceY,
                          String pieceName, boolean isWhite) {
            this.boardWidth = boardWidth;
            this.boardHeight = boardHeight;
            this.pieceX = pieceX;
            this.pieceY = pieceY;
            this.pieceName = pieceName;
            this.isWhite = isWhite;
        }

        public static long key(int x, int y) {
            return ((long) x << 32) | (y & 0xFFFFFFFFL);
        }

        public void putPiece(int x, int y, String name, boolean white) {
            pieces.put(key(x, y), new PieceInfo(name, white));
        }

        public boolean inBounds(int x, int y) {
            return x >= 0 && x < boardWidth && y >= 0 && y < boardHeight;
        }

        public boolean isEmpty(int x, int y) {
            return inBounds(x, y) && !pieces.containsKey(key(x, y));
        }

        public boolean hasEnemy(int x, int y) {
            PieceInfo info = pieces.get(key(x, y));
            return info != null && info.isWhite != this.isWhite;
        }

        public boolean hasFriendly(int x, int y) {
            PieceInfo info = pieces.get(key(x, y));
            return info != null && info.isWhite == this.isWhite;
        }

        public boolean hasPiece(int x, int y, String pieceName) {
            PieceInfo info = pieces.get(key(x, y));
            return info != null && info.name.equals(pieceName);
        }

        public int getState(String k) {
            return state.getOrDefault(k, 0);
        }

        public boolean isDanger(int x, int y) {
            return dangerSquares.contains(key(x, y));
        }
    }
}
