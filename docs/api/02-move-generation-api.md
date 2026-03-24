# ChessStack Move Generation API 사용법

Chessembly 인터프리터를 사용한 합법 수 생성 API 문서입니다.

## 목차
- [MoveGenerator](#movegenerator) - 합법 수 생성
- [고급 사용법](#고급-사용법)

---

## MoveGenerator

`MoveGenerator`는 Chessembly 인터프리터를 사용하여 특정 기물의 합법 수를 계산하는 클래스입니다.

### 기본 사용법

```java
import com.chesstack.engine.movegen.MoveGenerator;
import com.chesstack.engine.core.*;
import java.util.*;

// GameState와 기물 ID로 합법 수 생성
List<Move.LegalMove> legalMoves = MoveGenerator.generateLegalMoves(state, pieceId);

for (Move.LegalMove move : legalMoves) {
    System.out.printf("%s → %s (%s)%n", 
        move.from, move.to, move.moveType);
}
```

### generateLegalMoves()

기물의 모든 합법 수를 계산합니다.

```java
/**
 * 특정 기물의 모든 합법 수를 계산한다.
 *
 * @param state   현재 게임 상태
 * @param pieceId 기물 ID
 * @return 합법 수 목록
 */
public static List<Move.LegalMove> generateLegalMoves(
    GameState state, 
    String pieceId
)
```

**동작 과정:**
1. 기물 정보 조회 (위치, 종류, 소유자)
2. Chessembly 보드 상태 생성
3. 기물 종류에 맞는 행마법 스크립트 가져오기
4. Chessembly 인터프리터 실행
5. Activation → LegalMove 변환

**예제:**

```java
GameState state = GameState.newDefault();
state.setupInitialPosition();

// d4에 나이트 배치
Move.Square d4 = Move.Square.fromNotation("d4");
String knightId = state.placePiece(0, Piece.PieceKind.KNIGHT, d4);
state.endTurn();

// 합법 수 생성
List<Move.LegalMove> moves = MoveGenerator.generateLegalMoves(state, knightId);

System.out.println("나이트 합법 수: " + moves.size() + "개");
for (Move.LegalMove move : moves) {
    System.out.printf("  %s → %s (캡처: %b)%n",
        move.from.toNotation(), 
        move.to.toNotation(), 
        move.isCapture);
}
```

---

## 고급 사용법

### 1. 조건부 이동 구현

```java
/**
 * 체크 상태에서는 킹만 이동 가능하게 제한
 */
public List<Move.LegalMove> getSafeMoves(GameState state, String pieceId) {
    Piece.PieceData piece = state.getPiece(pieceId);
    
    // 체크 확인 (간단한 예시)
    boolean inCheck = isKingInCheck(state, piece.owner);
    
    if (inCheck && !piece.isRoyal) {
        // 체크 상태에서 일반 기물은 이동 불가
        return Collections.emptyList();
    }
    
    return MoveGenerator.generateLegalMoves(state, pieceId);
}
```

### 2. 이동 범위 제한

```java
/**
 * 최대 이동 거리를 제한하는 필터
 */
public List<Move.LegalMove> getMovesWithinRange(
    GameState state, 
    String pieceId, 
    int maxDistance
) {
    List<Move.LegalMove> allMoves = MoveGenerator.generateLegalMoves(state, pieceId);
    List<Move.LegalMove> filtered = new ArrayList<>();
    
    for (Move.LegalMove move : allMoves) {
        int dx = Math.abs(move.to.x - move.from.x);
        int dy = Math.abs(move.to.y - move.from.y);
        int distance = Math.max(dx, dy); // 체비셰프 거리
        
        if (distance <= maxDistance) {
            filtered.add(move);
        }
    }
    
    return filtered;
}
```

### 3. 이동 타입별 필터링

```java
/**
 * 캡처 이동만 반환
 */
public List<Move.LegalMove> getCapturesOnly(GameState state, String pieceId) {
    List<Move.LegalMove> allMoves = MoveGenerator.generateLegalMoves(state, pieceId);
    
    return allMoves.stream()
        .filter(m -> m.isCapture)
        .collect(Collectors.toList());
}

/**
 * 비-캡처 이동만 반환
 */
public List<Move.LegalMove> getQuietMoves(GameState state, String pieceId) {
    List<Move.LegalMove> allMoves = MoveGenerator.generateLegalMoves(state, pieceId);
    
    return allMoves.stream()
        .filter(m -> !m.isCapture)
        .collect(Collectors.toList());
}

/**
 * 특정 MoveType만 필터링
 */
public List<Move.LegalMove> getByMoveType(
    GameState state, 
    String pieceId, 
    AST.MoveType type
) {
    List<Move.LegalMove> allMoves = MoveGenerator.generateLegalMoves(state, pieceId);
    
    return allMoves.stream()
        .filter(m -> m.moveType == type)
        .collect(Collectors.toList());
}
```

### 4. AI용 이동 평가

```java
/**
 * 이동의 가치를 평가하는 함수
 */
public static class MoveEvaluator {
    
    public static int evaluateMove(GameState state, Move.LegalMove move) {
        int score = 0;
        
        // 중앙 제어 보너스
        int centerDist = Math.abs(move.to.x - 3) + Math.abs(move.to.y - 3);
        score += (6 - centerDist) * 10;
        
        // 캡처 보너스
        if (move.isCapture) {
            Piece.PieceData captured = state.getPieceAt(move.to);
            if (captured != null) {
                score += captured.score() * 100;
            }
        }
        
        // 프로모션 접근 보너스
        String pieceId = state.getBoard().get(move.from);
        Piece.PieceData piece = state.getPiece(pieceId);
        if (piece != null && piece.kind.canPromote()) {
            int oldDist = piece.kind.distanceToPromotion(move.from, piece.isWhite());
            int newDist = piece.kind.distanceToPromotion(move.to, piece.isWhite());
            score += (oldDist - newDist) * 20;
        }
        
        return score;
    }
    
    public static Move.LegalMove getBestMove(GameState state, String pieceId) {
        List<Move.LegalMove> moves = MoveGenerator.generateLegalMoves(state, pieceId);
        
        return moves.stream()
            .max(Comparator.comparingInt(m -> evaluateMove(state, m)))
            .orElse(null);
    }
}
```

### 5. 전체 보드 합법 수 생성

```java
/**
 * 특정 플레이어의 모든 기물에 대한 합법 수 생성
 */
public Map<String, List<Move.LegalMove>> getAllPlayerMoves(
    GameState state, 
    int player
) {
    Map<String, List<Move.LegalMove>> allMoves = new HashMap<>();
    
    for (Piece.PieceData piece : state.getBoardPieces()) {
        if (piece.owner == player && piece.canMove()) {
            List<Move.LegalMove> moves = MoveGenerator.generateLegalMoves(
                state, piece.id
            );
            allMoves.put(piece.id, moves);
        }
    }
    
    return allMoves;
}

/**
 * 총 합법 수 개수 계산
 */
public int countLegalMoves(GameState state, int player) {
    Map<String, List<Move.LegalMove>> allMoves = getAllPlayerMoves(state, player);
    
    return allMoves.values().stream()
        .mapToInt(List::size)
        .sum();
}
```

---

## 실전 예제

### 예제 1: 이동 힌트 시스템

```java
public class MoveHintSystem {
    
    /**
     * 플레이어에게 추천 이동을 제공
     */
    public static List<Move.LegalMove> getRecommendedMoves(
        GameState state, 
        String pieceId, 
        int maxHints
    ) {
        List<Move.LegalMove> allMoves = MoveGenerator.generateLegalMoves(state, pieceId);
        
        // 이동을 점수순으로 정렬
        allMoves.sort((m1, m2) -> {
            int score1 = evaluateMove(state, m1);
            int score2 = evaluateMove(state, m2);
            return Integer.compare(score2, score1); // 내림차순
        });
        
        // 상위 N개만 반환
        return allMoves.stream()
            .limit(maxHints)
            .collect(Collectors.toList());
    }
    
    private static int evaluateMove(GameState state, Move.LegalMove move) {
        int score = 0;
        
        // 캡처 우선
        if (move.isCapture) {
            Piece.PieceData target = state.getPieceAt(move.to);
            score += target.score() * 100;
        }
        
        // 중앙 선호
        int centerX = Math.abs(move.to.x - 3);
        int centerY = Math.abs(move.to.y - 3);
        score += (6 - centerX - centerY) * 10;
        
        return score;
    }
    
    public static void main(String[] args) {
        GameState state = GameState.newDefault();
        state.setupInitialPosition();
        
        // e2에 폰 배치
        Move.Square e2 = Move.Square.fromNotation("e2");
        String pawnId = state.placePiece(0, Piece.PieceKind.PAWN, e2);
        state.endTurn();
        
        // 추천 이동 3개 받기
        List<Move.LegalMove> hints = getRecommendedMoves(state, pawnId, 3);
        
        System.out.println("추천 이동:");
        for (int i = 0; i < hints.size(); i++) {
            Move.LegalMove move = hints.get(i);
            System.out.printf("%d. %s → %s (점수: %d)%n",
                i + 1,
                move.from.toNotation(),
                move.to.toNotation(),
                evaluateMove(state, move));
        }
    }
}
```

### 예제 2: 디버그 모드로 행마법 검증

```java
public class ChessemblyDebugger {
    
    public static void debugPieceMoves(GameState state, String pieceId) {
        // 디버그 모드 활성화
        state.setDebugMode(true);
        
        System.out.println("=== Chessembly 디버그 시작 ===");
        
        Piece.PieceData piece = state.getPiece(pieceId);
        System.out.printf("기물: %s at %s%n", piece.kind, piece.pos);
        
        // 행마법 스크립트 출력
        String script = piece.effectiveKind().chessemblyScript(piece.isWhite());
        System.out.println("\n스크립트:");
        System.out.println(script);
        
        // 합법 수 생성 (디버그 로그와 함께)
        System.out.println("\n실행 로그:");
        List<Move.LegalMove> moves = MoveGenerator.generateLegalMoves(state, pieceId);
        
        System.out.println("\n결과:");
        System.out.println("총 " + moves.size() + "개 합법 수");
        for (Move.LegalMove move : moves) {
            System.out.printf("  %s → %s (%s, 캡처=%b)%n",
                move.from.toNotation(),
                move.to.toNotation(),
                move.moveType,
                move.isCapture);
        }
        
        // 디버그 모드 비활성화
        state.setDebugMode(false);
    }
    
    public static void main(String[] args) {
        GameState state = GameState.newDefault();
        state.setupExperimentalPocket();
        
        // Grasshopper 배치 및 디버그
        Move.Square d4 = Move.Square.fromNotation("d4");
        String ghId = state.placePiece(0, Piece.PieceKind.GRASSHOPPER, d4);
        state.endTurn();
        
        debugPieceMoves(state, ghId);
    }
}
```

---

## 성능 최적화 팁

### 1. 합법 수 캐싱

```java
public class CachedMoveGenerator {
    private final Map<String, List<Move.LegalMove>> cache = new HashMap<>();
    
    public List<Move.LegalMove> getCachedMoves(GameState state, String pieceId) {
        // 캐시 키: pieceId + 보드 해시
        String key = pieceId + "_" + state.hashCode();
        
        return cache.computeIfAbsent(key, k -> 
            MoveGenerator.generateLegalMoves(state, pieceId)
        );
    }
    
    public void invalidateCache() {
        cache.clear();
    }
}
```

### 2. 병렬 처리

```java
import java.util.concurrent.*;

public List<Move.LegalMove> generateMovesParallel(
    GameState state, 
    List<String> pieceIds
) {
    return pieceIds.parallelStream()
        .flatMap(id -> MoveGenerator.generateLegalMoves(state, id).stream())
        .collect(Collectors.toList());
}
```

---

## 다음 단계

- [Minecraft Integration API](04-minecraft-integration-api.md) - 고수준 API 사용하기
