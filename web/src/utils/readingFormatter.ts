/**
 * Reading formatter for assistant messages in WYRD Web client.
 * Generates an exact 1:1 mapped plainText and structured blocks with character offsets
 * so that TTS word boundaries (onboundary event) can highlight words synchronously
 * with zero layout shifts and zero offset drift.
 *
 * Matches Android WYRD's formatMarkdownForReading & getHighlightedAnnotatedString.
 */

export interface FormattedWord {
  leadingPunct: string
  text: string
  trailingPunct: string
  start: number // 0-based character start in plainText
  end: number   // 0-based character end in plainText
  trailingSpace: string
}

export type InlineTokenType = 'text' | 'bold' | 'italic' | 'boldItalic' | 'code' | 'link'

export interface InlineSpan {
  type: InlineTokenType
  text: string
  url?: string
  words: FormattedWord[]
}

export type ColumnAlignment = 'left' | 'center' | 'right' | 'default'

export interface TableCell {
  spans: InlineSpan[]
  words: FormattedWord[]
  rawText: string
}

export interface TableRow {
  cells: TableCell[]
}

export type ReadingBlockType =
  | 'header'
  | 'paragraph'
  | 'blockquote'
  | 'bullet'
  | 'numbered'
  | 'codeBlock'
  | 'table'

export interface ReadingBlock {
  type: ReadingBlockType
  level?: number // 1, 2, 3 for headers
  numberPrefix?: string // "1. " for numbered list
  language?: string // for code block
  rawCode?: string // code content for copy button
  tableHeaders?: TableCell[]
  tableAlignments?: ColumnAlignment[]
  tableRows?: TableRow[]
  spans: InlineSpan[]
  words: FormattedWord[]
}

export interface FormattedReadingContent {
  plainText: string
  blocks: ReadingBlock[]
}

const INITIAL_WHITESPACE_REGEX = /^(\s+)/
const WORD_PUNCT_REGEX = /^([^\p{L}\p{N}]*)([\p{L}\p{N}'’\-_]+)([^\p{L}\p{N}]*)$/u
const SAFE_LINK_PROTOCOL_REGEX = /^(?:https?:\/\/|mailto:)/i

/**
 * Splits text into words while isolating leading/trailing punctuation and recording
 * exact character offsets relative to baseOffset in plainText.
 */
export function tokenizeTextIntoWords(text: string, baseOffset: number): FormattedWord[] {
  const words: FormattedWord[] = []
  const chunkRegex = /(\S+)(\s*)/g
  let chunkMatch: RegExpExecArray | null

  const initialWhitespaceMatch = text.match(INITIAL_WHITESPACE_REGEX)
  const initialWhitespace = initialWhitespaceMatch ? initialWhitespaceMatch[1] : ''
  let isFirst = true

  while ((chunkMatch = chunkRegex.exec(text)) !== null) {
    const rawChunk = chunkMatch[1]
    const trailingSpace = chunkMatch[2]
    const chunkStart = baseOffset + chunkMatch.index

    // Separate leading punctuation, core word, and trailing punctuation
    // Core word allows unicode letters, digits, apostrophes, hyphens
    const wordMatch = rawChunk.match(WORD_PUNCT_REGEX)

    if (wordMatch) {
      const punct = wordMatch[1]
      const leadingPunct = (isFirst ? initialWhitespace : '') + punct
      const coreWord = wordMatch[2]
      const trailingPunct = wordMatch[3]

      const start = chunkStart + punct.length
      const end = start + coreWord.length

      words.push({
        leadingPunct,
        text: coreWord,
        trailingPunct,
        start,
        end,
        trailingSpace,
      })
    } else {
      // Symbols or numbers only
      const leadingPunct = isFirst ? initialWhitespace : ''
      words.push({
        leadingPunct,
        text: rawChunk,
        trailingPunct: '',
        start: chunkStart,
        end: chunkStart + rawChunk.length,
        trailingSpace,
      })
    }
    isFirst = false
  }

  // Handle case where text consists entirely of whitespace
  if (words.length === 0 && text.length > 0) {
    words.push({
      leadingPunct: text,
      text: '',
      trailingPunct: '',
      start: baseOffset,
      end: baseOffset,
      trailingSpace: '',
    })
  }

  return words
}

/**
 * Parses inline formatting (bold, italic, inline code, links) while maintaining
 * exact character alignment with plainAcc.
 */
function parseInlineSpans(
  line: string,
  baseOffset: number
): { spans: InlineSpan[]; plainAcc: string } {
  const spans: InlineSpan[] = []
  let cursor = 0
  const len = line.length
  let currentOffset = baseOffset
  let plainAcc = ''

  while (cursor < len) {
    // Inline code: `code`
    if (line[cursor] === '`') {
      const end = line.indexOf('`', cursor + 1)
      if (end !== -1) {
        const codeText = line.substring(cursor + 1, end)
        const words = tokenizeTextIntoWords(codeText, currentOffset)
        spans.push({
          type: 'code',
          text: codeText,
          words,
        })
        currentOffset += codeText.length
        plainAcc += codeText
        cursor = end + 1
        continue
      }
    }

    // Bold + Italic: ***text***
    if (cursor + 2 < len && line.substring(cursor, cursor + 3) === '***') {
      const end = line.indexOf('***', cursor + 3)
      if (end !== -1) {
        const boldItalicText = line.substring(cursor + 3, end)
        const words = tokenizeTextIntoWords(boldItalicText, currentOffset)
        spans.push({
          type: 'boldItalic',
          text: boldItalicText,
          words,
        })
        currentOffset += boldItalicText.length
        plainAcc += boldItalicText
        cursor = end + 3
        continue
      }
    }

    // Bold: **text** or __text__
    if (
      cursor + 1 < len &&
      ((line[cursor] === '*' && line[cursor + 1] === '*') ||
        (line[cursor] === '_' && line[cursor + 1] === '_'))
    ) {
      const marker = line.substring(cursor, cursor + 2)
      const end = line.indexOf(marker, cursor + 2)
      if (end !== -1) {
        const boldText = line.substring(cursor + 2, end)
        const words = tokenizeTextIntoWords(boldText, currentOffset)
        spans.push({
          type: 'bold',
          text: boldText,
          words,
        })
        currentOffset += boldText.length
        plainAcc += boldText
        cursor = end + 2
        continue
      }
    }

    // Italic: *text* or _text_
    if (
      (line[cursor] === '*' || line[cursor] === '_') &&
      cursor + 1 < len &&
      line[cursor + 1] !== ' ' &&
      line[cursor + 1] !== line[cursor]
    ) {
      const marker = line[cursor]
      const end = line.indexOf(marker, cursor + 1)
      if (end !== -1 && end > cursor + 1) {
        const italicText = line.substring(cursor + 1, end)
        const words = tokenizeTextIntoWords(italicText, currentOffset)
        spans.push({
          type: 'italic',
          text: italicText,
          words,
        })
        currentOffset += italicText.length
        plainAcc += italicText
        cursor = end + 1
        continue
      }
    }

    // Markdown link: [label](url)
    if (line[cursor] === '[') {
      const closingBracket = line.indexOf(']', cursor + 1)
      if (
        closingBracket !== -1 &&
        closingBracket + 1 < len &&
        line[closingBracket + 1] === '('
      ) {
        const closingParen = line.indexOf(')', closingBracket + 2)
        if (closingParen !== -1) {
          const label = line.substring(cursor + 1, closingBracket)
          const url = line.substring(closingBracket + 2, closingParen).trim()
          const words = tokenizeTextIntoWords(label, currentOffset)
          const isSafeUrl = SAFE_LINK_PROTOCOL_REGEX.test(url)
          spans.push({
            type: isSafeUrl ? 'link' : 'text',
            text: label,
            url: isSafeUrl ? url : undefined,
            words,
          })
          currentOffset += label.length
          plainAcc += label
          cursor = closingParen + 1
          continue
        }
      }
    }

    // Find next special delimiter starting after cursor
    let nextSpecial = len
    for (let i = cursor + 1; i < len; i++) {
      const c = line[i]
      if (c === '`' || c === '*' || c === '_' || c === '[') {
        nextSpecial = i
        break
      }
    }

    const plainChunk = line.substring(cursor, nextSpecial)
    if (plainChunk) {
      const words = tokenizeTextIntoWords(plainChunk, currentOffset)
      spans.push({
        type: 'text',
        text: plainChunk,
        words,
      })
      currentOffset += plainChunk.length
      plainAcc += plainChunk
      cursor = nextSpecial
    } else {
      cursor++
    }
  }

  return { spans, plainAcc }
}

function parseTableCells(line: string): string[] {
  let trimmed = line.trim()
  if (trimmed.startsWith('|')) {
    trimmed = trimmed.substring(1)
  }
  if (trimmed.endsWith('|')) {
    trimmed = trimmed.substring(0, trimmed.length - 1)
  }
  return trimmed.split('|').map((c) => c.trim())
}

function isTableSeparator(line: string): boolean {
  const trimmed = line.trim()
  if (!trimmed.includes('-') || !trimmed.includes('|')) return false
  const cells = parseTableCells(trimmed)
  if (cells.length === 0) return false
  return cells.every((cell) => /^:?-{2,}:?$/.test(cell.trim()) || /^:?-+:?$/.test(cell.trim()))
}

function getColumnAlignment(sep?: string): ColumnAlignment {
  if (!sep) return 'default'
  const trimmed = sep.trim()
  const startsColon = trimmed.startsWith(':')
  const endsColon = trimmed.endsWith(':')
  if (startsColon && endsColon) return 'center'
  if (endsColon) return 'right'
  if (startsColon) return 'left'
  return 'default'
}

/**
 * Formats a markdown string into structured blocks with word token offsets and an exact
 * matching plainText string suitable for native TTS playback.
 */
export function formatMarkdownForReading(markdown: string): FormattedReadingContent {
  if (!markdown) {
    return { plainText: '', blocks: [] }
  }

  const lines = markdown.split('\n')
  const blocks: ReadingBlock[] = []
  let plainText = ''
  let inCodeBlock = false
  let codeBuffer: string[] = []
  let codeLang = ''

  for (let i = 0; i < lines.length; i++) {
    const rawLine = lines[i]
    const trimmed = rawLine.trim()

    // Code block toggle: ```lang
    if (trimmed.startsWith('```')) {
      if (!inCodeBlock) {
        inCodeBlock = true
        codeLang = trimmed.slice(3).trim()
        codeBuffer = []
      } else {
        inCodeBlock = false
        const rawCode = codeBuffer.join('\n')
        const startOffset = plainText.length
        const words = tokenizeTextIntoWords(rawCode, startOffset)
        blocks.push({
          type: 'codeBlock',
          language: codeLang,
          rawCode,
          words,
          spans: [],
        })
        plainText += rawCode
        if (i < lines.length - 1) {
          plainText += '\n\n'
        }
      }
      continue
    }

    if (inCodeBlock) {
      codeBuffer.push(rawLine)
      continue
    }

    // Table detection: line must have '|' and next line must be table separator
    if (trimmed.includes('|') && i + 1 < lines.length && isTableSeparator(lines[i + 1])) {
      const headerLine = rawLine
      const sepLine = lines[i + 1]
      const rawHeaderCells = parseTableCells(headerLine)
      const rawSepCells = parseTableCells(sepLine)
      const alignments = rawSepCells.map(getColumnAlignment)

      // Parse headers
      const tableHeaders: TableCell[] = []
      const allTableWords: FormattedWord[] = []
      for (const hText of rawHeaderCells) {
        const startOffset = plainText.length
        const { spans, plainAcc } = parseInlineSpans(hText, startOffset)
        const words = spans.flatMap((s) => s.words)
        allTableWords.push(...words)
        tableHeaders.push({
          spans,
          words,
          rawText: hText,
        })
        plainText += plainAcc + ' '
      }
      plainText = plainText.trimEnd() + '\n'

      // Skip separator line in loop
      i += 1

      // Parse consecutive data rows
      const tableRows: TableRow[] = []
      while (i + 1 < lines.length) {
        const nextLine = lines[i + 1]
        const nextTrimmed = nextLine.trim()
        if (
          !nextTrimmed ||
          !nextTrimmed.includes('|') ||
          nextTrimmed.startsWith('```') ||
          nextTrimmed.startsWith('#') ||
          nextTrimmed.startsWith('>')
        ) {
          break
        }

        i += 1
        const rawRowCells = parseTableCells(nextLine)
        const rowCells: TableCell[] = []
        for (let c = 0; c < rawHeaderCells.length; c++) {
          const cellText = rawRowCells[c] || ''
          const startOffset = plainText.length
          const { spans, plainAcc } = parseInlineSpans(cellText, startOffset)
          const words = spans.flatMap((s) => s.words)
          allTableWords.push(...words)
          rowCells.push({
            spans,
            words,
            rawText: cellText,
          })
          plainText += plainAcc + ' '
        }
        plainText = plainText.trimEnd() + '\n'
        tableRows.push({ cells: rowCells })
      }

      if (i < lines.length - 1) {
        plainText += '\n'
      }

      blocks.push({
        type: 'table',
        tableHeaders,
        tableAlignments: alignments,
        tableRows,
        words: allTableWords,
        spans: [],
      })
      continue
    }

    // Header: #, ##, ###
    if (trimmed.startsWith('#')) {
      const match = trimmed.match(/^(#{1,6})\s+(.*)$/)
      if (match) {
        const level = match[1].length
        const content = match[2].trim()
        const startOffset = plainText.length
        const { spans, plainAcc } = parseInlineSpans(content, startOffset)
        blocks.push({
          type: 'header',
          level,
          spans,
          words: spans.flatMap((s) => s.words),
        })
        plainText += plainAcc
        if (i < lines.length - 1) {
          plainText += '\n\n'
        }
        continue
      }
    }

    // Blockquote: > quote
    if (trimmed.startsWith('>')) {
      const content = trimmed.replace(/^>\s*/, '')
      const startOffset = plainText.length
      const { spans, plainAcc } = parseInlineSpans(content, startOffset)
      blocks.push({
        type: 'blockquote',
        spans,
        words: spans.flatMap((s) => s.words),
      })
      plainText += plainAcc
      if (i < lines.length - 1) {
        plainText += '\n\n'
      }
      continue
    }

    // Bullet List: - item or * item
    if ((trimmed.startsWith('- ') || trimmed.startsWith('* ')) && trimmed.length > 2) {
      const content = trimmed.substring(2).trim()
      const startOffset = plainText.length
      const { spans, plainAcc } = parseInlineSpans(content, startOffset)
      blocks.push({
        type: 'bullet',
        spans,
        words: spans.flatMap((s) => s.words),
      })
      plainText += plainAcc
      if (i < lines.length - 1) {
        plainText += '\n'
      }
      continue
    }

    // Numbered List: 1. item
    const numMatch = trimmed.match(/^(\d+)\.\s+(.*)$/)
    if (numMatch) {
      const numPrefix = numMatch[1] + '. '
      const content = numMatch[2].trim()
      const startOffset = plainText.length
      const { spans, plainAcc } = parseInlineSpans(content, startOffset)
      blocks.push({
        type: 'numbered',
        numberPrefix: numPrefix,
        spans,
        words: spans.flatMap((s) => s.words),
      })
      plainText += plainAcc
      if (i < lines.length - 1) {
        plainText += '\n'
      }
      continue
    }

    // Blank line
    if (!trimmed) {
      if (i < lines.length - 1) {
        plainText += '\n'
      }
      continue
    }

    // Normal paragraph line
    const startOffset = plainText.length
    const { spans, plainAcc } = parseInlineSpans(rawLine, startOffset)
    blocks.push({
      type: 'paragraph',
      spans,
      words: spans.flatMap((s) => s.words),
    })
    plainText += plainAcc
    if (i < lines.length - 1) {
      plainText += '\n'
    }
  }

  // Handle case where code block was not closed
  if (inCodeBlock && codeBuffer.length > 0) {
    const rawCode = codeBuffer.join('\n')
    const startOffset = plainText.length
    const words = tokenizeTextIntoWords(rawCode, startOffset)
    blocks.push({
      type: 'codeBlock',
      language: codeLang,
      rawCode,
      words,
      spans: [],
    })
    plainText += rawCode
  }

  return { plainText, blocks }
}
