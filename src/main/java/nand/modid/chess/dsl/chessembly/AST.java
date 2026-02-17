package nand.modid.chess.dsl.chessembly;

import java.util.*;

/**
 * AST — Chessembly DSL의 모든 토큰 타입, 액션 태그, 활성화 정보를 정의한다.
 */
public final class AST {

    private AST() {}

    // ── MoveType ──────────────────────────────────────
    public enum MoveType {
        TAKE_MOVE, // 이동 또는 잡기
        MOVE,      // 이동만 (빈 칸)
        TAKE,      // 잡기만 (적 있을 때)
        CATCH,     // 제자리에서 잡기 (원거리)
        SHIFT,     // 자리 바꾸기
        JUMP       // take 후 점프
    }

    // ── ActionTagType ─────────────────────────────────
    public enum ActionTagType {
        TRANSITION, // 기물 변환
        SET_STATE   // 전역 상태 설정
    }

    // ── ActionTag ─────────────────────────────────────
    public static final class ActionTag {
        public final ActionTagType tagType;
        public final String key;
        public final int value;
        public final String pieceName; // nullable

        public ActionTag(ActionTagType tagType, String key, int value, String pieceName) {
            this.tagType = tagType;
            this.key = key;
            this.value = value;
            this.pieceName = pieceName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ActionTag)) return false;
            ActionTag t = (ActionTag) o;
            return tagType == t.tagType
                    && Objects.equals(key, t.key)
                    && value == t.value
                    && Objects.equals(pieceName, t.pieceName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tagType, key, value, pieceName);
        }

        @Override
        public String toString() {
            return "ActionTag{" + tagType + ", key='" + key + "', val=" + value
                    + (pieceName != null ? ", piece=" + pieceName : "") + "}";
        }
    }

    // ── Activation ────────────────────────────────────
    public static final class Activation {
        public final int dx;
        public final int dy;
        public final MoveType moveType;
        public final List<ActionTag> tags;
        public final int[] catchTo; // null이면 해당 없음, [dx, dy] offsets

        public Activation(int dx, int dy, MoveType moveType, List<ActionTag> tags, int[] catchTo) {
            this.dx = dx;
            this.dy = dy;
            this.moveType = moveType;
            this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
            this.catchTo = catchTo;
        }

        @Override
        public String toString() {
            return "Activation{dx=" + dx + ", dy=" + dy + ", " + moveType + "}";
        }
    }

    // ── TokenType ─────────────────────────────────────
    public enum TokenType {
        // 행마식
        TAKE_MOVE, MOVE, TAKE, CATCH, SHIFT, JUMP, ANCHOR,
        // 조건식
        OBSERVE, PEEK, ENEMY, FRIENDLY, PIECE_ON, DANGER, CHECK,
        BOUND, EDGE, EDGE_TOP, EDGE_BOTTOM, EDGE_LEFT, EDGE_RIGHT,
        CORNER, CORNER_TOP_LEFT, CORNER_TOP_RIGHT, CORNER_BOTTOM_LEFT, CORNER_BOTTOM_RIGHT,
        // 상태
        PIECE, IF_STATE, SET_STATE, SET_STATE_RESET, TRANSITION,
        // 제어
        REPEAT, DO, WHILE, JMP, JNE, LABEL, NOT, END,
        // 구조
        OPEN_BRACE, CLOSE_BRACE, SEMICOLON
    }

    // ── Token ─────────────────────────────────────────
    public static final class Token {
        public final TokenType type;
        public final int dx;
        public final int dy;
        public final String strArg;  // 기물 이름, 라벨, 상태 키 등
        public final int intArg;     // repeat 횟수, 상태 값 등

        public Token(TokenType type) {
            this(type, 0, 0, null, 0);
        }

        public Token(TokenType type, int dx, int dy) {
            this(type, dx, dy, null, 0);
        }

        public Token(TokenType type, String strArg) {
            this(type, 0, 0, strArg, 0);
        }

        public Token(TokenType type, String strArg, int intArg) {
            this(type, 0, 0, strArg, intArg);
        }

        public Token(TokenType type, int dx, int dy, String strArg, int intArg) {
            this.type = type;
            this.dx = dx;
            this.dy = dy;
            this.strArg = strArg;
            this.intArg = intArg;
        }

        /** piece-on(name, dx, dy) 전용 팩토리 */
        public static Token pieceOn(String pieceName, int dx, int dy) {
            return new Token(TokenType.PIECE_ON, dx, dy, pieceName, 0);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Token{").append(type);
            if (dx != 0 || dy != 0) sb.append(", dx=").append(dx).append(", dy=").append(dy);
            if (strArg != null) sb.append(", str='").append(strArg).append("'");
            if (intArg != 0) sb.append(", int=").append(intArg);
            return sb.append('}').toString();
        }
    }
}
