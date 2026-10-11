import React, { useMemo, useRef, useEffect, useState } from 'react'
import { Copy, Check } from 'lucide-react'
import {
  formatMarkdownForReading,
  type FormattedWord,
  type InlineSpan,
  type ReadingBlock,
} from '@/utils/readingFormatter'

interface FormattedMessageProps {
  content: string
  messageId: string
  activeHighlightRange?: [number, number] | null
  isDark?: boolean
  onSeek?: (plainText: string, charOffset: number) => void
}

export function FormattedMessage({
  content,
  messageId,
  activeHighlightRange = null,
  isDark = true,
  onSeek,
}: FormattedMessageProps) {
  const { plainText, blocks } = useMemo(() => {
    return formatMarkdownForReading(content)
  }, [content])

  const activeWordRef = useRef<HTMLSpanElement | null>(null)

  // Auto-scroll active word into view smoothly centered if it moves outside the visible area
  useEffect(() => {
    if (!activeHighlightRange || !activeWordRef.current) return

    const el = activeWordRef.current
    const rect = el.getBoundingClientRect()
    const viewportHeight = window.innerHeight || document.documentElement.clientHeight

    // Keep active word within comfortable reading band [135px, viewportHeight - 170px]
    // Top gradient & top bar: ~112px. Bottom floating controls & gradient: ~160px.
    const topLimit = 135
    const bottomLimit = viewportHeight - 170

    const isOutOfArea = rect.top < topLimit || rect.bottom > bottomLimit

    if (isOutOfArea) {
      el.scrollIntoView({
        behavior: 'smooth',
        block: 'center',
        inline: 'nearest',
      })
    }
  }, [activeHighlightRange])

  const textColor = isDark ? 'text-[#CECDC4]' : 'text-[#141414]'
  const mutedColor = isDark ? 'text-[#8E8E93]' : 'text-[#636366]'
  const quoteBorder = isDark ? 'border-[#8E8E93]' : 'border-[#A09F98]'

  return (
    <div
      className={`text-[17px] leading-[24px] font-sans ${textColor} select-text w-full transition-colors`}
      data-message-id={messageId}
    >
      {blocks.map((block, bIdx) => (
        <RenderBlock
          key={bIdx}
          block={block}
          plainText={plainText}
          activeHighlightRange={activeHighlightRange}
          isDark={isDark}
          textColor={textColor}
          mutedColor={mutedColor}
          quoteBorder={quoteBorder}
          activeWordRef={activeWordRef}
          onSeek={onSeek}
        />
      ))}
    </div>
  )
}

function RenderBlock({
  block,
  plainText,
  activeHighlightRange,
  isDark,
  textColor,
  mutedColor,
  quoteBorder,
  activeWordRef,
  onSeek,
}: {
  block: ReadingBlock
  plainText: string
  activeHighlightRange: [number, number] | null
  isDark: boolean
  textColor: string
  mutedColor: string
  quoteBorder: string
  activeWordRef: React.MutableRefObject<HTMLSpanElement | null>
  onSeek?: (plainText: string, charOffset: number) => void
}) {
  switch (block.type) {
    case 'header': {
      const level = block.level || 1
      const headerClass =
        level === 1
          ? `text-xl font-bold mt-5 mb-2.5 ${textColor} leading-snug`
          : level === 2
          ? `text-lg font-bold mt-4.5 mb-2 ${textColor} leading-snug`
          : `text-base font-semibold mt-4 mb-2 ${textColor} leading-snug`

      return (
        <div className={headerClass}>
          <RenderSpans
            spans={block.spans}
            plainText={plainText}
            activeHighlightRange={activeHighlightRange}
            isDark={isDark}
            activeWordRef={activeWordRef}
            onSeek={onSeek}
          />
        </div>
      )
    }

    case 'blockquote':
      return (
        <blockquote
          className={`border-l-2 ${quoteBorder} pl-3.5 my-3 italic ${mutedColor} leading-[22px]`}
        >
          <RenderSpans
            spans={block.spans}
            plainText={plainText}
            activeHighlightRange={activeHighlightRange}
            isDark={isDark}
            activeWordRef={activeWordRef}
            onSeek={onSeek}
          />
        </blockquote>
      )

    case 'bullet':
      return (
        <div className="flex items-start gap-2 my-1 leading-[24px]">
          <span className={`select-none font-bold ${mutedColor} shrink-0`}>•</span>
          <div className="flex-1">
            <RenderSpans
              spans={block.spans}
              plainText={plainText}
              activeHighlightRange={activeHighlightRange}
              isDark={isDark}
              activeWordRef={activeWordRef}
              onSeek={onSeek}
            />
          </div>
        </div>
      )

    case 'numbered':
      return (
        <div className="flex items-start gap-2 my-1 leading-[24px]">
          <span className={`select-none font-mono text-xs font-semibold ${mutedColor} shrink-0 pt-0.5`}>
            {block.numberPrefix || '1. '}
          </span>
          <div className="flex-1">
            <RenderSpans
              spans={block.spans}
              plainText={plainText}
              activeHighlightRange={activeHighlightRange}
              isDark={isDark}
              activeWordRef={activeWordRef}
              onSeek={onSeek}
            />
          </div>
        </div>
      )

    case 'codeBlock':
      return (
        <CodeBlockItem
          language={block.language || 'code'}
          rawCode={block.rawCode || ''}
          words={block.words}
          plainText={plainText}
          activeHighlightRange={activeHighlightRange}
          isDark={isDark}
          activeWordRef={activeWordRef}
          onSeek={onSeek}
        />
      )

    case 'paragraph':
    default:
      return (
        <p className="mb-3.5 last:mb-0 leading-[24px]">
          <RenderSpans
            spans={block.spans}
            plainText={plainText}
            activeHighlightRange={activeHighlightRange}
            isDark={isDark}
            activeWordRef={activeWordRef}
            onSeek={onSeek}
          />
        </p>
      )
  }
}

function RenderSpans({
  spans,
  plainText,
  activeHighlightRange,
  isDark,
  activeWordRef,
  onSeek,
}: {
  spans: InlineSpan[]
  plainText: string
  activeHighlightRange: [number, number] | null
  isDark: boolean
  activeWordRef: React.MutableRefObject<HTMLSpanElement | null>
  onSeek?: (plainText: string, charOffset: number) => void
}) {
  return (
    <>
      {spans.map((span, sIdx) => {
        let spanClasses = ''
        if (span.type === 'bold') spanClasses = 'font-bold'
        else if (span.type === 'italic') spanClasses = 'italic'
        else if (span.type === 'boldItalic') spanClasses = 'font-bold italic'
        else if (span.type === 'code') {
          spanClasses = `px-1.5 py-0.5 rounded font-mono text-xs border ${
            isDark
              ? 'bg-[#141414] border-[#262626] text-[#CECDC4]'
              : 'bg-[#E5E4DC] border-[#DCDBCF] text-[#141414]'
          }`
        }

        const content = (
          <span className={spanClasses}>
            {span.words.map((w, wIdx) => (
              <WordItem
                key={wIdx}
                word={w}
                plainText={plainText}
                activeHighlightRange={activeHighlightRange}
                isDark={isDark}
                activeWordRef={activeWordRef}
                onSeek={onSeek}
              />
            ))}
          </span>
        )

        if (span.type === 'link' && span.url) {
          return (
            <a
              key={sIdx}
              href={span.url}
              target="_blank"
              rel="noreferrer"
              className="underline underline-offset-2 hover:opacity-80 transition-opacity"
            >
              {content}
            </a>
          )
        }

        return <React.Fragment key={sIdx}>{content}</React.Fragment>
      })}
    </>
  )
}

function WordItem({
  word,
  plainText,
  activeHighlightRange,
  isDark,
  activeWordRef,
  onSeek,
}: {
  word: FormattedWord
  plainText: string
  activeHighlightRange: [number, number] | null
  isDark: boolean
  activeWordRef: React.MutableRefObject<HTMLSpanElement | null>
  onSeek?: (plainText: string, charOffset: number) => void
}) {
  // Check if this word overlaps the active TTS speech range
  const isActive =
    activeHighlightRange !== null &&
    ((activeHighlightRange[0] <= word.start && word.start < activeHighlightRange[1]) ||
      (word.start <= activeHighlightRange[0] && activeHighlightRange[0] < word.end) ||
      (Math.max(word.start, activeHighlightRange[0]) < Math.min(word.end, activeHighlightRange[1])))

  // Warm Brutalist inverted high-contrast highlight pill
  // Dark mode: #CECDC4 pill with black text
  // Light mode: #141414 pill with #F9F9F7 text
  // px-0.5 -mx-0.5 guarantees zero net layout impact, preventing text jitter
  const highlightClass = isDark
    ? 'bg-[#CECDC4] text-black font-medium rounded-[3px] px-0.5 -mx-0.5 shadow-xs'
    : 'bg-[#141414] text-[#F9F9F7] font-medium rounded-[3px] px-0.5 -mx-0.5 shadow-xs'

  const handleDoubleClick = (e: React.MouseEvent) => {
    e.stopPropagation()
    window.getSelection()?.removeAllRanges()
    onSeek?.(plainText, word.start)
    const target = e.currentTarget as HTMLElement
    target?.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' })
  }

  return (
    <span
      onDoubleClick={handleDoubleClick}
      title="Double click to read from here"
      className="cursor-pointer select-text inline"
    >
      {word.leadingPunct}
      <span
        ref={isActive ? (el) => { activeWordRef.current = el } : undefined}
        className={`transition-colors duration-100 ${
          isActive ? highlightClass : ''
        }`}
      >
        {word.text}
      </span>
      {word.trailingPunct}
      {word.trailingSpace}
    </span>
  )
}

function CodeBlockItem({
  language,
  rawCode,
  words,
  plainText,
  activeHighlightRange,
  isDark,
  activeWordRef,
  onSeek,
}: {
  language: string
  rawCode: string
  words: FormattedWord[]
  plainText: string
  activeHighlightRange: [number, number] | null
  isDark: boolean
  activeWordRef: React.MutableRefObject<HTMLSpanElement | null>
  onSeek?: (plainText: string, charOffset: number) => void
}) {
  const [copied, setCopied] = useState(false)

  const handleCopy = () => {
    navigator.clipboard.writeText(rawCode)
    setCopied(true)
    setTimeout(() => setCopied(false), 1800)
  }

  const containerBg = isDark ? 'bg-[#0A0A0A] border-[#262626]' : 'bg-[#F2F1EC] border-[#DCDBCF]'
  const headerBg = isDark ? 'bg-[#141414] border-[#262626]' : 'bg-[#E5E4DC] border-[#DCDBCF]'
  const headerText = isDark ? 'text-[#8E8E93]' : 'text-[#636366]'
  const codeColor = isDark ? 'text-[#CECDC4]' : 'text-[#141414]'

  return (
    <div className={`my-3.5 rounded-xl overflow-hidden border ${containerBg} shadow-xs`}>
      <div
        className={`${headerBg} px-3.5 py-1.5 flex items-center justify-between border-b text-[11px] font-mono ${headerText}`}
      >
        <span className="font-medium">{language || 'code'}</span>
        <button
          onClick={handleCopy}
          className="hover:text-white transition-colors flex items-center gap-1 cursor-pointer"
          title="Copy code"
        >
          {copied ? <Check className="w-3 h-3 text-[#30D158]" /> : <Copy className="w-3 h-3" />}
          <span>{copied ? 'Copied' : 'Copy'}</span>
        </button>
      </div>
      <pre className={`p-3.5 text-xs overflow-x-auto font-mono ${codeColor} leading-[22px] select-text`}>
        <code>
          {words.map((w, wIdx) => (
            <WordItem
              key={wIdx}
              word={w}
              plainText={plainText}
              activeHighlightRange={activeHighlightRange}
              isDark={isDark}
              activeWordRef={activeWordRef}
              onSeek={onSeek}
            />
          ))}
        </code>
      </pre>
    </div>
  )
}
