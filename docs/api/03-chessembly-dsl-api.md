# ChessStack Chessembly DSL 내부 참조 문서

> **이 문서는 내부 개발 참고용입니다.** Chessembly DSL은 엔진 내부에서 기물 행마법을 정의하는 데 사용되며, 외부 API로 제공되지 않습니다.

Chessembly는 체스 기물의 행마법(이동 패턴)을 정의하는 도메인 특화 언어(DSL)입니다.

## 목차
- [개요](#개요)
- [기본 문법](#기본-문법)
- [Interpreter](#interpreter) - 스크립트 실행
- [Parser](#parser) - 스크립트 파싱
- [Lexer](#lexer) - 토큰화
- [AST](#ast) - 추상 구문 트리
- [VM](#vm) - 가상 머신
- [BuiltinOps](#builtinops) - 내장 연산
- [고급 기법](#고급-기법)
- [디버깅 팁](#디버깅-팁)

---

## 개요

Chessembly는 다음과 같은 특징을 갖습니다:

- **선언적 문법**: 이동 방향과 거리를 간단하게 표현
- **스택 기반**: 앵커(Anchor) 개념으로 연속 이동 지원
- **조건부 실행**: 보드 상태에 따른 동적 행마법
- **제어 흐름**: 반복, 점프, 조건문

### Chessembly 파이프라인

```
스크립트 문자열
    ↓
Lexer: 토큰화
    ↓
Parser: AST 생성
    ↓
Interpreter: 실행
    ↓
Activation 목록 (합법 수)
```

---

## 기본 문법

### 이동 명령어

```chessembly
move(dx, dy)       # 빈 칸으로만 이동
take(dx, dy)       # 적 기물이 있는 칸으로만 이동
take-move(dx, dy)  # 빈 칸 또는 적 기물 칸으로 이동
shift(dx, dy)      # 다른 기물이 있는 칸으로 이동 (아군/적 무관)
catch(dx, dy)      # 캡처만 (이동하지 않음)
jump(dx, dy)       # 뛰어넘기 (마지막 take 위치에서 캡처)
```

### 조건 명령어

```chessembly
peek(dx, dy)       # 기물이 있으면 true
empty(dx, dy)      # 빈 칸이면 true
enemy(dx, dy)      # 적 기물이 있으면 true
friendly(dx, dy)   # 아군 기물이 있으면 true
edge-top(dx, dy)   # 상단 가장자리면 true
edge-bottom(dx, dy) # 하단 가장자리면 true
edge-left(dx, dy)  # 좌측 가장자리면 true
edge-right(dx, dy) # 우측 가장자리면 true
```

### 제어 흐름

```chessembly
repeat(n)          # 이전 명령어를 n번 반복 (0=무한)
do ... while       # do-while 루프
{ ... }            # 스코프 (앵커 격리)
label(n)           # 라벨 정의
jmp(n)             # 라벨로 점프
jne(n)             # 마지막 값이 false가 아니면 점프
not                # 마지막 값 반전
;                  # 체인 구분자 (앵커 초기화)
```

### 예제: 기본 기물들

```chessembly
# 폰 (백)
move(0, 1); take(1, 1); take(-1, 1);

# 룩
take-move(1, 0) repeat(1);
take-move(-1, 0) repeat(1);
take-move(0, 1) repeat(1);
take-move(0, -1) repeat(1);

# 나이트
take-move(1, 2); take-move(2, 1);
take-move(2, -1); take-move(1, -2);
take-move(-1, 2); take-move(-2, 1);
take-move(-2, -1); take-move(-1, -2);

# 비숍
take-move(1, 1) repeat(1);
take-move(1, -1) repeat(1);
take-move(-1, 1) repeat(1);
take-move(-1, -1) repeat(1);
```

---

## Interpreter

`Interpreter`는 Chessembly 스크립트를 실행하여 `Activation` 목록을 생성합니다.

### 기본 사용법

```java
import com.chesstack.engine.dsl.chessembly.*;
import java.util.*;

// 1. Interpreter 생성
Interpreter interpreter = new Interpreter();

// 2. 디버그 모드 (선택)
interpreter.setDebug(true);

// 3. 스크립트 파싱
String script = "take-move(1, 0); take-move(0, 1);";
interpreter.parse(script);

// 4. 보드 상태 생성
BuiltinOps.BoardState board = new BuiltinOps.BoardState();
board.ownerColor = BuiltinOps.Color.WHITE;
// ... 보드 설정

// 5. 실행
List<AST.Activation> activations = interpreter.execute(board);

// 6. 결과 확인
for (AST.Activation act : activations) {
    System.out.printf("이동: (%d, %d) - %s%n", 
        act.dx, act.dy, act.moveType);
}
```

### setDebug()

디버그 로그를 활성화합니다.

```java
interpreter.setDebug(true);

// 실행 시 상세 로그 출력:
// [PC:0] TAKE_MOVE(1,0) | Anchor(0,0) | last=true
// [PC:1] SEMICOLON | Anchor(1,0) | last=true
// ...
```

### parse()

스크립트를 토큰 리스트로 파싱합니다.

```java
String script = """
    move(0, 1) repeat(2);
    take(1, 1);
    """;

interpreter.parse(script);
// 내부적으로 Parser.parse()를 호출하여 Token 리스트 생성
```

### execute()

파싱된 스크립트를 실행합니다.

```java
/**
 * 행마법 계산 실행
 *
 * @param board Chessembly 보드 상태
 * @return Activation 목록 (합법 수)
 */
public List<AST.Activation> execute(BuiltinOps.BoardState board)
```

**동작 원리:**

1. **앵커 초기화**: (0, 0)에서 시작
2. **토큰 순회**: PC(Program Counter)로 순차 실행
3. **종료 규칙**: 일반 식이 false면 현재 체인 스킵
4. **제어식 면제**: `while`, `jmp`, `jne`, `not`, `label`은 종료하지 않음
5. **스코프 관리**: `{ }`로 앵커 저장/복원
6. **Activation 수집**: 이동 명령어 성공 시 추가

---

## Parser

`Parser`는 스크립트를 토큰 리스트로 변환합니다.

### parse()

```java
import com.chesstack.engine.dsl.chessembly.Parser;

String script = "move(1, 0); take(1, 1);";
List<AST.Token> tokens = Parser.parse(script);

for (AST.Token token : tokens) {
    System.out.println(token.type + " " + token.args);
}
```

**출력 예:**
```
MOVE [1, 0]
SEMICOLON []
TAKE [1, 1]
SEMICOLON []
```

### 지원하는 토큰 타입

```java
AST.TokenType.MOVE
AST.TokenType.TAKE
AST.TokenType.TAKE_MOVE
AST.TokenType.SHIFT
AST.TokenType.CATCH
AST.TokenType.JUMP
AST.TokenType.PEEK
AST.TokenType.EMPTY
AST.TokenType.ENEMY
AST.TokenType.FRIENDLY
AST.TokenType.REPEAT
AST.TokenType.DO
AST.TokenType.WHILE
AST.TokenType.LABEL
AST.TokenType.JMP
AST.TokenType.JNE
AST.TokenType.NOT
AST.TokenType.OPEN_BRACE
AST.TokenType.CLOSE_BRACE
AST.TokenType.SEMICOLON
```

---

## Lexer

`Lexer`는 문자열을 토큰으로 분해합니다 (내부 사용).

```java
import com.chesstack.engine.dsl.chessembly.Lexer;

Lexer lexer = new Lexer("move(1, 0); take(2, 1);");

while (lexer.hasNext()) {
    String token = lexer.next();
    System.out.println("Token: " + token);
}
```

**출력 예:**
```
Token: move
Token: (
Token: 1
Token: ,
Token: 0
Token: )
Token: ;
Token: take
...
```

---

## AST

`AST`는 추상 구문 트리 관련 클래스를 포함합니다.

### Token

```java
public static class Token {
    public TokenType type;
    public int[] args;      // 숫자 인자
    public String strArg;   // 문자열 인자
}
```

### Activation

```java
public static class Activation {
    public int dx;           // 이동 오프셋 X
    public int dy;           // 이동 오프셋 Y
    public MoveType moveType; // 이동 타입
    public List<ActionTag> tags; // 액션 태그
    public int[] catchTo;    // jump용 캡처 위치
}
```

### MoveType

```java
public enum MoveType {
    MOVE,      // 빈 칸으로만
    TAKE,      // 적 칸으로만
    TAKE_MOVE, // 빈 칸 또는 적
    CATCH,     // 캡처만
    SHIFT,     // 기물이 있는 칸
    JUMP       // 뛰어넘기
}
```

### ActionTag

```java
public enum ActionTag {
    CAPTURE,   // 캡처 발생
    JUMP       // 점프 이동
}
```

---

## VM

`VM`은 Chessembly 가상 머신으로, Interpreter의 실행 로직을 담당합니다 (내부 사용).

```java
// VM은 주로 Interpreter에서 사용되며,
// 직접 사용하는 경우는 드뭅니다.
```

---

## BuiltinOps

`BuiltinOps`는 Chessembly 내장 연산과 보드 상태를 정의합니다.

### BoardState

```java
public static class BoardState {
    public Color ownerColor;  // 기물 소유자
    public Map<String, Color> board; // 좌표 → 색상
    
    // 좌표를 문자열로 변환
    public static String pos(int x, int y) {
        return x + "," + y;
    }
}
```

### Color

```java
public enum Color {
    WHITE,
    BLACK,
    EMPTY  // 빈 칸
}
```

### 보드 상태 생성 예제

```java
BuiltinOps.BoardState board = new BuiltinOps.BoardState();
board.ownerColor = BuiltinOps.Color.WHITE;

// 기물 배치
board.board.put(BuiltinOps.BoardState.pos(0, 0), BuiltinOps.Color.WHITE);
board.board.put(BuiltinOps.BoardState.pos(1, 1), BuiltinOps.Color.BLACK);
board.board.put(BuiltinOps.BoardState.pos(2, 2), BuiltinOps.Color.BLACK);

// Interpreter 실행
Interpreter interpreter = new Interpreter();
interpreter.parse("take-move(1, 1); take-move(2, 2);");
List<AST.Activation> acts = interpreter.execute(board);

// 결과: [(1,1), (2,2)] 두 개의 activation
```

---

## 고급 기법

### 1. 앵커(Anchor) 활용

앵커는 연속 이동의 기준점입니다.

```chessembly
# 나이트라이더: 나이트 점프를 연속으로
take-move(1, 2) repeat(1);
# (1,2) → (2,4) → (3,6) ...

# 앵커 초기화 (세미콜론)
take-move(1, 0);   # 앵커: (1,0)
take-move(1, 0);   # 앵커: (2,0)
;                  # 앵커 초기화 → (0,0)
take-move(0, 1);   # 앵커: (0,1)
```

### 2. 스코프 { } 활용

스코프는 앵커를 격리합니다.

```chessembly
# Tempest Rook: 대각선 1칸 후 직선
take-move(1, 1) {
    take-move(1, 0) repeat(1)
} {
    take-move(0, 1) repeat(1)
};

# 동작:
# 1. (1,1)로 이동 성공
# 2. 첫 번째 { }: 앵커 저장 → (1,0) 방향 반복 → 앵커 복원
# 3. 두 번째 { }: 앵커 저장 → (0,1) 방향 반복 → 앵커 복원
```

### 3. do-while 루프

```chessembly
# Grasshopper: 기물을 만날 때까지 이동 후 뛰어넘기
do peek(1, 0) while take-move(1, 0);

# 동작:
# 1. peek(1,0) - 기물이 있으면 true
# 2. true면 take-move(1,0) 실행 → 반복
# 3. false면 종료
```

### 4. 조건부 점프

```chessembly
# Bouncing Bishop: 벽에 부딪히면 반사
do take-move(1, 1) while
    peek(0, 0)
    edge-right(1, 1) jne(0)    # 오른쪽 벽 아니면 label 0으로
    take-move(-1, 1) repeat(1) # 반사: 왼쪽으로
    label(0)
    edge-top(1, 1) jne(1)      # 위쪽 벽 아니면 label 1로
    take-move(1, -1) repeat(1) # 반사: 아래로
    label(1);
```

### 5. 조건 반전

```chessembly
# Cannon: 기물 뛰어넘어 캡처
do take(1, 0) enemy(0, 0) not while jump(1, 0) repeat(1);

# enemy(0, 0) not: 적이 아니면 (빈 칸 또는 아군)
```

---

## 디버깅 팁

### 1. 디버그 모드 활용

```java
Interpreter interpreter = new Interpreter();
interpreter.setDebug(true);
interpreter.parse(script);

// 상세 실행 로그 출력
List<AST.Activation> acts = interpreter.execute(board);
```

### 2. 토큰 확인

```java
List<AST.Token> tokens = Parser.parse(script);
System.out.println("토큰 목록:");
for (int i = 0; i < tokens.size(); i++) {
    AST.Token t = tokens.get(i);
    System.out.printf("%d: %s %s%n", i, t.type, Arrays.toString(t.args));
}
```

### 3. 앵커 추적

```java
// 디버그 출력에서 Anchor 값을 확인하여
// 연속 이동이 올바르게 동작하는지 확인
```

---

## 다음 단계

- [Minecraft Integration API](04-minecraft-integration-api.md) - 고수준 API
- [Core API](01-core-api.md) - 기본 구조 이해
