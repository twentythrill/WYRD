import React, { useState, useRef, useEffect } from 'react'
import { ChevronDown, Check, PanelLeftOpen, Sun, Moon, Folder } from 'lucide-react'

interface ModelOption {
  id: string
  shortName: string
}

const SUPPORTED_MODELS: ModelOption[] = [
  { id: 'mimo-v2.6-flash', shortName: 'MiMo 2.6 Flash' },
  { id: 'mimo-v2.6-pro', shortName: 'MiMo 2.6 Pro' },
]

const REASONING_EFFORTS = [
  { id: 'off', displayName: 'Off' },
  { id: 'low', displayName: 'Low' },
  { id: 'medium', displayName: 'Medium' },
  { id: 'high', displayName: 'High' },
]

interface TopBarProps {
  activeModelId: string
  reasoningEffortId: string
  activeChatTitle?: string
  activeProjectName?: string
  activeProjectColor?: string | null
  isSidebarOpen?: boolean
  isDark?: boolean
  onSelectModel: (modelId: string) => void
  onSelectReasoningEffort: (effortId: string) => void
  onToggleSidebar: () => void
  onToggleTheme?: () => void
}

export function TopBar({
  activeModelId,
  reasoningEffortId,
  activeChatTitle,
  activeProjectName,
  activeProjectColor,
  isSidebarOpen,
  isDark = true,
  onSelectModel,
  onSelectReasoningEffort,
  onToggleSidebar,
  onToggleTheme,
}: TopBarProps) {
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)

  const activeModel =
    SUPPORTED_MODELS.find((m) => m.id === activeModelId) || SUPPORTED_MODELS[1]
  const activeEffort =
    REASONING_EFFORTS.find((e) => e.id === reasoningEffortId) || REASONING_EFFORTS[2]

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
          className="p-2 -ml-1 mr-1 text-[#8E8E93] hover:text-[#CECDC4] hover:bg-[#141414] rounded-lg transition-colors flex items-center justify-center shrink-0"
        >
          <PanelLeftOpen className="w-5 h-5 stroke-[2]" />
        </button>
      )}

      <div className="relative" ref={dropdownRef}>
        {/* Model + Reasoning Effort Selector */}
        <button
          onClick={() => setDropdownOpen(!dropdownOpen)}
          className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg hover:bg-[#141414] transition-colors text-left"
        >
          <span className="text-[17px] font-bold text-[#CECDC4] font-sans">
            {modelBase}
          </span>
          <span className="text-[17px] font-normal text-[#CECDC4] font-sans">
            {modelSuffix}
          </span>
          <span className="text-[15px] font-normal text-[#8E8E93] font-sans">
            {' · ' + activeEffort.displayName}
          </span>
          <ChevronDown className="w-4 h-4 text-[#8E8E93] ml-1 stroke-[2]" />
        </button>

        {/* Dropdown Menu */}
        {dropdownOpen && (
          <div className="absolute left-0 top-full mt-1.5 w-56 rounded-xl bg-[#1A1A1A] border border-[#262626] shadow-2xl py-2 z-50 text-sm">
            <div className="px-3.5 py-1.5 text-[10px] font-semibold tracking-wider text-[#636366] uppercase font-sans">
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
                  className="w-full px-3.5 py-2 flex items-center justify-between text-left hover:bg-[#262626]/60 transition-colors"
                >
                  <span
                    className={`font-sans text-sm ${
                      isSelected ? 'font-bold text-[#CECDC4]' : 'font-normal text-[#8E8E93]'
                    }`}
                  >
                    {model.shortName}
                  </span>
                  {isSelected && <Check className="w-4 h-4 text-[#CECDC4]" />}
                </button>
              )
            })}

            <div className="my-1.5 border-t border-[#0A0A0A]" />

            <div className="px-3.5 py-1.5 text-[10px] font-semibold tracking-wider text-[#636366] uppercase font-sans">
              REASONING EFFORT
            </div>
            {REASONING_EFFORTS.map((effort) => {
              const isSelected = effort.id === reasoningEffortId
              return (
                <button
                  key={effort.id}
                  onClick={() => {
                    onSelectReasoningEffort(effort.id)
                    setDropdownOpen(false)
                  }}
                  className="w-full px-3.5 py-2 flex items-center justify-between text-left hover:bg-[#262626]/60 transition-colors"
                >
                  <span
                    className={`font-sans text-sm ${
                      isSelected ? 'font-bold text-[#CECDC4]' : 'font-normal text-[#8E8E93]'
                    }`}
                  >
                    {effort.displayName}
                  </span>
                  {isSelected && <Check className="w-4 h-4 text-[#CECDC4]" />}
                </button>
              )
            })}
          </div>
        )}
      </div>

      {/* Active Conversation / Project Breadcrumb */}
      {activeProjectName ? (
        <div className="ml-2 pl-2.5 sm:ml-2.5 sm:pl-3 border-l border-[#262626] h-5 flex items-center min-w-0 gap-1.5 text-xs text-[#8E8E93]">
          <Folder
            className="w-3.5 h-3.5 shrink-0"
            style={{ color: activeProjectColor?.trim() || '#CECDC4' }}
          />
          <span className="font-semibold text-[#CECDC4] truncate max-w-[90px] sm:max-w-[140px] font-sans">
            {activeProjectName}
          </span>
          {activeChatTitle && (
            <>
              <span className="text-[#636366]">/</span>
              <span className="truncate max-w-[100px] sm:max-w-[200px] lg:max-w-[320px] font-sans">
                {activeChatTitle}
              </span>
            </>
          )}
        </div>
      ) : activeChatTitle ? (
        <div className="ml-2 pl-2.5 sm:ml-2.5 sm:pl-3 border-l border-[#262626] h-5 flex items-center min-w-0">
          <span className="text-xs text-[#8E8E93] truncate font-sans max-w-[140px] sm:max-w-[280px] lg:max-w-[420px]">
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
            className="p-2 text-[#8E8E93] hover:text-[#CECDC4] hover:bg-[#141414] rounded-lg transition-colors flex items-center justify-center shrink-0"
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
