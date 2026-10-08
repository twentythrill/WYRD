/**
 * Speech-to-Text (STT) Service:
 * 1. Web Microphone Audio Recording with MediaRecorder
 * 2. OpenAI Whisper transcription (/v1/audio/transcriptions)
 */

export function getOpenAiApiKey(): string {
  const customKey = localStorage.getItem('wyrd_openai_api_key')
  if (customKey && customKey.trim()) return customKey.trim()
  const genericKey = localStorage.getItem('wyrd_api_key')
  if (genericKey && genericKey.trim().startsWith('sk-')) return genericKey.trim()
  return ''
}

export function setOpenAiApiKey(key: string) {
  localStorage.setItem('wyrd_openai_api_key', key.trim())
}

export class AudioRecorder {
  private mediaRecorder: MediaRecorder | null = null
  private audioChunks: Blob[] = []
  private stream: MediaStream | null = null

  async start(): Promise<void> {
    this.audioChunks = []

    // Request microphone access
    this.stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: 1,
        sampleRate: 16000,
        echoCancellation: true,
        noiseSuppression: true,
      },
    })

    // Determine supported MIME type
    let mimeType = 'audio/webm'
    if (MediaRecorder.isTypeSupported('audio/webm;codecs=opus')) {
      mimeType = 'audio/webm;codecs=opus'
    } else if (MediaRecorder.isTypeSupported('audio/mp4')) {
      mimeType = 'audio/mp4'
    } else if (MediaRecorder.isTypeSupported('audio/ogg;codecs=opus')) {
      mimeType = 'audio/ogg;codecs=opus'
    }

    this.mediaRecorder = new MediaRecorder(this.stream, { mimeType })

    this.mediaRecorder.ondataavailable = (event) => {
      if (event.data && event.data.size > 0) {
        this.audioChunks.push(event.data)
      }
    }

    this.mediaRecorder.start(100) // 100ms chunks
  }

  stop(): Promise<Blob> {
    return new Promise((resolve, reject) => {
      if (!this.mediaRecorder) {
        return reject(new Error('Recorder not initialized'))
      }

      this.mediaRecorder.onstop = () => {
        const mimeType = this.mediaRecorder?.mimeType || 'audio/webm'
        const audioBlob = new Blob(this.audioChunks, { type: mimeType })
        this.cleanup()
        resolve(audioBlob)
      }

      this.mediaRecorder.stop()
    })
  }

  cancel(): void {
    if (this.mediaRecorder && this.mediaRecorder.state !== 'inactive') {
      try {
        this.mediaRecorder.stop()
      } catch {}
    }
    this.cleanup()
  }

  private cleanup(): void {
    if (this.stream) {
      this.stream.getTracks().forEach((track) => track.stop())
      this.stream = null
    }
    this.mediaRecorder = null
    this.audioChunks = []
  }
}

export async function transcribeAudio(audioBlob: Blob, apiKey?: string): Promise<string> {
  const key = (apiKey || getOpenAiApiKey()).trim()
  if (!key) {
    throw new Error('OpenAI API key is missing. Please configure it in Settings.')
  }

  const mimeType = audioBlob.type || 'audio/webm'
  const ext = mimeType.includes('mp4') ? 'mp4' : mimeType.includes('ogg') ? 'ogg' : 'webm'
  const file = new File([audioBlob], `speech.${ext}`, { type: mimeType })

  const formData = new FormData()
  formData.append('file', file)
  formData.append('model', 'whisper-1')

  const res = await fetch('https://api.openai.com/v1/audio/transcriptions', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${key}`,
    },
    body: formData,
  })

  if (!res.ok) {
    let errMsg = `Transcription failed (${res.status})`
    try {
      const err = await res.json()
      if (err.error?.message) errMsg = err.error.message
    } catch {}
    throw new Error(errMsg)
  }

  const data = await res.json()
  return data.text || ''
}
