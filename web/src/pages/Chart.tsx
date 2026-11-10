import { useEffect, useState } from 'react'
import { apiFetch } from '../auth.ts'

type PatientSummary = {
  id: string
  mrn: string
  givenName: string
  familyName: string
  birthDate: string
  sex: string
  language: string
  codeStatus: string
  photoUrl: string | null
  pcpName: string | null
  coverage: string | null
}

type Problem = {
  id: string
  icd10: string
  description: string
  onset: string | null
  status: string
}

type Allergy = {
  id: string
  substance: string
  reaction: string | null
  severity: 'mild' | 'moderate' | 'severe' | null
}

type LabResult = {
  id: string
  loinc: string
  display: string
  panel: string | null
  value: number
  unit: string
  refLow: number | null
  refHigh: number | null
  takenAt: string
}

type ChartData = {
  patient: PatientSummary
  problems: Problem[]
  allergies: Allergy[]
  labs: LabResult[]
}

// Reads the YYYY-MM-DD parts directly; new Date() parses the string as UTC midnight, which
// shifts the day back in time zones behind UTC.
function age(birthDate: string): number {
  const [year, month, day] = birthDate.split('-').map(Number)
  const now = new Date()
  const nowMonth = now.getMonth() + 1
  const hadBirthday = nowMonth > month || (nowMonth === month && now.getDate() >= day)
  return now.getFullYear() - year - (hadBirthday ? 0 : 1)
}

async function fetchJson<T>(path: string): Promise<T> {
  const res = await apiFetch(path)
  if (!res.ok) {
    throw new Error(
      res.status === 403
        ? 'Access denied. You are not on this patient’s care team. This attempt was audited.'
        : `Request failed (${res.status})`,
    )
  }
  return (await res.json()) as T
}

export default function Chart({ patientId }: { patientId: string }) {
  const [data, setData] = useState<ChartData | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    fetchJson<ChartData>(`/api/patients/${encodeURIComponent(patientId)}`)
      .then((chart) => !cancelled && setData(chart))
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [patientId])

  if (error) return <p className="banner">{error}</p>
  if (!data) return <p className="muted">Loading…</p>

  const { patient, problems, allergies, labs } = data
    return (
      <div className="chart">
        <aside className="storyboard">
          <div className="photo" aria-hidden>
            {patient.givenName[0]}
            {patient.familyName[0]}
          </div>
          <h2>
            {patient.familyName}, {patient.givenName}
          </h2>
          <p>
            {age(patient.birthDate)} y.o. {patient.sex}
          </p>
          <p className={allergies.length ? 'allergy-flag' : 'allergy-flag none'}>
            {allergies.length ? `Allergies (${allergies.length})` : 'No Allergies on File'}
          </p>
          <dl>
            <dt>MRN</dt>
            <dd>{patient.mrn}</dd>
            <dt>DOB</dt>
            <dd>{patient.birthDate}</dd>
            <dt>Language</dt>
            <dd>{patient.language}</dd>
            <dt>PCP</dt>
            <dd>{patient.pcpName ?? 'None'}</dd>
            <dt>Coverage</dt>
            <dd>{patient.coverage ?? 'None'}</dd>
          </dl>
          <p className={patient.codeStatus === 'Full Code' ? 'code' : 'code alert'}>
            {patient.codeStatus}
          </p>
        </aside>
        <section className="activity">
          <nav className="activity-tabs">
            <span className="tab active">SnapShot</span>
          </nav>
          <Snapshot problems={problems} allergies={allergies} labs={labs} />
        </section>
      </div>
    )
}

function refRange({ refLow, refHigh }: LabResult): string {
  if (refLow !== null && refHigh !== null) return `${refLow}–${refHigh}`
  if (refLow !== null) return `≥ ${refLow}`
  if (refHigh !== null) return `≤ ${refHigh}`
  return '—'
}

function flag({ value, refLow, refHigh }: LabResult): 'H' | 'L' | null {
  if (refHigh !== null && value > refHigh) return 'H'
  if (refLow !== null && value < refLow) return 'L'
  return null
}

function Snapshot({
  problems,
  allergies,
  labs,
}: {
  problems: Problem[]
  allergies: Allergy[]
  labs: LabResult[]
}) {
  return (
    <div className="snapshot">
      <section className="card">
        <h3>Problem List</h3>
        {problems.length === 0 ? (
          <p className="empty">No problems on file.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Problem</th>
                <th>ICD-10</th>
                <th>Onset</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {problems.map((p) => (
                <tr key={p.id} className={p.status === 'active' ? undefined : 'inactive'}>
                  <td>{p.description}</td>
                  <td>{p.icd10}</td>
                  <td>{p.onset ?? '—'}</td>
                  <td>{p.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
      <section className="card">
        <h3>Allergies</h3>
        {allergies.length === 0 ? (
          // An empty list means nothing recorded, which is not the same as "no known allergies".
          <p className="empty">No allergies on file. Allergies not yet reviewed.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Allergen</th>
                <th>Reaction</th>
                <th>Severity</th>
              </tr>
            </thead>
            <tbody>
              {allergies.map((a) => (
                <tr key={a.id}>
                  <td>{a.substance}</td>
                  <td>{a.reaction ?? '—'}</td>
                  <td className={a.severity === 'severe' ? 'severe' : undefined}>
                    {a.severity ?? '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
      <section className="card labs">
        <h3>Recent Labs</h3>
        {labs.length === 0 ? (
          <p className="empty">No lab results on file.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Component</th>
                <th>Value</th>
                <th>Ref Range</th>
                <th>Date</th>
              </tr>
            </thead>
            <tbody>
              {labs.flatMap((lab, i) => {
                const f = flag(lab)
                const row = (
                  <tr key={lab.id}>
                    <td>{lab.display}</td>
                    <td className={f ? 'abnormal' : undefined}>
                      {lab.value} {lab.unit}
                      {f && ` (${f})`}
                    </td>
                    <td>{refRange(lab)}</td>
                    <td>{new Date(lab.takenAt).toLocaleDateString('en-CA')}</td>
                  </tr>
                )
                // Rows arrive sorted by panel, so a header row starts each new panel.
                return lab.panel && lab.panel !== labs[i - 1]?.panel
                  ? [
                      <tr key={lab.panel} className="panel">
                        <td colSpan={4}>{lab.panel}</td>
                      </tr>,
                      row,
                    ]
                  : [row]
              })}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}
