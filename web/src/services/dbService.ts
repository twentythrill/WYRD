import {
  supabase,
  type Chat,
  type Message,
  type Profile,
  type Project,
  type ProjectFile,
} from '@/lib/supabase'

export const dbService = {
  // --- Profile Operations ---
  async getProfile(userId: string): Promise<Profile | null> {
    const { data, error } = await supabase
      .from('profiles')
      .select('*')
      .eq('id', userId)
      .maybeSingle()

    if (error) {
      console.error('Error fetching profile:', error)
      return null
    }
    return data
  },

  async updateProfile(userId: string, updates: Partial<Profile>): Promise<Profile | null> {
    const { data, error } = await supabase
      .from('profiles')
      .upsert({ id: userId, ...updates, updated_at: new Date().toISOString() })
      .select()
      .single()

    if (error) {
      console.error('Error updating profile:', error)
      return null
    }
    return data
  },

  // --- Project Operations ---
  async getProjects(userId: string): Promise<Project[]> {
    try {
      const { data, error } = await supabase
        .from('projects')
        .select('*')
        .eq('user_id', userId)
        .order('is_pinned', { ascending: false })
        .order('updated_at', { ascending: false })
        .order('id', { ascending: false })

      if (error) {
        console.error('Error fetching projects from Supabase:', error.message)
        return []
      }
      return data || []
    } catch (e) {
      console.error('Network error fetching projects:', e)
      return []
    }
  },

  async createProject(
    userId: string,
    name: string,
    systemPrompt: string = '',
    description?: string
  ): Promise<Project | null> {
    try {
      const { data, error } = await supabase
        .from('projects')
        .insert({
          user_id: userId,
          name,
          system_prompt: systemPrompt,
          description: description || null,
          is_pinned: false,
        })
        .select()
        .single()

      if (error) {
        console.error('Error creating project:', error.message)
        return null
      }
      return data
    } catch (err) {
      console.error('Exception creating project:', err)
      return null
    }
  },

  async updateProject(projectId: string, updates: Partial<Project>): Promise<Project | null> {
    try {
      const { data, error } = await supabase
        .from('projects')
        .update({ ...updates, updated_at: new Date().toISOString() })
        .eq('id', projectId)
        .select()
        .single()

      if (error) {
        console.error('Error updating project:', error.message)
        return null
      }
      return data
    } catch (err) {
      console.error('Exception updating project:', err)
      return null
    }
  },

  async deleteProject(projectId: string): Promise<boolean> {
    try {
      // 1. Delete all project files in storage
      const { data: files } = await supabase
        .from('project_files')
        .select('storage_path')
        .eq('project_id', projectId)

      if (files && files.length > 0) {
        const paths = files.map((f) => f.storage_path).filter(Boolean)
        if (paths.length > 0) {
          await deleteStorageAttachments(paths)
        }
      }

      // 2. Detach chats and delete project row
      await supabase.from('chats').update({ project_id: null }).eq('project_id', projectId)
      const { error } = await supabase.from('projects').delete().eq('id', projectId)
      if (error) {
        console.error('Error deleting project:', error.message)
        return false
      }
      return true
    } catch (e) {
      console.error('Error during project deletion:', e)
      return false
    }
  },

  // --- Project Files Operations ---
  async getProjectFiles(projectId: string): Promise<ProjectFile[]> {
    try {
      const { data, error } = await supabase
        .from('project_files')
        .select('*')
        .eq('project_id', projectId)
        .order('created_at', { ascending: true })

      if (error) {
        console.error('Error fetching project files:', error.message)
        return []
      }
      return data || []
    } catch (err) {
      console.error('Exception fetching project files:', err)
      return []
    }
  },

  async createProjectFile(
    userId: string,
    projectId: string,
    fileInfo: {
      name: string
      size: number
      mimeType: string
      storagePath: string
      textContent?: string
    }
  ): Promise<ProjectFile | null> {
    try {
      const { data, error } = await supabase
        .from('project_files')
        .insert({
          user_id: userId,
          project_id: projectId,
          name: fileInfo.name,
          size: fileInfo.size,
          mime_type: fileInfo.mimeType,
          storage_path: fileInfo.storagePath,
          text_content: fileInfo.textContent || null,
        })
        .select()
        .single()

      if (error) {
        console.error('Failed to insert project file:', error.message)
        return null
      }
      return data
    } catch (err) {
      console.error('Exception inserting project file:', err)
      return null
    }
  },

  async deleteProjectFile(fileId: string, storagePath?: string): Promise<boolean> {
    if (storagePath) {
      try {
        await deleteStorageAttachments([storagePath])
      } catch (e) {
        console.warn('Error deleting project file from storage:', e)
      }
    }
    try {
      const { error } = await supabase.from('project_files').delete().eq('id', fileId)
      return !error
    } catch {
      return false
    }
  },

  // --- Chat Operations ---
  async getChats(userId: string): Promise<Chat[]> {
    const { data, error } = await supabase
      .from('chats')
      .select('*')
      .eq('user_id', userId)
      .order('is_pinned', { ascending: false })
      .order('updated_at', { ascending: false })
      .order('id', { ascending: false })

    if (error) {
      console.error('Error fetching chats:', error)
      return []
    }
    return data || []
  },

  async createChat(
    userId: string,
    title: string = 'New Chat',
    projectId?: string | null
  ): Promise<Chat | null> {
    try {
      const { data, error } = await supabase
        .from('chats')
        .insert({
          user_id: userId,
          title,
          is_pinned: false,
          project_id: projectId || null,
        })
        .select()
        .single()

      if (error) {
        console.error('Error creating chat in Supabase:', error)
        return null
      }
      return data
    } catch (e) {
      console.error('Exception creating chat:', e)
      return null
    }
  },

  async updateChat(chatId: string, updates: Partial<Chat>): Promise<Chat | null> {
    const { data, error } = await supabase
      .from('chats')
      .update({ ...updates, updated_at: new Date().toISOString() })
      .eq('id', chatId)
      .select()
      .single()

    if (error) {
      console.error('Error updating chat:', error)
      return null
    }
    return data
  },

  async deleteChat(chatId: string): Promise<boolean> {
    if (chatId.startsWith('local_chat_')) {
      return true
    }

    try {
      // 1. Find and delete attachments in storage
      const { data: msgs } = await supabase
        .from('messages')
        .select('attachments')
        .eq('chat_id', chatId)

      if (msgs && msgs.length > 0) {
        const paths = extractStoragePathsFromMessages(msgs)
        if (paths.length > 0) {
          await deleteStorageAttachments(paths)
        }
      }

      await supabase.from('messages').delete().eq('chat_id', chatId)
    } catch (e) {
      console.warn('Error during chat messages/attachments cleanup:', e)
    }

    const { error } = await supabase.from('chats').delete().eq('id', chatId)
    if (error && error.code !== 'PGRST116') {
      console.error('Error deleting chat:', error)
      return false
    }
    return true
  },

  async deleteAllUnpinnedChats(userId: string): Promise<boolean> {
    try {
      const { data: unpinned } = await supabase
        .from('chats')
        .select('id')
        .eq('user_id', userId)
        .eq('is_pinned', false)

      if (unpinned && unpinned.length > 0) {
        const ids = unpinned.map((c) => c.id)

        // 1. Find and delete attachments across all unpinned chats
        const { data: msgs } = await supabase
          .from('messages')
          .select('attachments')
          .in('chat_id', ids)

        if (msgs && msgs.length > 0) {
          const paths = extractStoragePathsFromMessages(msgs)
          if (paths.length > 0) {
            await deleteStorageAttachments(paths)
          }
        }

        await supabase.from('messages').delete().in('chat_id', ids)
      }
    } catch (e) {
      console.warn('Error during unpinned messages/attachments cleanup:', e)
    }

    const { error } = await supabase
      .from('chats')
      .delete()
      .eq('user_id', userId)
      .eq('is_pinned', false)

    if (error) {
      console.error('Error deleting unpinned chats:', error)
      return false
    }
    return true
  },

  // --- Message Operations ---
  async getMessages(chatId: string): Promise<Message[]> {
    const { data, error } = await supabase
      .from('messages')
      .select('*')
      .eq('chat_id', chatId)
      .order('created_at', { ascending: true })

    if (error) {
      console.error('Error fetching messages:', error)
      return []
    }
    return data || []
  },

  async createMessage(msg: Omit<Message, 'id' | 'created_at'>): Promise<Message | null> {
    const fallbackMsg: Message = {
      id: `local_msg_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`,
      chat_id: msg.chat_id,
      user_id: msg.user_id,
      role: msg.role,
      content: msg.content,
      reasoning: msg.reasoning || null,
      model_name: msg.model_name || null,
      attachments: msg.attachments || [],
      created_at: new Date().toISOString(),
    }

    if (msg.chat_id.startsWith('local_')) {
      return fallbackMsg
    }

    try {
      const { data, error } = await supabase
        .from('messages')
        .insert({
          chat_id: msg.chat_id,
          user_id: msg.user_id,
          role: msg.role,
          content: msg.content,
          reasoning: msg.reasoning || null,
          model_name: msg.model_name || null,
          attachments: msg.attachments || [],
        })
        .select()
        .single()

      if (error) {
        console.error('Error creating message in Supabase, using fallback:', error)
        return fallbackMsg
      }

      // Touch chat updated_at
      try {
        await supabase
          .from('chats')
          .update({ updated_at: new Date().toISOString() })
          .eq('id', msg.chat_id)
      } catch {}

      return data
    } catch (e) {
      console.warn('Exception creating message, using fallback:', e)
      return fallbackMsg
    }
  },

  async createMessagesBatch(msgs: Array<Omit<Message, 'id' | 'created_at'>>): Promise<Message[]> {
    if (msgs.length === 0) return []
    const payload = msgs.map((m) => ({
      chat_id: m.chat_id,
      user_id: m.user_id,
      role: m.role,
      content: m.content,
      reasoning: m.reasoning || null,
      model_name: m.model_name || null,
      attachments: m.attachments || [],
    }))

    const { data, error } = await supabase.from('messages').insert(payload).select()
    if (error) {
      console.error('Error batch inserting messages:', error)
      return []
    }
    return data || []
  },

  async deleteMessage(messageId: string): Promise<boolean> {
    try {
      const { data: msg } = await supabase
        .from('messages')
        .select('attachments')
        .eq('id', messageId)
        .maybeSingle()

      if (msg) {
        const paths = extractStoragePathsFromMessages([msg])
        if (paths.length > 0) {
          await deleteStorageAttachments(paths)
        }
      }
    } catch (_e) {}

    const { error } = await supabase.from('messages').delete().eq('id', messageId)
    if (error) {
      console.error('Error deleting message:', error)
      return false
    }
    return true
  },

  async deleteMessagesFrom(chatId: string, createdAt: string): Promise<boolean> {
    try {
      const { data: msgs } = await supabase
        .from('messages')
        .select('attachments')
        .eq('chat_id', chatId)
        .gte('created_at', createdAt)

      if (msgs && msgs.length > 0) {
        const paths = extractStoragePathsFromMessages(msgs)
        if (paths.length > 0) {
          await deleteStorageAttachments(paths)
        }
      }
    } catch (_e) {}

    const { error } = await supabase
      .from('messages')
      .delete()
      .eq('chat_id', chatId)
      .gte('created_at', createdAt)

    if (error) {
      console.error('Error deleting messages from timestamp:', error)
      return false
    }
    return true
  },

  async deleteMessagesAfter(chatId: string, createdAt: string): Promise<boolean> {
    try {
      const { data: msgs } = await supabase
        .from('messages')
        .select('attachments')
        .eq('chat_id', chatId)
        .gt('created_at', createdAt)

      if (msgs && msgs.length > 0) {
        const paths = extractStoragePathsFromMessages(msgs)
        if (paths.length > 0) {
          await deleteStorageAttachments(paths)
        }
      }
    } catch (_e) {}

    const { error } = await supabase
      .from('messages')
      .delete()
      .eq('chat_id', chatId)
      .gt('created_at', createdAt)

    if (error) {
      console.error('Error deleting messages after timestamp:', error)
      return false
    }
    return true
  },

  // --- Real-time Subscriptions ---
  subscribeToProjects(_userId: string, onUpdate: () => void) {
    const channel = supabase
      .channel('public-projects-changes')
      .on(
        'postgres_changes',
        { event: '*', schema: 'public', table: 'projects' },
        () => onUpdate()
      )
      .subscribe()

    return () => {
      supabase.removeChannel(channel)
    }
  },

  subscribeToChats(_userId: string, onUpdate: () => void) {
    const channel = supabase
      .channel('public-chats-changes')
      .on(
        'postgres_changes',
        { event: '*', schema: 'public', table: 'chats' },
        () => onUpdate()
      )
      .subscribe()

    return () => {
      supabase.removeChannel(channel)
    }
  },

  subscribeToMessages(
    chatId: string,
    onInsertOrUpdate: (msg: Message) => void,
    onDelete?: (deletedId: string) => void
  ) {
    const channel = supabase
      .channel(`messages-${chatId}`)
      .on(
        'postgres_changes',
        { event: '*', schema: 'public', table: 'messages', filter: `chat_id=eq.${chatId}` },
        (payload) => {
          if (payload.eventType === 'DELETE' && payload.old && (payload.old as any).id) {
            onDelete?.((payload.old as any).id)
          } else if (payload.new && (payload.new as any).id) {
            onInsertOrUpdate(payload.new as Message)
          }
        }
      )
      .subscribe()

    return () => {
      supabase.removeChannel(channel)
    }
  },

  // --- Storage Operations ---
  async uploadAttachment(
    userId: string,
    fileOrBlob: Blob | File,
    fileName: string
  ): Promise<string | null> {
    try {
      const cleanName = fileName.replace(/[^a-zA-Z0-9._-]/g, '_')
      const path = `${userId}/${Date.now()}_${cleanName}`
      const contentType = fileOrBlob.type || 'application/octet-stream'

      const { data, error } = await supabase.storage
        .from('attachments')
        .upload(path, fileOrBlob, {
          cacheControl: '3600',
          contentType,
          upsert: false,
        })

      if (error) {
        console.warn('Supabase storage upload error:', error.message)
        return null
      }

      const { data: publicData } = supabase.storage
        .from('attachments')
        .getPublicUrl(data.path)

      return publicData.publicUrl
    } catch (err) {
      console.warn('Supabase storage upload exception:', err)
      return null
    }
  },

  async cleanOrphanedStorageAttachments(userId: string): Promise<string[]> {
    try {
      const { data: files, error: listError } = await supabase.storage
        .from('attachments')
        .list(userId, { limit: 1000 })

      if (listError || !files || files.length === 0) {
        return []
      }

      const { data: msgs, error: msgsError } = await supabase
        .from('messages')
        .select('attachments')
        .eq('user_id', userId)

      if (msgsError) {
        console.warn('Error querying messages for orphaned cleanup:', msgsError)
        return []
      }

      // Also query active project files so knowledge base files are NEVER deleted as orphans!
      const { data: pFiles, error: pFilesError } = await supabase
        .from('project_files')
        .select('storage_path')
        .eq('user_id', userId)

      if (pFilesError) {
        console.warn('Error querying project files for orphaned cleanup:', pFilesError)
      }

      const activePaths = new Set(extractStoragePathsFromMessages(msgs || []))
      if (pFiles && pFiles.length > 0) {
        for (const pf of pFiles) {
          const path = extractStoragePath(pf.storage_path)
          if (path) {
            activePaths.add(path)
          }
        }
      }

      const orphanedPaths: string[] = []
      for (const file of files) {
        if (!file.name || file.id === null) continue
        const fullPath = `${userId}/${file.name}`
        if (!activePaths.has(fullPath)) {
          orphanedPaths.push(fullPath)
        }
      }

      if (orphanedPaths.length > 0) {
        console.log(`[WYRD Storage] Removing ${orphanedPaths.length} orphaned attachment(s):`, orphanedPaths)
        await deleteStorageAttachments(orphanedPaths)
        return orphanedPaths
      }
      return []
    } catch (e) {
      console.warn('Exception during orphan cleanup:', e)
      return []
    }
  },
}

export function extractStoragePath(uriOrPath: string): string | null {
  if (!uriOrPath) return null
  const marker = '/attachments/'
  const idx = uriOrPath.indexOf(marker)
  if (idx !== -1) {
    const sub = uriOrPath.substring(idx + marker.length).split('?')[0]
    return decodeURIComponent(sub)
  }
  if (
    uriOrPath.includes('/') &&
    !uriOrPath.startsWith('http') &&
    !uriOrPath.startsWith('content:') &&
    !uriOrPath.startsWith('file:') &&
    !uriOrPath.startsWith('data:')
  ) {
    return uriOrPath
  }
  return null
}

export function extractStoragePathsFromMessages(messages: Array<{ attachments?: any }>): string[] {
  const paths: string[] = []
  for (const msg of messages) {
    if (!msg || !msg.attachments) continue
    const attList = Array.isArray(msg.attachments) ? msg.attachments : []
    for (const att of attList) {
      if (att && att.uri) {
        const path = extractStoragePath(att.uri)
        if (path) paths.push(path)
      }
    }
  }
  return Array.from(new Set(paths))
}

export async function deleteStorageAttachments(paths: string[]): Promise<boolean> {
  if (!paths || paths.length === 0) return true
  try {
    const { error } = await supabase.storage.from('attachments').remove(paths)
    if (error) {
      console.warn('Failed to delete attachments from storage:', error.message)
      return false
    }
    return true
  } catch (err) {
    console.warn('Exception deleting attachments from storage:', err)
    return false
  }
}

export function dataUriToBlob(dataUri: string): Blob {
  const splitIndex = dataUri.indexOf(',')
  const byteString = atob(splitIndex !== -1 ? dataUri.substring(splitIndex + 1) : dataUri)
  const mimeString = splitIndex !== -1 ? dataUri.split(',')[0].split(':')[1].split(';')[0] : 'image/jpeg'
  const ab = new ArrayBuffer(byteString.length)
  const ia = new Uint8Array(ab)
  for (let i = 0; i < byteString.length; i++) {
    ia[i] = byteString.charCodeAt(i)
  }
  return new Blob([ab], { type: mimeString })
}

