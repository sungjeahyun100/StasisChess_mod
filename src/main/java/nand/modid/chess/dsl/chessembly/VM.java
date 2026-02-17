package nand.modid.chess.dsl.chessembly;

import java.util.List;

/**
 * VM — Interpreter 위에 얹는 편의 래퍼.
 * 스크립트를 한 번에 파싱 → 실행 → 결과 반환하는 헬퍼 메서드 제공.
 */
public final class VM {

    private final Interpreter interpreter;

    public VM() {
        this.interpreter = new Interpreter();
    }

    public VM(boolean debug) {
        this.interpreter = new Interpreter();
        this.interpreter.setDebug(debug);
    }

    /** 스크립트 실행 → Activation 목록 반환 */
    public List<AST.Activation> run(String script, BuiltinOps.BoardState board) {
        interpreter.parse(script);
        return interpreter.execute(board);
    }

    /** 디버그 모드 설정 */
    public void setDebug(boolean debug) {
        interpreter.setDebug(debug);
    }

    /** 내부 인터프리터 접근 */
    public Interpreter getInterpreter() {
        return interpreter;
    }
}
