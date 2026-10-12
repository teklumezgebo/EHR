import { useState } from 'react'
import { keycloak } from './auth.ts'
import AuditLog from './pages/AuditLog.tsx'
import Chart from './pages/Chart.tsx'
import Lookup from './pages/Lookup.tsx'

const ROLE_LABELS: Record<string, string> = {
  PHYSICIAN: 'Physician',
  MA_NURSE: 'MA / Nurse',
  AUDITOR: 'Auditor',
}

// Workspace id of the audit log tab; patient tabs use patient UUIDs.
const AUDIT_TAB = 'audit'

export default function App() {
  const token = keycloak.tokenParsed ?? {}
  const roles = (token.realm_access?.roles ?? []).filter((r) => r in ROLE_LABELS)
  const isClinician = roles.some((r) => r !== 'AUDITOR')
  const isAuditor = roles.includes('AUDITOR')

  const [tabs, setTabs] = useState<{ id: string; label: string }[]>([])
  const [active, setActive] = useState<string | null>(isClinician ? null : AUDIT_TAB)

  function open(id: string, label: string) {
    if (!tabs.some((t) => t.id === id)) setTabs([...tabs, { id, label }])
    setActive(id)
  }

  function close(id: string) {
    const rest = tabs.filter((t) => t.id !== id)
    setTabs(rest)
    if (active === id) setActive(rest.at(-1)?.id ?? null)
  }

  return (
    <div className="shell">
      <header className="topbar">
        <span className="brand">EHR</span>
        <span className="user">
          {token.name ?? token.preferred_username} · {roles.map((r) => ROLE_LABELS[r]).join(', ')}
        </span>
        <button onClick={() => keycloak.logout({ redirectUri: window.location.origin })}>
          Log out
        </button>
      </header>

      <nav className="tabs">
        {isClinician && (
          <button className={active === null ? 'tab active' : 'tab'} onClick={() => setActive(null)}>
            Patient Lookup
          </button>
        )}
        {isAuditor && (
          <button
            className={active === AUDIT_TAB ? 'tab active' : 'tab'}
            onClick={() => setActive(AUDIT_TAB)}
          >
            Audit Log
          </button>
        )}
        {tabs.map((t) => (
          <span key={t.id} className={active === t.id ? 'tab active' : 'tab'}>
            <button onClick={() => setActive(t.id)}>{t.label}</button>
            <button className="close" aria-label={`Close ${t.label}`} onClick={() => close(t.id)}>
              ×
            </button>
          </span>
        ))}
      </nav>

      <main>
        {active === null ? (
          <Lookup onOpen={open} />
        ) : active === AUDIT_TAB ? (
          <AuditLog />
        ) : (
          <Chart key={active} patientId={active} />
        )}
      </main>
    </div>
  )
}
