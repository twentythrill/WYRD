# WYRD (v1.1.0)

A modern, high-performance, minimalist AI client ecosystem operating strictly client-side with zero intermediary servers. Features real-time bidirectional cloud synchronization across Android and Desktop Web via Supabase.

---

## Ecosystem Architecture

WYRD is organized into two isolated clients that synchronize cognitive states, conversation threads, and preferences in real time:

```text
WYRD/
├── android/          # Native Android AI Client (Kotlin + Jetpack Compose)
│   ├── app/          # App source, UI components, Room database, network services
│   ├── build.gradle.kts
│   └── README.md     # Android client architecture & documentation (v1.1.0)
│
├── web/              # Desktop & Browser Companion (React 19 + TypeScript + Vite)
│   ├── src/          # React components, services, Supabase integration
│   ├── package.json
│   └── README.md     # Web client architecture & documentation (v1.1.0)
│
├── .gitignore        # Unified repository ignore rules
└── README.md         # Master repository overview (v1.1.0)
```

---

## Key Highlights

### 1. Unified Design Language (Warm Brutalism)
* **AMOLED Dark**: Pure black (`#000000`) background, `#141414` elevated surfaces, `#1A1A1A` message bubbles, `#CECDC4` bone-white accents.
* **Inverted Light**: Warm off-white (`#F9F9F7`) background, `#E8E7E1` elevated cards, `#DFDED8` message bubbles, `#141414` deep charcoal text.
* **Typographic Pipeline**: Space Grotesk variable typography and JetBrains Mono monospace code formatting with condensed line-height parity (23sp on Android, leading-[24px]/[23px] on Web).
* **Top & Bottom Fade Gradients**: Transparent bars with multi-stop vertical gradients that softly dissolve scrolling text without harsh solid bars.

### 2. Dual-Engine LLM Intelligence & Autonomous Grounding
* **Xiaomi MiMo Streaming**: Direct SSE token streaming with automatic cluster routing based on API key tier (`Token Plan` vs `Pay-As-You-Go`).
* **Reasoning Control & Budget Guardrails**: Native thinking parameter with prompt steering and strict token budget enforcement (`Off`, `Low` 1024, `Medium` 8192, `High` 32768).
* **Autonomous Web Search Grounding (Exa AI)**: Multi-result extraction (`numResults: 5`), snippet highlights (`contents: { highlights: true }`), tool-boundary zero-result guards, and strict verification trailers to eliminate hallucinations.
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
* **Knowledge Base Source Files**: Attach `.md`, `.txt`, `.pdf`, and code documents to any project. The text content is extracted client-side and injected as contextual knowledge base memory during LLM completions.
* **Folder-Like Organization**: Folders in the sidebar (Web) and drawer (Android) grouping nested conversation threads. Conversations created within a project stay inside its folder; chats outside live in the generic HISTORY section.
* **Breadcrumb Navigation**: Dynamic folder indicator displaying the active project name directly in the top navigation bar.
* **Full Cloud & Offline Sync**: Full Room database support (v7 migration) and Supabase tables (`projects`, `project_files`, `chats.project_id`) with real-time bidirectional synchronization across Android and Web.

---

## Versioning

| Version | Date | Description |
| :--- | :--- | :--- |
| **v1.2.0** | 2026-10-11 | Added Projects feature across Web and Android: custom system prompt overrides, knowledge base source file attachments (.md, .txt, .pdf, code), folder-based sidebar and drawer organization, and real-time Supabase sync. |
| **v1.1.0** | 2026-10-09 | Added mid-speech model and reasoning selector in Android STT overlay, reasoning token budget enforcement guardrails, and condensed typography parity across Android (23sp) and Web. |
| **v1.0.0** | 2026-10-09 | Initial unified release of the WYRD ecosystem (Native Android Client + Desktop Web Companion) with synchronized Supabase backend, Exa search grounding, and dual brutalist themes. |

---

## Client Documentation

* **Android Client**: See [`android/README.md`](android/README.md) for Android SDK setup, Gradle build instructions, TTS engine profiles, and Room SQLite architecture.
* **Web Companion**: See [`web/README.md`](web/README.md) for Vite dev server commands, Tailwind CSS v4 styling, and Netlify serverless deployment instructions.
