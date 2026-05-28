// Thin wrapper around the JVM-side contract generator.
// The JVM generator (com.diploma.mrt.codegen.FrontendContractGenerator) is the
// single source of truth: it reflects on backend enums and serializes each value
// through Jackson, so the emitted TS bundle matches the API wire form exactly.

import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const frontendRoot = path.resolve(__dirname, '..')
const projectRoot = path.resolve(frontendRoot, '..')
const backendRoot = path.join(projectRoot, 'backend')
const outputPath = path.join(frontendRoot, 'src/types/generated-backend-contract.ts')

const checkMode = process.argv.includes('--check')

const mvnArgs = [
    '-q',
    '-pl', '.',
    '-DskipTests',
    'compile',
    'exec:java',
    `-Dexec.args=${checkMode ? '--check ' : ''}--output ${outputPath}`,
]

const result = spawnSync('mvn', mvnArgs, {
    cwd: backendRoot,
    stdio: 'inherit',
})

if (result.error) {
    console.error('Failed to invoke mvn — is Maven on PATH?')
    console.error(result.error)
    process.exit(1)
}
process.exit(result.status ?? 1)
