import { useState } from 'react'

// Seeded dev patients, listed until the API exposes patient search. Helen Park is outside the
// demo users' care teams, so opening her chart shows a denied (403) access.
const SEED_PATIENTS = [
  { id: 'c0ffee00-0001-4a7b-8000-000000000001', mrn: 'E1000001', name: 'Rivera, Sam' },
  { id: 'c0ffee00-0002-4a7b-8000-000000000002', mrn: 'E1000002', name: 'Delgado, Rosa' },
  { id: 'c0ffee00-0003-4a7b-8000-000000000003', mrn: 'E1000003', name: 'Asante, Kwame' },
  { id: 'c0ffee00-0004-4a7b-8000-000000000004', mrn: 'E1000004', name: 'Park, Helen' },
]

export default function Lookup({ onOpen }: { onOpen: (id: string, label: string) => void }) {
  const [rawId, setRawId] = useState('')

  return (
    <section className="lookup">
      <h2>Patient Lookup</h2>
      <table>
        <thead>
          <tr>
            <th>MRN</th>
            <th>Name</th>
          </tr>
        </thead>
        <tbody>
          {SEED_PATIENTS.map((p) => (
            <tr key={p.id} onClick={() => onOpen(p.id, p.name)}>
              <td>{p.mrn}</td>
              <td>{p.name}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (rawId.trim()) onOpen(rawId.trim(), rawId.trim().slice(0, 8))
        }}
      >
        <input
          value={rawId}
          onChange={(e) => setRawId(e.target.value)}
          placeholder="Open by patient ID"
          aria-label="Patient ID"
        />
        <button type="submit">Open</button>
      </form>
    </section>
  )
}
