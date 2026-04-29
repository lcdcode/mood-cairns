# Mood Cairns

A private, fully offline Android mood tracker. Log how you're feeling against
your own scales, in your own time windows, on your own device. Nothing is ever
uploaded — sync, if you want it, is your job (Syncthing works well).

## AI Declaration and Note about versions
Written with assistance from Claude Code and Opus 4.7.

NOTE: Written using some older libraries to get it working as a proof of concept writing and building on-device in Termux. These will be updated with time and SDK updated to ver 35/36 soon.

## What it does

- **Custom scales.** Five built-in scales (Happiness, Anxiety, Stress, Boredom,
  Pain), each numeric and color-tagged. Add your own; archive the ones you
  don't want.
- **Prompt windows.** Define time-of-day windows (e.g. morning 08:00–12:00,
  evening 18:00–20:00). The app fires one notification per window per day at
  a randomized time inside it. Tap the notification to jump straight to the
  log entry screen.
- **History and charts.** Browse past entries, see per-scale trends over time.
- **Encrypted backups.** Manual export writes an AES-GCM-encrypted JSON file
  to `Documents/MoodCairns/`. The encryption key is derived from your PIN with
  PBKDF2-HMAC-SHA256 (200k iterations) and a fresh per-backup salt embedded in
  the envelope, so any install — including a fresh one — can decrypt with the
  same PIN. Drop a file manager or Syncthing on that folder if you want
  off-device copies.
- **PIN + biometric lock.** PIN-gated app entry, with optional biometric
  unlock. PIN is hashed (PBKDF2, 600k iterations) before being stored.

## Privacy contract

The app declares **no network permissions**. Without `INTERNET` the process
literally cannot open a socket — phone-home is impossible regardless of what
any included library tries to do. The build enforces this:

```
./gradlew :app:verifyNoNetworkDebug
```

fails if any merged-manifest permission ever names `INTERNET`,
`ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_NETWORK_STATE`, or
`CHANGE_WIFI_STATE`. This task is wired into every `assemble*` build.

If you want backups synced off-device, point Syncthing (or any file manager
that can sync a folder) at `Documents/MoodCairns/`.

## Tech stack

- Kotlin + Jetpack Compose, Material 3
- Hilt for DI, Room for storage, WorkManager for the daily rollover task,
  AlarmManager for the actual prompt fires (WorkManager's delayed work isn't
  reliable enough under Doze)
- Vico for charts
- AndroidX Security Crypto (`EncryptedSharedPreferences`) for PIN material
- `kotlinx.serialization` for the backup JSON envelope

## Building

### Requirements

- JDK 21
- Android SDK with platform 34 and build-tools 34
- Gradle 8.10+ (the wrapper handles this)

### Configure the SDK

Create `local.properties` in the repo root pointing at your Android SDK:

```
sdk.dir=/path/to/your/android-sdk
```

### Build a debug APK

```
./gradlew :app:assembleDebug
```

Output lands at:

```
app/build/outputs/apk/debug/mood-cairns-1.0.0-debug.apk
```

This is what's used for personal-use installs — it's signed with the standard
debug keystore but installs under the real `com.moodcairns` application id.

### Build a release APK

The release build type has minification + resource shrinking enabled. To
produce a release APK you'll need a signing config; the simplest path is to
add a `signingConfigs.release { ... }` block to `app/build.gradle.kts` and
wire it into `buildTypes.release`. Without that, `assembleRelease` will
produce an unsigned APK Android won't install.

### Useful Gradle tasks

```
./gradlew :app:compileDebugKotlin       # type-check only, fast iteration
./gradlew :app:verifyNoNetworkDebug     # privacy guard
./gradlew :app:assembleDebug            # APK
```

If you're running on Termux on-device with the configuration cache enabled,
some custom tasks need `--no-configuration-cache`.

## Project layout

```
app/src/main/java/com/moodcairns/
  MainActivity.kt              # Compose host, handles deep-link from notifications
  backup/                      # Encrypted JSON export/import
  data/                        # Room entities, DAOs, repositories
  notifications/               # PromptAlarmReceiver + channels
  security/                    # PIN hashing, lock state
  settings/                    # SharedPrefs-backed user settings
  ui/                          # Compose screens (entry, history, charts, settings, backup, lock)
  widget/                      # Home-screen "Log mood" widget
  work/                        # PromptScheduler + DailyScheduleWorker
```

## License

MIT — see [LICENSE](LICENSE).
This is a personal project and I make no guarantees.
