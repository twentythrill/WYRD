# WYRD Web (Desktop & Browser Companion) (v1.1.0)

A minimal, hyper-efficient web companion for **WYRD**, synchronizing conversations, settings, and cognitive states in real-time with the Android application via Supabase.

## Architecture & Tech Stack

* **Framework**: React 19 + TypeScript + Vite 8
* **Styling**: Tailwind CSS v4 + Space Grotesk & JetBrains Mono typography
* **Audio & Speech**: OpenAI Whisper (`whisper-1`) for speech-to-text (STT) + Native Web Speech API (`window.speechSynthesis`) for zero-latency Text-to-Speech (TTS)
* **Backend & Sync**: Supabase (PostgreSQL + Row-Level Security + Realtime Replication)
* **Icons & UI**: Official WYRD Brand Starburst Eye Logo + Lucide React

## Key Capabilities

1. **Collapsible Sidebar & Breadcrumbs**:
   * Compact 270px sidebar with smooth animated collapse/expand toggle on desktop and responsive mobile drawer.
   * Breadcrumb display in the top navigation bar showing the active conversation thread title.
2. **Warm Brutalist Light & Dark Modes**:
   * Exact parity with Android `LightWyrdColors` and `DarkWyrdColors`: warm parchment `#F9F9F7`, surfaces `#F0EFEA` / `#E8E7E1`, cards `#DFDED8`, border `#CECDC4`, deep black `#141414`.
   * Dynamic eye logo & favicon switching: crisp white starburst (`/app_logo.png`) in dark mode, deep black starburst (`/app_icon_trans_black.png`) in light mode. Browser favicon automatically adapts to system color scheme (`(prefers-color-scheme: light)`) and in-app theme selection to ensure high contrast against browser tabs.
   * Theme controls: quick toggle icon in top bar and three-way preference (`System`, `Dark`, `Light`) in Settings.
3. **Audio Experience (STT & Tap/Double-Click to Hear TTS)**:
   * **Voice Input (STT)**: Microphone button in prompt capsule records audio via `MediaRecorder` with live recording timer and waveforms, then transcribes into text using OpenAI Whisper API (`whisper-1`).
   * **Double-Click Word to Hear (TTS Word-Seek)**: Parity with Android's tap-to-read feature. Double-clicking any word in an assistant response initiates browser speech synthesis from that exact word onwards. Includes smart bilingual detection (Italian vs English) and natural voice selection.
   * **Floating Play/Pause Controller**: Brutalist 50px circular button anchored above the send button on the right (matching Android's 54dp circle). Smoothly toggles Play and Pause states without textual clutter or pill badges.
4. **Cross-Device Cloud Sync**:
   * Synchronizes `chats`, `messages`, and user `profiles` in real-time with the WYRD Android app.
   * Auto-refreshes message streams whenever updates occur on either client.
5. **User Profiles & System Prompt**:
   * Global `username` and customizable `system_prompt` stored in the `profiles` table.
   * Model preference selection (`mimo-v2.6-pro`, `mimo-v2.6-flash`, etc.) and `reasoning_effort` toggle (`Off`, `Low`, `Medium`, `High`).
6. **Brutalist Chat Experience & Android Parity**:
   * **Message Bubble Actions (Android Parity)**: Hovering message bubbles reveals quick actions:
     * **Edit**: Loads user prompts back into the input capsule with autofocus and an "Editing prompt" cancel bar; submitting rewrites subsequent turns.
     * **Redo**: Truncates following assistant responses and re-streams a fresh generation from the model.
     * **Branch**: Slices the conversation thread up to the selected message into a new isolated session titled `"<Title> (Branch)"`.
     * **Copy**: One-click prompt/response copying to clipboard with confirmation feedback.
   * **Client-Side Draft New Chat**: Clicking "New Chat" resets to a clean draft session without inserting phantom rows into the database. Threads are created on-demand upon sending the first prompt.
   * **Clean Welcome Screen**: Zero-message and new chat states display the exact mobile app Welcome Screen (160px eye logo at 30% opacity, "Welcome Back, [Username]", and `(◕‿◕)`).
   * **Condensed Typography Parity (Android Parity)**: Tight, condensed reading rhythm matching Android's 23sp line height (`leading-[24px]` for markdown content, `leading-[23px]` for user bubbles, and `leading-[18px]` for reasoning blocks), eliminating excessive line spacing while maintaining clean legibility.
   * **Monochrome "Thinking" Block (Android Parity)**: Dedicated minimalist reasoning container titled "Thinking" with sleek Lightbulb icon and live streaming pulse indicator, collapsed by default to prevent visual jumping, and completely free of extraneous icons or colored emojis. Strictly suppressed when reasoning effort is "Off".
   * **Gemini-Style Top & Bottom Subtle Fade Gradients (No Solid Cut-Offs)**:
     * **Top**: Transparent TopBar sits directly over a 112px multi-stop subtle fade-to-background gradient, softly veiling text scrolling underneath without harsh solid bars.
     * **Bottom**: Floating controls and input capsule sit over a 176px multi-stop subtle fade gradient, dissolving messages smoothly behind the capsule with complete visual continuity.
   * **Dynamic Centered Scroll-to-Bottom Button**: 38px circular chevron button centered directly on top of the text input capsule. Smoothly fades and scales in only when scrolled up away from the bottom, and instantly fades out once bottom is reached.
   * **Universal Auto-Scroll on Chat Open (Android Parity)**: Opening or selecting any conversation immediately and reliably positions the view at the true bottom across all viewports using full container scroll offsets (`scrollTop = scrollHeight`), multi-stage layout settlement, programmatic scroll guards, and ResizeObserver tracking.
   * **Reading-First Scroll Control**: Strictly disables autoscrolling during incoming answer streaming so your reading flow is never broken by jumping viewports.
   * **Duplicate Protection**: Concurrency locks on input submission and state-level deduplication prevent duplicate prompt dispatches or ghost bubbles on rapid interactions or realtime sync events.
   * **Race-Condition-Free Thread Switching**: Cancellation tokens ensure async DB loading for chat transitions never overwrites an active thread.
   * Pinned threads, rename, and safe thread deletion with a confirmation modal ("Delete chat? [Cancel] [Delete]").
   * Danger Zone action to bulk-wipe unpinned chats while preserving pinned threads.
7. **Multimodal Attachments, Supabase Object Storage & Auto-Cleanup**:
   * **Dedicated Supabase Storage Bucket (`attachments`)**: Uploaded images and files are automatically uploaded to the public Supabase Object Storage bucket (`attachments/${userId}/${timestamp}_${cleanFileName}`). The PostgreSQL database stores only tiny public URLs (~150 bytes), entirely preventing database bloat and protecting the free Supabase database quota.
   * **Automatic Storage Cleanup on Chat Deletion**: Whenever a chat is deleted (or unpinned chats are purged in bulk), all associated storage objects in Supabase Storage (`attachments` bucket) are identified and automatically deleted via the Storage API. Individual turn edits/redo actions also clean up obsolete attachments.
   * **Proactive Orphan Cleaner**: On application startup, the storage cleaner cross-references all objects stored under the user's storage prefix against active messages in the database, automatically purging any unreferenced/leftover files to keep Supabase storage minimal and clean.
   * **Client-Side Image Optimization**: High-resolution photos are automatically downscaled to a maximum dimension of 1280px via HTML5 Canvas and compressed to 85% JPEG binary Blobs before upload for instantaneous transmission.
   * **Text & Code Extraction**: Text and source code files are parsed client-side with a null-byte binary check (up to 80KB) and injected into structured prompt fences (`[Attached File: <name>]`), while original files are saved to Supabase Storage.
   * **Staged Attachments Preview Bar**: Horizontal carousel above the capsule with thumbnail previews, filenames, formatted file sizes, and instant delete buttons.
   * **Clipboard Paste**: Paste screenshots or copied images directly from the clipboard (`Cmd+V` / `Ctrl+V`) into the chat input.
   * **Drag & Drop**: Drag images or files from the desktop over the chat area with an animated blur backdrop and drop target.
   * **Cross-Platform Parity**: Images and files uploaded from Web render smoothly on Android, and photos captured on Android render on Web in high resolution with click-to-view in a new tab.
8. **Browser Inference & Dual Endpoint Auto-Routing**:
   * Direct SSE streaming via Xiaomi MiMo with automatic cluster routing based on API key prefix (`tp-*` -> Token Plan, standard -> Pay-As-You-Go).
   * Native `thinking` parameter and token budget configuration (`Off`, `Low` 1024, `Medium` 8192, `High` 32768) matching Android. When "Off", thinking scratchpad is completely disabled and reasoning chunks are never rendered or saved.
   * Dual-header authentication (`Authorization: Bearer` and `api-key`).
   * **Multimodal Tool Suppression**: Suppresses function calling `tools` when images are present in the conversation, eliminating HTTP 400 Bad Request gateway errors on multimodal payloads.
   * **Streaming Demuxer (`ThinkAndToolParser`)**: Zero-leak XML streaming parser that mirrors Android's demuxer to isolate `<think>` reasoning and `<tool_call>` definitions, completely preventing raw tool XML from displaying in chat bubbles or persisting into DB content.
   * **Instant Generation Abort**: Tapping the Stop square fires an `AbortController` signal that immediately closes HTTP streams and sockets.
   * **Autonomous Web Search & Factual Grounding (Exa AI)**:
     * **Multi-Result Snippet Highlights**: Queries Exa with `numResults: 5` and requests snippet highlights via `contents: { highlights: true }`, retrieving URLs, publication dates, and extracted highlights.
     * **Zero-Result Guard**: Intercepts queries returning 0 results at the tool boundary before reaching the model, returning: `Search returned 0 results. No credible sources or records were found for this query.`
     * **Structured Text Blocks & Verification Trailer**: Formats positive results into structured blocks with URL, publication date, and extracted highlights, and appends an enforcement trailer to enforce strict factual grounding and eliminate hallucinations.
     * **Clean Grounding & Synthesis**: Executes Exa search in Round 1 with animated status ("Searching web with Exa for: '...'..."), transitions smoothly to "Synthesizing answer...", suppresses tools in Round 2, and guides the model to produce a verified answer without hesitation, looping, or failed synthesis.
     * Configurable with personal Exa API key or public tier fallback in Settings.
   * **30-Turn Context Window Clamping**: Clamps message payload to the most recent 30 conversation turns to preserve token budgets and avoid gateway limits.

## Local Development & Spin-Up

```bash
# Navigate to web project directory
cd _Web/WYRD

# Install dependencies (if not already installed)
npm install

# Start local development server
npm run dev

# Or build for production
npm run build
```

## Environment Variables

Configured in `.env`:
* `VITE_SUPABASE_URL`: Supabase project endpoint
* `VITE_SUPABASE_ANON_KEY`: Supabase public publishable/anon key

## Netlify Deployment (Zero-Server / Serverless)

WYRD Web is a 100% static Single Page Application (SPA). It requires **no server**:
* Build Command: `npm run build`
* Publish Directory: `dist`
* SPA redirects are pre-configured in [`netlify.toml`](file:///Users/gioelezorzi/Desktop/Agent_Workspace/_Web/WYRD/netlify.toml) and [`public/_redirects`](file:///Users/gioelezorzi/Desktop/Agent_Workspace/_Web/WYRD/public/_redirects).
