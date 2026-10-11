export interface TavilySearchResult {
  success: boolean
  content: string
  error?: string
}

export interface TavilySearchItem {
  title: string
  url: string
  content: string
  publishedDate?: string
}

export const TAVILY_ZERO_RESULTS_MESSAGE =
  'Tavily search returned 0 results. No live web sources or records were found for this query.'

export const TAVILY_VERIFICATION_CONSTRAINT_TRAILER =
  'Verification constraint: Base your answer strictly on the live web snippets above. If these snippets do not explicitly verify the event or claim, state that no credible sources were found. Do not invent details, extrapolate, or fill in gaps from memory.'

const TAVILY_API_ENDPOINT = 'https://api.tavily.com/search'

/**
 * Formats positive Tavily search results into structured text blocks for each item,
 * printing Title, URL, Publication Date, and Content Snippet, followed by the verification trailer.
 */
export function formatTavilyResults(items: TavilySearchItem[]): string {
  if (!items || items.length === 0) {
    return TAVILY_ZERO_RESULTS_MESSAGE
  }

  const blocks = items.map((item, idx) => {
    return `[Source ${idx + 1}]
Title: ${item.title || 'Untitled'}
URL: ${item.url}
Publication Date: ${item.publishedDate || 'Recent'}
Content Snippet:
${item.content?.trim() || '[No content snippet available]'}`
  })

  return `${blocks.join('\n\n')}\n\n${TAVILY_VERIFICATION_CONSTRAINT_TRAILER}`
}

/**
 * Executes a live web search via Tavily REST API.
 */
export async function searchTavilyWeb(
  query: string,
  apiKey?: string
): Promise<TavilySearchResult> {
  const cleanKey = apiKey?.trim() || localStorage.getItem('wyrd_tavily_api_key')?.trim() || ''

  if (!cleanKey) {
    return {
      success: false,
      content:
        'Tavily API key is not configured. Please add your free Tavily API key in Settings (get one at app.tavily.com) to search the live web.',
      error: 'Missing Tavily API key',
    }
  }

  try {
    const response = await fetch(TAVILY_API_ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        api_key: cleanKey,
        query,
        search_depth: 'basic',
        max_results: 5,
        include_answer: false,
      }),
    })

    if (!response.ok) {
      const errText = await response.text()
      console.warn(`Tavily HTTP error (${response.status}): ${errText}`)
      let parsedMessage = errText
      try {
        const errJson = JSON.parse(errText)
        parsedMessage = errJson.detail?.error || errJson.message || errText
      } catch {
        // use raw text
      }
      return {
        success: false,
        content: `Tavily search failed (${response.status}): ${parsedMessage}`,
        error: `HTTP ${response.status}`,
      }
    }

    const data = await response.json()
    const rawResults = Array.isArray(data.results) ? data.results : []
    const items: TavilySearchItem[] = rawResults.map((r: { title?: string; url?: string; content?: string; published_date?: string }) => ({
      title: r.title || '',
      url: r.url || '',
      content: r.content || '',
      publishedDate: r.published_date || '',
    }))

    return {
      success: true,
      content: formatTavilyResults(items),
    }
  } catch (err: unknown) {
    console.error('Tavily search exception:', err)
    const message = err instanceof Error ? err.message : String(err)
    return {
      success: false,
      content: `Tavily search encountered a network error: ${message}`,
      error: message,
    }
  }
}
