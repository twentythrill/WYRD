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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#100F0F]/80 backdrop-blur-sm p-4 select-none">
      <div className={`w-full max-w-md border rounded-2xl shadow-2xl p-6 relative ${
        isDark ? 'bg-[#100F0F] border-[#2E2D2C]' : 'bg-[#FEFCF1] border-[#D8D6CD]'
      }`}>
        <button
          onClick={onClose}
          className={`absolute top-4 right-4 transition-colors p-1 cursor-pointer ${
            isDark ? 'text-[#8A8983] hover:text-[#CECDC4]' : 'text-[#666460] hover:text-[#100F0F]'
          }`}
        >
          <X className="w-5 h-5" />
        </button>

        <div className="flex items-center gap-3 mb-3">
          <div className={`w-10 h-10 rounded-full border flex items-center justify-center ${
            isDark ? 'bg-[#1F1E1D] border-[#2E2D2C] text-[#CECDC4]' : 'bg-[#E1E0D6] border-[#D8D6CD] text-[#100F0F]'
          }`}>
            <Key className="w-5 h-5" />
          </div>
          <div>
            <h3 className={`text-base font-bold font-sans ${isDark ? 'text-[#CECDC4]' : 'text-[#100F0F]'}`}>
              OpenAI API Key Required
            </h3>
            <p className={`text-xs font-sans ${isDark ? 'text-[#8A8983]' : 'text-[#666460]'}`}>
              Voice Dictation (OpenAI gpt-4o-mini-transcribe)
            </p>
          </div>
        </div>

        <p className={`text-xs font-sans leading-relaxed mb-4 ${isDark ? 'text-[#8A8983]' : 'text-[#666460]'}`}>
          To record audio and transcribe your voice to text using OpenAI gpt-4o-mini-transcribe, configure your OpenAI API key below.
        </p>

        <div className="space-y-3">
          <div>
            <label className={`block text-[11px] font-sans uppercase font-bold tracking-wider mb-1.5 ${
              isDark ? 'text-[#8A8983]' : 'text-[#666460]'
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
                  ? 'bg-[#1F1E1D] border-[#2E2D2C] text-[#CECDC4] focus:border-[#CECDC4]'
                  : 'bg-[#E1E0D6] border-[#D8D6CD] text-[#100F0F] focus:border-[#100F0F]'
              }`}
            />
          </div>

          {error && (
            <p className="text-xs text-wyrd-text-primary font-sans">{error}</p>
          )}

          <div className="pt-2 flex items-center justify-end gap-2.5">
            <button
              onClick={onClose}
              className={`px-4 py-2 rounded-xl text-xs font-medium transition-colors font-sans cursor-pointer ${
                isDark
                  ? 'text-[#8A8983] hover:text-[#CECDC4] hover:bg-[#1F1E1D]'
                  : 'text-[#666460] hover:text-[#100F0F] hover:bg-[#EBE9DF]'
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
                    ? 'bg-[#2E2D2C] text-[#8A8983] cursor-not-allowed border border-[#2E2D2C]'
                    : 'bg-[#D8D6CD] text-[#666460] cursor-not-allowed border border-[#D8D6CD]'
                  : isDark
                  ? 'bg-[#CECDC4] text-[#100F0F] hover:bg-[#CECDC4] active:scale-95'
                  : 'bg-[#100F0F] text-[#FEFCF1] hover:bg-[#100F0F] active:scale-95'
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
