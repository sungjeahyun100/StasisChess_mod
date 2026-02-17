package nand.modid.chess.movegen;

import nand.modid.chess.core.Piece;

import java.util.*;

/**
 * StandardGenerators — 기본 기물들의 Chessembly 스크립트를 관리한다.
 * PieceKind에 내장된 스크립트 외에, 외부에서 커스텀 기물 스크립트를 등록/조회할 수 있다.
 */
public final class StandardGenerators {

    private StandardGenerators() {}

    /** 커스텀 기물 스크립트 레지스트리 */
    private static final Map<String, String> customScripts = new LinkedHashMap<>();

    /**
     * 커스텀 기물의 Chessembly 스크립트를 등록한다.
     *
     * @param pieceName 기물 이름 (소문자)
     * @param script    Chessembly 스크립트
     */
    public static void registerScript(String pieceName, String script) {
        customScripts.put(pieceName.toLowerCase(), script);
    }

    /**
     * 기물 이름으로 스크립트를 조회한다.
     * 커스텀 등록이 있으면 우선, 없으면 PieceKind 내장 스크립트 반환.
     */
    public static String getScript(String pieceName, boolean isWhite) {
        String custom = customScripts.get(pieceName.toLowerCase());
        if (custom != null) return custom;

        Piece.PieceKind kind = Piece.PieceKind.fromString(pieceName);
        return kind.chessemblyScript(isWhite);
    }

    /** 등록된 커스텀 기물 이름 목록 */
    public static Set<String> getCustomPieceNames() {
        return Collections.unmodifiableSet(customScripts.keySet());
    }

    /** 커스텀 기물 등록 해제 */
    public static void unregisterScript(String pieceName) {
        customScripts.remove(pieceName.toLowerCase());
    }

    /** 모든 커스텀 기물 등록 해제 */
    public static void clearCustomScripts() {
        customScripts.clear();
    }

    // ── 내장 기물 스크립트 예시 조회 ──────────────────

    /** 모든 내장 기물의 스크립트 맵 반환 */
    public static Map<String, String> getAllBuiltinScripts() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Piece.PieceKind kind : Piece.PieceKind.values()) {
            map.put(kind.scriptName(), kind.chessemblyScript(true));
        }
        return map;
    }
}
