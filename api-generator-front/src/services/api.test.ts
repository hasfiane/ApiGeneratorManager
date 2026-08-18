import { beforeEach, describe, expect, it, vi } from 'vitest'

function jsonResponse(body: unknown, init: ResponseInit = {}) {
  return new Response(JSON.stringify(body), {
    ...init,
    headers: {
      'content-type': 'application/json',
      ...(init.headers ?? {}),
    },
  })
}

async function loadApiWithFetch(fetchMock: ReturnType<typeof vi.fn>) {
  vi.resetModules()
  vi.stubGlobal('fetch', fetchMock)
  return import('./api')
}

describe('api service', () => {
  beforeEach(() => {
    vi.unstubAllGlobals()
  })

  it('encodes dynamic path segments before sending requests', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 'api/with spaces' }))
    const { api } = await loadApiWithFetch(fetchMock)

    await api.getGeneratedApi('api/with spaces')

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/account/apis/api%2Fwith%20spaces',
      expect.objectContaining({ credentials: 'include' })
    )
  })

  it('adds CSRF headers to state-changing calls and retries once with a fresh token after a 403', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'stale-token' }))
      .mockResolvedValueOnce(jsonResponse({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' }))
      .mockResolvedValueOnce(jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'fresh-token' }))
      .mockResolvedValueOnce(jsonResponse({ status: 'RUNNING' }))
    const { api } = await loadApiWithFetch(fetchMock)

    await expect(api.startGeneratedApiPreview('preview id')).resolves.toEqual({ status: 'RUNNING' })

    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      'http://localhost:8080/api/account/apis/preview%20id/preview/start',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        headers: expect.any(Headers),
      })
    )
    expect(fetchMock.mock.calls[1][1].headers.get('X-CSRF-TOKEN')).toBe('stale-token')
    expect(fetchMock.mock.calls[3][1].headers.get('X-CSRF-TOKEN')).toBe('fresh-token')
    expect(fetchMock).toHaveBeenCalledTimes(4)
  })

  it('does not expose HTML error bodies to callers', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response('<html><body>proxy error with internals</body></html>', {
        status: 502,
        statusText: 'Bad Gateway',
        headers: { 'content-type': 'text/html' },
      })
    )
    const { api } = await loadApiWithFetch(fetchMock)

    await expect(api.getMe()).rejects.toMatchObject({
      status: 502,
      message: 'The service is temporarily unavailable. Retry in a moment.',
    })
  })
})
