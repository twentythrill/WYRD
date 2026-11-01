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

export const WEB_SEARCH_TOOL = {
  type: 'function',
  function: {
    name: 'web_search',
    description:
      'Searches the web for up-to-date facts, breaking news, company information, community discussions, forum opinions, and user reviews.',
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

const PARAM_QUERY_REGEX = /<parameter\s*(?:=|name\s*=\s*["']?)query["']?>\s*([^<]+)\s*<\/parameter>/i
const QUERY_TAG_REGEX = /<(?:query|search_query)>\s*([^<]+)\s*<\/(?:query|search_query)>/i
const JSON_QUERY_REGEX = /["'](?:query|search_query)["']\s*:\s*["']([^"'\n\r]+)["']/i
const FN_QUERY_REGEX = /(?:call:)?(?:tavily_search|exa_search|web_search)\s*\(\s*(?:(?:query|q)\s*=\s*)?["']([^"'\n\r]+)["']\s*\)/i
const BRACKET_QUERY_REGEX = /\[(?:ACTION:\s*)?(?:tavily_search|exa_search|web_search)[\s:(]+["']?([^"'\])\n\r]+)["']?[)\]]/i

export function extractToolCall(text: string, explicitToolName?: string): ExtractedToolCall | null {
  const trimmed = text.trim()
  if (!trimmed) return null

  let tool: SearchToolType = 'web_search'
  if (explicitToolName && /exa_search/i.test(explicitToolName)) {
    tool = 'exa_search'
  } else if (explicitToolName && /tavily_search/i.test(explicitToolName)) {
    tool = 'tavily_search'
  } else if (/exa_search/i.test(trimmed)) {
    tool = 'exa_search'
  } else if (/tavily_search/i.test(trimmed)) {
    tool = 'tavily_search'
  }

  const paramMatch = trimmed.match(PARAM_QUERY_REGEX)
  if (paramMatch?.[1]?.trim()) {
    return { tool, query: paramMatch[1].trim() }
  }

  const queryTagMatch = trimmed.match(QUERY_TAG_REGEX)
  if (queryTagMatch?.[1]?.trim()) {
    return { tool, query: queryTagMatch[1].trim() }
  }

  const jsonMatch = trimmed.match(JSON_QUERY_REGEX)
  if (jsonMatch?.[1]?.trim()) {
    return { tool, query: jsonMatch[1].trim() }
  }

  const fnMatch = trimmed.match(FN_QUERY_REGEX)
  if (fnMatch?.[1]?.trim()) {
    return { tool, query: fnMatch[1].trim() }
  }

  const bracketMatch = trimmed.match(BRACKET_QUERY_REGEX)
  if (bracketMatch?.[1]?.trim()) {
    return { tool, query: bracketMatch[1].trim() }
  }

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
            this.buffer = this.buffer.replace(/^[\r\n]+/, '')
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
const PROXY_OPENAI_RESPONSES_ENDPOINT = '/api/openai/v1/responses'
const PROXY_OPENAI_CHAT_COMPLETIONS_ENDPOINT = '/api/openai/v1/chat/completions'
const OPENAI_RESPONSES_ENDPOINT = 'https://api.openai.com/v1/responses'
const OPENAI_CHAT_COMPLETIONS_ENDPOINT = 'https://api.openai.com/v1/chat/completions'

function getCandidateEndpoints(apiKey: string, customBaseUrl?: string, model?: string): string[] {
  const trimmed = customBaseUrl?.trim()
  if (trimmed && trimmed !== 'https://api.siliconflow.cn/v1') {
    const clean = trimmed.replace(/\/+$/, '')
    if (clean.endsWith('/responses') || clean.endsWith('/chat/completions')) {
      return [clean]
    }
    return model === 'gpt-6.1-sol'
      ? [`${clean}/responses`, `${clean}/chat/completions`]
      : [`${clean}/chat/completions`]
  }

  if (model === 'gpt-6.1-sol') {
    return [
      PROXY_OPENAI_RESPONSES_ENDPOINT,
      PROXY_OPENAI_CHAT_COMPLETIONS_ENDPOINT,
      OPENAI_RESPONSES_ENDPOINT,
      OPENAI_CHAT_COMPLETIONS_ENDPOINT,
    ]
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
  isThinkingEnabled,
  reasoningEffort = 'medium',
  systemPrompt = '',
  messages = [],
  signal,
}: {
  apiKey: string
  baseUrl?: string
  model?: string
  isThinkingEnabled?: boolean
  reasoningEffort?: string
  systemPrompt?: string
  messages: Array<{
    role: 'user' | 'assistant' | 'system'
    content: string
    imagesBase64?: string[]
  }>
  signal?: AbortSignal
}): AsyncGenerator<StreamChunk, void, unknown> {
  const isSol = model === 'gpt-6.1-sol'
  const effectiveKey = (isSol ? (localStorage.getItem('wyrd_openai_api_key') || apiKey) : apiKey)?.trim() || ''
  if (!effectiveKey) {
    const providerMsg = isSol
      ? 'Please add your OpenAI API key in **Settings** to generate replies with GPT 6.1 Sol in the browser.'
      : 'Please add your Xiaomi MiMo API key in **Settings** to generate replies directly in the browser.'
    yield {
      content:
        `**API Key Required for Web Generation**\n\n${providerMsg}\n\n*Note: Any conversations created or replied to on your WYRD Android app will sync here in real-time automatically.*`,
      done: true,
    }
    return
  }

  const isThinking = typeof isThinkingEnabled === 'boolean'
    ? isThinkingEnabled
    : reasoningEffort !== 'off'

  const isTavilyActive =
    localStorage.getItem('wyrd_tavily_enabled') !== 'false' &&
    Boolean(localStorage.getItem('wyrd_tavily_api_key')?.trim())
  const isExaExplicitlyDisabled = localStorage.getItem('wyrd_exa_enabled') === 'false'
  const isExaActive = !isExaExplicitlyDisabled
  const isSearchEnabled = true

  const currentDateTimeStr = new Date().toLocaleString()
  const temporalContext = `Current Device Date and Time: ${currentDateTimeStr}`

  let effectiveSystemPrompt = ''
  if (systemPrompt && systemPrompt.trim().length > 0) {
    effectiveSystemPrompt += `${systemPrompt.trim()}\n\n`
  }
  effectiveSystemPrompt += `[Context: ${temporalContext}]`
  if (isSearchEnabled) {
    effectiveSystemPrompt += `\n[Autonomous Web Search: You have access to a web search tool to ground your answers in verified external facts. You are in charge of deciding autonomously whether to use web search.
- \`web_search\`: Searches the web for live facts, current events, official documentation, community discussions, and user reviews. Invoke with:
<tool_call><function=web_search><parameter=query>your search query</parameter></function></tool_call>
If the request does not require external search, answer directly without invoking tools. Never tell the user to use manual search commands like /search.]`
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

  let currentMessages: Array<{ role: string; content: unknown }> = [
    { role: 'system', content: effectiveSystemPrompt },
    ...formattedHistory,
  ]

  let toolExecutionRounds = 0
  const maxToolExecutionRounds = isSearchEnabled ? 3 : 0

  while (toolExecutionRounds <= maxToolExecutionRounds) {
    if (signal?.aborted) return

    const endpoints = getCandidateEndpoints(effectiveKey, baseUrl, model)
    let lastError = ''
    let roundContentAccumulator = ''
    let hasEmittedReasoning = false
    let toolCallName = ''
    let toolCallArgs = ''
    let isStreamSuccess = false

    const parser = new ThinkAndToolParser()

    for (const endpoint of endpoints) {
      if (signal?.aborted) return

      const isResponsesApi = endpoint.includes('/responses')
      let bodyPayload: Record<string, unknown>

      if (isResponsesApi) {
        const inputItems = currentMessages
          .filter((m) => m.role !== 'system')
          .map((m) => {
            if (Array.isArray(m.content)) {
              const parts = m.content.map((p) => {
                if (p.type === 'text') {
                  return { type: 'input_text', text: p.text }
                }
                if (p.type === 'image_url') {
                  return { type: 'input_image', image_url: p.image_url?.url || '' }
                }
                return p
              })
              return { role: m.role, content: parts }
            }
            return { role: m.role, content: m.content }
          })

        bodyPayload = {
          model,
          stream: true,
          max_output_tokens: isThinking ? 32768 : 8192,
          ...(effectiveSystemPrompt ? { instructions: effectiveSystemPrompt } : {}),
          input: inputItems,
          reasoning: isThinking
            ? { mode: 'pro', effort: 'medium', summary: 'detailed' }
            : { mode: 'standard', effort: 'low' },
        }

        if (isSearchEnabled && toolExecutionRounds < maxToolExecutionRounds && !hasImagesInConversation) {
          bodyPayload.tools = [
            {
              type: 'function',
              name: 'web_search',
              description:
                'Searches the web for up-to-date facts, breaking news, company information, community discussions, forum opinions, and user reviews.',
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
          ]
          bodyPayload.tool_choice = 'auto'
        }
      } else {
        bodyPayload = {
          model,
          messages: currentMessages,
          stream: true,
          max_completion_tokens: isThinking ? 32768 : 8192,
        }

        if (isSearchEnabled && toolExecutionRounds < maxToolExecutionRounds && !hasImagesInConversation) {
          if (!isSol || !isThinking) {
            bodyPayload.tools = [WEB_SEARCH_TOOL]
            bodyPayload.tool_choice = 'auto'
          }
        }

        if (isSol) {
          bodyPayload.reasoning_effort = isThinking ? 'medium' : 'low'
        } else {
          bodyPayload.temperature = isThinking ? 1.0 : 0.7
          if (isThinking) {
            bodyPayload.thinking = { type: 'enabled' }
            bodyPayload.extra_body = { thinking: { type: 'enabled' } }
            bodyPayload.enable_thinking = true
          } else {
            bodyPayload.thinking = { type: 'disabled' }
            bodyPayload.extra_body = { thinking: { type: 'disabled' } }
            bodyPayload.enable_thinking = false
          }
        }
      }

      try {
        const response = await fetch(endpoint, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${effectiveKey}`,
            ...(isSol ? {} : { 'api-key': effectiveKey }),
            Accept: 'text/event-stream',
          },
          body: JSON.stringify(bodyPayload),
          signal,
        })

        if (!response.ok) {
          const errText = await response.text()
          lastError = `API Error (${response.status}) from ${endpoint}: ${errText}`
          console.warn(lastError)
          if (response.status === 401 || response.status === 403) {
            break
          }
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
                yield { error: `API Error: ${msg}` }
                return
              }

              const eventType = parsed.type || ''

              // 1. Responses API: reasoning summary or reasoning text deltas
              if (eventType === 'response.reasoning_summary_text.delta' || eventType === 'response.reasoning_text.delta') {
                isStreamSuccess = true
                const reasoningText = typeof parsed.delta === 'string'
                  ? parsed.delta
                  : (parsed.delta?.text || '')
                if (isThinking && reasoningText && reasoningText !== 'null') {
                  hasEmittedReasoning = true
                  yield { reasoning: reasoningText }
                }
              }
              // 2. Responses API: output text deltas
              else if (eventType === 'response.output_text.delta' || eventType === 'response.text.delta') {
                isStreamSuccess = true
                const deltaText = typeof parsed.delta === 'string' ? parsed.delta : (parsed.text || '')
                if (deltaText && deltaText !== 'null') {
                  const contentChunks: string[] = []
                  const reasoningChunks: string[] = []

                  parser.feed(
                    deltaText,
                    (c) => contentChunks.push(c),
                    (r) => {
                      hasEmittedReasoning = true
                      reasoningChunks.push(r)
                    }
                  )

                  if (isThinking) {
                    for (const r of reasoningChunks) {
                      yield { reasoning: r }
                    }
                  }
                  for (const c of contentChunks) {
                    roundContentAccumulator += c
                    yield { content: c }
                  }
                }
              }
              // 3. Responses API: function call output item added
              else if (eventType === 'response.output_item.added') {
                if (parsed.item?.type === 'function_call') {
                  if (parsed.item.name) toolCallName = parsed.item.name
                  if (parsed.item.arguments) toolCallArgs += parsed.item.arguments
                }
              }
              // 4. Responses API: function call arguments delta
              else if (eventType === 'response.function_call_arguments.delta') {
                if (parsed.delta) toolCallArgs += parsed.delta
              }
              // 5. Responses API: function call or reasoning output item done fallback
              else if (eventType === 'response.output_item.done') {
                if (parsed.item?.type === 'function_call') {
                  if (!toolCallName && parsed.item.name) toolCallName = parsed.item.name
                  if (!toolCallArgs && parsed.item.arguments) toolCallArgs += parsed.item.arguments
                } else if (parsed.item?.type === 'reasoning' && !hasEmittedReasoning) {
                  const summaryArr = parsed.item.summary
                  if (Array.isArray(summaryArr) && summaryArr.length > 0) {
                    const fullSummary = summaryArr
                      .map((s: { text?: string } | string) => (typeof s === 'string' ? s : s?.text || ''))
                      .join('')
                    if (isThinking && fullSummary) {
                      hasEmittedReasoning = true
                      yield { reasoning: fullSummary }
                    }
                  }
                }
              }
              // 6. Responses API: completed / done fallback
              else if (eventType === 'response.completed' || eventType === 'response.done') {
                isStreamSuccess = true
                const outputArr = parsed.response?.output
                if (Array.isArray(outputArr) && !hasEmittedReasoning) {
                  for (const item of outputArr) {
                    if (item?.type === 'reasoning' && Array.isArray(item.summary) && item.summary.length > 0) {
                      const fullSummary = item.summary
                        .map((s: { text?: string } | string) => (typeof s === 'string' ? s : s?.text || ''))
                        .join('')
                      if (isThinking && fullSummary) {
                        hasEmittedReasoning = true
                        yield { reasoning: fullSummary }
                      }
                    }
                  }
                }
                if (isThinking && !hasEmittedReasoning) {
                  const reasoningTokens = parsed.response?.usage?.output_tokens_details?.reasoning_tokens || 0
                  if (reasoningTokens > 0) {
                    hasEmittedReasoning = true
                    yield { reasoning: `Thought process completed (${reasoningTokens} reasoning tokens used).` }
                  }
                }
              }
              // 7. Chat Completions API fallback: choices array
              else {
                const delta = parsed.choices?.[0]?.delta
                if (delta) {
                  isStreamSuccess = true

                  // Native OpenAI tool call streaming
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

                  // Native reasoning delta
                  const reasoningText = delta.reasoning_content || delta.reasoning
                  if (isThinking && reasoningText && reasoningText !== 'null') {
                    hasEmittedReasoning = true
                    yield { reasoning: reasoningText }
                  }

                  // Content streaming through ThinkAndToolParser
                  if (delta.content && delta.content !== 'null') {
                    const contentChunks: string[] = []
                    const reasoningChunks: string[] = []

                    parser.feed(
                      delta.content,
                      (c) => contentChunks.push(c),
                      (r) => reasoningChunks.push(r)
                    )

                    if (isThinking) {
                      for (const r of reasoningChunks) {
                        yield { reasoning: r }
                      }
                    }
                    for (const c of contentChunks) {
                      roundContentAccumulator += c
                      yield { content: c }
                    }
                  }
                }

                if (parsed.usage && isThinking && !hasEmittedReasoning) {
                  const reasoningTokens = parsed.usage.completion_tokens_details?.reasoning_tokens || 0
                  if (reasoningTokens > 0) {
                    hasEmittedReasoning = true
                    yield { reasoning: `Thought process completed (${reasoningTokens} reasoning tokens used).` }
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
        if (isThinking) {
          for (const r of remReasoning) {
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
      } catch (err: unknown) {
        if ((err instanceof Error && err.name === 'AbortError') || signal?.aborted) {
          return
        }
        const errMsg = err instanceof Error ? err.message : 'Network request failed'
        if (isSol && (errMsg.includes('Failed to fetch') || errMsg.includes('NetworkError') || errMsg.includes('CORS'))) {
          lastError = 'Browser CORS restriction: OpenAI blocks direct browser requests to api.openai.com. Please run with the dev proxy (npm run dev) or configure a CORS-friendly OpenAI Base URL in Settings.'
        } else {
          lastError = errMsg
        }
        console.warn(`Attempt with ${endpoint} failed:`, err)
      }
    }

    if (signal?.aborted) return

    const parserToolContent = parser.getToolCallContent()
    if (!isStreamSuccess && !roundContentAccumulator && !toolCallArgs && !parserToolContent) {
      const defaultMsg = isSol
        ? 'Unable to connect to OpenAI API. Check your API key or proxy settings.'
        : 'Unable to connect to Xiaomi MiMo API. Check your API key.'
      yield { error: lastError || defaultMsg }
      return
    }

    // Check if model autonomously invoked web search tool (either via native args, parser XML buffer, or content)
    let extracted = isSearchEnabled ? extractToolCall(toolCallArgs, toolCallName) : null
    if (isSearchEnabled && !extracted && parserToolContent) {
      extracted = extractToolCall(parserToolContent, toolCallName)
    }
    if (isSearchEnabled && !extracted) {
      extracted = extractToolCall(roundContentAccumulator, toolCallName)
    }

    if (extracted && toolExecutionRounds < maxToolExecutionRounds) {
      toolExecutionRounds++
      const { query } = extracted

      // Clear any partial content from round 1 so XML or preamble never leaks
      yield { clearContent: true }
      const statusPrefix = toolExecutionRounds > 1 ? 'Gathering additional data for ' : 'Searching web for '
      const shortQuery = query.length > 40 ? query.slice(0, 37) + '...' : query
      yield { status: `${statusPrefix}"${shortQuery}"...` }

      let searchOutput = ''

      const executeExaFallback = async (useKey: boolean = true): Promise<string> => {
        const keyToUse = useKey ? (localStorage.getItem('wyrd_exa_api_key')?.trim() || undefined) : ''
        const res = await searchExaWeb(query, keyToUse).catch(() => ({ success: false, content: '' }))
        if (res.success && res.content && res.content !== ZERO_RESULTS_MESSAGE) {
          return res.content
        }
        if (useKey && keyToUse) {
          const keylessRes = await searchExaWeb(query, '').catch(() => ({ success: false, content: '' }))
          if (keylessRes.success && keylessRes.content && keylessRes.content !== ZERO_RESULTS_MESSAGE) {
            return keylessRes.content
          }
        }
        return res.content || ZERO_RESULTS_MESSAGE
      }

      if (isTavilyActive && isExaActive) {
        const [tavilyRes, exaRes] = await Promise.all([
          searchTavilyWeb(query).catch((e) => ({ success: false, content: '', error: String(e) })),
          searchExaWeb(query).catch((e) => ({ success: false, content: '', error: String(e) })),
        ])

        const tavilyText =
          tavilyRes.success &&
          tavilyRes.content &&
          tavilyRes.content !== TAVILY_ZERO_RESULTS_MESSAGE
            ? tavilyRes.content
            : null

        let exaText =
          exaRes.success &&
          exaRes.content &&
          exaRes.content !== ZERO_RESULTS_MESSAGE
            ? exaRes.content
            : null

        if (!exaText) {
          const mcpFallback = await searchExaWeb(query, '').catch(() => null)
          if (mcpFallback?.success && mcpFallback.content && mcpFallback.content !== ZERO_RESULTS_MESSAGE) {
            exaText = mcpFallback.content
          }
        }

        if (tavilyText && exaText) {
          searchOutput = `### Live Web & Official Sources:\n${tavilyText}\n\n---\n\n### Community Discussions, Reviews & Semantic Results:\n${exaText}`
        } else if (exaText) {
          searchOutput = exaText
        } else if (tavilyText) {
          searchOutput = tavilyText
        } else {
          searchOutput = await executeExaFallback(false)
        }
      } else if (isTavilyActive) {
        const tavilyRes = await searchTavilyWeb(query).catch((e) => ({ success: false, content: '', error: String(e) }))
        const tavilyText =
          tavilyRes.success &&
          tavilyRes.content &&
          tavilyRes.content !== TAVILY_ZERO_RESULTS_MESSAGE
            ? tavilyRes.content
            : null

        if (tavilyText) {
          searchOutput = tavilyText
        } else if (!isExaExplicitlyDisabled) {
          searchOutput = await executeExaFallback(true)
        } else {
          searchOutput = await executeExaFallback(false)
        }
      } else if (isExaActive) {
        searchOutput = await executeExaFallback(true)
      } else {
        searchOutput = await executeExaFallback(false)
      }

      yield { status: 'Synthesizing answer...' }

      const nextInstruction =
        toolExecutionRounds < maxToolExecutionRounds
          ? `[INSTRUCTION: Review the search results above against the user's prompt. If the retrieved evidence is complete and sufficient to answer all aspects of the request, provide your final response now without calling further tools. If crucial details are still missing or incomplete, you may invoke web_search again with a refined query to gather the remaining evidence.]`
          : `[INSTRUCTION: Web search is now COMPLETE. Do NOT invoke any further tools or emit <tool_call> tags. Directly, accurately, and thoroughly answer the user's question using all gathered search results and your knowledge.]`

      // Append assistant's tool call and search context for direct answer synthesis
      currentMessages = [
        ...currentMessages,
        {
          role: 'assistant',
          content: `<tool_call><function=web_search><parameter=query>${query}</parameter></function></tool_call>`,
        },
        {
          role: 'user',
          content: `[Web Search Results for "${query}"]:\n${searchOutput}\n\n${nextInstruction}`,
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

const THINK_BLOCK_REGEX = /<think>[\s\S]*?<\/think>/gi
const THOUGHT_BLOCK_REGEX = /<thought>[\s\S]*?<\/thought>/gi
const THINKING_BLOCK_REGEX = /<thinking>[\s\S]*?<\/thinking>/gi
const TOOL_CALL_BLOCK_REGEX = /<tool_call>[\s\S]*?<\/tool_call>/gi
const FUNCTION_CALL_BLOCK_REGEX = /<function_call>[\s\S]*?<\/function_call>/gi
const RESIDUAL_TAGS_REGEX = /<\/?(?:think|thought|thinking|tool_call|function_call|function|parameter)[^>]*>/gi
const REASONING_TAGS_REGEX = /<\/?(?:think|thought|thinking)[^>]*>/gi
const UNCLOSED_THINK_REGEX = /<(?:think|thought|thinking)>[\s\S]*$/gi
const TITLE_CLEAN_REGEX = /["'*`_#[\]()]/g
const TRAILING_PUNCT_REGEX = /^[.,:;!?-]+|[.,:;!?-]+$/g

export function sanitizeStreamedContent(text: string): string {
  return text
    .replace(THINK_BLOCK_REGEX, '')
    .replace(THOUGHT_BLOCK_REGEX, '')
    .replace(THINKING_BLOCK_REGEX, '')
    .replace(TOOL_CALL_BLOCK_REGEX, '')
    .replace(FUNCTION_CALL_BLOCK_REGEX, '')
    .replace(RESIDUAL_TAGS_REGEX, '')
    .trim()
}

export function sanitizeReasoning(text: string): string {
  return text.replace(REASONING_TAGS_REGEX, '').trim()
}

export function sanitizeChatTitle(raw: string): string {
  const withoutTags = sanitizeStreamedContent(raw).replace(UNCLOSED_THINK_REGEX, '').trim()
  const firstLine =
    withoutTags
      .split('\n')
      .map((l) => l.trim())
      .find((l) => l.length > 0) || ''
  const cleaned = firstLine
    .replace(TITLE_CLEAN_REGEX, '')
    .replace(/^(?:title|summary|topic|chat title|subject|here is a title|here's a title|the title is|conversation title)\s*:\s*/i, '')
    .trim()
  const words = cleaned
    .split(/\s+/)
    .filter((w) => w.length > 0)
    .slice(0, 4)
  if (words.length === 0) return ''
  return words.join(' ').replace(TRAILING_PUNCT_REGEX, '').trim()
}

export async function generateChatTitle({
  apiKey,
  firstUserPrompt,
  assistantReply = '',
  baseUrl = '',
  model = 'mimo-v2.6-pro',
}: {
  apiKey: string
  firstUserPrompt: string
  assistantReply?: string
  baseUrl?: string
  model?: string
}): Promise<string | null> {
  const cleanKey = apiKey?.trim() || ''
  if (!cleanKey || !firstUserPrompt.trim()) return null

  const userTurnSnippet =
    assistantReply && assistantReply.trim() && assistantReply.trim() !== '...'
      ? `User: ${firstUserPrompt.trim().slice(0, 300)}\nAssistant: ${assistantReply.trim().slice(0, 300)}`
      : `User: ${firstUserPrompt.trim().slice(0, 300)}`

  const endpoints = getCandidateEndpoints(cleanKey, baseUrl, model)
  const isSol = model === 'gpt-6.1-sol'
  const streamModes = [false, true]

  for (const endpoint of endpoints) {
    const isResponsesApi = endpoint.includes('/responses')
    for (const useStream of streamModes) {
      try {
        let bodyPayload: Record<string, unknown>

        if (isResponsesApi) {
          bodyPayload = {
            model,
            instructions:
              'You are a conversation title generator. Summarize the core topic of this exchange into a concise 2 to 4 word title. Output ONLY the raw title words without quotes, punctuation, preamble, or markdown.',
            input: [
              {
                role: 'user',
                content: userTurnSnippet,
              },
            ],
            reasoning: { effort: 'low' },
            stream: useStream,
          }
        } else {
          bodyPayload = {
            model,
            stream: useStream,
            max_completion_tokens: 256,
            messages: [
              {
                role: 'system',
                content:
                  'You are a conversation title generator. Summarize the core topic of this exchange into a concise 2 to 4 word title. Output ONLY the raw title words without quotes, punctuation, preamble, or markdown.',
              },
              {
                role: 'user',
                content: userTurnSnippet,
              },
            ],
          }
          if (isSol) {
            bodyPayload.reasoning_effort = 'low'
          } else {
            bodyPayload.temperature = 0.7
            bodyPayload.thinking = { type: 'disabled' }
            bodyPayload.extra_body = { thinking: { type: 'disabled' } }
            bodyPayload.enable_thinking = false
          }
        }

        const headers: Record<string, string> = {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${cleanKey}`,
          ...(isSol ? {} : { 'api-key': cleanKey }),
        }
        if (useStream) {
          headers.Accept = 'text/event-stream'
        }

        const response = await fetch(endpoint, {
          method: 'POST',
          headers,
          body: JSON.stringify(bodyPayload),
        })

        if (!response.ok) {
          if (response.status === 401 || response.status === 403) return null
          continue
        }

        const bodyText = (await response.text()).trim()
        if (!bodyText) continue

        let rawContent = ''
        if (bodyText.startsWith('data:') || bodyText.includes('\ndata:')) {
          for (const line of bodyText.split('\n')) {
            const trimmedLine = line.trim()
            if (!trimmedLine.startsWith('data:')) continue
            const dataStr = trimmedLine.replace(/^data:\s*/, '').trim()
            if (!dataStr || dataStr === '[DONE]') continue
            try {
              const parsed = JSON.parse(dataStr)
              const deltaType = parsed?.type || ''
              if (deltaType === 'response.output_text.delta' || deltaType === 'response.text.delta') {
                const piece = parsed.delta
                if (typeof piece === 'string' && piece !== 'null') {
                  rawContent += piece
                }
              } else {
                const piece = parsed?.choices?.[0]?.delta?.content
                if (typeof piece === 'string' && piece !== 'null') {
                  rawContent += piece
                }
              }
            } catch {
              // ignore malformed SSE chunk
            }
          }
        } else {
          const data = JSON.parse(bodyText)
          const directOutput = data?.output_text
          if (typeof directOutput === 'string' && directOutput.trim()) {
            rawContent = directOutput
          } else {
            const choice = data?.choices?.[0]
            rawContent = choice?.message?.content || choice?.delta?.content || choice?.text || ''
          }
        }

        if (typeof rawContent === 'string' && rawContent.trim() && rawContent.trim() !== 'null') {
          const cleaned = sanitizeChatTitle(rawContent)
          if (cleaned && cleaned.toLowerCase() !== 'new chat') {
            return cleaned
          }
        }
      } catch {
        // Try next mode or endpoint
      }
    }
  }

  return null
}
