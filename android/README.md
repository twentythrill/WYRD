# WYRD — Native Android AI Client (v1.1.0)

WYRD is a high-performance, minimalist, brutalist native Android chat application built with Kotlin and Jetpack Compose. Operating strictly client-side with zero intermediary servers, it combines cloud LLM intelligence (Xiaomi MiMo) with live web search (Exa MCP), native high-fidelity speech synthesis (TTS) with interactive word tracking, hardware-accelerated speech-to-text (Whisper), and complete local session persistence.

---

## Core Features

### 1. Visual Design & Theming
- **Dual Brutalist Theme**:
  - **AMOLED Dark**: Pure black (`#000000`) background, `#141414` elevated surfaces, `#1A1A1A` message bubbles, and `#cecdc4` warm bone-white text and primary buttons.
  - **Inverted Light**: Warm off-white (`#F9F9F7`) background, `#E8E7E1` elevated cards, `#DFDED8` message bubbles, `#141414` deep charcoal text, and solid black action buttons.
  - **Theme Selection**: Settings dropdown under **APPEARANCE** supporting **System** (tracks OS theme), **Light** (manual forced light mode), and **Dark** (manual AMOLED black).
  - **Adaptive Insets**: Edge-to-edge status and navigation bar icons invert dynamically for continuous contrast.
  - **Application Icon**: High-contrast dark variant icon (`app_icon.jpg`).
- **Typography & Brand Mark**:
  - Global variable Space Grotesk typography (`MaterialTheme`) with calibrated weights.
  - Home screen centered eye mark (30% opacity, auto-switching dark/light assets), personalized greeting ("Welcome Back, **[Name]**"), and `(◕‿◕)` kaomoji.
  - **Transparent Edge-to-Edge System Bars & Subtle Gemini-Style Fade Gradients**:
    - Status bar and 3-button navigation bar are 100% transparent (`android.graphics.Color.TRANSPARENT`) with OS contrast enforcement disabled (`isStatusBarContrastEnforced = false`, `isNavigationBarContrastEnforced = false`), eliminating gray scrims.
    - **Top Subtle Gradient**: Multi-stop vertical gradient (~130dp) softly dissolving text scrolling behind the status bar and top bar without harsh solid black blocks.
    - **Bottom Subtle Gradient**: 230dp multi-stop vertical gradient dissolving text smoothly behind the floating capsule and through the transparent navigation bar, delivering pure seamless continuity.
- **Markdown Engine & Reading Layout**: Jetpack Compose renderer supporting headers, syntax-styled code cards with one-tap copy and horizontal scroll, blockquotes, ordered/unordered lists, bold/italic formatting, and clickable links. Dynamically maps all text spans, code backgrounds, blockquotes, and italic highlights to current theme colors (`LocalWyrdColors`), eliminating white-on-white text issues in Light mode.
- **Zero-Shift Streaming Typography**: `StreamingAssistantMessageItem` and `AssistantMessageItem` share the exact same condensed 23sp line-height typographic pipeline, markdown preprocessing (`formatMarkdownForReading`), and padding layout. Eliminates intermediate spacing shifts, erratic vertical jumps, or layout snapping between streaming and finalized assistant turns.
- **Selection & Double-Tap Actions**:
  - Native long-press text selection with system copy handles across all messages, streaming content, and reasoning blocks. Powered by a no-op `BringIntoViewSpec` provided to `LazyColumn` (`LocalBringIntoViewSpec`) and non-consuming gesture timeouts, ensuring text selection is 100% rock-solid with zero jumping, viewport snapping, or scrolling behind the floating capsule anywhere in messages of any length.
  - Double-tapping any sent user bubble opens an action menu:
    - **Copy**: Copies the entire prompt text to the system clipboard in a single tap with instant haptic confirmation.
    - **Edit**: Loads the prompt into the input field with autofocus, an "Editing prompt" cancel banner, and hardware back button support. Replaces subsequent conversation turns on submit.
    - **Branch into a new chat**: Slices conversation history up to that turn into a new isolated session titled `"<Title> (Branch)"`.
    - **Redo**: Truncates following assistant responses and re-streams a fresh generation from the model.

### 2. Chat Input, Soft Keyboard & Multimodal Multi-Attachments
- **Capsule Input & Keyboard Insets**:
  - Multiline text input with adaptive height (44dp–180dp), reactive `.imePadding()`, and auto-focus on launch.
  - In existing chats, when the soft keyboard appears, both `LazyColumn` and the input capsule adapt to IME insets; the message list auto-scrolls with tight, comfortable breathing room directly above the floating capsule, removing unnecessary empty space and keeping the conversation visible.
  - The floating TTS button gracefully fades out while typing (unless actively playing) to maximize visible viewport.
  - **Dynamic Scroll-to-Bottom Button & Universal Bottom Positioning**:
    - **Universal True Bottom Anchoring**: When opening any conversation from the history drawer or on launch, the message list automatically and consistently scrolls to the true bottom using a layout bottom anchor (`chat_bottom_anchor`), settling reliably through multi-stage layout measurement passes so conversation history is immediately ready at the latest message above the capsule.
    - Centered directly above the text input capsule with smooth scale and fade animations (`fadeIn` + `scaleIn` / `fadeOut` + `scaleOut`).
    - Automatically appears whenever the user scrolls up away from the bottom to read previous conversation turns or review earlier text.
    - Tapping the 38dp brutalist circular arrow button fires tactile haptic feedback and smoothly scrolls the conversation down to the latest message (or active streaming chunk), fading out smoothly once the bottom is reached.
    - Gracefully hides while typing when the soft keyboard is open, and seamlessly re-appears when the user drags the message list.
  - Keyboard collapses only upon sending, opening the drawer, or when the user manually drags the chat list (`collectIsDraggedAsState`), preventing programmatic scrolls from accidentally collapsing the keyboard.
  - **Dynamic Send / Stop Control**:
    - While idle or typing, the action button presents a sleek black arrow on a 54dp white circle, activated whenever text is entered or attachments are staged.
    - During LLM generation (dispatching, reasoning, or content streaming), the button dynamically morphs via `AnimatedContent` into a brutalist solid black stop square (`16dp × 16dp`, 3dp rounded corners).
    - Tapping Stop at any time halts generation immediately, closes the streaming socket, triggers mechanical haptic feedback, stops TTS playback, and cleanly commits any partial response generated so far into Room SQLite without error alerts or ghost messages.
- **Attachment Popup Menu (Photo, Files, Camera)**:
  - Tapping the `+` button triggers a smooth 45° spring rotation into an `✕` and reveals three prominent action buttons:
    - **Photo**: Multi-photo picker via `ActivityResultContracts.PickMultipleVisualMedia()`.
    - **Files**: Multi-document picker via `ActivityResultContracts.OpenMultipleDocuments()`.
    - **Camera**: Captures a full-resolution photo via `ActivityResultContracts.TakePicture()` using a secure private `FileProvider` URI.
  - Dismisses cleanly upon selecting items, re-tapping `✕`, tapping outside the menu, or pressing the hardware back button.
- **Attachment Processing & Preview Bar**:
  - **Images**: Automatically corrects EXIF orientation, downscales large photos to a maximum dimension of 1280px, compresses to 82% quality JPEG in private app storage, and encodes Base64 for fast streaming transmission.
  - **Files**: Inspects MIME type and file extension; reads UTF-8 code and text documents (up to 80KB each) into contextual message memory.
  - **PDF Documents**: Natively rasterizes PDF pages into high-resolution images via `android.graphics.pdf.PdfRenderer`, enabling native multimodal visual document inspection by Xiaomi MiMo 2.6.
  - **Attachments Preview Bar**: Horizontal scrolling bar directly above the input capsule showing thumbnail chips, filename, file size, individual `✕` remove buttons, and a "Clear All" action.
  - **Prompt Flexibility**: The send button activates if text is typed or any attachments are present, allowing one-tap visual questions.
- **Multimodal Intelligence, Supabase Object Storage & Cloud Parity**:
  - **Dedicated Supabase Storage Bucket (`attachments`)**: When syncing messages to Supabase, local attachments are automatically uploaded to the public Supabase Object Storage bucket (`attachments/${userId}/${timestamp}_${fileName}`). Only the lightweight public URL is recorded in the PostgreSQL `messages` table, preventing database quota exhaustion and eliminating Base64 payload bloat.
  - **Automatic Storage & Local Attachment Cleanup on Chat Deletion**: Whenever a chat is deleted locally or unpinned chats are wiped in bulk, all associated files in Supabase Storage (`attachments` bucket) and local private cache (`context.filesDir/attachments/`) are automatically pruned. Replaced/edited prompt turns also purge obsolete local attachments.
  - **Proactive Orphan Cleaner**: Two-way synchronization (`syncAll`) cross-references all remote objects stored under the user's bucket prefix against active messages in Room SQLite and Supabase, proactively deleting any unreferenced or orphaned files.
  - **Asynchronous Image Loading with Disk Cache**: `AttachmentImageView` asynchronously resolves both local files and remote Supabase Storage URLs on `Dispatchers.IO`, featuring an on-device MD5 disk cache in `context.cacheDir/image_cache/` for instant re-rendering without duplicate network requests.
  - **Tap-to-Open Attachment Actions**: Tapping any image opens it in the system viewer via `FileProvider` or browser; tapping file attachments launches the appropriate document viewer.
  - Attached code/text files are formatted with structured markdown code fences into the model prompt.
  - Room database (`MessageEntity`) stores attachments as serialized JSON lists (`attachmentsJson`), which sync bidirectionally in real-time with Supabase and Desktop Web.
  - User message bubbles render single full-width cards, two-column side-by-side grids, or horizontal galleries for 3+ images, along with brutalist file pills.
  - Double-tap actions (**Edit**, **Branch**, **Redo**) retain all attachment metadata across forks and regenerated turns.

### 3. Speech Synthesis (TTS) & Highlighting
- **Native Android Engine**: Built entirely on `android.speech.tts.TextToSpeech` with instant initialization, application-scoped lifecycle, and local offline voice priority.
- **Real-Time Streaming Playback**:
  - In conversational STT mode, speech synthesis starts speaking as soon as the very first tokens arrive rather than waiting for the entire multi-paragraph completion to finish.
  - Dynamically extracts semantic speech chunks at sentence and clause boundaries in real-time, chaining subsequent chunks seamlessly via `TextToSpeech.QUEUE_ADD` without gaps or stuttering while the LLM continues generating in the background.
  - Streaming word-by-word brutalist highlighting illuminates spoken words live on the active streaming card (`StreamingAssistantMessageItem`) and transitions smoothly to the finalized message bubble upon completion.
  - **Streaming Pause & Resume Stability**: Pausing during live token streaming immediately halts speech synthesis and locks the playback state (`isStreamingPaused`), buffering subsequent incoming tokens without auto-resuming playback. Playback stays paused until the user explicitly hits Resume or taps a word in the text.
- **Continuous Background Playback**: Audio continues uninterrupted across app switches or device screen locks, stopping only upon explicit pause, tap-to-read, or chat navigation.
- **Engine Provider & Voice Profile Selection**:
  - Configurable under Settings -> **SPEECH SYNTHESIS (TTS)**.
  - **TTS Engine Provider**: Dynamic selection between **System Default** (tracking device settings), **Samsung text-to-speech** (`com.samsung.SMT`), and **Google Speech Services** (`com.google.android.tts`). Persisted locally via Jetpack DataStore.
  - **Voice Profile Customization**: Detects installed high-definition voices and surfaces intuitive, human-friendly voice names (e.g., `English (US) · Voice 2 (Male)`, `English (US) · Voice 1 (Female)`, `Italian · Voice 2 (Male)`). Filters out network duplicate latency and generic fallback tokens.
  - **In-App Speech Preview**: Dedicated "Test Speech" control with instant **▶ Preview / ■ Stop** toggle button to audit tone, pitch, and engine timbre before chatting.
  - **Samsung One UI Private Engine Compatibility**: Automatically adapts to One UI 6+ framework policies where `com.samsung.SMT` is marked as a private system-only engine, ensuring smooth fallback through public engines while applying the user's chosen voice profile.
- **Intelligent Utterance Chunking**: Circumvents the Android TTS engine's strict 4,000-character single-utterance limit by segmenting long texts into semantic chunks ($\le 2,000$ characters) across paragraphs, line breaks, sentence endings, and word boundaries. First chunk flushes queue (`QUEUE_FLUSH`) while subsequent chunks chain smoothly (`QUEUE_ADD`), preserving continuous global word offset tracking and highlight synchronization across chunk boundaries.
- **Word-by-Word Highlighting**: Tracks `onRangeStart` to invert each active spoken word to a high-contrast pill with 1:1 character index accuracy and zero font-metric alteration (preventing text layout shift).
- **Upper-Third Reading Snapping**: Dynamically tracks speech position in the viewport, smoothly auto-scrolling to position the active spoken line at the upper third (~32%) of the screen whenever reading progresses below the comfortable reading zone, leaving the remaining 68% of subsequent text content exposed and readable.
- **Tap-to-Seek Playback**: Tapping any word in an assistant bubble immediately jumps playback to that exact word with natural word-boundary snapping.
- **Floating Play/Pause Controller**: 54dp floating button anchored above the input capsule. Plays the latest assistant response, or pauses and resumes playback at the current offset.
- **Smart Bilingual Detection**:
  - Comprehensive linguistic analysis across Italian (articles, prepositions, articulated prepositions, suffixes `-zione`, `-mente`, `-ità`) and English stopwords.
  - In conversational STT mode, immediately passes the user prompt's detected language as a directional hint to `startStreaming()`, dynamically refining against cumulative incoming tokens (`fullTextSoFar`). Ensures Italian answers spoken right after an Italian voice query are consistently read using the Italian TTS engine from chunk 0.

### 4. Speech-to-Text Pipeline (STT)
- **Spring-Loaded Pull-to-Record**: Swiping right-to-left across the screen (~95dp threshold) features elastic spring resistance with progressive mechanical haptic ticks, collapsing the keyboard and opening the fullscreen STT screen.
- **Interactive Recording Controls**:
  - **Top Model & Reasoning Selector**: Elevated brutalist capsule pill positioned directly above the central recording button with generous horizontal padding, allowing the user to seamlessly switch the active LLM model (`MiMo 2.6 Pro` vs `MiMo 2.6 Flash`) and reasoning effort level (`Off`, `Low`, `Medium`, `High`) mid-speaking before hitting send.
  - **Center Glowing Button**: Stops recording, transcribes audio via OpenAI Whisper (`gpt-4o-mini-transcribe`), pastes into prompt, and immediately sends. Automatically engages conversational speech mode with instant real-time streaming TTS, speaking out loud as soon as the first tokens arrive with live word highlighting while the rest of the answer generates.
  - **Stop Button**: Stops recording, transcribes audio, and pastes into the prompt for review without sending.
  - **Cancel (✕) Button & Swipe Right**: Discards captured audio and collapses the overlay.
  - **Tap Isolation**: Any tap outside the three action buttons is consumed as a no-op to prevent accidental cancellation.

### 5. Intelligence, Streaming & Tools
- **Xiaomi MiMo Streaming**:
  - Model selection: **Xiaomi MiMo 2.6 Flash** (`mimo-v2.6-flash`, high-speed multimodal reasoning) and **Xiaomi MiMo 2.6 Pro** (`mimo-v2.6-pro`, deep complex reasoning) with candidate fallback cascades.
  - Dual-cluster auto-routing (Token Plan vs. Pay-As-You-Go) with optional custom base URL and dual-header (`Authorization` + `api-key`) authentication.
  - Native reasoning effort controls (**Off**, **Low**, **Medium**, **High**) tuning thinking budget and token generation:
    - **Off**: Completely disables the reasoning scratchpad (`"thinking": {"type": "disabled"}`) for immediate, direct streaming answers.
    - **Low**: Strictly constrains thinking tokens to a 1,024-token budget (`"thinking": {"type": "enabled", "budget_tokens": 1024}`) for rapid, concise reasoning.
    - **Medium**: Balanced reasoning scratchpad with an 8,192-token budget.
    - **High**: Comprehensive chain-of-thought exploration with up to 32,768 tokens.
    - Employs hybrid schema serialization sending structured `thinking` objects alongside `reasoning_effort` and fallback parameters, ensuring strict budget enforcement across all Xiaomi MiMo clusters and gateways.
  - **Lifecycle Status Progression**: Explicit phase indicators: displays "Sending..." during initial request serialization and HTTP transmission, shows "Searching web with Exa..." when autonomous searches trigger, displays "Synthesizing answer..." while aggregating results, transitions seamlessly to the collapsible **"Thinking"** card with live pulse indicator during reasoning, and renders clean formatted markdown as final content streams.
  - `ThinkAndToolParser`: High-precision streaming parser demuxing reasoning tags (`<think>`, `<thought>`, `<thinking>`) to reasoning memory, buffering tool call XML (`<tool_call>`, `<function_call>`, `<tool>`) without leaking tokens into visible text, and feeding clean assistant content to the UI.
  - **Zero Reasoning / Tool Leaks**: Thinking tokens are strictly preserved inside the Room SQLite `reasoning` column and never promoted or leaked into user-facing `content`. Automated content sanitizers strip residual XML or think tags from saved records, preventing speech-to-text / TTS from speaking internal thoughts aloud.
  - Automatic non-streaming fallback if SSE encounters packet drop.
  - **Reading-First Flow**: Strictly avoids auto-scrolling during incoming answer streaming (tokens and reasoning) so user reading flow is never broken by jumping viewports. Auto-scroll is only triggered when the user explicitly submits a prompt or opens a chat.
- **Continuous Background Execution & Foreground Service**:
  - Powered by `WyrdBackgroundService`: an Android Foreground Service (`dataSync` type on Android 14+) with CPU partial `WakeLock` and an application-scoped coroutine supervisor (`applicationScope`).
  - **Uninterrupted Multitasking**: When sending a prompt and navigating away to other apps (or locking the phone), the OS is prevented from freezing the process or severing SSE streaming sockets. Reasoning, Exa web search, and SQLite Room commits continue seamlessly in the background.
  - **Notification Controls**: Displays a low-profile ongoing notification with real-time status ("Sending...", "Searching web with Exa...", "Thinking...", "Generating response...") and includes a quick "■ Stop" action button to halt generation from the notification shade. Tapping the notification brings WYRD back to the foreground.
  - **Task Manager Cleanup**: Directly honors system lifecycle events—if the user swipes WYRD away from the Android Task Manager (Recents), `onTaskRemoved` immediately halts the streaming request, releases wake locks, and stops the service.
- **Autonomous Web Search & Factual Grounding (Exa AI)**:
  - Connects to Exa MCP and direct Exa REST search (`numResults: 5`, `contents: { "highlights": true }`) to extract URLs, publication dates, and snippet highlights.
  - **Zero-Result Guard**: Intercepts queries returning zero results at the tool boundary before reaching the model, returning: `Search returned 0 results. No credible sources or records were found for this query.`
  - **Structured Text Blocks & Verification Trailer**: Formats positive results into structured blocks with URL, publication date, and extracted highlights, and appends an enforcement trailer to enforce strict factual grounding and eliminate hallucinations.
  - **Clean Grounding & Synthesis**: Executes Exa search in Round 1 with animated status ("Searching web with Exa..."), transitions smoothly to "Synthesizing answer...", suppresses tools in Round 2, and guides the model to produce a verified answer without hesitation, looping, or failed synthesis.
  - Multi-format tool extraction supporting XML parameter syntax (`<parameter=query>`), XML tag syntax (`<query>`), JSON schemas, function calls (`web_search(...)`), and bracket actions.
  - Configurable under Settings -> **WEB SEARCH API** (personal key or public tier fallback).
- **Contextual Grounding**: Injects current device date, time, and timezone into the LLM system prompt on every completion.
- **Concise Auto-Naming**: Automatically summarizes conversations into punchy 3 to 4-word titles.
- **Side Panel & History**: Material 3 navigation drawer featuring a dedicated top brand header (eye logo marker + "WYRD" title), quick actions (New Chat, Search Chats), Pin chat, Rename chat (modal dialog), and Delete chat.
- **Tactile Haptics (`HapticManager`)**: Rate-limited micro-ticks during streaming token generation (`streamTokenTick`), dedicated post-generation completion snap (`completionSnap`) featuring an initial brief pause (~120ms) followed by an authoritative single hardware snap (`EFFECT_HEAVY_CLICK`) with zero rumbling modeled after the ChatGPT app, crisp tactile clicks for button presses and selections, spring tension ticks, and UI success confirmations.

### 6. Privacy & Data Storage
- **Local Room SQLite**: On-device database for `chats` and `messages` (cascading deletes, indexed timestamps).
- **Encrypted Storage**: Sensitive API keys stored in Keystore-backed `EncryptedSharedPreferences`; preferences stored in Jetpack DataStore.
- **Zero Cloud Leak & Data Controls**: Android auto-backup disabled at the OS level (`android:allowBackup="false"`). Includes a dedicated "Delete Unpinned Chats" setting to purge all unpinned conversation history while safely preserving pinned chats, alongside a complete "Reset All App Data" option.
- **Hierarchical Back Navigation**: Priority back order: Attachment Menu -> Prompt Editing -> STT Overlay -> Settings Dialog -> Settings Screen -> Drawer -> Exit.

### 7. Cloud Synchronization (Supabase & Web Companion)
- **Two-Way Real-Time Sync**:
  - Direct integration with Supabase PostgREST and Auth APIs via lightweight, optimized `OkHttp` (`SupabaseSyncManager`).
  - Synchronizes user `profiles` (`username` and `system_prompt`), `chats`, and `messages` between the Android app and the Desktop Web companion.
  - Safe Room migration (`MIGRATION_5_6`, database version 6) adding unique `syncId` UUIDs across all existing and new chats and messages without data loss.
  - **Conflict-Free Upsert & Deletion Propagation**: PostgREST endpoints ensure duplicate-free insertion and updating. Tracks previously synced chat IDs (`knownSyncedChatIds`) alongside an in-flight creation grace period so newly created chats streaming LLM responses are never prematurely deleted before their first remote push, eliminating foreign key race conditions.
  - **Two-Way Deletions**: Deleting individual chats or bulk-wiping unpinned conversations cleanly removes associated messages and threads across both Supabase and Room, updating tracked sync IDs.
- **Continuous Lifecycle & Background Sync**:
  - Automatic synchronization on app launch, whenever the user brings the app to the foreground (`onResume()`), whenever the user sends a prompt, and whenever an assistant generation completes.
  - **Thread-Safe Concurrency Lock (`syncMutex`)**: Guarded by a non-blocking `Mutex.tryLock()` within `SupabaseSyncManager.syncAll()`. Overlapping sync triggers from UI events or the background ticker gracefully no-op while a sync pass is active, preventing duplicate database writes and SQLite transaction contention.
  - Periodic 25-second background sync loop while the app is active, with active `isSyncing` state tracking, streaming down conversations created on the Web without requiring manual interaction.
  - Manual on-demand **"Sync Now"** button with live progress indicator in Settings.
- **Keystore-Backed Cloud Session with Auto-Refresh**:
  - User session tokens (`access_token`, `refresh_token`, `user_id`, `user_email`) encrypted via Android Keystore (`SecureKeyStorage`).
  - **Automatic JWT Token Refresh**: Proactively detects when 1-hour Supabase access tokens expire and silently renews them via Supabase Auth `refresh_token` without interrupting sync or user flow.
  - Graceful session recovery: automatically clears revoked sessions and prompts clean re-authentication if credentials are permanently invalidated.
  - Seamless Sign In / Sign Up dialog embedded natively in Settings under **CLOUD SYNC**.

### 8. Projects & Knowledge Base Spaces
- **Custom System Prompt Override**: Dedicated projects with custom instructions that strictly override the generic profile system prompt.
- **Knowledge Base Source Files**: Attach `.md`, `.txt`, `.pdf`, and code documents to any project. The text is extracted client-side via `AttachmentHelper` and formatted into contextual knowledge base memory before prompt dispatch.
- **Collapsible Folder Organization**: The Material 3 navigation drawer features a collapsible `PROJECTS` section with nested chat threads, chat counts, quick `+` chat creation, and project settings dialog. Generic chats reside in the `HISTORY` section.
- **TopBar Breadcrumb**: Subtle folder indicator in the top bar displaying the active project name.
- **Room SQLite Migration (v7)**: Added `ProjectEntity`, `ProjectFileEntity`, foreign keys, and indices with `MIGRATION_6_7`.
- **Bidirectional Cloud Sync**: Full two-way sync with Supabase tables `projects`, `project_files`, and `chats.project_id`.

---

## Directory Structure

```text
WYRD/
├── README.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── local.properties
├── gradlew
├── gradlew.bat
├── gradle/wrapper/
├── Assets/
│   ├── app_icon.jpg
│   ├── app_icon_trans.png
│   ├── app_icon_trans_black.png
│   └── tts_icon.png
├── Mockups/
│   ├── homescreen_1.jpg
│   ├── homescreen_2.jpg
│   └── chat_bubble.jpg
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/
        │   ├── drawable-nodpi/     # app_icon.jpg, app_logo.png, app_icon_trans_black.png, splash_logo.png, ic_soundwave.png
        │   ├── font/               # space_grotesk.ttf
        │   └── values/             # strings.xml, themes.xml
        └── java/com/wyrd/app/
            ├── WyrdApp.kt          # Application initialization & native TTS warmup
            ├── MainActivity.kt     # Single Activity hosting edge-to-edge Compose navigation & theme provider
            ├── service/
            │   └── WyrdBackgroundService.kt # Foreground service for background LLM streaming & task manager lifecycle
            ├── data/
            │   ├── local/          # Room AppDatabase, ChatEntity, MessageEntity, DAOs
            │   ├── preferences/    # PreferencesManager (DataStore with AppTheme) & SecureKeyStorage (Keystore)
            │   ├── network/        # XiaomiMimoService (SSE), ExaSearchService (MCP), OpenAiSttService
            │   └── tts/            # NativeTtsManager (Android TextToSpeech & word-range callbacks)
            ├── util/
            │   └── HapticManager.kt # Tactile vibration engine (typewriter ticks, clicks, success)
            └── ui/
                ├── MainViewModel.kt
                ├── theme/          # Color (WyrdColors), Type (Space Grotesk), Theme (WyrdTheme)
                ├── components/     # TopBar, ChatInputBubble, AttachmentPopupMenu, BigSttOverlay, SpringLoadedPullToRecord, MessageItem, DrawerContent
                └── screens/        # HomeScreen, SettingsScreen
```

---

## Build & Run

```bash
./gradlew assembleDebug
```

Output artifact:
```text
app/build/outputs/apk/debug/app-debug.apk
```
