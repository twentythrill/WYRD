import { useState, useRef, useEffect } from 'react'
import { ChevronDown, Check, PanelLeftOpen, Sun, Moon, Folder } from 'lucide-react'

interface ModelOption {
  id: string
  shortName: string
}

const SUPPORTED_MODELS: ModelOption[] = [
  { id: 'mimo-v2.6-flash', shortName: 'MiMo 2.6 Flash' },
  { id: 'mimo-v2.6-pro', shortName: 'MiMo 2.6 Pro' },
]

interface TopBarProps {
  activeModelId: string
  isThinkingEnabled?: boolean
  reasoningEffortId?: string
  activeChatTitle?: string
  activeProjectName?: string
  activeProjectColor?: string | null
  isSidebarOpen?: boolean
  isDark?: boolean
  onSelectModel: (modelId: string) => void
  onToggleThinking?: (enabled: boolean) => void
  onToggleSidebar: () => void
  onToggleTheme?: () => void
}

export function TopBar({
  activeModelId,
  isThinkingEnabled,
  reasoningEffortId,
  activeChatTitle,
  activeProjectName,
  activeProjectColor,
  isSidebarOpen,
  isDark = true,
  onSelectModel,
  onToggleThinking,
  onToggleSidebar,
  onToggleTheme,
}: TopBarProps) {
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)

  const activeModel =
    SUPPORTED_MODELS.find((m) => m.id === activeModelId) || SUPPORTED_MODELS[1]

  const isThinking = typeof isThinkingEnabled === 'boolean'
    ? isThinkingEnabled
    : (reasoningEffortId ? reasoningEffortId !== 'off' : true)

  const handleToggleThinking = (newVal: boolean) => {
    onToggleThinking?.(newVal)
  }

  const modelBase = 'MiMo 2.6'
  const modelSuffix = activeModel.id.includes('flash') ? ' Flash' : ' Pro'

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setDropdownOpen(false)
      }
    }
    if (dropdownOpen) {
      document.addEventListener('mousedown', handleClickOutside)
    }
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [dropdownOpen])

  return (
    <header className="h-14 w-full bg-transparent px-4 flex items-center z-30 select-none">
      {/* Sidebar Toggle Icon (only shown when sidebar is collapsed) */}
      {!isSidebarOpen && (
        <button
          onClick={onToggleSidebar}
          title="Expand sidebar"
          className={`p-2 -ml-1 mr-1 rounded-lg transition-colors flex items-center justify-center shrink-0 ${
            isDark
              ? 'text-[#8E8E93] hover:text-[#CECDC4] hover:bg-[#141414]'
              : 'text-[#636366] hover:text-[#141414] hover:bg-[#E8E7E1]'
          }`}
        >
          <PanelLeftOpen className="w-5 h-5 stroke-[2]" />
        </button>
      )}

      <div className="relative" ref={dropdownRef}>
        {/* Model + Thinking Selector */}
        <button
          onClick={() => setDropdownOpen(!dropdownOpen)}
          className={`flex items-center gap-1 px-2.5 py-1.5 rounded-lg transition-colors text-left ${
            isDark ? 'hover:bg-[#141414]' : 'hover:bg-[#E8E7E1]'
          }`}
        >
          <span className={`text-[17px] font-bold font-sans ${isDark ? 'text-[#CECDC4]' : 'text-[#141414]'}`}>
            {modelBase}
          </span>
          <span className={`text-[17px] font-normal font-sans ${isDark ? 'text-[#CECDC4]' : 'text-[#141414]'}`}>
            {modelSuffix}
          </span>
          {isThinking && (
            <span className={`text-[15px] font-normal font-sans ${isDark ? 'text-[#8E8E93]' : 'text-[#636366]'}`}>
              {' · Thinking'}
            </span>
          )}
          <ChevronDown className={`w-4 h-4 ml-1 stroke-[2] ${isDark ? 'text-[#8E8E93]' : 'text-[#636366]'}`} />
        </button>

        {/* Dropdown Menu */}
        {dropdownOpen && (
          <div className={`absolute left-0 top-full mt-1.5 w-56 rounded-xl border shadow-2xl py-2 z-50 text-sm ${
            isDark ? 'bg-[#1A1A1A] border-[#262626]' : 'bg-[#F0EFEA] border-[#CECDC4]'
          }`}>
            <div className={`px-3.5 py-1.5 text-[10px] font-semibold tracking-wider uppercase font-sans ${
              isDark ? 'text-[#636366]' : 'text-[#8E8E93]'
            }`}>
              MODEL
            </div>
            {SUPPORTED_MODELS.map((model) => {
              const isSelected = model.id === activeModelId
              return (
                <button
                  key={model.id}
                  onClick={() => {
                    onSelectModel(model.id)
                    setDropdownOpen(false)
                  }}
                  className={`w-full px-3.5 py-2 flex items-center justify-between text-left transition-colors cursor-pointer ${
                    isDark ? 'hover:bg-[#262626]/60' : 'hover:bg-[#DFDED8]'
                  }`}
                >
                  <span
                    className={`font-sans text-sm ${
                      isSelected
                        ? `font-bold ${isDark ? 'text-[#CECDC4]' : 'text-[#141414]'}`
                        : `font-normal ${isDark ? 'text-[#8E8E93]' : 'text-[#636366]'}`
                    }`}
                  >
                    {model.shortName}
                  </span>
                  {isSelected && (
                    <Check className={`w-4 h-4 ${isDark ? 'text-[#CECDC4]' : 'text-[#141414]'}`} />
                  )}
                </button>
              )
            })}

            <div className={`my-1.5 border-t ${isDark ? 'border-[#262626]' : 'border-[#CECDC4]'}`} />

            <div
              onClick={() => handleToggleThinking(!isThinking)}
              className={`px-3.5 py-2 flex items-center justify-between cursor-pointer transition-colors ${
                isDark ? 'hover:bg-[#262626]/60' : 'hover:bg-[#DFDED8]'
              }`}
            >
              <div className="flex flex-col">
                <span className={`font-sans text-sm font-medium ${isDark ? 'text-[#CECDC4]' : 'text-[#141414]'}`}>
                  Thinking
                </span>
                <span className={`font-sans text-[11px] ${isDark ? 'text-[#8E8E93]' : 'text-[#636366]'}`}>
                  Deep reasoning mode
                </span>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={isThinking}
                onClick={(e) => {
                  e.stopPropagation()
                  handleToggleThinking(!isThinking)
                }}
                className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none ${
                  isThinking
                    ? isDark ? 'bg-[#CECDC4]' : 'bg-[#141414]'
                    : isDark ? 'bg-[#262626]' : 'bg-[#CECDC4]'
                }`}
              >
                <span
                  className={`pointer-events-none inline-block h-4 w-4 transform rounded-full shadow ring-0 transition duration-200 ease-in-out ${
                    isThinking
                      ? `translate-x-4 ${isDark ? 'bg-[#141414]' : 'bg-[#F9F9F7]'}`
                      : `translate-x-0 ${isDark ? 'bg-[#8E8E93]' : 'bg-white'}`
                  }`}
                />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Active Conversation / Project Breadcrumb */}
      {activeProjectName ? (
        <div className={`ml-2 pl-2.5 sm:ml-2.5 sm:pl-3 border-l h-5 flex items-center min-w-0 gap-1.5 text-xs ${
          isDark ? 'border-[#262626] text-[#8E8E93]' : 'border-[#CECDC4] text-[#636366]'
        }`}>
          <Folder
            className="w-3.5 h-3.5 shrink-0"
            style={{ color: activeProjectColor?.trim() || (isDark ? '#CECDC4' : '#141414') }}
          />
          <span className={`font-semibold truncate max-w-[90px] sm:max-w-[140px] font-sans ${
            isDark ? 'text-[#CECDC4]' : 'text-[#141414]'
          }`}>
            {activeProjectName}
          </span>
          {activeChatTitle && (
            <>
              <span className={isDark ? 'text-[#636366]' : 'text-[#8E8E93]'}>/</span>
              <span className="truncate max-w-[100px] sm:max-w-[200px] lg:max-w-[320px] font-sans">
                {activeChatTitle}
              </span>
            </>
          )}
        </div>
      ) : activeChatTitle ? (
        <div className={`ml-2 pl-2.5 sm:ml-2.5 sm:pl-3 border-l h-5 flex items-center min-w-0 ${
          isDark ? 'border-[#262626]' : 'border-[#CECDC4]'
        }`}>
          <span className={`text-xs truncate font-sans max-w-[140px] sm:max-w-[280px] lg:max-w-[420px] ${
            isDark ? 'text-[#8E8E93]' : 'text-[#636366]'
          }`}>
            {activeChatTitle}
          </span>
        </div>
      ) : null}

      {/* Right Action: Theme Switcher */}
      {onToggleTheme && (
        <div className="ml-auto flex items-center">
          <button
            onClick={onToggleTheme}
            title={isDark ? "Switch to Light Mode" : "Switch to Dark Mode"}
            className={`p-2 rounded-lg transition-colors flex items-center justify-center shrink-0 cursor-pointer ${
              isDark
                ? 'text-[#8E8E93] hover:text-[#CECDC4] hover:bg-[#141414]'
                : 'text-[#636366] hover:text-[#141414] hover:bg-[#E8E7E1]'
            }`}
          >
            {isDark ? (
              <Sun className="w-4 h-4 stroke-[2]" />
            ) : (
              <Moon className="w-4 h-4 stroke-[2]" />
            )}
          </button>
        </div>
      )}
    </header>
  )
}
