import React, { useMemo, useRef, useEffect, useState } from 'react'
import { Copy, Check } from 'lucide-react'
import {
  formatMarkdownForReading,
  type FormattedWord,
  type InlineSpan,
  type ReadingBlock,
  type TableCell,
  type TableRow,
  type ColumnAlignment,
} from '@/utils/readingFormatter'

interface FormattedMessageProps {
  content: string
  messageId: string
  activeHighlightRange?: [number, number] | null
  isDark?: boolean
  onSeek?: (plainText: string, charOffset: number) => void
}

export const FormattedMessage = React.memo(function FormattedMessage({
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

  const handleContainerDoubleClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const target = (e.target as HTMLElement | null)?.closest<HTMLElement>('[data-word-start]')
    if (!target) return
    const rawStart = target.getAttribute('data-word-start')
    const startOffset = rawStart !== null ? parseInt(rawStart, 10) : NaN
    if (Number.isNaN(startOffset)) return

    e.stopPropagation()
    window.getSelection()?.removeAllRanges()
    onSeek?.(plainText, startOffset)
    target.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' })
  }

  const textColor = isDark ? 'text-[#CECDC4]' : 'text-[#141414]'
  const mutedColor = isDark ? 'text-[#8E8E93]' : 'text-[#636366]'
  const quoteBorder = isDark ? 'border-[#8E8E93]' : 'border-[#636366]'

  return (
    <div
      className={`text-[17px] leading-[24px] font-sans ${textColor} select-text w-full transition-colors`}
      data-message-id={messageId}
      onDoubleClick={handleContainerDoubleClick}
    >
      {blocks.map((block, bIdx) => (
        <RenderBlock
          key={bIdx}
          block={block}
          activeHighlightRange={activeHighlightRange}
          isDark={isDark}
          textColor={textColor}
          mutedColor={mutedColor}
          quoteBorder={quoteBorder}
          activeWordRef={activeWordRef}
        />
      ))}
    </div>
  )
})

function RenderBlock({
  block,
  activeHighlightRange,
  isDark,
  textColor,
  mutedColor,
  quoteBorder,
  activeWordRef,
}: {
  block: ReadingBlock
  activeHighlightRange: [number, number] | null
  isDark: boolean
  textColor: string
  mutedColor: string
  quoteBorder: string
  activeWordRef: React.MutableRefObject<HTMLSpanElement | null>
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
            activeHighlightRange={activeHighlightRange}
            isDark={isDark}
            activeWordRef={activeWordRef}
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
            activeHighlightRange={activeHighlightRange}
            isDark={isDark}
            activeWordRef={activeWordRef}
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
              activeHighlightRange={activeHighlightRange}
              isDark={isDark}
              activeWordRef={activeWordRef}
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
              activeHighlightRange={activeHighlightRange}
              isDark={isDark}
              activeWordRef={activeWordRef}
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
          activeHighlightRange={activeHighlightRange}
          isDark={isDark}
          activeWordRef={activeWordRef}
        />
      )

    case 'table':
      return (
        <TableBlockItem
          headers={block.tableHeaders || []}
          alignments={block.tableAlignments || []}
          rows={block.tableRows || []}
          activeHighlightRange={activeHighlightRange}
          isDark={isDark}
          activeWordRef={activeWordRef}
        />
      )

    case 'paragraph':
    default:
      return (
        <p className="mb-3.5 last:mb-0 leading-[24px]">
          <RenderSpans
            spans={block.spans}
            activeHighlightRange={activeHighlightRange}
            isDark={isDark}
            activeWordRef={activeWordRef}
          />
        </p>
      )
  }
}

function RenderSpans({
  spans,
  activeHighlightRange,
  isDark,
  activeWordRef,
}: {
  spans: InlineSpan[]
  activeHighlightRange: [number, number] | null
  isDark: boolean
  activeWordRef: React.MutableRefObject<HTMLSpanElement | null>
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
              : 'bg-[#E8E7E1] border-[#CECDC4] text-[#141414]'
          }`
        }

        const content = (
          <span className={spanClasses}>
            {span.words.map((w, wIdx) => (
              <WordItem
                key={wIdx}
                word={w}
                activeHighlightRange={activeHighlightRange}
                isDark={isDark}
                activeWordRef={activeWordRef}
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
  activeHighlightRange,
  isDark,
  activeWordRef,
}: {
  word: FormattedWord
  activeHighlightRange: [number, number] | null
  isDark: boolean
  activeWordRef: React.MutableRefObject<HTMLSpanElement | null>
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

  return (
    <span
      data-word-start={word.start}
      title="Double click to read from here"
      className="cursor-pointer select-text inline break-words [overflow-wrap:anywhere]"
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
  activeHighlightRange,
  isDark,
  activeWordRef,
}: {
  language: string
  rawCode: string
  words: FormattedWord[]
  activeHighlightRange: [number, number] | null
  isDark: boolean
  activeWordRef: React.MutableRefObject<HTMLSpanElement | null>
}) {
  const [copied, setCopied] = useState(false)

  const handleCopy = () => {
    navigator.clipboard.writeText(rawCode)
    setCopied(true)
    setTimeout(() => setCopied(false), 1800)
  }

  const containerBg = isDark ? 'bg-[#0A0A0A] border-[#262626]' : 'bg-[#F0EFEA] border-[#CECDC4]'
  const headerBg = isDark ? 'bg-[#141414] border-[#262626]' : 'bg-[#E8E7E1] border-[#CECDC4]'
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
          {copied ? <Check className="w-3 h-3 text-[#32D74B]" /> : <Copy className="w-3 h-3" />}
          <span>{copied ? 'Copied' : 'Copy'}</span>
        </button>
      </div>
      <pre className={`p-3.5 text-[17px] font-mono ${codeColor} leading-[24px] select-text whitespace-pre-wrap break-words [overflow-wrap:anywhere]`}>
        <code className="whitespace-pre-wrap break-words [overflow-wrap:anywhere]">
          {words.map((w, wIdx) => (
            <WordItem
              key={wIdx}
              word={w}
              activeHighlightRange={activeHighlightRange}
              isDark={isDark}
              activeWordRef={activeWordRef}
            />
          ))}
        </code>
      </pre>
    </div>
  )
}

function TableBlockItem({
  headers,
  alignments,
  rows,
  activeHighlightRange,
  isDark,
  activeWordRef,
}: {
  headers: TableCell[]
  alignments: ColumnAlignment[]
  rows: TableRow[]
  activeHighlightRange: [number, number] | null
  isDark: boolean
  activeWordRef: React.MutableRefObject<HTMLSpanElement | null>
}) {
  const containerBg = isDark ? 'bg-[#141414] border-[#262626]' : 'bg-[#E8E7E1] border-[#CECDC4]'
  const headerBg = isDark ? 'bg-[#1A1A1A] border-[#262626]' : 'bg-[#DFDED8] border-[#CECDC4]'
  const headerTextColor = isDark ? 'text-[#CECDC4]' : 'text-[#141414]'
  const rowBorder = isDark ? 'border-[#262626]' : 'border-[#CECDC4]'
  const hoverRowBg = isDark ? 'hover:bg-[#1A1A1A]/50' : 'hover:bg-[#DFDED8]/50'

  const getAlignClass = (align?: ColumnAlignment) => {
    switch (align) {
      case 'center':
        return 'text-center'
      case 'right':
        return 'text-right'
      case 'left':
      default:
        return 'text-left'
    }
  }

  return (
    <div className={`my-4 w-full overflow-x-auto rounded-xl border ${containerBg} shadow-xs`}>
      <table className="w-full border-collapse text-xs md:text-sm font-sans select-text">
        {headers.length > 0 && (
          <thead>
            <tr className={headerBg}>
              {headers.map((h, hIdx) => {
                const align = alignments[hIdx] || 'default'
                return (
                  <th
                    key={hIdx}
                    className={`px-3.5 py-2.5 font-mono text-[11px] font-semibold uppercase tracking-wider border-b ${headerTextColor} ${getAlignClass(
                      align
                    )}`}
                  >
                    <RenderSpans
                      spans={h.spans}
                      activeHighlightRange={activeHighlightRange}
                      isDark={isDark}
                      activeWordRef={activeWordRef}
                    />
                  </th>
                )
              })}
            </tr>
          </thead>
        )}
        <tbody>
          {rows.map((row, rIdx) => (
            <tr
              key={rIdx}
              className={`border-b ${rowBorder} last:border-b-0 ${hoverRowBg} transition-colors`}
            >
              {row.cells.map((cell, cIdx) => {
                const align = alignments[cIdx] || 'default'
                return (
                  <td
                    key={cIdx}
                    className={`px-3.5 py-2.5 align-top leading-[22px] ${getAlignClass(align)}`}
                  >
                    <RenderSpans
                      spans={cell.spans}
                      activeHighlightRange={activeHighlightRange}
                      isDark={isDark}
                      activeWordRef={activeWordRef}
                    />
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
