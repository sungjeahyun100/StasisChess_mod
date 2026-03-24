package nand.modid.chess.movegen;

import nand.modid.chess.core.Piece;

import java.util.*;

/**
 * StandardGenerators — 내장 기물들의 Chessembly 스크립트를 관리한다.
 */
public final class StandardGenerators {

    private StandardGenerators() {}

    // ── 내장 기물 스크립트 조회 ──────────────────

    /** 모든 내장 기물의 스크립트 맵 반환 */
    public static Map<String, String> getAllBuiltinScripts() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Piece.PieceKind kind : Piece.PieceKind.values()) {
            map.put(kind.scriptName(), kind.chessemblyScript(true));
        }
        return map;
    }
}
