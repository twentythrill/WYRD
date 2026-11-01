export default async (request: Request) => {
  if (request.method === 'OPTIONS') {
    return new Response(null, {
      status: 204,
      headers: {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type, Authorization, api-key, Accept',
      },
    })
  }

  const url = new URL(request.url)
  const targetPath = url.pathname.replace(/^\/api\/openai/, '')
  const targetUrl = `https://api.openai.com${targetPath}${url.search}`

  // Clone headers and remove host/connection headers that fetch overrides
  const headers = new Headers(request.headers)
  headers.delete('host')
  headers.delete('connection')

  try {
    const body = request.method !== 'GET' && request.method !== 'HEAD'
      ? await request.arrayBuffer()
      : undefined

    const upstreamResponse = await fetch(targetUrl, {
      method: request.method,
      headers,
      body,
    })

    const responseHeaders = new Headers(upstreamResponse.headers)
    responseHeaders.set('Access-Control-Allow-Origin', '*')

    return new Response(upstreamResponse.body, {
      status: upstreamResponse.status,
      statusText: upstreamResponse.statusText,
      headers: responseHeaders,
    })
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err)
    return new Response(JSON.stringify({ error: { message: `OpenAI Proxy error: ${msg}` } }), {
      status: 502,
      headers: {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*',
      },
    })
  }
}
