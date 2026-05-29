// Visual check of the redesigned Case Details (Overview) with mocked API,
// so no backend is needed. Run while `npm run dev` serves on :5173.
import { chromium } from '@playwright/test'

const BASE = process.env.PREVIEW_BASE_URL || 'http://localhost:5173'
const now = Date.now()
const iso = (offsetMs) => new Date(now - offsetMs).toISOString()

const caseItem = {
  id: 1, patientPseudoId: 'demo-patient-1', modality: 'CT', status: 'COMPLETED',
  inferenceStatus: 'COMPLETED', executionMode: 'real', origin: 'LIVE_PROCESSED',
  sourceDataset: 'MSD Task03 Liver', sourceAttribution: 'Medical Segmentation Decathlon',
  createdAt: iso(600000), updatedAt: iso(60000),
}
const status = {
  caseId: 1, status: 'COMPLETED', inferenceStatus: 'COMPLETED', executionMode: 'real',
  modelVersion: 'real-ts:on-lesion:nnU-Net v2 [Dataset501]@cuda',
  metrics: { mode: 'real', liverModel: true, lesionModel: true, lesionModelName: 'nnU-Net v2 [Dataset501_AtlasMRILesion]', device: 'cuda', medsamAvailable: false, supportsMri3dSuspiciousZone: true },
  failureDetails: null, resultReady: true, resultSource: 'ML_INFERENCE',
  stageAuditTrail: [
    { action: 'CASE_CREATED', at: iso(600000) },
    { action: 'CASE_UPLOADED', at: iso(540000) },
    { action: 'INFERENCE_ENQUEUED', at: iso(520000) },
    { action: 'INFERENCE_STARTED', at: iso(500000) },
    { action: 'INFERENCE_COMPLETED', at: iso(60000) },
  ],
}
const artifacts = [
  { id: 1, type: 'ORIGINAL_STUDY', mimeType: 'application/gzip', fileName: 'input.nii.gz', downloadUrl: '#' },
  { id: 2, type: 'ENHANCED_VOLUME', mimeType: 'application/gzip', fileName: 'enhanced.nii.gz', downloadUrl: '#' },
  { id: 3, type: 'LIVER_MASK', mimeType: 'application/gzip', fileName: 'liver_mask.nii.gz', downloadUrl: '#' },
  { id: 4, type: 'LESION_MASK', mimeType: 'application/gzip', fileName: 'lesion_mask.nii.gz', downloadUrl: '#' },
  { id: 5, type: 'LIVER_MESH', mimeType: 'model/gltf-binary', fileName: 'liver.glb', downloadUrl: '#' },
  { id: 6, type: 'LESION_MESH', mimeType: 'model/gltf-binary', fileName: 'lesion.glb', downloadUrl: '#' },
]
const viewer3d = { liverMeshArtifactId: 5, lesionMeshArtifactId: 6 }
const findings = [
  { id: 1, type: 'LESION', label: 'Lesion component #1', confidence: 0.82, sizeMm: 24.5, volumeMm3: 3120.4, location: { segment: 'VII', centroid: [60, 70, 40], bbox: { min: [50, 60, 35], max: [72, 82, 47] }, extent: [22, 22, 12], suspicion: null } },
  { id: 2, type: 'LESION', label: 'Lesion component #2', confidence: 0.61, sizeMm: 12.1, volumeMm3: 540.2, location: { segment: 'V', centroid: [40, 50, 30], bbox: { min: [36, 46, 27], max: [44, 54, 33] }, extent: [8, 8, 6], suspicion: null } },
]
const report = {
  reportText: 'Findings: 2 lesion component(s).\n\nImpression: 2 lesion component(s) require clinical correlation.',
  reportData: {
    modality: 'CT', executionMode: 'real', lesionCount: 2, evidenceBound: true,
    sections: { findings: '2 lesion components.', impression: 'Require clinical correlation.', limitations: 'Decision-support only.', recommendation: 'Correlate with source images.' },
    capabilities: { supports3dLiver: true, supports3dLesion: true },
  },
}

const routes = [
  [/\/cases\/1$/, caseItem],
  [/\/cases\/1\/status$/, status],
  [/\/cases\/1\/artifacts$/, artifacts],
  [/\/cases\/1\/viewer\/3d$/, viewer3d],
  [/\/cases\/1\/report$/, report],
  [/\/cases\/1\/findings$/, findings],
]

async function shoot(mode) {
  const browser = await chromium.launch()
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  page.on('console', (m) => { if (m.type() === 'error') console.log(`[console.error] ${m.text()}`) })
  page.on('pageerror', (e) => console.log(`[pageerror] ${e.message}`))
  await page.route('**/api/cases/**', (route) => {
    const url = route.request().url()
    const hit = routes.find(([re]) => re.test(url.split('?')[0]))
    if (hit) return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(hit[1]) })
    return route.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
  })
  await page.addInitScript((m) => {
    const payload = btoa(JSON.stringify({ role: 'DOCTOR' }))
    localStorage.setItem('mrt.auth.token', `x.${payload}.y`)
    localStorage.setItem('mrt.theme.mode', m)
  }, mode)
  await page.goto(`${BASE}/cases/1`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(800)
  const rootLen = await page.evaluate(() => document.getElementById('root')?.innerHTML.length ?? -1)
  console.log(`[${mode}] #root innerHTML length = ${rootLen}`)
  await page.screenshot({ path: `case-preview-overview-${mode}.png`, fullPage: true })
  await browser.close()
}

await shoot('dark')
await shoot('light')
console.log('Saved case-preview-overview-dark.png and case-preview-overview-light.png')
