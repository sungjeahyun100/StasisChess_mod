# ChessStack Minecraft Integration API 사용법

Minecraft Fabric 모드와의 통합을 위한 고수준 API 문서입니다. `ChessStackEngine`은 게임 로직을 간편하게 사용할 수 있는 인터페이스를 제공합니다.

## 목차
- [개요](#개요)
- [ChessStackEngine](#chessstackengine) - 메인 API
- [게임 생명주기](#게임-생명주기)
- [Minecraft 통합 예제](#minecraft-통합-예제)
- [멀티플레이어 지원](#멀티플레이어-지원)
- [GUI 통합](#gui-통합)

---

## 개요

`ChessStackEngine`은 다음과 같은 특징을 갖습니다:

- **Pure Java**: Minecraft 코드 의존성 없음
- **간단한 API**: 복잡한 내부 로직 숨김
- **멀티 게임**: 여러 게임 동시 관리
- **DSL 지원**: 커스텀 기물 로드

### 사용 흐름

```
1. ChessStackEngine 인스턴스 생성
2. createGame() → 게임 ID 받기
3. (선택) loadDSLPiece() - 커스텀 기물 등록
4. getLegalMoves() / makeMove() / placePiece()
5. endTurn() - 턴 종료
6. getGameResult() - 승리 확인
```

---

## ChessStackEngine

### 생성자

```java
import com.chesstack.minecraft.api.ChessStackEngine;

ChessStackEngine engine = new ChessStackEngine();
```

---

## 게임 생성

### createGame()

표준 포켓으로 새 게임을 생성합니다.

```java
String gameId = engine.createGame();
System.out.println("게임 생성: " + gameId); // "game_1"
```

**반환값**: 게임 ID (예: `"game_1"`, `"game_2"`, ...)

**초기 설정**:
- 백/흑 킹 자동 배치 (e1, e8)
- 표준 포켓 설정 (퀸, 룩x2, 비숍x2, 나이트x2, 폰x8)
- 총 39점 제한

### createExperimentalGame()

실험용 포켓으로 게임을 생성합니다.

```java
String gameId = engine.createExperimentalGame();
```

**실험용 포켓 기물**:
- Amazon, Grasshopper, Knightrider, Archbishop
- Dabbaba, Alfil, Ferz, Centaur, Camel
- Tempest Rook, Cannon, Bouncing Bishop, Experiment

### registerGame()

기존 `GameState`를 등록합니다.

```java
GameState customState = GameState.newDefault();
// ... 커스텀 설정

String gameId = engine.registerGame(customState);
```

---

## DSL 기물 로드

### loadDSLPiece()

커스텀 Chessembly 기물을 등록합니다.

```java
String script = "take-move(1, 0) repeat(2); take-move(0, 1) repeat(2);";
engine.loadDSLPiece("custompiece", script);
```

**파라미터**:
- `pieceName`: 기물 이름 (소문자)
- `script`: Chessembly 스크립트

### loadDSLPieces()

여러 기물을 한번에 등록합니다.

```java
Map<String, String> pieces = new HashMap<>();

pieces.put("spider", 
    "take-move(1, 1); take-move(-1, 1); " +
    "take-move(1, -1); take-move(-1, -1);");

pieces.put("dragon",
    "take-move(2, 0); take-move(0, 2); " +
    "take-move(1, 1) repeat(1);");

engine.loadDSLPieces(pieces);
```

---

## 게임 플레이

### getLegalMoves()

특정 위치의 합법 수를 조회합니다.

```java
List<Move.LegalMove> moves = engine.getLegalMoves(gameId, 4, 3); // e4

System.out.println("합법 수: " + moves.size() + "개");
for (Move.LegalMove move : moves) {
    System.out.printf("%s → %s%n", 
        move.from.toNotation(), 
        move.to.toNotation());
}
```

**파라미터**:
- `gameId`: 게임 ID
- `x`, `y`: 좌표 (0-based)

**반환값**: 합법 수 목록

### makeMove()

이동을 실행합니다.

```java
// e2 → e4
String captured = engine.makeMove(gameId, 4, 1, 4, 3);

if (captured != null) {
    System.out.println("캡처: " + captured);
}
```

**파라미터**:
- `gameId`: 게임 ID
- `fromX`, `fromY`: 출발 좌표
- `toX`, `toY`: 도착 좌표

**반환값**: 캡처된 기물 ID (없으면 `null`)

**예외**: `IllegalArgumentException` - 유효하지 않은 이동

### placePiece()

포켓에서 기물을 착수합니다.

```java
String pieceId = engine.placePiece(gameId, "knight", 3, 3); // d4
System.out.println("배치된 기물: " + pieceId);
```

**파라미터**:
- `gameId`: 게임 ID
- `kindName`: 기물 이름 (예: `"knight"`, `"queen"`)
- `x`, `y`: 좌표

**반환값**: 배치된 기물 ID

**예외**: `IllegalStateException` - 착수 규칙 위반

### endTurn()

턴을 종료합니다.

```java
engine.endTurn(gameId);
```

**동작**:
- `actionTaken` 초기화
- `activePiece` 초기화
- 모든 기물 스턴 감소
- 상속 처리
- 다음 플레이어로 전환

---

## 게임 조회

### getGameResult()

게임 결과를 확인합니다.

```java
Move.GameResult result = engine.getGameResult(gameId);

switch (result) {
    case ONGOING:
        System.out.println("게임 진행 중");
        break;
    case WHITE_WINS:
        System.out.println("백 승리!");
        break;
    case BLACK_WINS:
        System.out.println("흑 승리!");
        break;
}
```

### getCurrentPlayer()

현재 턴 플레이어를 조회합니다.

```java
int player = engine.getCurrentPlayer(gameId);
// 0 = 백, 1 = 흑
```

### getPieceAt()

특정 위치의 기물 정보를 조회합니다.

```java
Piece.PieceData piece = engine.getPieceAt(gameId, 4, 3); // e4

if (piece != null) {
    System.out.printf("기물: %s (소유자: %d, 스턴: %d)%n",
        piece.kind, piece.owner, piece.stun);
}
```

### getBoardPieces()

보드 위 모든 기물을 조회합니다.

```java
List<Piece.PieceData> pieces = engine.getBoardPieces(gameId);

for (Piece.PieceData piece : pieces) {
    System.out.printf("%s at %s%n", piece.kind, piece.pos);
}
```

### getPocket()

포켓을 조회합니다.

```java
List<Piece.PieceSpec> pocket = engine.getPocket(gameId, 0); // 백 포켓

System.out.println("백 포켓:");
for (Piece.PieceSpec spec : pocket) {
    System.out.println("  " + spec.kind);
}
```

---

## 게임 관리

### getGame()

`GameState` 직접 접근.

```java
GameState state = engine.getGame(gameId);

// 저수준 API 사용 가능
state.setDebugMode(true);
state.disguisePiece("piece_1", Piece.PieceKind.QUEEN);
```

### removeGame()

게임을 삭제합니다.

```java
engine.removeGame(gameId);
```

### setDebugMode()

디버그 모드를 설정합니다.

```java
engine.setDebugMode(gameId, true);
// Chessembly 실행 로그 출력
```

---

## 게임 생명주기

### 전체 워크플로우

```java
import com.chesstack.minecraft.api.ChessStackEngine;
import com.chesstack.engine.core.*;
import java.util.*;

public class GameLifecycle {
    
    public static void main(String[] args) {
        ChessStackEngine engine = new ChessStackEngine();
        
        // 1. 게임 생성
        String gameId = engine.createGame();
        System.out.println("게임 시작: " + gameId);
        
        // 2. 게임 루프
        while (engine.getGameResult(gameId) == Move.GameResult.ONGOING) {
            // 현재 플레이어
            int player = engine.getCurrentPlayer(gameId);
            String color = (player == 0) ? "백" : "흑";
            
            System.out.println("\n" + color + " 턴");
            
            // 포켓 출력
            List<Piece.PieceSpec> pocket = engine.getPocket(gameId, player);
            System.out.println("포켓: " + pocket);
            
            // 보드 출력
            displayBoard(engine, gameId);
            
            // 플레이어 액션 (예시)
            performPlayerAction(engine, gameId, player);
            
            // 턴 종료
            engine.endTurn(gameId);
        }
        
        // 3. 게임 종료
        Move.GameResult result = engine.getGameResult(gameId);
        System.out.println("\n게임 종료: " + result);
        
        // 4. 정리
        engine.removeGame(gameId);
    }
    
    static void performPlayerAction(ChessStackEngine engine, String gameId, int player) {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("1=착수, 2=이동: ");
        int choice = scanner.nextInt();
        
        if (choice == 1) {
            // 착수
            System.out.print("기물 종류: ");
            String kind = scanner.next();
            System.out.print("좌표 (x y): ");
            int x = scanner.nextInt();
            int y = scanner.nextInt();
            
            try {
                String pieceId = engine.placePiece(gameId, kind, x, y);
                System.out.println("배치 완료: " + pieceId);
            } catch (Exception e) {
                System.out.println("오류: " + e.getMessage());
            }
            
        } else if (choice == 2) {
            // 이동
            System.out.print("출발 (x y): ");
            int fromX = scanner.nextInt();
            int fromY = scanner.nextInt();
            
            // 합법 수 표시
            List<Move.LegalMove> moves = engine.getLegalMoves(gameId, fromX, fromY);
            System.out.println("가능한 이동:");
            for (int i = 0; i < moves.size(); i++) {
                System.out.printf("%d: %s%n", i, moves.get(i));
            }
            
            System.out.print("선택: ");
            int idx = scanner.nextInt();
            
            Move.LegalMove selected = moves.get(idx);
            try {
                String captured = engine.makeMove(gameId, 
                    selected.from.x, selected.from.y,
                    selected.to.x, selected.to.y);
                if (captured != null) {
                    System.out.println("캡처: " + captured);
                }
            } catch (Exception e) {
                System.out.println("오류: " + e.getMessage());
            }
        }
    }
    
    static void displayBoard(ChessStackEngine engine, String gameId) {
        List<Piece.PieceData> pieces = engine.getBoardPieces(gameId);
        
        System.out.println("\n현재 보드:");
        for (Piece.PieceData p : pieces) {
            String owner = (p.owner == 0) ? "백" : "흑";
            System.out.printf("  %s %s at %s%n", owner, p.kind, p.pos);
        }
    }
}
```

---

## Minecraft 통합 예제

### 예제: Fabric 모드 통합

```java
package com.example.chessmod;

import com.chesstack.minecraft.api.ChessStackEngine;
import com.chesstack.engine.core.*;
import net.minecraft.server.MinecraftServer;
import java.util.*;

public class ChessGameManager {
    
    private final ChessStackEngine engine;
    private final Map<UUID, String> playerGames; // 플레이어 → 게임 ID
    
    public ChessGameManager() {
        this.engine = new ChessStackEngine();
        this.playerGames = new HashMap<>();
        
        // 커스텀 기물 등록
        loadCustomPieces();
    }
    
    private void loadCustomPieces() {
        Map<String, String> custom = new HashMap<>();
        
        custom.put("enderpawn",
            "move(0, 2); take(1, 2); take(-1, 2);");
        
        custom.put("blazeknight",
            "take-move(1, 2) repeat(2); take-move(2, 1) repeat(2);");
        
        engine.loadDSLPieces(custom);
    }
    
    /**
     * 새 게임 시작
     */
    public String startGame(UUID player1, UUID player2) {
        String gameId = engine.createGame();
        
        playerGames.put(player1, gameId);
        playerGames.put(player2, gameId);
        
        return gameId;
    }
    
    /**
     * 기물 착수
     */
    public boolean placePiece(UUID playerId, String kind, int x, int z) {
        String gameId = playerGames.get(playerId);
        if (gameId == null) return false;
        
        try {
            int player = getPlayerNumber(playerId, gameId);
            
            // y 좌표를 z로 매핑 (Minecraft 좌표계)
            String pieceId = engine.placePiece(gameId, kind, x, z);
            
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 기물 이동
     */
    public boolean movePiece(UUID playerId, int fromX, int fromZ, int toX, int toZ) {
        String gameId = playerGames.get(playerId);
        if (gameId == null) return false;
        
        try {
            String captured = engine.makeMove(gameId, fromX, fromZ, toX, toZ);
            
            if (captured != null) {
                // 캡처 효과 (파티클, 사운드 등)
                playCaptureFX(captured);
            }
            
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 턴 종료
     */
    public void endTurn(UUID playerId) {
        String gameId = playerGames.get(playerId);
        if (gameId != null) {
            engine.endTurn(gameId);
        }
    }
    
    /**
     * 승리 확인
     */
    public Move.GameResult checkWinner(UUID playerId) {
        String gameId = playerGames.get(playerId);
        if (gameId == null) return Move.GameResult.ONGOING;
        
        return engine.getGameResult(gameId);
    }
    
    /**
     * 게임 종료 및 정리
     */
    public void endGame(UUID player1, UUID player2) {
        String gameId = playerGames.get(player1);
        if (gameId != null) {
            engine.removeGame(gameId);
            playerGames.remove(player1);
            playerGames.remove(player2);
        }
    }
    
    private int getPlayerNumber(UUID playerId, String gameId) {
        // 플레이어 ID를 게임 내 번호로 변환
        // 실제 구현은 별도 매핑 필요
        return 0;
    }
    
    private void playCaptureFX(String capturedId) {
        // Minecraft 파티클/사운드 효과
    }
}
```

### 예제: 커맨드 핸들러

```java
public class ChessCommand {
    
    private final ChessGameManager manager;
    
    public ChessCommand(ChessGameManager manager) {
        this.manager = manager;
    }
    
    /**
     * /chess start <player2>
     */
    public void handleStart(ServerPlayerEntity player, ServerPlayerEntity opponent) {
        UUID p1 = player.getUuid();
        UUID p2 = opponent.getUuid();
        
        String gameId = manager.startGame(p1, p2);
        
        player.sendMessage(Text.of("체스 게임 시작: " + gameId));
        opponent.sendMessage(Text.of("체스 게임 시작: " + gameId));
    }
    
    /**
     * /chess place <piece> <x> <z>
     */
    public void handlePlace(ServerPlayerEntity player, String kind, int x, int z) {
        UUID playerId = player.getUuid();
        
        boolean success = manager.placePiece(playerId, kind, x, z);
        
        if (success) {
            player.sendMessage(Text.of(kind + " 배치 완료"));
        } else {
            player.sendMessage(Text.of("배치 실패"));
        }
    }
    
    /**
     * /chess move <fromX> <fromZ> <toX> <toZ>
     */
    public void handleMove(ServerPlayerEntity player, 
                          int fromX, int fromZ, int toX, int toZ) {
        UUID playerId = player.getUuid();
        
        boolean success = manager.movePiece(playerId, fromX, fromZ, toX, toZ);
        
        if (success) {
            player.sendMessage(Text.of("이동 완료"));
            
            // 승리 확인
            Move.GameResult result = manager.checkWinner(playerId);
            if (result != Move.GameResult.ONGOING) {
                player.sendMessage(Text.of("게임 종료: " + result));
            }
        } else {
            player.sendMessage(Text.of("이동 실패"));
        }
    }
    
    /**
     * /chess endturn
     */
    public void handleEndTurn(ServerPlayerEntity player) {
        manager.endTurn(player.getUuid());
        player.sendMessage(Text.of("턴 종료"));
    }
}
```

---

## 멀티플레이어 지원

### 게임 세션 관리

```java
public class MultiplayerChessServer {
    
    private final ChessStackEngine engine;
    private final Map<String, GameSession> sessions;
    
    public MultiplayerChessServer() {
        this.engine = new ChessStackEngine();
        this.sessions = new HashMap<>();
    }
    
    static class GameSession {
        String gameId;
        UUID whitePlayer;
        UUID blackPlayer;
        long startTime;
    }
    
    /**
     * 매치메이킹
     */
    public GameSession createMatch(UUID player1, UUID player2) {
        String gameId = engine.createGame();
        
        GameSession session = new GameSession();
        session.gameId = gameId;
        session.whitePlayer = player1;
        session.blackPlayer = player2;
        session.startTime = System.currentTimeMillis();
        
        sessions.put(gameId, session);
        
        return session;
    }
    
    /**
     * 플레이어 검증
     */
    public boolean validatePlayer(String gameId, UUID playerId) {
        GameSession session = sessions.get(gameId);
        if (session == null) return false;
        
        int currentPlayer = engine.getCurrentPlayer(gameId);
        
        if (currentPlayer == 0) {
            return playerId.equals(session.whitePlayer);
        } else {
            return playerId.equals(session.blackPlayer);
        }
    }
    
    /**
     * 액션 실행 (검증 포함)
     */
    public boolean executeAction(String gameId, UUID playerId, Runnable action) {
        if (!validatePlayer(gameId, playerId)) {
            return false;
        }
        
        try {
            action.run();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
```

---

## GUI 통합

### 보드 렌더링

```java
public class ChessBoardRenderer {
    
    /**
     * 3D 체스 보드 렌더링 (Minecraft)
     */
    public void renderBoard(ChessStackEngine engine, String gameId, 
                           World world, BlockPos origin) {
        
        // 보드 기초
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                BlockPos pos = origin.add(x, 0, z);
                
                // 체커보드 패턴
                boolean isWhite = (x + z) % 2 == 0;
                BlockState state = isWhite ? 
                    Blocks.QUARTZ_BLOCK.getDefaultState() :
                    Blocks.OBSIDIAN.getDefaultState();
                
                world.setBlockState(pos, state);
            }
        }
        
        // 기물 배치
        List<Piece.PieceData> pieces = engine.getBoardPieces(gameId);
        
        for (Piece.PieceData piece : pieces) {
            BlockPos piecePos = origin.add(piece.pos.x, 1, piece.pos.y);
            
            // 기물 렌더링 (엔티티 또는 아이템 프레임)
            renderPiece(world, piecePos, piece);
        }
    }
    
    private void renderPiece(World world, BlockPos pos, Piece.PieceData piece) {
        // 기물을 Minecraft 엔티티로 표현
        // 예: ArmorStand + 커스텀 모델
    }
    
    /**
     * 합법 수 하이라이트
     */
    public void highlightLegalMoves(ChessStackEngine engine, String gameId,
                                   World world, BlockPos origin, 
                                   int x, int z) {
        
        List<Move.LegalMove> moves = engine.getLegalMoves(gameId, x, z);
        
        for (Move.LegalMove move : moves) {
            BlockPos highlightPos = origin.add(move.to.x, 0, move.to.y);
            
            // 파티클 효과
            spawnParticles(world, highlightPos);
        }
    }
    
    private void spawnParticles(World world, BlockPos pos) {
        // Minecraft 파티클 효과
    }
}
```

---

## 실전 예제: 완전한 Fabric 모드

```java
// FabricBridgeExample.java 참고
package com.chesstack.minecraft.api;

public class FabricBridgeExample {
    
    /**
     * Fabric 모드 초기화 예제
     */
    public static void initialize() {
        ChessStackEngine engine = new ChessStackEngine();
        
        // 1. 커스텀 기물 로드
        loadMinecraftPieces(engine);
        
        // 2. 커맨드 등록
        registerCommands(engine);
        
        // 3. 이벤트 핸들러
        registerEventHandlers(engine);
    }
    
    private static void loadMinecraftPieces(ChessStackEngine engine) {
        Map<String, String> pieces = new HashMap<>();
        
        // 엔더맨 폰 (2칸 전진 + 텔레포트)
        pieces.put("enderpawn",
            "move(0, 1); move(0, 2); take(1, 1); take(-1, 1); " +
            "take-move(3, 0); take-move(-3, 0);");
        
        // 블레이즈 나이트 (나이트 + 화염)
        pieces.put("blazeknight",
            "take-move(1, 2); take-move(2, 1); " +
            "take(1, 0); take(-1, 0); take(0, 1); take(0, -1);");
        
        engine.loadDSLPieces(pieces);
    }
    
    private static void registerCommands(ChessStackEngine engine) {
        // Fabric 커맨드 등록 로직
    }
    
    private static void registerEventHandlers(ChessStackEngine engine) {
        // Fabric 이벤트 핸들러 등록
    }
}
```

---

## 성능 최적화

### 1. 게임 풀링

```java
public class GamePool {
    private final Queue<String> availableGames = new LinkedList<>();
    private final ChessStackEngine engine;
    
    public GamePool(ChessStackEngine engine, int poolSize) {
        this.engine = engine;
        
        for (int i = 0; i < poolSize; i++) {
            String gameId = engine.createGame();
            availableGames.offer(gameId);
        }
    }
    
    public String acquireGame() {
        String gameId = availableGames.poll();
        if (gameId == null) {
            gameId = engine.createGame();
        }
        return gameId;
    }
    
    public void releaseGame(String gameId) {
        // 게임 상태 초기화
        GameState state = engine.getGame(gameId);
        state.getBoard().clear();
        // ...
        
        availableGames.offer(gameId);
    }
}
```

### 2. 비동기 처리

```java
import java.util.concurrent.*;

public class AsyncChessEngine {
    private final ChessStackEngine engine;
    private final ExecutorService executor;
    
    public AsyncChessEngine() {
        this.engine = new ChessStackEngine();
        this.executor = Executors.newCachedThreadPool();
    }
    
    public CompletableFuture<List<Move.LegalMove>> getLegalMovesAsync(
        String gameId, int x, int y
    ) {
        return CompletableFuture.supplyAsync(
            () -> engine.getLegalMoves(gameId, x, y),
            executor
        );
    }
    
    public CompletableFuture<String> makeMoveAsync(
        String gameId, int fromX, int fromY, int toX, int toY
    ) {
        return CompletableFuture.supplyAsync(
            () -> engine.makeMove(gameId, fromX, fromY, toX, toY),
            executor
        );
    }
}
```

---

## 다음 단계

- [Core API](01-core-api.md) - 저수준 API 이해
- [Move Generation API](02-move-generation-api.md) - 합법 수 생성 커스터마이징
- [Chessembly DSL API](03-chessembly-dsl-api.md) - 커스텀 기물 만들기
