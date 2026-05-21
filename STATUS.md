# Privora — Project Status

Last updated: 2026-05-20

---

## Current Version

**Active branch**: `dev-v3-calibrate` — substantial WIP for the next minor release (Health feature + cycle tracker + calibration wizard expansion). Tracks `private/dev-v3-calibrate`, **not** `origin`.
**Default branch**: `main` at `ba45758` (v2.0.7 + STATUS update).
**Last public release**: **v2.0.7** (versionCode 9) — committed `a92acdc`, tagged, GitHub release published with `app-fdroid-release.apk` attached.
**Last published to Play**: v2.0.5 (versionCode 7) — v2.0.7 AAB is ready at `app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab` but not yet uploaded to Play Console.
**Repo visibility**: **public** at https://github.com/robomixes/Privora.
**License**: AGPL-3.0-or-later (commercial license retained via CLA).
**CLA enforcement**: live via [CLA Assistant](https://cla-assistant.io/).

---

## Git Remotes & Mirror Management

| Remote | URL | Visibility | Purpose |
|---|---|---|---|
| `origin` | https://github.com/robomixes/Privora | public | Releases, F-Droid/IzzyOnDroid pickup, public marketing |
| `private` | https://github.com/robomixes/privora-private | private | WIP backup mirror — `dev-v3-calibrate` lives here until ready to ship |

**State invariants** (keep these true):
- `main` and all release tags (`v2.0.5`, `v2.0.6`, `v2.0.7`) exist identically on both remotes.
- WIP branches (`dev-v3-calibrate`, future `dev-*`) live **only** on `private`.
- `dev-v3-calibrate` tracks `private/dev-v3-calibrate` — `git push` with no args goes to private.
- Public never sees a force-push or rewritten history. Force-pushes only allowed on `private` WIP branches.

**Common workflows**:

| Goal | Command |
|---|---|
| Save WIP progress (no public exposure) | `git commit && git push` (defaults to `private/<current-branch>`) |
| Promote a branch to public | `git checkout main && git merge dev-v3-calibrate && git push origin main && git push origin --tags` |
| Sync a public update down to private mirror | `git fetch origin && git push private main && git push private --tags` |
| Add a new public release tag to private | `git push private vX.Y.Z` |
| Start a new WIP branch | `git checkout -b dev-<name> && git push -u private dev-<name>` |
| List branches on each remote | `git branch -r` (shows both `origin/*` and `private/*`) |

Reason for the split: AGPL is fine to develop in private — AGPL only triggers on *distribution*. Pushing to a private repo is not distribution. Pushing to `origin/main` or releasing the APK is. So WIP can stay private until quality + translations are ready.

---

## Distribution Status

| Channel | Status | Notes |
|---|---|---|
| **GitHub Releases** | ✅ Live | https://github.com/robomixes/Privora/releases/tag/v2.0.7 — APK attached (178 MB) |
| **Google Play Store** | ⏳ AAB ready, awaiting upload | Trimmed changelog at `fastlane/metadata/android/en-US/changelogs/9.txt` (≤500 chars for "What's new") |
| **F-Droid main repo** | ❌ Rejected | MR `f-droid/fdroiddata!37677` — maintainer linsui closed: "MLKit is not allowed". Policy line, not antifeature-fixable. |
| **IzzyOnDroid (apt.izzysoft.de)** | ⏳ Recommended next | More permissive third-party F-Droid-compatible repo; standard home for ML Kit apps. |

---

## Feature Status

### Core Features (v1.0 — all DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Camera (photo + video) | DONE | `ui/camera/CaptureScreen.kt` |
| Object Detection (YOLOv8n) | DONE | `ui/camera/CameraScreen.kt` |
| Photo Editor (crop, rotate, filters) | DONE | `ui/camera/PhotoEditorScreen.kt` |
| Encrypted Vault | DONE | `security/VaultRepository.kt`, `ui/vault/VaultScreen.kt` |
| Secure Notes | DONE | `security/NoteRepository.kt`, `ui/notes/` |
| Document Scanner + OCR | DONE | `ui/scanner/ScannerScreen.kt` |
| QR Scanner + Generator | DONE | `ui/qrscanner/QrScannerScreen.kt` |
| Offline Translation (15 languages) | DONE | `ui/translate/TranslateScreen.kt` |
| Tools (Unit Converter) | DONE | `ui/tools/UnitConverterScreen.kt` |
| Backup / Restore (.paicbackup) | DONE | `security/BackupManager.kt` |
| Onboarding + Auth Mode | DONE | `ui/onboarding/OnboardingScreen.kt` |
| Settings (features, security, storage) | DONE | `ui/settings/SettingsScreen.kt` |

### Encryption & Security (v1.0 — all DONE)

| Feature | Status | Notes |
|---------|--------|-------|
| AES-256-GCM (KEK/DEK two-layer) | DONE | TEE/StrongBox backed |
| Biometric + App PIN auth | DONE | Rate limiting with escalating cooldowns |
| Emergency PIN (empty / wipe modes) | DONE | `security/DuressManager.kt` |
| EXIF stripping + face blur on share | DONE | Applied to all shared media |
| Screenshot protection (FLAG_SECURE) | DONE | Toggle in Settings |
| Auto-lock with configurable grace | DONE | `security/VaultLockManager.kt` |

### People / Contacts (v1.1 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Encrypted contacts (CRUD) | DONE | `security/ContactRepository.kt` |
| Face identity linking | DONE | `security/PrivoraDatabase.kt` (SQLCipher) |
| Self contact (system profile) | DONE | `ContactRepository.SELF_CONTACT_ID = "self"` |
| Health profile linking | DONE | Cross-links to Insights |
| Note + vault cross-references | DONE | Person filtering in notes + vault search |

### Gemma 4 On-Device AI (v1.2 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| LiteRT-LM engine (GPU-first, CPU fallback) | DONE | `bridge/GemmaRunner.kt` |
| Model download service (2.7GB) | DONE | `service/GemmaDownloadService.kt` |
| Notes AI (summarize, rewrite, extract, continue, grammar) | DONE | `bridge/GemmaPrompts.kt`, `ui/notes/NoteEditorScreen.kt` |
| Vault photo description + image Q&A + AI tags (Gemma vision) | DONE | Photo viewer 3-dot menu: Describe / Ask about this image / Generate AI tags. Results cached in `PhotoIndex.description` + `labels`, searchable via existing label search. Unblocked 2026-05-13 by adding `visionBackend = Backend.GPU()` to `EngineConfig` — was a missing API parameter, not a runtime/model bug. See `docs/vision-crash-0.11.0.md`. |
| Settings: "Show AI labels on photos" (toggle, default ON) | DONE | `ui/settings/SettingsScreen.kt` — `app_settings/show_ai_labels`. Controls description text + label chips overlay on photo only; AI menu actions stay available regardless. |
| Spinner + Toast feedback for AI actions | DONE | 3-dot button replaced by `CircularProgressIndicator` while AI is working; Toast pops with the description / tag list when call completes (visible regardless of "Show AI labels" toggle). |
| `PhotoIndex.mergeAiTags()` + `ensureRow()` helpers | DONE | `security/PhotoIndex.kt` — Gemma tags merged at score=1.0, dedup case-insensitive, capped at 12. `ensureRow()` makes both this and `setDescription()` safe for never-indexed photos (was a silent no-op on `db.update` with no row). |
| `vision_crashed` flag try/finally | DONE | `bridge/GemmaRunner.kt` — try/finally now always clears the flag (with `commit()`), so interrupted calls don't strand it forever. Prior bug: an `adb install -r` killing the JVM mid-call left vision permanently disabled. |
| Detect: per-detection "Describe with AI" (D3a) | DONE | `ui/camera/CameraScreen.kt` — small black sparkle icon at the top-right corner of the selected YOLO box. Rendered after `DetectionOverlay` so taps don't get intercepted as background-tap. Result shows in the bottom detail card under Search / Image search. Uses `GemmaPrompts.describeDetection(yoloClass)` which asks Gemma to refine YOLO's coarse class label. |
| Detect: whole-scene "Describe scene" (D3b) | DONE | `ui/camera/CameraScreen.kt` — black sparkle FAB at BottomStart (mirrors capture FAB at BottomEnd). Tap freezes the full frame, runs Gemma `describePhoto()` over the whole image, shows result card at BottomCenter. Frozen Image now covers live preview even without a selectedDetection (was a bug: live feed kept showing through). Bottom YOLO chips are hidden during freeze + horizontally scrollable when live so they don't overlap or hide the FAB. |
| Detect: read AI descriptions aloud / TTS (D10) | DONE | `ui/camera/CameraScreen.kt` — inline `TextToSpeech` engine (pattern from `TranslateScreen.kt`). Settings → AI Detection toggle "Read AI descriptions aloud" (default OFF). When ON, D3a + D3b results auto-speak in the UI language via `Locale.getDefault()`. Speaker icon next to every result text doubles as stop button (toggles to `VolumeOff` while speaking, taps call `tts.stop()`). De-Googled fallback: one-time Toast if no TTS engine / voice is installed. Pure AOSP API — F-Droid compatible. |
| Track C — Background AI tagging & descriptions | DONE | `service/GemmaIndexingManager.kt` (singleton coroutine queue, `StateFlow` progress + isRunning). Capture hook in `CaptureScreen.kt` enqueues on save when the toggle is on. Settings → AI Detection: "Auto-tag new photos with AI" toggle (default OFF) + "Process all photos with AI…" item with mode-picker dialog (Descriptions only / Tags only / Both) showing per-mode counts + dynamic ETA. Bulk pass yields to camera UI + ONNX `IndexingManager` (priority order: indexing → Gemma). Unified `pendingQueue: List<QueueEntry(id, mode)>` survives camera takeover — bulk pass dumps its remainder back, `tryDrain()` resumes when conditions clear. `IndexingManager.finally` triggers `GemmaIndexingManager.tryDrain(context)` so Gemma kicks in the moment ONNX finishes. AI description in the photo viewer moved to TopCenter (above the image) so it stops competing with the chip row at the bottom. |
| Turkish locale | DONE | `values-tr/strings.xml` (946 strings), `xml/locales_config.xml` adds `tr`, `GemmaPrompts.uiLanguageName()` maps `tr → Turkish`, in-app language picker in `SettingsScreen.kt` includes "Türkçe". Native-speaker review pending for some choices (Vault → "Kasa", AI → "YZ", Authenticator → "Kimlik doğrulayıcı", informal Sen-style throughout). |
| `PhotoIndex.indexPhoto` preserves Gemma data on re-index | DONE | `security/PhotoIndex.kt` — reads existing score≥0.99 Gemma tags before writing, switched from INSERT OR REPLACE to `ensureRow + UPDATE` so the `description` column survives. Re-index dialog warning text updated in 5 locales to mention Gemma data loss + restore path. |
| Localization for AI prompts (5 locales) | DONE | `bridge/GemmaPrompts.kt` — `describePhoto`, `generateTags`, `askAboutImage`, `describeDetection` inject "Reply in <UI language>" hint based on `Locale.getDefault()`. Ask dialog UI strings + scrollable bounded answer. Arabic tag generation falls back to English words (Gemma's structured-output reliability drops in Arabic). |
| Settings top-bar wizard icon removed | DONE | `ui/settings/SettingsScreen.kt` — `Re-run calibration wizard` row stays in Settings → Advanced; top-bar Refresh icon dropped. |
| Daily AI tips on home landing | DONE | `ui/home/HomeLandingData.kt` |

### Insights Redesign (v1.3 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Profile unification (shared chip row) | DONE | `ui/insights/ProfileFilter.kt` |
| Expenses (always self, no profile filter) | DONE | `ui/insights/ExpensesTab.kt` |
| Health (family profiles, charts, export) | DONE | `ui/insights/HealthTab.kt` |
| Medications (CRUD + linked reminders) | DONE | `ui/insights/MedicationsTab.kt` |
| Habits (streaks, calendar, export) | DONE | `ui/insights/HabitsTab.kt` |

### Reminders — Standalone Feature (v1.3 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| One-time + recurring reminders | DONE | `ui/insights/ScheduleTab.kt`, `ui/reminders/RemindersScreen.kt` |
| AlarmManager with exact alarms | DONE | `service/ReminderScheduler.kt` |
| Done / Skip / Missed tracking | DONE | `service/ReminderActionReceiver.kt`, `service/MissedSweepWorker.kt` |
| Boot persistence | DONE | `service/BootReceiver.kt` |
| Med/Habit → Reminder linking | DONE | `ui/reminders/ReminderLinker.kt`, `ui/reminders/RemindersEditor.kt` |
| Habit auto-tick on Done | DONE | Propagates to HabitLog |
| Notification privacy (VISIBILITY_SECRET) | DONE | `service/ReminderReceiver.kt` |

### Home Layout (v1.3 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Grid layout (default) | DONE | `ui/home/HomeScreen.kt` |
| Tabs layout (persistent bottom bar) | DONE | `ui/home/HomeTabsLayout.kt`, `ui/home/PrivoraBottomTabs.kt` |
| Tab bar hides with keyboard | DONE | `ui/PrivateAICameraApp.kt` |
| Lock/unlock in both top bars (PIN + biometric) | DONE | `ui/home/HomeScreen.kt`, `HomeTabsLayout.kt` |
| Today's reminders in Recent Activity | DONE | `ui/home/HomeTabsLayout.kt` |
| Tips above Recent Activity | DONE | Reordered in Tabs layout |
| Home redesign (gradient tip, mini-cards, photo stack, bold greeting) | DONE | `ui/home/HomeTabsLayout.kt` |

### AI Assistant (v1.4 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Chat surface with ✨ entry in top bar | DONE | `ui/assistant/AssistantScreen.kt` |
| Knowledge snapshot (8KB cap) | DONE | `bridge/KnowledgeSnapshot.kt` |
| 3 tools: search_notes, fetch_note, summarize_expenses | DONE | `bridge/AssistantTools.kt` |
| Markdown rendering (bold, italic, bullets, headers) | DONE | `ui/assistant/ChatBubble.kt` |
| Dynamic temperature (data vs creative) | DONE | 0.3 data / 0.7 creative |
| Persistent session (in-memory) | DONE | Singleton `AssistantSession` |
| Tappable data refs (single-topic: notes, reminders, habits, health) | DONE | `DataRef` + `RefKind` |
| Deep-link to specific notes | DONE | `notes?openNoteId=` route |
| New chat button | DONE | Top bar `+` icon |
| JSON cleanup (malformed model output) | DONE | `ParsedReply.cleanupText()` |
| Refined prompt (few-shot, personality, markdown guidance) | DONE | `bridge/AssistantPrompts.kt` |
| Deeper history (16 messages / 8 exchanges) | DONE | `bridge/AssistantPrompts.kt` |

### Market Readiness Fixes (v1.5 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Notification privacy (VISIBILITY_SECRET) | DONE | `service/ReminderReceiver.kt` |
| Share-to-Privora (ACTION_SEND) | DONE | `AndroidManifest.xml`, `MainActivity.kt`, `VaultScreen.kt`, `NotesScreen.kt` |
| Break-in / intruder alerts (Camera2) | DONE | `security/IntruderCapture.kt` |
| Calculator disguise (PIN+= unlock, duress) | DONE | `ui/disguise/CalculatorScreen.kt`, `CalculatorActivity.kt`, `DisguiseManager.kt` |

### Translation Enhancements (v1.6 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| 8s timeout on ML Kit translation | DONE | `ui/translate/TranslateScreen.kt` |
| AI grammar fix button (Gemma) | DONE | `ui/translate/TranslateScreen.kt` |
| Side-by-side Translate + AI Translate buttons | DONE | ML Kit for words, Gemma for sentences |
| Alternative translations with context notes | DONE | `getAlternativeTranslations()` |
| Keyboard dismiss on translate | DONE | `focusManager.clearFocus()` |
| Stale alternatives clear on re-translate | DONE | Tracked by `lastAltSource` |

### Vault Enhancements (v1.6 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Hidden folder (tap title N times to reveal) | DONE | `ui/vault/VaultScreen.kt` |
| Hidden folder config (tap count 3-15) | DONE | `ui/settings/SettingsScreen.kt` |
| Hidden folder suppressed during duress (no toasts) | DONE | `isDuressActive` guard |
| Move to Hidden from gallery/folder | DONE | Move dialog red "Hidden folder" option |
| Hidden folder viewer back → categories (not gallery) | DONE | `viewerFromHidden` flag |
| Vault UI polish (shadows, pill search, icon circles) | DONE | `CompactCategoryCard`, search bar |
| Smart filter icons (Duplicates, Blurry) | DONE | Leading icons on FilterChips |
| "+ New Folder" pill button | DONE | TextButton replaces lone + icon |
| Files as universal explorer (ALL vault items) | DONE | Loads all categories + folders |
| File type filter chips (All/Photos/Videos/PDF/Other) | DONE | `filesFilter` state in gallery |
| FILE media type for documents | DONE | `.file.enc` extension, `VaultMediaType.FILE` |
| PDF detection in `.file.enc` (Wi-Fi transferred PDFs) | DONE | Checks filename for `.pdf` |
| File display (icon + name + size + type badge) | DONE | PDF + FILE rendering in gallery + folder view |
| Folder multi-select (long-press → move/share/delete) | DONE | `combinedClickable` + selection action bar |
| Folder viewer back → folder (not gallery) | DONE | `viewerFromFolder` flag |
| Pinch-to-zoom image viewer (1x-5x) | DONE | `detectTransformGestures` + `graphicsLayer` |
| Double-tap zoom toggle (1x ↔ 2.5x) | DONE | `detectTapGestures(onDoubleTap)` |
| Pan when zoomed + swipe when not zoomed | DONE | Gesture mode switches on scale |
| Categories page scrollable (folders + trash reachable) | DONE | `verticalScroll` on normal view only |
| Duress leak fix: Photos/Videos/Files virtual views | DONE | Redirect to `openCategory()` in duress |
| Duress leak fix: folder counts show 0 | DONE | `if (isDuressActive) 0` |
| Translations: Photos, Videos, Files in AR/FR/ES/ZH | DONE | All 4 locales |
| All Wi-Fi received files → Received folder | DONE | Images, videos, PDFs, docs — all in one folder |

### Wi-Fi Transfer (v1.6 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| NanoHTTPD embedded server | DONE | `service/WifiTransferServer.kt` |
| QR code + URL + PIN display | DONE | `ui/vault/WifiTransferScreen.kt` |
| Browser-side AES-256-CTR encryption (pure JS) | DONE | No crypto.subtle needed, works on HTTP |
| Server-side decryption with PIN-derived key | DONE | `decryptWithPin()` |
| PIN never sent over network | DONE | Both sides derive key from PIN+salt |
| All files → Received folder | DONE | Images, videos, PDFs, docs unified |
| Configurable size limit (50MB-1GB) | DONE | Advanced Settings |
| 3 wrong PINs = server lockout | DONE | `MAX_PIN_FAILURES = 3` |
| Wi-Fi IP detection (wlan0 + fallback) | DONE | `getLocalIpAddress()` |
| 📶 icon in vault top bar | DONE | Hidden during duress |
| Fallback: multipart + PIN when encryption fails | DONE | Dual-mode upload handler |

### Public-Source Launch (v2.0.0 → 2.0.5 — DONE)

| Item | Status | Notes |
|------|--------|-------|
| **Repository public on GitHub** | DONE | https://github.com/robomixes/Privora |
| **AGPL-3.0-or-later license** | DONE | LICENSE + 107 SPDX headers |
| **CLA infrastructure** | DONE | `CLA.md` (Harmony HA-CLA-I-Lite), CLA Assistant linked + Gist hosted, all PRs auto-gated |
| **Public docs** | DONE | README rewrite, `CONTRIBUTING.md`, `SECURITY.md`, `THREAT_MODEL.md`, `CHANGELOG.md`, `MARKETING.md` |
| **Build flavors** (`fdroid` / `playstore`) | DONE | Closed Pro module can later ship via Play flavor without contaminating F-Droid build |
| **Fastlane / F-Droid metadata** | DONE | `fastlane/metadata/android/en-US/` (title, descriptions, changelogs/7.txt) + `fdroid/com.privateai.camera.yml` ready for fdroiddata MR |
| **`v2.0.5` git tag pushed** | DONE | F-Droid `UpdateCheckMode: Tags` will auto-pick up subsequent releases |
| **GitHub release published** | DONE | Changelog body attached |
| **Default branch normalised** | DONE | `main` fast-forwarded to v5 work via PR #7; `v5-dev-gemma4` retired |

### Play Store Release Polish (v2.0.0 → 2.0.5 — DONE)

| Item | Status | Notes |
|------|--------|-------|
| versionCode 1 → 7, versionName 1.0.0 → 2.0.5 | DONE | Several Play-rejection cycles resolved; AAB at `app/build/outputs/bundle/playstoreRelease/` |
| 16 KB page-size compliance (arm64-v8a + x86_64) | DONE | Bumped ONNX Runtime 1.21.0 → 1.22.0 to fix `libonnxruntime4j_jni.so` alignment |
| Removed `USE_EXACT_ALARM` (Play policy: alarm/calendar apps only) | DONE | Reminders fall back to `SCHEDULE_EXACT_ALARM` (user-grantable) |
| Removed `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` + `WAKE_LOCK` | DONE | Switched AI model download from custom foreground service to system `DownloadManager` — also removes the Play Console foreground-service-justification video requirement |
| Custom `GemmaDownloadService` deleted | DONE | Replaced with `DownloadManager`-backed `GemmaModelManager` |
| Model file migrated to `getExternalFilesDir("models")` | DONE | One-shot migration on app start moves any existing internal copy |
| AI download lifecycle fixes (stuck → recoverable) | DONE | Disable / re-enable / Delete model now properly cancels in-flight; stall detector after 60 s of no progress with Retry button |
| Cellular fallback for AI download | DONE | `setAllowedOverMetered(true)` |
| Kotlin `removeFirst()` / `removeLast()` Android 15 fix | DONE | All call sites switched to `removeAt(...)` |
| Per-feature crash-recovery flag for vision (`vision_crashed`) | DONE | Vision SDK is unstable upstream; flag prevents crash loops if user retries |
| `gemma-4-E2B-it.litertlm` model URL still HuggingFace `litert-community` | DONE | Public URL, no auth required |
| Theme picker (System / Light / Dark) | DONE | Settings → Theme |
| Dark mode adaptation: home grid + bottom tabs | DONE | Per-feature accent overlay computed at render time |
| Home-screen widgets (4 new + 1 XL): Quick Note, Reminders, AI Assistant, Quick Access XL | DONE | Dark rounded shell + accent-colored icons |
| AI download dialog skip when model already on disk | DONE | One-tap re-enable |

### Sprint 2: Assistant Upgrades + Widgets (v1.9 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| AI streaming responses (token-by-token) | DONE | `bridge/GemmaRunner.completeStreaming`, `ui/assistant/AssistantScreen.streamAndCollect` |
| Progressive JSON `text` field decoder (no flashing wrapper) | DONE | `extractJsonStringPartial`, `TextFieldMarker` regex |
| AI action proposals — 7 types with one-tap confirm | DONE | `bridge/AssistantActions.kt`, `ChatBubble.ActionCard` |
| Reminder action (one-shot, ISO timestamp) | DONE | `ProposedAction.Reminder` → `ReminderScheduler.scheduleItem` |
| Expense action (8 categories, defaults to "Other") | DONE | `ProposedAction.Expense` → `InsightsRepository.saveExpense` |
| Note action | DONE | `ProposedAction.Note` → `NoteRepository.createNote` |
| Health record action (any subset of metrics) | DONE | `ProposedAction.HealthRecord` → `saveHealthEntry` |
| Contact action (name + optional phone/email/notes) | DONE | `ProposedAction.Contact` → `ContactRepository.saveContact` |
| Medication action | DONE | `ProposedAction.MedicationAction` → `saveMedication` |
| Habit action | DONE | `ProposedAction.HabitAction` → `saveHabits` |
| Action card states: Pending / Added ✓ / Failed → Retry / Dismissed | DONE | `ActionStatus` enum + `ActionCard` composable |
| Built-in PDF viewer (no external Intent handoff) | DONE | `ui/vault/PdfViewerScreen.kt` (PdfRenderer + LazyColumn) |
| Pinch-to-zoom on PDF viewer | DONE | `detectTransformGestures` + `graphicsLayer` |
| Status-bar inset on PDF top bar | DONE | `statusBarsPadding()` |
| Duress-mode crash on Insights entry — FIXED | DONE | Composition-time I/O isolated in nested try/catch + skip on duress |
| `_tobedeleted_` filter on Insights + PasswordHint repos | DONE | Defense-in-depth against background-deletion races |
| Home-screen widget: Quick Note (1×1) | DONE | `widget/QuickNoteWidget.kt` |
| Home-screen widget: Reminders (1×1) | DONE | `widget/RemindersWidget.kt` |
| Home-screen widget: AI Assistant (1×1) | DONE | `widget/AssistantWidget.kt` |
| Shared widget layout + custom rounded background | DONE | `layout/widget_action.xml`, `drawable/widget_bg.xml` |
| `__new__` deep-link to NotesScreen for Quick Note widget | DONE | `NotesScreen.openNoteId == "__new__"` |
| 13 new strings × 5 locales | DONE | EN/AR/FR/ES/ZH |

### Security & Stability (v1.8 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Change PIN flow (current → new → confirm) | DONE | `ui/settings/ChangePinScreen.kt` |
| App PIN hardened with PBKDF2 (10K iter, 256-bit, salted) | DONE | `security/AppPinManager.kt` |
| Lazy migration: legacy plaintext PINs re-hashed on first unlock | DONE | `AppPinManager.verify()` |
| Duress-collision guard (new PIN can't equal duress PIN) | DONE | `ChangePinScreen.kt` |
| Calculator screen takes verifier lambda (no raw PIN in memory) | DONE | `CalculatorScreen.kt` |
| Calculator disguise crash on PIN + = unlock — FIXED | DONE | `LauncherDefault` alias keeps MainActivity enabled |
| Backup OOM on 380MB+ restore over populated vault — FIXED | DONE | Streaming re-encrypt, one plaintext at a time |
| 5-locale translations for Change PIN (10 keys × 5) | DONE | EN/AR/FR/ES/ZH |

### Password Hints (v1.7 — DONE)

| Feature | Status | Key Files |
|---------|--------|-----------|
| Encrypted hint vault (new top-level feature) | DONE | `security/PasswordHintRepository.kt` |
| 8 color-coded categories (Email, Social, Banking, etc.) | DONE | `HintCategory` enum |
| Favorites / starred entries pinned to top | DONE | `isFavorite` field |
| Search across service name, hints, notes | DONE | `PasswordHintRepository.search()` |
| Category filter chips | DONE | Horizontal scroll row |
| Add/Edit dialog with all fields | DONE | `ui/passwords/PasswordHintDialog.kt` |
| Password generator (SecureRandom, length slider, options) | DONE | `ui/passwords/PasswordGenerator.kt` |
| Copy with 30s auto-clear clipboard | DONE | `copyWithAutoClear()` |
| Auth gate (PIN / biometric) | DONE | Same pattern as Vault/Notes |
| Duress mode: empty list | DONE | `isDuressActive` guard |
| Home tile (🔑 purple) | DONE | `ui/home/HomeScreen.kt` |
| Settings feature toggle | DONE | `FeatureToggleManager` + `SettingsScreen` |
| Included in backup/restore automatically | DONE | `.hint.enc` files in `vault/passwords/` |
| 5-locale translations (22 keys) | DONE | EN/AR/FR/ES/ZH |

### v2.1 / dev-v3-calibrate (IN PROGRESS — private branch)

Substantial WIP committed on `private/dev-v3-calibrate`. Not yet released; not yet merged to public `main`.

| Area | What landed | Files |
|---|---|---|
| **Health top-level feature** | New "Health" route in home grid + bottom-tab. Three sub-tabs: Vitals (renamed from HealthTab), Medications (moved), Cycle (new). Shared profile filter. Mirrors Insights' auth + duress patterns. | `ui/health/HealthScreen.kt`, `ui/health/VitalsTab.kt`, `ui/health/MedicationsTab.kt` (moved from `ui/insights/`) |
| **Cycle / period tracker (v1.1)** | Monthly calendar with chevron navigation, day-of-month labels, 12-cycle forward prediction projection, today-only-when-current ring, period fills + ringed estimates, 5 canonical symptoms, first-use non-medical disclaimer. Stored as `${id}.cycle.enc` files (same DEK). | `ui/health/CycleTab.kt`, `ui/health/CycleCalendar.kt`, `ui/health/AddCycleDialog.kt`, `ui/health/CycleDisclaimerDialog.kt`, `security/InsightsRepository.kt` (CycleEntry + CRUD + predictNextPeriod) |
| **Insights cleanup** | Health and Medications cases removed from InsightsScreen — Insights now hosts only Expenses + Habits. | `ui/insights/InsightsScreen.kt` |
| **Calibration wizard (expanded)** | First-run + Settings-rerun guided flow. **15 steps**: 5 actions (Layout / Modules / Device test / Emergency PIN / AI download) + Privacy defaults (screenshot protection toggle + lock-after-inactivity radio, shared keys with Settings → Security) + 8 info pages (Vault, Health, Backup, AI, Emergency PIN, Calculator, Intruder, Authenticator) + Finish. Refresh icon in Settings top-bar re-runs the wizard. | `ui/calibrate/CalibrationWizardScreen.kt`, `ui/calibrate/WizardManager.kt` |
| **Grid header redesign** | LargeTopAppBar replaced with compact TopAppBar. Reminders bell with count badge → today's-reminders popup. Tip card upgrades to AI-generated tip when Gemma is available. Static-tip pool refreshed with 8 new AI-focused entries. | `ui/home/HomeScreen.kt`, `ui/home/HomeLandingData.kt` |
| **Camera pinch-to-zoom** | CameraX `cameraControl.setZoomRatio` wired via `detectTransformGestures`. "1.5x" overlay fades after 900ms. Photo + video. | `ui/camera/CaptureScreen.kt`, `ui/camera/CameraPreview.kt` |
| **Settings polish** | "Privora version" auto-tracks `BuildConfig.VERSION_NAME` (no more stale 1.0.0). Refresh icon in top bar re-runs the calibration wizard. | `ui/settings/SettingsScreen.kt`, all `strings.xml` (settings_privo_desc → `%1$s`) |
| **Profile-link dialog** | Fully translated (no more English fallback). Empty state offers one-tap "Open People" shortcut to add a contact. | `ui/health/HealthScreen.kt#LinkHealthProfileDialog` |
| **Strings** | ~50 new keys × 5 locales (EN/FR/ES/ZH/AR). **Arabic needs human review** for menstruation vocabulary cultural sensitivity. | All `values-*/strings.xml` |
| **Backup/Restore** | Cycle entries auto-included via existing `vault/...*.enc` recursive walk in BackupManager. No code change. Verified. | `security/BackupManager.kt` (no change) |
| **AI Assistant: bigger input + writing chips** | OutlinedTextField raised to `minLines=4` / `heightIn(min=110.dp)` / `maxLines=18`. Below the input: 8 quick-action chips that wrap typed text in a directive and send — Grammar / Rewrite / Summarize / Formal / Simpler / Translate (with language picker) / Expand / Bullets. | `ui/assistant/AssistantScreen.kt` |
| **AI Assistant: bubble action row** | Below every assistant reply: discoverable Copy / Share / Save-as-note icons. Save creates a note tagged `[assistant]`, deduplicated — once-saved replies show a green checkmark and the button disables to prevent duplicate notes from re-taps. | `ui/assistant/ChatBubble.kt`, `ui/assistant/AssistantScreen.kt` |
| **AI Assistant: longer context** | Per-message history truncation bumped 500 → 1500 chars in `formatTurn`. Multi-turn refinements on email-length text now actually have the prior reply in scope; previously the model only remembered the first paragraph. | `bridge/AssistantPrompts.kt:81` |
| **Vault: gallery scroll preserved** | `LazyListState` hoisted above the `when (page)` switch so the gallery survives the gallery → viewer → gallery round-trip. With 1500 photos the user no longer lands at the top after peeking at one. | `ui/vault/VaultScreen.kt` (galleryListState) |
| **Vault: viewer polish** | Viewer background back to black (standard photo-viewer convention). `Crossfade(100ms)` between images on swipe / tap-next so transitions are smooth instead of a hard pop. | `ui/vault/VaultScreen.kt` (VIEWER branch) |
| **Vault: trash batch fix** | New `moveToTrashBatch(photos)` in `VaultRepository` — per-item try/catch + load-and-save the encrypted index ONCE (not 73× per item). `deletePhotos` runs on `Dispatchers.IO` so the UI doesn't freeze, and surfaces honest "Moved X of N" / orphan-warning toasts when not all succeed. Fixes a silent-data-loss bug where 73 multi-deletes could fail mid-loop and leave items removed-from-view-but-not-in-trash. | `security/VaultRepository.kt` (`moveToTrashBatch`), `ui/vault/VaultScreen.kt` (`deletePhotos`) |
| **Vault: viewer delete sweeps to next** | Deleting the displayed image now navigates to the next item in the folder (or previous, if it was the last one). Only falls back to the gallery when the list is empty after delete. | `ui/vault/VaultScreen.kt` (delete dialog confirm handler) |
| **Vault: gallery loads progressively** | `openCategory` flips `page = GALLERY` immediately, then streams thumbnails 40 at a time on `Dispatchers.IO`. Previously a 1500-photo open showed the lock-styled categories page for ~3 seconds while every thumbnail decrypted. Now the gallery appears instantly with grey placeholders and rows fill in progressively. Streaming bails out if the user navigates away mid-decode. | `ui/vault/VaultScreen.kt` (`openCategory`) |
| **Source EXIF preserved on import** | New `ExifUtils.readMetadata(bytes)` reads `dateTaken / width / height / orientation / gpsLat / gpsLng` from raw bytes BEFORE `BitmapFactory.decodeByteArray` strips them. Saved as an encrypted `<id>.meta.enc` JSON sidecar next to each photo. Sidecar is carried with the photo through `moveToFolder`, `moveToTrash`, `moveToTrashBatch`, `restoreFromTrash`; deleted with `permanentDeleteFromTrash`, `emptyTrash`, `autoPurgeTrash`; auto-included in backups via the existing `.enc` recursive walk. `setLastModified(dateTaken)` on the photo file makes `listPhotos` sort correctly without decrypting any sidecars. The encrypted JPEG payload itself stays metadata-free, so sharing from the vault still leaks nothing — privacy invariant preserved by design. | `util/ExifUtils.kt`, `security/VaultRepository.kt` (`PhotoMetadata`, `saveMetadataSidecar`, `loadMetadata`), `ui/vault/WifiTransferScreen.kt`, `ui/vault/VaultScreen.kt` (3 import paths) |
| **Vault: photo details dialog surfaces source metadata** | The existing details dialog (Info icon in selection mode + viewer top bar) now shows an "Original metadata" section when the sidecar carries data — original dimensions and GPS coordinates formatted as `48.8520°N, 2.3500°E`. Lazily decrypted, cached via `remember(item.id)`. | `ui/vault/VaultScreen.kt` (details dialog), `formatGps` helper |
| **Authenticator (TOTP) — new top-level feature** | Standalone "Authenticator" feature (its own home tile + route, sibling to Vault / Notes). Pure-Kotlin RFC 6238 generator + RFC 4648 Base32 codec + `otpauth://` parser/builder, full RFC reference-vector test coverage. Per-entry encrypted storage at `vault/totp/<id>.totp.enc` — auto-handled by Duress + BackupManager via the existing `vault/...` walks (zero new code on those paths). List screen with countdown ring, tap-to-copy, hide-until-tap, deterministic issuer-initial avatars. Add/Edit screens (manual entry + Scan QR shortcut, edit reuses the same form with `existingEntry` seeding). Long-press menu: Copy / Edit / Show as QR (with reveal gate + warning) / Delete (with seed-rotation warning). Duplicate-on-add detection by secret bytes — Replace / Keep both / Cancel. In-app Settings page (Hide-by-default toggle + How-it-works explainer). Lock gate reuses `VaultLockManager` + duress PIN handling. `QrScannerScreen` now branches on `otpauth://` prefix and routes any scanned TOTP QR to the Authenticator add screen. Wired into Home grid (LockClock icon), Settings feature toggles, and the calibration wizard as info page step 13. `totp_settings` registered in `BackupManager.prefsToBackup`. | `bridge/Totp.kt`, `bridge/TotpTest.kt`, `security/TotpRepository.kt`, `security/TotpPrefs.kt`, `ui/totp/TotpListScreen.kt`, `ui/qrscanner/QrScannerScreen.kt`, `ui/PrivateAICameraApp.kt`, `ui/home/HomeScreen.kt`, `ui/settings/SettingsScreen.kt`, `ui/settings/FeatureToggleManager.kt` |
| **Backup-restore: photos no longer all collapse into "Today"** | Restored vault photos all grouped under "Today" in the gallery because (1) the backup ZIP wasn't preserving `file.lastModified()` per entry, and (2) the importer wrote files with restore-time mtime. `VaultPhoto.timestamp` reads `file.lastModified()`, so `groupPhotosByDate` produced wrong groups. Fix is layered: backup now sets `ZipEntry.time = file.lastModified()` per file; restore now applies `entry.time` to each extracted file's mtime; and a one-shot `VaultRepository.restorePhotoTimestampsFromMetadata` runs at end of import — walks every `.meta.enc` sidecar and re-applies the EXIF `dateTaken` to all matching photo / thumb / video / pdf companions, rescuing `.paicbackup` files created before this fix shipped. | `security/BackupManager.kt` (export + import), `security/VaultRepository.kt` (`restorePhotoTimestampsFromMetadata`) |
| **AI Assistant disabled after restore — fixed** | After restoring a `.paicbackup`, the Gemma model file was on disk but the Assistant button was hidden and toggling Settings off/on didn't help. Two-part bug: `gemma_settings` SharedPreferences (which holds `ai_enabled`) wasn't in `prefsToBackup`, AND a sticky `load_crashed` flag persisted from prior failed loads (or device-specific OpenCL crashes carried over from the source phone). Fix: `gemma_settings` added to `prefsToBackup`; self-heal pass at end of `importBackup` auto-enables when the model is present and clears `load_crashed` / `vision_crashed`; Settings AI toggle additionally calls `resetCrashFlag` / `resetVisionCrashFlag` when the user explicitly turns it on (treat opt-in as "retry"). | `security/BackupManager.kt`, `ui/settings/SettingsScreen.kt` |
| **Wizard: Privacy defaults step (new step 5 of 15)** | Calibration wizard gains a **Privacy defaults** step between Emergency PIN and AI Model: toggle for screenshot protection (`FLAG_SECURE`, applied immediately to the live window so the recents preview goes black on flip) + radio list for "Lock after inactivity" (Immediately / 10s / 30s / 1m / 2m / 5m). Both share storage with Settings → Security via the existing `privacy_settings` SharedPreferences keys (`block_screenshots`, `lock_grace_seconds`) — change in either place, the other reflects it. | `ui/calibrate/CalibrationWizardScreen.kt` (`StepPrivacyDefaults`), 5-locale strings |
| **Wizard: Authenticator info page (step 13 of 15)** | New info page in the wizard's info-pages tail: explains the Authenticator (2FA codes), why secrets stay local, and that they're wiped by duress along with everything else. LockClock icon, teal accent. | `ui/calibrate/CalibrationWizardScreen.kt` (`InfoPages.AUTHENTICATOR`) |
| **Ask My Documents — Phase 0 quality-gate spike** | New on-device RAG-over-vault feature, intentionally minimal so we can decide if Gemma 4 E2B is good enough on real OCR before committing to full RAG. **OCR persistence**: `ScannerScreen.saveAsPdfAndShare`'s vault path now OCRs every page at save time and writes a `<id>.ocr.enc` encrypted JSON sidecar (`{v, text, perPage}`) next to the `.pdf.enc`. **VaultRepository**: new `saveOcrSidecar / loadOcr / hasOcr` helpers; `.ocr.enc` excluded from photo listings; threaded through every lifecycle method (move / batch trash / restore / permanent delete / empty trash / auto-purge) — sidecars ride existing BackupManager + DuressManager paths automatically. **Assistant integration**: vault → Assistant deep-link sets a session-level `attachedDocId` (out-of-band, not in the user-visible message) — `runAssistantTurn` loads the OCR text and injects it as `ATTACHED DOCUMENT (id: ...): <text>` directly into the prompt, with strong grounding rules (copy numbers verbatim, refuse if not in text, hedge on noisy OCR, reply in the user's language). 4 KB doc-text budget so the model has output room left. Switching docs auto-clears the chat to prevent prior summaries biasing the next answer. The previously-shipped `summarize_document` / `ask_document` tools are now deprecated (Gemma kept truncating 13-digit doc ids), removed from the system prompt; parser remains tolerant of malformed `{"type":"<tool_name>",...}` schemas Gemma sometimes emits from training memory. **Truncated-JSON salvage** in `cleanupText` so `{"type":"answer","text":"..."` cut off mid-string still renders as plain text, no raw JSON leaks into the chat. **PDF viewer overflow** (when an OCR sidecar is present) gains: Rename / Summarize / Ask the Assistant / View extracted text / Share. **View extracted text** dialog shows the raw OCR — debugging affordance to tell whether wrong answers came from bad OCR or from Gemma hallucinating digits. | `ui/scanner/ScannerScreen.kt`, `security/VaultRepository.kt` (`.ocr.enc` lifecycle), `bridge/AssistantTools.kt` (deprecated tools + tolerant parser), `bridge/AssistantPrompts.kt`, `ui/assistant/AssistantScreen.kt` (`attachedDocId` session binding + `describeLanguage` + truncated-JSON salvage), `ui/vault/VaultScreen.kt`, `ui/vault/PdfViewerScreen.kt`, `ui/PrivateAICameraApp.kt` (`assistant?seed=...&docId=...` route) |
| **Vault: rename feature (atomic, name = unique id)** | Files in the vault can now be renamed. The user-typed name becomes the unique id everywhere — encrypted file, thumbnail (when separate), `.meta.enc` (EXIF), `.ocr.enc` (Ask My Documents), `_starred.enc` set membership, and the `VaultPhoto.id` used by all listings. `VaultRepository.renameItem` validates (no path separators, wildcards, empty), refuses on collision (no silent overwrite), preserves the encrypted-file suffix (`.pdf.enc` / `.vid.enc` / `.file.enc` / `.enc`) so listing-side type detection keeps working, returns a typed `RenameResult.{Success/InvalidName/NameAlreadyExists/Failed}`. UI: **Rename** at the top of the PDF viewer's 3-dot menu opens a TextField dialog pre-filled with the current name (`.pdf` stripped). Backup roundtrip is unchanged because renamed files are still under `vault/`. The Assistant chat now shows the renamed doc name in the seed prompt — `Summarize the document "Lease 2024".` is what the user sees in their first chat bubble, instead of the generic "Summarize this document." | `security/VaultRepository.kt` (`renameItem` + `RenameResult`), `ui/vault/PdfViewerScreen.kt` (rename dialog), `ui/vault/VaultScreen.kt` (call site + state refresh), 5-locale strings |
| **Backup screen: full module summary** | The Backup screen's "Your data" card was static (photos / videos / notes / size). Now reflects every module that ships data with the backup, hidden when count is 0. `BackupStats` extended from 4 fields to 18: photos / videos / scanned PDFs / other files / custom folders / starred / OCR-queryable docs / notes / reminders / expenses / health entries / medications / cycle entries / habits / contacts / Authenticator codes / password hints / trash. Each module's count wrapped in per-module try/catch — a single corrupt sidecar can't black-hole the whole summary screen. New `isEmpty` helper replaces the ad-hoc `photoCount + videoCount + noteCount > 0` checks at both call sites. Card also notes that app settings, feature toggles, language, layout, and module preferences travel with the backup. | `security/BackupManager.kt` (`BackupStats` + `getBackupStats`), `ui/settings/BackupScreen.kt` |
| **Ask My Documents — Phase 2: open existing PDFs to the Assistant** | Previously only docs scanned through Privora had OCR sidecars; everything imported (Wi-Fi transfer, share-to-vault, pre-existing PDFs) was invisible to Gemma. New `VaultRepository.extractOcrForPdf(photo, onProgress)` runs a hybrid extraction: try PdfBox-Android's native text-layer pull first (sub-second on Word-exported / generated PDFs), fall back to rendering each page via `PdfRenderer` and re-OCR'ing via ML Kit (multi-second per page, covers image-only PDFs). Result is written through the existing `saveOcrSidecar` so the rest of the Phase 0 pipeline picks it up unchanged. New **Extract text for the AI** entry in the PDF viewer's 3-dot when no sidecar exists; once it succeeds, the menu rotates to show Summarize / Ask the Assistant. Non-dismissible progress dialog during the OCR fallback shows "Page X of Y" with a progress bar. Returns typed `OcrExtractionResult.{Success(pageCount, charCount, viaOcr) / NoTextFound / Failed}`. | `app/build.gradle.kts` (PdfBox-Android 2.0.27.0), `security/VaultRepository.kt`, `ui/vault/PdfViewerScreen.kt`, `ui/vault/VaultScreen.kt`, 5-locale strings |
| **Ask My Documents — non-Latin OCR guard** | ML Kit Text Recognition v2 only ships a Latin model (no Arabic, Hebrew, Thai, etc.). When an Arabic paper was scanned, the recognizer returned garbage Unicode and the AI Assistant then "answered" against nonsense. Honest fix: a `looksLikeReadableLatinOcr` heuristic (≥50 chars + ≥40% Latin letters in U+0000..U+024F) gates the sidecar write at *both* call sites — the scanner save-to-vault flow AND the PDF extraction path. Non-readable output returns `NoTextFound` and the user sees "No readable text found in this PDF" instead of a confidently-wrong summary. Tesseract integration for Arabic / Hebrew / etc. is documented as a future v2.x option but not in scope now. | `security/VaultRepository.kt` (`looksLikeReadableLatinOcr`), `security/VaultRepository.kt` (`extractOcrForPdf` both passes), `ui/scanner/ScannerScreen.kt` (scanner save path) |
| **Ask My Documents — prompt + runtime fixes from real-doc testing** | After 80% easy-tier passing on initial eval, several quality issues surfaced in iteration. Fixed: doc-switch in same chat was carrying prior summary as context (now auto-clears on `attachedDocId` change); truncated-JSON salvage in `cleanupText` so partial answers render as plain text instead of leaking raw JSON; tools `summarize_document` / `ask_document` removed from system prompt (the ATTACHED DOCUMENT injection makes them redundant — Gemma was misformatting them as `{"type":"ask_document",...}` and tripping the parser); parser tolerates that misformat anyway as a safety net; **language detection** rewritten to require ≥2 uniquely-French/Spanish stop-words (excluding overlaps like "document"/"resume") with device-locale fallback, so an English UI asking about a French doc now gets an English reply; **doc Q&A defaults to temp=0.2** instead of the classifier's 0.7 — high temperature was triggering Gemma 4 E2B repetition loops where the same summary block emitted forever; **runtime repetition guard** in `streamAndCollect` cuts the stream when a 60-char tail appears 3× in the last 600 chars; **input prompt trimmed when doc is attached** — knowledge snapshot (notes/expenses/health) becomes `{}`, doc budget bumped from 3 KB → 5 KB now that the snapshot isn't competing; **streaming errors no longer silent** — partial-output cut-offs now log emit count + exception class. | `bridge/AssistantPrompts.kt`, `bridge/AssistantTools.kt`, `bridge/GemmaRunner.kt` (streaming error logging), `ui/assistant/AssistantScreen.kt` (`describeLanguage` + `streamAndCollect` repetition guard + temp override + snapshot skip), `ui/vault/PdfViewerScreen.kt` (extracted-text dialog size header), `ui/vault/VaultScreen.kt` |
| **Ask My Documents — UX polish from second real-doc pass** | After the first iteration shipped, more failure modes surfaced. Fixed: **half-sentence seed prompts** ("About the document 'scan_X', ") were getting sent as-is and Gemma was reading the docId as a literal search term, replying "I can't find that in the document" — replaced with complete defaults ("What's in this document?" / "Summarize this document.") that work whether the user edits them or not. **Attached-doc chip** at top of the chat surface ("📄 Attached: scan_X · ×") — always visible while a doc is bound to the session so the user can see what's attached on every message, not just buried in the first bubble; × detaches the doc without clearing the chat. Backed by Compose `mutableStateOf` so the chip reacts to detach instantly; binding moved out of `LaunchedEffect` into a `remember(attachedDocId)` block so the chip renders on the first frame (was flashing in late before). **OCR-language disclaimer** under the chip — "⚠️ OCR reads Latin scripts only. Arabic, Chinese, etc. content (if any) is missing." Honest user-facing surface for ML Kit's Latin-only limitation, since Gemma cannot detect missing content from the OCR output (no gap markers — ML Kit drops non-Latin chars before we ever see them). **Size header in View extracted text dialog** ("4287 characters total — only the first 5120 reach the Assistant") so the user can see at a glance whether the cap is biting on their typical docs. | `ui/assistant/AssistantScreen.kt` (`AssistantSession.attachedDocId` becomes observable, attached-doc chip + disclaimer composables, synchronous session binding), `ui/vault/VaultScreen.kt` (seed-prompt call sites updated, extracted-text dialog size header), `values*/strings.xml` (5 locales, simplified seed strings + chip strings + disclaimer) |
| **Track C — Background AI tagging & descriptions for vault photos** | Opt-in Gemma auto-tagging of new captures + a manual bulk pass for the existing library. New `GemmaIndexingManager` singleton coroutine queue (`pendingQueue: List<QueueEntry(id, mode)>` survives camera takeover and process death via `gemma_settings` CSV persistence; `shouldYield()` predicate covers camera UI / ONNX `IndexingManager` running / AI unavailable). Per-photo: skip if already cached → load+JPEG → `GemmaPrompts.describePhoto()` → `setDescription` → `GemmaPrompts.generateTags()` → `mergeAiTags` (case-insensitive dedup with score-1.0 marker so re-index preserves Gemma data). Settings → AI Detection: "Auto-tag new photos with AI" toggle (default OFF) + "Process all photos with AI…" mode/limit picker (DESCRIPTION_ONLY / TAGS_ONLY / BOTH × 25 / 100 / 500 / All, with per-mode pending counts and ETA). CaptureScreen `DisposableEffect` pauses Gemma on enter, resumes on dispose. `IndexingManager.finally` drains the Gemma queue once the ONNX indexer finishes. | `service/GemmaIndexingManager.kt` (new), `service/IndexingManager.kt` (drain-on-finally), `ui/camera/CaptureScreen.kt`, `ui/settings/SettingsScreen.kt`, `security/PhotoIndex.kt` (Gemma-aware `mergeAiTags` + `indexPhoto` preservation), 5-locale strings |
| **Turkish locale** | New `values-tr/strings.xml` (946 strings, full parity with `values/`). `xml/locales_config.xml` adds `<locale android:name="tr" />`. `GemmaPrompts.uiLanguageName()` maps `"tr"` → `"Turkish"` so vision prompts inject "Reply in Turkish". Settings language picker shows "Türkçe". Native-speaker review pending for some choices (Vault → "Kasa", AI → "YZ", Authenticator → "Kimlik doğrulayıcı"). | `values-tr/strings.xml`, `xml/locales_config.xml`, `bridge/GemmaPrompts.kt`, `ui/settings/SettingsScreen.kt` |
| **Detect AI (D3a + D3b + D10) — Gemma vision in the Detect module** | D3a: per-detection sparkle icon at the top-right of selected YOLO box; tap → `GemmaRunner.describeImage` + `GemmaPrompts.describeDetection(yoloClass)`; result in the bottom detail card. D3b: black sparkle FAB at BottomStart (mirrors capture FAB); tap → freezes whole frame + `describePhoto()` over the entire image; result card at BottomCenter; bottom YOLO chips hidden when frozen + horizontally scrollable when live. D10: optional TTS — Settings toggle "Read AI descriptions aloud" (off by default) auto-speaks D3a/D3b descriptions via Android's `TextToSpeech` (AOSP API, F-Droid clean); speaker icon always visible for manual replay; tap during playback calls `tts.stop()`; one-time Toast on de-Googled devices missing a TTS engine. | `ui/camera/CameraScreen.kt`, `ui/settings/SettingsScreen.kt`, 5-locale strings |
| **Track E — Settings reorganization (Essentials + collapsible sections)** | Settings had grown to 2,200+ LOC, 9 sections, 60+ items. First-time users couldn't find common controls without scrolling past technical knobs. New **Essentials** section pinned at the top (Layout selector / Performance tier / Theme / Language / Show AI labels / Auto-tag / Screenshot Protection / Screen lock), always expanded, no chevron. All 8 other top-level sections (Home Features / AI Detection / Device / Camera / Security / Storage / Privacy / About) wrapped in a new `CollapsibleSectionHeader` — body hidden until the user taps. Per-section expand state persisted to `SharedPreferences("settings_ui_state")` via new `rememberSectionExpansion` helper. Non-blank search query force-expands matching sections so results aren't hidden. Advanced section retains its existing PIN/biometric auth gate unchanged (`advancedUnlocked` state stays per-composition, never persisted as expanded). "Process all photos with AI…" + dialog moved from AI Detection to Advanced (heavy operation belongs behind auth gate). Hoisted Theme + Language dialog state to SettingsScreen top so Essentials triggers the same picker without duplicating the dialog; extracted `ScreenshotProtectionSetting` into a reusable composable so Essentials and Security share the same prefs-backed toggle. | `ui/settings/SettingsScreen.kt` (CollapsibleSectionHeader, rememberSectionExpansion, Essentials section, 8 sections wrapped, dialog hoist, ScreenshotProtectionSetting helper), 6-locale strings (Essentials, screenshot-disabled label) |
| **Track F — People-aware photo search ("Anas with dogs")** | Wires the existing face-group, ONNX-label, and Gemma-description datasets together. New `PhotoIndex.searchByPersonAndTags(contactRepo, rawText, limit)` + `PersonAwareSearchResult` data class with greedy 3→2→1-token person-name match against face identities + contact names (multi-word names like "John Smith"), residual tokens intersected via per-token AND (`multiTokenAnd`) with cheap plural→singular fallback ("cars" → "car"), date phrase detection (`today`, `yesterday`, `this/last week`, `this/last month`, `this/last year`, `last <weekday>`, ISO `YYYY-MM-DD`). Two surfaces: **Vault search box** — calls the new helper; renders a removable "Person: Anas ×" InputChip above the grid when a name is detected; clearing the chip strips just the person token. **AI Assistant `search_photos` tool** — accepts free-form queries blending person/topic/date, returns JSON with `total`, `detectedPerson`, `detectedDate`, and up to 6 base64-encoded JPEG thumbnails. Lean tool-result variant (no base64 bytes) passed to the second Gemma call so context budget isn't blown. `ChatMessage.Assistant.photoThumbs` field + `ChatBubble` renders a horizontal 96 dp `LazyRow` under the text; tap a thumb → new `vault?openPhotoId={id}` route → `LaunchedEffect` finds the photo across categories + folders and calls `openViewer` directly; Back from a deep-link viewer pops the Vault destination so the user lands back in the Assistant chat (new `viewerFromDeepLink` flag honored by `BackHandler` + the in-viewer Back arrow). SYSTEM prompt strengthened with mandatory tool-call examples ("show me cars" / "do I have any beach photos" / "photos of Anas with dogs" / "show me last saturday photos"). | `security/PhotoIndex.kt` (searchByPersonAndTags + multiTokenAnd + plural fallback), `ui/vault/VaultScreen.kt` (search box + Person chip + openPhotoId deep-link LaunchedEffect + back-handling), `bridge/AssistantTools.kt` (searchPhotos + date parser + folder-walk), `bridge/AssistantPrompts.kt` (SYSTEM prompt: triggers + date examples), `ui/assistant/AssistantScreen.kt` (tool dispatch + lean tool result + thumb decode + onPhotoClick), `ui/assistant/ChatBubble.kt` (PhotoThumb data class + thumb strip), `ui/PrivateAICameraApp.kt` (vault?openPhotoId route), 6-locale strings (Person chip) |
| **Bug fixes from Track C / E / F testing** | **Bulk AI pending count never decreased** — `PhotoIndex.mergeAiTags` was case-insensitive-dropping Gemma tags that overlapped with ONNX labels, so no score-1.0 marker was ever written and `countPending` kept re-selecting the same photos forever. Fix: BUMP existing matches to score 1.0 (preserves the existing tag string, just stamps it as Gemma-blessed). **Folder count doubled after EXIF imports** — `FolderManager.countItems` was counting `.meta.enc` sidecars as separate items. Fix: mirror `VaultRepository.listFolderItems`'s canonical filter (also exclude `.meta.enc` / `.ocr.enc`). **Stale `vision_crashed=true` permanently disabling AI** — flag persisted across sessions with no auto-clear. Fix: `GemmaRunner.clearStaleCrashFlagsOnUpgrade` runs once at every `MainActivity.onCreate` and clears the flag on **either** versionCode bump **or** age > 10 min (timestamp `vision_crashed_at` stamped with the flag arm). Both retain the in-session loop protection. **Backup DB never bundled** — `BackupManager` was using `context.getDatabasePath("privora.db")` (Android default `databases/` dir), but PrivoraDatabase actually lives at `<filesDir>/vault/privora.db`. The DB file was therefore never included in the backup zip. Users who ran Process All AI then restored were silently losing every Gemma description + AI tag. Fix: both export and import paths now point at `<filesDir>/vault/privora.db`. **Vault import folder-target race** — 2000-photo bulk imports were flipping to CAMERA mid-stream because `folderDir` was re-evaluated inside the per-iteration IO loop. Fix: capture page + currentFolder ONCE before `withContext(Dispatchers.IO)`. **Vault crash on open** — earlier round eagerly built `ContactRepository` at composition, which opened the SQLCipher DB before `crypto.initialize()` had run. Fix: lazy. | `security/PhotoIndex.kt` (mergeAiTags bump), `security/FolderManager.kt` (countItems), `bridge/GemmaRunner.kt` (clearStaleCrashFlagsOnUpgrade + vision_crashed_at), `MainActivity.kt` (call on every launch), `security/BackupManager.kt` (DB path), `ui/vault/VaultScreen.kt` (folderDir snapshot, lazy contactRepo) |
| **Bug-fix bundle (commit f471f66)** | **Photo editor save didn't refresh the gallery thumbnail** — `replacePhoto` overwrote the encrypted thumb on disk but the in-memory `thumbnails` map kept the pre-edit bitmap; `onDone` now reloads both `thumbnails` and `searchThumbnails` for the edited id. **Vault Back lost context from face-group / search / smart-filter views** — BackHandler checked `selectedFaceGroup != null` before VIEWER so the first Back silently cleared the flag, then the second went to an empty Gallery, then the third to Categories. Reordered so VIEWER wins first; new `viewerFromSearch` flag set at face/search/smart-filter tap call sites; both the BackHandler and the in-viewer Back arrow now route to CATEGORIES (where the search/face/smart state flags still paint the right UI). **Vault search "car" returned "card" / "bank card"** — scoring in `searchByLabel` used raw `.contains()` substring matching. Now tokenizes labels on `_` / `-` / space and matches per-token with a 1-char singular/plural slack. **Detect → Lens rename** (strings only, 6 locales). **Number hallucination in Assistant** — `search_photos` and `summarize_expenses` followups had Gemma mis-copying digits from the tool result JSON (logged "total:87" → "found 7 photos"). Fix: skip the second Gemma pass entirely for both tools; new `buildSearchPhotosSummary` + `buildExpensesSummary` host helpers compose the bubble text directly from the tool JSON. Numbers copied verbatim — no LLM in the loop for digit handling. 12 new strings × 6 locales. **Noise-token filter** in `AssistantTools.searchPhotos` strips `photo/photos/picture/show/find/of/the/with/and/me/my/...` from the model's query before the per-token AND search ("girl photos" → "girl"). | `ui/vault/VaultScreen.kt` (thumb-refresh in editor onDone, BackHandler reorder, `viewerFromSearch`, in-viewer Back arrow), `security/PhotoIndex.kt` (per-token label match), `bridge/AssistantPrompts.kt`, `bridge/AssistantTools.kt` (`stripNoiseTokens`), `ui/assistant/AssistantScreen.kt` (`buildSearchPhotosSummary`, `buildExpensesSummary`, route both branches through host-built summary), 6-locale strings |
| **Vault sort menu + edit preserves creation date** (commit 6889da4) | Editing a photo (rotate / crop / filter / sticker) no longer bumps it to the top of every gallery / smart view / search result. `VaultRepository.replacePhoto` now captures `file.lastModified()` before the overwrite and restores it after — and that mtime is the field every list path reads via `VaultPhoto.timestamp`, so the single fix covers all Vault listings. Gallery 3-dot menu gains four **Sort by** entries: Newest first (creation), Oldest first (creation), Recently edited first, Least recently edited first. Choice is persisted in `app_settings/vault_sort_mode` and applied globally — category gallery, custom folder, face-group results, search results, smart filters (Blurry / Duplicates). `PhotoIndex` gains an `updated_at` column (ALTER TABLE migration) plus `markUpdated(id)` + `getUpdatedTimes(ids)` batch APIs; the UPDATED_* sort modes read this map, falling back to creation timestamp for never-edited photos. `PhotoEditorScreen` gains an `onSaved((id) -> Unit)` callback the vault wires to `photoIndex.markUpdated(id)`. New `SortMode` enum + `rememberSectionExpansion`-style helpers, `LaunchedEffect` to lazily fetch updated times only when UPDATED_* is active, centralized `sortPhotos(list)` applied at every assembly site. **Resource-compile fix surfaced en route:** French `d'abord` apostrophe needed to be `d\'abord` so AAPT wouldn't fail silently and ship a stale APK. 4 new strings × 6 locales. | `security/VaultRepository.kt` (`replacePhoto` mtime preserve), `security/PhotoIndex.kt` (updated_at APIs), `security/PrivoraDatabase.kt` (updated_at column + ALTER TABLE migration), `ui/camera/PhotoEditorScreen.kt` (`onSaved` callback), `ui/vault/VaultScreen.kt` (SortMode enum + state + helpers + 3-dot menu items + apply at every list site) |
| **Smart Scanner (D1, commit a4fb1a9)** | After the document scanner saves a PDF AND the user has the new Settings → AI Detection → "Smart scan" toggle on AND Gemma is loaded, fire a single vision pass on the first page bitmap and present a Material 3 `ModalBottomSheet` with the parsed suggestion. **`bridge/ScannerAi.kt`** new module: `analyze(context, bitmap, existingFolders)` runs Gemma with a JSON-instructing prompt and parses the result into a `Suggestion(type, title, folder, fields)`. Numbers + names are NEVER round-tripped through a second LLM pass — same anti-hallucination pattern Track F's host-built summaries use. **`bridge/GemmaPrompts.smartScanAnalyze(existingFolders)`** instructs the model to pick a `type` from a fixed English enum (receipt / business_card / id / invoice / recipe / handwritten_note / generic), suggest a localized `title` and `folder`, and emit a `fields` object whose keys depend on type. Explicit "copy numbers character-by-character" + "omit illegible fields" guardrails. **`ui/scanner/SmartScanSheet.kt`** new file: detected-type chip, editable filename (pre-filled), destination picker (Material 3 radio-list dialog with "Scan album" as default, plus AI suggestion + each existing folder + "Create new folder…" with inline text input), extracted-fields key/value list (read-only for v1), Skip / Apply. ScannerScreen hooks post-PDF-save (after OCR sidecar pass) to run analyze on the first page and show the sheet. Apply: `vault.renameItem(photo, title)` (preserves the `.pdf.enc` suffix + carries the `.ocr.enc` / `.meta.enc` sidecars) + optional `vault.moveToFolder`. **Bug fixes that surfaced**: `renameItem`'s sanitize regex char class was `[/\\:*?"<>| ]` — the SPACE inside the bracket eat spaces in user filenames so "Receipt 2024" landed as "Receipt2024". Fixed to `[/\\:*?"<>|]`. Smart Scanner stopped pre-appending `.pdf` (`renameItem` does that automatically). Skip + Apply buttons share the Row 50/50 via `weight(1f)` instead of fighting over `fillMaxWidth()`. ~30 new strings × 6 locales. | `bridge/ScannerAi.kt`, `bridge/GemmaPrompts.kt` (`smartScanAnalyze`), `ui/scanner/SmartScanSheet.kt`, `ui/scanner/ScannerScreen.kt` (hook + apply path), `ui/settings/SettingsScreen.kt` (toggle), `security/VaultRepository.kt` (regex space fix) |
| **AI Assistant image attach (D2, post-Smart-Scanner)** | New attach surface on the Assistant input row. Tap the **+** icon → dropdown menu: **From device gallery** (system `PickVisualMedia`, no storage permission) or **From Privora vault** (new `VaultPhotoPickerSheet` bottom sheet). Vault picker lists photos AND PDFs across categories + custom folders, with a horizontally-scrolling **folder filter chip row** at the top (All / Categories / each custom folder). PDF tiles show a "PDF" badge top-left + filename strip at the bottom. Tap a photo → decrypt to a temp JPEG in cache → set as `AssistantSession.attachedImageUri`. Tap a PDF → check `vault.hasOcr(photo)`; if missing, run `vault.extractOcrForPdf` inline (PdfBox native pull → ML Kit OCR fallback, with a "Preparing…" spinner) so imported PDFs (Wi-Fi transfer, share-to-vault) just work without the user having to find the viewer's "Extract text" action first. On success, set `AssistantSession.attachedDocId` and the existing "Ask My Documents" plumbing handles OCR injection. If extraction returns NoTextFound (scanned non-Latin script — ML Kit Latin-only) the picker surfaces a one-line toast instead of attaching an unusable doc. Attached image renders as a 56dp preview row above the input with detach ×; the user message bubble now persists the thumbnail above the question text (new `image: Bitmap?` field on `ChatMessage.User`) so chat history shows which photo each turn referred to. Image send bypasses `runAssistantTurn` and calls `GemmaRunner.describeImage` directly with the user's text as the prompt (or a localized "Describe this image." default). **Quick-action chips** now enabled when ANY of text / image / doc is attached — with no text, the directive becomes the prompt verbatim ("Summarize this document."). **Input redesign (ChatGPT-style)**: replaced the 110dp `OutlinedTextField` block with a rounded `Surface` (24dp radius, surface-variant background) containing a leading **+** attach button, a `BasicTextField` that's 1 line at rest and grows up to 6 lines, and a trailing 40dp filled send circle (primary color when enabled, surface-variant when not). | `ui/assistant/AssistantScreen.kt` (image-attach launcher + state + send-path branch + input pill + chip-enable rule), `ui/assistant/VaultPhotoPickerSheet.kt` (new file: folder filter chips + photo + PDF grid + inline OCR extraction on PDF tap), `ui/assistant/ChatBubble.kt` (`image: Bitmap?` field + thumbnail-above-text layout in user side), 6-locale strings (~20 new keys) |
| **Assistant: 4-way attach + camera + zoom + locked-mode + Stop fix** (commit `4ba53eb`, 2026-05-19) | The `+` menu split into 4 distinct options (Photo from device, PDF from device, Photo from vault, PDF from vault) plus a 5th **Take photo** entry that opens the system camera via `TakePicture()`. New full-size thumbnail viewer dialog (tap any thumb — attached image preview, sent user image in history, or search-result thumb — to view full-screen; long-press search thumbs still opens Vault). Device-PDF picker: copies the picked PDF to cache, runs `VaultRepository.extractOcrTextFromPdfFile` inline, stashes the OCR text in `AssistantSession.attachedDocText`. **Locked-vault Assistant access** (Settings → AI Detection → "Allow Assistant without unlocking vault", default OFF): when on, the Assistant tile is reachable from Home even with the vault locked; chat runs in text-only mode with `KnowledgeSnapshot.empty()`, vault picker greyed, tool calls AND action proposals (Add expense / Save note / etc.) both blocked client-side with a friendly "Tap Unlock above" reply. Reused `VaultPinDialog` from HomeScreen inline. Stale "vault is locked" assistant replies are scrubbed from history once unlocked so Gemma E2B doesn't parrot them. **Stop button** for in-flight turns — replaces the send-button glyph with a red square while `thinking == true`; tap cancels the coroutine and crucially in `GemmaRunner.completeStreaming` switches to `channelFlow` with the upstream `sendMessageAsync.collect` wrapped in `withContext(NonCancellable)` so the LiteRT-LM native worker thread runs to natural end (it's the SIGSEGV-source on cancellation — tombstone showed Thread-267 fault addr 0x0 at +0x4c9060 in `liblitertlm_jni.so`). Vision path skips external `cancelInflight` entirely because synchronous `sendMessage` can't be safely closed mid-call. Cancelling state holds Send disabled (spinner shown in send-button slot) until the old job's finally fully runs. | `ui/assistant/AssistantScreen.kt` (4-way menu + camera capture launcher + zoom dialog + locked banner + Pin dialog + Stop button + cancelling gate + cancelRequested flag), `ui/assistant/VaultPhotoPickerSheet.kt` (mediaTypeFilter param), `bridge/GemmaRunner.kt` (channelFlow + NonCancellable drain in completeStreaming, async cancelInflight helper, NaN-safe handling), `bridge/KnowledgeSnapshot.kt` (empty() for locked mode), `ui/vault/PdfViewerScreen.kt` (onDelete callback + Delete menu item), `ui/vault/VaultScreen.kt` (Delete in photo viewer 3-dot, pdfTempFile cleanup on delete), `ui/home/HomeScreen.kt` (Assistant tile gated on unlocked-access pref), `ui/settings/SettingsScreen.kt` (assistant_unlocked_access toggle + helper), 6-locale strings |

**Pending before public ship**:
- Real-device testing of cycle tracker across multiple cycles
- Arabic translation review for menstruation vocabulary
- **Ask My Documents — decision-fork (preliminary signal already in)**: informal testing showed ~80% on easy-tier extractive questions across a few real docs; qualitative gap vs big cloud models is the model-size ceiling, not fixable in Phase 1. Phase 2 (existing-PDF text extraction) shipped. Remaining question is whether Phase 1 (RAG) is worth 4-6 weeks given that long-doc handling is the only thing it solves and short docs already work. Lean: defer Phase 1 until usage data justifies it.
- ~~**Arabic / non-Latin OCR**~~ — **RESOLVED 2026-05-20 via Track A1.3**. Tesseract 5 replaced ML Kit's Latin-only recogniser; Settings → OCR languages picker lets users download Arabic / Hebrew / CJK / Devanagari / Cyrillic / Thai etc. on demand. The `looksLikeReadableLatinOcr` heuristic is gone; OCR sidecar is written for any non-empty Tesseract output.
- TOTP authenticator real-world end-to-end test against an actual 2FA-enabled service (GitHub or similar)
- Version bump (likely 2.1.0)
- Updated changelog + AAB build
- Merge to `main`, tag, push to `origin`, GitHub release, Play upload

---

## Known Issues / Limitations

| Issue | Impact | Status |
|-------|--------|--------|
| ~~Gemma 4 vision still crashes~~ | RESOLVED 2026-05-13 | Root cause: missing `visionBackend` parameter in `EngineConfig`. Fix: one parameter in `GemmaRunner.load()`. Verified on Pixel 9a / Tensor G4 / Android 16 with LiteRT-LM 0.11.0 + April-2026 gemma-4-E2B-it model. |
| Intruder capture device-dependent | Camera2 headless may fail on some devices | Plain JPEG fallback |
| Wi-Fi transfer key derivation is custom mixing | Not PBKDF2, uses 10K-round iterative mix | Acceptable for ephemeral transfers |

---

## What's Next

Two named workstreams sit between now and v2.1.0 — Track A (ML Kit removal, F-Droid eligibility) and Track B (Voice I/O for the AI Assistant). They don't block each other and can land in either order. Combined view in [`~/.claude/plans/starry-orbiting-galaxy.md`](~/.claude/plans/starry-orbiting-galaxy.md).

### Track A — ML Kit removal, F-Droid main eligibility (COMPLETE — 5/5 deps gone as of 2026-05-20)

Approved plan in [`~/.claude/plans/starry-orbiting-galaxy.md`](~/.claude/plans/starry-orbiting-galaxy.md). The app already has `playstore` / `fdroid` build flavors at [`app/build.gradle.kts`](app/build.gradle.kts); we're removing ML Kit from BOTH flavors phase by phase. Only translation will stay flavor-gated.

| Phase | What | Status | Commit |
|---|---|---|---|
| **1.1** | Barcode/QR → ZXing — new [`BarcodeType.kt`](app/src/main/java/com/privateai/camera/ui/qrscanner/BarcodeType.kt) (int constants matching legacy ML Kit `Barcode.TYPE_*` for on-disk QR-history compat); [`QrScannerScreen.kt`](app/src/main/java/com/privateai/camera/ui/qrscanner/QrScannerScreen.kt) analyzer now feeds the YUV Y plane through ZXing's `MultiFormatReader` + `HybridBinarizer`; rotation-aware `buildLuminanceSource` helper. TOTP otpauth:// shortcut preserved verbatim. | ✅ DONE 2026-05-19 | `cc6e534` (merge of `feature/mlkit-zxing`) |
| **1.2** | Face detection → ONNX **YuNet 2023mar** (~234 KB, force-added to git despite `*.onnx` rule). New [`FaceDetector.kt`](app/src/main/java/com/privateai/camera/bridge/FaceDetector.kt) wraps the 12-tensor (cls/obj/bbox/kps × stride-8/16/32) decoder; sigmoid baked into graph, score = sqrt(cls × obj); NaN guard for non-positive products (was the actual root cause of 1500-2500 ghost anchors before fix). [`FaceDetectorHolder.kt`](app/src/main/java/com/privateai/camera/bridge/FaceDetectorHolder.kt) singleton amortises ONNX session init across [`FaceBlur`](app/src/main/java/com/privateai/camera/util/FaceBlur.kt), [`FaceEmbedder`](app/src/main/java/com/privateai/camera/bridge/FaceEmbedder.kt), [`CaptureScreen`](app/src/main/java/com/privateai/camera/ui/camera/CaptureScreen.kt) live count, [`ContactsScreen`](app/src/main/java/com/privateai/camera/ui/contacts/ContactsScreen.kt) photo crop. Also added Settings → Camera → "Reset face groups" (wipes `face_identities`/`face_exclusions`/`face_unlinked` while keeping embeddings + AI tags) and date-grouped Today/Yesterday/weekday headers in the Faces gallery. | ✅ DONE 2026-05-20 | `74dbc30` (merge of `feature/mlkit-onnx-face`) |
| **1.3** | OCR → **Tesseract 5** via `tesseract4android-openmp:4.9.0` (JitPack — Maven Central doesn't carry it). New [`TesseractDataManager.kt`](app/src/main/java/com/privateai/camera/service/TesseractDataManager.kt) lazy-downloads `.traineddata` from `tesseract-ocr/tessdata_fast` (atomic `.part` rename); [`TesseractRecognizer.kt`](app/src/main/java/com/privateai/camera/bridge/TesseractRecognizer.kt) wraps `TessBaseAPI` (mutex-serialised, dispatchers.IO); new [`OcrLanguagesScreen.kt`](app/src/main/java/com/privateai/camera/ui/settings/OcrLanguagesScreen.kt) gives the user a per-language Download / Delete picker (18 languages covering eng / fra / spa / deu / ita / por / nld / ara / heb / rus / chi_sim / chi_tra / jpn / kor / tur / hin / tha / vie). **Unlocks Arabic / Hebrew / CJK / Devanagari / Cyrillic / Thai OCR** — the audit also flagged a 4th call site (TranslateScreen's photo-translate overlay) that the initial 3-site count missed. The `looksLikeReadableLatinOcr` Latin-only sanity gate is dropped (Tesseract handles whatever the user installs). | ✅ DONE 2026-05-20 | `0bc05fb` (merge of `feature/mlkit-tesseract`) |
| **2** | Document scanner → **CameraX + manual corner-drag** (perspective transform via Android `Matrix.setPolyToPoly`, no OpenCV). New [`ScannerCaptureScreen.kt`](app/src/main/java/com/privateai/camera/ui/scanner/ScannerCaptureScreen.kt) (live preview + tap-to-focus + flash + gallery import + shutter + multi-page badge) and [`PerspectiveTransform.kt`](app/src/main/java/com/privateai/camera/util/PerspectiveTransform.kt). **Known regression vs ML Kit**: no auto-detection of document corners — the user drags 4 yellow handles to outline the document on every page. Tracked as a follow-up "A2.5 — auto document corners" (likely a small TFLite quad-corner model in `assets/`, ~2-3 MB). OpenCV-Android was evaluated but the Maven distribution had rotted (quickbirdstudios gone, official AAR isn't published to any registry, JitPack mirrors auth-walled) — not worth a ~30 MB vendored AAR + native libs in git for the autodetect feature alone. CAPTURE_MODE_MAXIMIZE_QUALITY + 2592×3456 target resolution + tap-to-focus overlay (PreviewView's touch handling was swallowing the Compose `pointerInput`, fixed by moving the gesture detector to an unpadded overlay Box that covers the same area). | ✅ DONE 2026-05-20 | (pending — to be merged this session) |
| **3** | Translation — flavor-gated via source-set split. New [`bridge/Translator.kt`](app/src/main/java/com/privateai/camera/bridge/Translator.kt) interface in main + 15-language `LANGUAGES` list (ISO 639-1 codes). Flavor-specific implementations: [`app/src/playstore/java/.../TranslatorImpl.kt`](app/src/playstore/java/com/privateai/camera/bridge/TranslatorImpl.kt) wraps ML Kit Translate (cached client per language pair, 8 s timeout), [`app/src/fdroid/java/.../TranslatorImpl.kt`](app/src/fdroid/java/com/privateai/camera/bridge/TranslatorImpl.kt) routes through `GemmaRunner.complete` with the existing "AI translate" prompt (`temperature=0.3`, output-only system instruction). [`TranslateScreen.kt`](app/src/main/java/com/privateai/camera/ui/translate/TranslateScreen.kt) no longer references any ML Kit Translate / TranslateLanguage / kotlinx.coroutines.tasks symbol directly. `mlkit-translate` + `kotlinx-coroutines-play-services` moved to `playstoreImplementation` only. **fdroid APK has ZERO ML Kit / play-services packages confirmed** via `unzip -l | grep -iE 'mlkit\|play-services'` → empty. | ✅ DONE 2026-05-20 | (pending — to be merged this session) |

**Status as of 2026-05-20**: ✅ **Track A complete.** All 5 ML Kit deps gone from the fdroid flavor (`barcode-scanning`, `face-detection`, `text-recognition`, `play-services-mlkit-document-scanner`, `mlkit-translate`). The playstore flavor keeps ML Kit Translate for the on-device phrasebook quality + faster cold start; everything else is shared between flavors. **F-Droid main eligibility unblocked** — fdroid APK is genuinely ML-Kit-free and ready for resubmission (subject to Gemma's "Gemma Terms of Use" license review by F-Droid maintainers — three documented outcomes in the plan).

**Follow-up tasks queued (not blocking v2.1.0)**:
- **A2.5 — auto document corners**. Likely a small TFLite quadrilateral-corner regression model trained on the existing public document datasets (MIDV, SmartDoc). 2-3 days of work (dataset prep + ONNX/TFLite export + integration). Drop-in addition to `ScannerCaptureScreen`'s capture path — if auto-detect produces a confident quad, pre-populate the four corners; otherwise fall back to the current centred-inset default. Standalone — doesn't block A3 or v2.1.0.

### Track B — Voice input + voice output for the AI Assistant (PLANNED, ~2-3 days)

Mic icon in the Assistant chat input row → tap → speak → text fills the input → review + send. When the voice-output toggle is on, assistant replies are spoken aloud as they stream. Both opt-in via Settings toggles.

Cheap because ~80% of the infrastructure already exists:
- `RECORD_AUDIO` permission flow shipped with v2.0.7 voice notes ([`NoteEditorScreen.kt`](app/src/main/java/com/privateai/camera/ui/notes/NoteEditorScreen.kt)) — permission launcher is drop-in.
- `TextToSpeech` already wired in [`TranslateScreen.kt`](app/src/main/java/com/privateai/camera/ui/translate/TranslateScreen.kt) (lines 130-243) — reusable verbatim.
- Streaming `onChunk` hook in [`AssistantScreen.kt`](app/src/main/java/com/privateai/camera/ui/assistant/AssistantScreen.kt) at line 190-206 is the natural place to plumb TTS.
- Feature toggle scaffolding + the precedent `clean_voice_notes` toggle ([`SettingsScreen.kt:730`](app/src/main/java/com/privateai/camera/ui/settings/SettingsScreen.kt#L730)).

Uses AOSP `SpeechRecognizer` (with `EXTRA_PREFER_OFFLINE = true`) + `TextToSpeech` — **no new dependencies**, fully F-Droid-compatible, doesn't interact with Track A.

**Graceful fallback**: on fully de-Googled / GrapheneOS devices where `SpeechRecognizer.isRecognitionAvailable()` returns false, the mic shows a toast and doesn't crash. Bundled Whisper/Vosk is deferred until F-Droid users actually report the gap.

### Sequencing options

- **Sequential**: ship Track A (3-5 weeks), then Track B (2-3 days), then cut v2.1.0.
- **Parallel-first**: ship Track B (2-3 days, immediate user-facing win), then Track A.

Track B never blocks Track A and vice versa.

### Not Started

| Feature | Priority | Effort |
|---------|----------|--------|
| **Track A2.5 — Auto document corner detection** (TFLite quad-corner regression model in assets, fallback when manual drag isn't needed) | Medium | ~2-3 days. Follow-up to A2's manual-only crop. Standalone — doesn't block v2.1.0. |
| **Resubmit fdroid flavor to F-Droid main** | High | ~30 min. Now genuinely ML-Kit-free. Open question: whether Gemma's "Gemma Terms of Use" license passes F-Droid's free-software bar. Three documented outcomes in the plan. |
| **Track B — Voice input for the AI Assistant** (`SpeechRecognizer`, mic IconButton, partial-results into the input field) | High | ~1 day |
| **Track B — Voice output for the AI Assistant** (`TextToSpeech`, sentence-buffered streaming, top-bar pause toggle, per-bubble replay) | High | ~½ day |
| **Track B — Settings toggles + 5-locale strings for Voice I/O** | High | ~½ day |
| **Track C — Background AI tagging & descriptions** (new `service/GemmaIndexingManager.kt`, opt-in toggle + manual bulk button in Settings → AI Detection, capture hook in `CaptureScreen.kt:262`, scanner hook in `ScannerScreen.kt`) | High | ~2-3 days. Approved 2026-05-13. ~10 sec/photo on warm Gemma engine; toggle defaults OFF (opt-in); manual bulk button shows count + ETA before processing. |
| Upload v2.0.7 AAB to Play Console | High | 5 min — paste trimmed changelog, attach AAB, roll out |
| IzzyOnDroid submission (interim while F-Droid main work proceeds) | High | ~30 min — same yaml format, submit at `gitlab.com/IzzyOnDroid/repo` |
| Close F-Droid main MR with polite reply to linsui | Low | 1 min — could now say "rework in progress, will resubmit clean" instead of pivoting away |
| Multi-locale Fastlane metadata (fr / es / zh / ar) | Medium | 1-2 hours; en-US already done |
| Lawyer review of `CLA.md` | Recommended | Before accepting first non-trivial external PR |
| `.github/FUNDING.yml` | Low | Pending sponsor / OpenCollective accounts |
| Audio transcription in notes (Vosk or Android on-device) | Medium | Deferred — privacy approach pending. Track B's `SpeechRecognizer` work could be reused directly here once it lands. |
| ~~Gemma vision (photo Q&A)~~ | DONE 2026-05-13 | Unblocked by `visionBackend = Backend.GPU()` in `EngineConfig`. Was a missing API parameter all along. |
| Cross-device sync (E2E encrypted, AGPL server) | High | 2-3 weeks (Phase 3 of monetization plan) |
| Iterate AI action prompts based on real usage | Medium | Ongoing |
| Replace "Anas" → full legal name in LICENSE / CLA / SPDX | Low | One find/replace when entity / name decision settles |
| Publish Play signing-key SHA-256 fingerprint in README | Low | Helps reproducible-build verifiers |

### Vision-driven feature backlog (post-v2.1.0, not committed)

Track 0 unblocked Gemma 4 vision (2026-05-13). The same `describeImage()` path is reusable across several product surfaces. Listed in rough impact-vs-cost order; each can ship incrementally and none block each other. Detailed in the plan file's "Vision-driven feature backlog" section.

| Idea | Effort | Notes |
|------|--------|-------|
| **D1 — Smart document scanner** | ~3-5 days | After a scan in `ScannerScreen.kt`, one-shot Gemma classifies doc type (receipt / business card / ID / invoice / recipe), suggests filename + folder + extracts key fields. Highest impact-to-cost — Privora already does the camera + edge detection + OCR; vision adds the understanding layer. |
| **D2 — AI Assistant image attach** | ~1-2 days | Image-attach button in `AssistantScreen.kt` input row (next to Track B's mic). Free-form Q&A about any photo. Engine path already validated; mostly UI. |
| ~~**D3 — Camera Detect: semantic descriptions**~~ | **DONE 2026-05-13** | Both halves shipped: D3a (per-detection refine, sparkle icon on selected box) + D3b (whole-scene describe, BottomStart FAB). See the Detect rows in the Gemma 4 features table above. |
| **D4 — Business card → contact** | ~2-3 days | Camera or shared image → Gemma vision OCR + structured extraction → pre-filled new `Contact` form (name / phone / email / company / title). |
| **D5 — Photo translation overlay** | ~3-4 days | Photo of foreign text → vision reads + translates in one pass → render as a card under the photo in `TranslateScreen.kt`. Positional alignment to source text deferred. |
| **D6 — Food / receipt structured logging** | Deferred | Big scope — needs new food log + expense log modules. Mention only; revisit if a finance or nutrition feature ever lands. |

**Cross-cutting constraint**: every Gemma vision call is ~5-10 sec + ~1 GB RAM. All D-track features must be **tap-to-trigger**, never live / always-on. Same pattern as the "Generate AI tags" chip already shipped.

### Play Store Readiness

| Item | Status |
|------|--------|
| App name: Privora | DONE |
| Version management | DONE (versionCode 9 / 2.0.7) |
| Privacy policy (PRIVACY.md) | DONE |
| Adaptive icon | DONE |
| ProGuard/R8 | DONE |
| Signing config | DONE |
| 16 KB page-size compliance | DONE |
| FOREGROUND_SERVICE_DATA_SYNC justification (no longer needed) | DONE — removed permission |
| USE_EXACT_ALARM removed (policy compliance) | DONE |
| Kotlin Android 15 collision fix (`removeFirst/Last`) | DONE |
| v2.0.7 AAB built + signed | DONE — `app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab` (~221 MB) |
| v2.0.7 changelog (≤500 chars for "What's new") | DONE — `fastlane/metadata/android/en-US/changelogs/9.txt` |
| v2.0.7 upload to Play Console | TODO (manual) |
| Data Safety declaration | TODO (manual in Play Console) |
| Screenshots + feature graphic | TODO (manual) |
| Store listing description | DONE (text in `fastlane/metadata/android/en-US/`) — paste into Play Console |

---

## Architecture

```
Privora (Kotlin + Jetpack Compose + Material 3)
├── UI Layer
│   ├── Camera (CaptureScreen, CameraScreen, PhotoEditor)
│   ├── Vault (VaultScreen, folders, hidden folder, viewer, video player, Wi-Fi transfer)
│   ├── Notes (NotesScreen, NoteEditorScreen, AI sheet)
│   ├── Scanner (ScannerScreen + OCR + AI receipt extraction)
│   ├── QR (QrScannerScreen + generator)
│   ├── Translate (TranslateScreen + AI translate + grammar fix + alternatives)
│   ├── Contacts (ContactsScreen)
│   ├── Insights (Expenses, Health + AI summary, Meds, Habits)
│   ├── Reminders (RemindersScreen, RemindersEditor, ReminderLinker)
│   ├── Passwords (PasswordHintsScreen, PasswordHintDialog, PasswordGenerator)
│   ├── Assistant (AssistantScreen, ChatBubble + markdown, data refs)
│   ├── Disguise (CalculatorScreen, CalculatorActivity, DisguiseManager)
│   ├── Home (Grid + Tabs layouts, gradient tips, mini-card activity, photo stack)
│   ├── Settings (features, security, AI, disguise, intruder alerts, hidden folder, transfer limit)
│   └── Onboarding (welcome, auth, PIN, benchmark)
├── Bridge Layer (AI)
│   ├── GemmaRunner (LiteRT-LM engine, GPU/CPU, mutex, completeJson)
│   ├── GemmaPrompts (notes AI + FIX_GRAMMAR)
│   ├── AssistantPrompts (chat system instruction, few-shot, markdown)
│   ├── AssistantTools (search_notes, fetch_note, summarize_expenses, ParsedReply)
│   ├── KnowledgeSnapshot (compact data builder, 8KB cap)
│   └── GemmaModelManager (download service)
├── Security Layer
│   ├── CryptoManager (AES-256-GCM, KEK/DEK)
│   ├── VaultRepository (photos, videos, PDFs, files with .file.enc)
│   ├── NoteRepository (encrypted notes)
│   ├── InsightsRepository (expenses/health/habits/meds/schedule)
│   ├── PasswordHintRepository (encrypted password hints)
│   ├── ContactRepository (encrypted contacts, self profile)
│   ├── PrivoraDatabase (SQLCipher)
│   ├── VaultLockManager (grace period, duress state)
│   ├── DuressManager (emergency PIN, wipe)
│   ├── AppPinManager (PBKDF2-hashed app PIN, lazy migration from legacy plaintext)
│   ├── PinRateLimiter (escalating cooldowns + intruder trigger)
│   ├── IntruderCapture (Camera2 front capture on wrong PIN)
│   ├── BackupManager (export/import .paicbackup)
│   └── FolderManager (custom vault folders)
├── Service Layer
│   ├── WifiTransferServer (NanoHTTPD + pure-JS AES-256-CTR encryption)
│   ├── ReminderScheduler (AlarmManager wrapper)
│   ├── ReminderReceiver (notification with Done/Skip + VISIBILITY_SECRET)
│   ├── ReminderActionReceiver (mark done/skip + habit propagation)
│   ├── BootReceiver (re-register alarms)
│   ├── MissedSweepWorker (daily WorkManager)
│   ├── GemmaDownloadService (foreground model download)
│   └── CrashHandler (local logging)
└── Dependencies
    ├── CameraX 1.4.2
    ├── LiteRT-LM 0.11.0 (Gemma 4, vision enabled via visionBackend)
    ├── ONNX Runtime 1.21.0 (YOLOv8n)
    ├── NanoHTTPD 2.3.1 (Wi-Fi transfer)
    ├── ML Kit Translate — playstore flavor ONLY (fdroid uses Gemma via Translator interface). Others removed: barcode→ZXing, face→ONNX YuNet, OCR→Tesseract, scanner→CameraX+manual crop.
    ├── ZXing core 3.5.3 (QR/1-D barcode)
    ├── Tesseract4Android 4.9.0-openmp (OCR; lazy-downloaded .traineddata per language)
    ├── YuNet 2023mar ONNX 234 KB (face detection, in assets/models/face_detect.onnx)
    ├── SQLCipher (encrypted database)
    ├── WorkManager 2.9.1
    ├── AndroidX Biometric 1.1.0
    ├── Navigation Compose 2.9.0
    └── Kotlin 2.3.20, AGP 8.9.3, minSdk 26, targetSdk 35
```

## Commit History (Recent)

| Commit | Date | Description |
|--------|------|-------------|
| (working tree) | 2026-05-20 | Track A3 — Translation flavor-gated: `playstore` keeps ML Kit Translate, `fdroid` uses Gemma. New `Translator` interface in main + flavor-specific `TranslatorImpl` source sets. `mlkit-translate` + `kotlinx-coroutines-play-services` moved to `playstoreImplementation` only. **fdroid APK confirmed ML-Kit-free.** Track A COMPLETE — 5/5 deps gone. F-Droid main eligibility unblocked. |
| `aa71570` | 2026-05-20 | Track A2 — Document scanner → CameraX + manual corner-drag (perspective via Android Matrix.setPolyToPoly, no OpenCV). New `ScannerCaptureScreen` + `PerspectiveTransform.kt`. Removes `mlkit-document-scanner`. Auto-detect deferred to follow-up "A2.5" track (TFLite quad-corner model). Tap-to-focus overlay + CAPTURE_MODE_MAXIMIZE_QUALITY + 2592×3456 resolution. STATUS updated. |
| `0bc05fb` | 2026-05-20 | Track A1.3 — OCR: ML Kit text-recognition → Tesseract 5. New TesseractRecognizer + TesseractDataManager + OcrLanguagesScreen + 18-language picker (lazy `.traineddata` download). 4 call sites swapped (Scanner ×2 + VaultRepository ×2 — TranslateScreen overlay also caught). looksLikeReadableLatinOcr Latin-only gate removed. JitPack repo added scoped to adaptech-cz. **Unlocks Arabic / Hebrew / CJK / Devanagari / Cyrillic / Thai OCR.** |
| `74dbc30` | 2026-05-20 | Track A1.2 — Face detection: ML Kit → ONNX YuNet 2023mar (~234 KB force-added). New FaceDetector + FaceDetectorHolder singleton. Score = sqrt(cls × obj) with NaN guard for negative products (1500-2500 ghost anchors before fix). Settings → Reset face groups action. Faces gallery grouped by date headers. |
| `cc6e534` | 2026-05-19 | Track A1.1 — Barcode/QR: ML Kit → ZXing. New BarcodeType.kt with int constants matching legacy Barcode.TYPE_* (on-disk QR-history compat). YUV Y-plane → ZXing MultiFormatReader + HybridBinarizer, rotation-aware buildLuminanceSource. |
| `4ba53eb` | 2026-05-19 | Assistant: 4-way attach + camera + zoom + locked-mode + Stop fix. New `+` menu (Photo/PDF × device/vault), TakePicture(), full-size thumb viewer dialog, locked-vault Assistant mode + PIN dialog, Stop button with channelFlow + NonCancellable drain (LiteRT-LM SIGSEGV fix). |
| `20e372d` | 2026-05-09 | STATUS: Authenticator (TOTP) + Ask My Documents Phase 0 + rename + backup summary |
| `4b12bff` | 2026-05-09 | Ask My Documents iteration (session-bound docId, doc-switch chat clear, truncated-JSON salvage, language directive, tool removal) + Vault rename + Backup full module summary |
| `1564222` | 2026-05-09 | Ask My Documents — Phase 0 quality-gate spike (OCR sidecars + session-bound docId + ATTACHED DOCUMENT prompt injection + PDF viewer entries) |
| `cf49966` | 2026-05-09 | Settings AI toggle: clear sticky `load_crashed` / `vision_crashed` flags when the user re-enables (treat opt-in as retry) |
| `d78547f` | 2026-05-09 | Fix AI Assistant disabled after backup restore (`gemma_settings` added to `prefsToBackup` + post-import self-heal) |
| `fc5fa6d` | 2026-05-09 | Fix backup-restore date grouping (mtime preserved in ZipEntry + sidecar-based fallback for old backups); wizard: privacy-defaults step |
| `d74f69c` | 2026-05-09 | WIP: Authenticator (TOTP) feature + AI Assistant writing chips + Vault polish (starred, EXIF preservation, scroll preservation, viewer crossfade, trash-batch fix) |
| `b553f4c` | 2026-05-06 | STATUS: capture EXIF preservation + gallery streaming + viewer polish |
| `df244c3` | 2026-05-05 | STATUS: capture AI Assistant + Vault enhancements on dev-v3-calibrate |
| `0ca9f9c` | 2026-05-05 | STATUS: document v2.1 WIP + private remote mirror workflow |
| `ce70d81` | 2026-05-05 | WIP: Health top-level feature, cycle tracker, calibration wizard, grid header redesign |
| `a92acdc` | 2026-05-04 | v2.0.7: voice-note quality batch (VOICE_RECOGNITION audio source + toggle, mic permission gate, "Play all" auto-advance, draft persistence on auto-lock, defensive save, locale-safe Wi-Fi PIN/IP, Settings → Advanced translations, password generator extracted) |
| `791ac02` | 2026-05-03 | v2.0.6: fix crash on PHONE_LOCK onboarding (empty-PIN PBKDF2) + libbarhopper packaging fix for 32-bit ARM |
| `8740684` | 2026-05-03 | Merge PR #7: v5 work merged into `main`; default branch fully current |
| `b19f5b8` | 2026-05-02 | Add F-Droid + Fastlane metadata + CHANGELOG.md |
| `11ad369` | 2026-05-02 | v2.0.5: replace Kotlin removeLast() with removeAt() (Android 15 fix) |
| `4052fe2` | 2026-05-02 | v2.0.4: AI download — allow metered, detect stalls, expose Retry |
| `6aab38a` | 2026-05-02 | v2.0.3: rebuild for Play with stuck-download fix |
| `471ee1d` | 2026-05-02 | Fix stuck AI model download not recovering on retry |
| `b063faa` | 2026-05-02 | v2.0.2: replace foreground-service download with system DownloadManager — drops 3 permissions, no Play video required |
| `ce349f3` | 2026-05-02 | v2.0.1: drop USE_EXACT_ALARM (Play policy: alarm/calendar apps only) |
| `e1e5224` | 2026-05-02 | v2: bump versionCode + ONNX Runtime 1.22.0 for 16 KB page-size compliance |
| `1a02bce` | 2026-05-02 | Vision still crashes on 0.11.0-rc1 — keep disabled |
| `f0d2df9` | 2026-05-02 | Skip download prompt when AI model is already on disk |
| `3d07f59` | 2026-05-02 | Bump LiteRT-LM to 0.11.0-rc1 — 0.10.2 vision still crashes |
| `ac032dd` | 2026-05-02 | Re-enable Gemma vision with crash-flag safety net |
| `9078180` | 2026-04-29 | Apply XL widget logic to all 1×1 widgets |
| `237c16c` | 2026-04-29 | Make widgets fresh + colorful (initial pass) |
| `936a4a0` | 2026-04-29 | Theme picker (System / Light / Dark) in Settings |
| `83e10a4` | 2026-04-29 | 4×1 Quick Access XL widget |
| `17bff83` | 2026-04-29 | Dark mode adaptation for home grid + bottom tabs |
| `1f5b6f2` | 2026-04-28 | Add SECURITY.md + THREAT_MODEL.md |
| `f0a931f` | 2026-04-28 | Add CLA + CONTRIBUTING + PR template (CLA Assistant workflow) |
| `b5cb356` | 2026-04-28 | License the code under AGPL-3.0 |
| `d04297a` | 2026-04-28 | Add fdroid / playstore product flavors |
| `e18b559` | 2026-04-28 | Sprint 2: AI streaming, action proposals (7 kinds), home-screen widgets |
| `1e6cc25` | 2026-04-27 | Fix PDF viewer back button hidden behind status bar |
| `0ba1630` | 2026-04-27 | Built-in PDF viewer (no external app handoff) |
| `51d92d3` | 2026-04-27 | Fix duress-mode crash on Insights entry |
| `0a9e35c` | 2026-04-27 | Change PIN flow + PBKDF2 hardening for app PIN |
| `0f4340d` | 2026-04-24 | Fix OOM when restoring backup over a populated vault (streaming re-encrypt) |
| `ccb1d5a` | 2026-04-24 | Fix calculator disguise crash on PIN + = (LauncherDefault alias, MainActivity stays enabled) |
| `12b4352` | 2026-04-24 | Password Hints feature + backup streaming fix + contacts in backup |
| `4e4913a` | 2026-04-23 | Duress fixes, PDF detection, folder multi-select, pinch-to-zoom, all-to-Received |
| `735437c` | 2026-04-23 | Vault: Files as universal explorer with filters, translations, scroll + nav fixes |
| `3b819f1` | 2026-04-22 | Wi-Fi Transfer with browser-side encryption, Files category, vault fixes |
| `c5d9e91` | 2026-04-22 | Translate AI + grammar + alternatives, vault hidden folder, home redesign |
| `15d65b7` | 2026-04-20 | Update README, PLAN, and add STATUS.md |
| `bb519ce` | 2026-04-16 | Calculator disguise + Share-to-Privora + duress from calculator |
| `28cc0f0` | 2026-04-16 | AI Assistant + intruder alerts + notification privacy + home UX polish |
| `59c6f52` | 2026-04-15 | Insights redesign + standalone Reminders feature |
| `5db4a9f` | 2026-04-14 | Home layout: Grid/Tabs option + dynamic landing with AI tips |
| `38fcaa1` | 2026-04-13 | GPU backend with CPU fallback, OpenCL manifest fix |
| `42b14cf` | 2026-04-13 | Phase 3: Photo Intelligence DB + Vision (disabled) |
| `1dc8c36` | 2026-04-12 | Gemma 4 Phase 1: Notes Intelligence with on-device LLM |
