# Šípky

Natívna Android hra (Kotlin + Jetpack Compose). Hádžeš šípky na terč s reálnou geometriou; ruka sa sama trasie, ťahaním prsta korigujete mierenie a zdvihnutím prsta hádžete.

## Ovládanie

- Ťahaj prstom kdekoľvek pod HUD – zameriavač sa posúva **relatívne** (prst nemusí byť na terči).
- Zameriavač sa sám trasie (ruka). Šípka dopadne presne tam, kde je zameriavač v okamihu zdvihnutia prsta.
- Hádže sa po 3 šípkach. „Znova" začne hru odznova, „Ukončiť" (alebo tlačidlo späť) vráti do menu.
- Náročnosť Ľahká / Stredná / Ťažká sa volí v menu.

## Hry

- **Tréning** – voľné hádzanie, body sa sčítavajú. Rekord: najlepšie kolo (3 šípky).
- **X01** – 301 / 501 / 701 / 1001, odpočítava sa presne na nulu (bez povinného double).
  Prestrelenie ukončí kolo a vráti skóre na začiatok kola. Rekord: najmenej šípok.
- **Okolo terča** – ciele v poradí, aktuálny cieľ je na terči zvýraznený. Rekord: najmenej šípok.
  - *Čokoľvek*: 1–20 v ľubovoľnom poli, potom 25 (platí aj bull).
  - *Násobky*: single 1–20, potom double alebo triple 1–20, potom 25 a 50.
  - *Double-Triple*: single 1–20, double 1–20, triple 1–20, potom 25 a 50.

Rekordy sa ukladajú v telefóne (SharedPreferences) pre každú hru, variant a náročnosť zvlášť.

## Build

Požiadavky: JDK 17+, Android SDK s platformou `android-37.2` (`ANDROID_HOME` alebo `local.properties`).

```bash
./gradlew test            # unit testy (geometria, trasenie, pravidlá, rekordy)
./gradlew lint
./gradlew assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```

Inštalácia do mobilu cez kábel (zapnuté ladenie cez USB):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Štruktúra

| Súbor | Účel |
|---|---|
| `game/BoardGeometry.kt` | čisté bodovanie `score(xMm, yMm)`, žiadne Android API |
| `game/Sway.kt` | trasenie ruky (2 sínusovky na os, náhodné fázy pre každú šípku) |
| `game/GameState.kt` | priebeh hry (kolá, let, rekordy), `update(dt)`, vstupy down/move/up |
| `game/GameRules.kt` | pravidlá: tréning, X01, Okolo terča |
| `game/GameType.kt`, `game/Records.kt` | druh hry z menu, rekordy |
| `game/GameConfig.kt` | všetky ladiace konštanty |
| `ui/MenuScreen.kt` | menu s výberom hry, náročnosti a rekordmi |
| `ui/GameScreen.kt` | herná obrazovka, herná slučka (`withFrameNanos`), dotyk, HUD |
| `ui/SceneRenderer.kt` | kreslenie terča, ruky, šípky a zameriavača na `Canvas` |
| `ui/GameViewModel.kt` | drží stav cez recreate aktivity |

## Ladenie obtiažnosti (`GameConfig`)

- `swayEasy` / `swayMedium` / `swayHard` – amplitúda (mm) a násobok frekvencií trasenia pre úrovne Ľahká (3 mm, 0.35×), Stredná (18 mm, 0.6×), Ťažká (35 mm, 1×)
- `defaultDifficulty` – úroveň pri spustení (Stredná)
- `swayFreqX` / `swayFreqY` / `swayWeights` – charakter trasenia
- `dragSensitivity` (1.0) – px terča na 1 px prsta
- `maxDragOffsetMm` (300) – maximálny posun zameriavača
- `flightSeconds` (0.45), `arcHeightFraction` (0.12) – let šípky
- `handReturnSeconds` (0.3), `roundSummarySeconds` (1.2) – tempo hry
- `crosshairDiameterMm` (14), `handSwayFactor`, `handDragFactor`, `stuckDartMaxTiltDeg` – vzhľad

## Identifikátor aplikácie

`applicationId = "sk.sipky.darts"` je v `app/build.gradle.kts` (jediné miesto). Po prvom nahratí na Google Play sa nedá zmeniť.
