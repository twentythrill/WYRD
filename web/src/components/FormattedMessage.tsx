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

  const textColor = isDark ? 'text-[#CECDC4]' : 'text-[#100F0F]'
  const mutedColor = isDark ? 'text-[#8A8983]' : 'text-[#666460]'
  const quoteBorder = isDark ? 'border-[#8A8983]' : 'border-[#666460]'

  return (
    <div
      className={`text-[17px] leading-[25px] font-sans ${textColor} select-text w-full transition-colors`}
      data-message-id={messageId}
      onDoubleClick={handleContainerDoubleClick}
    >
      {blocks.map((block, bIdx) => (
        <RenderBlock
          key={bIdx}
          block={block}
          prevBlockType={bIdx > 0 ? blocks[bIdx - 1].type : undefined}
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
  prevBlockType,
  activeHighlightRange,
  isDark,
  textColor,
  mutedColor,
  quoteBorder,
  activeWordRef,
}: {
  block: ReadingBlock
  prevBlockType?: ReadingBlock['type']
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
          ? `text-[22px] font-bold mt-6 mb-2.5 first:mt-0 ${textColor} leading-tight`
          : level === 2
          ? `text-[19px] font-bold mt-5 mb-2 first:mt-0 ${textColor} leading-snug`
          : `text-[17px] font-semibold mt-4 mb-1.5 first:mt-0 ${textColor} leading-snug`

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

    case 'horizontalRule':
      return (
        <hr
          className={`my-5 border-t border-wyrd-border`}
        />
      )

    case 'blockquote':
      return (
        <blockquote
          className={`border-l-2 ${quoteBorder} pl-3.5 my-3.5 italic ${mutedColor} leading-[24px]`}
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
        <div
          className={`flex items-start gap-2.5 leading-[25px] ${
            prevBlockType === 'bullet' ? 'mt-2.5 mb-1' : 'mt-2 mb-1'
          }`}
        >
          <span className={`select-none font-bold ${mutedColor} shrink-0 text-sm pt-0.5`}>•</span>
          <div className="flex-1 min-w-0">
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
        <div
          className={`flex items-start gap-2.5 leading-[25px] ${
            prevBlockType === 'numbered' ? 'mt-2.5 mb-1' : 'mt-2 mb-1'
          }`}
        >
          <span className={`select-none font-mono text-xs font-semibold ${mutedColor} shrink-0 pt-1`}>
            {block.numberPrefix || '1. '}
          </span>
          <div className="flex-1 min-w-0">
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
        <p className="mb-3.5 last:mb-0 leading-[25px]">
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
        else if (span.type === 'strike') spanClasses = 'line-through opacity-75'
        else if (span.type === 'code') {
          spanClasses = `px-1.5 py-0.5 rounded-[4px] font-mono text-[14.5px] border ${
            isDark
              ? 'bg-[#1F1E1D] border-[#2E2D2C] text-[#CECDC4]'
              : 'bg-[#EBE9DF] border-[#D8D6CD] text-[#100F0F]'
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
  // Dark mode: ink (#CECDC4) pill with surface text
  // Light mode: ink (#100F0F) pill with surface text
  // px-0.5 -mx-0.5 guarantees zero net layout impact, preventing text jitter
  const highlightClass = isDark
    ? 'bg-[#CECDC4] text-[#100F0F] font-medium rounded-[3px] px-0.5 -mx-0.5 shadow-xs'
    : 'bg-[#100F0F] text-[#FEFCF1] font-medium rounded-[3px] px-0.5 -mx-0.5 shadow-xs'

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

  const containerBg = isDark ? 'bg-[#100F0F] border-[#2E2D2C]' : 'bg-[#FEFCF1] border-[#D8D6CD]'
  const headerBg = isDark ? 'bg-[#1F1E1D] border-[#2E2D2C]' : 'bg-[#EBE9DF] border-[#D8D6CD]'
  const headerText = isDark ? 'text-[#8A8983]' : 'text-[#666460]'
  const codeColor = isDark ? 'text-[#CECDC4]' : 'text-[#100F0F]'

  return (
    <div className={`my-3.5 rounded-xl overflow-hidden border ${containerBg} shadow-xs`}>
      <div
        className={`${headerBg} px-3.5 py-1.5 flex items-center justify-between border-b text-[11px] font-mono ${headerText}`}
      >
        <span className="font-medium">{language || 'code'}</span>
        <button
          onClick={handleCopy}
          className="hover:text-wyrd-text-primary transition-colors flex items-center gap-1 cursor-pointer"
          title="Copy code"
        >
          {copied ? <Check className="w-3 h-3 text-wyrd-text-primary" /> : <Copy className="w-3 h-3" />}
          <span>{copied ? 'Copied' : 'Copy'}</span>
        </button>
      </div>
      <pre className={`p-3.5 text-[14.5px] font-mono ${codeColor} leading-[22px] select-text whitespace-pre-wrap break-words [overflow-wrap:anywhere]`}>
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
  const containerBg = isDark ? 'bg-[#1F1E1D] border-[#2E2D2C]' : 'bg-[#EBE9DF] border-[#D8D6CD]'
  const headerBg = isDark ? 'bg-[#1F1E1D] border-[#2E2D2C]' : 'bg-[#E1E0D6] border-[#D8D6CD]'
  const headerTextColor = isDark ? 'text-[#CECDC4]' : 'text-[#100F0F]'
  const rowBorder = isDark ? 'border-[#2E2D2C]' : 'border-[#D8D6CD]'
  const hoverRowBg = isDark ? 'hover:bg-[#1F1E1D]/50' : 'hover:bg-[#E1E0D6]/50'

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
                    className={`px-3.5 py-2.5 font-sans text-xs font-bold uppercase tracking-wider border-b ${headerTextColor} ${getAlignClass(
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
              className={`border-b ${rowBorder} last:border-b-0 ${hoverRowBg} ${
                rIdx % 2 === 1 ? (isDark ? 'bg-[#100F0F]/60' : 'bg-[#EBE9DF]/60') : 'bg-transparent'
              } transition-colors`}
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
