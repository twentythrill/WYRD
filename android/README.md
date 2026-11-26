# WYRD — Native Android AI Client (v1.6.9)

WYRD is a high-performance, minimalist, brutalist native Android chat application built with Kotlin and Jetpack Compose. Operating strictly client-side with zero intermediary servers, it combines cloud LLM intelligence (Xiaomi MiMo & OpenAI GPT 6.1 Sol) with parallel dual web search engines (Tavily AI for live web and Exa AI for semantic exploration), native high-fidelity speech synthesis (TTS) with interactive word tracking, hardware-accelerated speech-to-text (`gpt-4o-mini-transcribe`), and complete local session persistence.

---

## Core Features

### 1. Visual Design & Theming
- **Dual DitherMono Theme**:
  - **DitherMono palette** (`ui/theme/Color.kt`): one `surface` ground, `ink` for text/icons/primary button, and `fill` / `hairline` / `ink-muted` as ink at 8% / 16% / 64% pre-composited on `surface`. No accent colours.
  - **Dark**: `#CECDC4` ink on true AMOLED black `#000000` (fill `#100F0F`, hairline `#21211F`, muted `#84837D`).
  - **Light**: `#100F0F` ink on `#FEFCF1` (fill `#EBE9DF`, hairline `#D8D6CD`, muted `#666460`).
  - **Theme Selection**: Settings dropdown under **APPEARANCE** supporting **System** (tracks OS theme), **Light** (manual forced light mode), and **Dark** (manual dark).
  - **Adaptive Insets**: Edge-to-edge status and navigation bar icons invert dynamically for continuous contrast.
  - **Application Icon**: High-contrast dark variant icon (`app_icon.jpg`).
  - **Custom Vector Iconography (`WyrdIcons`)**: Lightweight custom `ImageVector` set replacing `material-icons-extended`, paired with R8 code shrinking and resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`).
- **Typography & Brand Mark**:
  - Global variable Space Grotesk typography (`MaterialTheme`) with calibrated weights.
  - Home screen centered eye mark (30% opacity, auto-switching dark/light assets), personalized greeting ("Welcome Back, **[Name]**"), and `(◕‿◕)` kaomoji.
  - **Transparent Edge-to-Edge System Bars & Multi-Stop Fade Gradients**:
    - Status bar and 3-button navigation bar are 100% transparent (`android.graphics.Color.TRANSPARENT`) with OS contrast enforcement disabled (`isStatusBarContrastEnforced = false`, `isNavigationBarContrastEnforced = false`), eliminating gray scrims.
    - **Top Seamless Gradient**: Multi-stop, slightly transparent vertical gradient that smoothly dissolves scrolling messages behind the floating top bar and status bar up to the top screen edge across both dark and light themes.
    - **Bottom Seamless Scrim & Fade**: Multi-stop, slightly transparent vertical gradient (`totalBottomScrimHeight = navBarBottom + 108.dp`) that smoothly dissolves scrolling messages through the floating input capsule and Android 3-button system navigation bar down to the bottom screen edge across both dark and light themes.
- **Markdown, Code Block & Tabular Layout Engine (`ReadingMarkdownParser`)**:
  - **Native Markdown Code Blocks (`CodeBlockCard`)**: Fenced code blocks (` ```lang ... ``` `) render inside dedicated, DitherMono cards adhering to active theme tokens (`wyrdColors.surface`, `1.dp` border `wyrdColors.border`, `12.dp` rounded corners). Features:
    - **Header Bar**: Displays language title (`FontFamily.Monospace`, 11sp) on `wyrdColors.surfaceElevated` alongside a one-tap copy button with instant mechanical haptic feedback and a checkmark ("Copied") state in `ink`.
    - **Natural Line Wrapping & Text Selection**: Monospace code body (`17sp`, `23sp` line height, matching body text) with natural soft line-wrapping, eliminating horizontal scrolling even for long code lines or minified tokens, and non-jumping text selection (`SelectionContainer` with `suppressBringIntoView()`).
    - **Audio & TTS Parity**: Preserves 1:1 character index offsets in `plainBuilder` and `annotatedBuilder`, allowing word-by-word reading highlight pills and tap-to-seek playback inside code blocks.
    - **Streaming Resilient**: Streams in real-time without layout jumps even while code blocks are unclosed mid-stream.
  - **Markdown Tables (`MarkdownTableCard`)**: Specialized parser rendering aligned, padded monospaced tabular grids with header separation, dynamic column width calculation (`minWordWidth = (longestWordChars * 11f + 32f).coerceAtLeast(125f)`) preventing multi-character word hyphenation, and 1:1 character index parity in `plainBuilder` to ensure uninterrupted TTS word-by-word tracking.
  - **Typography & Rich Elements**: Headers (`#`, `##`, `###` and standalone bold lines `**Heading**` / `__Heading__` with 20dp top and 8dp bottom Gestalt margins), blockquotes, ordered/unordered lists with dedicated fixed-width badge containers (`20dp` / `28dp`) for hanging indents, bold/italic/strikethrough (`~~text~~`) formatting, and safe protocol-validated clickable `[label](url)` links.
- **Zero-Shift Streaming Typography & 120Hz Scroll Pipeline**:
  - `MainActivity` explicitly negotiates the highest hardware display refresh rate mode (`preferredDisplayModeId` & `preferredRefreshRate`, 120Hz+) on creation and resume.
  - `StreamingAssistantMessageItem` and `AssistantMessageItem` share the exact same condensed 23sp line-height typographic pipeline, `@Immutable` AST block models (`formatMarkdownForReading`), `LazyColumn` `contentType` slot reuse, remembered item callbacks, and conditional `onPlaced` coordinate tracking (attached strictly to the active TTS spoken message), eliminating per-frame scroll overhead.
- **Answer Provenance Caption**: `AssistantMessageItem` renders a small muted caption beneath each answer (e.g. `MiMo 2.6 Pro · Reasoning on`) from the persisted `modelName` and `reasoningEnabled` message fields.
- **Selection & Double-Tap Actions**:
  - Native long-press text selection with system copy handles across all messages, streaming content, and reasoning blocks. Powered by a no-op `BringIntoViewSpec` provided to `LazyColumn` (`LocalBringIntoViewSpec`) and non-consuming gesture timeouts, ensuring text selection is 100% rock-solid with zero jumping, viewport snapping, or scrolling behind the floating capsule anywhere in messages of any length.
  - Double-tapping any sent user bubble opens an action menu anchored precisely at the touch coordinates:
    - **Copy**: Copies the entire prompt text to the system clipboard in a single tap with instant haptic confirmation.
    - **Edit**: Loads the prompt and all its existing attachments back into the input field with autofocus, an "Editing prompt" cancel banner, and hardware back button support. Replaces subsequent conversation turns on submit while preserving kept attachments.
    - **Branch into a new chat**: Slices conversation history up to that turn into a new isolated session titled `"<Title> (Branch)"`.
    - **Redo**: Truncates following assistant responses and re-streams a fresh generation from the model.

### 2. Chat Input, Soft Keyboard & Multimodal Multi-Attachments
- **Capsule Input & Keyboard Insets**:
  - Multiline text input with adaptive height (44dp–180dp), reactive `.imePadding()`, and auto-focus on launch.
  - **Keyboard-Persistent Enter**: The Enter key inserts a newline and keeps the soft keyboard open. The key event is handled and fully consumed in `onPreviewKeyEvent`, IME actions are no-ops (never `clearFocus`), and the tap-to-dismiss surface uses plain tap detection so it can never act as a focus target.
  - In existing chats, when the soft keyboard appears, both `LazyColumn` and the input capsule adapt to IME insets; the message list auto-scrolls with tight, comfortable breathing room directly above the floating capsule, removing unnecessary empty space and keeping the conversation visible.
  - The floating TTS button gracefully fades out while typing (unless actively playing) to maximize visible viewport.
  - **Instant Top-Snapped Prompt on Send**:
    - The moment the user sends a prompt, the message list cleanly and smoothly animates that prompt directly to the top of the screen (positioned comfortably below the top bar).
    - As the LLM streams its response, text flows naturally downwards from the user prompt into clear view, allowing the user to begin reading immediately without any jumping, erratic back-and-forth bounce, or auto-scroll disruption.
    - Full manual scroll control is preserved with zero artificial voids or empty black space, allowing the user to freely scroll up or down at any time.
  - **Dynamic Scroll-to-Bottom Button & Universal Bottom Positioning**:
    - **Universal True Bottom Anchoring**: When opening any conversation from the history drawer or on launch, the message list automatically and consistently scrolls to the true bottom using a layout bottom anchor (`chat_bottom_anchor`), settling reliably through multi-stage layout measurement passes so conversation history is immediately ready at the latest message above the capsule.
    - Centered directly above the text input capsule with smooth scale and fade animations (`fadeIn` + `scaleIn` / `fadeOut` + `scaleOut`).
    - Automatically appears whenever the user scrolls up away from the bottom to read previous conversation turns or review earlier text.
    - Tapping the 44dp brutalist circular arrow button fires tactile haptic feedback and smoothly scrolls the conversation down to the latest message (or active streaming chunk), fading out smoothly once the bottom is reached.
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
- **Attachment Processing & Preview Bar (`AttachmentHelper` & `PdfTextExtractor`)**:
  - **Images**: Uses `ImageDecoder.setTargetSize` (API 28+) and two-pass subsampled `BitmapFactory.Options` to downscale large photos directly during decode to a maximum dimension of 1280px without full-resolution heap spikes, compresses to 82% quality JPEG in private app storage, and encodes Base64 on demand.
  - **Files**: Inspects MIME type and file extension; reads UTF-8 code and text documents (up to 80KB each) into contextual message memory.
  - **PDF Documents**: Extracts embedded text from PDF streams and `/ToUnicode` CMaps via `PdfTextExtractor` for chats and Project Knowledge Base files, and also rasterizes PDF pages into high-resolution images via `android.graphics.pdf.PdfRenderer` when no selectable text is present.
  - **Attachments Preview Bar**: Horizontal scrolling bar directly above the input capsule showing thumbnail chips, filename, file size, individual `✕` remove buttons, and a "Clear All" action.
  - **Prompt Flexibility**: The send button activates if text is typed or any attachments are present, allowing one-tap visual questions.
- **Multimodal Intelligence, Supabase Object Storage & Cloud Parity**:
  - **Dedicated Supabase Storage Bucket (`attachments`)**: When syncing messages to Supabase, local attachments are automatically uploaded to the public Supabase Object Storage bucket (`attachments/${userId}/${timestamp}_${fileName}`). Only the lightweight public URL is recorded in the PostgreSQL `messages` table, preventing database quota exhaustion and eliminating Base64 payload bloat.
  - **Shared Attachment Protection**: Deleting conversations, wiping unpinned chats, or editing/redoing turns automatically cross-references remaining messages (`getAllMessages()`) and project files so attachments shared across branched chats or retained in edited prompts are never deleted.
  - **Asynchronous Image Loading with Memory & Disk Cache (`AttachmentImageLoader`)**: Asynchronously resolves local files, `data:image/...;base64,...` URIs, and remote Supabase Storage URLs on `Dispatchers.IO` using an in-memory `LruCache`, subsampled `BitmapFactory` decoding, and an on-device MD5 disk cache in `context.cacheDir/image_cache/`.
  - **Tap-to-Open Attachment Actions**: Tapping any image opens it in the system viewer via `FileProvider` or browser; tapping file attachments launches the appropriate document viewer.

### 3. Speech Synthesis (TTS) & Highlighting
- **Native Android Engine**: Built entirely on `android.speech.tts.TextToSpeech` with instant initialization, application-scoped lifecycle, and local offline voice priority.
- **Real-Time Streaming Playback**:
  - In conversational STT mode, or whenever tapping the floating Play button while the model is actively streaming an answer, speech synthesis starts speaking as soon as the initial tokens arrive without waiting for generation to complete.
  - Dynamically extracts semantic speech chunks at sentence and clause boundaries in real-time via `findStreamingCutPoint`, chaining subsequent chunks seamlessly via `TextToSpeech.QUEUE_ADD` without gaps or stuttering while the LLM continues generating in the background.
  - Streaming word-by-word brutalist highlighting illuminates spoken words live on the active streaming card (`StreamingAssistantMessageItem`) and transitions smoothly to the finalized message bubble upon completion.
  - **Streaming Pause & Resume Stability**: Pausing during live token streaming immediately halts speech synthesis and locks the playback state (`isStreamingPaused`), buffering subsequent incoming tokens without auto-resuming playback. Playback stays paused until the user explicitly hits Resume or taps a word in the text.
- **Continuous Background Playback**: Audio continues uninterrupted across app switches or device screen locks, stopping only upon explicit pause, tap-to-read, or chat navigation.
- **Engine Provider & Voice Profile Selection**:
  - Configurable under Settings -> **SPEECH SYNTHESIS (TTS)** with **System Default**, **Samsung text-to-speech** (`com.samsung.SMT`), and **Google Speech Services** (`com.google.android.tts`).
- **Intelligent Utterance Chunking**: Segments long texts into semantic chunks ($\le 2,000$ characters) across paragraphs, line breaks, sentence endings, and word boundaries.
- **Word-by-Word Highlighting & Tap-to-Seek**: Tracks `onRangeStart` to invert each active spoken word to a high-contrast pill with 1:1 character index accuracy, upper-third viewport snapping, and tap-to-seek playback.

### 4. Speech-to-Text Pipeline (STT)
- **Spring-Loaded Pull-to-Record**: Swiping right-to-left across the screen (~95dp threshold) features elastic spring resistance with progressive mechanical haptic ticks, collapsing the keyboard and opening the fullscreen STT screen.
- **Interactive Recording Controls**:
  - **Top Model & Thinking Selector**: Elevated brutalist capsule pill positioned directly above the central recording button, allowing the user to switch the active LLM model (`MiMo 2.6 Pro`, `MiMo 2.6 Flash`, or `GPT 6.1 Sol`) and toggle Thinking (on / off) mid-speaking before hitting send.
  - **Center Glowing Button**: Stops recording, transcribes audio via OpenAI **`gpt-4o-mini-transcribe`**, pastes into prompt, and immediately sends with conversational streaming TTS.
  - **Stop Button**: Stops recording, transcribes audio via `gpt-4o-mini-transcribe`, and pastes into the prompt for review without sending.
  - **Cancel (✕) Button & Swipe Right**: Discards captured audio and collapses the overlay.

### 5. Intelligence, Streaming & Tools
- **Multi-Model LLM Intelligence**:
  - Model selection: **Xiaomi MiMo 2.6 Pro** (`mimo-v2.6-pro`, deep complex reasoning), **Xiaomi MiMo 2.6 Flash** (`mimo-v2.6-flash`, high-speed multimodal reasoning), and **OpenAI GPT 6.1 Sol** (`gpt-6.1-sol`, high-performance frontier reasoning).
  - **OpenAI GPT 6.1 Sol Integration**:
    - Direct SSE streaming via OpenAI's Responses API (`https://api.openai.com/v1/responses`) with Chat Completions fallback, using standard Bearer authorization and flat tool definitions.
    - **Reasoning Architecture & Collapsible Stream**:
      - When Thinking is ON: runs with `"mode": "pro"`, hardcoded `"effort": "medium"`, and `"summary": "detailed"`, streaming typed SSE reasoning deltas (`response.reasoning_summary_text.delta`) into real-time collapsible thinking blocks in the UI with token-usage fallback heuristics.
      - When Thinking is OFF: reasoning is completely shut off (`"mode": "standard"`, `"effort": "low"`, no reasoning summary or collapsible block rendered).
  - **Xiaomi MiMo Streaming**:
    - Dual-cluster auto-routing (Token Plan vs. Pay-As-You-Go) with optional custom base URL and dual-header (`Authorization` + `api-key`) authentication; fails fast on HTTP 401/403 authentication errors.
    - **Official Thinking Parameter & On/Off Toggle**:
      - When enabled: sends `"thinking": {"type": "enabled"}` along with `temperature: 1.0` and a 32,768 completion token budget.
      - When disabled: sends `"thinking": {"type": "disabled"}` along with `temperature: 0.7` and an 8,192 token allocation for instant direct answers.
  - **Unified OpenAI API Configuration (Inference & STT)**: In Settings, the OpenAI configuration section manages the API key for both GPT 6.1 Sol inference and `gpt-4o-mini-transcribe` speech-to-text.
  - `ThinkAndToolParser`: High-precision streaming parser demuxing reasoning tags (`<think>`, `<thought>`, `<thinking>`) to reasoning memory and buffering tool call XML (`<tool_call>`, `<function_call>`, `<tool>`) without leaking tokens into visible text.
- **Continuous Background Execution & Foreground Service**:
  - Powered by `WyrdBackgroundService`: an Android Foreground Service (`dataSync` type on Android 14+) with CPU partial `WakeLock` and an application-scoped coroutine supervisor (`applicationScope`).
- **Autonomous Unified Multi-Engine Web Search Grounding (`web_search`)**:
  - **Unified Tool Interface (`web_search`)**: Exposes a single `web_search` tool to the model.
  - **Parallel Execution Engine**: When both Tavily and Exa are enabled in Settings, the pipeline executes both search engines concurrently in parallel via Kotlin coroutines (`coroutineScope { async }`), combining live factual web data (Tavily) and deep semantic community discussions (Exa).
  - **Multi-Tiered Fallback Resilience**: If Tavily fails or yields zero results, the engine automatically falls back to Exa API (provided Exa has not been explicitly disabled in Settings); if neither API is working or both are disabled in settings, it seamlessly falls back to Exa's keyless MCP endpoint (`https://mcp.exa.ai/mcp`).
- **Concise Auto-Naming**: Automatically summarizes conversations into punchy 3 to 4-word titles.

### 6. Privacy & Data Storage
- **Local Room SQLite (v11)**: On-device database for `chats`, `messages`, `projects`, and `project_files` with canonical `8-4-4-4-12` UUID `syncId`s, `modelName` and `reasoningEnabled` persistence, and 6 foreign-key/syncId indices (`MIGRATION_9_10`, `MIGRATION_10_11`).
- **Encrypted Storage**: Sensitive API keys stored in Keystore-backed `EncryptedSharedPreferences`, warmed up asynchronously on `Dispatchers.IO` off the main thread.

### 7. Cloud Synchronization (Supabase & Web Companion)
- **Two-Way Real-Time Sync (`SupabaseSyncManager`)**:
  - Shares the application-wide `OkHttpClient` pool.
  - Synchronizes all 4 `profiles` fields (`username`, `system_prompt`, `active_model`, `reasoning_effort`), `projects`, `project_files`, `chats` (including nullable `project_id`), and `messages` (including `model_name` and `reasoning_enabled`).
  - **Batched Message Pull & Push**: Pulls all remote messages in a single PostgREST `user_id=eq.$userId` query and batch-inserts unsynced local messages in a single POST request, eliminating $N+1$ HTTP round-trips.
  - **Remote Deletion Reconciliation**: Reconciles remote deletions of projects, project files, chats, and messages against tracked synced IDs so deletions performed on Web immediately reflect on Android without re-uploading.

### 8. Projects & Knowledge Base Spaces
- **Custom System Prompt Override & Knowledge Base Source Files**: Attach `.md`, `.txt`, `.pdf`, and code documents to any project. Text is extracted client-side via `AttachmentHelper` and `PdfTextExtractor` and injected into contextual knowledge base memory.
- **Custom Project Colors & Palettes**: 9-color curated project palette with two-way Supabase sync.

---

## Directory Structure

```text
WYRD/android/
├── README.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/
        └── java/com/wyrd/app/
            ├── WyrdApp.kt                  # Application initialization, shared OkHttpClient & async Keystore warmup
            ├── MainActivity.kt             # Single Activity hosting edge-to-edge Compose navigation & theme provider
            ├── service/
            │   └── WyrdBackgroundService.kt # Foreground service for background LLM streaming
            ├── data/
            │   ├── local/                  # Room AppDatabase (v11), ChatEntity, MessageEntity, ProjectEntity, ProjectFileEntity, DAOs
            │   ├── preferences/            # PreferencesManager (DataStore) & SecureKeyStorage (Keystore)
            │   ├── network/                # XiaomiMimoService, TavilySearchService, ExaSearchService, OpenAiSttService, SupabaseSyncManager, AudioRecorderHelper
            │   └── tts/                    # NativeTtsManager
            ├── util/
            │   ├── AttachmentHelper.kt     # Subsampled image compression, file & PDF processing
            │   ├── PdfTextExtractor.kt     # Pure-Kotlin PDF stream & CMap text extractor
            │   └── HapticManager.kt        # Tactile vibration engine
            └── ui/
                ├── MainViewModel.kt
                ├── theme/                  # Color, Type, Theme, WyrdIcons
                ├── components/             # TopBar, ChatInputBubble, AttachmentPopupMenu, AttachmentImageLoader, BigSttOverlay, SpringLoadedPullToRecord, MessageItem, ProjectDialog, ReadingMarkdownParser, DrawerContent
                └── screens/                # HomeScreen, SettingsScreen
```

---

## Build & Run

```bash
./gradlew assembleDebug assembleRelease lintDebug
```

Output artifacts:
```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```
