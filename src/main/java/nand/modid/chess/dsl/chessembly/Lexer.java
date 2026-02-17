package nand.modid.chess.dsl.chessembly;

import java.util.*;

/**
 * Lexer — Chessembly 스크립트를 토큰 스트림으로 변환한다.
 * Rust의 Lexer 구조체를 1:1 포팅.
 */
public final class Lexer {

    private final String input;
    private int pos;

    public Lexer(String input) {
        this.input = input;
        this.pos = 0;
    }

    // ── 스캔 유틸 ────────────────────────────────────

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private void skipComment() {
        if (pos < input.length() && input.charAt(pos) == '#') {
            while (pos < input.length() && input.charAt(pos) != '\n') {
                pos++;
            }
        }
    }

    private String readWord() {
        int start = pos;
        while (pos < input.length()) {
            char ch = input.charAt(pos);
            if (Character.isWhitespace(ch) || ";{}(),#".indexOf(ch) >= 0) break;
            pos++;
        }
        return input.substring(start, pos);
    }

    private List<String> readArgs() {
        skipWhitespace();
        if (pos >= input.length() || input.charAt(pos) != '(') {
            return Collections.emptyList();
        }
        pos++; // consume '('
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;

        while (pos < input.length()) {
            char ch = input.charAt(pos);
            pos++;
            if (ch == '(') {
                depth++;
                current.append(ch);
            } else if (ch == ')') {
                if (depth == 0) {
                    String trimmed = current.toString().trim();
                    if (!trimmed.isEmpty()) args.add(trimmed);
                    return args;
                }
                depth--;
                current.append(ch);
            } else if (ch == ',') {
                if (depth == 0) {
                    String trimmed = current.toString().trim();
                    if (!trimmed.isEmpty()) args.add(trimmed);
                    current.setLength(0);
                } else {
                    current.append(ch);
                }
            } else {
                current.append(ch);
            }
        }
        return args;
    }

    // ── 토큰 읽기 ───────────────────────────────────

    public AST.Token nextToken() {
        while (true) {
            skipWhitespace();
            skipComment();
            skipWhitespace();
            if (pos >= input.length()) return null;

            char ch = input.charAt(pos);
            if (ch == ';') { pos++; return new AST.Token(AST.TokenType.SEMICOLON); }
            if (ch == '{') { pos++; return new AST.Token(AST.TokenType.OPEN_BRACE); }
            if (ch == '}') { pos++; return new AST.Token(AST.TokenType.CLOSE_BRACE); }
            if (ch == '#') { skipComment(); continue; }

            String word = readWord();
            if (word.isEmpty()) { pos++; continue; }

            List<String> args = readArgs();
            return buildToken(word, args);
        }
    }

    /** 모든 토큰을 리스트로 반환 */
    public List<AST.Token> tokenizeAll() {
        List<AST.Token> tokens = new ArrayList<>();
        AST.Token t;
        while ((t = nextToken()) != null) {
            tokens.add(t);
        }
        return tokens;
    }

    // ── 토큰 빌드 ───────────────────────────────────

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private static int[] xy(List<String> args) {
        if (args.size() >= 2) return new int[]{ parseInt(args.get(0)), parseInt(args.get(1)) };
        return new int[]{ 0, 0 };
    }

    private AST.Token buildToken(String word, List<String> args) {
        int[] p;
        switch (word) {
            // 행마식
            case "take-move": p = xy(args); return new AST.Token(AST.TokenType.TAKE_MOVE, p[0], p[1]);
            case "move":      p = xy(args); return new AST.Token(AST.TokenType.MOVE, p[0], p[1]);
            case "take":      p = xy(args); return new AST.Token(AST.TokenType.TAKE, p[0], p[1]);
            case "catch":     p = xy(args); return new AST.Token(AST.TokenType.CATCH, p[0], p[1]);
            case "shift":     p = xy(args); return new AST.Token(AST.TokenType.SHIFT, p[0], p[1]);
            case "jump":      p = xy(args); return new AST.Token(AST.TokenType.JUMP, p[0], p[1]);
            case "anchor":    p = xy(args); return new AST.Token(AST.TokenType.ANCHOR, p[0], p[1]);
            // 조건식
            case "observe":   p = xy(args); return new AST.Token(AST.TokenType.OBSERVE, p[0], p[1]);
            case "peek":      p = xy(args); return new AST.Token(AST.TokenType.PEEK, p[0], p[1]);
            case "enemy":     p = xy(args); return new AST.Token(AST.TokenType.ENEMY, p[0], p[1]);
            case "friendly":  p = xy(args); return new AST.Token(AST.TokenType.FRIENDLY, p[0], p[1]);
            case "piece-on":
                if (args.size() >= 3)
                    return AST.Token.pieceOn(args.get(0), parseInt(args.get(1)), parseInt(args.get(2)));
                return new AST.Token(AST.TokenType.END);
            case "danger":    p = xy(args); return new AST.Token(AST.TokenType.DANGER, p[0], p[1]);
            case "check":     return new AST.Token(AST.TokenType.CHECK);
            case "bound":     p = xy(args); return new AST.Token(AST.TokenType.BOUND, p[0], p[1]);
            case "edge":      p = xy(args); return new AST.Token(AST.TokenType.EDGE, p[0], p[1]);
            case "edge-top":  p = xy(args); return new AST.Token(AST.TokenType.EDGE_TOP, p[0], p[1]);
            case "edge-bottom": p = xy(args); return new AST.Token(AST.TokenType.EDGE_BOTTOM, p[0], p[1]);
            case "edge-left":   p = xy(args); return new AST.Token(AST.TokenType.EDGE_LEFT, p[0], p[1]);
            case "edge-right":  p = xy(args); return new AST.Token(AST.TokenType.EDGE_RIGHT, p[0], p[1]);
            case "corner":      p = xy(args); return new AST.Token(AST.TokenType.CORNER, p[0], p[1]);
            case "corner-top-left":     p = xy(args); return new AST.Token(AST.TokenType.CORNER_TOP_LEFT, p[0], p[1]);
            case "corner-top-right":    p = xy(args); return new AST.Token(AST.TokenType.CORNER_TOP_RIGHT, p[0], p[1]);
            case "corner-bottom-left":  p = xy(args); return new AST.Token(AST.TokenType.CORNER_BOTTOM_LEFT, p[0], p[1]);
            case "corner-bottom-right": p = xy(args); return new AST.Token(AST.TokenType.CORNER_BOTTOM_RIGHT, p[0], p[1]);
            // 상태
            case "piece":
                if (args.size() >= 1) return new AST.Token(AST.TokenType.PIECE, args.get(0));
                return new AST.Token(AST.TokenType.END);
            case "if-state":
                if (args.size() >= 2) return new AST.Token(AST.TokenType.IF_STATE, args.get(0), parseInt(args.get(1)));
                return new AST.Token(AST.TokenType.END);
            case "set-state":
                if (args.size() >= 2) return new AST.Token(AST.TokenType.SET_STATE, args.get(0), parseInt(args.get(1)));
                return new AST.Token(AST.TokenType.SET_STATE_RESET);
            case "transition":
                if (args.size() >= 1) return new AST.Token(AST.TokenType.TRANSITION, args.get(0));
                return new AST.Token(AST.TokenType.END);
            // 제어
            case "repeat":
                return new AST.Token(AST.TokenType.REPEAT, 0, 0, null,
                                     args.size() >= 1 ? parseInt(args.get(0)) : 1);
            case "do":    return new AST.Token(AST.TokenType.DO);
            case "while": return new AST.Token(AST.TokenType.WHILE);
            case "jmp":
                if (args.size() >= 1) return new AST.Token(AST.TokenType.JMP, args.get(0));
                return new AST.Token(AST.TokenType.END);
            case "jne":
                if (args.size() >= 1) return new AST.Token(AST.TokenType.JNE, args.get(0));
                return new AST.Token(AST.TokenType.END);
            case "label":
                if (args.size() >= 1) return new AST.Token(AST.TokenType.LABEL, args.get(0));
                return new AST.Token(AST.TokenType.END);
            case "not": return new AST.Token(AST.TokenType.NOT);
            case "end": return new AST.Token(AST.TokenType.END);
            default:    return new AST.Token(AST.TokenType.END);
        }
    }
}
