# ChessStack Java API 문서

ChessStack Java API의 완전한 사용 가이드입니다.

## 📚 문서 목록

### 시작하기
- **[Quick Start Guide](00-quick-start.md)** - 5분 안에 시작하기
  - 첫 게임 실행
  - 기본 개념
  - 시나리오별 예제
  - 문제 해결

### API 레퍼런스

1. **[Core API](01-core-api.md)** - 핵심 엔진 기능
   - Board - 체스판 관리
   - GameState - 게임 상태
   - Move - 좌표 및 이동
   - Piece - 기물 정의
   - RuleSet - 게임 규칙

2. **[Move Generation API](02-move-generation-api.md)** - 합법 수 생성
   - MoveGenerator - Chessembly 기반 이동 생성
   - StandardGenerators - 기본 행마법 관리
   - 고급 사용법 및 필터링

3. **[Chessembly DSL 내부 참조](03-chessembly-dsl-api.md)** - 내부 구현 참조
   - Interpreter - 스크립트 실행
   - Parser - 스크립트 파싱
   - AST - 추상 구문 트리
   - 고급 기법 및 디버깅

4. **[Minecraft Integration API](04-minecraft-integration-api.md)** - 고수준 API
   - ChessStackEngine - 메인 API
   - Minecraft Fabric 통합
   - 멀티플레이어 지원

## 🚀 빠른 시작

### 설치

```gradle
// build.gradle
dependencies {
    implementation project(':engine')
}
```

### 첫 게임

```java
import com.chesstack.minecraft.api.ChessStackEngine;

ChessStackEngine engine = new ChessStackEngine();
String gameId = engine.createGame();

// 백: 나이트 착수
engine.placePiece(gameId, "knight", 3, 3);
engine.endTurn();

// 흑: 폰 착수
engine.placePiece(gameId, "pawn", 4, 4);
engine.endTurn();

// 백: 나이트로 캡처
engine.makeMove(gameId, 3, 3, 4, 4);

System.out.println("게임 상태: " + engine.getGameResult(gameId));
```

## 📖 어떤 문서를 읽어야 할까요?

### 🎯 목적별 가이드

| 목적 | 추천 문서 |
|------|----------|
| **빠르게 시작하고 싶어요** | [Quick Start](00-quick-start.md) |
| **기본 구조를 이해하고 싶어요** | [Core API](01-core-api.md) |
| **Minecraft에 통합하고 싶어요** | [Minecraft Integration](04-minecraft-integration-api.md) |
| **합법 수 생성을 이해하고 싶어요** | [Move Generation](02-move-generation-api.md) |

### 🎓 학습 경로

#### 초급 (처음 시작하는 분)
1. [Quick Start Guide](00-quick-start.md) (30분)
2. [Core API - 기본 예제](01-core-api.md#전체-예제) (1시간)
3. [Minecraft Integration - ChessStackEngine](04-minecraft-integration-api.md#chessstackengine) (30분)

#### 중급 (기본을 이해한 분)
1. [Core API - 전체](01-core-api.md) (2시간)
2. [Move Generation API](02-move-generation-api.md) (1.5시간)
3. [Chessembly DSL - 기본 문법](03-chessembly-dsl-api.md#기본-문법) (1시간)

#### 고급 (깊이 있는 커스터마이징)
1. [Chessembly DSL - 전체](03-chessembly-dsl-api.md) (3시간)
2. [Move Generation - 고급 사용법](02-move-generation-api.md#고급-사용법) (1.5시간)
3. [Core API - 특수 액션](01-core-api.md#특수-액션) (1시간)

## 🔍 주요 클래스 빠른 참조

### ChessStackEngine (고수준 API)
```java
// 게임 생성
String gameId = engine.createGame();

// 기물 착수
String pieceId = engine.placePiece(gameId, "knight", 3, 3);

// 합법 수 조회
List<Move.LegalMove> moves = engine.getLegalMoves(gameId, 3, 3);

// 이동 실행
String captured = engine.makeMove(gameId, 3, 3, 4, 5);

// 턴 종료
engine.endTurn(gameId);

// 승리 확인
Move.GameResult result = engine.getGameResult(gameId);
```

### GameState (저수준 API)
```java
// 게임 생성
GameState state = GameState.newDefault();
state.setupInitialPosition();

// 착수
Move.Square sq = Move.Square.fromNotation("d4");
String pieceId = state.placePiece(0, Piece.PieceKind.KNIGHT, sq);

// 합법 수 조회
List<Move.LegalMove> moves = state.getLegalMovesAt(sq);

// 이동
String captured = state.movePieceByLegalMove(moves.get(0));

// 턴 종료
state.endTurn();
```

### Chessembly Interpreter
```java
// 인터프리터 생성
Interpreter interpreter = new Interpreter();

// 스크립트 파싱
String script = "take-move(1, 0) repeat(1); take-move(0, 1) repeat(1);";
interpreter.parse(script);

// 보드 상태 생성
BuiltinOps.BoardState board = new BuiltinOps.BoardState();
board.ownerColor = BuiltinOps.Color.WHITE;

// 실행
List<AST.Activation> activations = interpreter.execute(board);
```

## 💡 코드 예제

### 사용 사례별 예제

각 문서에는 다양한 실전 예제가 포함되어 있습니다:

- **[Quick Start - 시나리오별 예제](00-quick-start.md#사용-시나리오별-예제)**
  - 2인 게임
  - 합법 수 표시
  - 간단한 AI
  - 게임 저장/로드

- **[Core API - 전체 워크플로우](01-core-api.md#전체-워크플로우-예제)**
  - 완전한 게임 루프
  - 보드 출력
  - 플레이어 입력 처리

- **[Move Generation - 실전 예제](02-move-generation-api.md#실전-예제)**
  - 이동 힌트 시스템
  - 디버그 도구

- **[Minecraft Integration - 예제](04-minecraft-integration-api.md#minecraft-통합-예제)**
  - Fabric 모드 통합
  - 커맨드 핸들러
  - 멀티플레이어
  - GUI 렌더링

## 🛠️ 문제 해결

일반적인 문제와 해결 방법은 [Quick Start - 문제 해결](00-quick-start.md#문제-해결) 섹션을 참조하세요.

주요 오류:
- "자신의 턴이 아닙니다"
- "이번 턴에 이미 행동했습니다"
- "포켓에 해당 기물이 없습니다"
- 합법 수가 비어있음
- 좌표 변환 오류

## 📦 프로젝트 구조

```
src/main/java/com/chesstack/
├── engine/
│   ├── core/              # 핵심 엔진
│   │   ├── Board.java
│   │   ├── GameState.java
│   │   ├── Move.java
│   │   ├── Piece.java
│   │   └── RuleSet.java
│   ├── movegen/           # 합법 수 생성
│   │   ├── MoveGenerator.java
│   │   └── StandardGenerators.java
│   └── dsl/
│       └── chessembly/    # Chessembly DSL
│           ├── Interpreter.java
│           ├── Parser.java
│           ├── Lexer.java
│           ├── AST.java
│           ├── VM.java
│           └── BuiltinOps.java
└── minecraft/
    └── api/               # Minecraft 통합
        └── ChessStackEngine.java
```

## 🔗 관련 문서

### ChessStack 개념
- [move.md](../chesstack/move.md) - 이동 시스템
- [stack.md](../chesstack/stack.md) - 스택 시스템
- [promotion.md](../chesstack/promotion.md) - 프로모션
- [rule.md](../chesstack/rule.md) - 게임 규칙

### Chessembly 개념
- [CONCEPT.md](../chessembly/CONCEPT.md) - 기본 개념
- [TUTORIAL.md](../chessembly/TUTORIAL.md) - 상세 튜토리얼
- [CONTROL.md](../chessembly/CONTROL.md) - 제어 흐름
- [DEBUGGER.md](../chessembly/DEBUGGER.md) - 디버깅

## 📝 버전 정보

- **현재 버전**: 1.0.0
- **최소 Java 버전**: Java 11
- **빌드 도구**: Gradle 7.0+

## 🤝 기여하기

버그 리포트, 기능 제안, 문서 개선은 GitHub Issues를 통해 제출해 주세요.

## 📄 라이선스

이 프로젝트는 MIT 라이선스를 따릅니다.

---

