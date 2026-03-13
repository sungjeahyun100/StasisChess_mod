# 새 기물 추가 방법 (How to Add a New Piece)

이 문서는 StasisChess 모드에 새로운 체스 기물을 추가하는 전체 절차를 단계별로 정리합니다.
이 문서에서 사용된 엔진 버전은 v0.1.0입니다.

---

## 목차

1. [개요](#1-개요)
2. [Step 1 — PieceKind enum에 기물 등록](#2-step-1--piecekind-enum에-기물-등록)
3. [Step 2 — Chessembly 행마법 스크립트 작성](#3-step-2--chessembly-행마법-스크립트-작성)
4. [Step 3 — fromString() 파싱 등록](#4-step-3--fromstring-파싱-등록)
5. [Step 4 — 마인크래프트 시각적 블록 지정](#5-step-4--마인크래프트-시각적-블록-지정)
6. [Step 5 (선택) — 실험용 포켓에 추가](#6-step-5-선택--실험용-포켓에-추가)
7. [Step 6 (선택) — 프로모션 대상 설정](#7-step-6-선택--프로모션-대상-설정)
8. [점수 & 스택 참고표](#8-점수--스택-참고표)
9. [전체 체크리스트](#9-전체-체크리스트)
10. [예제: "워지르(Wazir)" 기물 추가하기](#10-예제-워지르wazir-기물-추가하기)
11. [중립기물(Gray Piece) 추가하기](#11-중립기물gray-piece-추가하기)

---

## 1. 개요

기물 추가는 크게 **엔진 등록(Steps 1~3)**과 **마인크래프트 렌더링(Step 4)** 두 파트로 구성됩니다.
Steps 5~6은 필요에 따라 선택적으로 추가합니다.

| 파일 | 변경 내용 |
|---|---|
| `src/main/java/nand/modid/chess/core/Piece.java` | enum 상수, 행마법, fromString |
| `src/main/java/nand/modid/game/MinecraftChessManager.java` | 시각적 블록 매핑 |
| `src/main/java/nand/modid/chess/core/GameState.java` | (선택) 실험용 포켓 |

---

## 2. Step 1 — PieceKind enum에 기물 등록

**파일:** `src/main/java/nand/modid/chess/core/Piece.java`

`PieceKind` enum에 새로운 상수를 추가합니다.

```java
// 기존 마지막 항목 아래에 추가 (CUSTOM 앞)
WAZIR("wazir", 2),
```

- 첫 번째 인자 `scriptName` — Chessembly 내부에서 사용하는 소문자 식별자. `fromString()` 파싱 키로도 사용됩니다.
- 두 번째 인자 `score` — 기물 점수. 포켓 점수 합계 검증(최대 39점)과 이동 스택 계산에 직접 영향을 미칩니다.

> **점수 설계 기준:** 기동성과 커버 범위를 고려해 결정합니다. 자세한 점수 기준은 [점수 & 스택 참고표](#8-점수--스택-참고표)를 참조하세요.

---

## 3. Step 2 — Chessembly 행마법 스크립트 작성

**파일:** `src/main/java/nand/modid/chess/core/Piece.java`  
**메서드:** `chessemblyScript(boolean isWhite)`

`switch` 문에 새 `case`를 추가합니다.

```java
case WAZIR:
    return "take-move(1, 0); take-move(-1, 0); take-move(0, 1); take-move(0, -1);";
```

### Chessembly 주요 명령어 요약

| 명령어 | 설명 |
|---|---|
| `move(dx, dy)` | 빈 칸으로만 이동 |
| `take(dx, dy)` | 적 기물이 있는 칸으로만 이동 (캡처) |
| `take-move(dx, dy)` | 빈 칸 또는 적 기물 칸 모두 이동 가능 |
| `shift(dx, dy)` | 아군/적 기물이 있는 칸으로 이동 |
| `catch(dx, dy)` | 이동 없이 캡처만 |
| `jump(dx, dy)` | 기물을 뛰어넘어 캡처 |
| `repeat(1)` | 바로 앞의 이동을 보드 끝까지 반복 (슬라이딩) |
| `repeat(n)` | 바로 앞 n개의 식을 반복 |
| `peek(dx, dy)` | 해당 칸에 기물이 있으면 true |
| `empty(dx, dy)` | 해당 칸이 비어 있으면 true |
| `enemy(dx, dy)` | 해당 칸에 적 기물이 있으면 true |
| `friendly(dx, dy)` | 해당 칸에 아군 기물이 있으면 true |
| `do ... while` | do-while 루프 |
| `{ ... }` | 앵커 격리 스코프 |
| `;` | 독립된 설명 구분자 (앵커 초기화) |

방향이 색(진영)에 따라 반전되어야 하는 기물(예: 폰)은 `isWhite` 파라미터를 활용해 두 가지 스크립트를 반환합니다.

```java
case WAZIR:
    // 방향 무관 기물이면 isWhite를 무시해도 됩니다.
    return "take-move(1, 0); take-move(-1, 0); take-move(0, 1); take-move(0, -1);";
```

더 복잡한 DSL 문법은 [Chessembly DSL API](03-chessembly-dsl-api.md) 및 [Chessembly 튜토리얼](../chessembly/TUTORIAL.md)을 참조하세요.

---

## 4. Step 3 — fromString() 파싱 등록

**파일:** `src/main/java/nand/modid/chess/core/Piece.java`  
**메서드:** `fromString(String s)`

`switch` 문에 새 `case`를 추가합니다. 입력 값은 Step 1에서 정한 `scriptName`과 동일해야 합니다.

```java
case "wazir": return WAZIR;
```

이 등록을 빠뜨리면 `PieceKind.fromString("wazir")` 호출 시 `CUSTOM`이 반환되어 행마법이 잘못 적용됩니다.

---

## 5. Step 4 — 마인크래프트 시각적 블록 지정

**파일:** `src/main/java/nand/modid/game/MinecraftChessManager.java`  
**메서드:** `getPieceBlockForKind(Piece.PieceKind kind, boolean isWhite)`

`switch` 표현식에 새 `case`를 추가합니다.

```java
case WAZIR -> Blocks.MOSS_BLOCK.getDefaultState();
```

- 진영(백/흑)에 따라 다른 블록을 사용하려면 조건식을 활용합니다.

```java
case WAZIR -> (isWhite ? Blocks.MOSS_BLOCK : Blocks.WARPED_NYLIUM).getDefaultState();
```

- 마인크래프트 `Blocks` 클래스의 모든 블록을 사용할 수 있습니다.
- 기존 기물과 구별되는 블록을 선택하는 것이 권장됩니다.

---

## 6. Step 5 (선택) — 실험용 포켓에 추가

**파일:** `src/main/java/nand/modid/chess/core/GameState.java`  
**메서드:** `setupExperimentalPocket()`

실험용 게임 모드(`Start Experimental Tool`)에서 새 기물을 사용하려면 포켓 목록에 추가합니다.

```java
new Piece.PieceSpec(Piece.PieceKind.WAZIR),
```

실험용 포켓은 39점 제한 없이 `setupPocketUnchecked()`를 사용하므로 점수 제한에 관계없이 추가할 수 있습니다.

---

## 7. Step 6 (선택) — 프로모션 대상 설정

현재 프로모션은 `PAWN`에만 적용됩니다. 새 기물을 폰의 **프로모션 대상**으로 추가하려면 `PieceKind.promotionTargets()` 메서드를 수정합니다.

```java
public List<PieceKind> promotionTargets() {
    if (this == PAWN) {
        return Arrays.asList(QUEEN, ROOK, BISHOP, KNIGHT, WAZIR); // WAZIR 추가
    }
    return Collections.emptyList();
}
```

새 기물 자체가 **프로모션 가능한 기물**이어야 한다면 (현재 설계상 지원되지 않으며, 상당한 추가 구현이 필요합니다), `canPromote()`, `isPromotionSquare()`, `distanceToPromotion()`, `maxPromotionStun()` 메서드도 함께 수정해야 합니다.

---

## 8. 점수 & 스택 참고표

기물 점수는 **포켓 구성 가능 여부**와 **이동/스턴 스택 초기값**을 결정합니다.

### 점수 → 이동 스택 변환 (`RuleSet.initialMoveStack()`)

| 기물 점수 | 초기 이동 스택 |
|---|---|
| 1 ~ 2점 | 5스택 |
| 3 ~ 5점 | 3스택 |
| 6 ~ 7점 | 2스택 |
| 8점 이상 | 1스택 |

### 기존 기물 점수 참고

| 기물 | 점수 |
|---|---|
| PAWN, FERZ | 1점 |
| DABBABA, ALFIL | 2점 |
| KNIGHT, BISHOP, CAMEL | 3점 |
| KING, GRASSHOPPER | 4점 |
| ROOK, CENTAUR, CANNON | 5점 |
| ARCHBISHOP | 6점 |
| KNIGHTRIDER, TEMPEST_ROOK, BOUNCING_BISHOP | 7점 |
| QUEEN | 9점 |
| AMAZON | 13점 |

포켓 점수 총합 제한은 **39점**입니다 (`RuleSet.MAX_POCKET_SCORE`).

---

## 9. 전체 체크리스트

새 기물을 추가할 때 아래 항목을 순서대로 확인하세요.

- [ ] **Step 1** `Piece.PieceKind` enum에 `(scriptName, score)` 상수 추가
- [ ] **Step 2** `chessemblyScript()` switch에 Chessembly 행마법 case 추가
- [ ] **Step 3** `fromString()` switch에 파싱 case 추가 (`scriptName`과 일치)
- [ ] **Step 4** `getPieceBlockForKind()` switch에 마인크래프트 블록 매핑 case 추가
- [ ] **Step 5** (선택) `setupExperimentalPocket()`에 `PieceSpec` 추가
- [ ] **Step 6** (선택) `promotionTargets()`에 승급 대상으로 추가

---

## 10. 예제: "워지르(Wazir)" 기물 추가하기

워지르(Wazir)는 상하좌우 1칸을 이동하거나 캡처할 수 있는 변형 체스 기물입니다. 점수는 2점으로 설정합니다.

### Step 1 — `Piece.java` enum 추가

```java
// EXPERIMENT("experiment", 1) 위에 추가
WAZIR("wazir", 2),
EXPERIMENT("experiment", 1),
```

### Step 2 — `chessemblyScript()` case 추가

```java
case WAZIR:
    return "take-move(1, 0); take-move(-1, 0); take-move(0, 1); take-move(0, -1);";
```

### Step 3 — `fromString()` case 추가

```java
case "wazir": return WAZIR;
```

### Step 4 — `MinecraftChessManager.java` 블록 매핑 추가

```java
case WAZIR -> (isWhite ? Blocks.MOSS_BLOCK : Blocks.WARPED_NYLIUM).getDefaultState();
```

### Step 5 — `setupExperimentalPocket()`에 추가 (선택)

```java
new Piece.PieceSpec(Piece.PieceKind.WAZIR),
```

### API 사용 예제

```java
GameState state = GameState.newDefault();
state.setupPocketUnchecked(0, Arrays.asList(
    new Piece.PieceSpec(Piece.PieceKind.WAZIR)
));

// 착수
Move.Square d4 = Move.Square.fromNotation("d4");
String wazirId = state.placePiece(0, Piece.PieceKind.WAZIR, d4);

// 합법 수 조회 (상하좌우 4칸)
List<Move.LegalMove> moves = state.getLegalMovesAt(d4);
```

---

## 다음 단계

- [Core API](01-core-api.md) — 게임 상태 및 기물 API
- [Chessembly DSL API](03-chessembly-dsl-api.md) — 고급 행마법 문법
- [Chessembly 튜토리얼](../chessembly/TUTORIAL.md) — DSL 입문

---

## 11. 중립기물(Gray Piece) 추가하기

중립기물은 **백과 흑 양측 모두 사용할 수 있는 특수 기물**입니다.
중요: **중립 여부는 `PieceKind` 정의 시점에 결정**됩니다. 기존 일반 기물(KNIGHT, QUEEN 등)을 중립으로 사용하는 것이 아니라, 처음부터 중립기물용 새 `PieceKind`를 만들어야 합니다.

### 중립기물의 주요 특성

- 백과 흑 **양측 모두** 자신의 턴에 사용(이동)할 수 있다.
- **포켓에 넣을 수 없다.** 오직 보드 위에서만 존재한다.
- 아군/적 판별 시 **사용하는 플레이어의 색을 따른다** (중립기물은 항상 현재 플레이어에게 아군으로 보인다).
- `take` / `take-move` 등 **캡처 계열 행마로 포획할 수 없다** (항상 아군이므로).
- 다른 기물이 `shift`로 중립기물 위치를 교환하는 것은 가능하다.
- **스턴 스택**은 매 반턴(각 플레이어의 턴이 끝날 때)마다 1씩 감소한다.
- **이동 스택**은 매 반턴마다 `RuleSet.initialMoveStack(score)`로 초기화된다.

### Step A — `PieceKind` enum 등록: 중립 속성 내장

중립기물은 **3-인자 생성자**로 등록합니다. 세 번째 인자가 `isNeutral`을 결정합니다.

```java
// 형식: (scriptName, score, isNeutral)

NEUTRAL_KNIGHTRIDER("neutral_knightrider", 7, true),
```

> **왜 PieceKind에 내장하는가?**
> 중립 여부는 기물의 **본질적 속성**입니다. 런타임 플래그로 기존 기물을 중립화시키는 것은 설계 상 혼란을 줍니다.
> `NEUTRAL_KNIGHTRIDER`는 항상 중립이고, `KNIGHT`는 항상 일반 기물입니다.

### Step B — Chessembly 행마법 스크립트 작성 (일반 절차와 동일)

행마법은 일반 기물과 동일하게 작성합니다.
방향 판별(`isWhite`)은 중립기물을 **사용 중인 플레이어**의 색을 기준으로 자동 결정됩니다.

```java
case NEUTRAL_KNIGHTRIDER:
    return "take-move(1, 2) repeat(1); take-move(2, 1) repeat(1);"
         + " take-move(2, -1) repeat(1); take-move(1, -2) repeat(1);"
         + " take-move(-1, 2) repeat(1); take-move(-2, 1) repeat(1);"
         + " take-move(-2, -1) repeat(1); take-move(-1, -2) repeat(1);";
```

### Step C — `fromString()` 파싱 등록 (일반 절차와 동일)

```java
case "neutral_knightrider": return NEUTRAL_KNIGHTRIDER;
```

### Step D — 보드에 배치: `placeNeutralPiece(kind, target)`

중립기물은 포켓 경유 없이 **`GameState.placeNeutralPiece()`** 로 보드에 직접 배치합니다.
모든 중립기물은 `RuleSet.initialMoveStack(score)`에 해당하는 이동 스택을 부여받습니다.
실제 이동 가능 여부는 Chessembly 스크립트가 결정합니다.

```java
GameState state = GameState.newDefault();

Move.Square d4 = new Move.Square(3, 3);
String neutralId = state.placeNeutralPiece(Piece.PieceKind.NEUTRAL_KNIGHTRIDER, d4);
```

> **주의:** `placePiece()` (포켓 착수 메서드)는 중립기물용이 아닙니다.
> `placeNeutralPiece()`에 비-중립 `PieceKind`를 전달하면 `IllegalArgumentException`이 발생합니다.

### Step E — 마인크래프트 시각적 블록 지정 (일반 절차와 동일)

중립기물도 `abbrev()` 및 `getPieceItemForKind()` switch에 case를 추가합니다.
양측이 사용하는 기물이므로 **회색 계열** 블록을 권장합니다.

```java
// abbrev() (MinecraftChessManager.java)
case NEUTRAL_KNIGHTRIDER -> "nNr";

// getPieceItemForKind() — 필요한 경우
case NEUTRAL_KNIGHTRIDER ->
    Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
```

### `PieceKind` 중립 속성 메서드

| 메서드 | 설명 |
|---|---|
| `kind.isNeutral()` | `true`이면 중립기물 종류. `placeNeutralPiece()`만 사용 가능. |

### 중립기물 추가 체크리스트

- [ ] **Step A** `PieceKind` enum에 `(scriptName, score, true)` 3-인자 상수 추가
- [ ] **Step B** `chessemblyScript()` switch에 Chessembly 행마법 case 추가
- [ ] **Step C** `fromString()` switch에 파싱 case 추가
- [ ] **Step D** 배치 시 `placeNeutralPiece(kind, target)` 호출
- [ ] **Step E** `abbrev()` 및 `getPieceItemForKind()` switch에 마인크래프트 케이스 추가 (회색 계열 권장)
