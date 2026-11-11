import Keycloak from 'keycloak-js'

export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8081',
  realm: 'ehr',
  clientId: 'ehr-web',
})

/** Calls the API with a fresh access token; refreshes when under 30 seconds remain. */
export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  await keycloak.updateToken(30)
  const headers = new Headers(init.headers)
  headers.set('Authorization', `Bearer ${keycloak.token}`)
  return fetch(path, { ...init, headers })
}

/**
 * Sends a JSON request and returns the parsed body. Throws with the API's problem-detail reason,
 * so a 400 or 409 shows why it was rejected.
 */
export async function apiJson<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const res = await apiFetch(path, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!res.ok) {
    if (res.status === 403) {
      throw new Error('Access denied. You are not on this patient’s care team. This attempt was audited.')
    }
    const problem = (await res.json().catch(() => null)) as { detail?: string } | null
    throw new Error(problem?.detail ?? `Request failed (${res.status})`)
  }
  return (await res.json()) as T
}
