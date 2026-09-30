# Burn Alarm

**English** · [简体中文](README.zh.md)

An Android alarm clock that puts your own money on the line.

When the alarm goes off you get 60 seconds to solve three mental-math problems.
Fail, and the app starts hammering your own LLM API with maxed-out `max_tokens`,
showing the running cost — `¥0.1384 and climbing` — until you solve the problems.

> ⚠️ **This spends your real money.** The charges come out of your own API account
> and cannot be reversed. Read the *Runaway guard* section before you enter a key.

---

## Screenshots

| Alarm list | New alarm | Settings |
|---|---|---|
| ![Alarm list](docs/01-alarm-list-zh.png) | ![New alarm](docs/02-alarm-edit-zh.png) | ![Settings](docs/03-settings-zh.png) |

| Ringing over the lock screen | Burning over the lock screen | Burning |
|---|---|---|
| ![Ring on lock screen](docs/04-ring-lockscreen-zh.png) | ![Burn on lock screen](docs/05-burning-lockscreen-zh.png) | ![Burning](docs/07-burning-zh.png) |

The UI ships in **English and Simplified Chinese**, switchable in-app
(Settings → Language) or from the system per-app language screen.

| Settings (English) | New alarm (English) | Ring (English) |
|---|---|---|
| ![Settings EN](docs/08-settings-en.png) | ![New alarm EN](docs/09-alarm-edit-en.png) | ![Ring EN](docs/10-ring-en.png) |

---

## How it works

```
AlarmManager.setAlarmClock()          ← survives Doze batching
        │
        ▼
AlarmReceiver (exact alarm)
        ├─ posts a full-screen-intent notification ──→ takes over the lock screen, wakes the display
        ├─ startActivity fallback                    ──→ blocked by Android 15 BAL, harmless
        └─ starts BurnService (foreground, mediaPlayback|dataSync)
                │
                ▼
        ┌──────────────────────────────────────────────┐
        │ Phase 1 · RINGING: sound + vibration + 60 s  │
        │          ↑ solve all problems → stop now      │
        └──────────────────────────────────────────────┘
                │ grace period expires, still unsolved
                ▼
        ┌──────────────────────────────────────────────┐
        │ Phase 2 · BURNING: one API call every 3 s,    │
        │ live token + cost counter                    │
        │   Alternate: roast (200 tok) ↔ waste (4096)  │
        └──────────────────────────────────────────────┘
                │
                ▼ solved, or runaway guard tripped
             everything stops
```

Key design point: **state lives in a singleton (`BurnState`), the foreground service
advances it, the Activity only renders it.** Either side can die without breaking the
other, and reopening from the notification picks the session straight back up.

---

## Download

Grab the latest `BurnAlarm-x.y.apk` from the
[**Releases**](../../releases) page and sideload it.

Requires **Android 8.0 (API 26)** or newer; targets **Android 15 (API 35)**.

Two permissions matter for it to actually work:

- **Exact alarms** — the app is an alarm clock, so it declares `USE_EXACT_ALARM`
- **Full-screen notifications** — how it takes over the lock screen

On Android 13+ the system per-app language screen also exposes English / 简体中文.

---

## Configuration

Everything lives in Settings:

| Field | Notes |
|---|---|
| Base URL | Any OpenAI-compatible endpoint. **No `/v1` needed — it is appended automatically.** e.g. `https://api.deepseek.com` |
| API Key | Stored in the app's private DataStore. Never leaves the device except to the endpoint you configure. |
| Model | e.g. `deepseek-chat` |
| Input / output price | Copy from your provider's pricing page, in CNY per million tokens. Used only to *estimate* the on-screen cost. |
| Burn mode | Pure burn / Roast / Alternate |
| Max output tokens | 256–8192. **This is the main throttle on how fast money disappears.** |
| Burn interval | One call every 1–60 s |
| Speak the roast aloud | Uses the system TTS |
| Continuous burn limit | See below |

There is a **Test connection** button, and a live estimate at the bottom:
*"At full tilt with these settings: about ¥39.32 / hour."* Look at it before you save.

### Runaway guard

**This guards against a bug causing an infinite loop. It is not here to stop you.**

Choosing "burn until solved" is a legitimate choice, so there is **no spending cap by
default**. But if the loop logic ever went wrong, it could keep burning while you sleep.
So there is a time limit, default **120 minutes**, and **setting it to `0` disables it
entirely**.

For scale: at the defaults (4096 tokens, one call every 3 s, ¥8/M output) that is roughly
**¥39/hour**. Swap in a GPT-4o-class model and it becomes **~$96/hour**. That is the
order of magnitude the guard is for.

---

## Privacy

**Your API key is stored only on this device. It is never uploaded to the developer,
and there is no analytics, tracking or telemetry of any kind — this app has no server
of its own.**

The only time it leaves the device is when burning tokens, as the credential sent to the
endpoint **you** entered. That is how API authentication works, and where it goes is
entirely your choice.

Apart from that one step, the alarms, the question generation and the answering all work
fully offline.

Corollary: a claim like "this app needs no network" would be false. **Burning tokens
inherently requires network access** — otherwise there is nothing to burn.

---

## Build from source

Prerequisites: JDK 17+, Android SDK with platform 35 and build-tools 35.0.0.

```bash
git clone git@github.com:Sauomore/Money-Burning-alarm-colok.git
cd Money-Burning-alarm-colok

# debug build
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug

# signed release build (optional)
cp keystore.properties.example keystore.properties   # then fill in your own keystore
./gradlew assembleRelease
```

Without `keystore.properties` the release build simply comes out unsigned — the build
does not fail, so a fresh clone compiles out of the box.

### Trying it without spending anything

`tools/mock_openai.py` is a tiny fake OpenAI endpoint:

```bash
python tools/mock_openai.py 8765
```

Then point Base URL at `http://10.0.2.2:8765` (that is how an emulator reaches its host),
put anything in the API key field, and hit **Ring now**. The token counter and the cost
readout move exactly as they would for real, but nothing is billed.

---

## Tech stack

- Android Gradle Plugin `8.7.3`, Gradle `8.14`
- Kotlin `2.0.21`, Jetpack Compose (BOM `2024.10.01`), Material 3
- `compileSdk` / `targetSdk` 35, `minSdk` 26
- AppCompat (only for `setApplicationLocales`), DataStore, OkHttp, kotlinx.serialization
- No Compose Navigation dependency? Yes there is — `navigation-compose` `2.8.4`

---

## Things that bit us

### 1. Background activity starts are hard-blocked on Android 15

```
E ActivityTaskManager: Background activity launch blocked! goo.gle/android-bal
    [callingUidProcState: RECEIVER]              ← exact alarms are NOT exempt
E ActivityTaskManager: Background activity launch blocked!
    [callingUidProcState: FOREGROUND_SERVICE]    ← neither are foreground services
```

**Neither exact alarms nor foreground services are exempt from BAL.** The only supported
way to take over the lock screen is a **full-screen-intent notification** — see
`AlarmReceiver.postFullScreenNotification()`. The two `startActivity` fallbacks get
silently dropped (no exception is thrown), and are kept only for more permissive ROMs.

### 2. Full-screen intents only fire when the device is genuinely locked

The system evaluates the full-screen intent at the moment the notification is **first
posted**, and requires `isKeyguardShowing == true`.

- **A device or emulator with no lock screen** never enters the locked state, so the
  full-screen intent never fires.
- **When the app is already in the foreground**, you only get a heads-up notification.
- Calling `notify()` on the same id every second does **not** re-trigger it.

Set a PIN or pattern on a real phone, or the app's bite is much weaker than intended.

### 3. Project paths must be ASCII

AGP refuses non-ASCII paths on Windows outright, so this project lives in an
ASCII directory. Do not move it under a path with Chinese characters.

### 4. Cleartext HTTP is blocked by default

Android 9+ blocks it. This app opts out via `res/xml/network_security_config.xml`,
because plenty of people will point it at a LAN Ollama / LM Studio instance.
The trade-off is that the API key travels in the clear over `http://` — Settings shows
an explicit warning when you use it, and public endpoints should be `https://`.

### 5. PowerShell scripts need a UTF-8 BOM

The bundled `build.ps1` contains non-ASCII text that Windows PowerShell 5.1 would read as
GBK and garble. It carries a UTF-8 BOM — **do not lose it when editing.**

### 6. A placeholder default in a StateFlow can eat the user's config

`AlarmViewModel.settings` **must** start as `null`, not `AppSettings()`.

With `AppSettings()` as the initial value, the Settings screen's `LaunchedEffect` treats
that synthetic default as the real config and fills the draft with it, while the actual
DataStore value is still loading asynchronously. The consequence: **open Settings and hit
back before the read finishes, and the saved API key is overwritten with an empty
default.**

The fix is `stateIn(..., SharingStarted.Eagerly, null)`, a spinner (not the form) while
`stored == null`, and an `if (loaded)` guard on the back button as a second line of
defence.

---

## Known limitations

- If the process is killed mid-burn, the burn does not resume automatically — a restarted
  `BurnService` with no `alarmId` shuts itself down rather than leaving an unstoppable
  foreground service behind.
- Some OEM ROMs block full-screen intents. Working around that needs a
  `SYSTEM_ALERT_WINDOW` overlay fallback (show a 1×1 overlay so
  `callingUidHasNonAppVisibleWindow` becomes true, then start the Activity).
  Not implemented yet.
- The displayed cost is an **estimate** based on the prices you enter. It is not a bill.

---

## Repository layout

```
.
├── app/src/main/
│   ├── java/com/dsh/burnalarm/
│   │   ├── MainActivity.kt
│   │   ├── BurnAlarmApp.kt            notification channels
│   │   ├── alarm/                     AlarmScheduler · AlarmReceiver · BootReceiver
│   │   ├── burn/                      BurnState · LlmClient · BurnService
│   │   ├── ring/RingActivity.kt       full-screen lock-screen ring + math
│   │   ├── math/MathGenerator.kt      three difficulty tiers
│   │   ├── data/                      Alarm · AppSettings · AlarmRepository
│   │   ├── i18n/AppLocale.kt          per-app language switching
│   │   └── ui/                        list · editor · settings · theme · labels
│   └── res/
│       ├── values/strings.xml         English (default)
│       ├── values-zh/strings.xml      简体中文
│       ├── xml/locales_config.xml
│       └── xml/network_security_config.xml
├── tools/mock_openai.py               fake API for zero-cost testing
├── docs/                              screenshots
├── keystore.properties.example
└── build.ps1                          one-shot build script (Windows)
```

---

## License

[MIT](LICENSE)

Use it, fork it, ship it. Just do not blame anyone but yourself for the bill.
