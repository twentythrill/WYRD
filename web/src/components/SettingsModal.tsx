import React, { useState, useEffect } from 'react'
import { ArrowLeft, Check, Trash2, LogOut } from 'lucide-react'
import { type Profile } from '@/lib/supabase'

interface SettingsModalProps {
  isOpen: boolean
  profile: Profile | null
  userEmail?: string
  appTheme?: 'system' | 'dark' | 'light'
  onClose: () => void
  onSaveProfile: (updates: Partial<Profile>) => Promise<void>
  onDeleteUnpinnedChats: () => Promise<void>
  onSignOut?: () => void
  onThemeChange?: (theme: 'system' | 'dark' | 'light') => void
}

export function SettingsModal({
  isOpen,
  profile,
  userEmail,
  appTheme = 'system',
  onClose,
  onSaveProfile,
  onDeleteUnpinnedChats,
  onSignOut,
  onThemeChange,
}: SettingsModalProps) {
  const [username, setUsername] = useState(profile?.username || 'Human')
  const [systemPrompt, setSystemPrompt] = useState(profile?.system_prompt || '')
  const [apiKey, setApiKey] = useState(localStorage.getItem('wyrd_api_key') || '')
  const [openAiKey, setOpenAiKey] = useState(localStorage.getItem('wyrd_openai_api_key') || '')
  const [exaKey, setExaKey] = useState(localStorage.getItem('wyrd_exa_api_key') || '')
  const [tavilyKey, setTavilyKey] = useState(localStorage.getItem('wyrd_tavily_api_key') || '')
  const [baseUrl, setBaseUrl] = useState(() => {
    const saved = localStorage.getItem('wyrd_base_url') || ''
    return saved === 'https://api.siliconflow.cn/v1' ? '' : saved
  })

  const [isSaving, setIsSaving] = useState(false)
  const [savedSuccess, setSavedSuccess] = useState(false)
  const [showClearConfirm, setShowClearConfirm] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)

  useEffect(() => {
    if (profile) {
      setUsername(profile.username || 'Human')
      setSystemPrompt(profile.system_prompt || '')
    }
  }, [profile])

  if (!isOpen) return null

  const handleSave = async () => {
    setIsSaving(true)
    try {
      localStorage.setItem('wyrd_api_key', apiKey.trim())
      localStorage.setItem('wyrd_openai_api_key', openAiKey.trim())
      localStorage.setItem('wyrd_exa_api_key', exaKey.trim())
      localStorage.setItem('wyrd_tavily_api_key', tavilyKey.trim())
      localStorage.setItem('wyrd_base_url', baseUrl.trim())

      await onSaveProfile({
        username: username.trim() || 'Human',
        system_prompt: systemPrompt,
      })

      setSavedSuccess(true)
      setTimeout(() => {
        setSavedSuccess(false)
        onClose()
      }, 600)
    } catch (err) {
      console.error(err)
    } finally {
      setIsSaving(false)
    }
  }

  const handleDeleteUnpinned = async () => {
    setIsDeleting(true)
    try {
      await onDeleteUnpinnedChats()
      setShowClearConfirm(false)
    } catch (err) {
      console.error(err)
    } finally {
      setIsDeleting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
      <div className="w-full max-w-lg bg-[#0A0A0A] border border-[#262626] rounded-2xl shadow-2xl flex flex-col max-h-[90vh] overflow-hidden select-none">
        {/* Header matching SettingsScreen.kt TopAppBar */}
        <div className="h-14 px-4 border-b border-[#262626] flex items-center justify-between bg-[#0A0A0A]">
          <div className="flex items-center gap-3">
            <button
              onClick={onClose}
              className="p-1.5 -ml-1 text-[#CECDC4] hover:text-white rounded-lg transition-colors"
            >
              <ArrowLeft className="w-6 h-6 stroke-[2]" />
            </button>
            <h2 className="text-lg font-bold text-[#CECDC4] font-sans">
              Settings
            </h2>
          </div>

          <button
            onClick={handleSave}
            disabled={isSaving}
            className="px-4 py-1.5 rounded-full bg-[#CECDC4] text-black font-semibold text-xs hover:bg-white transition-all flex items-center gap-1.5"
          >
            {savedSuccess ? (
              <>
                <Check className="w-3.5 h-3.5 stroke-[3]" />
                <span>Saved</span>
              </>
            ) : (
              <span>{isSaving ? 'Saving...' : 'Save'}</span>
            )}
          </button>
        </div>

        {/* Content */}
        <div className="p-6 overflow-y-auto space-y-6 text-sm">
          {/* Identity Section */}
          <div className="space-y-4">
            <div className="text-[11px] font-sans font-bold tracking-[1.2px] text-[#636366] uppercase">
              PROFILE
            </div>

            <div>
              <label className="block text-xs text-[#8E8E93] mb-1.5 font-sans">
                Username
              </label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="Human"
                className="w-full px-3.5 py-2.5 bg-[#141414] border border-[#262626] rounded-xl text-[#CECDC4] text-sm focus:outline-none focus:border-[#CECDC4] transition-colors font-sans"
              />
            </div>

            <div>
              <label className="block text-xs text-[#8E8E93] mb-1.5 font-sans">
                System Prompt
              </label>
              <textarea
                rows={4}
                value={systemPrompt}
                onChange={(e) => setSystemPrompt(e.target.value)}
                placeholder="Set instructions for the model..."
                className="w-full px-3.5 py-2.5 bg-[#141414] border border-[#262626] rounded-xl text-[#CECDC4] text-sm focus:outline-none focus:border-[#CECDC4] transition-colors resize-none font-sans"
              />
            </div>
          </div>

          {/* Appearance Section matching Android SettingsScreen.kt */}
          <div className="space-y-3 pt-2 border-t border-[#262626]">
            <div className="text-[11px] font-sans font-bold tracking-[1.2px] text-[#636366] uppercase">
              APPEARANCE
            </div>

            <div className="p-4 rounded-xl bg-[#141414] border border-[#262626] flex items-center justify-between">
              <div>
                <div className="text-sm font-semibold text-[#CECDC4] font-sans">
                  App Theme
                </div>
                <div className="text-xs text-[#8E8E93] font-sans mt-0.5">
                  {appTheme === 'system'
                    ? 'System (Follows device settings)'
                    : appTheme === 'light'
                    ? 'Light (Warm brutalist parchment)'
                    : 'Dark (Manual AMOLED black)'}
                </div>
              </div>

              <select
                value={appTheme}
                onChange={(e) => onThemeChange?.(e.target.value as any)}
                className="px-3 py-1.5 rounded-lg bg-[#1A1A1A] border border-[#262626] text-xs text-[#CECDC4] font-sans focus:outline-none focus:border-[#CECDC4] cursor-pointer"
              >
                <option value="system">System</option>
                <option value="dark">Dark</option>
                <option value="light">Light</option>
              </select>
            </div>
          </div>

          {/* Cloud Sync / Account Section matching SettingsScreen.kt */}
          <div className="space-y-3 pt-2 border-t border-[#262626]">
            <div className="text-[11px] font-sans font-bold tracking-[1.2px] text-[#636366] uppercase">
              CLOUD SYNC
            </div>

            <div className="p-4 rounded-xl bg-[#141414] border border-[#262626] flex items-center justify-between">
              <div>
                <div className="text-sm font-bold text-[#CECDC4] font-sans">
                  WYRD Cloud (Supabase)
                </div>
                <div className="text-xs text-[#30D158] font-sans mt-0.5">
                  {userEmail ? `Connected as ${userEmail}` : 'Connected'}
                </div>
              </div>

              {onSignOut && (
                <button
                  onClick={() => {
                    onClose()
                    onSignOut()
                  }}
                  className="px-3.5 py-1.5 rounded-lg bg-[#1A1A1A] border border-[#262626] text-[#8E8E93] hover:text-[#FF453A] hover:bg-[#262626] text-xs font-medium transition-colors flex items-center gap-1.5"
                >
                  <LogOut className="w-3.5 h-3.5" />
                  <span>Log Out</span>
                </button>
              )}
            </div>
          </div>

          {/* API Provider Config */}
          <div className="space-y-4 pt-2 border-t border-[#262626]">
            <div className="text-[11px] font-sans font-bold tracking-[1.2px] text-[#636366] uppercase">
              API CONFIGURATION
            </div>

            <div>
              <label className="block text-xs text-[#8E8E93] mb-1.5 font-sans">
                Xiaomi MiMo / LLM API Key
              </label>
              <input
                type="password"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder="sk-..."
                className="w-full px-3.5 py-2.5 bg-[#141414] border border-[#262626] rounded-xl text-[#CECDC4] text-sm focus:outline-none focus:border-[#CECDC4] transition-colors font-mono"
              />
            </div>

            <div>
              <label className="block text-xs text-[#8E8E93] mb-1.5 font-sans">
                OpenAI API Key (Voice Dictation / STT)
              </label>
              <input
                type="password"
                value={openAiKey}
                onChange={(e) => setOpenAiKey(e.target.value)}
                placeholder="sk-proj-..."
                className="w-full px-3.5 py-2.5 bg-[#141414] border border-[#262626] rounded-xl text-[#CECDC4] text-sm focus:outline-none focus:border-[#CECDC4] transition-colors font-mono"
              />
              <p className="text-[11px] text-[#636366] mt-1 font-sans">
                Used for microphone voice transcription via OpenAI Whisper STT.
              </p>
            </div>

            <div>
              <label className="block text-xs text-[#8E8E93] mb-1.5 font-sans">
                Tavily Search API Key (Live Web)
              </label>
              <input
                type="password"
                value={tavilyKey}
                onChange={(e) => setTavilyKey(e.target.value)}
                placeholder="tvly-..."
                className="w-full px-3.5 py-2.5 bg-[#141414] border border-[#262626] rounded-xl text-[#CECDC4] text-sm focus:outline-none focus:border-[#CECDC4] transition-colors font-mono"
              />
              <p className="text-[11px] text-[#636366] mt-1 font-sans">
                Get a free key at <a href="https://app.tavily.com" target="_blank" rel="noreferrer" className="underline hover:text-[#CECDC4]">app.tavily.com</a> (1,000 free searches/mo) for real-time live web browsing.
              </p>
            </div>

            <div>
              <label className="block text-xs text-[#8E8E93] mb-1.5 font-sans">
                Exa Search API Key (Optional)
              </label>
              <input
                type="password"
                value={exaKey}
                onChange={(e) => setExaKey(e.target.value)}
                placeholder="Paste Exa key or leave blank for public tier..."
                className="w-full px-3.5 py-2.5 bg-[#141414] border border-[#262626] rounded-xl text-[#CECDC4] text-sm focus:outline-none focus:border-[#CECDC4] transition-colors font-mono"
              />
              <p className="text-[11px] text-[#636366] mt-1 font-sans">
                Get a free key at <a href="https://dashboard.exa.ai" target="_blank" rel="noreferrer" className="underline hover:text-[#CECDC4]">dashboard.exa.ai</a> (1,000 free searches). Used for semantic and deep concept research.
              </p>
            </div>

            <div>
              <label className="block text-xs text-[#8E8E93] mb-1.5 font-sans">
                Base URL (Optional, auto-routed to Xiaomi MiMo)
              </label>
              <input
                type="text"
                value={baseUrl}
                onChange={(e) => setBaseUrl(e.target.value)}
                placeholder="Auto-routed from API key (Leave blank for official MiMo)"
                className="w-full px-3.5 py-2.5 bg-[#141414] border border-[#262626] rounded-xl text-[#CECDC4] text-xs focus:outline-none focus:border-[#CECDC4] transition-colors font-mono"
              />
              <p className="text-[11px] text-[#636366] mt-1 font-sans">
                Automatically connects to Xiaomi Token Plan (tp-*) or Pay-As-You-Go API endpoints.
              </p>
            </div>
          </div>

          {/* Danger Zone: Delete Unpinned Chats */}
          <div className="space-y-3 pt-2 border-t border-[#262626]">
            <div className="text-[11px] font-sans font-bold tracking-[1.2px] text-[#FF453A] uppercase">
              CHAT STORAGE
            </div>

            <div className="p-4 rounded-xl bg-[#141414] border border-[#262626] flex items-center justify-between">
              <div>
                <div className="text-sm font-medium text-[#CECDC4] font-sans">
                  Delete Unpinned Chats
                </div>
                <div className="text-xs text-[#636366] font-sans mt-0.5">
                  Permanently delete all chats except pinned ones
                </div>
              </div>

              {showClearConfirm ? (
                <div className="flex items-center gap-2">
                  <button
                    onClick={handleDeleteUnpinned}
                    disabled={isDeleting}
                    className="px-3 py-1.5 rounded-lg bg-[#FF453A] text-white text-xs font-medium hover:bg-red-600 transition-colors"
                  >
                    {isDeleting ? 'Deleting...' : 'Confirm'}
                  </button>
                  <button
                    onClick={() => setShowClearConfirm(false)}
                    className="px-2.5 py-1.5 rounded-lg bg-[#262626] text-[#CECDC4] text-xs transition-colors hover:text-white"
                  >
                    Cancel
                  </button>
                </div>
              ) : (
                <button
                  onClick={() => setShowClearConfirm(true)}
                  className="px-3.5 py-1.5 rounded-lg bg-[#1A1A1A] border border-[#262626] text-[#FF453A] hover:bg-[#262626] text-xs font-medium transition-colors flex items-center gap-1.5"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                  <span>Delete</span>
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
