# ChessStack Java API - Quick Start Guide

ChessStack Java API를 빠르게 시작하는 가이드입니다.

## 목차
- [5분 시작하기](#5분-시작하기)
- [기본 개념](#기본-개념)
- [사용 시나리오별 예제](#사용-시나리오별-예제)
- [문제 해결](#문제-해결)
- [다음 단계](#다음-단계)

---

## 5분 시작하기

### 1. 의존성 추가

```gradle
// build.gradle
dependencies {
    implementation project(':engine')
}
```

### 2. 첫 게임 실행

```java
import com.chesstack.minecraft.api.ChessStackEngine;
import com.chesstack.engine.core.*;

public class QuickStart {
    public static void main(String[] args) {
        // 1. 엔진 생성
        ChessStackEngine engine = new ChessStackEngine();
        
        // 2. 게임 시작
        String gameId = engine.createGame();
        
        // 3. 백 플레이어: 나이트 착수
        engine.placePiece(gameId, "knight", 3, 3); // d4
        engine.endTurn();
        
        // 4. 흑 플레이어: 폰 착수
        engine.placePiece(gameId, "pawn", 4, 4); // e5
        engine.endTurn();
        
        // 5. 백 플레이어: 나이트 이동
        engine.makeMove(gameId, 3, 3, 4, 4); // d4 → e5 (캡처)
        
        // 6. 결과 확인
        System.out.println("게임 상태: " + engine.getGameResult(gameId));
        
        // 7. 보드 출력
        engine.getBoardPieces(gameId).forEach(p -> 
            System.out.println(p.kind + " at " + p.pos)
        );
    }
}
```

**출력:**
```
게임 상태: ONGOING
KING at e1
KING at e8
KNIGHT at e5
```

---

## 기본 개념

### 좌표 시스템

ChessStack은 0-based 좌표를 사용합니다:

```
  a b c d e f g h
8 7 7 7 7 7 7 7 7
7 6 6 6 6 6 6 6 6
6 5 5 5 5 5 5 5 5
5 4 4 4 4 4 4 4 4
4 3 3 3 3 3 3 3 3
3 2 2 2 2 2 2 2 2
2 1 1 1 1 1 1 1 1
1 0 0 0 0 0 0 0 0
  0 1 2 3 4 5 6 7
```

**좌표 변환:**
```java
// 문자열 → 좌표
Move.Square e4 = Move.Square.fromNotation("e4"); // (4, 3)

// 좌표 → 문자열
String notation = e4.toNotation(); // "e4"
```

### 게임 흐름

```
1. createGame() - 게임 생성
2. 플레이어 턴 시작
3. placePiece() 또는 makeMove() - 행동
4. endTurn() - 턴 종료
5. 2-4 반복
6. checkVictory() - 승리 확인
```

### 핵심 클래스

| 클래스 | 역할 |
|--------|------|
| `ChessStackEngine` | 고수준 API (추천) |
| `GameState` | 게임 상태 관리 |
| `Board` | 체스판 |
| `Piece` | 기물 정의 |
| `Move` | 좌표 및 이동 |
| `MoveGenerator` | 합법 수 생성 |
| `Interpreter` | Chessembly 실행 |

---

## 사용 시나리오별 예제

### 시나리오 1: 간단한 2인 게임

```java
import com.chesstack.minecraft.api.ChessStackEngine;
import com.chesstack.engine.core.*;
import java.util.*;

public class TwoPlayerGame {
    public static void main(String[] args) {
        ChessStackEngine engine = new ChessStackEngine();
        String gameId = engine.createGame();
        Scanner scanner = new Scanner(System.in);
        
        while (engine.getGameResult(gameId) == Move.GameResult.ONGOING) {
            int player = engine.getCurrentPlayer(gameId);
            System.out.println("\n" + (player == 0 ? "백" : "흑") + " 턴");
            
            // 포켓 표시
            List<Piece.PieceSpec> pocket = engine.getPocket(gameId, player);
            System.out.println("포켓: " + pocket.size() + "개 기물");
            
            // 착수 또는 이동
            System.out.print("p=착수, m=이동: ");
            String action = scanner.next();
            
            if (action.equals("p")) {
                System.out.print("기물 (knight, pawn, etc.): ");
                String kind = scanner.next();
                System.out.print("좌표 (e4): ");
                String pos = scanner.next();
                Move.Square sq = Move.Square.fromNotation(pos);
                
                try {
                    engine.placePiece(gameId, kind, sq.x, sq.y);
                    System.out.println("✓ 착수 완료");
                } catch (Exception e) {
                    System.out.println("✗ " + e.getMessage());
                    continue;
                }
            } else {
                System.out.print("출발 (e2): ");
                Move.Square from = Move.Square.fromNotation(scanner.next());
                System.out.print("도착 (e4): ");
                Move.Square to = Move.Square.fromNotation(scanner.next());
                
                try {
                    String captured = engine.makeMove(gameId, from.x, from.y, to.x, to.y);
                    System.out.println("✓ 이동 완료" + 
                        (captured != null ? " (캡처!)" : ""));
                } catch (Exception e) {
                    System.out.println("✗ " + e.getMessage());
                    continue;
                }
            }
            
            engine.endTurn(gameId);
        }
        
        System.out.println("\n게임 종료: " + engine.getGameResult(gameId));
    }
}
```

### 시나리오 2: 합법 수 표시 기능

```java
public class MoveHintGame {
    public static void main(String[] args) {
        ChessStackEngine engine = new ChessStackEngine();
        String gameId = engine.createGame();
        
        // 나이트 배치
        engine.placePiece(gameId, "knight", 3, 3); // d4
        engine.endTurn();
        
        // 합법 수 조회
        List<Move.LegalMove> moves = engine.getLegalMoves(gameId, 3, 3);
        
        System.out.println("d4 나이트 가능한 이동:");
        for (int i = 0; i < moves.size(); i++) {
            Move.LegalMove move = moves.get(i);
            System.out.printf("%d. %s (타입: %s, 캡처: %b)%n",
                i + 1,
                move.to.toNotation(),
                move.moveType,
                move.isCapture);
        }
        
        // 시각화
        visualizeMoves(moves, new Move.Square(3, 3));
    }
    
    static void visualizeMoves(List<Move.LegalMove> moves, Move.Square start) {
        char[][] board = new char[8][8];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                board[y][x] = '.';
            }
        }
        
        board[start.y][start.x] = 'K'; // Knight
        
        for (Move.LegalMove move : moves) {
            board[move.to.y][move.to.x] = '*';
        }
        
        System.out.println("\n  a b c d e f g h");
        for (int y = 7; y >= 0; y--) {
            System.out.print((y + 1) + " ");
            for (int x = 0; x < 8; x++) {
                System.out.print(board[y][x] + " ");
            }
            System.out.println((y + 1));
        }
        System.out.println("  a b c d e f g h");
    }
}
```

### 시나리오 3: 커스텀 기물 사용

```java
public class CustomPieceGame {
    public static void main(String[] args) {
        ChessStackEngine engine = new ChessStackEngine();
        
        // 1. 커스텀 기물 정의
        String superKnightScript = 
            "take-move(1, 2) repeat(2); " +  // 나이트 점프 2번
            "take-move(2, 1) repeat(2);";
        
        engine.loadDSLPiece("superknight", superKnightScript);
        
        // 2. 게임 생성
        String gameId = engine.createExperimentalGame();
        
        // 3. 커스텀 기물 착수
        engine.placePiece(gameId, "superknight", 0, 0); // a1
        engine.endTurn();
        
        // 4. 합법 수 확인
        List<Move.LegalMove> moves = engine.getLegalMoves(gameId, 0, 0);
        System.out.println("슈퍼나이트 이동 가능: " + moves.size() + "칸");
        
        moves.forEach(m -> System.out.println("  → " + m.to.toNotation()));
    }
}
```

### 시나리오 4: AI 대전 (간단한 예)

```java
public class SimpleAI {
    
    private final ChessStackEngine engine;
    
    public SimpleAI(ChessStackEngine engine) {
        this.engine = engine;
    }
    
    /**
     * 랜덤 AI
     */
    public void makeRandomMove(String gameId) {
        int player = engine.getCurrentPlayer(gameId);
        List<Piece.PieceData> pieces = engine.getBoardPieces(gameId);
        
        // 현재 플레이어의 기물 중 이동 가능한 것 찾기
        List<Move.LegalMove> allMoves = new ArrayList<>();
        
        for (Piece.PieceData piece : pieces) {
            if (piece.owner == player && piece.canMove()) {
                List<Move.LegalMove> moves = engine.getLegalMoves(
                    gameId, piece.pos.x, piece.pos.y
                );
                allMoves.addAll(moves);
            }
        }
        
        // 이동 가능한 수가 없으면 착수
        if (allMoves.isEmpty()) {
            placeRandomPiece(gameId, player);
        } else {
            // 랜덤 이동
            Move.LegalMove move = allMoves.get(
                new Random().nextInt(allMoves.size())
            );
            engine.makeMove(gameId, 
                move.from.x, move.from.y,
                move.to.x, move.to.y);
        }
    }
    
    private void placeRandomPiece(String gameId, int player) {
        List<Piece.PieceSpec> pocket = engine.getPocket(gameId, player);
        if (pocket.isEmpty()) return;
        
        Piece.PieceSpec spec = pocket.get(0);
        
        // 빈 칸 찾기
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                try {
                    engine.placePiece(gameId, 
                        spec.kind.name().toLowerCase(), x, y);
                    return;
                } catch (Exception ignored) {}
            }
        }
    }
    
    public static void main(String[] args) {
        ChessStackEngine engine = new ChessStackEngine();
        String gameId = engine.createGame();
        SimpleAI ai = new SimpleAI(engine);
        
        // AI vs AI 대전
        while (engine.getGameResult(gameId) == Move.GameResult.ONGOING) {
            int player = engine.getCurrentPlayer(gameId);
            System.out.println((player == 0 ? "백" : "흑") + " AI 턴");
            
            ai.makeRandomMove(gameId);
            engine.endTurn(gameId);
            
            // 보드 출력
            engine.getBoardPieces(gameId).forEach(p ->
                System.out.printf("  %s at %s%n", p.kind, p.pos));
            
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        }
        
        System.out.println("승자: " + engine.getGameResult(gameId));
    }
}
```

### 시나리오 5: 게임 저장/로드

```java
import com.google.gson.Gson;
import java.io.*;

public class SaveLoadGame {
    
    private static final Gson gson = new Gson();
    
    /**
     * 게임 저장
     */
    public static void saveGame(ChessStackEngine engine, String gameId, 
                                String filePath) throws IOException {
        GameState state = engine.getGame(gameId);
        
        // GameState를 JSON으로 직렬화
        String json = gson.toJson(state);
        
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(json);
        }
        
        System.out.println("게임 저장 완료: " + filePath);
    }
    
    /**
     * 게임 로드
     */
    public static String loadGame(ChessStackEngine engine, String filePath) 
            throws IOException {
        
        // JSON에서 GameState 역직렬화
        String json;
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            json = reader.lines().reduce("", String::concat);
        }
        
        GameState state = gson.fromJson(json, GameState.class);
        
        // 엔진에 등록
        String gameId = engine.registerGame(state);
        
        System.out.println("게임 로드 완료: " + gameId);
        return gameId;
    }
    
    public static void main(String[] args) throws IOException {
        ChessStackEngine engine = new ChessStackEngine();
        
        // 게임 생성 및 진행
        String gameId = engine.createGame();
        engine.placePiece(gameId, "knight", 3, 3);
        engine.endTurn();
        
        // 저장
        saveGame(engine, gameId, "game_save.json");
        
        // 새 엔진으로 로드
        ChessStackEngine newEngine = new ChessStackEngine();
        String loadedId = loadGame(newEngine, "game_save.json");
        
        // 확인
        System.out.println("로드된 보드:");
        newEngine.getBoardPieces(loadedId).forEach(p ->
            System.out.println("  " + p.kind + " at " + p.pos));
    }
}
```

---

## 문제 해결

### Q1: "자신의 턴이 아닙니다" 오류

```java
// 문제: 턴 순서를 지키지 않음
engine.placePiece(gameId, "pawn", 0, 0);
engine.placePiece(gameId, "pawn", 1, 1); // ❌ 오류!

// 해결: endTurn() 호출
engine.placePiece(gameId, "pawn", 0, 0);
engine.endTurn(); // ✓
engine.placePiece(gameId, "pawn", 1, 1); // ✓
```

### Q2: "이번 턴에 이미 행동했습니다" 오류

```java
// 문제: 한 턴에 여러 행동
engine.placePiece(gameId, "pawn", 0, 0);
engine.makeMove(gameId, 0, 0, 0, 1); // ❌ 오류!

// 해결: 턴당 하나의 행동만
engine.placePiece(gameId, "pawn", 0, 0);
engine.endTurn();
// 다음 턴에 이동
engine.makeMove(gameId, 0, 0, 0, 1);
```

### Q3: "포켓에 해당 기물이 없습니다" 오류

```java
// 문제: 포켓 확인 안함
engine.placePiece(gameId, "queen", 0, 0);
engine.placePiece(gameId, "queen", 1, 1); // ❌ 포켓에 1개만 있음

// 해결: 포켓 확인
List<Piece.PieceSpec> pocket = engine.getPocket(gameId, player);
System.out.println("사용 가능한 기물: " + pocket);
```

### Q4: 합법 수가 비어있음

```java
// 문제: 스턴 상태 또는 이동 스택 부족
List<Move.LegalMove> moves = engine.getLegalMoves(gameId, x, y);
// moves.isEmpty() == true

// 해결: 기물 상태 확인
Piece.PieceData piece = engine.getPieceAt(gameId, x, y);
if (piece != null) {
    if (piece.stun > 0) {
        System.out.println("스턴 상태: " + piece.stun + "턴 남음");
    }
    if (piece.moveStack == 0) {
        System.out.println("이동 스택 소진");
    }
}
```

### Q5: 좌표 변환 오류

```java
// 문제: 좌표 혼동
Move.Square wrong = new Move.Square(1, 4); // ??? 

// 해결: fromNotation 사용
Move.Square e4 = Move.Square.fromNotation("e4"); // (4, 3) ✓

// 또는 직접 계산
int x = 'e' - 'a'; // 4
int y = '4' - '1'; // 3
Move.Square e4_manual = new Move.Square(x, y); // ✓
```

---

## 성능 팁

### 1. 게임 재사용

```java
// ❌ 나쁜 예: 매번 새 게임 생성
for (int i = 0; i < 100; i++) {
    String gameId = engine.createGame();
    // ... 게임 플레이
    engine.removeGame(gameId);
}

// ✓ 좋은 예: 게임 재사용
String gameId = engine.createGame();
for (int i = 0; i < 100; i++) {
    // ... 게임 플레이
    
    // 게임 리셋 (재사용)
    GameState state = engine.getGame(gameId);
    state.getBoard().clear();
    state.setupInitialPosition();
}
```

### 2. 합법 수 캐싱

```java
// 같은 위치를 여러 번 조회하는 경우
Map<String, List<Move.LegalMove>> moveCache = new HashMap<>();

String key = gameId + "_" + x + "_" + y;
List<Move.LegalMove> moves = moveCache.computeIfAbsent(key,
    k -> engine.getLegalMoves(gameId, x, y));

// 보드 변경 시 캐시 무효화
moveCache.clear();
```

### 3. 디버그 모드 비활성화

```java
// 프로덕션에서는 디버그 모드 끄기
engine.setDebugMode(gameId, false); // ✓
```

---

## 다음 단계

### 더 배우기

1. **[Core API](01-core-api.md)** - 상세한 Core 기능
   - Board, GameState, Move, Piece, RuleSet

2. **[Move Generation API](02-move-generation-api.md)** - 합법 수 생성
   - MoveGenerator, StandardGenerators
   - 커스텀 이동 패턴

3. **[Chessembly DSL API](03-chessembly-dsl-api.md)** - DSL 심화
   - Interpreter, Parser, AST
   - 고급 기법 및 디버깅

4. **[Minecraft Integration API](04-minecraft-integration-api.md)** - Minecraft 통합
   - Fabric 모드 연동
   - 멀티플레이어, GUI

### 실전 프로젝트 아이디어

1. **체스 AI 만들기**
   - Minimax 알고리즘
   - 평가 함수 설계
   - 오프닝 북

2. **체스 변형 게임**
   - 960 체스 (Fischer Random)
   - 3인 체스
   - 원형 체스

3. **웹 인터페이스**
   - Spring Boot + REST API
   - WebSocket 실시간 동기화
   - React/Vue 프론트엔드

4. **토너먼트 시스템**
   - ELO 레이팅
   - 매치메이킹
   - 리플레이 기능

### 커뮤니티

- GitHub Issues: 버그 리포트 및 기능 제안
- 예제 프로젝트: `project_chesstack_java/examples/`
- 문서: `docs/chesstack/` 및 `docs/chessembly/`

---

## 전체 예제 모음

### 완전한 콘솔 게임

```java
import com.chesstack.minecraft.api.ChessStackEngine;
import com.chesstack.engine.core.*;
import java.util.*;

public class CompleteConsoleGame {
    
    private final ChessStackEngine engine;
    private String gameId;
    private final Scanner scanner;
    
    public CompleteConsoleGame() {
        this.engine = new ChessStackEngine();
        this.scanner = new Scanner(System.in);
    }
    
    public void start() {
        System.out.println("=== ChessStack 콘솔 게임 ===\n");
        
        // 게임 타입 선택
        System.out.println("1. 표준 게임");
        System.out.println("2. 실험 게임");
        System.out.print("선택: ");
        int choice = scanner.nextInt();
        
        if (choice == 1) {
            gameId = engine.createGame();
        } else {
            gameId = engine.createExperimentalGame();
        }
        
        // 게임 루프
        gameLoop();
        
        // 결과
        Move.GameResult result = engine.getGameResult(gameId);
        System.out.println("\n게임 종료: " + result);
    }
    
    private void gameLoop() {
        while (engine.getGameResult(gameId) == Move.GameResult.ONGOING) {
            displayStatus();
            
            boolean actionTaken = false;
            while (!actionTaken) {
                actionTaken = handlePlayerAction();
            }
            
            engine.endTurn(gameId);
        }
    }
    
    private void displayStatus() {
        int player = engine.getCurrentPlayer(gameId);
        String color = (player == 0) ? "백" : "흑";
        
        System.out.println("\n" + "=".repeat(50));
        System.out.println(color + " 플레이어 턴");
        System.out.println("=".repeat(50));
        
        // 보드
        displayBoard();
        
        // 포켓
        List<Piece.PieceSpec> pocket = engine.getPocket(gameId, player);
        System.out.print("\n포켓: ");
        pocket.forEach(spec -> System.out.print(spec.kind + " "));
        System.out.println("(" + pocket.size() + "개)");
    }
    
    private void displayBoard() {
        List<Piece.PieceData> pieces = engine.getBoardPieces(gameId);
        
        char[][] board = new char[8][8];
        for (int y = 0; y < 8; y++) {
            Arrays.fill(board[y], '.');
        }
        
        for (Piece.PieceData p : pieces) {
            char symbol = getPieceSymbol(p);
            board[p.pos.y][p.pos.x] = symbol;
        }
        
        System.out.println("\n  a b c d e f g h");
        for (int y = 7; y >= 0; y--) {
            System.out.print((y + 1) + " ");
            for (int x = 0; x < 8; x++) {
                System.out.print(board[y][x] + " ");
            }
            System.out.println((y + 1));
        }
        System.out.println("  a b c d e f g h");
    }
    
    private char getPieceSymbol(Piece.PieceData piece) {
        char base;
        switch (piece.kind) {
            case KING:   base = 'K'; break;
            case QUEEN:  base = 'Q'; break;
            case ROOK:   base = 'R'; break;
            case BISHOP: base = 'B'; break;
            case KNIGHT: base = 'N'; break;
            case PAWN:   base = 'P'; break;
            default:     base = '?';
        }
        return piece.owner == 0 ? base : Character.toLowerCase(base);
    }
    
    private boolean handlePlayerAction() {
        System.out.println("\n명령어: place/move/help/quit");
        System.out.print("> ");
        String cmd = scanner.next();
        
        switch (cmd.toLowerCase()) {
            case "place":
            case "p":
                return handlePlace();
            
            case "move":
            case "m":
                return handleMove();
            
            case "help":
            case "h":
                showHelp();
                return false;
            
            case "quit":
            case "q":
                System.exit(0);
                return false;
            
            default:
                System.out.println("잘못된 명령어");
                return false;
        }
    }
    
    private boolean handlePlace() {
        System.out.print("기물 종류 (knight, pawn, etc.): ");
        String kind = scanner.next();
        
        System.out.print("좌표 (e4): ");
        String pos = scanner.next();
        Move.Square sq = Move.Square.fromNotation(pos);
        
        if (sq == null || !sq.isValid()) {
            System.out.println("잘못된 좌표");
            return false;
        }
        
        try {
            String pieceId = engine.placePiece(gameId, kind, sq.x, sq.y);
            System.out.println("✓ " + kind + " 배치 완료 (ID: " + pieceId + ")");
            return true;
        } catch (Exception e) {
            System.out.println("✗ " + e.getMessage());
            return false;
        }
    }
    
    private boolean handleMove() {
        System.out.print("출발 좌표 (e2): ");
        Move.Square from = Move.Square.fromNotation(scanner.next());
        
        if (from == null || !from.isValid()) {
            System.out.println("잘못된 좌표");
            return false;
        }
        
        // 합법 수 표시
        List<Move.LegalMove> moves = engine.getLegalMoves(gameId, from.x, from.y);
        
        if (moves.isEmpty()) {
            System.out.println("이동 가능한 수가 없습니다");
            return false;
        }
        
        System.out.println("\n가능한 이동:");
        for (int i = 0; i < moves.size(); i++) {
            System.out.printf("%d. %s%n", i + 1, moves.get(i).to.toNotation());
        }
        
        System.out.print("\n도착 좌표 (e4) 또는 번호: ");
        String input = scanner.next();
        
        Move.Square to;
        try {
            int idx = Integer.parseInt(input) - 1;
            to = moves.get(idx).to;
        } catch (NumberFormatException e) {
            to = Move.Square.fromNotation(input);
        }
        
        if (to == null || !to.isValid()) {
            System.out.println("잘못된 입력");
            return false;
        }
        
        try {
            String captured = engine.makeMove(gameId, from.x, from.y, to.x, to.y);
            System.out.println("✓ 이동 완료" + 
                (captured != null ? " (캡처!)" : ""));
            return true;
        } catch (Exception e) {
            System.out.println("✗ " + e.getMessage());
            return false;
        }
    }
    
    private void showHelp() {
        System.out.println("\n=== 도움말 ===");
        System.out.println("place/p - 포켓에서 기물 배치");
        System.out.println("move/m  - 기물 이동");
        System.out.println("help/h  - 도움말 표시");
        System.out.println("quit/q  - 종료");
        System.out.println("\n좌표는 체스 표기법 사용 (예: e4, d4)");
    }
    
    public static void main(String[] args) {
        new CompleteConsoleGame().start();
    }
}
```

이제 ChessStack Java API를 사용할 준비가 되었습니다! 🎉
