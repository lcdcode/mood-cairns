<img src="https://github.com/lcdcode/mood-cairns/blob/main/media/ic_launcher_round_512.png" alt="Mood Cairns icon: a stacked cairn of colorful round stones topped by a smiling brown stone" height="300" width="300">

# Mood Cairns

A private, fully offline Android mood tracker. Log how you're feeling against your own scales, in your own time windows, on your own device. Nothing is ever uploaded to any cloud service and there is no tracking. Backup sync, if you want to, is your responsibility (Syncthing works well - encrypted exported backups can be found in `Documents/MoodCairns/`).

[<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/com.lcdcode.moodcairns)

## Notes about SDK and Methodology

This project was built targeting Android SDK 34 as it was developed as a proof of concept to *write, compile, and install all on-device using Termux.* I wanted the challenge of a self-contained mobile development platform.

Termux aapt2 version does not yet support SDK 35 but this is a work in progress.

## AI Declaration

Written with assistance from Claude Code and Opus 4.7.

**All code is human reviewed and approved.**

For more information, see [AI-DECLARATION.md](AI-DECLARATION.md)

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
  to `Documents/MoodCairns/`. The encryption key is derived from a backup
  passphrase you choose (minimum 8 characters) with PBKDF2-HMAC-SHA256 (600k
  iterations) and a fresh per-backup salt embedded in the envelope, so any
  install — including a fresh one — can decrypt with the same passphrase.
  Backups from older versions, which were encrypted with the device PIN, still
  import — just enter that PIN as the passphrase. Drop a file manager or
  Syncthing on that folder if you want off-device copies.
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

## Contact

The best way to get in touch with me for issues, feature requests, etc. is right here on github.
Just open an issue and I will usually respond within 3 business days.

## Tech stack

- Kotlin + Jetpack Compose, Material 3
- Hilt for DI, Room for storage, WorkManager for the daily rollover task,
  AlarmManager for the actual prompt fires (WorkManager's delayed work isn't
  reliable enough under Doze)
- Vico for charts
- AndroidX Security Crypto (`EncryptedSharedPreferences`) for PIN material
- `kotlinx.serialization` for the backup JSON envelope

## Building for yourself

### Requirements

- JDK 21
- Android SDK with platform 34+ and build-tools 34+
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
app/build/outputs/apk/debug/mood-cairns-x.x.x-debug.apk
```

This is what's used for personal-use installs — it's signed with the standard
debug keystore but installs under the real `com.lcdcode.moodcairns` application id.

**NOTE:** If using your own build, you will need to uninstall any other version first - YOU WILL LOSE ANY DATA YOU HAVE LOGGED ALREADY.

### Useful Gradle tasks

```
./gradlew :app:compileDebugKotlin       # type-check only, fast iteration
./gradlew :app:verifyNoNetworkDebug     # verify privacy guard
./gradlew :app:assembleDebug            # build Debug APK
```

## Project layout

```
app/src/main/java/com/moodcairns/
  MainActivity.kt              # Compose host, handles deep-link from notifications
  MoodApp.kt                   # hilt integration / notif setup
  backup/                      # Encrypted JSON export/import
  data/                        # Room entities, DAOs, repositories
  notifications/               # PromptAlarmReceiver + channels
  receivers/                   # boot receiver
  security/                    # PIN hashing, lock state, db key crypto
  ui/                          # Compose screens (entry, history, charts, settings, backup, lock)
  widget/                      # Home-screen "Log mood" widget
  work/                        # PromptScheduler + DailyScheduleWorker
```

## License

GPL-3.0-only — see [LICENSE](LICENSE).
