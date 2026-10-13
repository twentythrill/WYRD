import { useState } from 'react'
import { Key, X, Check } from 'lucide-react'
import { getOpenAiApiKey, setOpenAiApiKey } from '@/services/audioService'

interface OpenAiKeyModalProps {
  isOpen: boolean
  onClose: () => void
  onSuccess: (apiKey: string) => void
  isDark?: boolean
}

export function OpenAiKeyModal({ isOpen, onClose, onSuccess, isDark = true }: OpenAiKeyModalProps) {
  const [key, setKey] = useState(getOpenAiApiKey())
  const [error, setError] = useState('')

  if (!isOpen) return null

  const handleSave = () => {
    const trimmed = key.trim()
    if (!trimmed) {
      setError('Please enter a valid OpenAI API key.')
      return
    }
    setOpenAiApiKey(trimmed)
    setError('')
    onSuccess(trimmed)
    onClose()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 select-none">
      <div className={`w-full max-w-md border rounded-2xl shadow-2xl p-6 relative ${
        isDark ? 'bg-[#0A0A0A] border-[#262626]' : 'bg-[#F0EFEA] border-[#CECDC4]'
      }`}>
        <button
          onClick={onClose}
          className={`absolute top-4 right-4 transition-colors p-1 cursor-pointer ${
            isDark ? 'text-[#8E8E93] hover:text-[#CECDC4]' : 'text-[#636366] hover:text-[#141414]'
          }`}
        >
          <X className="w-5 h-5" />
        </button>

        <div className="flex items-center gap-3 mb-3">
          <div className={`w-10 h-10 rounded-full border flex items-center justify-center ${
            isDark ? 'bg-[#1A1A1A] border-[#262626] text-[#CECDC4]' : 'bg-[#DFDED8] border-[#CECDC4] text-[#141414]'
          }`}>
            <Key className="w-5 h-5" />
          </div>
          <div>
            <h3 className={`text-base font-bold font-sans ${isDark ? 'text-[#CECDC4]' : 'text-[#141414]'}`}>
              OpenAI API Key Required
            </h3>
            <p className={`text-xs font-sans ${isDark ? 'text-[#8E8E93]' : 'text-[#636366]'}`}>
              Voice Dictation (OpenAI gpt-4o-mini-transcribe)
            </p>
          </div>
        </div>

        <p className={`text-xs font-sans leading-relaxed mb-4 ${isDark ? 'text-[#8E8E93]' : 'text-[#636366]'}`}>
          To record audio and transcribe your voice to text using OpenAI gpt-4o-mini-transcribe, configure your OpenAI API key below.
        </p>

        <div className="space-y-3">
          <div>
            <label className={`block text-[11px] font-sans uppercase font-bold tracking-wider mb-1.5 ${
              isDark ? 'text-[#636366]' : 'text-[#8E8E93]'
            }`}>
              OpenAI API Key
            </label>
            <input
              type="password"
              value={key}
              onChange={(e) => {
                setKey(e.target.value)
                setError('')
              }}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleSave()
                if (e.key === 'Escape') onClose()
              }}
              placeholder="sk-proj-..."
              autoFocus
              className={`w-full px-3.5 py-2.5 border rounded-xl text-sm font-mono focus:outline-none transition-colors ${
                isDark
                  ? 'bg-[#141414] border-[#262626] text-[#CECDC4] focus:border-[#CECDC4]'
                  : 'bg-[#DFDED8] border-[#CECDC4] text-[#141414] focus:border-[#141414]'
              }`}
            />
          </div>

          {error && (
            <p className="text-xs text-[#FF453A] font-sans">{error}</p>
          )}

          <div className="pt-2 flex items-center justify-end gap-2.5">
            <button
              onClick={onClose}
              className={`px-4 py-2 rounded-xl text-xs font-medium transition-colors font-sans cursor-pointer ${
                isDark
                  ? 'text-[#8E8E93] hover:text-[#CECDC4] hover:bg-[#141414]'
                  : 'text-[#636366] hover:text-[#141414] hover:bg-[#E8E7E1]'
              }`}
            >
              Cancel
            </button>
            <button
              onClick={handleSave}
              disabled={!key.trim()}
              className={`px-4 py-2 rounded-xl text-xs font-bold transition-all font-sans flex items-center gap-1.5 cursor-pointer ${
                !key.trim()
                  ? isDark
                    ? 'bg-[#262626] text-[#636366] cursor-not-allowed border border-[#262626]'
                    : 'bg-[#CECDC4] text-[#8E8E93] cursor-not-allowed border border-[#CECDC4]'
                  : isDark
                  ? 'bg-[#CECDC4] text-black hover:bg-white active:scale-95'
                  : 'bg-[#141414] text-[#F9F9F7] hover:bg-black active:scale-95'
              }`}
            >
              <Check className="w-4 h-4 stroke-[2.5]" />
              <span>Save & Continue</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
