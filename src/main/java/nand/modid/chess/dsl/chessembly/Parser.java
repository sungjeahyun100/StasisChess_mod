package nand.modid.chess.dsl.chessembly;

import java.util.List;

/**
 * Parser — Lexer를 감싸서 스크립트 문자열 → 토큰 리스트 변환 제공.
 * Chessembly는 플랫한 토큰 기반 언어이므로 별도 AST 트리 구성 없이 토큰 리스트를 그대로 사용한다.
 */
public final class Parser {

    private Parser() {}

    /**
     * 스크립트 문자열을 파싱하여 토큰 리스트를 반환한다.
     */
    public static List<AST.Token> parse(String script) {
        return new Lexer(script).tokenizeAll();
    }
}
