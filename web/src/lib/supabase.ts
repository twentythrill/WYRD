import { createClient } from '@supabase/supabase-js'

const supabaseUrl = 
  import.meta.env.VITE_SUPABASE_URL || 
  import.meta.env.NEXT_PUBLIC_SUPABASE_URL || 
  'https://ncxbhueublthjfawkkvy.supabase.co'

const supabaseAnonKey = 
  import.meta.env.VITE_SUPABASE_ANON_KEY || 
  import.meta.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY || 
  'sb_publishable_lZa8oy7FwdDOKvv4Jrj26A_skhlS325'

export const supabase = createClient(supabaseUrl, supabaseAnonKey, {
  auth: {
    persistSession: true,
    autoRefreshToken: true,
  },
  realtime: {
    params: {
      eventsPerSecond: 10,
    },
  },
})

export interface Profile {
  id: string
  username: string
  system_prompt: string
  active_model?: string
  reasoning_effort?: string
  created_at?: string
  updated_at?: string
}

export interface Chat {
  id: string
  user_id: string
  title: string
  is_pinned: boolean
  created_at: string
  updated_at: string
}

export interface MessageAttachment {
  uri: string
  type: string
  name: string
  size?: number
  textContent?: string
  file?: File
}

export interface Message {
  id: string
  chat_id: string
  user_id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  reasoning?: string | null
  model_name?: string | null
  attachments?: MessageAttachment[]
  created_at: string
}
