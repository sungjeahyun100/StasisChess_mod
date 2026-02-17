package nand.modid.comand;

import nand.modid.chess.core.*;
import nand.modid.chess.dsl.chessembly.*;
import nand.modid.chess.movegen.*;
import nand.modid.chess.core.GameState;

import java.util.*;

/**
 * ChessStackEngine — Minecraft Fabric 모드가 호출하는 고수준 API.
 * Pure Java로 유지되며, Minecraft 코드 의존성 없음.
 *
 * 사용 흐름:
 * 1. createGame()
 * 2. loadDSLPiece() (선택)
 * 3. getLegalMoves() / makeMove() / endTurn()
 * 4. getGameResult()
 */
public final class ChessStackEngine {

    /** 활성 게임 스토리지 (게임 ID → GameState) */
    private final Map<String, GameState> games = new HashMap<>();
    private int nextGameId = 1;

    // ── 게임 생성 ─────────────────────────────────────

    /** 새 게임 생성 (표준 포켓) → 게임 ID 반환 */
    public String createGame() {
        String id = "game_" + nextGameId++;
        GameState state = GameState.newDefault();
        state.setupInitialPosition();
        games.put(id, state);
        return id;
    }

    /** 새 게임 생성 (실험용 포켓) */
    public String createExperimentalGame() {
        String id = "game_" + nextGameId++;
        GameState state = GameState.newDefault();
        state.setupExperimentalPocket();
        games.put(id, state);
        return id;
    }

    /** 기존 GameState로 게임 등록 */
    public String registerGame(GameState state) {
        String id = "game_" + nextGameId++;
        games.put(id, state);
        return id;
    }

    // ── DSL 기물 로드 ─────────────────────────────────

    /**
     * 커스텀 DSL 기물을 로드한다.
     *
     * @param pieceName 기물 이름 (소문자)
     * @param script    Chessembly 스크립트
     */
    public void loadDSLPiece(String pieceName, String script) {
        StandardGenerators.registerScript(pieceName, script);
    }

    /** 여러 커스텀 기물을 한번에 등록 */
    public void loadDSLPieces(Map<String, String> nameToScript) {
        nameToScript.forEach(StandardGenerators::registerScript);
    }

    // ── 이동 ──────────────────────────────────────────

    /** 특정 위치의 합법 수 목록 반환 */
    public List<Move.LegalMove> getLegalMoves(String gameId, int x, int y) {
        GameState state = getGame(gameId);
        return state.getLegalMovesAt(new Move.Square(x, y));
    }

    /** 이동 실행 → 캡처된 기물 ID (없으면 null) */
    public String makeMove(String gameId, int fromX, int fromY, int toX, int toY) {
        GameState state = getGame(gameId);
        Move.Square from = new Move.Square(fromX, fromY);
        Move.Square to = new Move.Square(toX, toY);

        List<Move.LegalMove> moves = state.getLegalMovesAt(from);
        for (Move.LegalMove lm : moves) {
            if (lm.to.equals(to)) {
                return state.movePieceByLegalMove(lm);
            }
        }
        throw new IllegalArgumentException("유효하지 않은 이동: " + from + " → " + to);
    }

    /** 착수 실행 → 배치된 기물 ID */
    public String placePiece(String gameId, String kindName, int x, int y) {
        GameState state = getGame(gameId);
        Piece.PieceKind kind = Piece.PieceKind.fromString(kindName);
        return state.placePiece(state.getTurn(), kind, new Move.Square(x, y));
    }

    /** 턴 종료 */
    public void endTurn(String gameId) {
        getGame(gameId).endTurn();
    }

    // ── 조회 ──────────────────────────────────────────

    /** 게임 결과 */
    public Move.GameResult getGameResult(String gameId) {
        return getGame(gameId).checkVictory();
    }

    /** 현재 턴 (0=백, 1=흑) */
    public int getCurrentPlayer(String gameId) {
        return getGame(gameId).getTurn();
    }

    /** 특정 위치 기물 정보 */
    public Piece.PieceData getPieceAt(String gameId, int x, int y) {
        return getGame(gameId).getPieceAt(new Move.Square(x, y));
    }

    /** 보드 위 모든 기물 */
    public List<Piece.PieceData> getBoardPieces(String gameId) {
        return getGame(gameId).getBoardPieces();
    }

    /** 포켓 조회 */
    public List<Piece.PieceSpec> getPocket(String gameId, int player) {
        return getGame(gameId).getPocket(player);
    }

    /** GameState 직접 접근 */
    public GameState getGame(String gameId) {
        GameState state = games.get(gameId);
        if (state == null) throw new IllegalArgumentException("게임을 찾을 수 없습니다: " + gameId);
        return state;
    }

    /** 게임 제거 */
    public void removeGame(String gameId) {
        games.remove(gameId);
    }

    /** 디버그 모드 설정 */
    public void setDebugMode(String gameId, boolean debug) {
        getGame(gameId).setDebugMode(debug);
    }
}
