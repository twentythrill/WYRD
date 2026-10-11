import {
  supabase,
  type Chat,
  type Message,
  type Profile,
  type Project,
  type ProjectFile,
} from '@/lib/supabase'

const LOCAL_PROJECTS_KEY = 'wyrd_local_projects'
const LOCAL_PROJECT_FILES_KEY = 'wyrd_local_project_files'
const LOCAL_CHAT_PROJECT_MAP_KEY = 'wyrd_chat_project_map'

export function getChatProjectMap(): Record<string, string> {
  try {
    const raw = localStorage.getItem(LOCAL_CHAT_PROJECT_MAP_KEY)
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}

export function setChatProjectAssociation(chatId: string, projectId: string | null): void {
  try {
    const map = getChatProjectMap()
    if (projectId) {
      map[chatId] = projectId
    } else {
      delete map[chatId]
    }
    localStorage.setItem(LOCAL_CHAT_PROJECT_MAP_KEY, JSON.stringify(map))
  } catch (e) {
    console.warn('Failed to update chat project map:', e)
  }
}

export function reassignChatProjectAssociation(oldProjectId: string, newProjectId: string): void {
  try {
    const map = getChatProjectMap()
    let changed = false
    for (const [chatId, pId] of Object.entries(map)) {
      if (pId === oldProjectId) {
        map[chatId] = newProjectId
        changed = true
      }
    }
    if (changed) {
      localStorage.setItem(LOCAL_CHAT_PROJECT_MAP_KEY, JSON.stringify(map))
    }
  } catch (e) {
    console.warn('Failed to reassign chat project map:', e)
  }
}

function getLocalProjects(userId?: string): Project[] {
  try {
    const raw = localStorage.getItem(LOCAL_PROJECTS_KEY)
    const list: Project[] = raw ? JSON.parse(raw) : []
    return userId ? list.filter((p) => p.user_id === userId || !p.user_id) : list
  } catch {
    return []
  }
}

function saveLocalProject(project: Project): void {
  try {
    const raw = localStorage.getItem(LOCAL_PROJECTS_KEY)
    const list: Project[] = raw ? JSON.parse(raw) : []
    const index = list.findIndex((p) => p.id === project.id)
    if (index >= 0) {
      list[index] = project
    } else {
      list.unshift(project)
    }
    localStorage.setItem(LOCAL_PROJECTS_KEY, JSON.stringify(list))
  } catch (e) {
    console.warn('Failed to save local project:', e)
  }
}

function updateLocalProject(projectId: string, updates: Partial<Project>): Project | null {
  try {
    const raw = localStorage.getItem(LOCAL_PROJECTS_KEY)
    const list: Project[] = raw ? JSON.parse(raw) : []
    const index = list.findIndex((p) => p.id === projectId)
    if (index >= 0) {
      const updated = { ...list[index], ...updates, updated_at: new Date().toISOString() }
      list[index] = updated
      localStorage.setItem(LOCAL_PROJECTS_KEY, JSON.stringify(list))
      return updated
    }
    return null
  } catch {
    return null
  }
}

function deleteLocalProject(projectId: string): void {
  try {
    const raw = localStorage.getItem(LOCAL_PROJECTS_KEY)
    const list: Project[] = raw ? JSON.parse(raw) : []
    const filtered = list.filter((p) => p.id !== projectId)
    localStorage.setItem(LOCAL_PROJECTS_KEY, JSON.stringify(filtered))
  } catch (e) {
    console.warn('Failed to delete local project:', e)
  }
}

function getLocalProjectFiles(projectId: string): ProjectFile[] {
  try {
    const raw = localStorage.getItem(LOCAL_PROJECT_FILES_KEY)
    const all: ProjectFile[] = raw ? JSON.parse(raw) : []
    return all.filter((f) => f.project_id === projectId)
  } catch {
    return []
  }
}

function saveLocalProjectFile(file: ProjectFile): void {
  try {
    const raw = localStorage.getItem(LOCAL_PROJECT_FILES_KEY)
    const all: ProjectFile[] = raw ? JSON.parse(raw) : []
    const index = all.findIndex((f) => f.id === file.id)
    if (index >= 0) {
      all[index] = file
    } else {
      all.push(file)
    }
    localStorage.setItem(LOCAL_PROJECT_FILES_KEY, JSON.stringify(all))
  } catch (e) {
    console.warn('Failed to save local project file:', e)
  }
}

function deleteLocalProjectFile(fileId: string): void {
  try {
    const raw = localStorage.getItem(LOCAL_PROJECT_FILES_KEY)
    const all: ProjectFile[] = raw ? JSON.parse(raw) : []
    const filtered = all.filter((f) => f.id !== fileId)
    localStorage.setItem(LOCAL_PROJECT_FILES_KEY, JSON.stringify(filtered))
  } catch (e) {
    console.warn('Failed to delete local project file:', e)
  }
}

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

  // --- Project Operations with Resilient Local Fallback ---
  async getProjects(userId: string): Promise<Project[]> {
    const local = getLocalProjects(userId)
    try {
      const { data, error } = await supabase
        .from('projects')
        .select('*')
        .eq('user_id', userId)
        .order('is_pinned', { ascending: false })
        .order('updated_at', { ascending: false })
        .order('id', { ascending: false })

      if (error) {
        console.warn('Supabase projects query returned error (using local storage fallback):', error.message)
        return local
      }

      if (data) {
        // Prune any local projects that were previously synced with UUIDs but deleted remotely
        const localProjectsOnly = local.filter((p) => p.id.startsWith('local_'))
        if (localProjectsOnly.length !== local.length) {
          localStorage.setItem(LOCAL_PROJECTS_KEY, JSON.stringify(localProjectsOnly))
        }

        // Auto-migrate any local_ projects to Supabase now that connection/schema is active
        for (const lp of localProjectsOnly) {
          try {
            const { data: created } = await supabase
              .from('projects')
              .insert({
                user_id: userId,
                name: lp.name,
                system_prompt: lp.system_prompt,
                description: lp.description,
                is_pinned: lp.is_pinned,
              })
              .select()
              .single()
            if (created) {
              deleteLocalProject(lp.id)
              data.unshift(created)
              reassignChatProjectAssociation(lp.id, created.id)
              // Update any chats that used this local project id
              try {
                await supabase
                  .from('chats')
                  .update({ project_id: created.id })
                  .eq('project_id', lp.id)
              } catch {}
            }
          } catch {
            // Keep local project until next sync opportunity
          }
        }

        const remainingLocal = getLocalProjects(userId).filter((p) => p.id.startsWith('local_'))
        const remoteIds = new Set(data.map((p) => p.id))
        const unsynced = remainingLocal.filter((p) => !remoteIds.has(p.id))
        return [...unsynced, ...data]
      }
      return local
    } catch (e) {
      console.warn('Network error fetching projects, using local storage fallback:', e)
      return local
    }
  },

  async createProject(
    userId: string,
    name: string,
    systemPrompt: string = '',
    description?: string
  ): Promise<Project | null> {
    const localProj: Project = {
      id: `local_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`,
      user_id: userId,
      name,
      description: description || null,
      system_prompt: systemPrompt,
      is_pinned: false,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    }

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
        console.warn('Supabase projects table unavailable or error, saving locally:', error.message)
        saveLocalProject(localProj)
        return localProj
      }
      return data
    } catch (err) {
      console.warn('Network exception while saving project, saving locally:', err)
      saveLocalProject(localProj)
      return localProj
    }
  },

  async updateProject(projectId: string, updates: Partial<Project>): Promise<Project | null> {
    if (projectId.startsWith('local_')) {
      return updateLocalProject(projectId, updates)
    }

    try {
      const { data, error } = await supabase
        .from('projects')
        .update({ ...updates, updated_at: new Date().toISOString() })
        .eq('id', projectId)
        .select()
        .single()

      if (error) {
        console.warn('Supabase update failed, saving locally:', error.message)
        return updateLocalProject(projectId, updates)
      }
      return data
    } catch (err) {
      console.warn('Network exception while updating project:', err)
      return updateLocalProject(projectId, updates)
    }
  },

  async deleteProject(projectId: string): Promise<boolean> {
    deleteLocalProject(projectId)
    if (projectId.startsWith('local_')) {
      return true
    }
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

      await supabase.from('chats').update({ project_id: null }).eq('project_id', projectId)
      await supabase.from('projects').delete().eq('id', projectId)
      return true
    } catch (e) {
      console.warn('Error during project deletion:', e)
      return true
    }
  },

  // --- Project Files Operations with Resilient Local Fallback ---
  async getProjectFiles(projectId: string): Promise<ProjectFile[]> {
    const local = getLocalProjectFiles(projectId)
    if (projectId.startsWith('local_')) {
      return local
    }
    try {
      const { data, error } = await supabase
        .from('project_files')
        .select('*')
        .eq('project_id', projectId)
        .order('created_at', { ascending: true })

      if (error) {
        return local
      }
      if (data) {
        const remoteIds = new Set(data.map((f) => f.id))
        const unsynced = local.filter((f) => !remoteIds.has(f.id))
        return [...unsynced, ...data]
      }
      return local
    } catch {
      return local
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
    const localFile: ProjectFile = {
      id: `local_f_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`,
      project_id: projectId,
      user_id: userId,
      name: fileInfo.name,
      size: fileInfo.size,
      mime_type: fileInfo.mimeType,
      storage_path: fileInfo.storagePath,
      text_content: fileInfo.textContent || null,
      created_at: new Date().toISOString(),
    }

    if (projectId.startsWith('local_')) {
      saveLocalProjectFile(localFile)
      return localFile
    }

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
        console.warn('Failed to insert project file to Supabase, saving locally:', error.message)
        saveLocalProjectFile(localFile)
        return localFile
      }
      return data
    } catch {
      saveLocalProjectFile(localFile)
      return localFile
    }
  },

  async deleteProjectFile(fileId: string, storagePath?: string): Promise<boolean> {
    deleteLocalProjectFile(fileId)
    if (storagePath) {
      try {
        await deleteStorageAttachments([storagePath])
      } catch (e) {
        console.warn('Error deleting project file from storage:', e)
      }
    }
    if (fileId.startsWith('local_')) {
      return true
    }
    try {
      await supabase.from('project_files').delete().eq('id', fileId)
      return true
    } catch {
      return true
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

    const localMap = getChatProjectMap()
    if (error) {
      console.error('Error fetching chats:', error)
      return []
    }
    const list = data || []
    return list.map((chat) => ({
      ...chat,
      project_id: chat.project_id || localMap[chat.id] || null,
    }))
  },

  async createChat(
    userId: string,
    title: string = 'New Chat',
    projectId?: string | null
  ): Promise<Chat | null> {
    const isValidUuid = projectId && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(projectId)
    const remoteProjectId = isValidUuid ? projectId : null

    const localChatId = `local_chat_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`
    const localChat: Chat = {
      id: localChatId,
      user_id: userId,
      title,
      is_pinned: false,
      project_id: projectId || null,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    }

    if (projectId) {
      setChatProjectAssociation(localChatId, projectId)
    }

    try {
      const insertPayload: Record<string, any> = {
        user_id: userId,
        title,
        is_pinned: false,
      }
      if (remoteProjectId) {
        insertPayload.project_id = remoteProjectId
      }

      let { data, error } = await supabase
        .from('chats')
        .insert(insertPayload)
        .select()
        .single()

      // If inserting with project_id failed (e.g. column doesn't exist yet on chats table), retry without project_id
      if (error && remoteProjectId) {
        console.warn('createChat with project_id failed, retrying without project_id:', error.message)
        const retry = await supabase
          .from('chats')
          .insert({
            user_id: userId,
            title,
            is_pinned: false,
          })
          .select()
          .single()
        data = retry.data
        error = retry.error
      }

      if (error) {
        console.error('Error creating chat in Supabase, using local chat fallback:', error)
        return localChat
      }

      const assignedProjectId = projectId || data?.project_id || null
      if (data?.id && assignedProjectId) {
        setChatProjectAssociation(data.id, assignedProjectId)
      }

      return {
        ...data,
        project_id: assignedProjectId,
      }
    } catch (e) {
      console.warn('Exception creating chat, using local chat fallback:', e)
      return localChat
    }
  },

  async updateChat(chatId: string, updates: Partial<Chat>): Promise<Chat | null> {
    if (updates.project_id !== undefined) {
      setChatProjectAssociation(chatId, updates.project_id)
    }
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
    setChatProjectAssociation(chatId, null)
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

      const activePaths = new Set(extractStoragePathsFromMessages(msgs || []))
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

