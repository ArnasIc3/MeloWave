# MeloWave — File Architecture

Overview of every main source file: what it owns, what it does, and its public API.

---

## Activities

### `SplashActivity.kt`
Entry point of the app. Shows the MELOWAVE logo with a scale + fade animation for ~1.6 seconds, then navigates to `LoginActivity`. Declared as the launcher activity in the manifest.

---

### `LoginActivity.kt`
Handles user authentication.

| Function | Description |
|---|---|
| `setupUI()` | Wires up email/password fields, show-password toggle, remember-me checkbox, login button, and keyboard Done action |
| `doLogin` (lambda) | Validates input, calls `UserDbHelper.login()`, saves last-used email, handles remember-me |
| `goToMain(username, userId)` | Starts `MainActivity` with username and userId extras, clears back stack |
| `showError(tv, msg)` | Shows an error label and auto-hides it after 3 seconds |
| `showDebugDialog()` | Dev-only viewer (long-press title): shows all registered users and SQLite DB path |
| `exportDbToDownloads()` | Dev helper: copies the SQLite DB file to the Downloads folder |

Reads/writes `SharedPreferences "auth"` for remember-me state, last-used email, and userId.

---

### `RegisterActivity.kt`
Standalone registration screen (same visual style as login).

| Function | Description |
|---|---|
| `doRegister` (lambda) | Validates username/email/password/confirm, calls `UserDbHelper.register()`, then immediately calls `UserDbHelper.login()` and navigates to `MainActivity` on success |
| `validate(...)` | Local input validation: username length, email format, password length and digit requirement, password match |
| `showError(tv, msg)` | Shows error with 3-second auto-hide |

Password show/hide toggle mirrors `LoginActivity`.

---

### `MainActivity.kt`
Home screen after login. Shows the username greeting and four navigation buttons.

| Action | Destination |
|---|---|
| Create New Beat | `SecondActivity` |
| My Projects | `ProjectsActivity` |
| Arrangement | `ArrangementActivity` |
| Sound Library | `SoundLibraryActivity` |

All navigation passes `userId` as an intent extra. Logo and button sections animate in on enter. Logout clears the remember-me preference and returns to `LoginActivity`.

---

### `SecondActivity.kt`
The beat editor — the core feature of the app.

| Function | Description |
|---|---|
| `setupSoundPool()` | Builds a `SoundPool` with 10 streams, loads 5 built-in sounds and any custom sounds for this user. Sets `setOnLoadCompleteListener` to dismiss the loading overlay when all 5 are ready |
| `setupUI()` | Wires up all controls: BPM ±, swing seekbar, step toggles (8/16), row labels → sound picker, mute/volume/pan per row, FX/EQ buttons, play/stop, save, export, back |
| `buildRow(row, stateArray, stepCount)` | Programmatically creates step buttons for one instrument row. Each button toggles a `BooleanArray` entry, fires haptic feedback, and animates on press |
| `toggleRow(...)` | Switches a row between 8 and 16 steps and rebuilds it |
| `startSequencer()` / `stopSequencer()` | Runs the step sequencer on a `HandlerThread`. Each tick plays the active sounds for the current step and advances the highlight |
| `showSaveDialog()` | Prompts for a name, calls `ProjectManager.saveProject()`, resets `hasUnsavedChanges` |
| `startExport()` / `enqueueExport()` | Requests notification permission if needed, then serialises the current beat to a params file and enqueues a `BeatExportWorker`. Observes the work result — on success opens the share sheet directly |
| `handleBack()` | If `hasUnsavedChanges` is true, shows "Leave without saving?" dialog before finishing |
| `loadProjectData(json)` | Deserialises a saved project JSON into the step arrays and settings |
| `showReverbDialog()` / `showEqDialog()` | In-app FX dialogs: reverb preset picker and a 5-band equaliser |

`hasUnsavedChanges` is set to `true` on any step toggle or BPM change, and reset to `false` after a successful save.

---

### `ProjectsActivity.kt`
Displays the list of saved beats for the current user.

| Function | Description |
|---|---|
| `loadProjects()` | Calls `ProjectManager.listProjects(userId)` and refreshes the RecyclerView |
| `updateEmptyState()` | Shows/hides the "No saved projects yet" empty-state view |
| `onLoad` callback | Loads the project JSON and starts `SecondActivity` with it |
| `onDelete` callback | Confirms deletion, calls `ProjectManager.deleteProject(userId, fileName)` |

Reloads on `onResume` so the list stays current after returning from the editor.

---

### `SoundLibraryActivity.kt`
Sound picker and custom sound manager.

| Function | Description |
|---|---|
| `buildSoundList()` | Combines 5 built-in sounds with custom sounds for this user into `allSounds` |
| `setupSoundPool()` | Pre-loads all sounds into a preview `SoundPool` |
| `previewSound(sound)` | Plays a short preview of the selected sound |
| `returnSound(sound)` | Sets the activity result and finishes, passing the chosen `soundResName` back to `SecondActivity` |
| `showEditDialog(sound)` | Edits display name, pitch (0.5×–2.0×), and level (0–100%) for a sound. Saves via `SoundSettingsManager.update()` |
| `confirmDelete(sound, pos)` | Confirms and deletes a custom sound file via `CustomSoundManager.deleteSound()` |
| `startRecordingDialog()` | Records from microphone using `AudioRecord`, saves raw PCM as WAV via `CustomSoundManager.saveRecording()` |
| `filePicker` launcher | Opens a system file picker for audio files, imports via `CustomSoundManager.importSound()` |

---

### `ArrangementActivity.kt`
Arrangement editor — chains saved beat patterns into a full track.

| Function | Description |
|---|---|
| `setupSoundPool()` | Loads all built-in + custom sounds into a `SoundPool` for arrangement playback |
| `setupUI()` | Wires BPM, swing, add-bar button, RecyclerView, play/stop, save, export |
| `addBar(entry)` | Loads pattern JSON via `ProjectManager`, ensures all sounds are loaded, adds bar to list |
| `showPatternPicker()` | Shows an AlertDialog with the user's saved projects; selecting one adds it as a bar |
| `exportArrangement()` / `enqueueExport()` | Serialises all bars to a params file and enqueues `BeatExportWorker`. Observes work result for direct share |
| `startSequencer()` | Plays bars in sequence on a `HandlerThread`, advances `currentBar` and `currentStep`, highlights the active bar in the RecyclerView |
| `BarSlotAdapter` (inner class) | RecyclerView adapter for bar slots. `buildStepPreview()` draws 5 rows of 4 dp coloured dots per bar showing active steps |

---

## Data & Logic

### `UserDbHelper.kt`
SQLite database for user accounts. Database name: `melowave_users.db`, version 2.

| Method | Description |
|---|---|
| `register(username, email, password)` | Validates input, hashes password with a random UUID salt (SHA-256), inserts row. Returns a sealed `RegisterResult` |
| `login(email, password)` | Looks up user by email, re-hashes the provided password with the stored salt, compares. Returns a sealed `LoginResult` with the `User` on success |
| `allUsers()` | Returns all users as a list of triples — used by the dev debug dialog |
| `hashPassword(password, salt)` | SHA-256 of `salt + password`, returned as a hex string |

---

### `ProjectManager.kt`
Saves and loads beat projects as JSON files. Each user's projects are stored in `files/projects/user_<id>/`.

| Method | Description |
|---|---|
| `saveProject(context, userId, name, bpm, rows, swing)` | Serialises the beat state to JSON and writes to `project_<timestamp>.json` |
| `listProjects(context, userId)` | Lists all `.json` files in the user's project directory, sorted by creation time |
| `loadProjectJson(context, userId, fileName)` | Returns the raw JSON string for one project file |
| `deleteProject(context, userId, fileName)` | Deletes the project file |
| `parseRows(json)` | Deserialises a project JSON into `(bpm, Map<String, RowState>, name)` |
| `parseSwing(json)` | Extracts the swing value from a project JSON |

---

### `CustomSoundManager.kt`
Manages user-uploaded and recorded audio files. Each user's files live in `files/custom_sounds/user_<id>/`.

| Method | Description |
|---|---|
| `customSoundsDir(context, userId)` | Returns (and creates) the user's custom sounds directory |
| `importSound(context, userId, uri)` | Copies an audio file from a content URI into the user's directory, sanitising the filename |
| `listSounds(context, userId)` | Returns all supported audio files as a list of `SoundItem` |
| `deleteSound(context, userId, fileName)` | Deletes a custom sound file |
| `fileForResName(context, userId, resName)` | Resolves a `custom:<filename>` resource name to a `File` object |
| `saveRecording(context, userId, pcmData, sampleRate)` | Writes raw PCM bytes as a valid WAV file and returns the filename |

Supported formats: WAV, MP3, OGG, M4A, AAC.

---

### `SoundSettingsManager.kt`
Persists per-sound pitch, level, and display name settings. Stored as `sound_settings_<userId>.json` in the app's files directory.

| Method | Description |
|---|---|
| `getAll(context, userId)` | Reads and deserialises all sound settings into a `Map<String, SoundSettings>` |
| `update(context, userId, resName, settings)` | Merges the new settings for one sound and rewrites the JSON file |

`SoundSettings` data class holds `displayName: String`, `pitch: Float` (0.5–2.0), `level: Float` (0.0–1.0).

---

### `ArrangementManager.kt`
Saves and loads the arrangement (bar list + BPM + swing) as a single `arrangement.json` file in the app's files directory.

| Method | Description |
|---|---|
| `save(context, data)` | Serialises an `ArrangementSave` (name, bpm, swing, list of `BarEntry`) to JSON |
| `load(context)` | Reads `arrangement.json` and returns an `ArrangementSave`, or null if not found |

`BarEntry` holds `patternFileName` (the JSON file in the projects folder) and `patternName` (display label).

---

### `BeatExporter.kt`
Offline WAV renderer. Mixes all instrument tracks into a stereo interleaved PCM buffer and writes a valid WAV file.

| Method | Description |
|---|---|
| `export(...)` | Async wrapper: runs `mix()` on a new `Thread`, calls `onProgress` and `onDone` callbacks |
| `exportArrangement(...)` | Same as `export()` but accepts a list of bar row maps instead of a single pattern |
| `exportSync(...)` | Synchronous version called directly by `BeatExportWorker` on its WorkManager thread |
| `writeParamsFile(...)` | Serialises all export parameters (rows, volumes, pans, muted, soundSettings) to a JSON cache file and returns it. Used to hand off data to the Worker |
| `mix(...)` *(private)* | Core rendering: decodes all unique sounds to mono PCM, applies pitch and resampling, mixes all active steps with per-channel gain and pan, normalises, writes WAV |
| `decodeToMono(...)` *(private)* | Uses `MediaExtractor` + `MediaCodec` to decode any audio format to raw 16-bit mono PCM at 44100 Hz |

---

### `BeatExportWorker.kt`
WorkManager `Worker` that runs the WAV export in the background.

| Method | Description |
|---|---|
| `doWork()` | Reads the params JSON file written by `BeatExporter.writeParamsFile()`, deserialises it, calls `BeatExporter.exportSync()`, shows a progress notification during rendering, shows a "tap to share" notification on completion. Returns the output file path in `Result.success()` output data |
| `notify(...)` *(private)* | Updates the ongoing progress notification |
| `notifyDone(file)` *(private)* | Cancels the progress notification and shows a completion notification with a `PendingIntent` that opens the system share sheet for the WAV file |

The activity that enqueued the work observes `getWorkInfoByIdLiveData()` — if the activity is still in the foreground when the export finishes, the share sheet opens immediately without needing the notification.

---

## Adapters

### `ProjectAdapter.kt`
RecyclerView adapter for `ProjectsActivity`. Binds `ProjectInfo` items to `item_project.xml`. Exposes `onLoad` and `onDelete` lambdas. `removeAt(position)` removes an item and calls `notifyItemRemoved`.

### `SoundAdapter.kt`
RecyclerView adapter for `SoundLibraryActivity`. Binds `SoundItem` entries. Shows delete button only for custom sounds (`isCustom`). Highlights the currently active sound. Exposes `onPreview`, `onSelect`, `onEdit`, `onDelete` lambdas. `updateAt(position, item)` refreshes a single item after an edit.

---

## Data Classes

| Class | File | Fields |
|---|---|---|
| `User` | `UserDbHelper.kt` | `id: Long`, `username`, `email` |
| `RowState` | `ProjectManager.kt` | `steps: BooleanArray`, `count: Int`, `soundResName: String`, `pan: Float` |
| `ProjectInfo` | `ProjectManager.kt` | `fileName`, `name`, `bpm`, `createdAt` + `formattedDate()` |
| `SoundItem` | `SoundAdapter.kt` | `name`, `category`, `resName`, `resId`, `filePath?` + `isCustom` |
| `SoundSettings` | `SoundSettingsManager.kt` | `displayName`, `pitch: Float`, `level: Float` |
| `BarEntry` | `ArrangementManager.kt` | `patternFileName`, `patternName` |
| `ArrangementSave` | `ArrangementManager.kt` | `name`, `bpm`, `swing`, `bars: List<BarEntry>` |
