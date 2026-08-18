import { readFileSync } from 'node:fs'
import { spawnSync } from 'node:child_process'

const configPath = new URL('../security/frontend-npm-audit-exceptions.json', import.meta.url)
const lockPath = new URL('../api-generator-front/package-lock.json', import.meta.url)
const config = JSON.parse(readFileSync(configPath, 'utf8'))
const lockfile = JSON.parse(readFileSync(lockPath, 'utf8'))

const today = new Date().toISOString().slice(0, 10)
if (today > config.expiresOn) {
  console.error(`The frontend npm audit exception expired on ${config.expiresOn}.`)
  process.exit(1)
}

const audit = spawnSync('npm', ['audit', '--json'], {
  cwd: new URL('../api-generator-front/', import.meta.url),
  encoding: 'utf8',
  shell: process.platform === 'win32',
})

let report
try {
  report = JSON.parse(audit.stdout)
} catch {
  process.stderr.write(audit.stderr)
  console.error('npm audit did not return a JSON report.')
  process.exit(1)
}

const activeExceptions = config.exceptions.filter((exception) => {
  return exception.packages.every((packageName) =>
    lockfile.packages?.[`node_modules/${packageName}`]?.version === exception.requiredVersion,
  )
})

const allowedPackages = new Map()
for (const exception of activeExceptions) {
  for (const packageName of exception.packages) {
    allowedPackages.set(packageName, exception)
  }
}

const unresolved = Object.values(report.vulnerabilities ?? {}).filter((vulnerability) => {
  const exception = allowedPackages.get(vulnerability.name)
  if (!exception) return true

  return !vulnerability.via.every((entry) => {
    return typeof entry === 'string' || entry.url?.endsWith(exception.advisory)
  })
})

if (unresolved.length > 0) {
  console.error('Unapproved frontend dependency vulnerabilities found:')
  for (const vulnerability of unresolved) {
    console.error(`- ${vulnerability.name} (${vulnerability.severity})`)
  }
  process.exit(1)
}

if (activeExceptions.length > 0) {
  for (const exception of activeExceptions) {
    console.warn(`Accepted temporary exception ${exception.advisory} until ${config.expiresOn}: ${exception.reason}`)
  }
}
