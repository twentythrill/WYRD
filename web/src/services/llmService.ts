import { searchExaWeb, ZERO_RESULTS_MESSAGE } from './exaSearchService'
import { searchTavilyWeb, TAVILY_ZERO_RESULTS_MESSAGE } from './tavilySearchService'

export interface StreamChunk {
  content?: string
  reasoning?: string
  status?: string
  clearContent?: boolean
  done?: boolean
  error?: string
}

export type SearchToolType = 'tavily_search' | 'exa_search' | 'web_search'

export interface ExtractedToolCall {
  tool: SearchToolType
  query: string
}

export const TAVILY_SEARCH_TOOL = {
  type: 'function',
  function: {
    name: 'tavily_search',
    description:
      'Searches the live web in real time for breaking news, corporate announcements, funding, official company site, and live facts via Tavily. Note: cannot access Glassdoor or Reddit.',
    parameters: {
      type: 'object',
      properties: {
        query: {
          type: 'string',
          description: 'The search query to look up on the live web via Tavily',
        },
      },
      required: ['query'],
    },
  },
}

export const EXA_SEARCH_TOOL = {
  type: 'function',
  function: {
    name: 'exa_search',
    description:
      'Searches the web semantically for employee reviews, Glassdoor ratings, Reddit threads, interview feedback, workplace culture, and forum discussions via Exa.',
    parameters: {
      type: 'object',
      properties: {
        query: {
          type: 'string',
          description: 'The search query to look up on the web semantically via Exa',
        },
      },
      required: ['query'],
    },
  },
}

export function isReviewOrForumQuery(query: string): boolean {
  const q = query.toLowerCase()
  return (
    q.includes('glassdoor') ||
    q.includes('reddit') ||
    q.includes('blind') ||
    q.includes('teamblind') ||
    q.includes('indeed') ||
    q.includes('comparably') ||
    q.includes('levels.fyi') ||
    q.includes('trustpilot') ||
    q.includes('g2') ||
    q.includes('capterra') ||
    q.includes('employee review') ||
    q.includes('employee reviews') ||
    q.includes('staff review') ||
    q.includes('worker review') ||
    q.includes('company review') ||
    q.includes('workplace culture') ||
    q.includes('work culture') ||
    q.includes('company culture') ||
    q.includes('interview review') ||
    q.includes('interview feedback') ||
    q.includes('interview question') ||
    q.includes('interview questions') ||
    q.includes('interview experience') ||
    q.includes('work-life') ||
    q.includes('wlb') ||
    q.includes('management review') ||
    q.includes('leadership review') ||
    q.includes('layoff sentiment') ||
    q.includes('employee sentiment') ||
    q.includes('salary') ||
    q.includes('compensation') ||
    (q.includes('review') &&
      (q.includes('culture') ||
        q.includes('sentiment') ||
        q.includes('employee') ||
        q.includes('workplace') ||
        q.includes('intern')))
  )
}

export const WEB_SEARCH_TOOL = {
  type: 'function',
  function: {
    name: 'web_search',
    description:
      'Searches the web for recent events and factual corroboration. Returns URLs, publication dates, and extracted snippets. If the returned snippets do not confirm the claim, treat the claim as unverified.',
    parameters: {
      type: 'object',
      properties: {
        query: {
          type: 'string',
          description: 'The search query to look up on the web',
        },
      },
      required: ['query'],
    },
  },
}

export function extractToolCall(text: string, explicitToolName?: string): ExtractedToolCall | null {
  const trimmed = text.trim()
  if (!trimmed) return null

  // Determine tool function name if explicitly present
  let tool: SearchToolType = 'web_search'
  if (explicitToolName && /exa/i.test(explicitToolName)) {
    tool = 'exa_search'
  } else if (explicitToolName && /tavily/i.test(explicitToolName)) {
    tool = 'tavily_search'
  } else if (/exa_search|exa/i.test(trimmed)) {
    tool = 'exa_search'
  } else if (/tavily_search|tavily/i.test(trimmed)) {
    tool = 'tavily_search'
  }

  // 1. XML parameter syntax: <parameter=query>...</parameter> or <parameter name="query">...</parameter>
  const paramRegex = /<parameter\s*(?:=|name\s*=\s*["']?)query["']?>\s*([^<]+)\s*<\/parameter>/i
  const paramMatch = trimmed.match(paramRegex)
  if (paramMatch?.[1]?.trim()) {
    return { tool, query: paramMatch[1].trim() }
  }

  // 2. Direct tag syntax: <query>...</query> or <search_query>...</search_query>
  const queryTagRegex = /<(?:query|search_query)>\s*([^<]+)\s*<\/(?:query|search_query)>/i
  const queryTagMatch = trimmed.match(queryTagRegex)
  if (queryTagMatch?.[1]?.trim()) {
    return { tool, query: queryTagMatch[1].trim() }
  }

  // 3. JSON function arguments syntax: {"query": "..."}
  const jsonQueryRegex = /["'](?:query|search_query)["']\s*:\s*["']([^"'\n\r]+)["']/i
  const jsonMatch = trimmed.match(jsonQueryRegex)
  if (jsonMatch?.[1]?.trim()) {
    return { tool, query: jsonMatch[1].trim() }
  }

  // 4. Function call syntax: tavily_search("...") or exa_search("...") or web_search("...")
  const fnRegex = /(?:call:)?(?:tavily_search|exa_search|web_search)\s*\(\s*(?:(?:query|q)\s*=\s*)?["']([^"'\n\r]+)["']\s*\)/i
  const fnMatch = trimmed.match(fnRegex)
  if (fnMatch?.[1]?.trim()) {
    return { tool, query: fnMatch[1].trim() }
  }

  // 5. Action bracket syntax: [tavily_search: ...] or [ACTION: exa_search("...")]
  const bracketRegex = /\[(?:ACTION:\s*)?(?:tavily_search|exa_search|web_search)[\s:(]+["']?([^"'\])\n\r]+)["']?[)\]]/i
  const bracketMatch = trimmed.match(bracketRegex)
  if (bracketMatch?.[1]?.trim()) {
    return { tool, query: bracketMatch[1].trim() }
  }

  return null
}

export function extractToolQuery(text: string): string | null {
  return extractToolCall(text)?.query ?? null
}

/**
 * Robust streaming demuxer matching Android's ThinkAndToolParser.
 * Buffers chunks to isolate <think> and <tool_call> tags without leaking XML into content.
 */
class ThinkAndToolParser {
  private inThink = false
  private inTool = false
  private wasInThinkBeforeTool = false
  private buffer = ''
  private toolCallBuffer = ''

  private thinkOpenTags = ['<think>', '<thought>', '<thinking>']
  private thinkCloseTags = ['</think>', '</thought>', '</thinking>']
  private toolOpenPrefixes = ['<tool_call', '<function_call', '<tool>', '<tool ']
  private toolCloseTags = ['</tool_call>', '</function_call>', '</tool>']

  getToolCallContent(): string {
    return this.toolCallBuffer.trim()
  }

  feed(
    chunk: string,
    onContent: (s: string) => void,
    onReasoning: (s: string) => void
  ) {
    this.buffer += chunk

    while (this.buffer.length > 0) {
      if (this.inTool) {
        let closeIdx = -1
        let closeTagLen = 0
        for (const tag of this.toolCloseTags) {
          const idx = this.buffer.indexOf(tag)
          if (idx !== -1 && (closeIdx === -1 || idx < closeIdx)) {
            closeIdx = idx
            closeTagLen = tag.length
          }
        }

        if (closeIdx !== -1) {
          this.toolCallBuffer += this.buffer.substring(0, closeIdx + closeTagLen)
          this.buffer = this.buffer.substring(closeIdx + closeTagLen)
          this.inTool = false
          this.inThink = this.wasInThinkBeforeTool
          continue
        } else {
          const prefixLen = this.getPrefixLen(this.buffer, this.toolCloseTags)
          const safeLen = this.buffer.length - prefixLen
          if (safeLen > 0) {
            this.toolCallBuffer += this.buffer.substring(0, safeLen)
            this.buffer = this.buffer.substring(safeLen)
          }
          break
        }
      } else {
        let firstTagIdx = -1
        let tagType: 'tool_open' | 'think_open' | 'think_close' | 'none' = 'none'
        let matchedTagStr = ''

        for (const prefix of this.toolOpenPrefixes) {
          const idx = this.buffer.indexOf(prefix)
          if (idx !== -1 && (firstTagIdx === -1 || idx < firstTagIdx)) {
            firstTagIdx = idx
            tagType = 'tool_open'
            matchedTagStr = prefix
          }
        }

        if (!this.inThink) {
          for (const tag of this.thinkOpenTags) {
            const idx = this.buffer.indexOf(tag)
            if (idx !== -1 && (firstTagIdx === -1 || idx < firstTagIdx)) {
              firstTagIdx = idx
              tagType = 'think_open'
              matchedTagStr = tag
            }
          }
          // Also catch lone closing think tags without open tag
          for (const tag of this.thinkCloseTags) {
            const idx = this.buffer.indexOf(tag)
            if (idx !== -1 && (firstTagIdx === -1 || idx < firstTagIdx)) {
              firstTagIdx = idx
              tagType = 'think_close'
              matchedTagStr = tag
            }
          }
        } else {
          for (const tag of this.thinkCloseTags) {
            const idx = this.buffer.indexOf(tag)
            if (idx !== -1 && (firstTagIdx === -1 || idx < firstTagIdx)) {
              firstTagIdx = idx
              tagType = 'think_close'
              matchedTagStr = tag
            }
          }
        }

        if (firstTagIdx !== -1) {
          const before = this.buffer.substring(0, firstTagIdx)
          if (before.length > 0) {
            if (this.inThink) onReasoning(before)
            else onContent(before)
          }

          if (tagType === 'tool_open') {
            const closeBracketIdx = this.buffer.indexOf('>', firstTagIdx)
            if (closeBracketIdx === -1) {
              this.buffer = this.buffer.substring(firstTagIdx)
              break
            }
            this.wasInThinkBeforeTool = this.inThink
            this.inTool = true
            this.toolCallBuffer += this.buffer.substring(firstTagIdx, closeBracketIdx + 1)
            this.buffer = this.buffer.substring(closeBracketIdx + 1)
            continue
          } else if (tagType === 'think_open') {
            this.buffer = this.buffer.substring(firstTagIdx + matchedTagStr.length)
            this.inThink = true
            continue
          } else if (tagType === 'think_close') {
            this.buffer = this.buffer.substring(firstTagIdx + matchedTagStr.length)
            this.inThink = false
            continue
          }
        } else {
          const candidates = [
            ...this.toolOpenPrefixes,
            ...(this.inThink ? this.thinkCloseTags : this.thinkOpenTags),
          ]
          const prefixLen = this.getPrefixLen(this.buffer, candidates)
          const safeLen = this.buffer.length - prefixLen
          if (safeLen > 0) {
            const safeText = this.buffer.substring(0, safeLen)
            if (this.inThink) onReasoning(safeText)
            else onContent(safeText)
            this.buffer = this.buffer.substring(safeLen)
          }
          break
        }
      }
    }
  }

  flush(onContent: (s: string) => void, onReasoning: (s: string) => void) {
    if (this.inTool) {
      this.toolCallBuffer += this.buffer
      this.buffer = ''
    } else if (this.buffer.length > 0) {
      const rem = this.buffer
      this.buffer = ''
      const trimmed = rem.trim()
      if (!trimmed.startsWith('<think') && !trimmed.startsWith('<tool') && !trimmed.startsWith('<function')) {
        if (this.inThink) onReasoning(rem)
        else onContent(rem)
      }
    }
  }

  private getPrefixLen(str: string, candidateTags: string[]): number {
    let maxLen = 0
    for (const tag of candidateTags) {
      for (let len = Math.min(str.length, tag.length - 1); len >= 1; len--) {
        if (tag.startsWith(str.substring(str.length - len))) {
          if (len > maxLen) maxLen = len
          break
        }
      }
    }
    return maxLen
  }
}

const TOKEN_PLAN_ENDPOINT = 'https://token-plan-cn.xiaomimimo.com/v1/chat/completions'
const PAY_AS_YOU_GO_ENDPOINT = 'https://api.xiaomimimo.com/v1/chat/completions'

function getCandidateEndpoints(apiKey: string, customBaseUrl?: string): string[] {
  const trimmed = customBaseUrl?.trim()
  if (trimmed && trimmed !== 'https://api.siliconflow.cn/v1') {
    const clean = trimmed.replace(/\/+$/, '')
    const ep = clean.endsWith('/chat/completions') ? clean : `${clean}/chat/completions`
    return [ep]
  }

  const cleanKey = apiKey.trim()
  if (cleanKey.startsWith('tp-') || cleanKey.startsWith('ttp-')) {
    return [TOKEN_PLAN_ENDPOINT, PAY_AS_YOU_GO_ENDPOINT]
  }
  return [PAY_AS_YOU_GO_ENDPOINT, TOKEN_PLAN_ENDPOINT]
}

export async function* streamLlmCompletion({
  apiKey,
  baseUrl = '',
  model = 'mimo-v2.6-pro',
  reasoningEffort = 'medium',
  systemPrompt = '',
  messages = [],
  signal,
}: {
  apiKey: string
  baseUrl?: string
  model?: string
  reasoningEffort?: string
  systemPrompt?: string
  messages: Array<{
    role: 'user' | 'assistant' | 'system'
    content: string
    imagesBase64?: string[]
  }>
  signal?: AbortSignal
}): AsyncGenerator<StreamChunk, void, unknown> {
  const cleanKey = apiKey?.trim() || ''
  if (!cleanKey) {
    yield {
      content:
        '**API Key Required for Web Generation**\n\nPlease add your Xiaomi MiMo API key in **Settings** to generate replies directly in the browser.\n\n*Note: Any conversations created or replied to on your WYRD Android app will sync here in real-time automatically.*',
      done: true,
    }
    return
  }

  const currentDateTimeStr = new Date().toLocaleString()
  const temporalContext = `Current Device Date and Time: ${currentDateTimeStr}`

  let effectiveSystemPrompt = ''
  if (systemPrompt && systemPrompt.trim().length > 0) {
    effectiveSystemPrompt += `${systemPrompt.trim()}\n\n`
  }
  effectiveSystemPrompt += `[Context: ${temporalContext}]`
  effectiveSystemPrompt += `\n[Autonomous Web Search Tools: You have access to external search engines to ground your answers in verified external facts. You are in charge of deciding autonomously whether to use web search.
- \`tavily_search\`: Real-time live web search engine. Specialized for: breaking news, current corporate announcements, recent funding rounds, official company website info, and live facts. Note: Tavily CANNOT bypass anti-bot blocks on Glassdoor or Reddit. Invoke with:
<tool_call><function=tavily_search><parameter=query>your search query</parameter></function></tool_call>
- \`exa_search\`: Neural semantic search engine. Specialized for: employee reviews (Glassdoor, Indeed, Comparably), community and forum discussions (Reddit, Hacker News, Blind), interview feedback, workplace culture, and salary data. Exa indexes and searches these platforms directly. Invoke with:
<tool_call><function=exa_search><parameter=query>your search query</parameter></function></tool_call>
- Tool Selection & Combination Directives:
  1. For company employee sentiment, Glassdoor ratings, Reddit threads, workplace culture, or interview experiences: you MUST use \`exa_search\`. Never use Tavily for Glassdoor or Reddit.
  2. For company business health checks, employment audits, or due diligence: ALWAYS combine both tools across search rounds! Use \`tavily_search\` to verify corporate facts, funding, and news, AND use \`exa_search\` to uncover employee sentiment, Glassdoor reviews, and workplace culture. Do not write a final verdict until you have searched both sides.
  3. If your first search round only returned corporate news or missed employee reviews, immediately invoke \`exa_search\` in the next round to gather the employee sentiment and Glassdoor data before finalizing your verdict.
If the request does not require external search, answer directly without invoking tools. Never tell the user to use manual search commands like /search.]`

  if (reasoningEffort === 'low') {
    effectiveSystemPrompt += '\n[Reasoning Budget: Low (~1000 tokens). Strictly constrain your internal thinking/reasoning process to be brief, concise, and focused solely on core deductions. Keep internal thought under 250 words. Do not engage in lengthy internal monologues, exhaustive scenario mapping, or speculative tangents.]'
  } else if (reasoningEffort === 'medium') {
    effectiveSystemPrompt += '\n[Reasoning Budget: Medium (~8000 tokens). Keep your internal reasoning balanced, direct, and focused on essential analysis without excessive verbosity.]'
  } else if (reasoningEffort === 'high') {
    effectiveSystemPrompt += '\n[Reasoning Budget: High (~32000 tokens). You have full allowance for thorough, deep chain-of-thought exploration, rigorous scenario analysis, and multi-perspective verification.]'
  } else if (reasoningEffort === 'off') {
    effectiveSystemPrompt += '\n[Reasoning: Disabled. Answer directly and immediately with zero internal monologue or thinking scratchpad.]'
  }

  // Retain recent context window (last 30 turns) matching Android MAX_CONTEXT_MESSAGES
  const historyToInclude = messages.length > 30 ? messages.slice(-30) : messages
  const hasImagesInConversation = historyToInclude.some(
    (m) => m.imagesBase64 && m.imagesBase64.length > 0
  )

  const formattedHistory = historyToInclude.map((m) => {
    if (m.role === 'user' && m.imagesBase64 && m.imagesBase64.length > 0) {
      const parts: Array<{ type: string; text?: string; image_url?: { url: string } }> = [
        { type: 'text', text: m.content || 'Please analyze the attached image(s).' },
      ]
      for (const b64 of m.imagesBase64) {
        const url =
          b64.startsWith('http://') || b64.startsWith('https://') || b64.startsWith('data:')
            ? b64
            : `data:image/jpeg;base64,${b64}`
        parts.push({
          type: 'image_url',
          image_url: { url },
        })
      }
      return { role: m.role, content: parts }
    }
    return { role: m.role, content: m.content || '...' }
  })

  let currentMessages: Array<{ role: string; content: any }> = [
    { role: 'system', content: effectiveSystemPrompt },
    ...formattedHistory,
  ]

  const budgetTokensMap: Record<string, number | null> = {
    low: 1024,
    medium: 8192,
    high: 32768,
    off: null,
  }
  const budget = budgetTokensMap[reasoningEffort] ?? (reasoningEffort === 'off' ? null : 8192)

  const maxTokensMap: Record<string, number> = {
    low: 4096,
    medium: 16384,
    high: 65536,
    off: 4096,
  }

  let toolExecutionRounds = 0
  const maxToolExecutionRounds = 3

  while (toolExecutionRounds <= maxToolExecutionRounds) {
    if (signal?.aborted) return

    const bodyPayload: Record<string, any> = {
      model,
      messages: currentMessages,
      stream: true,
      temperature: 0.7,
      max_completion_tokens: maxTokensMap[reasoningEffort] || 16384,
    }

    if (toolExecutionRounds < maxToolExecutionRounds && !hasImagesInConversation) {
      bodyPayload.tools = [TAVILY_SEARCH_TOOL, EXA_SEARCH_TOOL]
      bodyPayload.tool_choice = 'auto'
    }

    if (reasoningEffort === 'off') {
      bodyPayload.thinking = { type: 'disabled' }
      bodyPayload.enable_thinking = false
    } else {
      bodyPayload.thinking = {
        type: 'enabled',
        ...(budget ? { budget_tokens: budget } : {}),
      }
      if (!budget) {
        bodyPayload.reasoning_effort = reasoningEffort
      }
      if (budget) {
        bodyPayload.thinking_budget = budget
      }
      bodyPayload.enable_thinking = true
    }

    const endpoints = getCandidateEndpoints(cleanKey, baseUrl)
    let lastError = ''
    let roundContentAccumulator = ''
    let roundReasoningAccumulator = ''
    let toolCallName = ''
    let toolCallArgs = ''
    let isStreamSuccess = false

    const parser = new ThinkAndToolParser()

    for (const endpoint of endpoints) {
      if (signal?.aborted) return

      try {
        const response = await fetch(endpoint, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${cleanKey}`,
            'api-key': cleanKey,
            Accept: 'text/event-stream',
          },
          body: JSON.stringify(bodyPayload),
          signal,
        })

        if (!response.ok) {
          const errText = await response.text()
          lastError = `API Error (${response.status}) from ${endpoint}: ${errText}`
          console.warn(lastError)
          continue
        }

        const reader = response.body?.getReader()
        if (!reader) {
          lastError = 'No response body received from server'
          continue
        }

        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
          if (signal?.aborted) {
            reader.cancel().catch(() => {})
            return
          }

          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''

          for (const line of lines) {
            const trimmed = line.trim()
            if (!trimmed || !trimmed.startsWith('data:')) continue
            const dataStr = trimmed.replace(/^data:\s*/, '')
            if (dataStr === '[DONE]') {
              isStreamSuccess = true
              break
            }

            try {
              const parsed = JSON.parse(dataStr)
              if (parsed.error) {
                const msg = parsed.error.message || JSON.stringify(parsed.error)
                yield { error: `MiMo Error: ${msg}` }
                return
              }

              const delta = parsed.choices?.[0]?.delta
              if (delta) {
                isStreamSuccess = true

                // 1. Native OpenAI tool call streaming
                if (delta.tool_calls) {
                  for (const tc of delta.tool_calls) {
                    if (tc.function?.name) {
                      toolCallName = tc.function.name
                    }
                    if (tc.function?.arguments) {
                      toolCallArgs += tc.function.arguments
                    }
                  }
                }

                // 2. Native reasoning delta
                const reasoningText = delta.reasoning_content || delta.reasoning
                if (reasoningEffort !== 'off' && reasoningText && reasoningText !== 'null') {
                  roundReasoningAccumulator += reasoningText
                  yield { reasoning: reasoningText }
                }

                // 3. Content streaming through ThinkAndToolParser
                if (delta.content && delta.content !== 'null') {
                  const contentChunks: string[] = []
                  const reasoningChunks: string[] = []

                  parser.feed(
                    delta.content,
                    (c) => contentChunks.push(c),
                    (r) => reasoningChunks.push(r)
                  )

                  if (reasoningEffort !== 'off') {
                    for (const r of reasoningChunks) {
                      roundReasoningAccumulator += r
                      yield { reasoning: r }
                    }
                  }
                  for (const c of contentChunks) {
                    roundContentAccumulator += c
                    yield { content: c }
                  }
                }
              }
            } catch {
              // Ignore incomplete SSE json lines
            }
          }
        }

        // Flush remaining buffer in parser
        const remContent: string[] = []
        const remReasoning: string[] = []
        parser.flush(
          (c) => remContent.push(c),
          (r) => remReasoning.push(r)
        )
        if (reasoningEffort !== 'off') {
          for (const r of remReasoning) {
            roundReasoningAccumulator += r
            yield { reasoning: r }
          }
        }
        for (const c of remContent) {
          roundContentAccumulator += c
          yield { content: c }
        }

        if (isStreamSuccess) {
          break
        }
      } catch (err: any) {
        if (err.name === 'AbortError' || signal?.aborted) {
          return
        }
        lastError = err.message || 'Network request failed'
        console.warn(`Attempt with ${endpoint} failed:`, err)
      }
    }

    if (signal?.aborted) return

    const parserToolContent = parser.getToolCallContent()
    if (!isStreamSuccess && !roundContentAccumulator && !toolCallArgs && !parserToolContent) {
      yield { error: lastError || 'Unable to connect to Xiaomi MiMo API. Check your API key.' }
      return
    }

    // Check if model autonomously invoked web search tool (either via native args, parser XML buffer, or content)
    let extracted = extractToolCall(toolCallArgs, toolCallName)
    if (!extracted && parserToolContent) {
      extracted = extractToolCall(parserToolContent, toolCallName)
    }
    if (!extracted) {
      extracted = extractToolCall(roundContentAccumulator, toolCallName)
    }

    if (extracted && toolExecutionRounds < maxToolExecutionRounds) {
      toolExecutionRounds++
      const { tool, query } = extracted
      const hasTavilyKey = Boolean(localStorage.getItem('wyrd_tavily_api_key')?.trim())
      let isTavily = tool === 'tavily_search' || (tool === 'web_search' && hasTavilyKey)
      if (isTavily && isReviewOrForumQuery(query)) {
        console.log(`Auto-switching tool from Tavily to Exa for review/forum query: "${query}"`)
        isTavily = false
      }
      let engineName = isTavily ? 'Tavily' : 'Exa'
      const toolFunctionName = isTavily ? 'tavily_search' : 'exa_search'

      // Clear any partial content from round 1 so XML or preamble never leaks
      yield { clearContent: true }
      const statusPrefix = toolExecutionRounds > 1 ? 'Gathering additional data with ' : 'Searching '
      const statusLabel = isTavily
        ? `${statusPrefix}live web with Tavily`
        : isReviewOrForumQuery(query)
          ? `${statusPrefix}employee reviews & Glassdoor with Exa`
          : `${statusPrefix}web with Exa`
      yield { status: `${statusLabel} for: "${query}"...` }

      let searchOutput: string
      if (isTavily) {
        const tavilyRes = await searchTavilyWeb(query)
        if (tavilyRes.success) {
          searchOutput = tavilyRes.content
        } else {
          // Tavily failed (expired key, quota limit, network down) -> Automatic fallback to Exa!
          console.warn('Tavily search failed, falling back to Exa:', tavilyRes.error)
          yield { status: `Tavily unavailable, falling back to Exa for: "${query}"...` }
          const exaRes = await searchExaWeb(query)
          if (exaRes.success && exaRes.content !== ZERO_RESULTS_MESSAGE) {
            engineName = 'Exa (fallback from Tavily)'
            searchOutput = exaRes.content
          } else {
            searchOutput = exaRes.content || tavilyRes.content || TAVILY_ZERO_RESULTS_MESSAGE
          }
        }
      } else {
        const exaRes = await searchExaWeb(query)
        if (exaRes.success && exaRes.content !== ZERO_RESULTS_MESSAGE) {
          searchOutput = exaRes.content
        } else if (!exaRes.success && hasTavilyKey) {
          // Exa failed (e.g. MCP down/rate limit) and Tavily key is available -> Automatic fallback to Tavily!
          console.warn('Exa search failed, falling back to Tavily:', exaRes.error)
          yield { status: `Exa unavailable, falling back to Tavily for: "${query}"...` }
          const tavilyRes = await searchTavilyWeb(query)
          if (tavilyRes.success) {
            engineName = 'Tavily (fallback from Exa)'
            searchOutput = tavilyRes.content
          } else {
            searchOutput = exaRes.content || ZERO_RESULTS_MESSAGE
          }
        } else {
          searchOutput = exaRes.content || ZERO_RESULTS_MESSAGE
        }
      }

      yield { status: 'Synthesizing answer...' }

      const nextInstruction =
        toolExecutionRounds < maxToolExecutionRounds
          ? isTavily
            ? `[INSTRUCTION: Round ${toolExecutionRounds} results from Tavily (corporate/news) received. CRITICAL: If the user request or project directives ask for employee sentiment, workplace culture, Glassdoor/Reddit reviews, or interview experiences, you MUST now invoke \`exa_search\` (e.g. <tool_call><function=exa_search><parameter=query>Company Name employee reviews Glassdoor Reddit</parameter></function></tool_call>). Do NOT call tavily_search again for reviews or forums. If all required information across both business and employee sentiment is already complete, provide your final response now without calling further tools.]`
            : `[INSTRUCTION: Round ${toolExecutionRounds} results from Exa (reviews/sentiment) received. If you still need breaking news, recent corporate announcements, or official company records, you may invoke \`tavily_search\`. If all required information is already complete, provide your final response now without calling further tools.]`
          : `[INSTRUCTION: Web search is now COMPLETE. Do NOT invoke any further tools or emit <tool_call> tags. Directly, accurately, and thoroughly answer the user's question using all gathered search results and your knowledge.]`

      // Append assistant's tool call and search context for direct answer synthesis
      currentMessages = [
        ...currentMessages,
        {
          role: 'assistant',
          content: `<tool_call><function=${toolFunctionName}><parameter=query>${query}</parameter></function></tool_call>`,
        },
        {
          role: 'user',
          content: `[Web Search Results (via ${engineName}) for "${query}"]:\n${searchOutput}\n\n${nextInstruction}`,
        },
      ]

      continue
    }

    // Completed normally without further tool calls
    yield { done: true }
    return
  }

  yield { done: true }
}

export function sanitizeStreamedContent(text: string): string {
  let cleaned = text
  cleaned = cleaned.replace(/<think>[\s\S]*?<\/think>/gi, '')
  cleaned = cleaned.replace(/<thought>[\s\S]*?<\/thought>/gi, '')
  cleaned = cleaned.replace(/<thinking>[\s\S]*?<\/thinking>/gi, '')
  cleaned = cleaned.replace(/<tool_call>[\s\S]*?<\/tool_call>/gi, '')
  cleaned = cleaned.replace(/<function_call>[\s\S]*?<\/function_call>/gi, '')
  cleaned = cleaned.replace(/<\/?(?:think|thought|thinking|tool_call|function_call|function|parameter)[^>]*>/gi, '')
  return cleaned.trim()
}

export function sanitizeReasoning(text: string): string {
  let cleaned = text
  cleaned = cleaned.replace(/<\/?(?:think|thought|thinking)[^>]*>/gi, '')
  return cleaned.trim()
}
