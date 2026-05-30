// Exploratory edge-case probe against the live backend (:8080). Read-mostly,
// creates a couple of throwaway cases. Prints PASS/FAIL with observed status.
const API = 'http://localhost:8080'
const out = []
const ok = (name, cond, note = '') => out.push({ name, pass: !!cond, note })

async function login(email) {
  const r = await fetch(`${API}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: 'Admin123!' }),
  })
  return (await r.json()).token
}
const fs = await import('node:fs')
const admin = await login('admin@demo.local')
const doctor = await login('doctor@demo.local')
const H = (t, extra = {}) => ({ Authorization: `Bearer ${t}`, ...extra })

// E1: seeded import is idempotent by slug (no duplicate case on re-import)
{
  const manifest = JSON.parse(fs.readFileSync('../../demo-data/manifests/ct-multifocal-001.json', 'utf-8'))
  const post = () => fetch(`${API}/api/admin/demo-cases/import`, { method: 'POST', headers: H(admin, { 'Content-Type': 'application/json' }), body: JSON.stringify(manifest) })
  const a = await (await post()).json()
  const b = await (await post()).json()
  ok('Seeded import is idempotent (same caseId, UPDATED on 2nd)', a.caseId === b.caseId, `ids ${a.caseId}/${b.caseId}, 2nd action=${b.action}`)
}

// E2: delete removes a case (404 afterwards)
{
  const created = await (await fetch(`${API}/api/cases`, { method: 'POST', headers: H(doctor, { 'Content-Type': 'application/json' }), body: JSON.stringify({ patientPseudoId: `DEL-${Date.now()}`, modality: 'CT' }) })).json()
  const del = await fetch(`${API}/api/cases/${created.id}`, { method: 'DELETE', headers: H(doctor) })
  const after = await fetch(`${API}/api/cases/${created.id}`, { headers: H(doctor) })
  ok('Delete case then GET -> 404', del.status === 204 && after.status === 404, `delete ${del.status}, get ${after.status}`)
}

// E3: script-like pseudo id is stored/echoed literally (React escapes on render)
{
  const payload = '<img src=x onerror=alert(1)>'
  const created = await fetch(`${API}/api/cases`, { method: 'POST', headers: H(doctor, { 'Content-Type': 'application/json' }), body: JSON.stringify({ patientPseudoId: payload, modality: 'CT' }) })
  const body = await created.json()
  ok('Injection-like pseudoId accepted and echoed literally', created.status === 201 && body.patientPseudoId === payload, `status ${created.status}`)
}

// E4: a non-admin cannot mutate (process) a seeded demo case
{
  const list = await (await fetch(`${API}/api/cases`, { headers: H(admin) })).json()
  const seeded = list.find((c) => c.origin === 'SEEDED_DEMO')
  if (seeded) {
    const res = await fetch(`${API}/api/cases/${seeded.id}/process`, { method: 'POST', headers: H(doctor) })
    ok('Doctor cannot process a seeded demo case', res.status >= 400, `status ${res.status}`)
  } else ok('Doctor cannot process a seeded demo case', false, 'no seeded case found')
}

// E5: double process does not 500 (graceful conflict or accept)
{
  const c = await (await fetch(`${API}/api/cases`, { method: 'POST', headers: H(doctor, { 'Content-Type': 'application/json' }), body: JSON.stringify({ patientPseudoId: `DP-${Date.now()}`, modality: 'CT' }) })).json()
  // upload fixture so process is allowed
  const buf = fs.readFileSync('../../example4d.nii.gz')
  const fd = new FormData()
  fd.append('file', new Blob([buf]), 'example4d.nii.gz')
  await fetch(`${API}/api/cases/${c.id}/upload`, { method: 'POST', headers: H(doctor), body: fd })
  const r1 = await fetch(`${API}/api/cases/${c.id}/process`, { method: 'POST', headers: H(doctor) })
  const r2 = await fetch(`${API}/api/cases/${c.id}/process`, { method: 'POST', headers: H(doctor) })
  ok('Double process never returns 5xx', r1.status < 500 && r2.status < 500, `statuses ${r1.status}/${r2.status}`)
}

console.log('\n===== EXPLORATORY PROBE =====')
for (const r of out) console.log(`${r.pass ? 'PASS' : 'FAIL'}  ${r.name}  [${r.note}]`)
console.log(`\n${out.filter((r) => r.pass).length}/${out.length} passed`)
