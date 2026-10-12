import Keycloak from 'keycloak-js'

export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8081',
  realm: 'ehr',
  clientId: 'ehr-web',
})

/** Calls the API with a fresh access token; refreshes when under 30 seconds remain. */
export async function apiFetch(path: string): Promise<Response> {
  await keycloak.updateToken(30)
  return fetch(path, { headers: { Authorization: `Bearer ${keycloak.token}` } })
}
