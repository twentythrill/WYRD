import React, { useState, useEffect, useRef } from 'react'
import { ArrowLeft, Check, Trash2, Upload, FileText, X, AlertTriangle, Plus } from 'lucide-react'
import { type Project, type ProjectFile } from '@/lib/supabase'
import { dbService } from '@/services/dbService'

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
  onClose: () => void
  onSave: (name: string, description: string, systemPrompt: string, color: string) => Promise<Project | null>
  onDelete?: (projectId: string) => Promise<boolean>
}

async function extractTextFromFile(file: File): Promise<string | undefined> {
  try {
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
      <div className="w-full max-w-lg bg-[#0A0A0A] border border-[#262626] rounded-2xl shadow-2xl flex flex-col max-h-[90vh] overflow-hidden select-none">
        {/* Header */}
        <div className="h-14 px-4 border-b border-[#262626] flex items-center justify-between bg-[#0A0A0A] shrink-0">
          <div className="flex items-center gap-3">
            <button
              onClick={onClose}
              className="p-1.5 -ml-1 text-[#CECDC4] hover:text-white rounded-lg transition-colors cursor-pointer"
            >
              <ArrowLeft className="w-6 h-6 stroke-[2]" />
            </button>
            <span className="font-sans font-bold text-base text-[#CECDC4]">
              {project ? 'Project Settings' : 'New Project'}
            </span>
          </div>

          <button
            onClick={handleSave}
            disabled={!name.trim() || isSaving}
            className={`px-4 py-1.5 rounded-xl font-sans text-xs font-semibold flex items-center gap-1.5 transition-all cursor-pointer ${
              savedSuccess
                ? 'bg-emerald-600 text-white'
                : !name.trim() || isSaving
                ? 'bg-[#1F1F1F] text-[#636366] cursor-not-allowed'
                : 'bg-[#CECDC4] text-black hover:bg-white active:scale-95'
            }`}
          >
            {savedSuccess ? (
              <>
                <Check className="w-3.5 h-3.5" />
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
            <div className="p-3 bg-[#1F1414] border border-[#FF453A]/40 rounded-xl text-xs text-[#FF453A] flex items-center justify-between gap-2">
              <div className="flex items-center gap-2 min-w-0">
                <AlertTriangle className="w-4 h-4 shrink-0" />
                <span className="truncate">{errorMessage}</span>
              </div>
              <button
                type="button"
                onClick={() => setErrorMessage(null)}
                className="p-1 hover:text-white transition-colors cursor-pointer"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          )}

          {/* Project Name */}
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-[#CECDC4] flex items-center gap-1">
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
              className="w-full bg-[#141414] border border-[#262626] rounded-xl text-sm text-[#CECDC4] placeholder-[#636366] focus:border-[#CECDC4] focus:outline-none p-3 transition-colors"
            />
          </div>

          {/* Description */}
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-[#8E8E93]">
              Description (Optional)
            </label>
            <input
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Brief description of the project purpose..."
              className="w-full bg-[#141414] border border-[#262626] rounded-xl text-sm text-[#CECDC4] placeholder-[#636366] focus:border-[#CECDC4] focus:outline-none p-3 transition-colors"
            />
          </div>

          {/* Project Color */}
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-[#CECDC4]">
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
                        ? 'border-2 border-white'
                        : 'border-2 border-transparent hover:border-[#2C2C2E]'
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
              <label className="text-xs font-medium text-[#CECDC4]">
                Project System Instructions
              </label>
              <span className="text-[10px] font-sans text-amber-400/90 bg-amber-400/10 px-2 py-0.5 rounded-full border border-amber-400/20">
                Overrides Global Prompt
              </span>
            </div>
            <textarea
              value={systemPrompt}
              onChange={(e) => setSystemPrompt(e.target.value)}
              rows={5}
              placeholder="e.g. You are a senior frontend architect specializing in React and Tailwind. Always format responses with concise TypeScript code snippets and verify edge cases..."
              className="w-full bg-[#141414] border border-[#262626] rounded-xl text-sm text-[#CECDC4] placeholder-[#636366] focus:border-[#CECDC4] focus:outline-none p-3 resize-none font-mono text-xs transition-colors"
            />
          </div>

          {/* Source Files / Knowledge Base */}
          <div className="space-y-2 pt-2 border-t border-[#262626]">
            <div className="flex items-center justify-between">
              <div>
                <h4 className="text-xs font-semibold text-[#CECDC4]">Knowledge Base Files</h4>
                <p className="text-[11px] text-[#8E8E93]">
                  Attach Markdown, text, PDFs, or code files for contextual awareness.
                </p>
              </div>

              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={isUploading}
                className="px-2.5 py-1 rounded-lg bg-[#1F1F1F] hover:bg-[#262626] border border-[#262626] text-xs font-medium text-[#CECDC4] flex items-center gap-1.5 transition-colors cursor-pointer"
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
              <div className="p-4 flex items-center justify-center gap-2 text-xs text-[#8E8E93]">
                <div className="w-3.5 h-3.5 border-2 border-[#262626] border-t-[#CECDC4] rounded-full animate-spin" />
                <span>Loading project files...</span>
              </div>
            ) : files.length === 0 && stagedFiles.length === 0 ? (
              <div
                onClick={() => fileInputRef.current?.click()}
                className="p-6 bg-[#141414]/50 border border-dashed border-[#262626] hover:border-[#383838] rounded-xl flex flex-col items-center justify-center gap-2 cursor-pointer transition-colors"
              >
                <Upload className="w-6 h-6 text-[#636366]" />
                <span className="text-xs text-[#8E8E93]">Click to add context files (.md, .txt, .pdf, code)</span>
              </div>
            ) : (
              <div className="space-y-1.5 max-h-48 overflow-y-auto pr-1">
                {/* Staged files for new project */}
                {stagedFiles.map((file, idx) => (
                  <div
                    key={`staged_${idx}`}
                    className="flex items-center justify-between p-2.5 bg-[#141414] border border-[#383838] rounded-xl group transition-colors"
                  >
                    <div className="flex items-center gap-2.5 min-w-0 pr-2">
                      <FileText className="w-4 h-4 text-[#CECDC4] shrink-0" />
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <p className="text-xs font-medium text-[#CECDC4] truncate">{file.name}</p>
                          <span className="text-[9px] font-mono uppercase px-1.5 py-0.2 bg-[#262626] text-[#8E8E93] rounded">
                            Staged
                          </span>
                        </div>
                        <p className="text-[10px] text-[#636366]">{formatFileSize(file.size)}</p>
                      </div>
                    </div>

                    <button
                      type="button"
                      onClick={() => handleRemoveStagedFile(idx)}
                      title="Remove staged file"
                      className="p-1 rounded text-[#8E8E93] hover:text-[#FF453A] hover:bg-[#FF453A]/10 transition-colors cursor-pointer"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}

                {/* Uploaded files */}
                {files.map((file) => (
                  <div
                    key={file.id}
                    className="flex items-center justify-between p-2.5 bg-[#141414] border border-[#262626] rounded-xl group hover:border-[#383838] transition-colors"
                  >
                    <div className="flex items-center gap-2.5 min-w-0 pr-2">
                      <FileText className="w-4 h-4 text-[#8E8E93] shrink-0" />
                      <div className="min-w-0">
                        <p className="text-xs font-medium text-[#CECDC4] truncate">{file.name}</p>
                        <p className="text-[10px] text-[#636366]">{formatFileSize(file.size)}</p>
                      </div>
                    </div>

                    <button
                      onClick={() => handleDeleteFile(file)}
                      title="Remove file"
                      className="p-1 rounded text-[#8E8E93] hover:text-[#FF453A] hover:bg-[#FF453A]/10 transition-colors cursor-pointer"
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
            <div className="pt-4 border-t border-[#262626]">
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
                <div className="p-3 bg-[#1F1414] border border-[#FF453A]/30 rounded-xl space-y-2">
                  <div className="flex items-center gap-2 text-xs text-[#FF453A] font-semibold">
                    <AlertTriangle className="w-4 h-4 shrink-0" />
                    <span>Delete "{project.name}" and all its files?</span>
                  </div>
                  <p className="text-[11px] text-[#8E8E93]">
                    Chats within this project will be converted to regular chats or removed. This action cannot be undone.
                  </p>
                  <div className="flex items-center justify-end gap-2 pt-1">
                    <button
                      type="button"
                      onClick={() => setShowDeleteConfirm(false)}
                      className="px-3 py-1.5 rounded-lg bg-[#262626] text-xs text-[#CECDC4] hover:bg-[#333333] transition-colors cursor-pointer"
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
        <div className="h-14 px-4 border-t border-[#262626] flex items-center justify-end gap-2 bg-[#0A0A0A] shrink-0">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-xl text-xs font-medium text-[#8E8E93] hover:text-[#CECDC4] transition-colors cursor-pointer"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleSave}
            disabled={!name.trim() || isSaving}
            className={`px-5 py-2 rounded-xl font-sans text-xs font-semibold flex items-center gap-1.5 transition-all cursor-pointer ${
              savedSuccess
                ? 'bg-emerald-600 text-white'
                : !name.trim() || isSaving
                ? 'bg-[#1F1F1F] text-[#636366] cursor-not-allowed'
                : 'bg-[#CECDC4] text-black hover:bg-white active:scale-95'
            }`}
          >
            {savedSuccess ? (
              <>
                <Check className="w-3.5 h-3.5" />
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
