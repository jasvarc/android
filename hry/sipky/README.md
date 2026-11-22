# Šípky

Natívna Android hra (Kotlin + Jetpack Compose). Hádžeš šípky na terč s reálnou geometriou; ruka sa sama trasie, ťahaním prsta korigujete mierenie a zdvihnutím prsta hádžete.

## Ovládanie

- Ťahaj prstom kdekoľvek pod HUD – zameriavač sa posúva **relatívne** (prst nemusí byť na terči).
- Zameriavač sa sám trasie (ruka). Šípka dopadne presne tam, kde je zameriavač v okamihu zdvihnutia prsta.
- Kolo = 3 šípky, skóre sa sčítava. „Nová hra" vynuluje skóre aj kolá.

## Build

Požiadavky: JDK 17+, Android SDK s platformou `android-37.2` (`ANDROID_HOME` alebo `local.properties`).

```bash
./gradlew test            # unit testy (BoardGeometry, Sway, GameState)
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
| `game/GameState.kt` | stav hry, `update(dt)`, vstupy down/move/up |
| `game/GameConfig.kt` | všetky ladiace konštanty |
| `ui/GameScreen.kt` | Compose obrazovka, herná slučka (`withFrameNanos`), dotyk, HUD |
| `ui/SceneRenderer.kt` | kreslenie terča, ruky, šípky a zameriavača na `Canvas` |
| `ui/GameViewModel.kt` | drží stav cez recreate aktivity |

## Ladenie obtiažnosti (`GameConfig`)

- `swayAmplitudeMm` (35) – sila trasenia, hlavný parameter obtiažnosti
- `swayFreqX` / `swayFreqY` / `swayWeights` – charakter trasenia
- `dragSensitivity` (1.0) – px terča na 1 px prsta
- `maxDragOffsetMm` (300) – maximálny posun zameriavača
- `flightSeconds` (0.45), `arcHeightFraction` (0.12) – let šípky
- `handReturnSeconds` (0.3), `roundSummarySeconds` (1.2) – tempo hry
- `crosshairDiameterMm` (14), `handSwayFactor`, `handDragFactor`, `stuckDartMaxTiltDeg` – vzhľad

## Identifikátor aplikácie

`applicationId = "sk.sipky.darts"` je v `app/build.gradle.kts` (jediné miesto). Po prvom nahratí na Google Play sa nedá zmeniť.
