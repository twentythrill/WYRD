import React, { useState, useRef, useEffect, useCallback } from 'react'
import { createPortal } from 'react-dom'
import {
  ArrowRight,
  Plus,
  ChevronDown,
  ChevronUp,
  Copy,
  Check,
  Edit2,
  GitBranch,
  RotateCcw,
  Mic,
  X,
  Play,
  Pause,
  FileText,
  Paperclip,
  Lightbulb,
} from 'lucide-react'
import { type Chat, type Message, type Profile, type MessageAttachment, type Project } from '@/lib/supabase'
import {
  AudioRecorder,
  transcribeAudio,
  getOpenAiApiKey,
} from '@/services/audioService'
import { ttsService, type TtsPlaybackState } from '@/services/ttsService'
import { OpenAiKeyModal } from '@/components/OpenAiKeyModal'
import { FormattedMessage } from '@/components/FormattedMessage'
import { extractTextFromPdf } from '@/utils/pdfExtractor'

interface ChatAreaProps {
  chat: Chat | null
  activeProject?: Project | null
  messages: Message[]
  profile: Profile | null
  isGenerating: boolean
  isLoadingMessages?: boolean
  isDark?: boolean
  chatSelectTrigger?: number
  currentStreamingReasoning: string
  currentStreamingContent: string
  streamingStatus?: string | null
  editingMessage?: Message | null
  onCancelEdit?: () => void
  onSendMessage: (content: string, attachments?: MessageAttachment[]) => void
  onStopGeneration: () => void
  onEditMessage?: (msg: Message) => void
  onBranchMessage?: (msg: Message) => void
  onRedoMessage?: (msg: Message) => void
}

export interface StagedAttachmentItem {
  id: string
  file?: File
  name: string
  size: number
  type: 'image' | 'file'
  previewUrl: string
  pureBase64?: string
  textContent?: string
}

async function compressImageFile(file: File): Promise<{ base64DataUri: string; pureBase64: string }> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => {
      const img = new Image()
      img.onload = () => {
        const MAX_DIM = 1280
        let width = img.width
        let height = img.height

        if (width > MAX_DIM || height > MAX_DIM) {
          if (width > height) {
            height = Math.round((height * MAX_DIM) / width)
            width = MAX_DIM
          } else {
            width = Math.round((width * MAX_DIM) / height)
            height = MAX_DIM
          }
        }

        const canvas = document.createElement('canvas')
        canvas.width = width
        canvas.height = height
        const ctx = canvas.getContext('2d')
        if (!ctx) {
          const raw = e.target?.result as string
          const pure = raw.split(',')[1] || ''
          resolve({ base64DataUri: raw, pureBase64: pure })
          return
        }

        ctx.drawImage(img, 0, 0, width, height)
        const base64DataUri = canvas.toDataURL('image/jpeg', 0.85)
        const pureBase64 = base64DataUri.split(',')[1] || ''
        resolve({ base64DataUri, pureBase64 })
      }
      img.onerror = () => {
        const raw = e.target?.result as string
        const pure = raw.split(',')[1] || ''
        resolve({ base64DataUri: raw, pureBase64: pure })
      }
      img.src = e.target?.result as string
    }
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

async function extractTextFromFile(file: File): Promise<string | undefined> {
  try {
    if (file.name.toLowerCase().endsWith('.pdf') || file.type === 'application/pdf') {
      const pdfText = await extractTextFromPdf(file)
      return pdfText.trim() ? pdfText : undefined
    }
    const maxBytes = 80 * 1024
    const slice = file.slice(0, maxBytes)
    const text = await slice.text()
    if (text.includes('\0')) {
      return undefined
    }
    return text.trim() ? text : undefined
  } catch {
    return undefined
  }
}

function formatFileSize(bytes: number): string {
  if (!bytes || bytes <= 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`
}

export function ChatArea({
  chat,
  activeProject,
  messages,
  profile,
  isGenerating,
  isLoadingMessages = false,
  isDark = true,
  chatSelectTrigger,
  currentStreamingReasoning,
  currentStreamingContent,
  streamingStatus,
  editingMessage,
  onCancelEdit,
  onSendMessage,
  onStopGeneration,
  onEditMessage,
  onBranchMessage,
  onRedoMessage,
}: ChatAreaProps) {
  const logoSrc = isDark ? '/app_logo.png' : '/app_icon_trans_black.png'
  const [inputText, setInputText] = useState('')
  const [stagedAttachments, setStagedAttachments] = useState<StagedAttachmentItem[]>([])
  const [isProcessingAttachments, setIsProcessingAttachments] = useState(false)
  const [isDragOver, setIsDragOver] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [copiedId, setCopiedId] = useState<string | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const scrollContainerRef = useRef<HTMLDivElement>(null)
  const contentContainerRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  // Smart Scroll Tracking State & Refs
  const isUserAtBottomRef = useRef<boolean>(true)
  const [showScrollBottom, setShowScrollBottom] = useState(false)
  const lastPositionedChatIdRef = useRef<string | null>(null)
  const prevMessagesLengthRef = useRef<number>(messages.length)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const isSubmittingRef = useRef<boolean>(false)
  const isProgrammaticScrollRef = useRef<boolean>(false)
  const isSnappingToPromptRef = useRef<boolean>(false)
  const isTtsStreamingRequestedRef = useRef<boolean>(false)
  const prevIsGeneratingRef = useRef<boolean>(false)
  const prevStreamingContentLenRef = useRef<number>(0)

  // Voice Recording (STT via OpenAI Whisper) State
  const [isRecording, setIsRecording] = useState(false)
  const [recordingDuration, setRecordingDuration] = useState(0)
  const [isTranscribing, setIsTranscribing] = useState(false)
  const [audioError, setAudioError] = useState<string | null>(null)
  const [showKeyModal, setShowKeyModal] = useState(false)
  const [pendingRecord, setPendingRecord] = useState(false)

  // Native Browser Text-to-Speech (TTS) State
  const [ttsState, setTtsState] = useState<TtsPlaybackState>(() => ttsService.getState())

  const recorderRef = useRef<AudioRecorder | null>(null)
  const timerRef = useRef<number | null>(null)

  const handleScroll = () => {
    const container = scrollContainerRef.current
    if (!container) return
    if (isProgrammaticScrollRef.current) {
      return
    }
    const distanceToBottom = container.scrollHeight - container.scrollTop - container.clientHeight
    const isScrolledUp = distanceToBottom > 50 && container.scrollHeight > container.clientHeight + 80
    isUserAtBottomRef.current = distanceToBottom < 30
    setShowScrollBottom(isScrolledUp)
  }

  const scrollToBottom = (smooth = true) => {
    isUserAtBottomRef.current = true
    setShowScrollBottom(false)
    const container = scrollContainerRef.current
    if (container) {
      if (smooth) {
        container.scrollTo({
          top: container.scrollHeight,
          behavior: 'smooth',
        })
      } else {
        container.scrollTop = container.scrollHeight
      }
    }
  }

  // Deduplicate messages by id, and defensively filter out consecutive rapid duplicate messages
  const displayedMessages = React.useMemo(() => {
    const seenIds = new Set<string>()
    const result: Message[] = []
    for (let i = 0; i < messages.length; i++) {
      const m = messages[i]
      if (seenIds.has(m.id)) continue
      seenIds.add(m.id)

      // Filter out accidental consecutive duplicate user messages (same role & content created in close proximity)
      const prev = result[result.length - 1]
      if (
        prev &&
        prev.role === m.role &&
        prev.content.trim() === m.content.trim() &&
        Math.abs(new Date(m.created_at).getTime() - new Date(prev.created_at).getTime()) < 5000
      ) {
        continue
      }
      result.push(m)
    }
    return result
  }, [messages])

  const scrollToBottomInstant = useCallback(() => {
    const container = scrollContainerRef.current
    if (container) {
      container.scrollTop = container.scrollHeight
      isUserAtBottomRef.current = true
      setShowScrollBottom(false)
    }
  }, [])

  const snapPromptToTop = useCallback((messageId?: string) => {
    const container = scrollContainerRef.current
    if (!container) return

    let targetEl: HTMLElement | null = null
    if (messageId) {
      targetEl = document.getElementById(`message-${messageId}`)
    }
    if (!targetEl) {
      const userBubbles = container.querySelectorAll('[data-message-role="user"]')
      if (userBubbles.length > 0) {
        targetEl = userBubbles[userBubbles.length - 1] as HTMLElement
      }
    }

    if (targetEl && container) {
      isProgrammaticScrollRef.current = true
      isSnappingToPromptRef.current = true
      isUserAtBottomRef.current = false
      setShowScrollBottom(false)

      const containerRect = container.getBoundingClientRect()
      const targetRect = targetEl.getBoundingClientRect()
      // Top offset of 76px positions prompt right under the TopBar (56px TopBar + breathing room)
      const topOffset = 76
      const targetScrollTop = Math.max(0, container.scrollTop + (targetRect.top - containerRect.top) - topOffset)

      container.scrollTo({
        top: targetScrollTop,
        behavior: 'smooth',
      })

      setTimeout(() => {
        isProgrammaticScrollRef.current = false
        isSnappingToPromptRef.current = false
      }, 600)
    }
  }, [])

  // Explicit Chat Selection Trigger (re-selecting active chat or clicking a chat)
  useEffect(() => {
    if (chatSelectTrigger && chatSelectTrigger > 0) {
      lastPositionedChatIdRef.current = null
      isProgrammaticScrollRef.current = true
      isUserAtBottomRef.current = true
      setShowScrollBottom(false)
      scrollToBottomInstant()
      const t1 = setTimeout(scrollToBottomInstant, 50)
      const t2 = setTimeout(scrollToBottomInstant, 150)
      const t3 = setTimeout(scrollToBottomInstant, 300)
      const t4 = setTimeout(() => {
        scrollToBottomInstant()
        isProgrammaticScrollRef.current = false
      }, 500)
      return () => {
        clearTimeout(t1)
        clearTimeout(t2)
        clearTimeout(t3)
        clearTimeout(t4)
      }
    }
  }, [chatSelectTrigger, scrollToBottomInstant])

  // 1. Initial chat load: when opening or switching to a chat with existing history, position at the bottom once.
  // Exact parity with Android HomeScreen.kt
  useEffect(() => {
    const currentChatId = chat?.id || null

    if (!currentChatId) {
      lastPositionedChatIdRef.current = null
      return
    }

    // Only position once messages actually belonging to this active chat are loaded and ready
    const belongsToCurrentChat =
      displayedMessages.length > 0 && displayedMessages[0].chat_id === currentChatId

    if (!isLoadingMessages && belongsToCurrentChat) {
      if (lastPositionedChatIdRef.current !== currentChatId) {
        lastPositionedChatIdRef.current = currentChatId
        isProgrammaticScrollRef.current = true
        isUserAtBottomRef.current = true
        setShowScrollBottom(false)

        // Multi-stage positioning to guarantee 100% bottom alignment as Markdown & syntax rendering finish
        scrollToBottomInstant()
        const rafId = requestAnimationFrame(scrollToBottomInstant)
        const t1 = setTimeout(scrollToBottomInstant, 50)
        const t2 = setTimeout(scrollToBottomInstant, 150)
        const t3 = setTimeout(scrollToBottomInstant, 300)
        const t4 = setTimeout(() => {
          scrollToBottomInstant()
          isProgrammaticScrollRef.current = false
        }, 500)

        return () => {
          cancelAnimationFrame(rafId)
          clearTimeout(t1)
          clearTimeout(t2)
          clearTimeout(t3)
          clearTimeout(t4)
        }
      }
    }
  }, [chat?.id, isLoadingMessages, displayedMessages, scrollToBottomInstant])

  // Keep viewport pinned to the bottom if content expands (e.g. image loads, code formatting, KaTeX math),
  // but ONLY if user hasn't scrolled up away from the bottom and not snapping or generating.
  useEffect(() => {
    const el = contentContainerRef.current
    if (!el || typeof ResizeObserver === 'undefined') return

    const observer = new ResizeObserver(() => {
      if (isSnappingToPromptRef.current || isGenerating) return
      if (isUserAtBottomRef.current && !isProgrammaticScrollRef.current) {
        scrollToBottomInstant()
      }
    })

    observer.observe(el)
    return () => observer.disconnect()
  }, [isGenerating, scrollToBottomInstant])

  // 2. New message added: when user sends a prompt, snap that prompt directly to the top!
  useEffect(() => {
    const currentChatId = chat?.id || null
    const hasNewMessage = messages.length > prevMessagesLengthRef.current
    prevMessagesLengthRef.current = messages.length

    if (hasNewMessage && currentChatId && lastPositionedChatIdRef.current === currentChatId) {
      const lastMsg = messages[messages.length - 1]
      const isUserMessage = lastMsg?.role === 'user'
      if (isUserMessage) {
        snapPromptToTop(lastMsg.id)
      }
    }
  }, [messages, chat?.id, snapPromptToTop])

  // NOTE: Strictly do NOT autoscroll during incoming streaming of answer
  // (currentStreamingContent or currentStreamingReasoning), preserving user reading flow.

  // Focus textarea when chat changes
  useEffect(() => {
    textareaRef.current?.focus()
  }, [chat?.id])

  // Populate prompt, attachments, and focus when entering edit mode
  useEffect(() => {
    if (editingMessage) {
      setInputText(editingMessage.content)
      if (editingMessage.attachments && editingMessage.attachments.length > 0) {
        setStagedAttachments(
          editingMessage.attachments.map((att, idx) => ({
            id: `edit-${idx}-${Date.now()}`,
            name: att.name || 'Attachment',
            size: att.size || 0,
            type: att.type === 'image' ? 'image' : 'file',
            previewUrl: att.uri || '',
            textContent: att.textContent,
          }))
        )
      } else {
        setStagedAttachments([])
      }
      if (textareaRef.current) {
        textareaRef.current.focus()
        textareaRef.current.style.height = 'auto'
        textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 180)}px`
      }
    }
  }, [editingMessage])

  // Cleanup timers & recorder on unmount
  useEffect(() => {
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
      recorderRef.current?.cancel()
      ttsService.stop()
    }
  }, [])

  // TTS State Subscription
  useEffect(() => {
    const unsubscribe = ttsService.subscribe((state) => {
      setTtsState(state)
    })
    return () => {
      unsubscribe()
    }
  }, [])

  // Stop TTS speech when switching chats
  useEffect(() => {
    isTtsStreamingRequestedRef.current = false
    ttsService.stop()
  }, [chat?.id])

  // Synchronize streaming TTS chunks as LLM tokens arrive
  useEffect(() => {
    if (isGenerating && isTtsStreamingRequestedRef.current) {
      if (currentStreamingContent) {
        // If content was cleared mid-stream (e.g. tool execution / re-prompt), restart TTS stream
        if (prevStreamingContentLenRef.current > 0 && currentStreamingContent.length < prevStreamingContentLenRef.current) {
          ttsService.startStreaming('streaming')
        }
        ttsService.updateStreamingContent(currentStreamingContent)
      }
    }
    prevStreamingContentLenRef.current = currentStreamingContent ? currentStreamingContent.length : 0
  }, [isGenerating, currentStreamingContent])

  // Finalize streaming TTS when generation completes
  useEffect(() => {
    if (prevIsGeneratingRef.current && !isGenerating) {
      if (isTtsStreamingRequestedRef.current) {
        isTtsStreamingRequestedRef.current = false
        const latestAssistant = [...messages].reverse().find((m) => m.role === 'assistant')
        if (latestAssistant) {
          ttsService.finishStreaming(latestAssistant.id, latestAssistant.content)
        } else {
          ttsService.stop()
        }
      }
    }
    prevIsGeneratingRef.current = isGenerating
  }, [isGenerating, messages])

  const handleToggleFloatingTts = () => {
    if (ttsState.isPlaying) {
      ttsService.pause()
    } else if (ttsState.isPaused) {
      ttsService.resume()
    } else {
      if (isGenerating) {
        isTtsStreamingRequestedRef.current = true
        ttsService.startStreaming('streaming')
        if (currentStreamingContent) {
          ttsService.updateStreamingContent(currentStreamingContent)
        }
      } else {
        const latestAssistant = [...displayedMessages].reverse().find((m) => m.role === 'assistant')
        if (latestAssistant) {
          ttsService.speak(latestAssistant.id, latestAssistant.content, 0)
        }
      }
    }
  }

  const handleProcessFiles = async (fileList: FileList | File[]) => {
    const files = Array.from(fileList)
    if (files.length === 0) return

    setIsProcessingAttachments(true)
    const newItems: StagedAttachmentItem[] = []

    for (const file of files) {
      const isImg = file.type.startsWith('image/')
      const id = `${Date.now()}-${Math.random().toString(36).substring(2, 9)}`

      if (isImg) {
        try {
          const { base64DataUri, pureBase64 } = await compressImageFile(file)
          newItems.push({
            id,
            file,
            name: file.name,
            size: file.size,
            type: 'image',
            previewUrl: base64DataUri,
            pureBase64,
          })
        } catch (err) {
          console.error('Error compressing image:', err)
        }
      } else {
        const textContent = await extractTextFromFile(file)
        newItems.push({
          id,
          file,
          name: file.name,
          size: file.size,
          type: 'file',
          previewUrl: '',
          textContent,
        })
      }
    }

    setStagedAttachments((prev) => [...prev, ...newItems])
    setIsProcessingAttachments(false)
  }

  const handleRemoveStagedAttachment = (id: string) => {
    setStagedAttachments((prev) => prev.filter((a) => a.id !== id))
  }

  const handlePaste = async (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
    if (e.clipboardData.files && e.clipboardData.files.length > 0) {
      e.preventDefault()
      await handleProcessFiles(e.clipboardData.files)
    }
  }

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setIsDragOver(true)
  }

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setIsDragOver(false)
  }

  const handleDrop = async (e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setIsDragOver(false)
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      await handleProcessFiles(e.dataTransfer.files)
    }
  }

  const handleSend = () => {
    const text = inputText.trim()
    const hasAttachments = stagedAttachments.length > 0
    if ((!text && !hasAttachments) || isGenerating || isSubmitting || isSubmittingRef.current || isProcessingAttachments) return
    setIsSubmitting(true)
    isSubmittingRef.current = true

    const attachmentsPayload: MessageAttachment[] = stagedAttachments.map((att) => ({
      uri: att.previewUrl || att.name,
      type: att.type,
      name: att.name,
      size: att.size,
      textContent: att.textContent,
      file: att.file,
    }))

    setInputText('')
    setStagedAttachments([])
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto'
    }
    isTtsStreamingRequestedRef.current = false
    ttsService.stop()
    onSendMessage(text, attachmentsPayload)
    setTimeout(() => {
      setIsSubmitting(false)
      isSubmittingRef.current = false
    }, 400)
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      if (e.nativeEvent.isComposing) return
      e.preventDefault()
      handleSend()
    }
  }

  const copyToClipboard = (text: string, id: string) => {
    navigator.clipboard.writeText(text)
    setCopiedId(id)
    setTimeout(() => setCopiedId(null), 1800)
  }

  const handleInputChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInputText(e.target.value)
    e.target.style.height = 'auto'
    e.target.style.height = `${Math.min(e.target.scrollHeight, 180)}px`
  }

  // --- Voice Recording (STT via OpenAI Whisper) ---
  const handleStartRecording = async () => {
    setAudioError(null)
    const apiKey = getOpenAiApiKey()
    if (!apiKey) {
      setPendingRecord(true)
      setShowKeyModal(true)
      return
    }

    try {
      const recorder = new AudioRecorder()
      recorderRef.current = recorder
      await recorder.start()
      setIsRecording(true)
      setRecordingDuration(0)

      timerRef.current = window.setInterval(() => {
        setRecordingDuration((prev) => prev + 1)
      }, 1000)
    } catch (err: any) {
      console.error('Audio recording start error:', err)
      setAudioError(err.message || 'Microphone access denied.')
      setIsRecording(false)
    }
  }

  const handleStopRecording = async () => {
    if (timerRef.current) {
      clearInterval(timerRef.current)
      timerRef.current = null
    }

    if (!recorderRef.current) {
      setIsRecording(false)
      return
    }

    setIsRecording(false)
    setIsTranscribing(true)
    setAudioError(null)

    try {
      const audioBlob = await recorderRef.current.stop()
      const text = await transcribeAudio(audioBlob)
      if (text.trim()) {
        setInputText((prev) => (prev ? prev + ' ' + text.trim() : text.trim()))
        if (textareaRef.current) {
          textareaRef.current.focus()
        }
      }
    } catch (err: any) {
      console.error('Transcription error:', err)
      setAudioError(err.message || 'Failed to transcribe audio.')
    } finally {
      setIsTranscribing(false)
      recorderRef.current = null
    }
  }

  const handleCancelRecording = () => {
    if (timerRef.current) {
      clearInterval(timerRef.current)
      timerRef.current = null
    }
    if (recorderRef.current) {
      recorderRef.current.cancel()
      recorderRef.current = null
    }
    setIsRecording(false)
    setRecordingDuration(0)
  }

  const handleKeyModalSuccess = (_newKey: string) => {
    if (pendingRecord) {
      setTimeout(() => handleStartRecording(), 100)
    }
    setPendingRecord(false)
  }

  const formatDuration = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    const secs = seconds % 60
    return `${mins}:${secs < 10 ? '0' : ''}${secs}`
  }


  const canSend =
    (inputText.trim().length > 0 || stagedAttachments.length > 0) &&
    !isGenerating &&
    !isSubmitting &&
    !isProcessingAttachments
  const hasAssistantMessage = displayedMessages.some((m) => m.role === 'assistant')
  const showFloatingTts = hasAssistantMessage || isGenerating || ttsState.isPlaying || ttsState.isPaused
  const isEmptyState = (!chat || displayedMessages.length === 0) && !isGenerating

  const topGradient = isDark
    ? 'linear-gradient(to bottom, #100F0F 0%, rgba(0,0,0,0.92) 18%, rgba(0,0,0,0.80) 38%, rgba(0,0,0,0.62) 58%, rgba(0,0,0,0.32) 78%, rgba(0,0,0,0.10) 90%, transparent 100%)'
    : 'linear-gradient(to bottom, #FEFCF1 0%, rgba(249,249,247,0.92) 18%, rgba(249,249,247,0.80) 38%, rgba(249,249,247,0.62) 58%, rgba(249,249,247,0.32) 78%, rgba(249,249,247,0.10) 90%, transparent 100%)'

  const bottomGradient = isDark
    ? 'linear-gradient(to top, #100F0F 0%, rgba(0,0,0,0.92) 8%, rgba(0,0,0,0.82) 18%, rgba(0,0,0,0.66) 34%, rgba(0,0,0,0.48) 52%, rgba(0,0,0,0.28) 70%, rgba(0,0,0,0.12) 85%, transparent 100%)'
    : 'linear-gradient(to top, #FEFCF1 0%, rgba(249,249,247,0.92) 8%, rgba(249,249,247,0.82) 18%, rgba(249,249,247,0.66) 34%, rgba(249,249,247,0.48) 52%, rgba(249,249,247,0.28) 70%, rgba(249,249,247,0.12) 85%, transparent 100%)'

  // --- Reusable Input Capsule & Attachments Sub-renderers ---
  const renderAudioError = () => {
    if (!audioError) return null
    return (
      <div className="w-full mb-3 pointer-events-auto">
        <div className={`px-4 py-2.5 rounded-xl border border-wyrd-text-primary/40 text-wyrd-text-primary text-xs font-sans flex items-center justify-between shadow-lg ${
          isDark ? 'bg-[#100F0F]' : 'bg-[#EBE9DF]'
        }`}>
          <span>{audioError}</span>
          <button
            onClick={() => setAudioError(null)}
            className={`p-1 ${isDark ? 'text-[#84837D] hover:text-[#CECDC4]' : 'text-[#666460] hover:text-[#100F0F]'}`}
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      </div>
    )
  }

  const renderStagedAttachments = () => {
    if (stagedAttachments.length === 0 && !isProcessingAttachments) return null
    return (
      <>
        {stagedAttachments.length > 0 && (
          <div className={`mb-2 p-2 rounded-2xl border flex items-center gap-2 overflow-x-auto pointer-events-auto shadow-md max-w-full ${
            isDark ? 'bg-[#100F0F] border-[#21211F]' : 'bg-[#E1E0D6] border-[#D8D6CD]'
          }`}>
            {stagedAttachments.map((att) => (
              <div
                key={att.id}
                className={`relative group shrink-0 flex items-center gap-2 px-2.5 py-1.5 rounded-xl border text-xs ${
                  isDark ? 'bg-[#100F0F] border-[#21211F] text-[#CECDC4]' : 'bg-[#EBE9DF] border-[#D8D6CD] text-[#100F0F]'
                }`}
              >
                {att.type === 'image' ? (
                  <div className="flex items-center gap-2">
                    <img
                      src={att.previewUrl}
                      alt={att.name}
                      className={`w-8 h-8 rounded-lg object-cover border ${
                        isDark ? 'border-[#21211F]' : 'border-[#D8D6CD]'
                      }`}
                    />
                    <div className="flex flex-col">
                      <span className="max-w-[120px] truncate text-[11px] font-medium">{att.name}</span>
                      <span className="text-[9px] text-wyrd-text-secondary">{formatFileSize(att.size)}</span>
                    </div>
                  </div>
                ) : (
                  <div className="flex items-center gap-2">
                    <FileText className="w-5 h-5 text-wyrd-text-secondary shrink-0" />
                    <div className="flex flex-col">
                      <span className="max-w-[130px] truncate text-[11px] font-medium">{att.name}</span>
                      <span className="text-[9px] text-wyrd-text-secondary">{formatFileSize(att.size)}</span>
                    </div>
                  </div>
                )}
                <button
                  onClick={() => handleRemoveStagedAttachment(att.id)}
                  title="Remove attachment"
                  className={`ml-1 w-4 h-4 rounded-full flex items-center justify-center text-wyrd-text-secondary transition-colors cursor-pointer ${
                    isDark ? 'bg-[#21211F] hover:bg-[#21211F] hover:text-[#CECDC4]' : 'bg-[#D8D6CD] hover:bg-[#B2B0A9] hover:text-[#100F0F]'
                  }`}
                >
                  <X className="w-2.5 h-2.5 stroke-[2.5]" />
                </button>
              </div>
            ))}
            {isProcessingAttachments && (
              <div className="flex items-center gap-1.5 px-3 py-1 text-xs text-wyrd-text-secondary shrink-0">
                <div className={`w-3.5 h-3.5 border-2 rounded-full animate-spin ${
                  isDark ? 'border-[#21211F] border-t-[#CECDC4]' : 'border-[#D8D6CD] border-t-[#100F0F]'
                }`} />
                <span>Processing...</span>
              </div>
            )}
          </div>
        )}

        {stagedAttachments.length === 0 && isProcessingAttachments && (
          <div className={`mb-2 px-3.5 py-2 rounded-2xl border flex items-center gap-2 text-xs text-wyrd-text-secondary font-sans pointer-events-auto shadow-md ${
            isDark ? 'bg-[#100F0F] border-[#21211F]' : 'bg-[#E1E0D6] border-[#D8D6CD]'
          }`}>
            <div className={`w-3.5 h-3.5 border-2 rounded-full animate-spin ${
              isDark ? 'border-[#21211F] border-t-[#CECDC4]' : 'border-[#D8D6CD] border-t-[#100F0F]'
            }`} />
            <span>Loading attachment...</span>
          </div>
        )}
      </>
    )
  }

  const renderInputCapsule = () => (
    <div
      style={{
        backgroundColor: isDark ? '#100F0F' : '#EBE9DF',
      }}
      className="relative flex items-center rounded-[34px] pl-2.5 pr-1.5 py-1.5 pointer-events-auto transition-colors min-h-[66px]"
    >
      {/* Left '+' Attachment Button */}
      <button
        type="button"
        onClick={() => fileInputRef.current?.click()}
        title="Add Attachment (Images, Documents, Code)"
        className={`w-11 h-11 flex items-center justify-center rounded-full transition-colors shrink-0 cursor-pointer ${
          isDark
            ? 'text-[#84837D] hover:text-[#CECDC4]'
            : 'text-[#666460] hover:text-[#100F0F]'
        }`}
      >
        <Plus className="w-6 h-6 stroke-[2]" />
      </button>

      {/* Input Textarea OR Recording Interface */}
      {isRecording ? (
        <div className="flex-1 flex items-center justify-between px-3 py-2">
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1.5">
              <span className="w-2.5 h-2.5 rounded-full bg-wyrd-text-primary animate-pulse" />
              <span className="font-mono text-xs text-wyrd-text-primary font-semibold">REC</span>
            </div>
            <span className={`font-mono text-sm ${isDark ? 'text-[#CECDC4]' : 'text-[#100F0F]'}`}>
              {formatDuration(recordingDuration)}
            </span>
            {/* Audio wave indicator */}
            <div className="flex items-center gap-0.5 h-4">
              <span className={`w-0.5 h-2 ${isDark ? 'bg-[#CECDC4]' : 'bg-[#100F0F]'} animate-pulse`} />
              <span className={`w-0.5 h-4 ${isDark ? 'bg-[#CECDC4]' : 'bg-[#100F0F]'} animate-pulse [animation-delay:150ms]`} />
              <span className={`w-0.5 h-2.5 ${isDark ? 'bg-[#CECDC4]' : 'bg-[#100F0F]'} animate-pulse [animation-delay:300ms]`} />
              <span className={`w-0.5 h-3.5 ${isDark ? 'bg-[#CECDC4]' : 'bg-[#100F0F]'} animate-pulse [animation-delay:450ms]`} />
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={handleCancelRecording}
              title="Cancel recording"
              className={`p-1.5 rounded-full transition-colors ${
                isDark
                  ? 'text-[#84837D] hover:text-[#CECDC4] hover:bg-[#100F0F]'
                  : 'text-[#666460] hover:text-[#100F0F] hover:bg-[#E1E0D6]'
              }`}
            >
              <X className="w-4 h-4" />
            </button>
            <button
              onClick={handleStopRecording}
              title="Finish & Transcribe"
              className={`px-3.5 py-1.5 rounded-full text-xs font-semibold transition-colors flex items-center gap-1.5 ${
                isDark
                  ? 'bg-[#CECDC4] text-[#100F0F] hover:bg-[#CECDC4]'
                  : 'bg-[#100F0F] text-[#FEFCF1] hover:bg-[#100F0F]'
              }`}
            >
              <Check className="w-3.5 h-3.5 stroke-[2.5]" />
              <span>Done</span>
            </button>
          </div>
        </div>
      ) : isTranscribing ? (
        <div className="flex-1 flex items-center gap-2.5 px-3 py-2 text-wyrd-text-secondary text-xs font-sans">
          <div className={`w-4 h-4 border-2 rounded-full animate-spin ${
            isDark
              ? 'border-[#21211F] border-t-[#CECDC4]'
              : 'border-[#D8D6CD] border-t-[#100F0F]'
          }`} />
          <span>Transcribing audio with OpenAI gpt-4o-mini-transcribe...</span>
        </div>
      ) : (
        <textarea
          ref={textareaRef}
          rows={1}
          value={inputText}
          onChange={handleInputChange}
          onKeyDown={handleKeyDown}
          onPaste={handlePaste}
          placeholder="Ask away..."
          className={`flex-1 px-3 py-2 bg-transparent text-[16px] focus:outline-none resize-none max-h-44 font-sans leading-snug ${
            isDark
              ? 'text-[#CECDC4] placeholder-[#84837D]'
              : 'text-[#100F0F] placeholder-[#666460]'
          }`}
        />
      )}

      {/* Right Action Buttons */}
      {!isRecording && !isTranscribing && (
        <div className="flex items-center gap-1 shrink-0">
          {/* Voice Input Button (Mic - OpenAI gpt-4o-mini-transcribe STT) */}
          <button
            onClick={handleStartRecording}
            title="Voice dictation (OpenAI gpt-4o-mini-transcribe)"
            className={`w-10 h-10 rounded-full flex items-center justify-center text-wyrd-text-secondary transition-colors cursor-pointer ${
              isDark
                ? 'hover:text-[#CECDC4] hover:bg-[#100F0F]'
                : 'hover:text-[#100F0F] hover:bg-[#E1E0D6]'
            }`}
          >
            <Mic className="w-5 h-5 stroke-[2]" />
          </button>

          {/* Right Action Button: Send or Stop matching Android ChatInputBubble exactly (54dp circle) */}
          {isGenerating ? (
            <button
              onClick={() => {
                isTtsStreamingRequestedRef.current = false
                ttsService.stop()
                onStopGeneration()
              }}
              title="Stop Generation"
              style={{
                backgroundColor: isDark ? '#CECDC4' : '#100F0F',
              }}
              className="w-[54px] h-[54px] rounded-full flex items-center justify-center transition-all shrink-0 ml-1 cursor-pointer hover:opacity-90"
            >
              <div
                style={{
                  backgroundColor: isDark ? '#000000' : '#FEFCF1',
                }}
                className="w-4 h-4 rounded-[3px]"
              />
            </button>
          ) : (
            <button
              onClick={handleSend}
              disabled={!canSend}
              title="Send"
              style={{
                backgroundColor: isDark ? '#CECDC4' : '#100F0F',
                color: canSend
                  ? (isDark ? '#000000' : '#FEFCF1')
                  : (isDark ? 'rgba(0, 0, 0, 0.35)' : 'rgba(249, 249, 247, 0.35)'),
              }}
              className={`w-[54px] h-[54px] rounded-full flex items-center justify-center transition-all shrink-0 ml-1 ${
                canSend ? 'cursor-pointer hover:opacity-90' : 'cursor-default'
              }`}
            >
              <ArrowRight className="w-6 h-6 stroke-[2.5]" />
            </button>
          )}
        </div>
      )}
    </div>
  )

  return (
    <div
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      className={`flex-1 h-full min-h-0 relative overflow-hidden ${isDark ? 'bg-[#000000] text-[#CECDC4]' : 'bg-[#FEFCF1] text-[#100F0F]'}`}
    >
      {/* Hidden File Input */}
      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept="image/*,.pdf,text/*,.txt,.md,.json,.py,.ts,.js,.tsx,.jsx,.html,.css,.csv,.xml,.yaml,.yml,.sh,.c,.cpp,.h,.java,.kt,.rs,.go,.sql"
        className="hidden"
        onChange={(e) => {
          if (e.target.files && e.target.files.length > 0) {
            handleProcessFiles(e.target.files)
            e.target.value = ''
          }
        }}
      />

      {/* Drag & Drop Visual Overlay */}
      {isDragOver && (
        <div className="absolute inset-0 bg-[#100F0F]/75 backdrop-blur-xs border-2 border-dashed border-[#CECDC4]/40 z-50 flex flex-col items-center justify-center pointer-events-none transition-all">
          <Paperclip className="w-10 h-10 text-[#CECDC4] animate-bounce mb-2" />
          <span className="font-sans text-sm font-medium text-[#CECDC4]">Drop images or files to attach</span>
        </div>
      )}

      {/* Top Subtle Fade Gradient: messages smoothly dissolve behind top bar */}
      <div
        className="absolute top-0 left-0 right-0 h-28 pointer-events-none z-20 transition-all duration-200"
        style={{ background: topGradient }}
      />

      {isLoadingMessages ? (
        <div className="h-full flex flex-col items-center justify-center text-center select-none py-12">
          <div className={`w-6 h-6 border-2 rounded-full animate-spin mb-3 ${
            isDark ? 'border-[#21211F] border-t-[#CECDC4]' : 'border-[#D8D6CD] border-t-[#100F0F]'
          }`} />
          <span className="font-sans text-xs text-wyrd-text-secondary">Loading conversation...</span>
        </div>
      ) : isEmptyState ? (
        <>
          {/* Preserves scrollContainerRef without errors */}
          <div ref={scrollContainerRef} className="hidden" />

          {/* Mathematically Centered Empty State Hero Block (Logo + Welcome + Kaomoji + Input Capsule) */}
          <div className="absolute inset-0 flex flex-col items-center justify-center px-4 md:px-8 pointer-events-none select-none z-20">
            <div className="w-full max-w-3xl flex flex-col items-center justify-center text-center pointer-events-auto">
              <img
                src={logoSrc}
                alt="WYRD Eye"
                className="w-40 h-40 object-contain opacity-30 pointer-events-none"
              />
              <div className="h-7" />
              <div className={`font-sans text-[23px] font-normal ${isDark ? 'text-[#CECDC4]' : 'text-[#100F0F]'}`}>
                Welcome Back,{' '}
                <span className={`font-bold ${isDark ? 'text-[#CECDC4]' : 'text-[#100F0F]'}`}>
                  {profile?.username?.trim() || 'Human'}
                </span>
              </div>
              <div className="h-2.5" />
              <div className={`font-sans text-[22px] ${isDark ? 'text-[#84837D]' : 'text-[#666460]'}`}>(◕‿◕)</div>
              {activeProject && (
                <div
                  className={`mt-4 inline-flex items-center gap-2 px-3.5 py-1.5 rounded-2xl text-xs font-sans border ${
                    isDark
                      ? 'border-[#21211F] bg-[#100F0F] text-[#CECDC4]'
                      : 'border-[#D8D6CD] bg-[#EBE9DF] text-[#100F0F]'
                  }`}
                >
                  <span className={`w-1.5 h-1.5 rounded-full ${isDark ? 'bg-[#CECDC4]' : 'bg-[#100F0F]'}`} />
                  <span className={isDark ? 'text-[#84837D]' : 'text-[#666460]'}>
                    Drafting in <span className={`font-semibold ${isDark ? 'text-[#CECDC4]' : 'text-[#100F0F]'}`}>{activeProject.name}</span>
                  </span>
                </div>
              )}

              {/* Closer Spacing to Input Capsule */}
              <div className="h-8" />

              {/* Centered Input Capsule Section */}
              <div className="w-full text-left">
                {renderAudioError()}
                {renderStagedAttachments()}
                {renderInputCapsule()}
              </div>
            </div>
          </div>
        </>
      ) : (
        <>
          {/* Messages Scroll Area - Full height so text scrolls underneath floating controls */}
          <div
            ref={scrollContainerRef}
            onScroll={handleScroll}
            onWheel={() => {
              isProgrammaticScrollRef.current = false
            }}
            onTouchMove={() => {
              isProgrammaticScrollRef.current = false
            }}
            onPointerDown={() => {
              isProgrammaticScrollRef.current = false
            }}
            className="w-full h-full overflow-y-auto px-4 md:px-8 pt-20 pb-36 space-y-6"
          >
            <div ref={contentContainerRef} className="max-w-3xl mx-auto space-y-6">
              {displayedMessages.map((msg) => (
                <MessageItem
                  key={msg.id}
                  message={msg}
                  isCopied={copiedId === msg.id}
                  activeHighlightRange={
                    ttsState.activeMessageId === msg.id ? ttsState.currentRange : null
                  }
                  isDark={isDark}
                  onCopy={() => copyToClipboard(msg.content, msg.id)}
                  onSeek={(plainText, offset) => ttsService.speak(msg.id, plainText, offset)}
                  onEdit={() => onEditMessage?.(msg)}
                  onBranch={() => onBranchMessage?.(msg)}
                  onRedo={() => onRedoMessage?.(msg)}
                />
              ))}

              {/* Live Streaming Assistant Message */}
              {isGenerating && (
                <div className="space-y-3">
                  {currentStreamingReasoning && (
                    <ReasoningBlock
                      reasoning={currentStreamingReasoning}
                      isStreaming={currentStreamingContent.length === 0}
                      isDark={isDark}
                    />
                  )}

                  {streamingStatus && currentStreamingContent.length === 0 && (
                    <div className="flex items-center gap-2.5 text-xs font-sans text-wyrd-text-secondary px-1 py-1">
                      <span className={`w-2 h-2 rounded-full animate-pulse ${
                        isDark ? 'bg-[#CECDC4]' : 'bg-[#100F0F]'
                      }`} />
                      <span className="tracking-wide">{streamingStatus}</span>
                    </div>
                  )}

                  {currentStreamingContent && (
                    <FormattedMessage
                      content={currentStreamingContent}
                      messageId="streaming"
                      activeHighlightRange={
                        ttsState.activeMessageId === 'streaming' ? ttsState.currentRange : null
                      }
                      isDark={isDark}
                      onSeek={(plainText, offset) => ttsService.speak('streaming', plainText, offset)}
                    />
                  )}
                </div>
              )}

              <div ref={messagesEndRef} />
            </div>
          </div>

          {/* Bottom Subtle Fade Gradient: messages smoothly dissolve behind floating input capsule */}
          <div
            className="absolute bottom-0 left-0 right-0 h-36 pointer-events-none z-10 transition-all duration-200"
            style={{ background: bottomGradient }}
          />

          {/* Floating Bottom Overlay: Floating Controls & Input Capsule directly over scrolling text */}
          <div className="absolute bottom-0 left-0 right-0 z-20 pointer-events-none px-4 md:px-8 pb-5">
            <div className="max-w-3xl mx-auto">
              {renderAudioError()}

              {/* Floating Controls Bar: Centered Scroll-to-Bottom Arrow & Right Play/Pause TTS (matching Android) */}
              <div className="relative w-full h-12 mb-2 pointer-events-none">
                {/* Scroll-to-Bottom Arrow Button: Centered on top of input (matching Android 44dp) */}
                <div className="absolute left-1/2 -translate-x-1/2 bottom-0 flex items-center justify-center">
                  <button
                    onClick={() => scrollToBottom(true)}
                    title="Scroll to bottom"
                    className={`w-11 h-11 rounded-full border transition-all duration-200 flex items-center justify-center cursor-pointer pointer-events-auto ${
                      isDark
                        ? 'bg-[#100F0F] border-[#21211F] text-[#CECDC4] hover:bg-[#100F0F] hover:border-[#31312F]'
                        : 'bg-[#EBE9DF] border-[#D8D6CD] text-[#100F0F] hover:bg-[#E1E0D6]'
                    } ${
                      showScrollBottom
                        ? 'opacity-100 scale-100'
                        : 'opacity-0 scale-90 pointer-events-none'
                    }`}
                  >
                    <ChevronDown className="w-5 h-5 stroke-[2]" />
                  </button>
                </div>

                {/* Floating Play/Pause TTS button on the right (matching Android 54dp circle) */}
                {showFloatingTts && (
                  <div className="absolute right-1 bottom-0 flex items-center justify-center">
                    <button
                      onClick={handleToggleFloatingTts}
                      title={
                        ttsState.isPlaying
                          ? 'Pause speech'
                          : ttsState.isPaused
                          ? 'Resume speech'
                          : 'Play latest response'
                      }
                      style={
                        ttsState.isPlaying
                          ? {
                              backgroundColor: isDark ? '#CECDC4' : '#100F0F',
                              color: isDark ? '#000000' : '#FEFCF1',
                              borderColor: isDark ? '#CECDC4' : '#100F0F',
                            }
                          : undefined
                      }
                      className={`w-[54px] h-[54px] rounded-full border flex items-center justify-center transition-all duration-200 cursor-pointer pointer-events-auto ${
                        ttsState.isPlaying
                          ? 'shadow-md'
                          : isDark
                          ? 'bg-[#100F0F] text-[#CECDC4] border-[#21211F] hover:bg-[#100F0F] hover:border-[#31312F]'
                          : 'bg-[#EBE9DF] text-[#100F0F] border-[#D8D6CD] hover:bg-[#E1E0D6]'
                      }`}
                    >
                      {ttsState.isPlaying ? (
                        <Pause className="w-5 h-5 fill-current" />
                      ) : (
                        <Play className="w-5 h-5 fill-current ml-0.5" />
                      )}
                    </button>
                  </div>
                )}
              </div>

              {/* Editing Prompt Banner matching Android editing banner */}
              {editingMessage && (
                <div className={`mb-2 px-4 py-2 rounded-2xl border flex items-center justify-between text-xs font-sans pointer-events-auto shadow-md ${
                  isDark
                    ? 'bg-[#100F0F] border-[#21211F] text-[#CECDC4]'
                    : 'bg-[#E1E0D6] border-[#D8D6CD] text-[#100F0F]'
                }`}>
                  <div className="flex items-center gap-2">
                    <Edit2 className="w-3.5 h-3.5 text-wyrd-text-secondary" />
                    <span className="font-medium">Editing prompt</span>
                  </div>
                  <button
                    onClick={() => {
                      onCancelEdit?.()
                      setInputText('')
                      setStagedAttachments([])
                      if (textareaRef.current) textareaRef.current.style.height = 'auto'
                    }}
                    className={`p-1 rounded transition-colors flex items-center gap-1 cursor-pointer ${
                      isDark ? 'text-[#84837D] hover:text-[#CECDC4]' : 'text-[#666460] hover:text-[#100F0F]'
                    }`}
                    title="Cancel editing"
                  >
                    <span className="text-[11px]">Cancel</span>
                    <X className="w-3.5 h-3.5" />
                  </button>
                </div>
              )}

              {renderStagedAttachments()}
              {renderInputCapsule()}
            </div>
          </div>
        </>
      )}

      {/* OpenAI API Key Prompt Modal */}
      <OpenAiKeyModal
        isOpen={showKeyModal}
        onClose={() => {
          setShowKeyModal(false)
          setPendingRecord(false)
        }}
        onSuccess={handleKeyModalSuccess}
        isDark={isDark}
      />
    </div>
  )
}

const MessageItem = React.memo(function MessageItem({
  message,
  isCopied,
  activeHighlightRange,
  isDark = true,
  onCopy,
  onSeek,
  onEdit,
  onBranch,
  onRedo,
}: {
  message: Message
  isCopied: boolean
  activeHighlightRange?: [number, number] | null
  isDark?: boolean
  onCopy: () => void
  onSeek?: (plainText: string, charOffset: number) => void
  onEdit?: () => void
  onBranch?: () => void
  onRedo?: () => void
}) {
  const [menuPosition, setMenuPosition] = useState<{ x: number; y: number } | null>(null)
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuPosition(null)
      }
    }
    function handleScrollOrResize() {
      setMenuPosition(null)
    }
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        setMenuPosition(null)
      }
    }
    if (menuPosition) {
      document.addEventListener('mousedown', handleClickOutside)
      window.addEventListener('scroll', handleScrollOrResize, true)
      window.addEventListener('resize', handleScrollOrResize)
      document.addEventListener('keydown', handleKeyDown)
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      window.removeEventListener('scroll', handleScrollOrResize, true)
      window.removeEventListener('resize', handleScrollOrResize)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [menuPosition])

  const handleDoubleClick = (e: React.MouseEvent) => {
    e.stopPropagation()
    window.getSelection()?.removeAllRanges()

    const menuWidth = 208 // w-52
    const menuHeight = 175
    const margin = 8

    let x = e.clientX
    let y = e.clientY

    // If dropdown would overflow right of window, position it to the left of the cursor
    if (x + menuWidth + margin > window.innerWidth) {
      x = e.clientX - menuWidth
    }
    // If dropdown would overflow bottom of window, position it above the cursor
    if (y + menuHeight + margin > window.innerHeight) {
      y = e.clientY - menuHeight
    }

    // Guard against edge cases
    x = Math.max(margin, Math.min(x, window.innerWidth - menuWidth - margin))
    y = Math.max(margin, Math.min(y, window.innerHeight - menuHeight - margin))

    setMenuPosition({ x, y })
  }

  if (message.role === 'user') {
    const attachments = message.attachments || []
    const imageAttachments = attachments.filter((att) => att.type === 'image' && att.uri)
    const fileAttachments = attachments.filter((att) => att.type !== 'image' || !att.uri)

    return (
      <div id={`message-${message.id}`} data-message-role="user" className="flex flex-col items-end relative w-full">
        <div
          onDoubleClick={handleDoubleClick}
          title="Double click for options"
          className={`max-w-[88%] rounded-[22px] px-4.5 py-3.5 select-text cursor-pointer transition-colors ${
            isDark ? 'bg-[#100F0F] text-[#CECDC4]' : 'bg-[#E1E0D6] text-[#100F0F]'
          }`}
        >
          {/* Internal Attachments inside user bubble matching Android */}
          {attachments.length > 0 && (
            <div className="space-y-2 mb-2.5">
              {/* Image Attachments */}
              {imageAttachments.length > 0 && (
                <div>
                  {imageAttachments.length === 1 ? (
                    <div
                      className={`relative rounded-[14px] overflow-hidden border ${
                        isDark ? 'border-[#21211F] bg-[#100F0F]' : 'border-[#D8D6CD] bg-[#EBE9DF]'
                      } cursor-pointer group`}
                      onClick={(e) => {
                        e.stopPropagation()
                        const img = imageAttachments[0]
                        if (img.uri.startsWith('data:') || img.uri.startsWith('http')) {
                          window.open(img.uri, '_blank')
                        }
                      }}
                    >
                      <img
                        src={imageAttachments[0].uri}
                        alt={imageAttachments[0].name || 'Image attachment'}
                        className="max-h-60 w-full object-cover transition-transform duration-200 group-hover:scale-[1.01]"
                      />
                    </div>
                  ) : imageAttachments.length === 2 ? (
                    <div className="grid grid-cols-2 gap-2">
                      {imageAttachments.map((img, idx) => (
                        <div
                          key={idx}
                          className={`relative h-[130px] rounded-[12px] overflow-hidden border ${
                            isDark ? 'border-[#21211F] bg-[#100F0F]' : 'border-[#D8D6CD] bg-[#EBE9DF]'
                          } cursor-pointer group`}
                          onClick={(e) => {
                            e.stopPropagation()
                            if (img.uri.startsWith('data:') || img.uri.startsWith('http')) {
                              window.open(img.uri, '_blank')
                            }
                          }}
                        >
                          <img
                            src={img.uri}
                            alt={img.name || 'Image attachment'}
                            className="w-full h-full object-cover transition-transform duration-200 group-hover:scale-[1.02]"
                          />
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="flex items-center gap-2 overflow-x-auto pb-1">
                      {imageAttachments.map((img, idx) => (
                        <div
                          key={idx}
                          className={`relative w-[110px] h-[110px] shrink-0 rounded-[12px] overflow-hidden border ${
                            isDark ? 'border-[#21211F] bg-[#100F0F]' : 'border-[#D8D6CD] bg-[#EBE9DF]'
                          } cursor-pointer group`}
                          onClick={(e) => {
                            e.stopPropagation()
                            if (img.uri.startsWith('data:') || img.uri.startsWith('http')) {
                              window.open(img.uri, '_blank')
                            }
                          }}
                        >
                          <img
                            src={img.uri}
                            alt={img.name || 'Image attachment'}
                            className="w-full h-full object-cover transition-transform duration-200 group-hover:scale-[1.02]"
                          />
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* File Attachments */}
              {fileAttachments.length > 0 && (
                <div className="space-y-1.5">
                  {fileAttachments.map((fileItem, idx) => {
                    const isRemote = fileItem.uri && (fileItem.uri.startsWith('http://') || fileItem.uri.startsWith('https://'))
                    return (
                      <div
                        key={idx}
                        onClick={(e) => {
                          if (isRemote) {
                            e.stopPropagation()
                            window.open(fileItem.uri, '_blank')
                          }
                        }}
                        className={`flex items-center gap-2.5 p-2 rounded-xl border transition-colors ${
                          isDark
                            ? 'bg-[#100F0F] border-[#21211F] text-[#CECDC4]'
                            : 'bg-[#EBE9DF] border-[#D8D6CD] text-[#100F0F]'
                        } ${isRemote ? 'cursor-pointer hover:opacity-90' : ''}`}
                      >
                        <div
                          className={`w-9 h-9 rounded-lg flex items-center justify-center shrink-0 ${
                            isDark ? 'bg-[#100F0F]' : 'bg-[#E1E0D6]'
                          }`}
                        >
                          <FileText className="w-5 h-5 text-wyrd-text-secondary" />
                        </div>
                        <div className="flex flex-col min-w-0 flex-1">
                          <span className="text-xs font-medium truncate font-sans">{fileItem.name}</span>
                          {fileItem.size ? (
                            <span className="text-[10px] text-wyrd-text-secondary font-sans">
                              {formatFileSize(fileItem.size)}
                            </span>
                          ) : null}
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>
          )}

          {/* User message text */}
          {message.content && (
            <div className="text-[17px] leading-[23px] font-sans whitespace-pre-wrap">
              {message.content}
            </div>
          )}
        </div>

        {/* Double-tap / context menu matching Android options: Copy, Edit, Branch, Redo */}
        {menuPosition &&
          createPortal(
            <div
              ref={menuRef}
              style={{ top: menuPosition.y, left: menuPosition.x }}
              className={`fixed z-50 w-52 rounded-xl border shadow-2xl py-1.5 text-sm animate-in fade-in duration-100 select-none ${
                isDark ? 'bg-[#100F0F] border-[#21211F]' : 'bg-[#E1E0D6] border-[#D8D6CD]'
              }`}
              onClick={(e) => e.stopPropagation()}
            >
              <button
                onClick={() => {
                  onCopy()
                  setMenuPosition(null)
                }}
                className={`w-full px-3.5 py-2 flex items-center gap-2.5 text-left text-sm transition-colors cursor-pointer ${
                  isDark ? 'text-[#CECDC4] hover:bg-[#21211F]' : 'text-[#100F0F] hover:bg-[#D8D6CD]'
                }`}
              >
                {isCopied ? (
                  <Check className={`w-4 h-4 ${isDark ? 'text-[#CECDC4]' : 'text-[#100F0F]'}`} />
                ) : (
                  <Copy className="w-4 h-4" />
                )}
                <span>{isCopied ? 'Copied' : 'Copy'}</span>
              </button>
              {onEdit && (
                <button
                  onClick={() => {
                    onEdit()
                    setMenuPosition(null)
                  }}
                  className={`w-full px-3.5 py-2 flex items-center gap-2.5 text-left text-sm transition-colors cursor-pointer ${
                    isDark ? 'text-[#CECDC4] hover:bg-[#21211F]' : 'text-[#100F0F] hover:bg-[#D8D6CD]'
                  }`}
                >
                  <Edit2 className="w-4 h-4" />
                  <span>Edit</span>
                </button>
              )}
              {onBranch && (
                <button
                  onClick={() => {
                    onBranch()
                    setMenuPosition(null)
                  }}
                  className={`w-full px-3.5 py-2 flex items-center gap-2.5 text-left text-sm transition-colors cursor-pointer ${
                    isDark ? 'text-[#CECDC4] hover:bg-[#21211F]' : 'text-[#100F0F] hover:bg-[#D8D6CD]'
                  }`}
                >
                  <GitBranch className="w-4 h-4" />
                  <span>Branch into a new chat</span>
                </button>
              )}
              {onRedo && (
                <button
                  onClick={() => {
                    onRedo()
                    setMenuPosition(null)
                  }}
                  className={`w-full px-3.5 py-2 flex items-center gap-2.5 text-left text-sm transition-colors cursor-pointer ${
                    isDark ? 'text-[#CECDC4] hover:bg-[#21211F]' : 'text-[#100F0F] hover:bg-[#D8D6CD]'
                  }`}
                >
                  <RotateCcw className="w-4 h-4" />
                  <span>Redo</span>
                </button>
              )}
            </div>,
            document.body
          )}
      </div>
    )
  }

  // Assistant Message (Clean, NO avatar, NO title label, single 48dp copy button at bottom matching Android)
  return (
    <div id={`message-${message.id}`} data-message-role="assistant" className="flex flex-col items-start space-y-2 w-full">
      {/* Reasoning Thought Block */}
      {message.reasoning && (
        <ReasoningBlock
          reasoning={message.reasoning}
          isStreaming={false}
          isDark={isDark}
        />
      )}

      {/* Assistant Content: Word-by-word synchronized TTS highlight & double-click seek */}
      <div className="w-full select-text cursor-text">
        <FormattedMessage
          content={message.content}
          messageId={message.id}
          activeHighlightRange={activeHighlightRange}
          isDark={isDark}
          onSeek={onSeek}
        />
      </div>

      {/* Provenance caption: model + reasoning state */}
      {(() => {
        const modelLabel = message.model_name ? formatModelLabel(message.model_name) : null
        const reasoningLabel =
          message.reasoning_enabled == null
            ? null
            : message.reasoning_enabled
              ? 'Reasoning on'
              : 'Reasoning off'
        const caption = [modelLabel, reasoningLabel].filter(Boolean).join(' · ')
        return caption ? (
          <div
            className={`text-[11px] leading-[15px] select-none ${isDark ? 'text-[#84837D]' : 'text-[#666460]'}`}
          >
            {caption}
          </div>
        ) : null
      })()}

      {/* Action buttons matching Android: Single copy button (48dp circle / 22dp icon) */}
      <div className="pt-2 flex items-center gap-2">
        <button
          onClick={onCopy}
          title="Copy message"
          className={`w-12 h-12 rounded-full border flex items-center justify-center transition-colors cursor-pointer ${
            isDark
              ? 'bg-[#100F0F] border-[#21211F] text-[#84837D] hover:text-[#CECDC4] hover:bg-[#100F0F]'
              : 'bg-[#EBE9DF] border-[#D8D6CD] text-[#666460] hover:text-[#100F0F] hover:bg-[#E1E0D6]'
          }`}
        >
          {isCopied ? (
            <Check className={`w-[22px] h-[22px] ${isDark ? 'text-[#CECDC4]' : 'text-[#100F0F]'}`} />
          ) : (
            <Copy className="w-[22px] h-[22px]" />
          )}
        </button>
      </div>
    </div>
  )
})

const MODEL_LABELS: Record<string, string> = {
  'mimo-v2.6-flash': 'MiMo 2.6 Flash',
  'mimo-v2.6-pro': 'MiMo 2.6 Pro',
  'gpt-6.1-sol': 'GPT 6.1 Sol',
}

function formatModelLabel(modelId: string): string {
  return MODEL_LABELS[modelId] || modelId
}

function ReasoningBlock({
  reasoning,
  isStreaming,
  isDark = true,
}: {
  reasoning: string
  isStreaming: boolean
  isDark?: boolean
}) {
  const [isExpanded, setIsExpanded] = useState(false)

  const bgClass = isDark ? 'bg-[#100F0F]' : 'bg-[#EBE9DF]'
  const hoverTextClass = isDark ? 'hover:text-[#CECDC4]' : 'hover:text-[#100F0F]'
  const activeColor = isDark ? 'text-[#CECDC4]' : 'text-[#100F0F]'
  const mutedColor = isDark ? 'text-[#84837D]' : 'text-[#666460]'
  const dividerBorder = isDark ? 'border-[#21211F]' : 'border-[#D8D6CD]'

  return (
    <div
      className={`w-full rounded-xl ${bgClass} overflow-hidden mt-1 mb-3 select-none transition-all`}
    >
      <button
        onClick={() => setIsExpanded(!isExpanded)}
        className={`w-full px-3.5 py-2.5 flex items-center justify-between text-left text-xs ${mutedColor} ${hoverTextClass} transition-colors ${bgClass} cursor-pointer`}
      >
        <div className="flex items-center gap-2">
          <Lightbulb
            className={`w-3.5 h-3.5 ${
              isStreaming ? `${activeColor} animate-pulse` : mutedColor
            }`}
          />
          <span
            className={`font-sans font-medium text-[13px] ${
              isStreaming ? activeColor : mutedColor
            }`}
          >
            Thinking
          </span>
          {isStreaming && (
            <span
              className={`w-1.5 h-1.5 rounded-full ${
                isDark ? 'bg-[#CECDC4]' : 'bg-[#100F0F]'
              } animate-ping`}
            />
          )}
        </div>
        {isExpanded ? (
          <ChevronUp className={`w-4 h-4 ${mutedColor}`} />
        ) : (
          <ChevronDown className={`w-4 h-4 ${mutedColor}`} />
        )}
      </button>

      {isExpanded && (
        <div
          className={`p-3.5 text-[13px] ${isDark ? 'text-[#84837D]' : 'text-[#666460]'} font-sans leading-[18px] whitespace-pre-wrap border-t ${dividerBorder} max-h-60 overflow-y-auto select-text`}
        >
          {reasoning}
        </div>
      )}
    </div>
  )
}
