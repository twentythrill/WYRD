import React, { useState, useRef, useEffect, useCallback } from 'react'
import {
  ArrowRight,
  Square,
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
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { type Chat, type Message, type Profile, type MessageAttachment } from '@/lib/supabase'
import {
  AudioRecorder,
  transcribeAudio,
  getOpenAiApiKey,
} from '@/services/audioService'
import { ttsService, type TtsPlaybackState } from '@/services/ttsService'
import { OpenAiKeyModal } from '@/components/OpenAiKeyModal'

interface ChatAreaProps {
  chat: Chat | null
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
  file: File
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
    const maxBytes = 80 * 1024
    const slice = file.slice(0, maxBytes)
    const text = await slice.text()
    if (text.includes('\0')) {
      return undefined
    }
    return text.trim() ? text : undefined
  } catch (_e) {
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

  const checkIfAtBottom = () => {
    const container = scrollContainerRef.current
    if (!container) return true
    const distanceToBottom = container.scrollHeight - container.scrollTop - container.clientHeight
    return distanceToBottom < 100
  }

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
  // but ONLY if user hasn't scrolled up away from the bottom and model is not streaming.
  useEffect(() => {
    const el = contentContainerRef.current
    if (!el || typeof ResizeObserver === 'undefined') return

    const observer = new ResizeObserver(() => {
      if ((isUserAtBottomRef.current || isProgrammaticScrollRef.current) && !isGenerating) {
        scrollToBottomInstant()
      }
    })

    observer.observe(el)
    return () => observer.disconnect()
  }, [isGenerating, scrollToBottomInstant])

  // 2. New message added: only auto-scroll if user sent it so their prompt is in view
  useEffect(() => {
    const currentChatId = chat?.id || null
    const hasNewMessage = messages.length > prevMessagesLengthRef.current
    prevMessagesLengthRef.current = messages.length

    if (hasNewMessage && currentChatId && lastPositionedChatIdRef.current === currentChatId) {
      const lastMsg = messages[messages.length - 1]
      const isUserMessage = lastMsg?.role === 'user'
      if (isUserMessage) {
        scrollToBottom(true)
      }
    }
  }, [messages, chat?.id])

  // NOTE: Strictly do NOT autoscroll during incoming streaming of answer
  // (currentStreamingContent or currentStreamingReasoning), preserving user reading flow.

  // Focus textarea when chat changes
  useEffect(() => {
    textareaRef.current?.focus()
  }, [chat?.id])

  // Populate prompt and focus when entering edit mode
  useEffect(() => {
    if (editingMessage) {
      setInputText(editingMessage.content)
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
    ttsService.stop()
  }, [chat?.id])

  const handleDoubleClickText = (e: React.MouseEvent<HTMLDivElement>, message: Message) => {
    e.stopPropagation()

    let textToSpeak = ''
    const currentTarget = e.currentTarget

    if ((document as any).caretRangeFromPoint) {
      const range = (document as any).caretRangeFromPoint(e.clientX, e.clientY)
      if (range && currentTarget.contains(range.startContainer)) {
        const preCaretRange = document.createRange()
        preCaretRange.selectNodeContents(currentTarget)
        preCaretRange.setEnd(range.startContainer, range.startOffset)
        const offset = preCaretRange.toString().length
        const fullText = currentTarget.innerText || currentTarget.textContent || ''
        let safeOffset = Math.max(0, Math.min(offset, fullText.length))
        while (safeOffset > 0 && /\S/.test(fullText[safeOffset - 1])) {
          safeOffset--
        }
        textToSpeak = fullText.slice(safeOffset).trim()
      }
    }

    if (!textToSpeak) {
      const sel = window.getSelection()
      if (sel && sel.rangeCount > 0 && currentTarget.contains(sel.getRangeAt(0).startContainer)) {
        const range = sel.getRangeAt(0)
        const preCaretRange = document.createRange()
        preCaretRange.selectNodeContents(currentTarget)
        preCaretRange.setEnd(range.startContainer, range.startOffset)
        const offset = preCaretRange.toString().length
        const fullText = currentTarget.innerText || currentTarget.textContent || ''
        let safeOffset = Math.max(0, Math.min(offset, fullText.length))
        while (safeOffset > 0 && /\S/.test(fullText[safeOffset - 1])) {
          safeOffset--
        }
        textToSpeak = fullText.slice(safeOffset).trim()
      }
    }

    if (!textToSpeak) {
      textToSpeak = currentTarget.innerText || currentTarget.textContent || message.content
    }

    // Clear any text selection caused by the browser's double click event
    window.getSelection()?.removeAllRanges()

    if (textToSpeak.trim()) {
      ttsService.speakFromText(message.id, textToSpeak)
    }
  }

  const handleToggleFloatingTts = () => {
    if (ttsState.isPlaying) {
      ttsService.pause()
    } else if (ttsState.isPaused) {
      ttsService.resume()
    } else {
      const latestAssistant = [...displayedMessages].reverse().find((m) => m.role === 'assistant')
      if (latestAssistant) {
        ttsService.speak(latestAssistant.id, latestAssistant.content)
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
    onSendMessage(text, attachmentsPayload)
    setTimeout(() => {
      setIsSubmitting(false)
      isSubmittingRef.current = false
    }, 400)
    scrollToBottom(true)
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

  const topGradient = isDark
    ? 'linear-gradient(to bottom, #000000 0%, rgba(0,0,0,0.90) 30%, rgba(0,0,0,0.70) 55%, rgba(0,0,0,0.40) 75%, rgba(0,0,0,0.15) 90%, transparent 100%)'
    : 'linear-gradient(to bottom, #F9F9F7 0%, rgba(249,249,247,0.90) 30%, rgba(249,249,247,0.70) 55%, rgba(249,249,247,0.40) 75%, rgba(249,249,247,0.15) 90%, transparent 100%)'

  const bottomGradient = isDark
    ? 'linear-gradient(to top, #000000 0%, rgba(0,0,0,0.90) 25%, rgba(0,0,0,0.70) 50%, rgba(0,0,0,0.38) 75%, rgba(0,0,0,0.12) 90%, transparent 100%)'
    : 'linear-gradient(to top, #F9F9F7 0%, rgba(249,249,247,0.90) 25%, rgba(249,249,247,0.70) 50%, rgba(249,249,247,0.38) 75%, rgba(249,249,247,0.12) 90%, transparent 100%)'

  return (
    <div
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      className={`flex-1 h-full min-h-0 relative overflow-hidden ${isDark ? 'bg-black text-[#CECDC4]' : 'bg-[#F9F9F7] text-[#141414]'}`}
    >
      {/* Drag & Drop Visual Overlay */}
      {isDragOver && (
        <div className="absolute inset-0 bg-black/75 backdrop-blur-xs border-2 border-dashed border-[#CECDC4]/40 z-50 flex flex-col items-center justify-center pointer-events-none transition-all">
          <Paperclip className="w-10 h-10 text-[#CECDC4] animate-bounce mb-2" />
          <span className="font-sans text-sm font-medium text-[#CECDC4]">Drop images or files to attach</span>
        </div>
      )}

      {/* Top Subtle Fade Gradient: messages smoothly dissolve behind top bar */}
      <div
        className="absolute top-0 left-0 right-0 h-28 pointer-events-none z-20 transition-all duration-200"
        style={{ background: topGradient }}
      />

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
        {isLoadingMessages ? (
          <div className="h-full flex flex-col items-center justify-center text-center select-none py-12">
            <div className="w-6 h-6 border-2 border-[#262626] border-t-[#CECDC4] rounded-full animate-spin mb-3" />
            <span className="font-sans text-xs text-[#8E8E93]">Loading conversation...</span>
          </div>
        ) : (!chat || displayedMessages.length === 0) && !isGenerating ? (
          // Exact Android Empty State: 160dp eye logo at 0.3 alpha + "Welcome Back, [Username]" + "(◕‿◕)"
          <div className="h-full flex flex-col items-center justify-center text-center select-none py-12">
            <img
              src={logoSrc}
              alt="WYRD Eye"
              className="w-40 h-40 object-contain opacity-30 pointer-events-none"
            />
            <div className="h-7" />
            <div className="font-sans text-[23px] text-[#CECDC4] font-normal">
              Welcome Back,{' '}
              <span className="font-bold text-[#CECDC4]">
                {profile?.username?.trim() || 'Human'}
              </span>
            </div>
            <div className="h-2.5" />
            <div className="font-sans text-[22px] text-[#636366]">(◕‿◕)</div>
          </div>
        ) : (
          <div ref={contentContainerRef} className="max-w-3xl mx-auto space-y-6">
            {displayedMessages.map((msg) => (
              <MessageItem
                key={msg.id}
                message={msg}
                isCopied={copiedId === msg.id}
                onCopy={() => copyToClipboard(msg.content, msg.id)}
                onDoubleClickText={handleDoubleClickText}
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
                  />
                )}

                {streamingStatus && currentStreamingContent.length === 0 && (
                  <div className="flex items-center gap-2.5 text-xs font-sans text-[#8E8E93] px-1 py-1">
                    <span className="w-2 h-2 rounded-full bg-[#CECDC4] animate-pulse" />
                    <span className="tracking-wide">{streamingStatus}</span>
                  </div>
                )}

                {currentStreamingContent && (
                  <MarkdownMessage content={currentStreamingContent} />
                )}
              </div>
            )}

            <div ref={messagesEndRef} />
          </div>
        )}
      </div>

      {/* Bottom Subtle Fade Gradient: messages smoothly dissolve behind floating input capsule */}
      <div
        className="absolute bottom-0 left-0 right-0 h-36 pointer-events-none z-10 transition-all duration-200"
        style={{ background: bottomGradient }}
      />

      {/* Floating Bottom Overlay: Floating Controls & Input Capsule directly over scrolling text */}
      <div className="absolute bottom-0 left-0 right-0 z-20 pointer-events-none px-4 md:px-8 pb-5">
        <div className="max-w-3xl mx-auto">
          {/* Audio Error Alert if any */}
          {audioError && (
            <div className="w-full mb-3 pointer-events-auto">
              <div className="px-4 py-2.5 rounded-xl bg-[#1A1A1A] border border-[#FF453A]/40 text-[#FF453A] text-xs font-sans flex items-center justify-between shadow-lg">
                <span>{audioError}</span>
                <button
                  onClick={() => setAudioError(null)}
                  className="text-[#8E8E93] hover:text-[#CECDC4] p-1"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            </div>
          )}

          {/* Floating Controls Bar: Centered Scroll-to-Bottom Arrow & Right Play/Pause TTS (matching Android) */}
          <div className="relative w-full h-12 mb-2 pointer-events-none">
            {/* Scroll-to-Bottom Arrow Button: Centered on top of input */}
            <div className="absolute left-1/2 -translate-x-1/2 bottom-0 flex items-center justify-center">
              <button
                onClick={() => scrollToBottom(true)}
                title="Scroll to bottom"
                className={`w-[38px] h-[38px] rounded-full bg-[#141414] border border-[#262626] text-[#CECDC4] hover:bg-[#1A1A1A] hover:border-[#383838] transition-all duration-200 flex items-center justify-center cursor-pointer pointer-events-auto ${
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
                  className={`w-11 h-11 rounded-full border flex items-center justify-center transition-all duration-200 cursor-pointer pointer-events-auto ${
                    ttsState.isPlaying
                      ? 'bg-[#CECDC4] text-black border-[#CECDC4] shadow-md'
                      : 'bg-[#141414] text-[#CECDC4] border-[#262626] hover:bg-[#1A1A1A] hover:border-[#383838]'
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
            <div className="mb-2 px-4 py-2 rounded-2xl bg-[#1A1A1A] border border-[#262626] flex items-center justify-between text-xs text-[#CECDC4] font-sans pointer-events-auto shadow-md">
              <div className="flex items-center gap-2">
                <Edit2 className="w-3.5 h-3.5 text-[#8E8E93]" />
                <span className="font-medium">Editing prompt</span>
              </div>
              <button
                onClick={() => {
                  onCancelEdit?.()
                  setInputText('')
                  if (textareaRef.current) textareaRef.current.style.height = 'auto'
                }}
                className="p-1 hover:text-white text-[#8E8E93] rounded transition-colors flex items-center gap-1 cursor-pointer"
                title="Cancel editing"
              >
                <span className="text-[11px]">Cancel</span>
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          )}

          {/* Staged Attachments Preview Bar */}
          {stagedAttachments.length > 0 && (
            <div className="mb-2 p-2 rounded-2xl bg-[#1A1A1A] border border-[#262626] flex items-center gap-2 overflow-x-auto pointer-events-auto shadow-md max-w-full">
              {stagedAttachments.map((att) => (
                <div
                  key={att.id}
                  className="relative group shrink-0 flex items-center gap-2 px-2.5 py-1.5 rounded-xl bg-[#141414] border border-[#262626] text-xs text-[#CECDC4]"
                >
                  {att.type === 'image' ? (
                    <div className="flex items-center gap-2">
                      <img
                        src={att.previewUrl}
                        alt={att.name}
                        className="w-8 h-8 rounded-lg object-cover border border-[#333333]"
                      />
                      <div className="flex flex-col">
                        <span className="max-w-[120px] truncate text-[11px] font-medium">{att.name}</span>
                        <span className="text-[9px] text-[#8E8E93]">{formatFileSize(att.size)}</span>
                      </div>
                    </div>
                  ) : (
                    <div className="flex items-center gap-2">
                      <FileText className="w-5 h-5 text-[#8E8E93] shrink-0" />
                      <div className="flex flex-col">
                        <span className="max-w-[130px] truncate text-[11px] font-medium">{att.name}</span>
                        <span className="text-[9px] text-[#8E8E93]">{formatFileSize(att.size)}</span>
                      </div>
                    </div>
                  )}
                  <button
                    onClick={() => handleRemoveStagedAttachment(att.id)}
                    title="Remove attachment"
                    className="ml-1 w-4 h-4 rounded-full bg-[#262626] hover:bg-[#333333] hover:text-white flex items-center justify-center text-[#8E8E93] transition-colors cursor-pointer"
                  >
                    <X className="w-2.5 h-2.5 stroke-[2.5]" />
                  </button>
                </div>
              ))}
              {isProcessingAttachments && (
                <div className="flex items-center gap-1.5 px-3 py-1 text-xs text-[#8E8E93] shrink-0">
                  <div className="w-3.5 h-3.5 border-2 border-[#262626] border-t-[#CECDC4] rounded-full animate-spin" />
                  <span>Processing...</span>
                </div>
              )}
            </div>
          )}

          {stagedAttachments.length === 0 && isProcessingAttachments && (
            <div className="mb-2 px-3.5 py-2 rounded-2xl bg-[#1A1A1A] border border-[#262626] flex items-center gap-2 text-xs text-[#8E8E93] font-sans pointer-events-auto shadow-md">
              <div className="w-3.5 h-3.5 border-2 border-[#262626] border-t-[#CECDC4] rounded-full animate-spin" />
              <span>Loading attachment...</span>
            </div>
          )}

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

          <div className="relative flex items-center bg-[#141414] border border-[#262626] rounded-[34px] pl-2.5 pr-1.5 py-1.5 shadow-lg pointer-events-auto">
            {/* Left '+' Attachment Button */}
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              title="Add Attachment (Images, Documents, Code)"
              className="w-11 h-11 flex items-center justify-center rounded-full text-[#636366] hover:text-[#CECDC4] transition-colors shrink-0 cursor-pointer"
            >
              <Plus className="w-6 h-6 stroke-[2]" />
            </button>

            {/* Input Textarea OR Recording Interface */}
            {isRecording ? (
              <div className="flex-1 flex items-center justify-between px-3 py-2">
                <div className="flex items-center gap-3">
                  <div className="flex items-center gap-1.5">
                    <span className="w-2.5 h-2.5 rounded-full bg-[#FF453A] animate-pulse" />
                    <span className="font-mono text-xs text-[#FF453A] font-semibold">REC</span>
                  </div>
                  <span className="font-mono text-sm text-[#CECDC4]">
                    {formatDuration(recordingDuration)}
                  </span>
                  {/* Audio wave indicator */}
                  <div className="flex items-center gap-0.5 h-4">
                    <span className="w-0.5 h-2 bg-[#CECDC4] animate-pulse" />
                    <span className="w-0.5 h-4 bg-[#CECDC4] animate-pulse [animation-delay:150ms]" />
                    <span className="w-0.5 h-2.5 bg-[#CECDC4] animate-pulse [animation-delay:300ms]" />
                    <span className="w-0.5 h-3.5 bg-[#CECDC4] animate-pulse [animation-delay:450ms]" />
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={handleCancelRecording}
                    title="Cancel recording"
                    className="p-1.5 rounded-full text-[#8E8E93] hover:text-[#FF453A] hover:bg-[#1A1A1A] transition-colors"
                  >
                    <X className="w-4 h-4" />
                  </button>
                  <button
                    onClick={handleStopRecording}
                    title="Finish & Transcribe"
                    className="px-3.5 py-1.5 rounded-full bg-[#CECDC4] text-black text-xs font-semibold hover:bg-white transition-colors flex items-center gap-1.5"
                  >
                    <Check className="w-3.5 h-3.5 stroke-[2.5]" />
                    <span>Done</span>
                  </button>
                </div>
              </div>
            ) : isTranscribing ? (
              <div className="flex-1 flex items-center gap-2.5 px-3 py-2 text-[#8E8E93] text-xs font-sans">
                <div className="w-4 h-4 border-2 border-[#262626] border-t-[#CECDC4] rounded-full animate-spin" />
                <span>Transcribing audio with OpenAI Whisper...</span>
              </div>
            ) : (
              <textarea
                ref={textareaRef}
                rows={1}
                value={inputText}
                onChange={handleInputChange}
                onKeyDown={handleKeyDown}
                onPaste={handlePaste}
                placeholder="Ask anything..."
                className="flex-1 px-3 py-2 bg-transparent text-[16px] text-[#CECDC4] placeholder-[#7A7A7A] focus:outline-none resize-none max-h-44 font-sans leading-relaxed"
              />
            )}

            {/* Right Action Buttons */}
            {!isRecording && !isTranscribing && (
              <div className="flex items-center gap-1 shrink-0">
                {/* Voice Input Button (Mic - OpenAI Whisper STT) */}
                <button
                  onClick={handleStartRecording}
                  title="Voice dictation (OpenAI Whisper STT)"
                  className="w-10 h-10 rounded-full flex items-center justify-center text-[#8E8E93] hover:text-[#CECDC4] hover:bg-[#1A1A1A] transition-colors"
                >
                  <Mic className="w-5 h-5 stroke-[2]" />
                </button>

                {/* Right Action Button: Send or Stop */}
                {isGenerating ? (
                  <button
                    onClick={onStopGeneration}
                    title="Stop Generation"
                    className="w-11 h-11 rounded-full bg-[#1A1A1A] border border-[#262626] text-[#CECDC4] hover:text-[#FF453A] flex items-center justify-center transition-all shrink-0 ml-1"
                  >
                    <Square className="w-4 h-4 fill-current" />
                  </button>
                ) : (
                  <button
                    onClick={handleSend}
                    disabled={!canSend}
                    title="Send"
                    className={`w-11 h-11 rounded-full flex items-center justify-center transition-all shrink-0 ml-1 ${
                      canSend
                        ? 'bg-[#CECDC4] text-black hover:bg-white'
                        : 'text-[#636366] opacity-30 cursor-not-allowed'
                    }`}
                  >
                    <ArrowRight className="w-5 h-5 stroke-[2.5]" />
                  </button>
                )}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* OpenAI API Key Prompt Modal */}
      <OpenAiKeyModal
        isOpen={showKeyModal}
        onClose={() => {
          setShowKeyModal(false)
          setPendingRecord(false)
        }}
        onSuccess={handleKeyModalSuccess}
      />
    </div>
  )
}

function MessageItem({
  message,
  isCopied,
  onCopy,
  onDoubleClickText,
  onEdit,
  onBranch,
  onRedo,
}: {
  message: Message
  isCopied: boolean
  onCopy: () => void
  onDoubleClickText?: (e: React.MouseEvent<HTMLDivElement>, message: Message) => void
  onEdit?: () => void
  onBranch?: () => void
  onRedo?: () => void
}) {
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false)
      }
    }
    if (menuOpen) {
      document.addEventListener('mousedown', handleClickOutside)
    }
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [menuOpen])

  if (message.role === 'user') {
    const attachments = message.attachments || []
    return (
      <div className="flex flex-col items-end relative space-y-2 w-full" ref={menuRef}>
        {/* Render Attachments if present */}
        {attachments.length > 0 && (
          <div className="flex flex-wrap items-center justify-end gap-2 max-w-[88%]">
            {attachments.map((att, idx) => {
              if (att.type === 'image' && att.uri) {
                const isDisplayable = att.uri.startsWith('data:') || att.uri.startsWith('http://') || att.uri.startsWith('https://')
                if (!isDisplayable) {
                  return (
                    <div
                      key={idx}
                      className="flex items-center gap-2 px-3 py-2 rounded-xl bg-[#141414] border border-[#262626] text-xs text-[#CECDC4] shadow-sm max-w-xs"
                    >
                      <Paperclip className="w-4 h-4 text-[#8E8E93] shrink-0" />
                      <span className="truncate font-medium">{att.name || 'Image'}</span>
                    </div>
                  )
                }
                return (
                  <div
                    key={idx}
                    className="relative rounded-2xl overflow-hidden border border-[#262626] bg-[#141414] shadow-md group cursor-pointer"
                    onClick={() => {
                      if (att.uri.startsWith('data:') || att.uri.startsWith('http')) {
                        const win = window.open()
                        if (win) {
                          win.document.write(
                            `<html><head><title>${att.name || 'Image'}</title></head><body style="margin:0;background:#0A0A0A;display:flex;align-items:center;justify-content:center;height:100vh;"><img src="${att.uri}" style="max-width:95vw;max-height:95vh;object-fit:contain;border-radius:12px;" /></body></html>`
                          )
                        }
                      }
                    }}
                  >
                    <img
                      src={att.uri}
                      alt={att.name || 'Image attachment'}
                      className="max-h-60 max-w-xs object-cover rounded-2xl transition-transform duration-200 group-hover:scale-[1.02]"
                      onError={(e) => {
                        (e.target as HTMLElement).style.display = 'none'
                      }}
                    />
                  </div>
                )
              }
              const isRemote = att.uri && (att.uri.startsWith('http://') || att.uri.startsWith('https://'))
              const content = (
                <div
                  key={idx}
                  className={`flex items-center gap-2 px-3 py-2 rounded-xl bg-[#141414] border border-[#262626] text-xs text-[#CECDC4] shadow-sm max-w-xs transition-colors ${
                    isRemote ? 'hover:border-[#383838] cursor-pointer' : ''
                  }`}
                  onClick={() => {
                    if (isRemote) window.open(att.uri, '_blank')
                  }}
                >
                  <FileText className="w-4 h-4 text-[#8E8E93] shrink-0" />
                  <span className="truncate font-medium">{att.name}</span>
                  {att.size ? (
                    <span className="text-[10px] text-[#8E8E93] shrink-0">
                      ({formatFileSize(att.size)})
                    </span>
                  ) : null}
                </div>
              )
              return content
            })}
          </div>
        )}

        <div
          onDoubleClick={() => setMenuOpen(true)}
          title="Double click for options"
          className="max-w-[88%] px-4.5 py-3.5 rounded-[22px] bg-[#1A1A1A] border border-[#262626] text-[16px] text-[#CECDC4] leading-[28px] font-sans shadow-sm cursor-pointer select-text whitespace-pre-wrap"
        >
          {message.content}
        </div>

        {/* Double-tap / context menu matching Android options: Copy, Edit, Branch, Redo */}
        {menuOpen && (
          <div className="absolute right-0 top-full mt-2 w-48 rounded-xl bg-[#1A1A1A] border border-[#262626] shadow-2xl py-1.5 z-40 text-sm">
            <button
              onClick={() => {
                onCopy()
                setMenuOpen(false)
              }}
              className="w-full px-3.5 py-2 flex items-center gap-2.5 text-left text-sm text-[#CECDC4] hover:bg-[#262626] transition-colors"
            >
              {isCopied ? (
                <Check className="w-4 h-4 text-[#CECDC4]" />
              ) : (
                <Copy className="w-4 h-4 text-[#CECDC4]" />
              )}
              <span>{isCopied ? 'Copied' : 'Copy'}</span>
            </button>
            {onEdit && (
              <button
                onClick={() => {
                  onEdit()
                  setMenuOpen(false)
                }}
                className="w-full px-3.5 py-2 flex items-center gap-2.5 text-left text-sm text-[#CECDC4] hover:bg-[#262626] transition-colors"
              >
                <Edit2 className="w-4 h-4 text-[#CECDC4]" />
                <span>Edit</span>
              </button>
            )}
            {onBranch && (
              <button
                onClick={() => {
                  onBranch()
                  setMenuOpen(false)
                }}
                className="w-full px-3.5 py-2 flex items-center gap-2.5 text-left text-sm text-[#CECDC4] hover:bg-[#262626] transition-colors"
              >
                <GitBranch className="w-4 h-4 text-[#CECDC4]" />
                <span>Branch into a new chat</span>
              </button>
            )}
            {onRedo && (
              <button
                onClick={() => {
                  onRedo()
                  setMenuOpen(false)
                }}
                className="w-full px-3.5 py-2 flex items-center gap-2.5 text-left text-sm text-[#CECDC4] hover:bg-[#262626] transition-colors"
              >
                <RotateCcw className="w-4 h-4 text-[#CECDC4]" />
                <span>Redo</span>
              </button>
            )}
          </div>
        )}
      </div>
    )
  }

  // Assistant Message (Clean, NO avatar, NO title label, single copy button at bottom matching Android)
  return (
    <div className="flex flex-col items-start space-y-2 w-full">
      {/* Reasoning Thought Block */}
      {message.reasoning && <ReasoningBlock reasoning={message.reasoning} isStreaming={false} />}

      {/* Assistant Content: Double click on any word to read via TTS */}
      <div
        onDoubleClick={(e) => onDoubleClickText?.(e, message)}
        className="w-full select-text cursor-text"
        title="Double click on any word to hear from there"
      >
        <MarkdownMessage content={message.content} />
      </div>

      {/* Action buttons matching Android: Single copy button */}
      <div className="pt-2 flex items-center gap-2">
        <button
          onClick={onCopy}
          title="Copy message"
          className="w-10 h-10 rounded-full bg-[#141414] border border-[#262626] flex items-center justify-center text-[#8E8E93] hover:text-[#CECDC4] hover:bg-[#1A1A1A] transition-colors cursor-pointer"
        >
          {isCopied ? <Check className="w-5 h-5 text-[#CECDC4]" /> : <Copy className="w-5 h-5" />}
        </button>
      </div>
    </div>
  )
}

function MarkdownMessage({ content }: { content: string }) {
  return (
    <div className="text-[17px] leading-[30px] font-sans text-[#CECDC4] select-text w-full">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          p: ({ children }) => <p className="mb-4 last:mb-0 leading-[30px]">{children}</p>,
          h1: ({ children }) => <h1 className="text-xl font-bold mt-6 mb-3 text-[#CECDC4] leading-snug">{children}</h1>,
          h2: ({ children }) => <h2 className="text-lg font-bold mt-5 mb-2.5 text-[#CECDC4] leading-snug">{children}</h2>,
          h3: ({ children }) => <h3 className="text-base font-semibold mt-4 mb-2 text-[#CECDC4] leading-snug">{children}</h3>,
          ul: ({ children }) => <ul className="list-disc pl-5 my-3.5 space-y-2 leading-[30px]">{children}</ul>,
          ol: ({ children }) => <ol className="list-decimal pl-5 my-3.5 space-y-2 leading-[30px]">{children}</ol>,
          li: ({ children }) => <li className="leading-[30px] pl-1">{children}</li>,
          blockquote: ({ children }) => (
            <blockquote className="border-l-2 border-[#8E8E93] pl-3.5 my-3.5 italic text-[#8E8E93] leading-[28px]">
              {children}
            </blockquote>
          ),
          code: ({ node, inline, className, children, ...props }: any) => {
            const match = /language-(\w+)/.exec(className || '')
            return !inline ? (
              <div className="my-3.5 rounded-xl overflow-hidden border border-[#262626] bg-[#0A0A0A]">
                <div className="bg-[#141414] px-3.5 py-1.5 flex items-center justify-between border-b border-[#262626] text-[10px] font-mono text-[#8E8E93]">
                  <span>{match ? match[1] : 'code'}</span>
                  <button
                    onClick={() =>
                      navigator.clipboard.writeText(String(children).replace(/\n$/, ''))
                    }
                    className="hover:text-[#CECDC4] transition-colors flex items-center gap-1"
                  >
                    <Copy className="w-3 h-3" />
                    Copy
                  </button>
                </div>
                <pre className="p-3.5 text-xs overflow-x-auto font-mono text-[#CECDC4] leading-[22px]">
                  <code className={className} {...props}>
                    {children}
                  </code>
                </pre>
              </div>
            ) : (
              <code
                className="px-1.5 py-0.5 rounded bg-[#141414] text-[#CECDC4] font-mono text-xs border border-[#262626]"
                {...props}
              >
                {children}
              </code>
            )
          },
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  )
}

function ReasoningBlock({
  reasoning,
  isStreaming,
}: {
  reasoning: string
  isStreaming: boolean
}) {
  const [isExpanded, setIsExpanded] = useState(false)

  return (
    <div className="w-full rounded-xl border border-[#262626] bg-[#141414] overflow-hidden mt-1 mb-3 select-none transition-all">
      <button
        onClick={() => setIsExpanded(!isExpanded)}
        className="w-full px-3.5 py-2.5 flex items-center justify-between text-left text-xs text-[#8E8E93] hover:text-[#CECDC4] transition-colors bg-[#141414]"
      >
        <div className="flex items-center gap-2">
          <Lightbulb
            className={`w-3.5 h-3.5 ${
              isStreaming ? 'text-white animate-pulse' : 'text-[#8E8E93]'
            }`}
          />
          <span
            className={`font-sans font-medium text-xs ${
              isStreaming ? 'text-white' : 'text-[#8E8E93]'
            }`}
          >
            Thinking
          </span>
          {isStreaming && (
            <span className="w-1.5 h-1.5 rounded-full bg-white animate-ping" />
          )}
        </div>
        {isExpanded ? (
          <ChevronUp className="w-4 h-4 text-[#8E8E93]" />
        ) : (
          <ChevronDown className="w-4 h-4 text-[#8E8E93]" />
        )}
      </button>

      {isExpanded && (
        <div className="p-3.5 text-xs text-[#8E8E93] font-mono leading-relaxed whitespace-pre-wrap border-t border-[#262626] max-h-60 overflow-y-auto select-text">
          {reasoning}
        </div>
      )}
    </div>
  )
}
