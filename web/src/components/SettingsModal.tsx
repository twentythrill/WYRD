import { useState, useEffect } from 'react'
import { ArrowLeft, Check, Trash2, LogOut } from 'lucide-react'
import { type Profile } from '@/lib/supabase'

interface SettingsModalProps {
  isOpen: boolean
  profile: Profile | null
  userEmail?: string
  appTheme?: 'system' | 'dark' | 'light'
  isDark?: boolean
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
  isDark = true,
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
  const [tavilyEnabled, setTavilyEnabled] = useState(() => localStorage.getItem('wyrd_tavily_enabled') !== 'false')
  const [exaEnabled, setExaEnabled] = useState(() => localStorage.getItem('wyrd_exa_enabled') !== 'false')
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
      localStorage.setItem('wyrd_tavily_enabled', String(tavilyEnabled))
      localStorage.setItem('wyrd_exa_enabled', String(exaEnabled))
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

  const inputClass = `w-full px-3.5 py-2.5 rounded-xl text-sm focus:outline-none transition-colors border ${
    isDark
      ? 'bg-[#141414] border-[#262626] text-[#CECDC4] focus:border-[#CECDC4]'
      : 'bg-[#E8E7E1] border-[#CECDC4] text-[#141414] focus:border-[#141414]'
  }`

  const cardClass = `p-4 rounded-xl border flex items-center justify-between ${
    isDark ? 'bg-[#141414] border-[#262626]' : 'bg-[#E8E7E1] border-[#CECDC4]'
  }`

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
      <div className={`w-full max-w-lg border rounded-2xl shadow-2xl flex flex-col max-h-[90vh] overflow-hidden select-none ${
        isDark ? 'bg-[#0A0A0A] border-[#262626]' : 'bg-[#F0EFEA] border-[#CECDC4]'
      }`}>
        {/* Header matching SettingsScreen.kt TopAppBar */}
        <div className={`h-14 px-4 border-b flex items-center justify-between shrink-0 ${
          isDark ? 'border-[#262626] bg-[#0A0A0A]' : 'border-[#CECDC4] bg-[#F0EFEA]'
        }`}>
          <div className="flex items-center gap-3">
            <button
              onClick={onClose}
              className={`p-1.5 -ml-1 rounded-lg transition-colors cursor-pointer ${
                isDark ? 'text-[#CECDC4] hover:text-white' : 'text-[#141414] hover:text-black'
              }`}
            >
              <ArrowLeft className="w-6 h-6 stroke-[2]" />
            </button>
            <h2 className={`text-lg font-bold font-sans ${isDark ? 'text-[#CECDC4]' : 'text-[#141414]'}`}>
              Settings
            </h2>
          </div>

          <button
            onClick={handleSave}
            disabled={isSaving}
            className={`px-4 py-1.5 rounded-full font-semibold text-xs transition-all flex items-center gap-1.5 cursor-pointer ${
              savedSuccess
                ? 'bg-[#32D74B] text-black font-semibold'
                : isSaving
                ? isDark
                  ? 'bg-[#262626] text-[#636366] cursor-not-allowed border border-[#262626]'
                  : 'bg-[#CECDC4] text-[#8E8E93] cursor-not-allowed border border-[#CECDC4]'
                : isDark
                ? 'bg-[#CECDC4] text-black hover:bg-white active:scale-95'
                : 'bg-[#141414] text-[#F9F9F7] hover:bg-black active:scale-95'
            }`}
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
            <div className={`text-[11px] font-sans font-bold tracking-[1.2px] uppercase ${
              isDark ? 'text-[#636366]' : 'text-[#8E8E93]'
            }`}>
              PROFILE
            </div>

            <div>
              <label className={`block text-xs mb-1.5 font-sans ${isDark ? 'text-[#8E8E93]' : 'text-[#636366]'}`}>
                Username
              </label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="Human"
                className={inputClass}
              />
            </div>

            <div>
              <label className={`block text-xs mb-1.5 font-sans ${isDark ? 'text-[#8E8E93]' : 'text-[#636366]'}`}>
                System Prompt
              </label>
              <textarea
                rows={4}
                value={systemPrompt}
                onChange={(e) => setSystemPrompt(e.target.value)}
                placeholder="Set instructions for the model..."
                className={`${inputClass} resize-none`}
              />
            </div>
          </div>

          {/* Appearance Section matching Android SettingsScreen.kt */}
          <div className={`space-y-3 pt-2 border-t ${isDark ? 'border-[#262626]' : 'border-[#CECDC4]'}`}>
            <div className={`text-[11px] font-sans font-bold tracking-[1.2px] uppercase ${
              isDark ? 'text-[#636366]' : 'text-[#8E8E93]'
            }`}>
              APPEARANCE
            </div>

            <div className={cardClass}>
              <div>
                <div className={`text-sm font-semibold font-sans ${isDark ? 'text-[#CECDC4]' : 'text-[#141414]'}`}>
                  App Theme
                </div>
                <div className={`text-xs font-sans mt-0.5 ${isDark ? 'text-[#8E8E93]' : 'text-[#636366]'}`}>
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
                className={`px-3 py-1.5 rounded-lg border text-xs font-sans focus:outline-none cursor-pointer ${
                  isDark
                    ? 'bg-[#1A1A1A] border-[#262626] text-[#CECDC4]'
                    : 'bg-[#DFDED8] border-[#CECDC4] text-[#141414]'
                }`}
              >
                <option value="system">System</option>
                <option value="dark">Dark</option>
                <option value="light">Light</option>
              </select>
            </div>
          </div>

          {/* Cloud Sync / Account Section matching SettingsScreen.kt */}
          <div className={`space-y-3 pt-2 border-t ${isDark ? 'border-[#262626]' : 'border-[#CECDC4]'}`}>
            <div className={`text-[11px] font-sans font-bold tracking-[1.2px] uppercase ${
              isDark ? 'text-[#636366]' : 'text-[#8E8E93]'
            }`}>
              CLOUD SYNC
            </div>

            <div className={cardClass}>
              <div>
                <div className={`text-sm font-bold font-sans ${isDark ? 'text-[#CECDC4]' : 'text-[#141414]'}`}>
                  WYRD Cloud (Supabase)
                </div>
                <div className="text-xs text-[#32D74B] font-sans mt-0.5">
                  {userEmail ? `Connected as ${userEmail}` : 'Connected'}
                </div>
              </div>

              {onSignOut && (
                <button
                  onClick={() => {
                    onClose()
                    onSignOut()
                  }}
                  className={`px-3.5 py-1.5 rounded-lg border text-xs font-medium transition-colors flex items-center gap-1.5 cursor-pointer ${
                    isDark
                      ? 'bg-[#1A1A1A] border-[#262626] text-[#8E8E93] hover:text-[#FF453A] hover:bg-[#262626]'
                      : 'bg-[#DFDED8] border-[#CECDC4] text-[#636366] hover:text-[#FF453A] hover:bg-[#CECDC4]'
                  }`}
                >
                  <LogOut className="w-3.5 h-3.5" />
                  <span>Log Out</span>
                </button>
              )}
            </div>
          </div>

          {/* API Provider Config */}
          <div className={`space-y-4 pt-2 border-t ${isDark ? 'border-[#262626]' : 'border-[#CECDC4]'}`}>
            <div className={`text-[11px] font-sans font-bold tracking-[1.2px] uppercase ${
              isDark ? 'text-[#636366]' : 'text-[#8E8E93]'
            }`}>
              API CONFIGURATION
            </div>

            <div>
              <label className={`block text-xs mb-1.5 font-sans ${isDark ? 'text-[#8E8E93]' : 'text-[#636366]'}`}>
                Xiaomi MiMo / LLM API Key
              </label>
              <input
                type="password"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder="sk-..."
                className={`${inputClass} font-mono`}
              />
            </div>

            <div>
              <label className={`block text-xs mb-1.5 font-sans ${isDark ? 'text-[#8E8E93]' : 'text-[#636366]'}`}>
                OpenAI API Key (Voice Dictation / STT)
              </label>
              <input
                type="password"
                value={openAiKey}
                onChange={(e) => setOpenAiKey(e.target.value)}
                placeholder="sk-proj-..."
                className={`${inputClass} font-mono`}
              />
              <p className={`text-[11px] mt-1 font-sans ${isDark ? 'text-[#636366]' : 'text-[#8E8E93]'}`}>
                Used for microphone voice transcription via OpenAI gpt-4o-mini-transcribe.
              </p>
            </div>

            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className={`text-xs font-sans ${isDark ? 'text-[#8E8E93]' : 'text-[#636366]'}`}>
                  Tavily Search API Key (Live Web)
                </label>
                <button
                  type="button"
                  role="switch"
                  aria-checked={tavilyEnabled}
                  onClick={() => setTavilyEnabled(!tavilyEnabled)}
                  className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                    tavilyEnabled
                      ? isDark ? 'bg-[#CECDC4]' : 'bg-[#141414]'
                      : isDark ? 'bg-[#262626]' : 'bg-[#CECDC4]'
                  }`}
                >
                  <span
                    className={`pointer-events-none inline-block h-4 w-4 transform rounded-full shadow ring-0 transition duration-200 ease-in-out ${
                      tavilyEnabled
                        ? `translate-x-4 ${isDark ? 'bg-[#141414]' : 'bg-[#F9F9F7]'}`
                        : `translate-x-0 ${isDark ? 'bg-[#8E8E93]' : 'bg-white'}`
                    }`}
                  />
                </button>
              </div>
              <input
                type="password"
                value={tavilyKey}
                onChange={(e) => setTavilyKey(e.target.value)}
                placeholder="tvly-..."
                disabled={!tavilyEnabled}
                className={`${inputClass} font-mono ${!tavilyEnabled ? 'opacity-50' : ''}`}
              />
              <p className={`text-[11px] mt-1 font-sans ${isDark ? 'text-[#636366]' : 'text-[#8E8E93]'}`}>
                Get a free key at <a href="https://app.tavily.com" target="_blank" rel="noreferrer" className="underline hover:opacity-80">app.tavily.com</a> (1,000 free searches/mo) for real-time live web browsing.
              </p>
            </div>

            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label className={`text-xs font-sans ${isDark ? 'text-[#8E8E93]' : 'text-[#636366]'}`}>
                  Exa Search API Key (Optional)
                </label>
                <button
                  type="button"
                  role="switch"
                  aria-checked={exaEnabled}
                  onClick={() => setExaEnabled(!exaEnabled)}
                  className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                    exaEnabled
                      ? isDark ? 'bg-[#CECDC4]' : 'bg-[#141414]'
                      : isDark ? 'bg-[#262626]' : 'bg-[#CECDC4]'
                  }`}
                >
                  <span
                    className={`pointer-events-none inline-block h-4 w-4 transform rounded-full shadow ring-0 transition duration-200 ease-in-out ${
                      exaEnabled
                        ? `translate-x-4 ${isDark ? 'bg-[#141414]' : 'bg-[#F9F9F7]'}`
                        : `translate-x-0 ${isDark ? 'bg-[#8E8E93]' : 'bg-white'}`
                    }`}
                  />
                </button>
              </div>
              <input
                type="password"
                value={exaKey}
                onChange={(e) => setExaKey(e.target.value)}
                placeholder="Paste Exa key or leave blank for public tier..."
                disabled={!exaEnabled}
                className={`${inputClass} font-mono ${!exaEnabled ? 'opacity-50' : ''}`}
              />
              <p className={`text-[11px] mt-1 font-sans ${isDark ? 'text-[#636366]' : 'text-[#8E8E93]'}`}>
                Get a free key at <a href="https://dashboard.exa.ai" target="_blank" rel="noreferrer" className="underline hover:opacity-80">dashboard.exa.ai</a> (1,000 free searches). Runs in parallel with Tavily when both are active.
              </p>
            </div>

            <div>
              <label className={`block text-xs mb-1.5 font-sans ${isDark ? 'text-[#8E8E93]' : 'text-[#636366]'}`}>
                Base URL (Optional, auto-routed to Xiaomi MiMo)
              </label>
              <input
                type="text"
                value={baseUrl}
                onChange={(e) => setBaseUrl(e.target.value)}
                placeholder="Auto-routed from API key (Leave blank for official MiMo)"
                className={`${inputClass} text-xs font-mono`}
              />
              <p className={`text-[11px] mt-1 font-sans ${isDark ? 'text-[#636366]' : 'text-[#8E8E93]'}`}>
                Automatically connects to Xiaomi Token Plan (tp-*) or Pay-As-You-Go API endpoints.
              </p>
            </div>
          </div>

          {/* Danger Zone: Delete Unpinned Chats */}
          <div className={`space-y-3 pt-2 border-t ${isDark ? 'border-[#262626]' : 'border-[#CECDC4]'}`}>
            <div className="text-[11px] font-sans font-bold tracking-[1.2px] text-[#FF453A] uppercase">
              CHAT STORAGE
            </div>

            <div className={cardClass}>
              <div>
                <div className={`text-sm font-medium font-sans ${isDark ? 'text-[#CECDC4]' : 'text-[#141414]'}`}>
                  Delete Unpinned Chats
                </div>
                <div className={`text-xs font-sans mt-0.5 ${isDark ? 'text-[#636366]' : 'text-[#8E8E93]'}`}>
                  Permanently delete all chats except pinned ones
                </div>
              </div>

              {showClearConfirm ? (
                <div className="flex items-center gap-2">
                  <button
                    onClick={handleDeleteUnpinned}
                    disabled={isDeleting}
                    className="px-3 py-1.5 rounded-lg bg-[#FF453A] text-white text-xs font-medium hover:opacity-90 transition-colors cursor-pointer"
                  >
                    {isDeleting ? 'Deleting...' : 'Confirm'}
                  </button>
                  <button
                    onClick={() => setShowClearConfirm(false)}
                    className={`px-2.5 py-1.5 rounded-lg text-xs transition-colors cursor-pointer ${
                      isDark ? 'bg-[#262626] text-[#CECDC4] hover:text-white' : 'bg-[#CECDC4] text-[#141414] hover:text-black'
                    }`}
                  >
                    Cancel
                  </button>
                </div>
              ) : (
                <button
                  onClick={() => setShowClearConfirm(true)}
                  className={`px-3.5 py-1.5 rounded-lg border text-xs font-medium transition-colors flex items-center gap-1.5 cursor-pointer ${
                    isDark
                      ? 'bg-[#1A1A1A] border-[#262626] text-[#FF453A] hover:bg-[#262626]'
                      : 'bg-[#DFDED8] border-[#CECDC4] text-[#FF453A] hover:bg-[#CECDC4]'
                  }`}
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
