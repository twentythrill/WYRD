import React, { useState, useEffect, useRef } from 'react'
import { supabase, type Chat, type Message, type Profile, type MessageAttachment, type Project } from '@/lib/supabase'
import { dbService, dataUriToBlob } from '@/services/dbService'
import { streamLlmCompletion, sanitizeStreamedContent, sanitizeReasoning } from '@/services/llmService'
import { TopBar } from '@/components/TopBar'
import { Sidebar } from '@/components/Sidebar'
import { ChatArea } from '@/components/ChatArea'
import { SettingsModal } from '@/components/SettingsModal'
import { AuthModal } from '@/components/AuthModal'
import { ProjectModal } from '@/components/ProjectModal'

export function App() {
  const [session, setSession] = useState<any>(null)
  const [loadingAuth, setLoadingAuth] = useState(true)
  const [profile, setProfile] = useState<Profile | null>(null)

  const [projects, setProjects] = useState<Project[]>([])
  const [activeProjectId, setActiveProjectId] = useState<string | null>(null)
  const [editingProject, setEditingProject] = useState<Project | null>(null)
  const [isProjectModalOpen, setIsProjectModalOpen] = useState(false)

  const [chats, setChats] = useState<Chat[]>([])
  const [activeChatId, setActiveChatId] = useState<string | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [isLoadingMessages, setIsLoadingMessages] = useState(false)

  // Sidebar toggle state
  const [isSidebarOpen, setIsSidebarOpen] = useState(true)
  const [chatSelectTrigger, setChatSelectTrigger] = useState(0)

  // Live generation state
  const [isGenerating, setIsGenerating] = useState(false)
  const isGeneratingRef = useRef(false)
  isGeneratingRef.current = isGenerating
  const [currentStreamingReasoning, setCurrentStreamingReasoning] = useState('')
  const [currentStreamingContent, setCurrentStreamingContent] = useState('')
  const [currentStreamingStatus, setCurrentStreamingStatus] = useState<string | null>(null)
  const abortControllerRef = useRef<AbortController | null>(null)
  const isSendingMessageRef = useRef<boolean>(false)

  // Editing prompt state (matching Android prompt editing)
  const [editingMessage, setEditingMessage] = useState<Message | null>(null)

  // Settings Modal state
  const [isSettingsOpen, setIsSettingsOpen] = useState(false)

  // Theme Management (System, Dark, Light matching Android AppTheme)
  const [appTheme, setAppTheme] = useState<'system' | 'dark' | 'light'>(() => {
    return (localStorage.getItem('wyrd_app_theme') as any) || 'system'
  })
  const [systemIsDark, setSystemIsDark] = useState(() => {
    if (typeof window !== 'undefined' && window.matchMedia) {
      return window.matchMedia('(prefers-color-scheme: dark)').matches
    }
    return true
  })

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    const handler = (e: MediaQueryListEvent) => setSystemIsDark(e.matches)
    mediaQuery.addEventListener('change', handler)
    return () => mediaQuery.removeEventListener('change', handler)
  }, [])

  const isDark = appTheme === 'system' ? systemIsDark : appTheme === 'dark'

  useEffect(() => {
    const root = document.documentElement
    if (isDark) {
      root.setAttribute('data-theme', 'dark')
      root.classList.remove('light')
      root.classList.add('dark')
    } else {
      root.setAttribute('data-theme', 'light')
      root.classList.remove('dark')
      root.classList.add('light')
    }

    // Dynamic Favicon switcher: use black logo for light theme, white logo for dark theme
    const targetFavicon = isDark ? '/app_logo.png' : '/app_icon_trans_black.png'
    const dynamicIcon = document.getElementById('wyrd-favicon') as HTMLLinkElement | null
    if (dynamicIcon) {
      dynamicIcon.href = targetFavicon
    }
    const faviconLinks = document.querySelectorAll<HTMLLinkElement>("link[rel*='icon']")
    faviconLinks.forEach((link) => {
      if (!link.hasAttribute('media') || link.id === 'wyrd-favicon') {
        link.href = targetFavicon
      }
    })
  }, [isDark])

  const handleThemeChange = (newTheme: 'system' | 'dark' | 'light') => {
    setAppTheme(newTheme)
    localStorage.setItem('wyrd_app_theme', newTheme)
  }

  const handleToggleTheme = () => {
    const nextTheme = isDark ? 'light' : 'dark'
    handleThemeChange(nextTheme)
  }

  // 1. Initialize Auth
  useEffect(() => {
    supabase.auth.getSession().then(({ data: { session } }) => {
      setSession(session)
      setLoadingAuth(false)
    })

    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange((_event, session) => {
      setSession(session)
      setLoadingAuth(false)
    })

    return () => subscription.unsubscribe()
  }, [])

  // 2. Fetch User Profile & Chats on Login
  useEffect(() => {
    if (!session?.user) {
      setProfile(null)
      setChats([])
      setMessages([])
      setActiveChatId(null)
      return
    }

    const userId = session.user.id

    const loadProfile = async () => {
      const p = await dbService.getProfile(userId)
      if (p) {
        setProfile(p)
      } else {
        const newProfile = await dbService.updateProfile(userId, {
          username: session.user.email?.split('@')[0] || 'Human',
          system_prompt: '',
          active_model: 'mimo-v2.6-pro',
          reasoning_effort: 'medium',
        })
        setProfile(newProfile)
      }
    }

    const loadProjects = async () => {
      const projectList = await dbService.getProjects(userId)
      setProjects(projectList)
    }

    const loadChats = async () => {
      const chatList = await dbService.getChats(userId)
      const seen = new Set<string>()
      const uniqueChats = chatList.filter((c) => {
        if (seen.has(c.id)) return false
        seen.add(c.id)
        return true
      })
      setChats((prev) => {
        if (
          prev.length === uniqueChats.length &&
          prev.every(
            (c, i) =>
              c.id === uniqueChats[i].id &&
              c.updated_at === uniqueChats[i].updated_at &&
              c.title === uniqueChats[i].title &&
              c.is_pinned === uniqueChats[i].is_pinned &&
              c.project_id === uniqueChats[i].project_id
          )
        ) {
          return prev
        }
        return uniqueChats
      })
    }

    loadProfile()
    loadProjects()
    loadChats()
    dbService.cleanOrphanedStorageAttachments(userId).then((deleted) => {
      if (deleted && deleted.length > 0) {
        console.log('[WYRD] Cleaned orphaned storage attachments on startup:', deleted)
      }
    })

    // Realtime chat & project sync
    const unsubscribeChats = dbService.subscribeToChats(userId, () => {
      loadChats()
    })
    const unsubscribeProjects = dbService.subscribeToProjects(userId, () => {
      loadProjects()
    })

    return () => {
      unsubscribeChats()
      unsubscribeProjects()
    }
  }, [session?.user?.id])

  // 3. Load Messages when Active Chat changes
  useEffect(() => {
    if (!activeChatId) {
      setMessages([])
      setIsLoadingMessages(false)
      return
    }

    let isCurrent = true
    setMessages([])
    setIsLoadingMessages(true)
    const loadMessages = async () => {
      try {
        const msgs = await dbService.getMessages(activeChatId)
        if (!isCurrent) return
        setMessages((prev) => {
          if (isGeneratingRef.current) {
            const map = new Map<string, Message>()
            for (const m of msgs) map.set(m.id, m)
            for (const m of prev) {
              if (!map.has(m.id)) map.set(m.id, m)
            }
            return Array.from(map.values())
          }
          const seen = new Set<string>()
          return msgs.filter((m) => {
            if (seen.has(m.id)) return false
            seen.add(m.id)
            return true
          })
        })
      } finally {
        if (isCurrent) {
          setIsLoadingMessages(false)
        }
      }
    }

    loadMessages()

    // Realtime message sync
    const unsubscribeMessages = dbService.subscribeToMessages(
      activeChatId,
      (newMsg) => {
        setMessages((prev) => {
          const index = prev.findIndex((m) => m.id === newMsg.id)
          if (index >= 0) {
            const updated = [...prev]
            updated[index] = newMsg
            return updated
          }
          return [...prev, newMsg]
        })
      },
      (deletedId) => {
        setMessages((prev) => prev.filter((m) => m.id !== deletedId))
      }
    )

    return () => {
      isCurrent = false
      unsubscribeMessages()
    }
  }, [activeChatId])

  // 4. Tab Focus & Periodic sync (fallback to ensure instant synchronization with mobile)
  useEffect(() => {
    if (!session?.user) return

    const handleSync = async () => {
      try {
        const [projectList, chatList] = await Promise.all([
          dbService.getProjects(session.user.id),
          dbService.getChats(session.user.id),
        ])
        setProjects(projectList)

        const seenChats = new Set<string>()
        const uniqueChats = chatList.filter((c) => {
          if (seenChats.has(c.id)) return false
          seenChats.add(c.id)
          return true
        })
        setChats((prev) => {
          if (
            prev.length === uniqueChats.length &&
            prev.every(
              (c, i) =>
                c.id === uniqueChats[i].id &&
                c.updated_at === uniqueChats[i].updated_at &&
                c.title === uniqueChats[i].title &&
                c.is_pinned === uniqueChats[i].is_pinned &&
                c.project_id === uniqueChats[i].project_id
            )
          ) {
            return prev
          }
          return uniqueChats
        })
        if (activeChatId && !isGeneratingRef.current) {
          const msgs = await dbService.getMessages(activeChatId)
          const seenMsgs = new Set<string>()
          const uniqueMsgs = msgs.filter((m) => {
            if (seenMsgs.has(m.id)) return false
            seenMsgs.add(m.id)
            return true
          })
          setMessages((prev) => {
            if (
              prev.length === uniqueMsgs.length &&
              prev.every(
                (m, i) =>
                  m.id === uniqueMsgs[i].id &&
                  m.content === uniqueMsgs[i].content &&
                  m.reasoning === uniqueMsgs[i].reasoning
              )
            ) {
              return prev
            }
            return uniqueMsgs
          })
        }
      } catch (err) {
        console.warn('Sync fallback error:', err)
      }
    }

    window.addEventListener('focus', handleSync)
    const interval = setInterval(handleSync, 10000)

    return () => {
      window.removeEventListener('focus', handleSync)
      clearInterval(interval)
    }
  }, [session?.user?.id, activeChatId])

  // --- Handlers ---
  const handleNewChat = () => {
    setActiveChatId(null)
    setActiveProjectId(null)
    setMessages([])
    setIsGenerating(false)
  }

  const handleNewChatInProject = (projectId: string) => {
    setActiveChatId(null)
    setActiveProjectId(projectId)
    setMessages([])
    setIsGenerating(false)
  }

  const handleCreateProject = () => {
    setEditingProject(null)
    setIsProjectModalOpen(true)
  }

  const handleEditProject = (project: Project) => {
    setEditingProject(project)
    setIsProjectModalOpen(true)
  }

  const handleSaveProject = async (
    name: string,
    description: string,
    systemPrompt: string,
    color: string = ''
  ): Promise<Project | null> => {
    const userId = session?.user?.id || 'local_user'
    if (editingProject) {
      const updated = await dbService.updateProject(editingProject.id, {
        name,
        description,
        system_prompt: systemPrompt,
        color,
      })
      if (updated) {
        setProjects((prev) => prev.map((p) => (p.id === updated.id ? updated : p)))
        return updated
      }
    } else {
      const created = await dbService.createProject(
        userId,
        name,
        systemPrompt,
        description,
        color
      )
      if (created) {
        setProjects((prev) => {
          const exists = prev.some((p) => p.id === created.id)
          return exists ? prev.map((p) => (p.id === created.id ? created : p)) : [created, ...prev]
        })
        setActiveProjectId(created.id)
        setActiveChatId(null)
        setMessages([])
        return created
      }
    }
    return null
  }

  const handleDeleteProject = async (projectId: string): Promise<boolean> => {
    const success = await dbService.deleteProject(projectId)
    if (success) {
      setProjects((prev) => prev.filter((p) => p.id !== projectId))
      if (activeProjectId === projectId) {
        setActiveProjectId(null)
        setActiveChatId(null)
        setMessages([])
      }
      setChats((prev) =>
        prev.map((c) => (c.project_id === projectId ? { ...c, project_id: null } : c))
      )
      return true
    }
    return false
  }

  const handleTogglePin = async (chatToToggle?: Chat) => {
    const targetChat = chatToToggle || chats.find((c) => c.id === activeChatId)
    if (!targetChat) return

    const updated = await dbService.updateChat(targetChat.id, {
      is_pinned: !targetChat.is_pinned,
    })

    if (updated) {
      setChats((prev) => prev.map((c) => (c.id === updated.id ? updated : c)))
    }
  }

  const handleDeleteChat = async (chatIdToDelete?: string) => {
    const targetId = chatIdToDelete || activeChatId
    if (!targetId) return

    const success = await dbService.deleteChat(targetId)
    if (success) {
      setChats((prev) => {
        const remaining = prev.filter((c) => c.id !== targetId)
        if (activeChatId === targetId) {
          setActiveChatId(null)
        }
        return remaining
      })
    }
  }

  const handleRenameChat = async (chat: Chat, newTitle: string) => {
    const updated = await dbService.updateChat(chat.id, { title: newTitle })
    if (updated) {
      setChats((prev) => prev.map((c) => (c.id === updated.id ? updated : c)))
    }
  }

  const handleDeleteUnpinnedChats = async () => {
    if (!session?.user) return
    const success = await dbService.deleteAllUnpinnedChats(session.user.id)
    if (success) {
      setChats((prev) => {
        const remaining = prev.filter((c) => c.is_pinned)
        if (!remaining.some((c) => c.id === activeChatId)) {
          setActiveChatId(null)
        }
        return remaining
      })
    }
  }

  const handleSaveProfile = async (updates: Partial<Profile>) => {
    if (!session?.user) return
    const updated = await dbService.updateProfile(session.user.id, updates)
    if (updated) {
      setProfile(updated)
    }
  }

  const handleModelSelected = async (modelId: string) => {
    if (!session?.user) return
    setProfile((prev) => (prev ? { ...prev, active_model: modelId } : null))
    await dbService.updateProfile(session.user.id, { active_model: modelId })
  }

  const isThinkingEnabled = profile?.reasoning_effort ? profile.reasoning_effort !== 'off' : true

  const handleToggleThinking = async (enabled: boolean) => {
    const effortId = enabled ? 'on' : 'off'
    if (!session?.user) {
      setProfile((prev) => (prev ? { ...prev, reasoning_effort: effortId } : null))
      return
    }
    setProfile((prev) => (prev ? { ...prev, reasoning_effort: effortId } : null))
    await dbService.updateProfile(session.user.id, { reasoning_effort: effortId })
  }

  const handleReasoningEffortSelected = async (effortId: string) => {
    if (!session?.user) return
    setProfile((prev) => (prev ? { ...prev, reasoning_effort: effortId } : null))
    await dbService.updateProfile(session.user.id, { reasoning_effort: effortId })
  }

  const handleSignOut = async () => {
    try {
      await supabase.auth.signOut()
    } catch (e) {
      console.error('Sign out error:', e)
    } finally {
      setSession(null)
      setProfile(null)
      setChats([])
      setMessages([])
      setActiveChatId(null)
      setIsSettingsOpen(false)
    }
  }

  // --- Stream Completion Helper ---
  const executeAssistantStream = async (chatId: string, history: Message[]) => {
    setIsGenerating(true)
    setCurrentStreamingReasoning('')
    setCurrentStreamingContent('')
    setCurrentStreamingStatus(null)

    const controller = new AbortController()
    abortControllerRef.current = controller

    const apiKey = localStorage.getItem('wyrd_api_key') || ''
    const storedBaseUrl = localStorage.getItem('wyrd_base_url') || ''
    const baseUrl = storedBaseUrl === 'https://api.siliconflow.cn/v1' ? '' : storedBaseUrl

    const conversationHistory = history.map((m) => {
      const imagesBase64: string[] = []
      const fileContexts: string[] = []

      if (m.attachments && m.attachments.length > 0) {
        for (const att of m.attachments) {
          if (att.type === 'image') {
            if (att.uri.startsWith('data:image/')) {
              const commaIdx = att.uri.indexOf(',')
              imagesBase64.push(commaIdx !== -1 ? att.uri.substring(commaIdx + 1) : att.uri)
            } else if (att.uri.startsWith('http://') || att.uri.startsWith('https://')) {
              imagesBase64.push(att.uri)
            }
          } else if (att.type === 'file' && att.textContent) {
            fileContexts.push(`[Attached File: ${att.name}]\n\`\`\`\n${att.textContent}\n\`\`\``)
          }
        }
      }

      const effectiveContent = fileContexts.length > 0
        ? `${fileContexts.join('\n\n')}\n\n${m.content}`
        : m.content

      return {
        role: m.role as 'user' | 'assistant' | 'system',
        content: effectiveContent,
        imagesBase64: imagesBase64.length > 0 ? imagesBase64 : undefined,
      }
    })

    let fullReasoning = ''
    let fullContent = ''

    // Determine system prompt: if in project, strictly override global system prompt with project instructions & knowledge files
    const currentChat = chats.find((c) => c.id === chatId)
    const effectiveProjectId = currentChat ? (currentChat.project_id || null) : activeProjectId
    const currentProject = effectiveProjectId ? projects.find((p) => p.id === effectiveProjectId) : null

    let effectiveSystemPrompt = profile?.system_prompt || ''

    if (currentProject) {
      let promptText = currentProject.system_prompt || ''
      try {
        const pFiles = await dbService.getProjectFiles(currentProject.id)
        const textFiles = pFiles.filter((f) => f.text_content && f.text_content.trim().length > 0)
        if (textFiles.length > 0) {
          const filesContent = textFiles
            .map((f) => `--- File: ${f.name} ---\n${f.text_content}`)
            .join('\n\n')
          promptText = promptText
            ? `${promptText}\n\n[Project Knowledge Base]:\n${filesContent}`
            : `[Project Knowledge Base]:\n${filesContent}`
        }
      } catch (err) {
        console.warn('Error loading project files for prompt context:', err)
      }
      effectiveSystemPrompt = promptText
    }

    try {
      const stream = streamLlmCompletion({
        apiKey,
        baseUrl,
        model: profile?.active_model || 'mimo-v2.6-pro',
        isThinkingEnabled,
        systemPrompt: effectiveSystemPrompt,
        messages: conversationHistory,
        signal: controller.signal,
      })

      for await (const chunk of stream) {
        if (controller.signal.aborted) break

        if (chunk.clearContent) {
          fullContent = ''
          setCurrentStreamingContent('')
          if (isThinkingEnabled) {
            if (fullReasoning.trim().length > 0 && !fullReasoning.endsWith('\n\n')) {
              fullReasoning += '\n\n'
            }
            setCurrentStreamingReasoning(fullReasoning)
          } else {
            fullReasoning = ''
            setCurrentStreamingReasoning('')
          }
        }
        if (chunk.status) {
          setCurrentStreamingStatus(chunk.status)
        }
        if (chunk.reasoning && isThinkingEnabled) {
          setCurrentStreamingStatus(null)
          fullReasoning += chunk.reasoning
          setCurrentStreamingReasoning(fullReasoning)
        }
        if (chunk.content) {
          setCurrentStreamingStatus(null)
          fullContent += chunk.content
          setCurrentStreamingContent(fullContent)
        }
        if (chunk.error) {
          fullContent += `\n\nError: ${chunk.error}`
          setCurrentStreamingContent(fullContent)
        }
      }

      // 3. Persist Assistant Message
      const rawContent = fullContent.trim()
      const rawReasoning = isThinkingEnabled ? fullReasoning.trim() : ''
      const finalContent = sanitizeStreamedContent(rawContent)
      const finalReasoning = rawReasoning ? sanitizeReasoning(rawReasoning) : null

      if ((finalContent || finalReasoning) && !controller.signal.aborted) {
        const assistantMsg = (await dbService.createMessage({
          chat_id: chatId,
          user_id: session?.user?.id || 'guest',
          role: 'assistant',
          content: finalContent || '...',
          reasoning: finalReasoning,
          model_name: profile?.active_model || 'mimo-v2.6-pro',
        })) || {
          id: `local_asst_${Date.now()}`,
          chat_id: chatId,
          user_id: session?.user?.id || 'guest',
          role: 'assistant' as const,
          content: finalContent || '...',
          reasoning: finalReasoning,
          model_name: profile?.active_model || 'mimo-v2.6-pro',
          created_at: new Date().toISOString(),
        }

        setMessages((prev) => {
          if (prev.some((m) => m.id === assistantMsg.id)) return prev
          return [...prev, assistantMsg]
        })
      }
    } catch (err: any) {
      if (err.name !== 'AbortError') {
        console.error('Generation error:', err)
      }
    } finally {
      setIsGenerating(false)
      setCurrentStreamingReasoning('')
      setCurrentStreamingContent('')
      setCurrentStreamingStatus(null)
      abortControllerRef.current = null
    }
  }

  // --- Send Message & Stream ---
  const handleSendMessage = async (
    text: string,
    attachments: MessageAttachment[] = []
  ) => {
    if (!session?.user) return
    const hasAttachments = attachments && attachments.length > 0
    let trimmed = text.trim()

    if (!trimmed && hasAttachments) {
      const allImages = attachments.every((a) => a.type === 'image')
      if (allImages) {
        trimmed = attachments.length === 1 ? 'What is in this image?' : 'What is in these images?'
      } else {
        trimmed = 'Analyze the attached file(s).'
      }
    }

    if (!trimmed || isSendingMessageRef.current || isGeneratingRef.current) return
    isSendingMessageRef.current = true

    let currentChatId = activeChatId
    try {
      if (!currentChatId) {
        const titleSource = text.trim() || attachments[0]?.name || 'New Chat'
        const initialWords = titleSource.split(/\s+/).filter(Boolean).slice(0, 4).join(' ')
        const initialTitle = initialWords || 'New Chat'
        const targetProjectId = activeProjectId || null
        const createdChat = await dbService.createChat(session?.user?.id || 'guest', initialTitle, targetProjectId)
        const newChat: Chat = {
          id: createdChat?.id || `local_chat_${Date.now()}`,
          user_id: session?.user?.id || 'guest',
          title: createdChat?.title || initialTitle,
          is_pinned: createdChat?.is_pinned || false,
          project_id: createdChat?.project_id || targetProjectId,
          created_at: createdChat?.created_at || new Date().toISOString(),
          updated_at: createdChat?.updated_at || new Date().toISOString(),
        }
        setChats((prev) => {
          if (prev.some((c) => c.id === newChat.id)) {
            return prev.map((c) => (c.id === newChat.id ? newChat : c))
          }
          return [newChat, ...prev]
        })
        setActiveChatId(newChat.id)
        currentChatId = newChat.id
      } else {
        const currentChat = chats.find((c) => c.id === currentChatId)
        if (currentChat && (currentChat.title === 'New Chat' || currentChat.title === '')) {
          const titleSource = text.trim() || attachments[0]?.name || 'New Chat'
          const initialWords = titleSource.split(/\s+/).filter(Boolean).slice(0, 4).join(' ')
          const generatedTitle = initialWords || 'New Chat'
          dbService.updateChat(currentChatId, { title: generatedTitle }).then((up) => {
            if (up) setChats((prev) => prev.map((c) => (c.id === up.id ? up : c)))
          })
        }
      }

      let updatedMessages = [...messages]

      // If user was editing a prompt, delete subsequent turns from that turn onward
      if (editingMessage && currentChatId) {
        const targetIdx = messages.findIndex((m) => m.id === editingMessage.id)
        const toDeleteIds = targetIdx !== -1 ? messages.slice(targetIdx).map((m) => m.id) : [editingMessage.id]
        if (targetIdx !== -1) {
          updatedMessages = messages.slice(0, targetIdx)
        }
        setMessages(updatedMessages)
        setEditingMessage(null)
        await dbService.deleteMessagesFrom(currentChatId, editingMessage.created_at, toDeleteIds)
      }

      // Process and upload attachments to Supabase Storage if present
      const processedAttachments: MessageAttachment[] = []
      if (attachments && attachments.length > 0) {
        for (const att of attachments) {
          let publicUrl: string | null = null
          if (att.uri && att.uri.startsWith('data:')) {
            try {
              const blob = dataUriToBlob(att.uri)
              publicUrl = await dbService.uploadAttachment(session?.user?.id || 'guest', blob, att.name || 'image.jpg')
            } catch (err) {
              console.warn('Failed to upload dataUri attachment to Supabase Storage:', err)
            }
          } else if (att.file) {
            try {
              publicUrl = await dbService.uploadAttachment(session?.user?.id || 'guest', att.file, att.name || 'file')
            } catch (err) {
              console.warn('Failed to upload File attachment to Supabase Storage:', err)
            }
          }

          processedAttachments.push({
            uri: publicUrl || att.uri,
            type: att.type,
            name: att.name,
            size: att.size,
            textContent: att.textContent,
          })
        }
      }

      // 1. Insert User Message with attachments
      const userMsg = (await dbService.createMessage({
        chat_id: currentChatId,
        user_id: session?.user?.id || 'guest',
        role: 'user',
        content: trimmed,
        attachments: processedAttachments,
      })) || {
        id: `local_msg_${Date.now()}`,
        chat_id: currentChatId,
        user_id: session?.user?.id || 'guest',
        role: 'user' as const,
        content: trimmed,
        attachments: processedAttachments,
        created_at: new Date().toISOString(),
      }

      updatedMessages = [...updatedMessages, userMsg]
      setMessages(updatedMessages)

      // 2. Prepare for Assistant Generation
      await executeAssistantStream(currentChatId, updatedMessages)
    } finally {
      isSendingMessageRef.current = false
    }
  }

  const handleStopGeneration = () => {
    abortControllerRef.current?.abort()
    setIsGenerating(false)
  }

  const handleBranchMessage = async (msg: Message) => {
    if (!session?.user || !activeChatId) return
    const currentChat = chats.find((c) => c.id === activeChatId)
    const baseTitle = currentChat?.title || 'Chat'
    const branchTitle = baseTitle.endsWith('(Branch)') ? baseTitle : `${baseTitle} (Branch)`

    const targetIdx = messages.findIndex((m) => m.id === msg.id)
    if (targetIdx === -1) return

    const branchSlice = messages.slice(0, targetIdx + 1)
    if (targetIdx + 1 < messages.length && messages[targetIdx + 1].role === 'assistant') {
      branchSlice.push(messages[targetIdx + 1])
    }

    const newChat = await dbService.createChat(
      session.user.id,
      branchTitle,
      currentChat?.project_id || null
    )
    if (!newChat) return

    const newMessagesPayload = branchSlice.map((m) => ({
      chat_id: newChat.id,
      user_id: session.user.id,
      role: m.role,
      content: m.content,
      reasoning: m.reasoning || null,
      model_name: m.model_name || null,
      attachments: m.attachments || [],
    }))

    const createdBatch = await dbService.createMessagesBatch(newMessagesPayload)
    setChats((prev) => [newChat, ...prev])
    setActiveChatId(newChat.id)
    setMessages(createdBatch)
  }

  const handleRedoMessage = async (fromUserMsg: Message) => {
    if (!session?.user || isGeneratingRef.current || !activeChatId) return

    // Truncate messages in local state immediately
    const targetIdx = messages.findIndex((m) => m.id === fromUserMsg.id)
    if (targetIdx === -1) return
    const toDeleteIds = messages.slice(targetIdx + 1).map((m) => m.id)
    const truncatedMessages = messages.slice(0, targetIdx + 1)
    setMessages(truncatedMessages)

    // Delete messages after this user message in Supabase
    await dbService.deleteMessagesAfter(activeChatId, fromUserMsg.created_at, toDeleteIds)

    // Re-run stream with history up to this user message
    await executeAssistantStream(activeChatId, truncatedMessages)
  }

  if (loadingAuth) {
    return (
      <div className="w-screen h-screen bg-black flex items-center justify-center font-sans text-xs text-[#636366]">
        WYRD
      </div>
    )
  }

  if (!session) {
    return <AuthModal onSuccess={() => {}} />
  }

  const activeChat = chats.find((c) => c.id === activeChatId) || null
  const activeProject =
    projects.find((p) => p.id === (activeChat ? (activeChat.project_id || null) : activeProjectId)) || null

  return (
    <div className="w-screen h-screen flex bg-black overflow-hidden select-none font-sans text-[#CECDC4]">
      {/* Drawer / Sidebar matching DrawerContent.kt */}
      <Sidebar
        isOpen={isSidebarOpen}
        chats={chats}
        projects={projects}
        activeChatId={activeChatId}
        activeProjectId={activeProjectId}
        isDark={isDark}
        onClose={() => setIsSidebarOpen(false)}
        onSelectChat={(id) => {
          const selected = chats.find((c) => c.id === id)
          setActiveProjectId(selected?.project_id || null)
          if (id !== activeChatId) {
            setMessages([])
            setIsLoadingMessages(true)
            setActiveChatId(id)
          }
          setChatSelectTrigger(Date.now())
        }}
        onNewChat={handleNewChat}
        onNewChatInProject={handleNewChatInProject}
        onTogglePin={(c) => handleTogglePin(c)}
        onRenameChat={handleRenameChat}
        onDeleteChat={(id) => handleDeleteChat(id)}
        onCreateProject={handleCreateProject}
        onEditProject={handleEditProject}
        onOpenSettings={() => setIsSettingsOpen(true)}
      />

      {/* Main Screen */}
      <div className={`flex-1 relative flex flex-col h-full min-w-0 overflow-hidden ${isDark ? 'bg-black text-[#CECDC4]' : 'bg-[#F9F9F7] text-[#141414]'}`}>
        {/* TopBar matching TopBar.kt */}
        <div className="absolute top-0 left-0 right-0 z-30 pointer-events-auto">
          <TopBar
            activeModelId={profile?.active_model || 'mimo-v2.6-pro'}
            isThinkingEnabled={isThinkingEnabled}
            reasoningEffortId={profile?.reasoning_effort || 'medium'}
            activeChatTitle={activeChat?.title}
            activeProjectName={activeProject?.name}
            activeProjectColor={activeProject?.color}
            isSidebarOpen={isSidebarOpen}
            isDark={isDark}
            onSelectModel={handleModelSelected}
            onToggleThinking={handleToggleThinking}
            onSelectReasoningEffort={handleReasoningEffortSelected}
            onToggleSidebar={() => setIsSidebarOpen(!isSidebarOpen)}
            onToggleTheme={handleToggleTheme}
          />
        </div>

        {/* Chat Feed & Empty State matching HomeScreen.kt */}
        <ChatArea
          chat={activeChat}
          activeProject={activeProject}
          messages={messages}
          profile={profile}
          isGenerating={isGenerating}
          isLoadingMessages={isLoadingMessages}
          isDark={isDark}
          chatSelectTrigger={chatSelectTrigger}
          currentStreamingReasoning={currentStreamingReasoning}
          currentStreamingContent={currentStreamingContent}
          streamingStatus={currentStreamingStatus}
          editingMessage={editingMessage}
          onCancelEdit={() => setEditingMessage(null)}
          onEditMessage={(msg) => setEditingMessage(msg)}
          onSendMessage={handleSendMessage}
          onStopGeneration={handleStopGeneration}
          onBranchMessage={handleBranchMessage}
          onRedoMessage={handleRedoMessage}
        />
      </div>

      {/* Settings Screen Dialog matching SettingsScreen.kt */}
      <SettingsModal
        isOpen={isSettingsOpen}
        profile={profile}
        userEmail={session?.user?.email}
        appTheme={appTheme}
        isDark={isDark}
        onClose={() => setIsSettingsOpen(false)}
        onSaveProfile={handleSaveProfile}
        onDeleteUnpinnedChats={handleDeleteUnpinnedChats}
        onSignOut={handleSignOut}
        onThemeChange={handleThemeChange}
      />

      {/* Project Settings / Create Dialog */}
      <ProjectModal
        isOpen={isProjectModalOpen}
        project={editingProject}
        userId={session?.user?.id || 'local_user'}
        isDark={isDark}
        onClose={() => {
          setIsProjectModalOpen(false)
          setEditingProject(null)
        }}
        onSave={handleSaveProject}
        onDelete={handleDeleteProject}
      />
    </div>
  )
}

export default App
