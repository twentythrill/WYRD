import React, { useState, useEffect, useRef } from 'react'
import { ArrowLeft, Check, Trash2, Upload, FileText, X, AlertTriangle, Plus } from 'lucide-react'
import { type Project, type ProjectFile } from '@/lib/supabase'
import { dbService } from '@/services/dbService'
import { extractTextFromPdf } from '@/utils/pdfExtractor'

export const PROJECT_COLOR_PRESETS = [
  { hex: '', name: 'Default', bg: '#CECDC4' },
  { hex: '#F97316', name: 'Orange', bg: '#F97316' },
  { hex: '#F59E0B', name: 'Amber', bg: '#F59E0B' },
  { hex: '#10B981', name: 'Emerald', bg: '#10B981' },
  { hex: '#06B6D4', name: 'Cyan', bg: '#06B6D4' },
  { hex: '#3B82F6', name: 'Blue', bg: '#3B82F6' },
  { hex: '#8B5CF6', name: 'Purple', bg: '#8B5CF6' },
  { hex: '#EC4899', name: 'Pink', bg: '#EC4899' },
  { hex: '#EF4444', name: 'Red', bg: '#EF4444' },
]

interface ProjectModalProps {
  isOpen: boolean
  project: Project | null
  userId: string
  isDark?: boolean
  onClose: () => void
  onSave: (name: string, description: string, systemPrompt: string, color: string) => Promise<Project | null>
  onDelete?: (projectId: string) => Promise<boolean>
}

async function extractTextFromFile(file: File): Promise<string | undefined> {
  try {
    if (file.name.toLowerCase().endsWith('.pdf') || file.type === 'application/pdf') {
      const pdfText = await extractTextFromPdf(file)
      return pdfText.trim() ? pdfText : undefined
    }
    const maxBytes = 150 * 1024 // 150 KB max per file for context
    const slice = file.slice(0, maxBytes)
    const text = await slice.text()
    if (text.includes('\0')) {
      return undefined
    }
    return text.trim() ? text : undefined
  } catch {
    return undefined
  }
}

function formatFileSize(bytes: number): string {
  if (!bytes || bytes <= 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`
}

export function ProjectModal({
  isOpen,
  project,
  userId,
  isDark = true,
  onClose,
  onSave,
  onDelete,
}: ProjectModalProps) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [systemPrompt, setSystemPrompt] = useState('')
  const [color, setColor] = useState('')
  const [files, setFiles] = useState<ProjectFile[]>([])
  const [stagedFiles, setStagedFiles] = useState<File[]>([])
  const [isLoadingFiles, setIsLoadingFiles] = useState(false)
  const [isUploading, setIsUploading] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [savedSuccess, setSavedSuccess] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    setErrorMessage(null)
    setStagedFiles([])
    if (project) {
      setName(project.name || '')
      setDescription(project.description || '')
      setSystemPrompt(project.system_prompt || '')
      setColor(project.color || '')
      setIsLoadingFiles(true)
      dbService.getProjectFiles(project.id).then((res) => {
        setFiles(res)
        setIsLoadingFiles(false)
      })
    } else {
      setName('')
      setDescription('')
      setSystemPrompt('')
      setColor('')
      setFiles([])
      setIsLoadingFiles(false)
    }
    setShowDeleteConfirm(false)
  }, [project, isOpen])

  if (!isOpen) return null

  const handleSave = async (e?: React.FormEvent) => {
    if (e) e.preventDefault()
    if (!name.trim()) {
      setErrorMessage('Project name is required.')
      return
    }
    setErrorMessage(null)
    setIsSaving(true)
    try {
      const saved = await onSave(name.trim(), description.trim(), systemPrompt.trim(), color)
      if (saved) {
        // If there were staged files for a new project, upload them now!
        if (stagedFiles.length > 0) {
          for (const file of stagedFiles) {
            try {
              const textContent = await extractTextFromFile(file)
              const cleanName = file.name.replace(/[^a-zA-Z0-9._-]/g, '_')
              const publicUrl = await dbService.uploadAttachment(userId, file, cleanName)
              await dbService.createProjectFile(userId, saved.id, {
                name: file.name,
                size: file.size,
                mimeType: file.type || 'application/octet-stream',
                storagePath: publicUrl || `${userId}/${cleanName}`,
                textContent: textContent || undefined,
              })
            } catch (fileErr) {
              console.warn('Failed to upload staged file:', file.name, fileErr)
            }
          }
        }

        setSavedSuccess(true)
        setTimeout(() => {
          setSavedSuccess(false)
          onClose()
        }, 400)
      } else {
        setErrorMessage('Could not save project. Please check database.')
      }
    } catch (err: any) {
      console.error('Project save error:', err)
      setErrorMessage(err.message || 'An error occurred while saving the project.')
    } finally {
      setIsSaving(false)
    }
  }

  const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFiles = event.target.files
    if (!selectedFiles || selectedFiles.length === 0) return

    if (!project) {
      // Stage files locally for new project
      setStagedFiles((prev) => [...prev, ...Array.from(selectedFiles)])
      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
      return
    }

    setIsUploading(true)
    try {
      for (const file of Array.from(selectedFiles)) {
        const textContent = await extractTextFromFile(file)
        const cleanName = file.name.replace(/[^a-zA-Z0-9._-]/g, '_')
        const publicUrl = await dbService.uploadAttachment(userId, file, cleanName)

        const newFile = await dbService.createProjectFile(userId, project.id, {
          name: file.name,
          size: file.size,
          mimeType: file.type || 'application/octet-stream',
          storagePath: publicUrl || `${userId}/${cleanName}`,
          textContent: textContent || undefined,
        })

        if (newFile) {
          setFiles((prev) => [...prev, newFile])
        }
      }
    } catch (err) {
      console.error('Error uploading project files:', err)
    } finally {
      setIsUploading(false)
      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
    }
  }

  const handleRemoveStagedFile = (index: number) => {
    setStagedFiles((prev) => prev.filter((_, i) => i !== index))
  }

  const handleDeleteFile = async (file: ProjectFile) => {
    const success = await dbService.deleteProjectFile(file.id, file.storage_path)
    if (success) {
      setFiles((prev) => prev.filter((f) => f.id !== file.id))
    }
  }

  const handleDeleteProject = async () => {
    if (!project || !onDelete) return
    setIsDeleting(true)
    try {
      const ok = await onDelete(project.id)
      if (ok) {
        onClose()
      }
    } finally {
      setIsDeleting(false)
      setShowDeleteConfirm(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
      <div className={`w-full max-w-lg border rounded-2xl shadow-2xl flex flex-col max-h-[90vh] overflow-hidden select-none ${
        isDark ? 'bg-[#0A0A0A] border-[#262626]' : 'bg-[#F0EFEA] border-[#CECDC4]'
      }`}>
        {/* Header */}
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
            <span className={`font-sans font-bold text-base ${isDark ? 'text-[#CECDC4]' : 'text-[#141414]'}`}>
              {project ? 'Project Settings' : 'New Project'}
            </span>
          </div>

          <button
            onClick={handleSave}
            disabled={!name.trim() || isSaving}
            className={`px-4 py-1.5 rounded-xl font-sans text-xs font-semibold flex items-center gap-1.5 transition-all cursor-pointer ${
              savedSuccess
                ? 'bg-[#32D74B] text-black font-semibold'
                : !name.trim() || isSaving
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
            ) : isSaving ? (
              <span>Saving...</span>
            ) : (
              <span>Save</span>
            )}
          </button>
        </div>

        {/* Content Body */}
        <div className="flex-1 overflow-y-auto p-4 space-y-5">
          {/* Error Banner */}
          {errorMessage && (
            <div className={`p-3 border rounded-xl text-xs flex items-center justify-between gap-2 ${
              isDark ? 'bg-[#1F1414] border-[#FF453A]/40 text-[#FF453A]' : 'bg-[#FDF2F2] border-[#FF453A]/30 text-[#FF453A]'
            }`}>
              <div className="flex items-center gap-2 min-w-0">
                <AlertTriangle className="w-4 h-4 shrink-0" />
                <span className="truncate">{errorMessage}</span>
              </div>
              <button
                type="button"
                onClick={() => setErrorMessage(null)}
                className={`p-1 transition-colors cursor-pointer ${
                  isDark ? 'hover:text-white' : 'hover:text-black'
                }`}
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          )}

          {/* Project Name */}
          <div className="space-y-1.5">
            <label className={`text-xs font-medium flex items-center gap-1 ${isDark ? 'text-[#CECDC4]' : 'text-[#141414]'}`}>
              Project Name <span className="text-[#FF453A]">*</span>
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => {
                setName(e.target.value)
                if (errorMessage) setErrorMessage(null)
              }}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault()
                  handleSave()
                }
              }}
              placeholder="e.g. Website Redesign, Paper Research..."
              className={`w-full rounded-xl text-sm focus:outline-none p-3 transition-colors border ${
                isDark
                  ? 'bg-[#141414] border-[#262626] text-[#CECDC4] placeholder-[#7A7A7A] focus:border-[#CECDC4]'
                  : 'bg-[#E8E7E1] border-[#CECDC4] text-[#141414] placeholder-[#9E9E9B] focus:border-[#141414]'
              }`}
            />
          </div>

          {/* Description */}
          <div className="space-y-1.5">
            <label className={`text-xs font-medium ${isDark ? 'text-[#8E8E93]' : 'text-[#636366]'}`}>
              Description (Optional)
            </label>
            <input
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Brief description of the project purpose..."
              className={`w-full rounded-xl text-sm focus:outline-none p-3 transition-colors border ${
                isDark
                  ? 'bg-[#141414] border-[#262626] text-[#CECDC4] placeholder-[#7A7A7A] focus:border-[#CECDC4]'
                  : 'bg-[#E8E7E1] border-[#CECDC4] text-[#141414] placeholder-[#9E9E9B] focus:border-[#141414]'
              }`}
            />
          </div>

          {/* Project Color */}
          <div className="space-y-1.5">
            <label className={`text-xs font-medium ${isDark ? 'text-[#CECDC4]' : 'text-[#141414]'}`}>
              Project Color
            </label>
            <div className="flex items-center gap-1.5 pt-1 pb-1 px-0.5 overflow-x-auto">
              {PROJECT_COLOR_PRESETS.map((preset) => {
                const isSelected = (color || '') === preset.hex
                return (
                  <button
                    key={preset.name}
                    type="button"
                    onClick={() => setColor(preset.hex)}
                    title={preset.name}
                    className={`w-8 h-8 rounded-full flex items-center justify-center transition-all cursor-pointer shrink-0 ${
                      isSelected
                        ? isDark ? 'border-2 border-white' : 'border-2 border-[#141414]'
                        : isDark ? 'border-2 border-transparent hover:border-[#383838]' : 'border-2 border-transparent hover:border-[#CECDC4]'
                    }`}
                  >
                    <span
                      className="w-6 h-6 rounded-full flex items-center justify-center border border-white/10 transition-transform"
                      style={{ backgroundColor: preset.bg }}
                    >
                      {isSelected && (
                        <Check
                          className={`w-3.5 h-3.5 ${
                            preset.hex === '' || preset.hex === '#F59E0B'
                              ? 'text-black'
                              : 'text-white'
                          } stroke-[3]`}
                        />
                      )}
                    </span>
                  </button>
                )
              })}
            </div>
          </div>

          {/* Custom System Prompt Override */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <label className={`text-xs font-medium ${isDark ? 'text-[#CECDC4]' : 'text-[#141414]'}`}>
                Project System Instructions
              </label>
            </div>
            <textarea
              value={systemPrompt}
              onChange={(e) => setSystemPrompt(e.target.value)}
              rows={5}
              placeholder="e.g. You are a senior frontend architect specializing in React and Tailwind. Always format responses with concise TypeScript code snippets and verify edge cases..."
              className={`w-full rounded-xl text-sm focus:outline-none p-3 resize-none font-mono text-xs transition-colors border ${
                isDark
                  ? 'bg-[#141414] border-[#262626] text-[#CECDC4] placeholder-[#7A7A7A] focus:border-[#CECDC4]'
                  : 'bg-[#E8E7E1] border-[#CECDC4] text-[#141414] placeholder-[#9E9E9B] focus:border-[#141414]'
              }`}
            />
          </div>

          {/* Source Files / Knowledge Base */}
          <div className={`space-y-2 pt-2 border-t ${isDark ? 'border-[#262626]' : 'border-[#CECDC4]'}`}>
            <div className="flex items-center justify-between">
              <h4 className={`text-xs font-semibold ${isDark ? 'text-[#CECDC4]' : 'text-[#141414]'}`}>Knowledge Base Files</h4>

              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={isUploading}
                className={`px-2.5 py-1 rounded-lg border text-xs font-medium flex items-center gap-1.5 transition-colors cursor-pointer ${
                  isDark
                    ? 'bg-[#141414] hover:bg-[#1A1A1A] border-[#262626] text-[#CECDC4]'
                    : 'bg-[#E8E7E1] hover:bg-[#DFDED8] border-[#CECDC4] text-[#141414]'
                }`}
              >
                <Plus className="w-3.5 h-3.5" />
                <span>{isUploading ? 'Uploading...' : 'Add Files'}</span>
              </button>
            </div>

            <input
              ref={fileInputRef}
              type="file"
              multiple
              accept=".txt,.md,.pdf,.json,.csv,.py,.ts,.js,.tsx,.jsx,.html,.css,.yaml,.yml,.sh,.sql"
              className="hidden"
              onChange={handleFileUpload}
            />

            {isLoadingFiles ? (
              <div className={`p-4 flex items-center justify-center gap-2 text-xs ${isDark ? 'text-[#8E8E93]' : 'text-[#636366]'}`}>
                <div className={`w-3.5 h-3.5 border-2 rounded-full animate-spin ${
                  isDark ? 'border-[#262626] border-t-[#CECDC4]' : 'border-[#CECDC4] border-t-[#141414]'
                }`} />
                <span>Loading project files...</span>
              </div>
            ) : files.length === 0 && stagedFiles.length === 0 ? (
              <div
                onClick={() => fileInputRef.current?.click()}
                className={`p-6 border border-dashed rounded-xl flex flex-col items-center justify-center gap-2 cursor-pointer transition-colors ${
                  isDark
                    ? 'bg-[#141414]/50 border-[#262626] hover:border-[#383838]'
                    : 'bg-[#E8E7E1]/50 border-[#CECDC4] hover:border-[#9E9E9B]'
                }`}
              >
                <Upload className={`w-6 h-6 ${isDark ? 'text-[#636366]' : 'text-[#8E8E93]'}`} />
                <span className={`text-xs ${isDark ? 'text-[#8E8E93]' : 'text-[#636366]'}`}>Click to add context files (.md, .txt, .pdf, code)</span>
              </div>
            ) : (
              <div className="space-y-1.5 max-h-48 overflow-y-auto pr-1">
                {/* Staged files for new project */}
                {stagedFiles.map((file, idx) => (
                  <div
                    key={`staged_${idx}`}
                    className={`flex items-center justify-between p-2.5 border rounded-xl group transition-colors ${
                      isDark ? 'bg-[#141414] border-[#383838]' : 'bg-[#E8E7E1] border-[#CECDC4]'
                    }`}
                  >
                    <div className="flex items-center gap-2.5 min-w-0 pr-2">
                      <FileText className={`w-4 h-4 shrink-0 ${isDark ? 'text-[#CECDC4]' : 'text-[#141414]'}`} />
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <p className={`text-xs font-medium truncate ${isDark ? 'text-[#CECDC4]' : 'text-[#141414]'}`}>{file.name}</p>
                          <span className={`text-[9px] font-mono uppercase px-1.5 py-0.2 rounded ${
                            isDark ? 'bg-[#262626] text-[#8E8E93]' : 'bg-[#DFDED8] text-[#636366]'
                          }`}>
                            Staged
                          </span>
                        </div>
                        <p className={`text-[10px] ${isDark ? 'text-[#636366]' : 'text-[#8E8E93]'}`}>{formatFileSize(file.size)}</p>
                      </div>
                    </div>

                    <button
                      type="button"
                      onClick={() => handleRemoveStagedFile(idx)}
                      title="Remove staged file"
                      className={`p-1 rounded transition-colors cursor-pointer ${
                        isDark ? 'text-[#8E8E93] hover:text-[#FF453A] hover:bg-[#FF453A]/10' : 'text-[#636366] hover:text-[#FF453A] hover:bg-[#FF453A]/10'
                      }`}
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}

                {/* Uploaded files */}
                {files.map((file) => (
                  <div
                    key={file.id}
                    className={`flex items-center justify-between p-2.5 border rounded-xl group transition-colors ${
                      isDark
                        ? 'bg-[#141414] border-[#262626] hover:border-[#383838]'
                        : 'bg-[#E8E7E1] border-[#CECDC4] hover:border-[#9E9E9B]'
                    }`}
                  >
                    <div className="flex items-center gap-2.5 min-w-0 pr-2">
                      <FileText className={`w-4 h-4 shrink-0 ${isDark ? 'text-[#8E8E93]' : 'text-[#636366]'}`} />
                      <div className="min-w-0">
                        <p className={`text-xs font-medium truncate ${isDark ? 'text-[#CECDC4]' : 'text-[#141414]'}`}>{file.name}</p>
                        <p className={`text-[10px] ${isDark ? 'text-[#636366]' : 'text-[#8E8E93]'}`}>{formatFileSize(file.size)}</p>
                      </div>
                    </div>

                    <button
                      onClick={() => handleDeleteFile(file)}
                      title="Remove file"
                      className={`p-1 rounded transition-colors cursor-pointer ${
                        isDark ? 'text-[#8E8E93] hover:text-[#FF453A] hover:bg-[#FF453A]/10' : 'text-[#636366] hover:text-[#FF453A] hover:bg-[#FF453A]/10'
                      }`}
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Delete Project Section (only if editing existing project) */}
          {project && onDelete && (
            <div className={`pt-4 border-t ${isDark ? 'border-[#262626]' : 'border-[#CECDC4]'}`}>
              {!showDeleteConfirm ? (
                <button
                  type="button"
                  onClick={() => setShowDeleteConfirm(true)}
                  className="w-full py-2.5 px-4 rounded-xl border border-[#FF453A]/20 bg-[#FF453A]/5 hover:bg-[#FF453A]/10 text-xs text-[#FF453A] font-medium flex items-center justify-center gap-2 transition-colors cursor-pointer"
                >
                  <Trash2 className="w-4 h-4" />
                  <span>Delete Project</span>
                </button>
              ) : (
                <div className={`p-3 border rounded-xl space-y-2 ${
                  isDark ? 'bg-[#1F1414] border-[#FF453A]/30' : 'bg-[#FDF2F2] border-[#FF453A]/30'
                }`}>
                  <div className="flex items-center gap-2 text-xs text-[#FF453A] font-semibold">
                    <AlertTriangle className="w-4 h-4 shrink-0" />
                    <span>Delete "{project.name}" and all its files?</span>
                  </div>
                  <p className={`text-[11px] ${isDark ? 'text-[#8E8E93]' : 'text-[#636366]'}`}>
                    Chats within this project will be converted to regular chats or removed. This action cannot be undone.
                  </p>
                  <div className="flex items-center justify-end gap-2 pt-1">
                    <button
                      type="button"
                      onClick={() => setShowDeleteConfirm(false)}
                      className={`px-3 py-1.5 rounded-lg text-xs transition-colors cursor-pointer ${
                        isDark ? 'bg-[#262626] text-[#CECDC4] hover:bg-[#333333]' : 'bg-[#DFDED8] text-[#141414] hover:bg-[#CECDC4]'
                      }`}
                    >
                      Cancel
                    </button>
                    <button
                      type="button"
                      disabled={isDeleting}
                      onClick={handleDeleteProject}
                      className="px-3 py-1.5 rounded-lg bg-[#FF453A] text-xs text-white font-medium hover:bg-[#FF453A]/90 transition-colors cursor-pointer"
                    >
                      {isDeleting ? 'Deleting...' : 'Confirm Delete'}
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className={`h-14 px-4 border-t flex items-center justify-end gap-2 shrink-0 ${
          isDark ? 'border-[#262626] bg-[#0A0A0A]' : 'border-[#CECDC4] bg-[#F0EFEA]'
        }`}>
          <button
            type="button"
            onClick={onClose}
            className={`px-4 py-2 rounded-xl text-xs font-medium transition-colors cursor-pointer ${
              isDark ? 'text-[#8E8E93] hover:text-[#CECDC4]' : 'text-[#636366] hover:text-[#141414]'
            }`}
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleSave}
            disabled={!name.trim() || isSaving}
            className={`px-5 py-2 rounded-xl font-sans text-xs font-semibold flex items-center gap-1.5 transition-all cursor-pointer ${
              savedSuccess
                ? 'bg-[#32D74B] text-black font-semibold'
                : !name.trim() || isSaving
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
            ) : isSaving ? (
              <span>Saving...</span>
            ) : (
              <span>{project ? 'Save Changes' : 'Create Project'}</span>
            )}
          </button>
        </div>
      </div>
    </div>
  )
}
