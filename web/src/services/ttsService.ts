/**
 * Native Browser Text-to-Speech (TTS) Service using Web Speech API (window.speechSynthesis).
 * Completely client-side, zero latency, zero external API keys needed.
 */

import { formatMarkdownForReading } from '@/utils/readingFormatter'

export interface TtsPlaybackState {
  isPlaying: boolean
  isPaused: boolean
  activeMessageId: string | null
  currentText: string
  currentRange: [number, number] | null
  startOffset: number
}

type TtsListener = (state: TtsPlaybackState) => void

const SENTENCE_BOUNDARY_REGEX = /[.!?](\s|\n)|(\n\n)/
const CLAUSE_BOUNDARY_REGEX = /([:;,\n])\s*/
const WORD_CHARS_REGEX = /[^a-zàèéìòù]/gi
const WHITESPACE_SPLIT_REGEX = /\s+/

const ITALIAN_STOPWORDS = new Set([
  'il', 'la', 'lo', 'i', 'gli', 'le', 'un', 'uno', 'una',
  'di', 'a', 'da', 'in', 'con', 'su', 'per', 'tra', 'fra',
  'del', 'dello', 'della', 'dei', 'degli', 'delle',
  'al', 'allo', 'alla', 'ai', 'agli', 'alle',
  'nel', 'nello', 'nella', 'nei', 'negli', 'nelle',
  'sul', 'sullo', 'sulla', 'sui', 'sugli', 'sulle',
  'che', 'non', 'sono', 'è', 'e', 'ho', 'ha', 'hanno',
  'questo', 'questa', 'questi', 'queste', 'quello', 'quella',
  'come', 'perché', 'cosa', 'quando', 'anche', 'più', 'tutto', 'tutti'
])

const ENGLISH_STOPWORDS = new Set([
  'the', 'be', 'to', 'of', 'and', 'a', 'in', 'that', 'have',
  'i', 'it', 'for', 'not', 'on', 'with', 'he', 'as', 'you',
  'do', 'at', 'this', 'but', 'his', 'by', 'from', 'they',
  'we', 'say', 'her', 'she', 'or', 'an', 'will', 'my', 'one',
  'all', 'would', 'there', 'their', 'what', 'so', 'up', 'out'
])

export function findStreamingCutPoint(unvoiced: string, isFirstChunk: boolean): number {
  if (!unvoiced) return 0

  // Priority 1: Sentence boundaries (. , ! , ? , .\n, !\n, ?\n, or \n\n)
  const sentenceMatch = SENTENCE_BOUNDARY_REGEX.exec(unvoiced)
  if (sentenceMatch && sentenceMatch.index !== undefined) {
    return sentenceMatch.index + sentenceMatch[0].length
  }

  // Priority 2: For first chunk, allow earlier clause break (: , ; , , , or \n) if at least 8 chars
  if (isFirstChunk) {
    const clauseMatch = CLAUSE_BOUNDARY_REGEX.exec(unvoiced)
    if (clauseMatch && clauseMatch.index !== undefined && clauseMatch.index >= 8) {
      return clauseMatch.index + clauseMatch[0].length
    }

    // If no punctuation yet, but we've accumulated at least 35 chars, break at nearest word boundary
    if (unvoiced.length >= 35) {
      const maxSearch = Math.min(45, unvoiced.length - 1)
      const spaceIndex = unvoiced.lastIndexOf(' ', maxSearch)
      if (spaceIndex >= 15) {
        return spaceIndex + 1
      }
    }
  } else {
    // Subsequent chunks: if sentence is very long (> 120 chars) without terminal punctuation, break at clause or word
    if (unvoiced.length >= 120) {
      const sliceAfter50 = unvoiced.slice(50)
      const clauseMatch = CLAUSE_BOUNDARY_REGEX.exec(sliceAfter50)
      if (clauseMatch && clauseMatch.index !== undefined) {
        return 50 + clauseMatch.index + clauseMatch[0].length
      }
      const maxSearch = Math.min(140, unvoiced.length - 1)
      const spaceIndex = unvoiced.lastIndexOf(' ', maxSearch)
      if (spaceIndex >= 70) {
        return spaceIndex + 1
      }
    }
  }

  return 0
}

class NativeTtsService {
  private state: TtsPlaybackState = {
    isPlaying: false,
    isPaused: false,
    activeMessageId: null,
    currentText: '',
    currentRange: null,
    startOffset: 0,
  }

  private listeners = new Set<TtsListener>()
  private chromeKeepAliveTimer: number | null = null
  private voices: SpeechSynthesisVoice[] = []
  private currentUtterance: SpeechSynthesisUtterance | null = null
  private currentSessionId: number = 0
  private speakTimeoutTimer: number | null = null

  // Streaming speech state
  private isStreamingActive: boolean = false
  private streamingSpokenCursor: number = 0
  private streamingUtteranceQueueCount: number = 0
  private streamingFinished: boolean = false
  private streamingFinalMessageId: string | null = null
  private streamingFullText: string = ''

  constructor() {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      this.loadVoices()
      window.speechSynthesis.onvoiceschanged = () => {
        this.loadVoices()
      }
    }
  }

  private loadVoices() {
    if (typeof window === 'undefined' || !('speechSynthesis' in window)) return
    this.voices = window.speechSynthesis.getVoices()
  }

  public isSupported(): boolean {
    return typeof window !== 'undefined' && 'speechSynthesis' in window
  }

  public subscribe(listener: TtsListener): () => void {
    this.listeners.add(listener)
    listener({ ...this.state })
    return () => {
      this.listeners.delete(listener)
    }
  }

  private notify() {
    const copy = { ...this.state }
    this.listeners.forEach((fn) => fn(copy))
  }

  private detectLanguage(text: string): 'it-IT' | 'en-US' {
    const sample = text.slice(0, 500).toLowerCase()
    const words = sample.split(WHITESPACE_SPLIT_REGEX)

    let itCount = 0
    let enCount = 0
    for (const w of words) {
      const clean = w.replace(WORD_CHARS_REGEX, '').toLowerCase()
      if (ITALIAN_STOPWORDS.has(clean)) itCount++
      if (ENGLISH_STOPWORDS.has(clean)) enCount++
    }

    if (itCount > enCount) return 'it-IT'
    if (enCount > itCount) return 'en-US'
    return navigator.language?.toLowerCase().startsWith('it') ? 'it-IT' : 'en-US'
  }

  private getBestVoice(targetLang: 'it-IT' | 'en-US'): SpeechSynthesisVoice | null {
    if (this.voices.length === 0) {
      this.loadVoices()
    }
    if (this.voices.length === 0) return null

    const langPrefix = targetLang.split('-')[0].toLowerCase()

    // 1. Natural / Premium / Enhanced voice in matching locale
    const naturalVoice = this.voices.find(
      (v) =>
        v.lang.toLowerCase().startsWith(langPrefix) &&
        (v.name.includes('Natural') ||
          v.name.includes('Enhanced') ||
          v.name.includes('Google') ||
          v.name.includes('Alice') ||
          v.name.includes('Federica') ||
          v.name.includes('Luca') ||
          v.name.includes('Siri') ||
          v.name.includes('Samantha') ||
          v.name.includes('Daniel'))
    )
    if (naturalVoice) return naturalVoice

    // 2. Any voice matching language prefix
    const langVoice = this.voices.find((v) => v.lang.toLowerCase().startsWith(langPrefix))
    if (langVoice) return langVoice

    // 3. Fallback
    const defaultVoice = this.voices.find((v) => v.default) || this.voices.find((v) => v.lang.startsWith('en'))
    return defaultVoice || this.voices[0] || null
  }

  public speak(messageId: string, textOrMarkdown: string, startOffset: number = 0) {
    if (!this.isSupported()) {
      console.warn('SpeechSynthesis is not supported in this browser.')
      return
    }

    // If already playing this message and at the exact same non-zero offset, toggle pause/resume
    if (
      this.state.activeMessageId === messageId &&
      this.state.startOffset === startOffset &&
      startOffset > 0
    ) {
      if (this.state.isPlaying) {
        this.pause()
        return
      } else if (this.state.isPaused) {
        this.resume()
        return
      }
    }

    // If seeking from FormattedMessage (startOffset > 0), textOrMarkdown is ALREADY the exact plainText!
    // Do not re-run formatMarkdownForReading on plainText as it may alter character offsets.
    let fullText = textOrMarkdown
    if (startOffset === 0) {
      const formatted = formatMarkdownForReading(textOrMarkdown)
      fullText = formatted.plainText.trim() ? formatted.plainText : textOrMarkdown.trim()
    }
    if (!fullText) return

    this.speakUtterance(messageId, fullText, startOffset)
  }

  public speakFromText(messageId: string, textToSpeak: string, startOffset: number = 0) {
    if (!this.isSupported()) {
      console.warn('SpeechSynthesis is not supported in this browser.')
      return
    }

    const cleanText = textToSpeak.trim()
    if (!cleanText) return

    this.speakUtterance(messageId, cleanText, startOffset)
  }

  private speakUtterance(messageId: string, fullText: string, startOffset: number = 0) {
    // Cleanly cancel any existing utterance and detach listeners to avoid stale boundary events
    this.stop()
    const sessionId = this.currentSessionId

    const safeOffset = Math.max(0, Math.min(startOffset, fullText.length))
    const textToSpeak = safeOffset > 0 ? fullText.slice(safeOffset) : fullText

    if (!textToSpeak.trim()) {
      return
    }

    const targetLang = this.detectLanguage(textToSpeak)
    const utterance = new SpeechSynthesisUtterance(textToSpeak)
    utterance.lang = targetLang

    const voice = this.getBestVoice(targetLang)
    if (voice) {
      utterance.voice = voice
    }
    utterance.rate = 1.0
    utterance.pitch = 1.0

    this.currentUtterance = utterance

    // Immediate optimistic state update with initial highlight position
    this.state = {
      isPlaying: true,
      isPaused: false,
      activeMessageId: messageId,
      currentText: fullText,
      currentRange: [safeOffset, safeOffset],
      startOffset: safeOffset,
    }
    this.notify()

    utterance.onstart = () => {
      if (this.currentSessionId !== sessionId) return
      this.state = {
        isPlaying: true,
        isPaused: false,
        activeMessageId: messageId,
        currentText: fullText,
        currentRange: [safeOffset, safeOffset],
        startOffset: safeOffset,
      }
      this.notify()
      this.startChromeKeepAlive()
    }

    utterance.onboundary = (event: SpeechSynthesisEvent) => {
      if (this.currentSessionId !== sessionId) return
      if (event.name === 'word') {
        const charIndex = event.charIndex
        let charLength = event.charLength || 0

        if (!charLength || charLength <= 0) {
          // Fallback: detect word length up to next whitespace or punctuation
          const slice = textToSpeak.slice(charIndex)
          const match = slice.match(/^[\p{L}\p{N}'’\-_]+/u) || slice.match(/^\S+/)
          charLength = match ? match[0].length : 1
        }

        const globalStart = safeOffset + charIndex
        const globalEnd = globalStart + charLength

        this.state = {
          ...this.state,
          isPlaying: true,
          isPaused: false,
          activeMessageId: messageId,
          currentRange: [globalStart, globalEnd],
        }
        this.notify()
      }
    }

    utterance.onpause = () => {
      if (this.currentSessionId !== sessionId) return
      this.state.isPlaying = false
      this.state.isPaused = true
      this.notify()
      this.stopChromeKeepAlive()
    }

    utterance.onresume = () => {
      if (this.currentSessionId !== sessionId) return
      this.state.isPlaying = true
      this.state.isPaused = false
      this.notify()
      this.startChromeKeepAlive()
    }

    utterance.onend = () => {
      if (this.currentSessionId !== sessionId) return
      this.currentUtterance = null
      this.state = {
        isPlaying: false,
        isPaused: false,
        activeMessageId: null,
        currentText: '',
        currentRange: null,
        startOffset: 0,
      }
      this.notify()
      this.stopChromeKeepAlive()
    }

    utterance.onerror = (e) => {
      if (this.currentSessionId !== sessionId) return
      if (e.error !== 'canceled' && e.error !== 'interrupted') {
        console.warn('SpeechSynthesis error:', e)
      }
      this.currentUtterance = null
      this.state = {
        isPlaying: false,
        isPaused: false,
        activeMessageId: null,
        currentText: '',
        currentRange: null,
        startOffset: 0,
      }
      this.notify()
      this.stopChromeKeepAlive()
    }

    // Small delay (50ms) ensures browser audio buffers are cleanly cleared after cancel()
    // and eliminates the Chromium/Safari audio queue desync on rapid seeks.
    this.speakTimeoutTimer = window.setTimeout(() => {
      if (this.currentSessionId !== sessionId) return
      try {
        window.speechSynthesis.speak(utterance)
      } catch (err) {
        console.error('Failed to invoke speech synthesis:', err)
        this.stop()
      }
    }, 50)
  }

  public startStreaming(messageId: string = 'streaming') {
    if (!this.isSupported()) return
    this.stop()
    this.isStreamingActive = true
    this.streamingSpokenCursor = 0
    this.streamingUtteranceQueueCount = 0
    this.streamingFinished = false
    this.streamingFinalMessageId = null
    this.streamingFullText = ''

    this.state = {
      isPlaying: true,
      isPaused: false,
      activeMessageId: messageId,
      currentText: '',
      currentRange: [0, 0],
      startOffset: 0,
    }
    this.notify()
  }

  public isStreaming(): boolean {
    return this.isStreamingActive
  }

  public updateStreamingContent(markdownText: string) {
    if (!this.isSupported() || !this.isStreamingActive || this.state.isPaused) return

    const formatted = formatMarkdownForReading(markdownText)
    const plainText = formatted.plainText.trim() ? formatted.plainText : markdownText.trim()
    this.streamingFullText = plainText

    while (true) {
      const safeCursor = Math.max(0, Math.min(this.streamingSpokenCursor, plainText.length))
      if (plainText.length <= safeCursor) break

      const unvoiced = plainText.slice(safeCursor)
      const cutLength = findStreamingCutPoint(unvoiced, safeCursor === 0)
      if (cutLength > 0) {
        const chunkText = unvoiced.slice(0, cutLength).trim()
        const chunkOffset = safeCursor
        this.streamingSpokenCursor = safeCursor + cutLength
        if (chunkText) {
          this.enqueueStreamingUtterance(chunkText, chunkOffset, plainText)
        }
      } else {
        break
      }
    }
  }

  private enqueueStreamingUtterance(chunkText: string, chunkOffset: number, fullTextSoFar: string) {
    if (!this.isSupported() || !chunkText.trim()) return

    const targetLang = this.detectLanguage(fullTextSoFar || chunkText)
    const utterance = new SpeechSynthesisUtterance(chunkText)
    utterance.lang = targetLang

    const voice = this.getBestVoice(targetLang)
    if (voice) {
      utterance.voice = voice
    }
    utterance.rate = 1.0
    utterance.pitch = 1.0

    const sessionId = this.currentSessionId
    this.streamingUtteranceQueueCount++

    utterance.onstart = () => {
      if (this.currentSessionId !== sessionId) return
      this.currentUtterance = utterance
      this.state.isPlaying = true
      this.state.isPaused = false
      this.notify()
      this.startChromeKeepAlive()
    }

    utterance.onboundary = (event: SpeechSynthesisEvent) => {
      if (this.currentSessionId !== sessionId) return
      if (event.name === 'word') {
        const charIndex = event.charIndex
        let charLength = event.charLength || 0

        if (!charLength || charLength <= 0) {
          const slice = chunkText.slice(charIndex)
          const match = slice.match(/^[\p{L}\p{N}'’\-_]+/u) || slice.match(/^\S+/)
          charLength = match ? match[0].length : 1
        }

        const globalStart = chunkOffset + charIndex
        const globalEnd = globalStart + charLength

        this.state = {
          ...this.state,
          isPlaying: true,
          isPaused: false,
          activeMessageId: this.streamingFinalMessageId || 'streaming',
          currentText: fullTextSoFar,
          currentRange: [globalStart, globalEnd],
        }
        this.notify()
      }
    }

    utterance.onpause = () => {
      if (this.currentSessionId !== sessionId) return
      this.state.isPlaying = false
      this.state.isPaused = true
      this.notify()
      this.stopChromeKeepAlive()
    }

    utterance.onresume = () => {
      if (this.currentSessionId !== sessionId) return
      this.state.isPlaying = true
      this.state.isPaused = false
      this.notify()
      this.startChromeKeepAlive()
    }

    utterance.onend = () => {
      if (this.currentSessionId !== sessionId) return
      this.streamingUtteranceQueueCount--
      if (this.streamingFinished && this.streamingUtteranceQueueCount <= 0) {
        this.stop()
      }
    }

    utterance.onerror = (e) => {
      if (this.currentSessionId !== sessionId) return
      this.streamingUtteranceQueueCount--
      if (e.error !== 'canceled' && e.error !== 'interrupted') {
        console.warn('SpeechSynthesis streaming error:', e)
      }
      if (this.streamingFinished && this.streamingUtteranceQueueCount <= 0) {
        this.stop()
      }
    }

    try {
      window.speechSynthesis.speak(utterance)
    } catch (err) {
      console.error('Failed to queue streaming utterance:', err)
      this.streamingUtteranceQueueCount--
    }
  }

  public finishStreaming(finalMessageId: string, finalMarkdown: string) {
    if (!this.isSupported() || !this.isStreamingActive) return

    const formatted = formatMarkdownForReading(finalMarkdown)
    const plainText = formatted.plainText.trim() ? formatted.plainText : finalMarkdown.trim()
    const safeCursor = Math.max(0, Math.min(this.streamingSpokenCursor, plainText.length))

    if (plainText.length > safeCursor) {
      const remaining = plainText.slice(safeCursor).trim()
      if (remaining) {
        this.streamingSpokenCursor = plainText.length
        this.enqueueStreamingUtterance(remaining, safeCursor, plainText)
      }
    }

    this.streamingFinished = true
    this.streamingFinalMessageId = finalMessageId
    this.state.activeMessageId = finalMessageId
    this.notify()

    if (this.streamingUtteranceQueueCount <= 0) {
      this.stop()
    }
  }

  public pause() {
    if (!this.isSupported()) return
    try {
      window.speechSynthesis.pause()
      this.state.isPlaying = false
      this.state.isPaused = true
      this.notify()
      this.stopChromeKeepAlive()
    } catch {}
  }

  public resume() {
    if (!this.isSupported()) return
    try {
      window.speechSynthesis.resume()
      this.state.isPlaying = true
      this.state.isPaused = false
      this.notify()
      this.startChromeKeepAlive()
      if (this.isStreamingActive && this.streamingFullText) {
        this.updateStreamingContent(this.streamingFullText)
      }
    } catch {}
  }

  public stop() {
    this.currentSessionId++
    this.isStreamingActive = false
    this.streamingSpokenCursor = 0
    this.streamingUtteranceQueueCount = 0
    this.streamingFinished = false
    this.streamingFinalMessageId = null
    this.streamingFullText = ''
    if (this.speakTimeoutTimer !== null) {
      clearTimeout(this.speakTimeoutTimer)
      this.speakTimeoutTimer = null
    }
    if (this.currentUtterance) {
      this.currentUtterance.onstart = null
      this.currentUtterance.onboundary = null
      this.currentUtterance.onpause = null
      this.currentUtterance.onresume = null
      this.currentUtterance.onend = null
      this.currentUtterance.onerror = null
      this.currentUtterance = null
    }
    if (this.isSupported()) {
      try {
        window.speechSynthesis.cancel()
      } catch {}
    }
    this.state = {
      isPlaying: false,
      isPaused: false,
      activeMessageId: null,
      currentText: '',
      currentRange: null,
      startOffset: 0,
    }
    this.notify()
    this.stopChromeKeepAlive()
  }

  public getState(): TtsPlaybackState {
    return { ...this.state }
  }

  /**
   * Chromium has a known bug where speech synthesis silently stops after ~15 seconds.
   * Periodically pausing and resuming keeps the audio synthesis engine active.
   */
  private startChromeKeepAlive() {
    this.stopChromeKeepAlive()
    this.chromeKeepAliveTimer = window.setInterval(() => {
      if (this.isSupported() && window.speechSynthesis.speaking && !window.speechSynthesis.paused) {
        window.speechSynthesis.pause()
        window.speechSynthesis.resume()
      }
    }, 10000)
  }

  private stopChromeKeepAlive() {
    if (this.chromeKeepAliveTimer !== null) {
      clearInterval(this.chromeKeepAliveTimer)
      this.chromeKeepAliveTimer = null
    }
  }
}

export const ttsService = new NativeTtsService()
