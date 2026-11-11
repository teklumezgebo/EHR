import { useEffect, useState, type FormEvent } from 'react'
import { apiJson, keycloak } from '../auth.ts'

export type CareTeamMember = { id: string; name: string; department: string | null }

type EncounterHeader = {
  id: string
  startAt: string
  providerName: string
  department: string
  type: string
  reason: string | null
  status: 'OPEN' | 'SIGNED'
  signedAt: string | null
  signedByName: string | null
}

type Vital = {
  id: string
  loinc: string
  display: string
  panel: string | null
  value: number
  unit: string
  takenAt: string
}

type Note = {
  id: string
  authorId: string
  authorName: string
  type: 'PROGRESS' | 'ROOMING'
  subjective: string
  objective: string
  assessment: string
  plan: string
  version: number
  updatedAt: string
  signedAt: string | null
}

type Snapshot = { encounter: EncounterHeader; vitals: Vital[]; notes: Note[] }

const SOAP = [
  ['subjective', 'Subjective'],
  ['objective', 'Objective'],
  ['assessment', 'Assessment'],
  ['plan', 'Plan'],
] as const

type SoapField = (typeof SOAP)[number][0]

const NOTE_LABELS: Record<Note['type'], string> = { PROGRESS: 'Progress Note', ROOMING: 'Rooming Note' }

// Flowsheet columns. BP shows systolic/diastolic together; each other column is one LOINC code.
const VITAL_COLUMNS = [
  { label: 'BP', unit: 'mmHg', loinc: ['8480-6', '8462-4'] },
  { label: 'Pulse', unit: '/min', loinc: ['8867-4'] },
  { label: 'Resp', unit: '/min', loinc: ['9279-1'] },
  { label: 'Temp', unit: '°F', loinc: ['8310-5'] },
  { label: 'SpO2', unit: '%', loinc: ['59408-5'] },
  { label: 'Wt', unit: 'lb', loinc: ['29463-7'] },
  { label: 'Ht', unit: 'in', loinc: ['8302-2'] },
  { label: 'BMI', unit: 'kg/m²', loinc: ['39156-5'] },
]

// Field names match the API's vitals request.
const VITAL_FIELDS = [
  { name: 'systolic', label: 'BP systolic', step: '1' },
  { name: 'diastolic', label: 'BP diastolic', step: '1' },
  { name: 'heartRate', label: 'Pulse', step: '1' },
  { name: 'respRate', label: 'Resp', step: '1' },
  { name: 'tempF', label: 'Temp (°F)', step: '0.1' },
  { name: 'spo2', label: 'SpO2 (%)', step: '1' },
  { name: 'weightLb', label: 'Weight (lb)', step: '0.1' },
  { name: 'heightIn', label: 'Height (in)', step: '0.1' },
]

const ENCOUNTER_TYPES = ['Office Visit', 'Nurse Visit', 'Telephone', 'Telehealth']

function visitDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-US')
}

function dateTime(iso: string): string {
  return new Date(iso).toLocaleString('en-US', {
    month: 'numeric',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}

/** Encounter list for one patient, with the selected encounter opened beside it. */
export default function Encounters({
  patientId,
  careTeam,
  pcpName,
}: {
  patientId: string
  careTeam: CareTeamMember[]
  pcpName: string | null
}) {
  const [encounters, setEncounters] = useState<EncounterHeader[] | null>(null)
  const [selected, setSelected] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // Bumped after a change so the list reloads with the new status.
  const [version, setVersion] = useState(0)

  const base = `/api/patients/${encodeURIComponent(patientId)}/encounters`

  useEffect(() => {
    let cancelled = false
    apiJson<EncounterHeader[]>(base)
      .then((list) => !cancelled && setEncounters(list))
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [base, version])

  if (error) return <p className="banner">{error}</p>
  if (!encounters) return <p className="muted">Loading…</p>

  return (
    <div className="encounters">
      <section className="card encounter-list">
        <h3>
          Encounters
          {!creating && (
            <button className="link" onClick={() => setCreating(true)}>
              + New
            </button>
          )}
        </h3>
        {creating && (
          <NewEncounterForm
            base={base}
            careTeam={careTeam}
            pcpName={pcpName}
            onCancel={() => setCreating(false)}
            onCreated={(id) => {
              setCreating(false)
              setSelected(id)
              setVersion((v) => v + 1)
            }}
          />
        )}
        {encounters.length === 0 ? (
          <p className="empty">No encounters on file.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Type</th>
                <th>Provider</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {encounters.map((e) => (
                <tr
                  key={e.id}
                  className={e.id === selected ? 'selectable selected' : 'selectable'}
                  onClick={() => setSelected(e.id)}
                >
                  <td>{visitDate(e.startAt)}</td>
                  <td>
                    {e.type}
                    {e.reason && <div className="sub">{e.reason}</div>}
                  </td>
                  <td>{e.providerName}</td>
                  <td>
                    <span className={e.status === 'OPEN' ? 'status open' : 'status'}>{e.status}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
      {selected ? (
        <EncounterView
          key={selected}
          url={`${base}/${encodeURIComponent(selected)}`}
          onChanged={() => setVersion((v) => v + 1)}
        />
      ) : (
        <p className="muted">Select an encounter to open it.</p>
      )}
    </div>
  )
}

function NewEncounterForm({
  base,
  careTeam,
  pcpName,
  onCancel,
  onCreated,
}: {
  base: string
  careTeam: CareTeamMember[]
  pcpName: string | null
  onCancel: () => void
  onCreated: (id: string) => void
}) {
  const me = keycloak.tokenParsed?.sub
  // Defaults to the PCP, then the current user, then the first care-team member.
  const initial =
    careTeam.find((m) => m.name === pcpName) ?? careTeam.find((m) => m.id === me) ?? careTeam[0]
  const [providerId, setProviderId] = useState(initial?.id ?? '')
  const [type, setType] = useState('Office Visit')
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    apiJson<EncounterHeader>(base, 'POST', { providerId, type, reason: reason.trim() || null })
      .then((created) => onCreated(created.id))
      .catch((err: Error) => setError(err.message))
      .finally(() => setBusy(false))
  }

  return (
    <form className="form" onSubmit={submit}>
      <label>
        Provider
        <select value={providerId} onChange={(e) => setProviderId(e.target.value)} required>
          {careTeam.map((m) => (
            <option key={m.id} value={m.id}>
              {m.name}
              {m.department ? ` (${m.department})` : ''}
            </option>
          ))}
        </select>
      </label>
      <label>
        Type
        <input
          list="encounter-types"
          value={type}
          onChange={(e) => setType(e.target.value)}
          maxLength={100}
          required
        />
        <datalist id="encounter-types">
          {ENCOUNTER_TYPES.map((t) => (
            <option key={t} value={t} />
          ))}
        </datalist>
      </label>
      <label>
        Reason for visit
        <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={200} />
      </label>
      {error && <p className="error">{error}</p>}
      <div className="actions">
        <button type="submit" className="primary" disabled={busy || !providerId}>
          Create
        </button>
        <button type="button" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  )
}

/** One encounter: header, vitals flowsheet, notes, and Sign Visit. */
function EncounterView({ url, onChanged }: { url: string; onChanged: () => void }) {
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let cancelled = false
    apiJson<Snapshot>(url)
      .then((s) => {
        if (cancelled) return
        setSnapshot(s)
        setError(null)
      })
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [url, reload])

  if (!snapshot) return error ? <p className="banner">{error}</p> : <p className="muted">Loading…</p>

  const { encounter, vitals, notes } = snapshot
  const open = encounter.status === 'OPEN'
  const me = keycloak.tokenParsed?.sub
  const myNote = notes.find((n) => n.authorId === me)
  const unsigned = notes.filter((n) => !n.signedAt)

  // Refetches the snapshot and the list after a write.
  function refresh() {
    setReload((r) => r + 1)
    onChanged()
  }

  function signVisit() {
    if (!window.confirm('Sign this visit? The encounter, its notes, and its vitals will be locked.')) return
    apiJson(`${url}/sign`, 'POST')
      .then(refresh)
      .catch((e: Error) => setError(e.message))
  }

  return (
    <section className="encounter">
      <header className="encounter-header">
        <h3>
          {visitDate(encounter.startAt)} visit with {encounter.providerName} for {encounter.type}
          {encounter.reason && ` - ${encounter.reason}`}
        </h3>
        <p className="sub">
          {encounter.department} ·{' '}
          {open
            ? 'Open'
            : `Signed ${encounter.signedAt ? dateTime(encounter.signedAt) : ''} by ${encounter.signedByName}`}
        </p>
      </header>

      {error && <p className="banner">{error}</p>}

      <section className="card">
        <h3>Vitals</h3>
        <VitalsFlowsheet vitals={vitals} />
        {open && <VitalsForm url={`${url}/vitals`} onSaved={refresh} />}
      </section>

      {notes
        .filter((n) => n.authorId !== me || !open || n.signedAt)
        .map((n) => (
          <NoteCard key={n.id} note={n} />
        ))}
      {open && !myNote?.signedAt && (
        // Keyed by version so the draft resets to the saved text after each save.
        <NoteEditor key={`${myNote?.id}-${myNote?.version}`} url={`${url}/note`} note={myNote} onSaved={refresh} />
      )}

      {open && (
        <footer className="encounter-footer">
          {unsigned.length > 0 && (
            <span className="sub">
              Waiting on {unsigned.map((n) => `${n.authorName}'s ${NOTE_LABELS[n.type].toLowerCase()}`).join(', ')}
            </span>
          )}
          <button className="primary" onClick={signVisit} disabled={unsigned.length > 0}>
            Sign Visit
          </button>
        </footer>
      )}
    </section>
  )
}

function VitalsFlowsheet({ vitals }: { vitals: Vital[] }) {
  if (vitals.length === 0) return <p className="empty">No vitals recorded.</p>

  // One row per reading set; vitals arrive newest first.
  const sets = new Map<string, Map<string, Vital>>()
  for (const v of vitals) {
    if (!sets.has(v.takenAt)) sets.set(v.takenAt, new Map())
    sets.get(v.takenAt)!.set(v.loinc, v)
  }

  return (
    <table>
      <thead>
        <tr>
          <th>Taken</th>
          {VITAL_COLUMNS.map((c) => (
            <th key={c.label}>
              {c.label} <span className="unit">{c.unit}</span>
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {[...sets].map(([takenAt, readings]) => (
          <tr key={takenAt}>
            <td>{dateTime(takenAt)}</td>
            {VITAL_COLUMNS.map((c) => {
              const values = c.loinc.map((code) => readings.get(code)?.value)
              return <td key={c.label}>{values.every((v) => v !== undefined) ? values.join('/') : '—'}</td>
            })}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function VitalsForm({ url, onSaved }: { url: string; onSaved: () => void }) {
  const [values, setValues] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function submit(e: FormEvent) {
    e.preventDefault()
    const body = Object.fromEntries(
      Object.entries(values)
        .filter(([, v]) => v.trim() !== '')
        .map(([k, v]) => [k, Number(v)]),
    )
    setBusy(true)
    apiJson(url, 'POST', body)
      .then(() => {
        setValues({})
        setError(null)
        onSaved()
      })
      .catch((err: Error) => setError(err.message))
      .finally(() => setBusy(false))
  }

  return (
    <form className="form vitals-form" onSubmit={submit}>
      <div className="vitals-grid">
        {VITAL_FIELDS.map((f) => (
          <label key={f.name}>
            {f.label}
            <input
              type="number"
              step={f.step}
              value={values[f.name] ?? ''}
              onChange={(e) => setValues({ ...values, [f.name]: e.target.value })}
            />
          </label>
        ))}
      </div>
      {error && <p className="error">{error}</p>}
      <div className="actions">
        <button type="submit" className="primary" disabled={busy}>
          Record Vitals
        </button>
        <span className="sub">BMI is calculated from weight and height.</span>
      </div>
    </form>
  )
}

function NoteCard({ note }: { note: Note }) {
  return (
    <section className="card note">
      <h3>
        {NOTE_LABELS[note.type]} · {note.authorName}
        <span className="sub">{note.signedAt ? `Signed ${dateTime(note.signedAt)}` : 'Unsigned draft'}</span>
      </h3>
      <dl>
        {SOAP.filter(([field]) => note[field]).map(([field, label]) => (
          <div key={field}>
            <dt>{label}</dt>
            <dd>{note[field]}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

/** The current user's own note, which only they can edit and sign. */
function NoteEditor({ url, note, onSaved }: { url: string; note: Note | undefined; onSaved: () => void }) {
  const saved: Record<SoapField, string> = {
    subjective: note?.subjective ?? '',
    objective: note?.objective ?? '',
    assessment: note?.assessment ?? '',
    plan: note?.plan ?? '',
  }
  const [draft, setDraft] = useState(saved)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const dirty = SOAP.some(([field]) => draft[field] !== saved[field])

  function run(request: Promise<unknown>) {
    setBusy(true)
    request
      .then(onSaved)
      .catch((e: Error) => setError(e.message))
      .finally(() => setBusy(false))
  }

  function save(e: FormEvent) {
    e.preventDefault()
    // Omitting the version creates the note; sending it updates only if nobody saved since.
    run(apiJson(url, 'PUT', { ...draft, version: note?.version }))
  }

  function sign() {
    if (!window.confirm('Sign your note? It cannot be edited after signing.')) return
    run(apiJson(`${url}/sign`, 'POST'))
  }

  return (
    <form className="card note form" onSubmit={save}>
      <h3>
        My Note
        <span className="sub">{note ? `Draft, version ${note.version}` : 'Not started'}</span>
      </h3>
      {SOAP.map(([field, label]) => (
        <label key={field}>
          {label}
          <textarea
            rows={3}
            maxLength={20000}
            value={draft[field]}
            onChange={(e) => setDraft({ ...draft, [field]: e.target.value })}
          />
        </label>
      ))}
      {error && <p className="error">{error}</p>}
      <div className="actions">
        <button type="submit" className="primary" disabled={busy || (!dirty && note !== undefined)}>
          Save
        </button>
        {note && (
          <button type="button" onClick={sign} disabled={busy || dirty} title={dirty ? 'Save before signing' : ''}>
            Sign Note
          </button>
        )}
        {dirty && <span className="sub">Unsaved changes</span>}
      </div>
    </form>
  )
}
