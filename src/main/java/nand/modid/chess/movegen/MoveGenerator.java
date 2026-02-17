package nand.modid.chess.movegen;

import nand.modid.chess.core.*;
import nand.modid.chess.dsl.chessembly.*;

import java.util.*;

/**
 * MoveGenerator — Chessembly 인터프리터를 사용하여 합법 수를 생성한다.
 * Rust의 GameState::get_legal_moves()를 별도 클래스로 분리.
 */
public final class MoveGenerator {

    private MoveGenerator() {}

    /**
     * 특정 기물의 모든 합법 수를 계산한다.
     *
     * @param state   현재 게임 상태
     * @param pieceId 기물 ID
     * @return 합법 수 목록
     */
    public static List<Move.LegalMove> generateLegalMoves(GameState state, String pieceId) {
        List<Move.LegalMove> legalMoves = new ArrayList<>();

        Piece.PieceData piece = state.getPiece(pieceId);
        if (piece == null || !piece.canMove() || piece.pos == null) {
            return legalMoves;
        }

        Move.Square pos = piece.pos;

        // Chessembly 보드 상태 생성
        BuiltinOps.BoardState board = state.toChessemblyBoard(pieceId);
        if (board == null) return legalMoves;

        // 행마법 스크립트 결정
        String script = piece.effectiveKind().chessemblyScript(piece.isWhite());

        // 인터프리터 실행
        Interpreter interpreter = new Interpreter();
        interpreter.setDebug(state.isDebugMode());
        interpreter.parse(script);
        List<AST.Activation> activations = interpreter.execute(board);

        // Activation → LegalMove 변환
        for (AST.Activation act : activations) {
            Move.Square target = new Move.Square(pos.x + act.dx, pos.y + act.dy);
            if (!target.isValid()) continue;

            Move.Square catchTo = new Move.Square(0, 0);
            if (act.catchTo != null) {
                catchTo = new Move.Square(pos.x + act.catchTo[0], pos.y + act.catchTo[1]);
            }

            boolean isCapture = state.getBoard().contains(target);

            legalMoves.add(new Move.LegalMove(
                    pos, target, act.moveType, isCapture, act.tags, catchTo));
        }

        return legalMoves;
    }

    /**
     * 커스텀 DSL 스크립트로 합법 수를 계산한다.
     * 외부에서 정의한 행마법 스크립트를 사용할 때 호출.
     */
    public static List<Move.LegalMove> generateWithScript(
            GameState state, String pieceId, String script) {

        List<Move.LegalMove> legalMoves = new ArrayList<>();

        Piece.PieceData piece = state.getPiece(pieceId);
        if (piece == null || piece.pos == null) return legalMoves;

        Move.Square pos = piece.pos;
        BuiltinOps.BoardState board = state.toChessemblyBoard(pieceId);
        if (board == null) return legalMoves;

        Interpreter interpreter = new Interpreter();
        interpreter.setDebug(state.isDebugMode());
        interpreter.parse(script);
        List<AST.Activation> activations = interpreter.execute(board);

        for (AST.Activation act : activations) {
            Move.Square target = new Move.Square(pos.x + act.dx, pos.y + act.dy);
            if (!target.isValid()) continue;

            Move.Square catchTo = new Move.Square(0, 0);
            if (act.catchTo != null) {
                catchTo = new Move.Square(pos.x + act.catchTo[0], pos.y + act.catchTo[1]);
            }
            boolean isCapture = state.getBoard().contains(target);

            legalMoves.add(new Move.LegalMove(
                    pos, target, act.moveType, isCapture, act.tags, catchTo));
        }

        return legalMoves;
    }
}
