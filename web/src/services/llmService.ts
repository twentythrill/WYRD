import { searchExaWeb, ZERO_RESULTS_MESSAGE } from './exaSearchService'

export interface StreamChunk {
  content?: string
  reasoning?: string
  status?: string
  clearContent?: boolean
  done?: boolean
  error?: string
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
          description: 'The search query to look up on the web via Exa',
        },
      },
      required: ['query'],
    },
  },
}

export function extractToolQuery(text: string): string | null {
  const trimmed = text.trim()
  if (!trimmed) return null

  // 1. XML parameter syntax: <parameter=query>...</parameter> or <parameter name="query">...</parameter>
  const paramRegex = /<parameter\s*(?:=|name\s*=\s*["']?)query["']?>\s*([^<]+)\s*<\/parameter>/i
  const paramMatch = trimmed.match(paramRegex)
  if (paramMatch?.[1]?.trim()) return paramMatch[1].trim()

  // 2. Direct tag syntax: <query>...</query>
  const queryTagRegex = /<query>\s*([^<]+)\s*<\/query>/i
  const queryTagMatch = trimmed.match(queryTagRegex)
  if (queryTagMatch?.[1]?.trim()) return queryTagMatch[1].trim()

  // 3. JSON function arguments syntax: {"query": "..."}
  const jsonQueryRegex = /["']query["']\s*:\s*["']([^"'\n\r]+)["']/i
  const jsonMatch = trimmed.match(jsonQueryRegex)
  if (jsonMatch?.[1]?.trim()) return jsonMatch[1].trim()

  // 4. Function call syntax: web_search("...") or web_search(query="...") or call:web_search(...)
  const fnRegex = /(?:call:)?web_search\s*\(\s*(?:(?:query|q)\s*=\s*)?["']([^"'\n\r]+)["']\s*\)/i
  const fnMatch = trimmed.match(fnRegex)
  if (fnMatch?.[1]?.trim()) return fnMatch[1].trim()

  // 5. Action bracket syntax: [web_search: ...] or [ACTION: web_search("...")]
  const bracketRegex = /\[(?:ACTION:\s*)?web_search[\s:(]+["']?([^"'\])\n\r]+)["']?[)\]]/i
  const bracketMatch = trimmed.match(bracketRegex)
  if (bracketMatch?.[1]?.trim()) return bracketMatch[1].trim()

  return null
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
  effectiveSystemPrompt += `\n[Web Search Tool: You have access to a live web search tool named \`web_search\` (which calls the Exa search engine). Searches the web for recent events and factual corroboration. Returns URLs, publication dates, and extracted snippets. If the returned snippets do not confirm the claim, treat the claim as unverified. You are in charge of deciding autonomously whether to use web search for the user's answer. If the user's question requires real-time facts, current news, recent releases/updates (e.g. game patch notes, versions), live data (weather, stocks, scores), or facts beyond your knowledge cutoff, you should invoke web search using:\n<tool_call><function=web_search><parameter=query>your search query</parameter></function></tool_call>\nIf the request does not require external search, answer directly without invoking tools. Never tell the user to use manual search commands like /search.]`

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
  const maxToolExecutionRounds = 1

  while (toolExecutionRounds <= maxToolExecutionRounds) {
    if (signal?.aborted) return

    const bodyPayload: Record<string, any> = {
      model,
      messages: currentMessages,
      stream: true,
      temperature: 0.7,
      max_completion_tokens: maxTokensMap[reasoningEffort] || 16384,
    }

    if (toolExecutionRounds === 0 && !hasImagesInConversation) {
      bodyPayload.tools = [WEB_SEARCH_TOOL]
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

    // Check if model autonomously invoked web_search tool (either via native args, parser XML buffer, or content)
    let extractedQuery = extractToolQuery(toolCallArgs)
    if (!extractedQuery && parserToolContent) {
      extractedQuery = extractToolQuery(parserToolContent)
    }
    if (!extractedQuery) {
      extractedQuery = extractToolQuery(roundContentAccumulator)
    }

    if (extractedQuery && toolExecutionRounds < maxToolExecutionRounds) {
      toolExecutionRounds++

      // Clear any partial content from round 1 so XML or preamble never leaks
      yield { clearContent: true }
      yield { status: `Searching web with Exa for: "${extractedQuery}"...` }

      const searchRes = await searchExaWeb(extractedQuery)
      const searchOutput = searchRes.content || ZERO_RESULTS_MESSAGE

      yield { status: 'Synthesizing answer...' }

      // Append assistant's tool call and Exa search context for direct answer synthesis
      currentMessages = [
        ...currentMessages,
        {
          role: 'assistant',
          content: `<tool_call><function=web_search><parameter=query>${extractedQuery}</parameter></function></tool_call>`,
        },
        {
          role: 'user',
          content: `[Web Search Results (via Exa) for "${extractedQuery}"]:\n${searchOutput}\n\n[INSTRUCTION: Web search via Exa is now COMPLETE. Do NOT invoke web_search or emit any <tool_call> tags. Directly, accurately, and thoroughly answer the user's question using the search results above and your knowledge.]`,
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
