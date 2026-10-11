export interface ExaSearchResult {
  success: boolean
  content: string
  error?: string
}

export interface ExaSearchItem {
  url: string
  publishedDate: string
  highlights: string[]
}

export const ZERO_RESULTS_MESSAGE =
  'Search returned 0 results. No credible sources or records were found for this query.'

export const VERIFICATION_CONSTRAINT_TRAILER =
  'Verification constraint: Base your answer strictly on the snippets above. If these snippets do not explicitly verify the event, state that no credible sources were found. Do not invent details, extrapolate, or fill in gaps from memory.'

const EXA_DIRECT_ENDPOINT = 'https://api.exa.ai/search'
const EXA_MCP_ENDPOINT = 'https://mcp.exa.ai/mcp'

/**
 * Formats positive search results into structured text blocks for each item,
 * explicitly printing the URL, publication date, and extracted highlights,
 * and appends the verification constraint trailer.
 */
export function formatExaResults(items: ExaSearchItem[]): string {
  if (!items || items.length === 0) {
    return ZERO_RESULTS_MESSAGE
  }

  const blocks = items.map((item, idx) => {
    const highlightsText =
      item.highlights && item.highlights.length > 0
        ? item.highlights.map((h) => `- ${h.trim()}`).join('\n')
        : '- [No snippet highlights available]'

    return `[Source ${idx + 1}]
URL: ${item.url}
Publication Date: ${item.publishedDate || 'N/A'}
Highlights:
${highlightsText}`
  })

  return `${blocks.join('\n\n')}\n\n${VERIFICATION_CONSTRAINT_TRAILER}`
}

function parseMcpBlocks(text: string): ExaSearchItem[] {
  const blocks = text.split(/\n\s*---\s*\n/)
  const items: ExaSearchItem[] = []

  for (const block of blocks) {
    const lines = block.trim().split('\n')
    let url = ''
    let publishedDate = 'N/A'
    let inHighlights = false
    const highlights: string[] = []

    for (const rawLine of lines) {
      const line = rawLine.trim()
      if (!line) continue

      if (/^URL:\s*/i.test(line)) {
        url = line.replace(/^URL:\s*/i, '').trim()
        inHighlights = false
      } else if (/^Published:\s*/i.test(line)) {
        const d = line.replace(/^Published:\s*/i, '').trim()
        if (d && d !== 'null' && d.toLowerCase() !== 'n/a') {
          publishedDate = d
        }
        inHighlights = false
      } else if (/^Highlights:\s*/i.test(line)) {
        inHighlights = true
      } else if (inHighlights) {
        if (/^(?:Title|URL|Published|Author):/i.test(line)) {
          inHighlights = false
        } else if (line !== '...' && !line.startsWith('#') && line.length > 3) {
          highlights.push(line)
        }
      }
    }

    if (url && url !== 'N/A') {
      items.push({ url, publishedDate, highlights })
    }
  }

  return items
}

/**
 * Searches the web via Exa using numResults: 5 and requesting snippet highlights
 * via contents: { highlights: true }.
 * Returns structured text blocks with URL, publication date, and highlights,
 * guarded by zero-result check and the verification trailer.
 */
export async function searchExaWeb(
  query: string,
  apiKey?: string
): Promise<ExaSearchResult> {
  try {
    const cleanKey = apiKey?.trim() || localStorage.getItem('wyrd_exa_api_key')?.trim() || ''

    // 1. If an Exa API key is provided, attempt direct REST search first
    if (cleanKey) {
      try {
        const directResp = await fetch(EXA_DIRECT_ENDPOINT, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'x-api-key': cleanKey,
          },
          body: JSON.stringify({
            query,
            numResults: 5,
            contents: {
              highlights: true,
              text: {
                maxCharacters: 1000,
              },
            },
          }),
        })

        if (directResp.ok) {
          const json = await directResp.json()
          const rawResults = json.results || []
          const items: ExaSearchItem[] = []

          for (const res of rawResults) {
            const url = res.url || ''
            if (!url) continue
            const publishedDate = res.publishedDate || res.published_date || 'N/A'
            let highlights: string[] = []
            if (Array.isArray(res.highlights)) {
              highlights = res.highlights.filter(
                (h: any) => typeof h === 'string' && h.trim().length > 0
              )
            } else if (typeof res.highlight === 'string' && res.highlight.trim().length > 0) {
              highlights = [res.highlight.trim()]
            }
            if (highlights.length === 0) {
              const textSnippet = typeof res.text === 'string' && res.text.trim() ? res.text.trim() : (typeof res.summary === 'string' ? res.summary.trim() : '')
              if (textSnippet) {
                highlights = [textSnippet.slice(0, 500)]
              }
            }
            items.push({ url, publishedDate, highlights })
          }

          // Zero-result guard at the tool boundary
          if (items.length === 0) {
            return {
              success: true,
              content: ZERO_RESULTS_MESSAGE,
            }
          }

          return {
            success: true,
            content: formatExaResults(items),
          }
        }
      } catch (directErr) {
        console.warn('Exa direct REST API attempt failed, falling back to MCP:', directErr)
      }
    }

    // 2. Fallback / default: Exa MCP endpoint
    const mcpHeaders: Record<string, string> = {
      'Content-Type': 'application/json',
      Accept: 'application/json, text/event-stream',
    }

    if (cleanKey) {
      mcpHeaders['x-api-key'] = cleanKey
      mcpHeaders['Authorization'] = `Bearer ${cleanKey}`
    }

    const payload = {
      jsonrpc: '2.0',
      id: 1,
      method: 'tools/call',
      params: {
        name: 'web_search_exa',
        arguments: {
          query,
          numResults: 5,
          contents: {
            highlights: true,
          },
        },
      },
    }

    const mcpUrl = cleanKey ? `${EXA_MCP_ENDPOINT}?exaApiKey=${encodeURIComponent(cleanKey)}` : EXA_MCP_ENDPOINT
    const response = await fetch(mcpUrl, {
      method: 'POST',
      headers: mcpHeaders,
      body: JSON.stringify(payload),
    })

    if (!response.ok) {
      const errText = await response.text()
      console.warn(`Exa MCP HTTP Error (${response.status}): ${errText}`)
      return {
        success: false,
        content: ZERO_RESULTS_MESSAGE,
        error: `Exa MCP HTTP ${response.status}`,
      }
    }

    const responseText = await response.text()
    let combinedText = ''
    let isError = false

    const lines = responseText.split('\n')
    for (const rawLine of lines) {
      const line = rawLine.trim()
      if (!line || line === 'data: [DONE]') continue

      const jsonStr = line.startsWith('data:') ? line.replace(/^data:\s*/, '') : line
      if (jsonStr.startsWith('{') && jsonStr.endsWith('}')) {
        try {
          const json = JSON.parse(jsonStr)

          if (json.error) {
            isError = true
            break
          }

          if (json.result) {
            const hasError = json.result.isError
            const contents = json.result.content
            if (Array.isArray(contents)) {
              for (const item of contents) {
                const text = item?.text || ''
                if (text && text !== 'null') {
                  const lower = text.toLowerCase()
                  if (
                    hasError ||
                    lower.includes('web_search_exa error') ||
                    lower.includes('invalid api key') ||
                    lower.includes('rate limit') ||
                    lower.includes('too many requests')
                  ) {
                    isError = true
                    break
                  }
                  combinedText += text + '\n\n'
                }
              }
            }
          }
        } catch {
          // Ignore incomplete lines
        }
      }
    }

    if (isError) {
      return {
        success: false,
        content: ZERO_RESULTS_MESSAGE,
        error: 'Exa MCP returned an error',
      }
    }

    if (!combinedText.trim()) {
      return {
        success: true,
        content: ZERO_RESULTS_MESSAGE,
      }
    }

    const parsedItems = parseMcpBlocks(combinedText)
    if (parsedItems.length === 0) {
      return {
        success: true,
        content: ZERO_RESULTS_MESSAGE,
      }
    }

    return {
      success: true,
      content: formatExaResults(parsedItems),
    }
  } catch (err: unknown) {
    console.error('Exa search exception:', err)
    return {
      success: false,
      content: ZERO_RESULTS_MESSAGE,
      error: err instanceof Error ? err.message : String(err),
    }
  }
}
