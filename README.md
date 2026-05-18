# MeloWave

Android beat-making app built with Kotlin. Create drum patterns, arrange them into full tracks, and export as WAV.

---

## Features

**Beat Editor**
- Step sequencer with 5 instrument rows (Kick, Snare, Hi-Hat, Open Hat, Clap)
- 8 or 16 steps per row, togglable per instrument
- BPM control (40–240), swing amount
- Per-track volume, pan, and mute controls
- Custom sound import (WAV, MP3, OGG, M4A, AAC) and microphone recording
- Per-sound pitch and level settings
- Reverb and EQ effects
- Export beat as WAV and share

**Arrangement Editor**
- Chain multiple saved patterns into a full arrangement
- Visual timeline with per-bar step preview
- Export full arrangement as WAV

**Projects**
- Save and load beat projects (stored as JSON)
- Each user's projects and sounds are stored separately

**Accounts**
- Local user registration and login with SQLite
- Remember me option

---

## Tech Stack

- **Language:** Kotlin
- **Min SDK:** 24 (Android 7.0) — **Target SDK:** 36
- **Audio:** `SoundPool`, `MediaCodec` / `MediaExtractor` for WAV export, `AudioRecord` for mic input
- **Storage:** JSON files for projects, SQLite for users
- **UI:** XML layouts, ConstraintLayout, RecyclerView, dark theme

---

## Building

Requires Android Studio (Hedgehog or newer) and Android SDK 36.

```bash
./gradlew assembleDebug
```

Or open the project in Android Studio and run on a device/emulator (API 24+).

---

## Project Structure

```
app/src/main/java/com/example/melow/
├── MainActivity.kt          # Home screen
├── LoginActivity.kt         # Login
├── RegisterActivity.kt      # Registration
├── SplashActivity.kt        # Splash screen
├── SecondActivity.kt        # Beat editor (step sequencer)
├── ArrangementActivity.kt   # Arrangement editor
├── ProjectsActivity.kt      # Saved projects list
├── SoundLibraryActivity.kt  # Sound picker and custom sounds
├── ProjectManager.kt        # Project save/load logic
├── CustomSoundManager.kt    # Custom sound file management
├── SoundSettingsManager.kt  # Per-sound pitch/level settings
├── BeatExporter.kt          # WAV rendering
├── ArrangementManager.kt    # Arrangement save/load
└── UserDbHelper.kt          # SQLite user database
```
