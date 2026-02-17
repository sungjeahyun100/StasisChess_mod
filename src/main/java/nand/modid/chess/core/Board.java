package nand.modid.chess.core;

import java.util.*;

/**
 * Board — HashMap 기반 체스판.
 * Square → PieceId(String) 매핑.
 */
public final class Board {

    private final Map<Move.Square, String> map = new HashMap<>();

    public void put(Move.Square sq, String pieceId) {
        map.put(sq, pieceId);
    }

    public String get(Move.Square sq) {
        return map.get(sq);
    }

    public String remove(Move.Square sq) {
        return map.remove(sq);
    }

    public boolean contains(Move.Square sq) {
        return map.containsKey(sq);
    }

    public Set<Map.Entry<Move.Square, String>> entries() {
        return map.entrySet();
    }

    public int size() {
        return map.size();
    }

    public void clear() {
        map.clear();
    }

    /** 모든 좌표-기물ID 쌍을 반환 */
    public Map<Move.Square, String> asMap() {
        return Collections.unmodifiableMap(map);
    }
}
