# WYRD Web (Desktop & Browser Companion) (v1.6.0)

A minimal, hyper-efficient web companion for **WYRD**, synchronizing conversations, settings, and cognitive states in real-time with the Android application via Supabase.

## Architecture & Tech Stack

* **Framework**: React 19 + TypeScript + Vite 8
* **Styling**: Tailwind CSS v4 + Space Grotesk & JetBrains Mono typography
* **Markdown & Tables**: Custom brutalist reading formatter (`readingFormatter.ts`) with responsive Markdown Tables (`| Col |`), styled headers, aligned cells, protocol-validated links, and 1:1 character index TTS sync.
* **PDF Extraction**: Zero-dependency client-side PDF text extractor (`pdfExtractor.ts`) using browser `DecompressionStream` and `/ToUnicode` CMap parsing for both chat attachments and Project Knowledge Base files.
* **Audio & Speech**: OpenAI **`gpt-4o-mini-transcribe`** for speech-to-text (STT) + Native Web Speech API (`window.speechSynthesis`) for zero-latency Text-to-Speech (TTS)
* **Backend & Sync**: Supabase (PostgreSQL + Row-Level Security + Realtime Replication + Object Storage)
* **Icons & UI**: Official WYRD Brand Starburst Eye Logo + Lucide React

## Key Capabilities

1. **Collapsible Sidebar & Breadcrumbs**:
   * Compact 270px sidebar with smooth animated collapse/expand toggle on desktop and responsive mobile drawer.
   * Breadcrumb display in the top navigation bar showing the active project and conversation thread title.
2. **Warm Brutalist Light & Dark Modes**:
   * Exact parity with Android `LightWyrdColors` and `DarkWyrdColors`: warm parchment `#F9F9F7`, surfaces `#F0EFEA` / `#E8E7E1`, cards `#DFDED8`, border `#CECDC4`, deep black `#141414`.
   * Dynamic eye logo & favicon switching: crisp white starburst (`/app_logo.png`) in dark mode, deep black starburst (`/app_icon_trans_black.png`) in light mode.
   * Theme controls: quick toggle icon in top bar and three-way preference (`System`, `Dark`, `Light`) in Settings.
3. **Audio Experience (STT, Double-Click to Hear TTS & Word-by-Word Highlighting)**:
   * **Voice Input (STT)**: Microphone button in prompt capsule records audio via `MediaRecorder` with live recording timer and waveforms, then transcribes into text using OpenAI **`gpt-4o-mini-transcribe`** via a dedicated OpenAI API key. The capsule keeps a constant height while recording and transcribing (recording controls swap in place without changing the capsule's shape).
   * **Word-by-Word Highlighting (Android Parity)**: Synchronous brutalist pill highlighting as `window.speechSynthesis` speaks assistant responses. Scoped `activeHighlightRange` props and memoized `MessageItem` / `FormattedMessage` components with container-level event delegation ensure zero unnecessary re-renders across the chat history.
   * **Double-Click Word to Hear (TTS Word-Seek)**: Double-clicking any word in an assistant response initiates browser speech synthesis directly from that exact word onwards with 1:1 character index accuracy.
   * **Floating Play/Pause Controller & Real-Time Streaming TTS**: Brutalist 54px circular button anchored above the send button on the right. Tapping Play while the model is actively streaming immediately begins reading the response out loud in real time from incoming tokens.
4. **Cross-Device Cloud Sync**:
   * Synchronizes `chats`, `messages`, `projects`, `project_files`, and user `profiles` (`username`, `system_prompt`, `active_model`, `reasoning_effort`) in real-time with the WYRD Android app.
   * **Deterministic Message Truncation & Shared Attachment Protection (Edit, Redo & Branch)**: Editing or redoing prompts permanently truncates all subsequent turns locally and deletes them from Supabase while protecting storage objects still referenced by edited prompts or branched conversations (`getProtectedStoragePaths`).
   * Visibility-gated periodic fallback sync (25s interval, paused when the browser tab is hidden) plus instant Realtime subscriptions.
5. **User Profiles, Smart Titles & System Prompt**:
   * Global `username` and customizable `system_prompt` stored in the `profiles` table.
   * Model preference selection (`mimo-v2.6-pro`, `mimo-v2.6-flash`) and official Xiaomi MiMo **Thinking** toggle (`medium` / `off`).
   * **Answer Provenance Caption**: Each assistant reply shows a small muted caption with the answering model and whether reasoning was on or off at answer time (`reasoning_enabled` persisted per message).
   * **Smart 4-Word Auto-Generated Titles**: Automatically summarizes the first turn into a concise 3–4 word conversation title via background LLM completion (`generateChatTitle`).
6. **Brutalist Chat Experience & Android Parity**:
   * **Message Bubble Actions & Pointer-Anchored Dropdown**:
     * **Double-Click Pointer Dropdown**: Double-clicking anywhere on a user prompt opens the action context menu anchored precisely at the mouse pointer location.
     * **Edit**: Loads user prompts and their existing attachments back into the input capsule with autofocus and an "Editing prompt" cancel bar; submitting permanently truncates subsequent conversation turns while preserving kept attachments.
     * **Redo**: Permanently deletes following assistant responses locally and in Supabase, and re-streams a fresh generation from the model.
     * **Branch**: Slices the conversation thread up to the selected message into a new isolated session titled `"<Title> (Branch)"`.
     * **Copy**: One-click prompt/response copying to clipboard with confirmation feedback.
   * **Instant Top-Snapped Prompt on Send & Universal Auto-Scroll on Chat Open**.
7. **Multimodal Attachments, PDF Parsing, Supabase Object Storage & Auto-Cleanup**:
   * **Dedicated Supabase Storage Bucket (`attachments`)**: Uploaded images and files are automatically uploaded to the public Supabase Object Storage bucket (`attachments/${userId}/${timestamp}_${cleanFileName}`).
   * **Shared Attachment Protection**: Deleting a chat, wiping unpinned chats, or editing a turn inspects all other chats and project files before deleting from Supabase Storage so shared branch attachments are never broken.
   * **Client-Side Image Optimization**: High-resolution photos are downscaled to 1280px max dimension via HTML5 Canvas and compressed to 85% JPEG before upload.
   * **PDF, Text & Code Extraction**: `.pdf` files are parsed client-side via `pdfExtractor.ts` (`extractTextFromPdf`), and text/code files are read up to 80KB into structured prompt fences (`[Attached File: <name>]`).
8. **Browser Inference & Parallel Dual-Engine Web Search (`web_search`)**:
   * Direct SSE streaming via Xiaomi MiMo with automatic cluster routing (`tp-*` -> Token Plan, standard -> Pay-As-You-Go) and fast-fail on HTTP 401/403 errors.
   * **Autonomous Unified Multi-Engine Web Search Grounding (`web_search`)**:
     * Independent toggles in Settings for **Tavily Search** (`wyrd_tavily_enabled`) and **Exa Search** (`wyrd_exa_enabled`).
     * **Parallel Execution Engine**: When both Tavily and Exa are enabled in Settings, the browser executes both Tavily and Exa concurrently in parallel via `Promise.all()`, returning live factual sources alongside semantic community/forum/review discussions.
     * **Fallback Resilience**: Automatically falls back to Exa when Tavily is disabled, unconfigured, or returns zero results.
9. **Projects & Knowledge Base Spaces**:
   * **Custom Project Colors & Palettes**: 9-color curated warm brutalist palette with swatches in the project modal.
   * **Custom System Prompt Override & Knowledge Base Source Files**: Attach `.md`, `.txt`, `.pdf`, and code files to any project. Text (including extracted PDF text) is injected into the LLM context for all chats within that project.

## Local Development & Spin-Up

```bash
# Navigate to web project directory
cd web

# Install dependencies (if not already installed)
npm install

# Start local development server
npm run dev

# Or lint and build for production
npm run lint && npm run build
```

## Environment Variables

Configured in `.env`:
* `VITE_SUPABASE_URL`: Supabase project endpoint
* `VITE_SUPABASE_ANON_KEY`: Supabase public publishable/anon key

## Netlify Deployment (Zero-Server / Serverless)

WYRD Web is a 100% static Single Page Application (SPA). It requires **no server**:
* Build Command: `npm run build`
* Publish Directory: `dist`
* SPA redirects are pre-configured in [`netlify.toml`](file:///Users/gioelezorzi/Agent_Workspace/_Android/WYRD/web/netlify.toml) and [`public/_redirects`](file:///Users/gioelezorzi/Agent_Workspace/_Android/WYRD/web/public/_redirects).
