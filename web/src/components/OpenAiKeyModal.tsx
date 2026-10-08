import React, { useState } from 'react'
import { Key, X, Check } from 'lucide-react'
import { getOpenAiApiKey, setOpenAiApiKey } from '@/services/audioService'

interface OpenAiKeyModalProps {
  isOpen: boolean
  onClose: () => void
  onSuccess: (apiKey: string) => void
}

export function OpenAiKeyModal({ isOpen, onClose, onSuccess }: OpenAiKeyModalProps) {
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
      <div className="w-full max-w-md bg-[#0A0A0A] border border-[#262626] rounded-2xl shadow-2xl p-6 relative">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-[#8E8E93] hover:text-[#CECDC4] transition-colors p-1"
        >
          <X className="w-5 h-5" />
        </button>

        <div className="flex items-center gap-3 mb-3">
          <div className="w-10 h-10 rounded-full bg-[#1A1A1A] border border-[#262626] flex items-center justify-center text-[#CECDC4]">
            <Key className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-base font-bold text-[#CECDC4] font-sans">
              OpenAI API Key Required
            </h3>
            <p className="text-xs text-[#8E8E93] font-sans">
              Voice Dictation (OpenAI Whisper STT)
            </p>
          </div>
        </div>

        <p className="text-xs text-[#8E8E93] font-sans leading-relaxed mb-4">
          To record audio and transcribe your voice to text using OpenAI Whisper, configure your OpenAI API key below.
        </p>

        <div className="space-y-3">
          <div>
            <label className="block text-[11px] font-sans uppercase font-bold tracking-wider text-[#636366] mb-1.5">
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
              className="w-full px-3.5 py-2.5 bg-[#141414] border border-[#262626] rounded-xl text-sm text-[#CECDC4] font-mono focus:outline-none focus:border-[#CECDC4] transition-colors"
            />
          </div>

          {error && (
            <p className="text-xs text-[#FF453A] font-sans">{error}</p>
          )}

          <div className="pt-2 flex items-center justify-end gap-2.5">
            <button
              onClick={onClose}
              className="px-4 py-2 rounded-xl text-xs font-medium text-[#8E8E93] hover:text-[#CECDC4] hover:bg-[#141414] transition-colors font-sans"
            >
              Cancel
            </button>
            <button
              onClick={handleSave}
              className="px-4 py-2 rounded-xl bg-[#CECDC4] hover:bg-white text-black text-xs font-bold transition-all font-sans flex items-center gap-1.5"
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
