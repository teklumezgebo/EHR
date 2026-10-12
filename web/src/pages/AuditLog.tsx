import { useEffect, useState } from 'react'
import { apiFetch } from '../auth.ts'

type AuditEvent = {
  id: number
  at: string
  userId: string
  userName: string | null
  action: string
  outcome: 'SUCCESS' | 'DENIED' | 'ERROR'
  resourceType: string
  resourceId: string | null
  patientId: string | null
  patientMrn: string | null
  ip: string | null
}

// Matches the API page size; a shorter page means the oldest event has been reached.
const AUDIT_PAGE_SIZE = 100

async function fetchAuditPage(before?: number): Promise<AuditEvent[]> {
  const res = await apiFetch(`/api/audit-events${before ? `?before=${before}` : ''}`)
  if (!res.ok) throw new Error(`Request failed (${res.status})`)
  return (await res.json()) as AuditEvent[]
}

export default function AuditLog() {
  const [events, setEvents] = useState<AuditEvent[]>([])
  const [hasMore, setHasMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  // Appends to the list when before is set, otherwise replaces it.
  function show(page: AuditEvent[], before?: number) {
    setEvents((prev) => (before ? [...prev, ...page] : page))
    setHasMore(page.length === AUDIT_PAGE_SIZE)
    setError(null)
  }

  function loadFrom(before?: number) {
    setLoading(true)
    fetchAuditPage(before)
      .then((page) => show(page, before))
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    let cancelled = false
    fetchAuditPage()
      .then((page) => !cancelled && show(page))
      .catch((e: Error) => !cancelled && setError(e.message))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <section className="audit">
      <div className="audit-head">
        <h2>Audit Log</h2>
        <button onClick={() => loadFrom()} disabled={loading}>
          Refresh
        </button>
      </div>
      {error && <p className="banner">{error}</p>}
      <div className="audit-scroll">
        <table>
          <thead>
            <tr>
              <th>Time</th>
              <th>User</th>
              <th>Action</th>
              <th>Resource</th>
              <th>Patient MRN</th>
              <th>Outcome</th>
              <th>IP</th>
            </tr>
          </thead>
          <tbody>
            {events.map((e) => (
              <tr key={e.id}>
                <td>{new Date(e.at).toLocaleString()}</td>
                <td title={e.userId}>{e.userName ?? e.userId}</td>
                <td>{e.action}</td>
                <td>{e.resourceType}</td>
                <td title={e.patientId ?? undefined}>{e.patientMrn ?? e.patientId ?? '—'}</td>
                <td className={e.outcome === 'DENIED' ? 'denied' : undefined}>{e.outcome}</td>
                <td>{e.ip ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {loading && <p className="muted">Loading…</p>}
      {!loading && hasMore && events.length > 0 && (
        <button onClick={() => loadFrom(events.at(-1)!.id)}>Load older</button>
      )}
    </section>
  )
}
