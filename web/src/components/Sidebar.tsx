import React, { useState } from 'react'
import { Plus, Search, Settings, Pin, PinOff, Trash2, Edit3, X, PanelLeftClose } from 'lucide-react'
import { type Chat } from '@/lib/supabase'

interface SidebarProps {
  isOpen: boolean
  chats: Chat[]
  activeChatId: string | null
  onClose: () => void
  onSelectChat: (chatId: string) => void
  onNewChat: () => void
  onTogglePin: (chat: Chat) => void
  onRenameChat: (chat: Chat, newTitle: string) => void
  onDeleteChat: (chatId: string) => void
  onOpenSettings: () => void
  isDark?: boolean
}

export function Sidebar({
  isOpen,
  chats,
  activeChatId,
  onClose,
  onSelectChat,
  onNewChat,
  onTogglePin,
  onRenameChat,
  onDeleteChat,
  onOpenSettings,
  isDark = true,
}: SidebarProps) {
  const [isSearchExpanded, setIsSearchExpanded] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [chatToRename, setChatToRename] = useState<Chat | null>(null)
  const [renameText, setRenameText] = useState('')
  const [chatToDelete, setChatToDelete] = useState<Chat | null>(null)

  React.useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        if (chatToDelete) setChatToDelete(null)
        if (chatToRename) setChatToRename(null)
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [chatToDelete, chatToRename])

  const deduplicatedChats = React.useMemo(() => {
    const seen = new Set<string>()
    return chats.filter((c) => {
      if (seen.has(c.id)) return false
      seen.add(c.id)
      return true
    })
  }, [chats])

  const filteredChats = deduplicatedChats.filter((c) =>
    c.title.toLowerCase().includes(searchQuery.toLowerCase())
  )

  const getTimeMs = (dateStr?: string | null): number => {
    if (!dateStr) return 0
    const t = new Date(dateStr).getTime()
    return isNaN(t) ? 0 : t
  }

  // Stable deterministic sort: pinned first, then updated_at desc, then created_at desc, then id desc
  const sortedChats = [...filteredChats].sort((a, b) => {
    if (a.is_pinned !== b.is_pinned) return a.is_pinned ? -1 : 1
    const diffUpdated = getTimeMs(b.updated_at) - getTimeMs(a.updated_at)
    if (diffUpdated !== 0) return diffUpdated
    const diffCreated = getTimeMs(b.created_at) - getTimeMs(a.created_at)
    if (diffCreated !== 0) return diffCreated
    return b.id.localeCompare(a.id)
  })

  const handleStartRename = (e: React.MouseEvent, chat: Chat) => {
    e.stopPropagation()
    setChatToRename(chat)
    setRenameText(chat.title)
  }

  const handleConfirmRename = () => {
    if (chatToRename && renameText.trim()) {
      onRenameChat(chatToRename, renameText.trim())
    }
    setChatToRename(null)
  }

  return (
    <>
      {/* Backdrop for mobile */}
      {isOpen && (
        <div
          onClick={onClose}
          className="fixed inset-0 bg-black/60 z-40 md:hidden"
        />
      )}

      <aside
        className={`fixed md:relative inset-y-0 left-0 z-50 h-screen bg-[#0A0A0A] flex flex-col select-none transition-all duration-300 ease-in-out shrink-0 overflow-hidden ${
          isOpen
            ? 'w-[270px] translate-x-0 opacity-100 border-r border-[#262626]'
            : 'w-0 -translate-x-full md:translate-x-0 md:w-0 opacity-0 pointer-events-none border-r-0'
        }`}
      >
        <div className="w-[270px] h-full flex flex-col">
          {/* App Branding Header: Exact logo marker + "WYRD" + Collapse Button */}
          <div className="h-14 px-4 flex items-center justify-between shrink-0">
            <div className="flex items-center">
              <img
                src={isDark ? '/app_logo.png' : '/app_icon_trans_black.png'}
                alt="WYRD Logo"
                className="w-7 h-7 object-contain"
              />
              <span className="ml-2.5 font-sans font-bold text-[18px] text-[#CECDC4] tracking-[2px] uppercase">
                WYRD
              </span>
            </div>
            <button
              onClick={onClose}
              title="Collapse sidebar"
              className="p-2 text-[#8E8E93] hover:text-[#CECDC4] hover:bg-[#141414] rounded-lg transition-colors flex items-center justify-center"
            >
              <PanelLeftClose className="w-5 h-5 stroke-[2]" />
            </button>
          </div>

        {/* Top Static Actions */}
        <div className="px-3 space-y-0.5">
          {/* New Chat */}
          <button
            onClick={() => {
              onNewChat()
              if (window.innerWidth < 768) onClose()
            }}
            className={`w-full h-11 px-3 flex items-center rounded-xl transition-colors ${
              activeChatId === null
                ? 'bg-[#1A1A1A] text-[#CECDC4]'
                : 'hover:bg-[#141414] text-[#CECDC4]'
            }`}
          >
            <Plus className="w-5 h-5 text-[#CECDC4] stroke-[2]" />
            <span className="ml-3 font-sans text-sm font-medium">New Chat</span>
          </button>

          {/* Search Chats */}
          <button
            onClick={() => setIsSearchExpanded(!isSearchExpanded)}
            className="w-full h-11 px-3 flex items-center rounded-xl hover:bg-[#141414] text-[#CECDC4] transition-colors"
          >
            <Search className="w-5 h-5 text-[#CECDC4] stroke-[2]" />
            <span className="ml-3 font-sans text-sm font-medium">Search Chats</span>
          </button>

          {/* Filter input */}
          {isSearchExpanded && (
            <div className="px-1 py-1">
              <div className="relative flex items-center">
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Filter conversations..."
                  autoFocus
                  className="w-full px-3 py-2 bg-[#141414] border border-[#262626] rounded-xl text-xs text-[#CECDC4] placeholder-[#636366] font-sans focus:outline-none focus:border-[#CECDC4]"
                />
                {searchQuery && (
                  <button
                    onClick={() => setSearchQuery('')}
                    className="absolute right-2.5 text-[#8E8E93] hover:text-[#CECDC4]"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>
            </div>
          )}

          {/* Settings */}
          <button
            onClick={() => {
              onOpenSettings()
              if (window.innerWidth < 768) onClose()
            }}
            className="w-full h-11 px-3 flex items-center rounded-xl hover:bg-[#141414] text-[#CECDC4] transition-colors"
          >
            <Settings className="w-5 h-5 text-[#CECDC4] stroke-[2]" />
            <span className="ml-3 font-sans text-sm font-medium">Settings</span>
          </button>
        </div>

        {/* Divider */}
        <div className="my-3.5 mx-3 border-t border-[#262626]" />

        {/* History Header */}
        <div className="px-5 py-1 text-[10px] font-sans font-bold tracking-[1.2px] text-[#636366] uppercase">
          HISTORY
        </div>

        {/* Past Chats List */}
        <div className="flex-1 overflow-y-auto px-2 py-1 space-y-0.5">
          {sortedChats.length === 0 ? (
            <div className="px-4 py-3 text-xs text-[#636366] font-sans">
              {searchQuery ? 'No matching conversations' : 'No conversation history yet'}
            </div>
          ) : (
            sortedChats.map((chat) => {
              const isSelected = chat.id === activeChatId
              return (
                <div
                  key={chat.id}
                  onClick={() => {
                    onSelectChat(chat.id)
                    if (window.innerWidth < 768) onClose()
                  }}
                  className={`group relative flex items-center justify-between px-3 py-2.5 rounded-xl cursor-pointer transition-colors ${
                    isSelected
                      ? 'bg-[#1A1A1A] text-[#CECDC4]'
                      : 'text-[#8E8E93] hover:text-[#CECDC4] hover:bg-[#141414]'
                  }`}
                >
                  <div className="flex items-center gap-2.5 min-w-0 pr-2">
                    {chat.is_pinned && (
                      <Pin className="w-3.5 h-3.5 text-[#CECDC4] shrink-0 fill-current" />
                    )}
                    <span className="text-sm font-sans truncate">{chat.title}</span>
                  </div>

                  {/* Context Actions on Hover */}
                  <div className="opacity-0 group-hover:opacity-100 flex items-center gap-1 transition-opacity">
                    <button
                      onClick={(e) => {
                        e.stopPropagation()
                        onTogglePin(chat)
                      }}
                      title={chat.is_pinned ? 'Unpin' : 'Pin'}
                      className="p-1 rounded text-[#8E8E93] hover:text-[#CECDC4] hover:bg-[#262626]"
                    >
                      {chat.is_pinned ? (
                        <PinOff className="w-3.5 h-3.5" />
                      ) : (
                        <Pin className="w-3.5 h-3.5" />
                      )}
                    </button>
                    <button
                      onClick={(e) => handleStartRename(e, chat)}
                      title="Rename"
                      className="p-1 rounded text-[#8E8E93] hover:text-[#CECDC4] hover:bg-[#262626]"
                    >
                      <Edit3 className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={(e) => {
                        e.stopPropagation()
                        setChatToDelete(chat)
                      }}
                      title="Delete"
                      className="p-1 rounded text-[#8E8E93] hover:text-[#FF453A] hover:bg-[#262626]"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              )
            })
          )}
            </div>
          </div>
        </aside>

      {/* Rename Chat Modal */}
      {chatToRename && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="w-full max-w-sm bg-[#141414] border border-[#262626] rounded-2xl p-5 shadow-2xl">
            <h3 className="text-sm font-bold text-[#CECDC4] font-sans mb-3">
              Rename Conversation
            </h3>
            <input
              type="text"
              value={renameText}
              onChange={(e) => setRenameText(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleConfirmRename()
                if (e.key === 'Escape') setChatToRename(null)
              }}
              autoFocus
              className="w-full px-3 py-2 bg-[#1A1A1A] border border-[#262626] rounded-xl text-sm text-[#CECDC4] focus:outline-none focus:border-[#CECDC4] font-sans"
            />
            <div className="mt-4 flex items-center justify-end gap-2">
              <button
                onClick={() => setChatToRename(null)}
                className="px-3 py-1.5 rounded-lg text-xs font-medium text-[#8E8E93] hover:text-[#CECDC4]"
              >
                Cancel
              </button>
              <button
                onClick={handleConfirmRename}
                className="px-3.5 py-1.5 rounded-lg bg-[#CECDC4] text-black text-xs font-semibold hover:bg-white"
              >
                Save
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Chat Confirmation Modal */}
      {chatToDelete && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 select-none animate-in fade-in duration-150"
          onClick={() => setChatToDelete(null)}
        >
          <div
            className="w-full max-w-sm bg-[#1C1C1E] border border-[#2C2C2E] rounded-2xl p-6 shadow-2xl animate-in zoom-in-95 duration-150"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="text-lg font-semibold text-white font-sans mb-1.5">
              Delete chat?
            </h3>
            <p className="text-[15px] text-[#A1A1A6] font-sans leading-relaxed mb-6">
              Are you sure you want to delete this chat?
            </p>
            <div className="flex items-center justify-end gap-3">
              <button
                onClick={() => setChatToDelete(null)}
                className="px-4 py-2 rounded-xl text-sm font-medium text-white bg-[#2C2C2E] hover:bg-[#3A3A3C] transition-colors font-sans"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  const id = chatToDelete.id
                  setChatToDelete(null)
                  onDeleteChat(id)
                }}
                className="px-4 py-2 rounded-xl bg-[#E53935] hover:bg-[#D32F2F] text-white text-sm font-semibold border border-[#FF5252]/70 shadow-sm transition-colors font-sans"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
