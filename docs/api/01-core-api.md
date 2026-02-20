# ChessStack Core API 사용법

ChessStack의 핵심 엔진 API 문서입니다. 게임 상태 관리, 보드, 기물, 이동 등의 기본 기능을 제공합니다.

## 목차
- [Board](#board) - 체스판 관리
- [GameState](#gamestate) - 게임 상태 관리
- [Move](#move) - 이동 및 액션 정의
- [Piece](#piece) - 기물 정의
- [RuleSet](#ruleset) - 게임 규칙

---

## Board

`Board`는 체스판의 기물 배치를 관리하는 클래스입니다. HashMap 기반으로 구현되어 있으며, Square(좌표)를 키로 사용하여 기물 ID를 저장합니다.

### 주요 메서드

```java
// 기물 배치
void put(Move.Square sq, String pieceId)

// 기물 조회
String get(Move.Square sq)

// 기물 제거
String remove(Move.Square sq)

// 기물 존재 확인
boolean contains(Move.Square sq)

// 모든 기물 정보 조회
Map<Move.Square, String> asMap()
```

### 사용 예제

```java
import com.chesstack.engine.core.*;

// 보드 생성
Board board = new Board();

// e4에 기물 배치
Move.Square e4 = new Move.Square(4, 3); // e4 = (4, 3)
board.put(e4, "piece_1");

// 기물 조회
String pieceId = board.get(e4); // "piece_1"

// 기물 존재 확인
boolean haspiece = board.contains(e4); // true

// 기물 제거
board.remove(e4);

// 모든 기물 조회
Map<Move.Square, String> allPieces = board.asMap();
```

---

## GameState

`GameState`는 게임의 전체 상태를 관리하는 핵심 클래스입니다. 보드, 포켓, 기물 정보, 턴 관리, 승리 조건 확인 등을 담당합니다.

### 생성 및 초기화

```java
// 기본 게임 생성 (백 플레이어부터 시작)
GameState state = GameState.newDefault();

// 표준 포켓으로 초기 포지션 설정
state.setupInitialPosition();

// 실험용 포켓 설정 (특수 기물 포함)
state.setupExperimentalPocket();
```

### 포켓 관리

```java
// 커스텀 포켓 설정 (점수 제한 검증)
List<Piece.PieceSpec> pocket = Arrays.asList(
    new Piece.PieceSpec(Piece.PieceKind.QUEEN),
    new Piece.PieceSpec(Piece.PieceKind.ROOK),
    new Piece.PieceSpec(Piece.PieceKind.BISHOP)
);
state.setupPocket(0, pocket); // 플레이어 0 (백)

// 포켓 조회
List<Piece.PieceSpec> playerPocket = state.getPocket(0);

// 점수 제한 없는 포켓 설정 (실험용)
state.setupPocketUnchecked(1, experimentalPocket);
```

### 기물 착수 (Drop)

```java
// 착수 가능 여부 확인
Move.Square target = new Move.Square(3, 3); // d4
state.canPlace(0, Piece.PieceKind.KNIGHT, target);

// 착수 실행 → 기물 ID 반환
String pieceId = state.placePiece(0, Piece.PieceKind.KNIGHT, target);

// 착수 시 자동으로:
// - 포켓에서 기물 제거
// - 착수 스턴 적용
// - 초기 이동 스택 설정
// - actionTaken = true
```

### 기물 이동

```java
// 특정 위치의 합법 수 조회
Move.Square from = Move.Square.fromNotation("e2");
List<Move.LegalMove> legalMoves = state.getLegalMovesAt(from);

// 합법 수 실행 → 캡처된 기물 ID 반환 (없으면 null)
for (Move.LegalMove move : legalMoves) {
    if (move.to.equals(Move.Square.fromNotation("e4"))) {
        String captured = state.movePieceByLegalMove(move);
        if (captured != null) {
            System.out.println("캡처: " + captured);
        }
        break;
    }
}
```

### 기물 조회

```java
// 특정 위치의 기물 조회
Move.Square sq = Move.Square.fromNotation("e4");
Piece.PieceData piece = state.getPieceAt(sq);

// ID로 기물 조회
Piece.PieceData piece = state.getPiece("piece_1");

// 보드 위 모든 기물 조회
List<Piece.PieceData> allPieces = state.getBoardPieces();
```

### 턴 관리

```java
// 현재 턴 조회 (0=백, 1=흑)
int currentPlayer = state.getTurn();

// 턴 종료 → 다음 플레이어로 전환
state.endTurn();
// - actionTaken 초기화
// - activePiece 초기화
// - stun 감소
// - 상속 처리
```

### 승리 조건 확인

```java
Move.GameResult result = state.checkVictory();

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

### 특수 액션

```java
// 위장 (Disguise) - 특정 기물을 다른 종류로 위장
state.disguisePiece("piece_1", Piece.PieceKind.QUEEN);

// 계승 (Crown) - 기물을 왕족으로 만들기
state.crownPiece("piece_1");

// 스턴 (Stun) - 기물 무력화
state.stunPiece("piece_1");
```

### 디버그 모드

```java
// 디버그 모드 활성화 → Chessembly 실행 로그 출력
state.setDebugMode(true);

// Chessembly 보드 상태 생성 (디버깅용)
BuiltinOps.BoardState chessemblyBoard = state.toChessemblyBoard("piece_1");
```

### 전체 예제

```java
import com.chesstack.engine.core.*;
import java.util.*;

public class CoreAPIExample {
    public static void main(String[] args) {
        // 1. 게임 생성 및 초기화
        GameState state = GameState.newDefault();
        state.setupInitialPosition();
        
        // 2. 백 플레이어가 나이트 착수
        Move.Square d4 = Move.Square.fromNotation("d4");
        String knightId = state.placePiece(0, Piece.PieceKind.KNIGHT, d4);
        System.out.println("나이트 배치: " + knightId);
        
        // 3. 턴 종료
        state.endTurn();
        
        // 4. 흑 플레이어가 폰 착수
        Move.Square e5 = Move.Square.fromNotation("e5");
        String pawnId = state.placePiece(1, Piece.PieceKind.PAWN, e5);
        System.out.println("폰 배치: " + pawnId);
        
        // 5. 턴 종료
        state.endTurn();
        
        // 6. 백 플레이어의 나이트 이동
        List<Move.LegalMove> moves = state.getLegalMovesAt(d4);
        System.out.println("나이트 합법 수: " + moves.size() + "개");
        
        for (Move.LegalMove move : moves) {
            System.out.println("  " + move);
        }
        
        // 7. e5로 이동 (캡처)
        for (Move.LegalMove move : moves) {
            if (move.to.equals(e5)) {
                String captured = state.movePieceByLegalMove(move);
                System.out.println("캡처된 기물: " + captured);
                break;
            }
        }
        
        // 8. 게임 결과 확인
        Move.GameResult result = state.checkVictory();
        System.out.println("게임 상태: " + result);
        
        // 9. 현재 보드 상태 출력
        List<Piece.PieceData> pieces = state.getBoardPieces();
        System.out.println("\n현재 보드:");
        for (Piece.PieceData p : pieces) {
            System.out.printf("  %s at %s (소유자: %d, 스턴: %d, 스택: %d)%n",
                p.kind, p.pos, p.owner, p.stun, p.moveStack);
        }
    }
}
```

---

## Move

`Move`는 게임 내 이동과 액션을 정의하는 유틸리티 클래스입니다.

### Square (좌표)

체스판의 좌표를 나타냅니다 (0-based).

```java
// 좌표 생성
Move.Square sq = new Move.Square(4, 3); // e4

// 체스 표기법에서 파싱
Move.Square e4 = Move.Square.fromNotation("e4");

// 체스 표기법으로 변환
String notation = e4.toNotation(); // "e4"

// 유효성 검사
boolean valid = sq.isValid(); // 0 <= x,y < 8
```

### LegalMove (합법 수)

합법 수는 Chessembly 계산 결과물입니다.

```java
// 합법 수 필드
public final Square from;           // 출발 좌표
public final Square to;              // 도착 좌표
public final AST.MoveType moveType;  // 이동 타입
public final boolean isCapture;      // 캡처 여부
public final List<AST.ActionTag> tags; // 액션 태그
public final Square catchTo;         // jump용 잡기 위치
```

### MoveType 종류

```java
AST.MoveType.MOVE      // 빈 칸으로만 이동
AST.MoveType.TAKE      // 적 기물 칸으로만 이동
AST.MoveType.TAKE_MOVE // 빈 칸 또는 적 기물 칸
AST.MoveType.CATCH     // 캡처 전용 (이동 안함)
AST.MoveType.SHIFT     // 다른 기물이 있는 칸으로 이동
AST.MoveType.JUMP      // 뛰어넘기
```

### Action (플레이어 액션)

```java
// 액션 생성
Move.Action action = new Move.Action(
    Move.ActionType.PLACE,
    "piece_1",
    null,               // from (MOVE만 필요)
    target,             // to
    null                // asKind (DISGUISE만 필요)
);

// 실행
state.performAction(action);
```

### ActionType 종류

```java
ActionType.PLACE    // 착수
ActionType.MOVE     // 이동
ActionType.DISGUISE // 위장
ActionType.CROWN    // 계승
ActionType.STUN     // 스턴
```

---

## Piece

`Piece`는 기물 종류와 데이터를 정의합니다.

### PieceKind (기물 종류)

```java
// 표준 기물
Piece.PieceKind.PAWN
Piece.PieceKind.KING
Piece.PieceKind.QUEEN
Piece.PieceKind.ROOK
Piece.PieceKind.KNIGHT
Piece.PieceKind.BISHOP

// 특수 기물
Piece.PieceKind.AMAZON         // 퀸 + 나이트
Piece.PieceKind.GRASSHOPPER    // 호퍼
Piece.PieceKind.KNIGHTRIDER    // 연속 나이트
Piece.PieceKind.ARCHBISHOP     // 비숍 + 나이트
Piece.PieceKind.DABBABA        // 2칸 직선
Piece.PieceKind.ALFIL          // 2칸 대각선
Piece.PieceKind.FERZ           // 1칸 대각선
Piece.PieceKind.CENTAUR        // 킹 + 나이트
Piece.PieceKind.CAMEL          // (3,1) 점프
Piece.PieceKind.TEMPEST_ROOK   // 폭풍 룩
Piece.PieceKind.CANNON         // 캐논
Piece.PieceKind.BOUNCING_BISHOP // 반사 비숍
```

### PieceKind 메서드

```java
// 점수 조회
int score = Piece.PieceKind.QUEEN.score(); // 9

// Chessembly 행마법 스크립트
String script = Piece.PieceKind.KNIGHT.chessemblyScript(true); // 백
String scriptBlack = Piece.PieceKind.PAWN.chessemblyScript(false); // 흑

// 프로모션 가능 여부
boolean canPromote = Piece.PieceKind.PAWN.canPromote(); // true

// 프로모션 대상 목록
List<Piece.PieceKind> targets = Piece.PieceKind.PAWN.promotionTargets();
// [QUEEN, ROOK, BISHOP, KNIGHT]

// 문자열로 파싱
Piece.PieceKind kind = Piece.PieceKind.fromString("knight");
```

### PieceSpec (포켓용 기물 스펙)

```java
// 기본 기물
Piece.PieceSpec spec = new Piece.PieceSpec(Piece.PieceKind.QUEEN);

// 위장된 기물 (실제는 PAWN이지만 QUEEN처럼 보임)
Piece.PieceSpec disguised = new Piece.PieceSpec(
    Piece.PieceKind.PAWN,
    Piece.PieceKind.QUEEN
);

// 점수 조회
int score = spec.score(); // kind 기준
```

### PieceData (게임 내 기물)

```java
// 필드
public String id;              // 고유 ID
public Piece.PieceKind kind;   // 실제 종류
public int owner;              // 소유자 (0=백, 1=흑)
public Move.Square pos;        // 위치
public int stun;               // 스턴 스택
public int moveStack;          // 이동 스택
public boolean isRoyal;        // 왕족 여부
public Piece.PieceKind disguiseAs; // 위장 종류

// 유틸리티 메서드
boolean canMove = piece.canMove(); // stun == 0 && moveStack > 0
boolean isWhite = piece.isWhite(); // owner == 0
int score = piece.score();
Piece.PieceKind effectiveKind = piece.effectiveKind(); // 위장 고려
```

### 예제: 커스텀 포켓

```java
// 특수 기물로만 구성된 포켓 만들기
List<Piece.PieceSpec> customPocket = Arrays.asList(
    new Piece.PieceSpec(Piece.PieceKind.AMAZON),
    new Piece.PieceSpec(Piece.PieceKind.GRASSHOPPER),
    new Piece.PieceSpec(Piece.PieceKind.KNIGHTRIDER),
    new Piece.PieceSpec(Piece.PieceKind.ARCHBISHOP)
);

GameState state = GameState.newDefault();
state.setupPocketUnchecked(0, customPocket);
state.setupPocketUnchecked(1, customPocket);
```

---

## RuleSet

`RuleSet`은 게임 규칙 상수를 정의합니다.

### 상수

```java
// 보드 크기
RuleSet.BOARD_WIDTH   // 8
RuleSet.BOARD_HEIGHT  // 8

// 점수 제한
RuleSet.MAX_POCKET_SCORE  // 39

// 플레이어
RuleSet.WHITE  // 0
RuleSet.BLACK  // 1
```

### 유틸리티 메서드

```java
// 점수 기반 초기 이동 스택 계산
int stack = RuleSet.initialMoveStack(score);
// 1-2점 → 5스택
// 3-5점 → 3스택
// 6-7점 → 2스택
// 8점 이상 → 1스택

// 착수 시 스턴 계산
int stun = RuleSet.calculatePlacementStun(piece, square);
// 프로모션 기물: 프로모션 거리에 비례
// 일반 기물: score() 값
```

---

## 전체 워크플로우 예제

```java
import com.chesstack.engine.core.*;
import java.util.*;

public class CompleteExample {
    public static void main(String[] args) {
        // 게임 설정
        GameState game = GameState.newDefault();
        game.setupInitialPosition();
        
        playGame(game);
    }
    
    static void playGame(GameState game) {
        Scanner scanner = new Scanner(System.in);
        
        while (game.checkVictory() == Move.GameResult.ONGOING) {
            displayBoard(game);
            
            int player = game.getTurn();
            String color = player == 0 ? "백" : "흑";
            
            System.out.println("\n" + color + " 플레이어 턴");
            System.out.println("포켓: " + game.getPocket(player));
            System.out.println("1=착수, 2=이동, 3=턴 종료: ");
            
            int choice = scanner.nextInt();
            
            try {
                if (choice == 1) {
                    // 착수
                    System.out.print("기물 종류 (예: KNIGHT): ");
                    String kindStr = scanner.next();
                    Piece.PieceKind kind = Piece.PieceKind.fromString(kindStr);
                    
                    System.out.print("좌표 (예: d4): ");
                    String coord = scanner.next();
                    Move.Square square = Move.Square.fromNotation(coord);
                    
                    String pieceId = game.placePiece(player, kind, square);
                    System.out.println("배치 완료: " + pieceId);
                    
                } else if (choice == 2) {
                    // 이동
                    System.out.print("출발 좌표: ");
                    Move.Square from = Move.Square.fromNotation(scanner.next());
                    
                    List<Move.LegalMove> moves = game.getLegalMovesAt(from);
                    System.out.println("가능한 이동:");
                    for (int i = 0; i < moves.size(); i++) {
                        System.out.println(i + ": " + moves.get(i));
                    }
                    
                    System.out.print("선택: ");
                    int idx = scanner.nextInt();
                    
                    String captured = game.movePieceByLegalMove(moves.get(idx));
                    if (captured != null) {
                        System.out.println("캡처: " + captured);
                    }
                    
                } else if (choice == 3) {
                    game.endTurn();
                }
                
            } catch (Exception e) {
                System.out.println("오류: " + e.getMessage());
            }
        }
        
        System.out.println("\n게임 종료: " + game.checkVictory());
    }
    
    static void displayBoard(GameState game) {
        System.out.println("\n현재 보드:");
        for (Piece.PieceData p : game.getBoardPieces()) {
            String owner = p.owner == 0 ? "백" : "흑";
            System.out.printf("%s %s at %s%n", owner, p.kind, p.pos);
        }
    }
}
```

---

## 다음 단계

- [Move Generation API](02-move-generation-api.md) - Chessembly 기반 합법 수 생성
- [Chessembly DSL API](03-chessembly-dsl-api.md) - DSL 직접 사용하기
- [Minecraft Integration API](04-minecraft-integration-api.md) - 고수준 API로 간편하게 사용하기
