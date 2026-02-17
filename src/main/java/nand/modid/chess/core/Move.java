package nand.modid.chess.core;

import nand.modid.chess.dsl.chessembly.AST;
import java.util.*;

/**
 * Move — 좌표(Square), 게임 결과, 합법 수, 액션 타입을 정의한다.
 */
public final class Move {

    private Move() {}

    // ── Square ────────────────────────────────────────
    public static final class Square {
        public final int x; // 0=a, 7=h
        public final int y; // 0=1, 7=8

        public Square(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public boolean isValid() {
            return x >= 0 && x < 8 && y >= 0 && y < 8;
        }

        /** "e4" 같은 체스 표기법에서 파싱 */
        public static Square fromNotation(String s) {
            if (s == null || s.length() != 2) return null;
            int fx = s.charAt(0) - 'a';
            int fy = s.charAt(1) - '1';
            if (fx < 0 || fx > 7 || fy < 0 || fy > 7) return null;
            return new Square(fx, fy);
        }

        /** 체스 표기법으로 변환 */
        public String toNotation() {
            return "" + (char)('a' + x) + (char)('1' + y);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Square)) return false;
            Square s = (Square) o;
            return x == s.x && y == s.y;
        }

        @Override
        public int hashCode() {
            return x * 31 + y;
        }

        @Override
        public String toString() {
            return toNotation();
        }
    }

    // ── GameResult ────────────────────────────────────
    public enum GameResult {
        ONGOING, WHITE_WINS, BLACK_WINS
    }

    // ── LegalMove ─────────────────────────────────────
    public static final class LegalMove {
        public final Square from;
        public final Square to;
        public final AST.MoveType moveType;
        public final boolean isCapture;
        public final List<AST.ActionTag> tags;
        public final Square catchTo; // jump용 잡기 위치

        public LegalMove(Square from, Square to, AST.MoveType moveType,
                         boolean isCapture, List<AST.ActionTag> tags, Square catchTo) {
            this.from = from;
            this.to = to;
            this.moveType = moveType;
            this.isCapture = isCapture;
            this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
            this.catchTo = catchTo;
        }

        @Override
        public String toString() {
            return from + "→" + to + " (" + moveType + (isCapture ? " capture" : "") + ")";
        }
    }

    // ── Action (플레이어 행동) ─────────────────────────
    public enum ActionType {
        PLACE, MOVE, DISGUISE, CROWN, STUN
    }

    public static final class Action {
        public final ActionType type;
        public final String pieceId;
        public final Square from;     // MOVE 전용
        public final Square to;       // MOVE, PLACE 전용
        public final String asKind;   // DISGUISE 전용
        public final int stunAmount;  // STUN 전용

        private Action(ActionType type, String pieceId, Square from, Square to,
                       String asKind, int stunAmount) {
            this.type = type;
            this.pieceId = pieceId;
            this.from = from;
            this.to = to;
            this.asKind = asKind;
            this.stunAmount = stunAmount;
        }

        public static Action place(String pieceId, Square target) {
            return new Action(ActionType.PLACE, pieceId, null, target, null, 0);
        }

        public static Action move(String pieceId, Square from, Square to) {
            return new Action(ActionType.MOVE, pieceId, from, to, null, 0);
        }

        public static Action disguise(String pieceId, String asKind) {
            return new Action(ActionType.DISGUISE, pieceId, null, null, asKind, 0);
        }

        public static Action crown(String pieceId) {
            return new Action(ActionType.CROWN, pieceId, null, null, null, 0);
        }

        public static Action stun(String pieceId, int amount) {
            return new Action(ActionType.STUN, pieceId, null, null, null, amount);
        }
    }
}
