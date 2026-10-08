/**
 * Native Browser Text-to-Speech (TTS) Service using Web Speech API (window.speechSynthesis).
 * Completely client-side, zero latency, zero external API keys needed.
 */

export interface TtsPlaybackState {
  isPlaying: boolean
  isPaused: boolean
  activeMessageId: string | null
  currentText: string
}

type TtsListener = (state: TtsPlaybackState) => void

export function stripMarkdownForSpeech(markdown: string): string {
  if (!markdown) return ''

  let text = markdown
    // Remove multi-line code blocks
    .replace(/```[\s\S]*?```/g, ' [code block omitted] ')
    // Replace inline code with its text content
    .replace(/`([^`]+)`/g, '$1')
    // Remove markdown links: [label](url) -> label
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    // Remove markdown images: ![alt](url) -> ''
    .replace(/!\[[^\]]*\]\([^)]+\)/g, '')
    // Remove bold/italics
    .replace(/(\*\*|__)(.*?)\1/g, '$2')
    .replace(/(\*|_)(.*?)\1/g, '$2')
    // Remove headers: # Header -> Header
    .replace(/^#{1,6}\s+(.*)$/gm, '$1. ')
    // Remove blockquotes: > Quote -> Quote
    .replace(/^\s*>\s+(.*)$/gm, '$1')
    // Remove bullet points / list markers
    .replace(/^\s*[\*\-\+]\s+/gm, '')
    .replace(/^\s*\d+\.\s+/gm, '')
    // Remove HTML tags
    .replace(/<[^>]*>/g, '')
    // Collapse extra spaces & line breaks into clean sentence pauses
    .replace(/\n{2,}/g, '. ')
    .replace(/\n/g, ' ')
    .replace(/\s{2,}/g, ' ')
    .trim()

  return text
}

class NativeTtsService {
  private state: TtsPlaybackState = {
    isPlaying: false,
    isPaused: false,
    activeMessageId: null,
    currentText: '',
  }

  private listeners = new Set<TtsListener>()
  private chromeKeepAliveTimer: number | null = null
  private voices: SpeechSynthesisVoice[] = []

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
    const words = sample.split(/\s+/)
    const italianStopwords = new Set([
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
    const englishStopwords = new Set([
      'the', 'be', 'to', 'of', 'and', 'a', 'in', 'that', 'have',
      'i', 'it', 'for', 'not', 'on', 'with', 'he', 'as', 'you',
      'do', 'at', 'this', 'but', 'his', 'by', 'from', 'they',
      'we', 'say', 'her', 'she', 'or', 'an', 'will', 'my', 'one',
      'all', 'would', 'there', 'their', 'what', 'so', 'up', 'out'
    ])

    let itCount = 0
    let enCount = 0
    for (const w of words) {
      const clean = w.replace(/[^a-zàèéìòù]/gi, '').toLowerCase()
      if (italianStopwords.has(clean)) itCount++
      if (englishStopwords.has(clean)) enCount++
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

  public speak(messageId: string, markdownText: string) {
    if (!this.isSupported()) {
      console.warn('SpeechSynthesis is not supported in this browser.')
      return
    }

    // If already playing this message, toggle pause/resume
    if (this.state.activeMessageId === messageId) {
      if (this.state.isPlaying) {
        this.pause()
        return
      } else if (this.state.isPaused) {
        this.resume()
        return
      }
    }

    const plainText = stripMarkdownForSpeech(markdownText)
    if (!plainText.trim()) return

    this.speakUtterance(messageId, plainText)
  }

  public speakFromText(messageId: string, textToSpeak: string) {
    if (!this.isSupported()) {
      console.warn('SpeechSynthesis is not supported in this browser.')
      return
    }

    const cleanText = textToSpeak.trim()
    if (!cleanText) return

    this.speakUtterance(messageId, cleanText)
  }

  private speakUtterance(messageId: string, text: string) {
    // Stop any existing speech
    this.stop()

    const targetLang = this.detectLanguage(text)
    const utterance = new SpeechSynthesisUtterance(text)
    utterance.lang = targetLang

    const voice = this.getBestVoice(targetLang)
    if (voice) {
      utterance.voice = voice
    }
    utterance.rate = 1.0
    utterance.pitch = 1.0

    utterance.onstart = () => {
      this.state = {
        isPlaying: true,
        isPaused: false,
        activeMessageId: messageId,
        currentText: text,
      }
      this.notify()
      this.startChromeKeepAlive()
    }

    utterance.onpause = () => {
      this.state.isPlaying = false
      this.state.isPaused = true
      this.notify()
      this.stopChromeKeepAlive()
    }

    utterance.onresume = () => {
      this.state.isPlaying = true
      this.state.isPaused = false
      this.notify()
      this.startChromeKeepAlive()
    }

    utterance.onend = () => {
      this.state = {
        isPlaying: false,
        isPaused: false,
        activeMessageId: null,
        currentText: '',
      }
      this.notify()
      this.stopChromeKeepAlive()
    }

    utterance.onerror = (e) => {
      // 'canceled' or 'interrupted' is normal when switching or stopping
      if (e.error !== 'canceled' && e.error !== 'interrupted') {
        console.warn('SpeechSynthesis error:', e)
      }
      this.state = {
        isPlaying: false,
        isPaused: false,
        activeMessageId: null,
        currentText: '',
      }
      this.notify()
      this.stopChromeKeepAlive()
    }

    try {
      window.speechSynthesis.speak(utterance)
    } catch (err) {
      console.error('Failed to invoke speech synthesis:', err)
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
    } catch {}
  }

  public stop() {
    if (!this.isSupported()) return
    try {
      window.speechSynthesis.cancel()
    } catch {}
    this.state = {
      isPlaying: false,
      isPaused: false,
      activeMessageId: null,
      currentText: '',
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
