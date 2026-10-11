# WYRD (v1.4.0)

A modern, high-performance, minimalist AI client ecosystem operating strictly client-side with zero intermediary servers. Features real-time bidirectional cloud synchronization across Android and Desktop Web via Supabase.

---

## Ecosystem Architecture

WYRD is organized into two isolated clients that synchronize cognitive states, conversation threads, and preferences in real time:

```text
WYRD/
├── android/          # Native Android AI Client (Kotlin + Jetpack Compose)
│   ├── app/          # App source, UI components, Room database, network services
│   ├── build.gradle.kts
│   └── README.md     # Android client architecture & documentation (v1.3.0)
│
├── web/              # Desktop & Browser Companion (React 19 + TypeScript + Vite)
│   ├── src/          # React components, services, Supabase integration
│   ├── package.json
│   └── README.md     # Web client architecture & documentation (v1.3.0)
│
├── .gitignore        # Unified repository ignore rules
└── README.md         # Master repository overview (v1.3.0)
```

---

## Key Highlights

### 1. Unified Design Language (Warm Brutalism)
* **AMOLED Dark**: Pure black (`#000000`) background, `#141414` elevated surfaces, `#1A1A1A` message bubbles, `#CECDC4` bone-white accents.
* **Inverted Light**: Warm off-white (`#F9F9F7`) background, `#E8E7E1` elevated cards, `#DFDED8` message bubbles, `#141414` deep charcoal text.
* **Typographic Pipeline**: Space Grotesk variable typography and JetBrains Mono monospace code formatting with condensed line-height parity (23sp on Android, leading-[24px]/[23px] on Web).
* **Top & Bottom Fade Gradients**: Transparent bars with multi-stop vertical gradients that softly dissolve scrolling text without harsh solid bars.

### 2. Dual-Engine LLM Intelligence & Autonomous Web Grounding (Tavily + Exa)
* **Xiaomi MiMo Streaming**: Direct SSE token streaming with automatic cluster routing based on API key tier (`Token Plan` vs `Pay-As-You-Go`).
* **Reasoning Control & Budget Guardrails**: Native thinking parameter with prompt steering and strict token budget enforcement (`Off`, `Low` 1024, `Medium` 8192, `High` 32768).
* **Autonomous Dual-Engine Web Search Grounding**:
  * **Tavily AI Search (`tavily_search`)**: Keyword-based real-time search engine for breaking news, current events, recent releases/updates, official websites, and live factual data.
  * **Exa AI Search (`exa_search`)**: Neural semantic search engine for community discussions, forum opinions, user reviews, sentiment analysis, long-form articles, and in-depth research.
  * **Autonomous Multi-Round Search & Tool Combination**: Enables up to 3 sequential tool execution rounds with iterative ReAct evaluation, allowing the model to combine factual search with community discussions or switch tools when initial results are incomplete.
  * **Zero-Result & Failure Fallback Cascade**: High-availability resilience between search engines. If a search engine yields zero results or encounters an error, the system automatically checks the alternative engine.
  * **Snippet Highlights & Verification Guards**: Structured source blocks (`URL`, `Publication Date`, `Highlights / Snippets`), tool-boundary zero-result guards, and strict verification trailers to eliminate hallucinations.
* **Zero-Leak Stream Demuxer (`ThinkAndToolParser`)**: Isolates `<think>` reasoning and `<tool_call>` definitions, completely preventing raw tool XML from leaking into user-facing content.

### 3. Voice & Audio Pipelines
* **Speech Synthesis (TTS)**:
  * **Android**: Native `android.speech.tts.TextToSpeech` with real-time streaming playback (speaks from first tokens), word-by-word brutalist highlighting, upper-third viewport snapping, tap-to-seek, and floating Play/Pause controls.
  * **Web**: Native Web Speech API (`window.speechSynthesis`) with word-by-word brutalist highlighting, double-click word-seek, upper/lower viewport auto-scrolling, and bilingual voice detection.
* **Speech-to-Text (STT)**: Spring-loaded pull-to-record interface powered by OpenAI Whisper (`whisper-1`) with mid-speech model and reasoning level selection.

### 4. Cloud Synchronization (Supabase)
* **Real-Time Two-Way Sync**: PostgreSQL database with Row-Level Security, PostgREST APIs, and realtime replication.
* **Supabase Object Storage (`attachments`)**: Uploaded images and documents are stored in the public `attachments` bucket; database records only lightweight public URLs.
* **Automated Storage Cleanup**: Deleting conversations or editing turns proactively purges orphaned remote files and local caches.

### 5. Projects & Knowledge Base Spaces (Web & Android)
* **Custom System Prompt Override**: Dedicated project spaces with custom system prompts that strictly override the generic global system prompt for focused domain tasks.
* **Knowledge Base Source Files**: Attach `.md`, `.txt`, `.pdf`, and code documents to any project. The text content is extracted client-side and injected as contextual knowledge base memory during LLM completions. Staging files during project creation is supported on both Web and Android.
* **Safe Storage Persistence**: Whitelisted project files in Supabase Storage prevent active project knowledge attachments from being purged during orphan cleanup passes.
* **Strict Context Scoping**: Strict project context isolation ensures project system instructions and knowledge base files never leak into generic chat sessions.
* **Folder-Like Organization**: Folders in the sidebar (Web) and drawer (Android) grouping nested conversation threads. Header clicks cleanly toggle folder expansion; conversations created within a project stay inside its folder; chats outside live in the generic HISTORY section.
* **Breadcrumb Navigation**: Dynamic folder indicator displaying the active project name directly in the top navigation bar and drafting state.
* **Full Cloud & Offline Sync**: Full Room database support (v8 migration with composite index optimization) and Supabase tables (`projects`, `project_files`, `chats.project_id`) with real-time bidirectional synchronization across Android and Web.

### 6. Full Markdown Tables & Typography Engine
* **Brutalist Tables Rendering**: Parses standard Markdown tables (`| Col 1 | Col 2 |`) into clean, responsive tabular cards with header distinctions, column alignment (`left`, `center`, `right`), horizontal scrolling, and custom borders.
* **100% TTS Character Index Parity**: Formatted table cells maintain exact character offsets and plain-text representation on both Web and Android, preserving seamless word-by-word TTS highlighting and tap/double-click seek.

---

## Versioning

| Version | Date | Description |
| :--- | :--- | :--- |
| **v1.4.0** | 2026-10-11 | Complete architecture overhaul of Projects (isolated scoping, storage orphan protection, creation file staging, clean navigation toggles) and native Markdown Table rendering engine with 1:1 TTS word-sync parity across Web and Android. |
| **v1.3.0** | 2026-10-11 | Implemented Dual Search Engine architecture (Tavily AI for real-time live web browsing + Exa AI for semantic exploration) with autonomous model tool selection, settings configuration, and clean verification trailers across Web and Android. |
| **v1.2.0** | 2026-10-11 | Added Projects feature across Web and Android: custom system prompt overrides, knowledge base source file attachments (.md, .txt, .pdf, code), folder-based sidebar and drawer organization, and real-time Supabase sync. |
| **v1.1.0** | 2026-10-09 | Added mid-speech model and reasoning selector in Android STT overlay, reasoning token budget enforcement guardrails, and condensed typography parity across Android (23sp) and Web. |
| **v1.0.0** | 2026-10-09 | Initial unified release of the WYRD ecosystem (Native Android Client + Desktop Web Companion) with synchronized Supabase backend, Exa search grounding, and dual brutalist themes. |

---

## Client Documentation

* **Android Client**: See [`android/README.md`](android/README.md) for Android SDK setup, Gradle build instructions, TTS engine profiles, and Room SQLite architecture.
* **Web Companion**: See [`web/README.md`](web/README.md) for Vite dev server commands, Tailwind CSS v4 styling, and Netlify serverless deployment instructions.
