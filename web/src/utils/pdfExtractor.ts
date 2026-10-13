/**
 * Lightweight, zero-dependency PDF text extractor for browser File objects.
 * Supports /FlateDecode stream decompression (via DecompressionStream),
 * /ToUnicode CMap tables (beginbfchar / beginbfrange), UTF-16BE (FEFF),
 * and BT...ET text blocks (Tj, TJ, ', ").
 */

async function inflateStream(compressed: Uint8Array): Promise<Uint8Array | null> {
  if (typeof DecompressionStream === 'undefined') return null;

  for (const format of ['deflate', 'deflate-raw'] as const) {
    try {
      const ds = new DecompressionStream(format);
      const writer = ds.writable.getWriter();
      void writer.write(new Uint8Array(compressed)).catch(() => {});
      void writer.close().catch(() => {});
      const response = new Response(ds.readable);
      const buffer = await response.arrayBuffer();
      if (buffer.byteLength > 0) {
        return new Uint8Array(buffer);
      }
    } catch {
      // Try next format
    }
  }
  return null;
}

function bytesToLatin1(bytes: Uint8Array): string {
  let out = '';
  const chunkSize = 8192;
  for (let i = 0; i < bytes.length; i += chunkSize) {
    const slice = bytes.subarray(i, Math.min(i + chunkSize, bytes.length));
    out += String.fromCharCode(...slice);
  }
  return out;
}

function findSequence(haystack: Uint8Array, needle: number[], startIndex: number): number {
  const limit = haystack.length - needle.length;
  for (let i = startIndex; i <= limit; i++) {
    let match = true;
    for (let j = 0; j < needle.length; j++) {
      if (haystack[i + j] !== needle[j]) {
        match = false;
        break;
      }
    }
    if (match) return i;
  }
  return -1;
}

const STREAM_MARKER = [0x73, 0x74, 0x72, 0x65, 0x61, 0x6d]; // "stream"
const ENDSTREAM_MARKER = [0x65, 0x6e, 0x64, 0x73, 0x74, 0x72, 0x65, 0x61, 0x6d]; // "endstream"

function decodeHexToUnicode(hexStr: string): string {
  const clean = hexStr.replace(/\s+/g, '');
  if (!clean) return '';
  if (clean.length >= 4 && clean.length % 4 === 0) {
    let out = '';
    for (let i = 0; i < clean.length; i += 4) {
      const code = parseInt(clean.slice(i, i + 4), 16);
      if (!Number.isNaN(code)) out += String.fromCharCode(code);
    }
    return out;
  }
  let out = '';
  for (let i = 0; i + 2 <= clean.length; i += 2) {
    const code = parseInt(clean.slice(i, i + 2), 16);
    if (!Number.isNaN(code)) out += String.fromCharCode(code);
  }
  return out;
}

const BFCHAR_BLOCK_REGEX = /beginbfchar([\s\S]*?)endbfchar/g;
const BFRANGE_BLOCK_REGEX = /beginbfrange([\s\S]*?)endbfrange/g;
const BFCHAR_PAIR_REGEX = /<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>/g;
const BFRANGE_TRIPLE_REGEX = /<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>/g;

function parseToUnicodeCMap(streamContent: string, cmap: Map<number, string>): void {
  BFCHAR_BLOCK_REGEX.lastIndex = 0;
  let blockMatch: RegExpExecArray | null;
  while ((blockMatch = BFCHAR_BLOCK_REGEX.exec(streamContent)) !== null) {
    const block = blockMatch[1];
    BFCHAR_PAIR_REGEX.lastIndex = 0;
    let pair: RegExpExecArray | null;
    while ((pair = BFCHAR_PAIR_REGEX.exec(block)) !== null) {
      const srcCode = parseInt(pair[1], 16);
      const dstStr = decodeHexToUnicode(pair[2]);
      if (!Number.isNaN(srcCode) && dstStr) {
        cmap.set(srcCode, dstStr);
      }
    }
  }

  BFRANGE_BLOCK_REGEX.lastIndex = 0;
  while ((blockMatch = BFRANGE_BLOCK_REGEX.exec(streamContent)) !== null) {
    const block = blockMatch[1];
    BFRANGE_TRIPLE_REGEX.lastIndex = 0;
    let triple: RegExpExecArray | null;
    while ((triple = BFRANGE_TRIPLE_REGEX.exec(block)) !== null) {
      const startCode = parseInt(triple[1], 16);
      const endCode = parseInt(triple[2], 16);
      const targetBase = parseInt(triple[3], 16);
      if (!Number.isNaN(startCode) && !Number.isNaN(endCode) && !Number.isNaN(targetBase)) {
        const span = Math.min(endCode - startCode, 1024);
        for (let offset = 0; offset <= span; offset++) {
          cmap.set(startCode + offset, String.fromCharCode(targetBase + offset));
        }
      }
    }
  }
}

function unescapePdfLiteral(literal: string, cmap: Map<number, string>): string {
  let raw = '';
  let i = 0;
  while (i < literal.length) {
    const ch = literal[i];
    if (ch === '\\' && i + 1 < literal.length) {
      const next = literal[i + 1];
      switch (next) {
        case 'n':
          raw += '\n';
          i += 2;
          break;
        case 'r':
          raw += '\r';
          i += 2;
          break;
        case 't':
          raw += '\t';
          i += 2;
          break;
        case 'b':
          raw += '\b';
          i += 2;
          break;
        case 'f':
          raw += '\f';
          i += 2;
          break;
        case '(':
        case ')':
        case '\\':
          raw += next;
          i += 2;
          break;
        default:
          if (next >= '0' && next <= '7') {
            let oct = next;
            let adv = 2;
            while (adv <= 3 && i + adv < literal.length && literal[i + adv] >= '0' && literal[i + adv] <= '7') {
              oct += literal[i + adv];
              adv++;
            }
            const code = parseInt(oct, 8);
            if (!Number.isNaN(code)) raw += String.fromCharCode(code);
            i += adv;
          } else {
            raw += next;
            i += 2;
          }
          break;
      }
    } else {
      raw += ch;
      i++;
    }
  }

  if (raw.length >= 2 && raw.charCodeAt(0) === 0xfe && raw.charCodeAt(1) === 0xff) {
    let utf16 = '';
    for (let j = 2; j + 1 < raw.length; j += 2) {
      const code = (raw.charCodeAt(j) << 8) | raw.charCodeAt(j + 1);
      if (code !== 0) utf16 += String.fromCharCode(code);
    }
    return utf16;
  }

  if (cmap.size > 0) {
    let mapped = '';
    let mappedHits = 0;
    for (let j = 0; j < raw.length; j++) {
      const code = raw.charCodeAt(j);
      const hit = cmap.get(code);
      if (hit !== undefined) {
        mapped += hit;
        mappedHits++;
      } else {
        mapped += raw[j];
      }
    }
    if (mappedHits > 0) return mapped;
  }

  return raw;
}

function decodeHexToken(hex: string, cmap: Map<number, string>): string {
  const clean = hex.replace(/\s+/g, '');
  if (!clean) return '';

  if (clean.toUpperCase().startsWith('FEFF')) {
    const payload = clean.slice(4);
    let out = '';
    for (let i = 0; i + 4 <= payload.length; i += 4) {
      const code = parseInt(payload.slice(i, i + 4), 16);
      if (!Number.isNaN(code) && code !== 0) out += String.fromCharCode(code);
    }
    return out;
  }

  if (cmap.size > 0 && clean.length % 4 === 0) {
    let mapped4 = '';
    let hits4 = 0;
    for (let i = 0; i + 4 <= clean.length; i += 4) {
      const code = parseInt(clean.slice(i, i + 4), 16);
      const hit = cmap.get(code);
      if (hit !== undefined) {
        mapped4 += hit;
        hits4++;
      } else if (!Number.isNaN(code) && code >= 32 && code <= 126) {
        mapped4 += String.fromCharCode(code);
      }
    }
    if (hits4 > 0) return mapped4;
  }

  let out = '';
  for (let i = 0; i + 2 <= clean.length; i += 2) {
    const code = parseInt(clean.slice(i, i + 2), 16);
    if (Number.isNaN(code)) continue;
    const hit = cmap.get(code);
    if (hit !== undefined) {
      out += hit;
    } else if (code >= 32 || code === 9 || code === 10 || code === 13) {
      out += String.fromCharCode(code);
    }
  }
  return out;
}

function extractTextFromOperand(operand: string, cmap: Map<number, string>): string {
  let out = '';
  let i = 0;
  while (i < operand.length) {
    const ch = operand[i];
    if (ch === '(') {
      let depth = 1;
      let j = i + 1;
      let literal = '';
      while (j < operand.length && depth > 0) {
        const c = operand[j];
        if (c === '\\' && j + 1 < operand.length) {
          literal += c + operand[j + 1];
          j += 2;
          continue;
        }
        if (c === '(') depth++;
        else if (c === ')') {
          depth--;
          if (depth === 0) {
            j++;
            break;
          }
        }
        literal += c;
        j++;
      }
      out += unescapePdfLiteral(literal, cmap);
      i = j;
    } else if (ch === '<' && i + 1 < operand.length && operand[i + 1] !== '<') {
      const end = operand.indexOf('>', i + 1);
      if (end !== -1) {
        out += decodeHexToken(operand.slice(i + 1, end), cmap);
        i = end + 1;
      } else {
        i++;
      }
    } else if (ch === '-' && i + 1 < operand.length && /\d/.test(operand[i + 1])) {
      let j = i + 1;
      while (j < operand.length && /[\d.]/.test(operand[j])) j++;
      const kern = parseFloat(operand.slice(i + 1, j));
      if (!Number.isNaN(kern) && kern >= 180 && !out.endsWith(' ')) {
        out += ' ';
      }
      i = j;
    } else {
      i++;
    }
  }
  return out;
}

const BT_ET_BLOCK_REGEX = /\bBT\b([\s\S]*?)\bET\b/g;
const TEXT_OP_REGEX =
  /(\[(?:[^\]\\]|\\[\s\S]|\((?:[^()\\]|\\[\s\S])*\))*\]\s*TJ|\((?:[^()\\]|\\[\s\S])*\)\s*(?:Tj|'|")|<[0-9A-Fa-f\s]+>\s*Tj|\b(?:T\*|TD|Td)\b)/g;
const TD_VERTICAL_REGEX = /(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)\s+T[dD]/;

function extractTextFromContentStream(streamContent: string, cmap: Map<number, string>): string {
  const lines: string[] = [];
  BT_ET_BLOCK_REGEX.lastIndex = 0;
  let blockMatch: RegExpExecArray | null;
  while ((blockMatch = BT_ET_BLOCK_REGEX.exec(streamContent)) !== null) {
    const block = blockMatch[1];
    let currentLine = '';
    TEXT_OP_REGEX.lastIndex = 0;
    let opMatch: RegExpExecArray | null;
    while ((opMatch = TEXT_OP_REGEX.exec(block)) !== null) {
      const token = opMatch[1].trim();
      if (token === 'T*' || token.endsWith('TD') || token.endsWith('Td')) {
        if (token === 'T*') {
          if (currentLine.trim()) {
            lines.push(currentLine.trim());
            currentLine = '';
          }
        } else {
          const tdMatch = TD_VERTICAL_REGEX.exec(token);
          const dy = tdMatch ? parseFloat(tdMatch[2]) : 0;
          if (Math.abs(dy) > 1.5 && currentLine.trim()) {
            lines.push(currentLine.trim());
            currentLine = '';
          }
        }
      } else {
        const piece = extractTextFromOperand(token, cmap);
        if (piece) {
          currentLine += piece;
          if ((token.endsWith("'") || token.endsWith('"')) && currentLine.trim()) {
            lines.push(currentLine.trim());
            currentLine = '';
          }
        }
      }
    }
    if (currentLine.trim()) {
      lines.push(currentLine.trim());
    }
  }
  return lines.join('\n').trim();
}

export async function extractTextFromPdf(file: File): Promise<string> {
  try {
    const buffer = await file.arrayBuffer();
    const pdfBytes = new Uint8Array(buffer);
    if (pdfBytes.length < 16) return '';

    const decodedStreams: string[] = [];
    const cmap = new Map<number, string>();
    let searchIdx = 0;

    while (searchIdx < pdfBytes.length) {
      const streamPos = findSequence(pdfBytes, STREAM_MARKER, searchIdx);
      if (streamPos === -1) break;

      const headerStart = Math.max(0, streamPos - 300);
      const dictHeader = bytesToLatin1(pdfBytes.subarray(headerStart, streamPos));

      let dataStart = streamPos + STREAM_MARKER.length;
      if (dataStart < pdfBytes.length && pdfBytes[dataStart] === 0x0d) dataStart++;
      if (dataStart < pdfBytes.length && pdfBytes[dataStart] === 0x0a) dataStart++;

      const endStreamPos = findSequence(pdfBytes, ENDSTREAM_MARKER, dataStart);
      if (endStreamPos === -1) break;

      let dataEnd = endStreamPos;
      while (dataEnd > dataStart && (pdfBytes[dataEnd - 1] === 0x0a || pdfBytes[dataEnd - 1] === 0x0d)) {
        dataEnd--;
      }

      if (dataEnd > dataStart) {
        const rawSlice = pdfBytes.subarray(dataStart, dataEnd);
        const isFlate = /\/FlateDecode\b/i.test(dictHeader);
        const isImageOrFont = /\/Subtype\s*\/Image\b|\/FontFile[23]?\b/i.test(dictHeader);

        if (!isImageOrFont) {
          let decompressed: Uint8Array | null = null;
          if (isFlate) {
            decompressed = await inflateStream(rawSlice);
          } else {
            decompressed = rawSlice;
          }

          if (decompressed && decompressed.length > 0) {
            const text = bytesToLatin1(decompressed);
            if (text.includes('beginbfchar') || text.includes('beginbfrange')) {
              parseToUnicodeCMap(text, cmap);
            }
            if (text.includes('BT') && text.includes('ET')) {
              decodedStreams.push(text);
            }
          }
        }
      }

      searchIdx = endStreamPos + ENDSTREAM_MARKER.length;
    }

    const segments: string[] = [];
    for (const streamText of decodedStreams) {
      const extracted = extractTextFromContentStream(streamText, cmap);
      if (extracted) {
        segments.push(extracted);
      }
    }

    const result = segments
      .join('\n\n')
      .replace(/[ \t]+\n/g, '\n')
      .replace(/\n{3,}/g, '\n\n')
      .trim();

    if (result) {
      return result.length > 120_000
        ? `${result.slice(0, 120_000)}\n\n... [PDF text truncated at 120,000 characters]`
        : result;
    }
    return `[PDF Document: ${file.name} (${Math.round(file.size / 1024)} KB) — No selectable text layer found (scanned or image-only PDF)]`;
  } catch (err) {
    console.warn('PDF text extraction failed:', err);
    return `[PDF Document: ${file.name} (${Math.round(file.size / 1024)} KB)]`;
  }
}
