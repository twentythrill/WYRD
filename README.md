# WYRD (v1.5.7)

A modern, high-performance, minimalist AI client ecosystem operating strictly client-side with zero intermediary servers. Features real-time bidirectional cloud synchronization across Android and Desktop Web via Supabase.

---

## Ecosystem Architecture

WYRD is organized into two isolated clients that synchronize cognitive states, conversation threads, and preferences in real time:

```text
WYRD/
├── android/          # Native Android AI Client (Kotlin + Jetpack Compose)
│   ├── app/          # App source, UI components, Room database, network services
│   ├── build.gradle.kts
│   └── README.md     # Android client architecture & documentation (v1.5.7)
│
├── web/              # Desktop & Browser Companion (React 19 + TypeScript + Vite)
│   ├── src/          # React components, services, Supabase integration
│   ├── package.json
│   └── README.md     # Web client architecture & documentation (v1.5.7)
│
├── .gitignore        # Unified repository ignore rules
└── README.md         # Master repository overview (v1.5.7)
```

---

## Key Highlights

### 1. Unified Design Language (Warm Brutalism)
* **AMOLED Dark**: Pure black (`#000000`) background, `#141414` elevated surfaces, `#1A1A1A` message bubbles, `#CECDC4` bone-white accents.
* **Inverted Light**: Warm off-white (`#F9F9F7`) background, `#E8E7E1` elevated cards, `#DFDED8` message bubbles, `#141414` deep charcoal text.
* **Typographic & Code Block Pipeline**: Space Grotesk variable typography and JetBrains Mono monospace code formatting with condensed line-height parity (23sp on Android, leading-[24px]/[23px] on Web). Native fenced code blocks render inside dedicated Warm Brutalist cards (`CodeBlockCard` on Android, `CodeBlockItem` on Web) featuring a language badge, one-tap copy button with haptic feedback, automatic soft line-wrapping (eliminating horizontal scrolling), and 1:1 TTS word-seek highlight synchronization.
* **Top & Bottom Fade Gradients**: Transparent bars with multi-stop vertical gradients that softly dissolve scrolling text without harsh solid bars.

### 2. Dual-Engine LLM Intelligence & Autonomous Web Grounding (Tavily + Exa)
* **Xiaomi MiMo Streaming**: Direct SSE token streaming with automatic cluster routing based on API key tier (`Token Plan` vs `Pay-As-You-Go`).
* **Official Xiaomi MiMo Thinking Toggle**: Native request parameterization matching the official Xiaomi MiMo specification (`thinking: { type: "enabled" | "disabled" }`, `temperature: 1.0` when enabled / `0.7` when disabled, and dynamic token allocations of 32k/8k). Artificial prompt injection budget overrides are completely eliminated in favor of a sleek on/off Thinking switch inside the model selector across both MiMo 2.6 Flash and Pro.
* **Autonomous Dual-Engine Web Search Grounding**:
  * **Tavily AI Search (`tavily_search`)**: Keyword-based real-time search engine for breaking news, current events, recent releases/updates, official websites, and live factual data.
  * **Exa AI Search (`exa_search`)**: Neural semantic search engine for community discussions, forum opinions, user reviews, sentiment analysis, long-form articles, and in-depth research.
  * **Autonomous Multi-Round Search & Tool Combination**: Enables up to 3 sequential tool execution rounds with iterative ReAct evaluation, allowing the model to combine factual search with community discussions or switch tools when initial results are incomplete.
  * **Zero-Result & Failure Fallback Cascade**: High-availability resilience between search engines. If a search engine yields zero results or encounters an error, the system automatically checks the alternative engine.
  * **Snippet Highlights & Verification Guards**: Structured source blocks (`URL`, `Publication Date`, `Highlights / Snippets`), tool-boundary zero-result guards, and strict verification trailers to eliminate hallucinations.
* **Zero-Leak Stream Demuxer (`ThinkAndToolParser`)**: Isolates `<think>` reasoning and `<tool_call>` definitions, completely preventing raw tool XML from leaking into user-facing content.

### 3. Voice & Audio Pipelines
* **Speech Synthesis (TTS)**:
  * **Android**: Native `android.speech.tts.TextToSpeech` with real-time streaming playback (speaks from first tokens when tapping floating Play during generation or via conversational STT), word-by-word brutalist highlighting, upper-third viewport snapping, tap-to-seek, and floating Play/Pause controls.
  * **Web**: Native Web Speech API (`window.speechSynthesis`) with real-time streaming playback on floating Play click, word-by-word brutalist highlighting, double-click word-seek, upper/lower viewport auto-scrolling, and bilingual voice detection.
* **Speech-to-Text (STT)**: Spring-loaded pull-to-record interface powered by OpenAI Whisper (`whisper-1`) with mid-speech model and reasoning level selection.

### 4. Cloud Synchronization (Supabase)
* **Real-Time Two-Way Sync**: PostgreSQL database with Row-Level Security, PostgREST APIs, and realtime replication.
* **Supabase Object Storage (`attachments`)**: Uploaded images and documents are stored in the public `attachments` bucket; database records only lightweight public URLs.
* **Automated Storage Cleanup**: Deleting conversations or editing turns proactively purges orphaned remote files and local caches.

### 5. Projects & Knowledge Base Spaces (Web & Android)
* **Custom Project Colors & Palettes**: 9-color curated warm brutalist palette (Default Bone White, Orange, Amber, Emerald, Cyan, Electric Blue, Purple, Pink, Crimson Red) with swatches in project dialogs. Selected project colors dynamically tint folder icons in the drawer/sidebar and top navigation bar breadcrumbs.
* **Streamlined Side Panel / Drawer Experience**: Removed chat counter badges and gear buttons for a minimal look, clean uncontained `+` icon on the PROJECTS header, and borderless/shadowless pill buttons (`#202020` in dark mode, `#E0DFDA` in light mode) on project rows.
* **Instant Settings Access**: Projects can be configured effortlessly: on Android, long-pressing any project row brings up a native settings dropdown; on Web, a sleek gear icon appears on row hover (mirroring chat hover actions) and right-clicking opens the settings context menu.
* **Custom System Prompt Override**: Dedicated project spaces with custom system prompts that strictly override the generic global system prompt for focused domain tasks.
* **Knowledge Base Source Files**: Attach `.md`, `.txt`, `.pdf`, and code documents to any project. The text content is extracted client-side and injected as contextual knowledge base memory during LLM completions. Staging files during project creation is supported on both Web and Android.
* **Safe Storage Persistence**: Whitelisted project files in Supabase Storage prevent active project knowledge attachments from being purged during orphan cleanup passes.
* **Strict Context Scoping**: Strict project context isolation ensures project system instructions and knowledge base files never leak into generic chat sessions.
* **Folder-Like Organization**: Folders in the sidebar (Web) and drawer (Android) grouping nested conversation threads. Header clicks cleanly toggle folder expansion; conversations created within a project stay inside its folder; chats outside live in the generic HISTORY section.
* **Breadcrumb Navigation**: Dynamic folder indicator displaying the active project name directly in the top navigation bar and drafting state.
* **Full Cloud & Offline Sync**: Full Room database support (v9 migration with `color` column and composite index optimization) and Supabase tables (`projects`, `project_files`, `chats.project_id`) with real-time bidirectional synchronization across Android and Web.

### 6. Full Markdown Tables & Typography Engine
* **Brutalist Tables Rendering**: Parses standard Markdown tables (`| Col 1 | Col 2 |`) into clean, responsive tabular cards with header distinctions, column alignment (`left`, `center`, `right`), horizontal scrolling, and custom borders.
* **100% TTS Character Index Parity**: Formatted table cells maintain exact character offsets and plain-text representation on both Web and Android, preserving seamless word-by-word TTS highlighting and tap/double-click seek.

---

## Versioning

| Version | Date | Description |
| :--- | :--- | :--- |
| **v1.5.7** | 2026-10-13 | Real-Time Mid-Stream TTS Playback & Touch-Anchored Dropdowns: Tapping the floating Play button while the model is actively streaming an answer immediately begins speech synthesis in real time from whatever tokens have arrived so far, dynamically enqueuing incoming sentence and clause chunks as generation continues, with live word-by-word brutalist highlighting tracking the stream seamlessly across Android and Web. On Android, double-tapping any message bubble now anchors the action context menu (Copy, Edit, Branch, Redo) precisely at the user's touch contact point with automatic screen boundary clamping. |
| **v1.5.6** | 2026-10-12 | Save Button Contrast, Pointer Dropdowns & Rock-Solid Top-Snapped Prompts: Completely resolved white-on-white and black-on-black button text collisions across Android and Web. Unified modal Save buttons (#141414 with #F9F9F7 text in Light mode, #CECDC4 with #000000 text in Dark mode). On Web, updated user prompt double-click action menu to anchor dynamically at the exact mouse pointer coordinates with smart boundary clamping. In both Web and Android, implemented Rock-Solid Top-Snapped Prompts on send: prompt smoothly snaps to the top on submit, eliminating scroll fighting, jerkiness, and artificial empty black voids, allowing responses to stream seamlessly downwards with complete manual scroll freedom. |
| **v1.5.5** | 2026-10-12 | Full Android-Web Component & Token Harmonization: Enforced canonical 11-token palette from Android Color.kt across all Web components, eliminated rogue and iOS hexes (#1C1C1E, #2C2C2E, #3A3A3C, #DCDBCF, #EFEFEA, #EAE9E2, #E5E4DC, #F2F1EC, #30D158, #E53935), harmonized Send (54px), Copy (48px), Floating TTS (54px), Scroll-to-bottom (44px), and Project plus (30px) button dimensions, embedded image/file attachments directly inside user message bubble cards, removed borders and drop-shadows from input capsule and message cards, converted Reasoning block typography to Space Grotesk with elevated backgrounds, and achieved full Light/Dark mode parity in SettingsModal, ProjectModal, Sidebar, and TopBar. |
| **v1.5.4** | 2026-10-12 | Centered Web Hero Layout: Integrated logo, welcome greetings, and prompt input capsule into a single mathematically centered block (50% horizontal and 50% vertical screen alignment), with elevated input bar positioning closer to greetings, full light/dark theme styling, and pure symmetry when sidebar is collapsed. |
| **v1.5.3** | 2026-10-12 | Official Xiaomi MiMo Thinking Toggle: Removed prompt injection budget overrides in favor of official Xiaomi MiMo request specification (thinking type 'enabled'/'disabled', temperature 1.0/0.7, token budget allocation), and streamlined model selector with a sleek Thinking on/off switch across Android and Web. |
| **v1.5.2** | 2026-10-12 | Minimalist project plus button redesign: removed border and shadow, removed header container/bubble for a clean icon, and updated project row bubbles to #202020 (Dark) and #E0DFDA (Light) across Android and Web. |
| **v1.5.1** | 2026-10-12 | Unified side panel button geometry, two-way Supabase project color sync, hover gear icon for Web project settings, concentric color swatch containment, and cleaned redundant dialog text. |
| **v1.5.0** | 2026-10-12 | Side panel/drawer projects redesign: removed counters and gear buttons, enlarged new chat button, added long-press (Android) and right-click (Web) settings dropdown, 9-color project palette customization, Room v9 migration, and Supabase sync parity. |
| **v1.4.0** | 2026-10-11 | Complete architecture overhaul of Projects (isolated scoping, storage orphan protection, creation file staging, clean navigation toggles) and native Markdown Table rendering engine with 1:1 TTS word-sync parity across Web and Android. |
| **v1.3.0** | 2026-10-11 | Implemented Dual Search Engine architecture (Tavily AI for real-time live web browsing + Exa AI for semantic exploration) with autonomous model tool selection, settings configuration, and clean verification trailers across Web and Android. |
| **v1.2.0** | 2026-10-11 | Added Projects feature across Web and Android: custom system prompt overrides, knowledge base source file attachments (.md, .txt, .pdf, code), folder-based sidebar and drawer organization, and real-time Supabase sync. |
| **v1.1.0** | 2026-10-09 | Added mid-speech model and reasoning selector in Android STT overlay, reasoning token budget enforcement guardrails, and condensed typography parity across Android (23sp) and Web. |
| **v1.0.0** | 2026-10-09 | Initial unified release of the WYRD ecosystem (Native Android Client + Desktop Web Companion) with synchronized Supabase backend, Exa search grounding, and dual brutalist themes. |

---

## Client Documentation

* **Android Client**: See [`android/README.md`](android/README.md) for Android SDK setup, Gradle build instructions, TTS engine profiles, and Room SQLite architecture.
* **Web Companion**: See [`web/README.md`](web/README.md) for Vite dev server commands, Tailwind CSS v4 styling, and Netlify serverless deployment instructions.
