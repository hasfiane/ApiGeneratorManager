import { type ComponentProps, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Shell } from '../components/Shell'
import { Icon } from '../components/Icon'
import { VisualIcon } from '../components/VisualIcon'
import { useLanguage } from '../i18n/LanguageProvider'
import { api, type ApiPreview, type ApiProject, type GeneratedApiDetail, type PreviewDiagnostics } from '../services/api'
import { env } from '../services/env'
import { useAuth } from '../state/auth'

type Status = {
  jobId: string
  status: string
  createdAt: string
  error?: string
  hostPort?: number
  apiBaseUrl?: string
  proxyUrl?: string
  containerId?: string
}

type ZipJob = {
  jobId: string
  createdAt?: string | null
  zipDownloadedAt?: string | null
}

type GenerationPreset = {
  key: 'postgres' | 'mysql' | 'h2'
  appName: string
  basePackage: string
  databaseType: 'postgres' | 'mysql' | 'h2'
  jdbcUrl: string
  jdbcUsername: string
  jdbcPassword: string
  schema: string
  build: boolean
  deployDocker: boolean
  hostPort: number
}

type GenerationMode = 'jdbc' | 'yaml'

const ZIP_READY_STATUSES = new Set(['SUCCEEDED', 'DEPLOYED', 'STOPPED'])
const JOB_TERMINAL_STATUSES = new Set(['FAILED', 'SUCCEEDED', 'DEPLOYED', 'STOPPED'])
const TERMINAL_GENERATION_STATUSES = new Set(['DONE', 'FAILED'])


function normalizeAppName(value: string) {
  return value
    .trim()
    .replace(/\s+/g, '_')
    .replace(/[^a-zA-Z0-9_-]/g, '_')
    .replace(/_+/g, '_')
}

function presetIconName(databaseType: GenerationPreset['databaseType']): ComponentProps<typeof Icon>['name'] {
  switch (databaseType) {
    case 'postgres':
      return 'postgres'
    case 'mysql':
      return 'mysql'
    case 'h2':
      return 'h2'
  }
}

function useGeneration(id: string | null, refreshKey: number) {
  const [data, setData] = useState<GeneratedApiDetail | null>(null)

  useEffect(() => {
    if (!id) {
      setData(null)
      return
    }

    let active = true
    let intervalId: number | null = null

    const load = async () => {
      try {
        const next = await api.getGeneratedApi(id)
        if (!active) return
        setData(next)
        if (TERMINAL_GENERATION_STATUSES.has(next.status ?? '') && intervalId !== null) {
          window.clearInterval(intervalId)
          intervalId = null
        }
      } catch {
        if (active) {
          setData(null)
        }
      }
    }

    void load()
    intervalId = window.setInterval(() => {
      void load()
    }, 1500)

    return () => {
      active = false
      if (intervalId !== null) {
        window.clearInterval(intervalId)
      }
    }
  }, [id, refreshKey])

  return data
}

function usePreview(id: string | null, refreshKey: number) {
  const [data, setData] = useState<ApiPreview | null>(null)

  useEffect(() => {
    if (!id) {
      setData(null)
      return
    }

    let active = true
    const load = async () => {
      try {
        const next = await api.getGeneratedApiPreview(id)
        if (active) {
          setData(next)
        }
      } catch {
        if (active) {
          setData(null)
        }
      }
    }

    void load()
    const intervalId = window.setInterval(() => {
      void load()
    }, 1500)

    return () => {
      active = false
      window.clearInterval(intervalId)
    }
  }, [id, refreshKey])

  return data
}

function usePreviewLogs(id: string | null, refreshKey: number) {
  const [data, setData] = useState<string[]>([])

  useEffect(() => {
    if (!id) {
      setData([])
      return
    }

    let active = true
    const load = async () => {
      try {
        const next = await api.getGeneratedApiPreviewLogs(id, 200)
        if (active) {
          setData(next)
        }
      } catch {
        if (active) {
          setData([])
        }
      }
    }

    void load()
    const intervalId = window.setInterval(() => {
      void load()
    }, 1500)

    return () => {
      active = false
      window.clearInterval(intervalId)
    }
  }, [id, refreshKey])

  return data
}

function usePreviewDiagnostics(id: string | null, refreshKey: number) {
  const [data, setData] = useState<PreviewDiagnostics | null>(null)

  useEffect(() => {
    if (!id) {
      setData(null)
      return
    }

    let active = true
    const load = async () => {
      try {
        const next = await api.getGeneratedApiPreviewDiagnostics(id)
        if (active) {
          setData(next)
        }
      } catch {
        if (active) {
          setData(null)
        }
      }
    }

    void load()
    const intervalId = window.setInterval(() => {
      void load()
    }, 5000)

    return () => {
      active = false
      window.clearInterval(intervalId)
    }
  }, [id, refreshKey])

  return data
}

export default function Generators() {
  const { text } = useLanguage()
  const { quotas } = useAuth()
  const t = text.generators
  const [appName, setAppName] = useState(env.demoAppName)
  const [basePackage, setBasePackage] = useState(env.demoBasePackage)
  const [databaseType, setDatabaseType] = useState<'postgres' | 'mysql' | 'h2'>(env.demoDatabaseType)
  const [jdbcUrl, setJdbcUrl] = useState(env.demoJdbcUrl)
  const [jdbcUsername, setJdbcUsername] = useState(env.demoJdbcUsername)
  const [jdbcPassword, setJdbcPassword] = useState(env.demoJdbcPassword)
  const [schema, setSchema] = useState(env.demoSchema)
  const [build, setBuild] = useState(true)
  const [deployDocker, setDeployDocker] = useState(env.demoDeployDocker)
  const [hostPort, setHostPort] = useState<number>(env.demoHostPort)
  const [generationMode, setGenerationMode] = useState<GenerationMode>('jdbc')
  const [yamlFile, setYamlFile] = useState<File | null>(null)
  const [showAdvanced, setShowAdvanced] = useState(false)
  const [loading, setLoading] = useState(false)
  const [generationInProgress, setGenerationInProgress] = useState(false)
  const [jobId, setJobId] = useState<string | null>(null)
  const [selectedApiId, setSelectedApiId] = useState<string | null>(null)
  const [selectedApiRefreshKey, setSelectedApiRefreshKey] = useState(0)
  const [selectedPreviewRefreshKey, setSelectedPreviewRefreshKey] = useState(0)
  const [apis, setApis] = useState<ApiProject[]>([])
  const [lastZipJobId, setLastZipJobId] = useState<string | null>(null)
  const [zipJobs, setZipJobs] = useState<ZipJob[]>([])
  const [jobStatuses, setJobStatuses] = useState<Record<string, string>>({})
  const [status, setStatus] = useState<Status | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const pollRef = useRef<number | null>(null)
  const terminalGenerationRef = useRef<string | null>(null)
  const selectedApiIdRef = useRef<string | null>(null)
  const jobIdRef = useRef<string | null>(null)
  const startInFlightRef = useRef(false)
  const selectedGeneration = useGeneration(selectedApiId, selectedApiRefreshKey)
  const selectedPreview = usePreview(selectedApiId, selectedPreviewRefreshKey)
  const previewLogs = usePreviewLogs(selectedApiId, selectedPreviewRefreshKey)
  const previewDiagnostics = usePreviewDiagnostics(selectedApiId, selectedPreviewRefreshKey)
  const presets: GenerationPreset[] = [
    {
      key: 'postgres',
      appName: 'postgres-starter-api',
      basePackage: 'com.example.postgresapi',
      databaseType: 'postgres',
      jdbcUrl: 'jdbc:postgresql://your-db-host:5432/your_database',
      jdbcUsername: 'your_db_user',
      jdbcPassword: '',
      schema: 'public',
      build: true,
      deployDocker: false,
      hostPort: 18080,
    },
    {
      key: 'mysql',
      appName: 'mysql-starter-api',
      basePackage: 'com.example.mysqlapi',
      databaseType: 'mysql',
      jdbcUrl: 'jdbc:mysql://your-db-host:3306/your_database?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC',
      jdbcUsername: 'your_db_user',
      jdbcPassword: '',
      schema: 'app',
      build: true,
      deployDocker: false,
      hostPort: 18081,
    },
    {
      key: 'h2',
      appName: 'h2-demo-api',
      basePackage: 'com.example.h2api',
      databaseType: 'h2',
      jdbcUrl: 'jdbc:h2:mem:generated_api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
      jdbcUsername: 'sa',
      jdbcPassword: '',
      schema: 'PUBLIC',
      build: false,
      deployDocker: false,
      hostPort: 18082,
    },
  ]

      const userActions = useMemo(() => (
    text.lang.code === 'fr'
      ? [
          '1. Remplis le formulaire avec ton app, ta base et tes identifiants.',
          '2. Lance la generation puis suis la progression en direct.',
          '3. Ouvre Swagger pour tester l API generee sans manipuler d URL interne.',
          '4. Verifie la sante de l API avec le bouton Health.',
          '5. Active la preview Docker seulement si tu veux un runtime isole.',
          '6. Telecharge le ZIP final quand la generation est terminee.',
        ]
      : [
          '1. Fill in the form with your app, database, and credentials.',
          '2. Start generation and track progress live.',
          '3. Open Swagger to test the generated API without dealing with internal URLs.',
          '4. Check API readiness with the Health button.',
          '5. Start Docker preview only when you need an isolated runtime.',
          '6. Download the final ZIP once generation is complete.',
        ]
  ), [text.lang.code])

  const refreshJob = useCallback(async (id: string) => {
    const nextStatus = await api.getGenerationStatus(id)
    setStatus(nextStatus)
    setJobStatuses((prev) => ({ ...prev, [id]: nextStatus.status }))
    return nextStatus
  }, [])

  function applyPreset(preset: GenerationPreset) {
    setGenerationMode('jdbc')
    setAppName(preset.appName)
    setBasePackage(preset.basePackage)
    setDatabaseType(preset.databaseType)
    setJdbcUrl(preset.jdbcUrl)
    setJdbcUsername(preset.jdbcUsername)
    setJdbcPassword(preset.jdbcPassword)
    setSchema(preset.schema)
    setBuild(preset.build)
    setDeployDocker(canDeployDocker ? preset.deployDocker : false)
    setHostPort(preset.hostPort)
    setErr(null)
  }

  function isYamlFile(file: File | null) {
    if (!file) return false
    const name = file.name.toLowerCase()
    return name.endsWith('.yaml') || name.endsWith('.yml')
  }

  function onYamlFileSelected(file: File | null) {
    setYamlFile(file)
    if (file && !isYamlFile(file)) {
      setErr('YAML_SCHEMA_INVALID_EXTENSION')
    } else {
      setErr(null)
    }
  }

  function buildZipJobs(projects: ApiProject[]) {
    return projects
      .filter((project) => !!project.jobId)
      .sort((a, b) => Date.parse(b.createdAt ?? '') - Date.parse(a.createdAt ?? ''))
      .slice(0, 5)
      .map((project) => ({
        jobId: project.jobId!,
        createdAt: project.createdAt,
        zipDownloadedAt: project.zipDownloadedAt,
      }))
  }

  function persistentStatusLabel(value?: string | null) {
    switch (value) {
      case 'DONE':
        return text.lang.code === 'fr' ? 'Terminee' : 'Done'
      case 'FAILED':
        return text.lang.code === 'fr' ? 'Echouee' : 'Failed'
      case 'PENDING':
        return text.lang.code === 'fr' ? 'En cours' : 'Pending'
      default:
        return value ?? t.ready
    }
  }

  function persistentStatusClass(value?: string | null) {
    if (value === 'FAILED') return 'badge bad'
    if (value === 'DONE') return 'badge good'
    return 'pill'
  }

  function previewStatusClass(value?: string | null) {
    if (value === 'FAILED') return 'badge bad'
    if (value === 'RUNNING') return 'badge good'
    if (value === 'STARTING' || value === 'STOPPING') return 'badge warn'
    return 'pill'
  }

  function formatDateTime(value?: string | null) {
    if (!value) return '-'
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
  }

  function formatDurationSeconds(seconds: number | null) {
    if (seconds === null || Number.isNaN(seconds)) return '-'
    if (seconds < 60) return `${seconds}s`
    const minutes = Math.floor(seconds / 60)
    const remainingSeconds = seconds % 60
    return remainingSeconds === 0 ? `${minutes}m` : `${minutes}m ${remainingSeconds}s`
  }

  function isLoopbackUrl(value?: string | null) {
    if (!value) return false
    try {
      const url = new URL(value, window.location.origin)
      return url.hostname === '127.0.0.1' || url.hostname === 'localhost'
    } catch {
      return false
    }
  }

  function isLocalBrowser() {
    const host = window.location.hostname
    return host === '127.0.0.1' || host === 'localhost'
  }

  function joinUrl(base?: string | null, suffix = '') {
    if (!base) return ''
    let resolvedBase = base
    try {
      resolvedBase = new URL(base, env.apiBaseUrl).toString()
    } catch {
      resolvedBase = base
    }
    const normalizedBase = resolvedBase.endsWith('/') ? resolvedBase.slice(0, -1) : resolvedBase
    const normalizedSuffix = suffix.startsWith('/') ? suffix : `/${suffix}`
    return `${normalizedBase}${normalizedSuffix}`
  }

  function previewDurationSeconds(from?: string | null, to?: string | null) {
    if (!from || !to) return null
    const start = Date.parse(from)
    const end = Date.parse(to)
    if (Number.isNaN(start) || Number.isNaN(end) || end < start) return null
    return Math.round((end - start) / 1000)
  }

  function translateError(message?: string | null) {
    if (!message) return ''
    const normalized = message
      .replace(/^Invalid config:\s*/i, '')
      .replace(/^Bad Request:\s*/i, '')
      .trim()
    const messages = t.errorMessages as Record<string, string>

    if (messages[normalized]) {
      return messages[normalized]
    }

    const runtimeBinaryMissing = normalized.match(/^Container runtime binary is not available:\s*(.+)$/)
    if (runtimeBinaryMissing) {
      return text.lang.code === 'fr'
        ? `Le runtime de conteneur est introuvable sur cet hote : ${runtimeBinaryMissing[1]}. Installe Docker ou Podman, puis redemarre la preview.`
        : `The container runtime is missing on this host: ${runtimeBinaryMissing[1]}. Install Docker or Podman, then restart preview.`
    }

    const runtimeUnavailable = normalized.match(/^Container runtime is not reachable:\s*(.+)$/)
    if (runtimeUnavailable) {
      return text.lang.code === 'fr'
        ? `Le runtime de conteneur ne repond pas : ${runtimeUnavailable[1]}. Verifie que Docker ou Podman est demarre avant de lancer la preview.`
        : `The container runtime is not reachable: ${runtimeUnavailable[1]}. Make sure Docker or Podman is running before starting preview.`
    }

    if (normalized === 'Maven build command is not available for preview runtime') {
      return text.lang.code === 'fr'
        ? 'Aucune commande Maven exploitable n est disponible pour construire la preview. Verifie `mvn`, `mvnw` ou la configuration hote.'
        : 'No usable Maven command is available to build the preview. Check `mvn`, `mvnw`, or the host setup.'
    }

    if (normalized === 'Generated ZIP is not available') {
      return text.lang.code === 'fr'
        ? 'Le ZIP genere n est pas disponible. Relance une generation terminee avant de demarrer la preview.'
        : 'The generated ZIP is not available. Run a completed generation again before starting preview.'
    }

    if (normalized === 'No tables were found in the configured database/schema. Prepare your database schema before generating the API.') {
      return text.lang.code === 'fr'
        ? 'Aucune table trouvee dans ce schema. Verifie que le schema saisi existe, que l utilisateur JDBC a les droits de lecture, et que la base contient bien des tables.'
        : 'No tables were found in this schema. Check that the schema exists, the JDBC user can read it, and the database contains tables.'
    }

    if (normalized === 'Preview startup timed out' || normalized === 'Preview did not become reachable in time') {
      return text.lang.code === 'fr'
        ? 'La preview a demarre trop lentement ou n est jamais devenue accessible. Ouvre les logs preview et verifie le runtime conteneur.'
        : 'Preview started too slowly or never became reachable. Open preview logs and verify the container runtime.'
    }

    const previewProbeNotReady = normalized.match(/^Preview probe\s+(.+)\s+is not ready$/)
    if (previewProbeNotReady) {
      return text.lang.code === 'fr'
        ? `La verification de sante ${previewProbeNotReady[1]} repond mais l application n est pas encore prete. Verifie l endpoint de health et les logs preview.`
        : `The health probe ${previewProbeNotReady[1]} answered but the application is not ready yet. Check the health endpoint and preview logs.`
    }

    const commandFailed = normalized.match(/^Command failed \((\d+)\):\s+(.+)$/)
    if (commandFailed) {
      const command = commandFailed[2]
      if (command.includes('clean package') || command.includes('mvn')) {
        return text.lang.code === 'fr'
          ? 'La construction Maven de la preview a echoue. Verifie les logs preview et la disponibilite des artefacts locaux.'
          : 'The preview Maven build failed. Check preview logs and local artifact availability.'
      }
      if (command.includes(' build ') || command.endsWith(' build') || command.includes('docker build') || command.includes('podman build')) {
        return text.lang.code === 'fr'
          ? 'La construction de l image de preview a echoue. Verifie le runtime conteneur et les logs preview.'
          : 'The preview image build failed. Check the container runtime and preview logs.'
      }
      if (command.includes(' run ') || command.includes('docker run') || command.includes('podman run')) {
        return text.lang.code === 'fr'
          ? 'Le conteneur de preview n a pas pu demarrer. Verifie le runtime conteneur, le port expose et les logs preview.'
          : 'The preview container could not start. Check the container runtime, the exposed port, and preview logs.'
      }
    }

    const commandTimedOut = normalized.match(/^Command timed out:\s+(.+)$/)
    if (commandTimedOut) {
      const command = commandTimedOut[1]
      if (command.includes('clean package') || command.includes('mvn')) {
        return text.lang.code === 'fr'
          ? 'La construction Maven de la preview a depasse le temps autorise. Verifie les logs preview et la charge de la machine.'
          : 'The preview Maven build exceeded the allowed time. Check preview logs and machine load.'
      }
      return text.lang.code === 'fr'
        ? 'Une commande preview a depasse le temps autorise. Verifie les logs preview et l etat du runtime conteneur.'
        : 'A preview command exceeded the allowed time. Check preview logs and the container runtime state.'
    }

    const unsupportedDatabase = normalized.match(/^Unsupported databaseType:\s*(.+)$/)
    if (unsupportedDatabase) {
      return text.lang.code === 'fr'
        ? `Type de base non supporte : ${unsupportedDatabase[1]}.`
        : `Unsupported database type: ${unsupportedDatabase[1]}.`
    }

    if (normalized.includes('hostPort must be >= 1')) {
      return text.lang.code === 'fr'
        ? 'Le port Docker doit etre superieur ou egal a 1.'
        : 'Docker port must be greater than or equal to 1.'
    }

    if (normalized.includes('hostPort must be <= 65535')) {
      return text.lang.code === 'fr'
        ? 'Le port Docker doit etre inferieur ou egal a 65535.'
        : 'Docker port must be less than or equal to 65535.'
    }

    return normalized
  }

  function translatePreviewErrorCode(code?: string | null, hint?: string | null) {
    if (!code) return hint ?? ''
    const localizedHint = hint ?? ''
    switch (code) {
      case 'HOST_RUNTIME_BINARY_MISSING':
        return text.lang.code === 'fr'
          ? 'Le runtime de conteneur configure est absent sur cet hote. Installe Docker ou Podman puis relance la preview.'
          : 'The configured container runtime is missing on this host. Install Docker or Podman, then restart preview.'
      case 'HOST_RUNTIME_UNREACHABLE':
        return text.lang.code === 'fr'
          ? 'Le runtime de conteneur est installe mais ne repond pas. Demarre Docker ou Podman avant de lancer la preview.'
          : 'The container runtime is installed but unreachable. Start Docker or Podman before launching preview.'
      case 'HOST_MAVEN_UNAVAILABLE':
        return text.lang.code === 'fr'
          ? 'Aucune commande Maven exploitable n est disponible pour construire la preview.'
          : 'No usable Maven command is available to build the preview.'
      case 'PREVIEW_ZIP_MISSING':
        return text.lang.code === 'fr'
          ? 'Le ZIP persiste de cette generation est introuvable. Relance une generation complete.'
          : 'The persisted ZIP for this generation is missing. Run a fresh successful generation.'
      case 'PREVIEW_CONFIG_MISSING':
        return text.lang.code === 'fr'
          ? 'La configuration de lancement preview est absente pour cette generation.'
          : 'The preview launch configuration is missing for this generation.'
      case 'PREVIEW_STARTUP_TIMEOUT':
        return text.lang.code === 'fr'
          ? 'La preview n est pas devenue accessible avant le timeout.'
          : 'Preview did not become reachable before the timeout.'
      case 'PREVIEW_HEALTH_NOT_READY':
        return text.lang.code === 'fr'
          ? 'L endpoint de health repond mais l application n est pas encore prete.'
          : 'The health endpoint responds but the application is not ready yet.'
      case 'PREVIEW_BUILD_FAILED':
        return text.lang.code === 'fr'
          ? 'La construction Maven de la preview a echoue.'
          : 'The preview Maven build failed.'
      case 'PREVIEW_IMAGE_BUILD_FAILED':
        return text.lang.code === 'fr'
          ? 'La construction de l image de preview a echoue.'
          : 'The preview image build failed.'
      case 'PREVIEW_CONTAINER_START_FAILED':
        return text.lang.code === 'fr'
          ? 'Le conteneur de preview n a pas pu demarrer.'
          : 'The preview container could not start.'
      case 'PREVIEW_COMMAND_TIMEOUT':
        return text.lang.code === 'fr'
          ? 'Une commande preview a depasse le temps autorise.'
          : 'A preview command exceeded the allowed time.'
      default:
        return localizedHint
    }
  }

  function hostCheckLabel(key: string) {
    if (text.lang.code === 'fr') {
      switch (key) {
        case 'containerRuntimeBinary':
          return 'Binaire conteneur'
        case 'containerRuntimeReachable':
          return 'Runtime conteneur'
        case 'mavenCommandAvailable':
          return 'Commande Maven'
        default:
          return key
      }
    }
    switch (key) {
      case 'containerRuntimeBinary':
        return 'Container binary'
      case 'containerRuntimeReachable':
        return 'Container runtime'
      case 'mavenCommandAvailable':
        return 'Maven command'
      default:
        return key
    }
  }

  function hostCheckDetails(key: string, ok: boolean, runtime?: string | null) {
    if (text.lang.code === 'fr') {
      switch (key) {
        case 'containerRuntimeBinary':
          return ok
            ? `Le binaire ${runtime ?? 'container runtime'} est disponible sur l hote.`
            : `Le binaire ${runtime ?? 'container runtime'} est introuvable sur l hote.`
        case 'containerRuntimeReachable':
          return ok
            ? `Le runtime ${runtime ?? 'conteneur'} repond correctement.`
            : `Le runtime ${runtime ?? 'conteneur'} ne repond pas encore.`
        case 'mavenCommandAvailable':
          return ok
            ? 'Une commande Maven exploitable est disponible pour construire la preview.'
            : 'Aucune commande Maven exploitable n est disponible pour construire la preview.'
        default:
          return ok ? 'Verification OK.' : 'Verification en echec.'
      }
    }
    switch (key) {
      case 'containerRuntimeBinary':
        return ok
          ? `The ${runtime ?? 'container runtime'} binary is available on the host.`
          : `The ${runtime ?? 'container runtime'} binary is missing on the host.`
      case 'containerRuntimeReachable':
        return ok
          ? `The ${runtime ?? 'container'} runtime is reachable.`
          : `The ${runtime ?? 'container'} runtime is not reachable yet.`
      case 'mavenCommandAvailable':
        return ok
          ? 'A usable Maven command is available to build the preview.'
          : 'No usable Maven command is available to build the preview.'
      default:
        return ok ? 'Check passed.' : 'Check failed.'
    }
  }

  function translateRecommendedAction(recommendation?: PreviewDiagnostics['recommendedAction']) {
    const code = recommendation?.code
    if (!code) return recommendation?.message ?? ''
    switch (code) {
      case 'WAIT_FOR_GENERATION':
        return text.lang.code === 'fr'
          ? 'Attends que la generation passe a DONE avant de lancer la preview.'
          : 'Wait until generation reaches DONE before starting preview.'
      case 'REGENERATE_FOR_PREVIEW_CONFIG':
        return text.lang.code === 'fr'
          ? 'Relance une generation pour recreer la configuration de lancement preview.'
          : 'Run a fresh generation to recreate the preview launch configuration.'
      case 'REGENERATE_FOR_ZIP':
        return text.lang.code === 'fr'
          ? 'Relance une generation complete pour restaurer le ZIP utilise par la preview.'
          : 'Run a successful generation again to restore the ZIP used by preview.'
      case 'FIX_HOST_DIAGNOSTICS':
        return text.lang.code === 'fr'
          ? 'Corrige d abord les checks hote en echec avant de lancer la preview.'
          : 'Fix the failing host checks before launching preview.'
      case 'FOLLOW_FAILURE_HINT':
        return translatePreviewErrorCode(selectedPreview?.errorCode, selectedPreview?.errorHint) || recommendation?.message || ''
      case 'START_PREVIEW':
        return text.lang.code === 'fr'
          ? 'Les checks sont au vert. Tu peux lancer ou relancer la preview.'
          : 'Checks are green. You can start or restart preview.'
      default:
        return recommendation?.message ?? ''
    }
  }

  function saveBlob(blob: Blob, filename: string) {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  }

  const refreshHistory = useCallback(async () => {
    const projects = await api.getMyApis()
    setApis(projects)
    setZipJobs(buildZipJobs(projects))
    return projects
  }, [])

  async function start() {
    if (startInFlightRef.current || generationInProgress || loading) return
    startInFlightRef.current = true
    setErr(null)
    setLoading(true)
    setGenerationInProgress(true)
    setStatus(null)
    terminalGenerationRef.current = null

    try {
      if (generationMode === 'yaml') {
        if (!isYamlFile(yamlFile)) {
          setErr('YAML_SCHEMA_INVALID_EXTENSION')
          setGenerationInProgress(false)
          return
        }
      }

      const res = generationMode === 'yaml'
        ? await api.startSchemaFileGeneration({
            file: yamlFile!,
            build,
            deployDocker: false,
            hostPort,
          }, true)
        : await api.startGeneration({
            appName: normalizeAppName(appName),
            basePackage,
            databaseType,
            jdbcUrl,
            jdbcUsername,
            jdbcPassword,
            schema,
            build,
            deployDocker: env.dockerRuntimeBlocked ? false : deployDocker,
            hostPort,
          }, true)

      setJobId(res.jobId)
      setJobStatuses((prev) => ({ ...prev, [res.jobId]: 'PENDING' }))
      if (res.generatedApiId) {
        setSelectedApiId(res.generatedApiId)
        setSelectedApiRefreshKey((prev) => prev + 1)
        setSelectedPreviewRefreshKey((prev) => prev + 1)
      }

      const projects = await refreshHistory().catch(() => [])
      if (!res.generatedApiId && projects[0]?.id) {
        setSelectedApiId(projects[0].id)
        setSelectedApiRefreshKey((prev) => prev + 1)
        setSelectedPreviewRefreshKey((prev) => prev + 1)
      }

      const initialStatus = await refreshJob(res.jobId)
      if (JOB_TERMINAL_STATUSES.has(initialStatus.status)) {
        await refreshHistory().catch(() => undefined)
        setGenerationInProgress(false)
        return
      }

      if (pollRef.current) window.clearInterval(pollRef.current)
      pollRef.current = window.setInterval(async () => {
        try {
          const nextStatus = await refreshJob(res.jobId)
          if (JOB_TERMINAL_STATUSES.has(nextStatus.status)) {
            await refreshHistory().catch(() => undefined)
            if (pollRef.current) window.clearInterval(pollRef.current)
            pollRef.current = null
            setGenerationInProgress(false)
          }
        } catch {
          // Network hiccups should not stop the current job.
        }
      }, 900)
    } catch (e) {
      setErr(e instanceof Error ? e.message : t.startError)
      setGenerationInProgress(false)
    } finally {
      startInFlightRef.current = false
      setLoading(false)
    }
  }

  async function stop() {
    if (!jobId) return
    if (env.dockerRuntimeBlocked) {
      setErr(t.dockerTemporarilyUnavailable)
      return
    }
    setLoading(true)
    try {
      await api.stopGeneration(jobId)
      await refreshJob(jobId)
      setSelectedApiRefreshKey((prev) => prev + 1)
      setSelectedPreviewRefreshKey((prev) => prev + 1)
    } catch (e) {
      setErr(e instanceof Error ? e.message : t.stopError)
    } finally {
      setLoading(false)
    }
  }

  async function downloadZip(targetJobId?: string) {
    const effectiveJobId = targetJobId ?? zipJobId
    if (!effectiveJobId) return
    setErr(null)
    try {
      const blob = await api.downloadGenerationZip(effectiveJobId)
      saveBlob(blob, `generated-api-${effectiveJobId}.zip`)
      const downloadedAt = new Date().toISOString()
      setZipJobs((prev) => prev.map((item) => (
        item.jobId === effectiveJobId ? { ...item, zipDownloadedAt: downloadedAt } : item
      )))
      setApis((prev) => prev.map((item) => (
        item.jobId === effectiveJobId ? { ...item, zipDownloadedAt: downloadedAt } : item
      )))
      setSelectedApiRefreshKey((prev) => prev + 1)
      setSelectedPreviewRefreshKey((prev) => prev + 1)
    } catch (e) {
      setErr(e instanceof Error ? e.message : t.downloadError)
    }
  }

  async function downloadPersistedApi(project: Pick<ApiProject, 'id' | 'name' | 'downloadUrl' | 'jobId'>) {
    if (!project.downloadUrl) return
    setErr(null)
    try {
      const blob = await api.downloadFile(project.downloadUrl)
      saveBlob(blob, `${project.name || 'generated-api'}.zip`)
      const downloadedAt = new Date().toISOString()
      setApis((prev) => prev.map((item) => (
        item.id === project.id ? { ...item, zipDownloadedAt: downloadedAt } : item
      )))
      if (project.jobId) {
        setZipJobs((prev) => prev.map((item) => (
          item.jobId === project.jobId ? { ...item, zipDownloadedAt: downloadedAt } : item
        )))
      }
      setSelectedApiRefreshKey((prev) => prev + 1)
      setSelectedPreviewRefreshKey((prev) => prev + 1)
    } catch (e) {
      setErr(e instanceof Error ? e.message : t.downloadError)
    }
  }

  async function startPreview() {
    if (!selectedApiId) return
    if (env.dockerRuntimeBlocked) {
      setErr(t.dockerTemporarilyUnavailable)
      return
    }
    setErr(null)
    setLoading(true)
    try {
      await api.startGeneratedApiPreview(selectedApiId)
      setSelectedPreviewRefreshKey((prev) => prev + 1)
    } catch (e) {
      setErr(e instanceof Error ? e.message : t.startError)
    } finally {
      setLoading(false)
    }
  }

  async function stopPreview() {
    if (!selectedApiId) return
    if (env.dockerRuntimeBlocked) {
      setErr(t.dockerTemporarilyUnavailable)
      return
    }
    setErr(null)
    setLoading(true)
    try {
      await api.stopGeneratedApiPreview(selectedApiId)
      setSelectedPreviewRefreshKey((prev) => prev + 1)
    } catch (e) {
      setErr(e instanceof Error ? e.message : t.stopError)
    } finally {
      setLoading(false)
    }
  }

  async function restartPreview() {
    if (!selectedApiId) return
    if (env.dockerRuntimeBlocked) {
      setErr(t.dockerTemporarilyUnavailable)
      return
    }
    setErr(null)
    setLoading(true)
    try {
      await api.restartGeneratedApiPreview(selectedApiId)
      setSelectedPreviewRefreshKey((prev) => prev + 1)
    } catch (e) {
      setErr(e instanceof Error ? e.message : t.startError)
    } finally {
      setLoading(false)
    }
  }

  function selectProject(project: ApiProject) {
    setSelectedApiId(project.id)
    setSelectedApiRefreshKey((prev) => prev + 1)
    setSelectedPreviewRefreshKey((prev) => prev + 1)
    setJobId(project.jobId ?? null)
    if (project.jobId) {
      void refreshJob(project.jobId).catch(() => undefined)
    } else {
      setStatus(null)
    }
  }

  useEffect(() => {
    return () => {
      if (pollRef.current) window.clearInterval(pollRef.current)
    }
  }, [])

  useEffect(() => {
    selectedApiIdRef.current = selectedApiId
  }, [selectedApiId])

  useEffect(() => {
    jobIdRef.current = jobId
  }, [jobId])

  useEffect(() => {
    let active = true

    void refreshHistory()
      .then(async (projects) => {
        if (!active) return

        const candidates = buildZipJobs(projects)

        if (!selectedApiIdRef.current && projects[0]?.id) {
          setSelectedApiId(projects[0].id)
          setSelectedPreviewRefreshKey((prev) => prev + 1)
        }

        if (!jobIdRef.current) {
          const latestJobId = candidates[0]?.jobId
          if (latestJobId) {
            setJobId(latestJobId)
            try {
              await refreshJob(latestJobId)
            } catch {
              // A persisted generation can outlive the in-memory job after a backend restart.
            }
          }
        }

        const statuses: Record<string, string> = {}
        for (const project of candidates) {
          try {
            const candidateStatus = await api.getGenerationStatus(project.jobId)
            statuses[project.jobId] = candidateStatus.status
          } catch {
            // Ignore stale jobs not present in memory anymore.
          }
        }
        if (active && Object.keys(statuses).length > 0) {
          setJobStatuses((prev) => ({ ...prev, ...statuses }))
        }

        const latestReadyNotDownloaded = candidates.find(
          (project) => !project.zipDownloadedAt && ZIP_READY_STATUSES.has(statuses[project.jobId] ?? '')
        )?.jobId
        if (latestReadyNotDownloaded && active) {
          setLastZipJobId(latestReadyNotDownloaded)
        }
      })
      .catch(() => undefined)

    return () => { active = false }
  }, [refreshHistory, refreshJob])

  useEffect(() => {
    if (!selectedGeneration?.id || !selectedGeneration.status) return

    if (!TERMINAL_GENERATION_STATUSES.has(selectedGeneration.status)) {
      terminalGenerationRef.current = null
      return
    }

    if (!selectedGeneration.jobId || selectedGeneration.jobId === jobIdRef.current) {
      if (pollRef.current) {
        window.clearInterval(pollRef.current)
        pollRef.current = null
      }
      setGenerationInProgress(false)
      setLoading(false)
    }

    const key = `${selectedGeneration.id}:${selectedGeneration.status}:${selectedGeneration.finishedAt ?? ''}`
    if (terminalGenerationRef.current === key) {
      return
    }
    terminalGenerationRef.current = key

    void refreshHistory().catch(() => undefined)
    if (selectedGeneration.jobId) {
      void refreshJob(selectedGeneration.jobId).catch(() => undefined)
    }
  }, [refreshHistory, refreshJob, selectedGeneration?.finishedAt, selectedGeneration?.id, selectedGeneration?.jobId, selectedGeneration?.status])

  const selectedProgress = selectedGeneration?.progress ?? 0
  const selectedLogs = selectedGeneration?.logs?.trim() ?? ''
  const logLineCount = selectedLogs ? selectedLogs.split('\n').length : 0
  const rawApiUrl = selectedGeneration?.apiBaseUrl ?? status?.apiBaseUrl
  const generatedApiUsesLoopback = isLoopbackUrl(rawApiUrl) && !isLocalBrowser()
  const apiUrl = rawApiUrl
  const apiActionBaseUrl = generatedApiUsesLoopback ? null : rawApiUrl
  const previewUrl = selectedPreview?.baseUrl
  const previewProxyUrl = selectedPreview?.proxyUrl
  const previewUsesLoopback = isLoopbackUrl(previewUrl) && !isLocalBrowser()
  const visiblePreviewUrl = previewUsesLoopback && previewProxyUrl ? previewProxyUrl : previewUrl
  const previewActionBaseUrl = previewProxyUrl ?? visiblePreviewUrl
  const previewIsRunning = selectedPreview?.status === 'RUNNING'
  const generatedApiIsRunning = !!apiActionBaseUrl
    && (selectedGeneration?.status === 'DONE' || selectedGeneration?.status === 'DEPLOYED' || status?.status === 'DEPLOYED')
  const previewStartupSeconds = previewDurationSeconds(selectedPreview?.createdAt, selectedPreview?.startedAt)
  const previewRuntimeSeconds = previewDurationSeconds(selectedPreview?.startedAt, selectedPreview?.stoppedAt)
  const previewError = translateError(selectedPreview?.errorMessage)
  const previewErrorHint = translatePreviewErrorCode(selectedPreview?.errorCode, selectedPreview?.errorHint)
  const previewRecommendedAction = translateRecommendedAction(previewDiagnostics?.recommendedAction)
  const canDeployDocker = quotas?.canDeployDocker ?? false
  const h2RuntimeDisabled = databaseType === 'h2'
  const yamlRuntimeDisabled = generationMode === 'yaml'
  const dockerToggleDisabled = env.dockerRuntimeBlocked || !canDeployDocker || h2RuntimeDisabled || yamlRuntimeDisabled
  const dockerDisabledMessage = env.dockerRuntimeBlocked
    ? t.dockerTemporarilyUnavailable
    : (yamlRuntimeDisabled ? t.yamlDockerLocked : (h2RuntimeDisabled ? t.deployDockerH2Locked : (canDeployDocker ? t.deployDockerHelp : t.deployDockerBetaLocked)))
  const dockerUnavailableTooltip = env.dockerRuntimeBlocked ? t.dockerTemporarilyUnavailableTooltip : dockerDisabledMessage
  const isDockerRunning = status?.status === 'DEPLOYED' && !!status?.containerId
  const liveJobId = selectedGeneration?.jobId ?? jobId
  const isZipReady = useCallback((candidateJobId: string) => ZIP_READY_STATUSES.has(jobStatuses[candidateJobId] ?? ''), [jobStatuses])
  const currentJobDownloaded = liveJobId ? zipJobs.find((item) => item.jobId === liveJobId)?.zipDownloadedAt : null
  const zipJobId = useMemo(() => {
    if (liveJobId && !currentJobDownloaded && isZipReady(liveJobId)) {
      return liveJobId
    }
    return zipJobs.find((item) => !item.zipDownloadedAt && isZipReady(item.jobId))?.jobId ?? lastZipJobId
  }, [currentJobDownloaded, isZipReady, lastZipJobId, liveJobId, zipJobs])

  const selectedDownloadReady = !!selectedGeneration?.downloadUrl
    && !selectedGeneration.zipDownloadedAt
    && selectedGeneration.status === 'DONE'
  const actionBusy = loading || generationInProgress

  useEffect(() => {
    if ((env.dockerRuntimeBlocked || !canDeployDocker || h2RuntimeDisabled || yamlRuntimeDisabled) && deployDocker) {
      setDeployDocker(false)
    }
  }, [canDeployDocker, deployDocker, h2RuntimeDisabled, yamlRuntimeDisabled])

  return (
    <Shell
      title={t.title}
      subtitle={t.subtitle}
      actions={
        <>
          <button className="btn primary" disabled={actionBusy} onClick={start}>
            {actionBusy ? t.processing : t.generate}
          </button>
          <span
            className="tooltipWrap"
            data-tooltip={env.dockerRuntimeBlocked ? dockerUnavailableTooltip : undefined}
            data-tooltip-placement="bottom"
            tabIndex={env.dockerRuntimeBlocked ? 0 : undefined}
          >
            <button className="btn" disabled={env.dockerRuntimeBlocked || actionBusy || !isDockerRunning || !jobId} onClick={stop}>{t.stop}</button>
          </span>
          {selectedDownloadReady ? (
            <button
              className="btn"
              disabled={actionBusy}
              onClick={() => downloadPersistedApi(selectedGeneration)}
              title={t.zip}
            >
              {t.zip}
            </button>
          ) : zipJobId ? (
            <button
              className="btn"
              disabled={actionBusy}
              onClick={() => downloadZip()}
              title={t.zip}
            >
              {t.zip}
            </button>
          ) : null}
        </>
      }
    >
      <div className="figmaGeneratorDashboard">
        <div className="figmaGeneratorPanel figmaGeneratorPanelForm">
          <div className="panelHeader">
            <div>
              <h3 className="panelTitle panelTitleWithIcon"><Icon name="bolt" size={17} />{t.configTitle}</h3>
              <p className="panelText">{t.configText}</p>
            </div>
            <span className="pill">{t.form}</span>
          </div>

          <div className="figmaGeneratorMode generatorModePanel">
            <div className="panelHeader" style={{ marginBottom: 12 }}>
              <div>
                <h3 className="panelTitle panelTitleWithIcon"><Icon name="db" size={17} />{t.modeTitle}</h3>
                <p className="panelText">{generationMode === 'yaml' ? t.yamlModeText : t.jdbcModeText}</p>
              </div>
            </div>
            <div className="segmentedControl" role="tablist" aria-label={t.modeTitle}>
              <button
                className={generationMode === 'jdbc' ? 'segmentedButton active' : 'segmentedButton'}
                role="tab"
                aria-selected={generationMode === 'jdbc'}
                type="button"
                onClick={() => {
                  setGenerationMode('jdbc')
                  setErr(null)
                }}
              >
                {t.modeJdbc}
              </button>
              <button
                className={generationMode === 'yaml' ? 'segmentedButton active' : 'segmentedButton'}
                role="tab"
                aria-selected={generationMode === 'yaml'}
                type="button"
                onClick={() => {
                  setGenerationMode('yaml')
                  setDeployDocker(false)
                  setErr(null)
                }}
              >
                {t.modeYaml}
              </button>
            </div>
          </div>

          {generationMode === 'jdbc' ? <div className="figmaGeneratorCallout onboardingPresetCallout">
            <div className="panelHeader" style={{ marginBottom: 10 }}>
              <div>
                <h3 className="panelTitle panelTitleWithIcon"><Icon name="jdbc" size={17} />{t.presetsTitle}</h3>
                <p className="panelText">{t.presetsText}</p>
              </div>
            </div>
            <div className="onboardingPresetGrid">
              {presets.map((preset) => {
                const presetText = t.presets[preset.key]
                return (
                  <button
                    key={preset.key}
                    className="onboardingPresetCard"
                    type="button"
                    onClick={() => applyPreset(preset)}
                  >
                    <span className="badge good">{presetText.badge}</span>
                    <span className="presetTitleRow">
                      <span className="techBadgeIcon techBadgeIcon--preset"><Icon name={presetIconName(preset.databaseType)} size={18} /></span>
                      <strong>{presetText.title}</strong>
                    </span>
                    <span>{presetText.description}</span>
                    <small>{presetText.details}</small>
                  </button>
                )
              })}
            </div>
          </div> : null}

          <div className="grid">
            {generationMode === 'yaml' ? (
              <div className="figmaYamlDropzone yamlDropzone">
                <div className="panelHeader" style={{ marginBottom: 12 }}>
                  <div>
                    <h3 className="panelTitle panelTitleWithIcon"><VisualIcon name="yaml" />{t.yamlUploadTitle}</h3>
                    <p className="panelText">{t.yamlUploadText}</p>
                  </div>
                </div>
                <input
                  aria-label={t.yamlFileLabel}
                  className="input yamlFileInput"
                  type="file"
                  accept=".yaml,.yml"
                  onChange={(e) => onYamlFileSelected(e.target.files?.[0] ?? null)}
                />
                <p className="panelText" style={{ marginTop: 8 }}>
                  {yamlFile ? `${t.selectedFile}: ${yamlFile.name}` : t.noYamlFile}
                </p>
                <p className="panelText" style={{ marginTop: 8 }}>
                  {t.yamlDockerLocked}
                </p>
              </div>
            ) : (
              <>
                <div>
                  <div className="label">{t.appName}</div>
                  <input className="input" value={appName} onChange={(e) => setAppName(normalizeAppName(e.target.value))} />
                </div>

                <div>
                  <div className="label">{t.basePackage}</div>
                  <input className="input" value={basePackage} onChange={(e) => setBasePackage(e.target.value)} />
                </div>

                <div className="grid cols-2">
                  <div>
                    <div className="label">{t.database}</div>
                    <select className="input" value={databaseType} onChange={(e) => setDatabaseType(e.target.value as 'postgres' | 'mysql' | 'h2')}>
                      <option value="postgres">PostgreSQL</option>
                      <option value="mysql">MySQL</option>
                      <option value="h2">H2</option>
                    </select>
                  </div>
                  <div>
                    <div className="label">{t.schema}</div>
                    <input className="input" value={schema} onChange={(e) => setSchema(e.target.value)} />
                  </div>
                </div>

                <div>
                  <div className="label">{t.jdbcUrl}</div>
                  <input className="input" value={jdbcUrl} onChange={(e) => setJdbcUrl(e.target.value)} />
                </div>

                <div className="grid cols-2">
                  <div>
                    <div className="label">{t.username}</div>
                    <input className="input" value={jdbcUsername} onChange={(e) => setJdbcUsername(e.target.value)} />
                  </div>
                  <div>
                    <div className="label">{t.password}</div>
                    <input className="input" type="password" value={jdbcPassword} onChange={(e) => setJdbcPassword(e.target.value)} />
                  </div>
                </div>
              </>
            )}

            <div className="figmaGeneratorCallout" style={{ marginBottom: 0 }}>
              <div className="panelHeader" style={{ marginBottom: showAdvanced ? 12 : 0 }}>
                <div>
                  <h3 className="panelTitle panelTitleWithIcon"><Icon name="docker" size={17} />{t.advancedTitle}</h3>
                  <p className="panelText">{t.advancedText}</p>
                </div>
                <button className="btn" type="button" onClick={() => setShowAdvanced((prev) => !prev)}>
                  {showAdvanced ? t.hideAdvanced : t.showAdvanced}
                </button>
              </div>

              {showAdvanced ? (
                <div className="grid">
                  <div className="grid cols-2">
                    <label className="pill" style={{ justifyContent: 'space-between', cursor: 'pointer' }}>
                      <span>{t.buildMaven}</span>
                      <input type="checkbox" checked={build} onChange={(e) => setBuild(e.target.checked)} />
                    </label>
                    <label
                      className="pill dockerTooltipTarget"
                      style={{ justifyContent: 'space-between', cursor: dockerToggleDisabled ? 'not-allowed' : 'pointer', opacity: dockerToggleDisabled ? 0.65 : 1 }}
                      data-tooltip={dockerUnavailableTooltip}
                      tabIndex={dockerToggleDisabled ? 0 : undefined}
                    >
                      <span>{canDeployDocker ? t.deployDocker : t.deployDockerBeta}</span>
                      <input
                        type="checkbox"
                        checked={deployDocker}
                        disabled={dockerToggleDisabled}
                        onChange={(e) => setDeployDocker(e.target.checked)}
                      />
                    </label>
                  </div>

                  <div className={env.dockerRuntimeBlocked || h2RuntimeDisabled || yamlRuntimeDisabled || !canDeployDocker ? 'errorBox' : 'infoBox'}>
                    {dockerDisabledMessage}
                  </div>

                  {canDeployDocker && !h2RuntimeDisabled && !yamlRuntimeDisabled && !env.dockerRuntimeBlocked ? <div>
                    <div className="label">{t.dockerHostPort}</div>
                    <input className="input" type="number" value={hostPort} onChange={(e) => setHostPort(Number(e.target.value || 0))} />
                    <p className="panelText">{t.generatedApiPortHint}: <span style={{ fontFamily: 'var(--mono)' }}>{hostPort}</span></p>
                  </div> : null}
                </div>
              ) : null}
            </div>

            {err ? <div className="errorBox">{translateError(err)}</div> : null}
          </div>

          <div className="hr" />
          <div className="panelHeader" style={{ marginBottom: 8 }}>
            <div>
              <h3 className="panelTitle panelTitleWithIcon"><Icon name="doc" size={17} />{t.helpTitle}</h3>
              <p className="panelText">{t.helpText}</p>
            </div>
          </div>
          <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))' }}>
            <a className="btn" href="/app/docs#generate-api">{t.helpGenerate}</a>
            <a className="btn" href="/app/docs#preview-api">{t.helpPreview}</a>
            <a className="btn" href="/app/docs#common-errors">{t.helpErrors}</a>
          </div>

          <div className="hr" />
          <div className="panelHeader" style={{ marginBottom: 8 }}>
            <h3 className="panelTitle panelTitleWithIcon"><VisualIcon name="history" />{text.lang.code === 'fr' ? 'Parcours' : 'Workflow'}</h3>
          </div>
          <div className="grid" style={{ gap: 8 }}>
            {userActions.map((step) => (
              <div key={step} className="docStep">
                <p>{step}</p>
              </div>
            ))}
          </div>
        </div>

        <div className="figmaGeneratorPanel figmaGeneratorPanelRuntime">
          <div className="panelHeader">
            <div>
              <h3 className="panelTitle">{selectedGeneration?.name || t.job}</h3>
              <p className="panelText" style={{ fontFamily: 'var(--mono)' }}>
                {selectedApiId
                  ? selectedApiId
                  : (text.lang.code === 'fr' ? 'Aucune generation selectionnee' : 'No generation selected')}
              </p>
            </div>
            <span className={persistentStatusClass(selectedGeneration?.status)}>
              {persistentStatusLabel(selectedGeneration?.status)}
            </span>
          </div>

          {selectedGeneration ? (
            <>
              <div className="panelHeader">
                <div>
                  <h3 className="panelTitle">{text.lang.code === 'fr' ? 'Progression' : 'Progress'}</h3>
                  <p className="panelText">
                    {text.lang.code === 'fr' ? 'Suivi live de la generation persistante.' : 'Live tracking of the persisted generation.'}
                  </p>
                </div>
                <span className="pill">{selectedProgress}%</span>
              </div>

              <div className="progressBar">
                <div className="progressBarFill" style={{ width: `${selectedProgress}%` }} />
              </div>

              {selectedGeneration.errorMessage ? <div className="errorBox" style={{ marginTop: 12 }}>{translateError(selectedGeneration.errorMessage)}</div> : null}

              <div className="hr" />
              <div className="panelHeader">
                <div>
                  <h3 className="panelTitle panelTitleWithIcon"><Icon name="docker" size={17} />{text.lang.code === 'fr' ? 'Preview' : 'Preview'}</h3>
                  <p className="panelText">
                    {text.lang.code === 'fr' ? 'Runtime Docker isole pour tester l API generee.' : 'Isolated Docker runtime to test the generated API.'}
                  </p>
                </div>
                <span className={previewStatusClass(selectedPreview?.status)}>
                  {selectedPreview?.status ?? (text.lang.code === 'fr' ? 'STOPPED' : 'STOPPED')}
                </span>
              </div>
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginBottom: 12 }}>
                <span className="tooltipWrap" data-tooltip={env.dockerRuntimeBlocked ? dockerUnavailableTooltip : undefined} tabIndex={env.dockerRuntimeBlocked ? 0 : undefined}>
                  <button
                    className="btn"
                    disabled={env.dockerRuntimeBlocked || actionBusy || !selectedApiId || selectedGeneration.status !== 'DONE' || selectedPreview?.status === 'STARTING' || selectedPreview?.status === 'RUNNING'}
                    onClick={startPreview}
                  >
                    {text.lang.code === 'fr' ? 'Start Preview' : 'Start Preview'}
                  </button>
                </span>
                <span className="tooltipWrap" data-tooltip={env.dockerRuntimeBlocked ? dockerUnavailableTooltip : undefined} tabIndex={env.dockerRuntimeBlocked ? 0 : undefined}>
                  <button
                    className="btn"
                    disabled={env.dockerRuntimeBlocked || actionBusy || !selectedApiId || !selectedPreview?.status || selectedPreview.status === 'STOPPED' || selectedPreview.status === 'STOPPING'}
                    onClick={stopPreview}
                  >
                    {text.lang.code === 'fr' ? 'Stop Preview' : 'Stop Preview'}
                  </button>
                </span>
                <span className="tooltipWrap" data-tooltip={env.dockerRuntimeBlocked ? dockerUnavailableTooltip : undefined} tabIndex={env.dockerRuntimeBlocked ? 0 : undefined}>
                  <button
                    className="btn"
                    disabled={env.dockerRuntimeBlocked || actionBusy || !selectedApiId || selectedGeneration.status !== 'DONE'}
                    onClick={restartPreview}
                  >
                    {text.lang.code === 'fr' ? 'Restart Preview' : 'Restart Preview'}
                  </button>
                </span>
              </div>
              {env.dockerRuntimeBlocked ? <div className="errorBox" style={{ marginBottom: 12 }}>{t.dockerTemporarilyUnavailable}</div> : null}
              <div className="figmaGeneratorCallout">
                <div className="panelHeader" style={{ marginBottom: 10 }}>
                  <div>
                    <h3 className="panelTitle panelTitleWithIcon"><VisualIcon name="warning" />{text.lang.code === 'fr' ? 'Diagnostic preview' : 'Preview diagnostics'}</h3>
                    <p className="panelText">
                      {text.lang.code === 'fr'
                        ? 'Etat actuel, derniers timings et aide de resolution pour la preview selectionnee.'
                        : 'Current state, latest timings, and resolution help for the selected preview.'}
                    </p>
                  </div>
                </div>
                <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 10 }}>
                  <div className="card" style={{ padding: 14 }}>
                    <div className="label">{text.lang.code === 'fr' ? 'Statut' : 'Status'}</div>
                    <div className="value" style={{ fontSize: 20 }}>{selectedPreview?.status ?? 'STOPPED'}</div>
                  </div>
                  <div className="card" style={{ padding: 14 }}>
                    <div className="label">{text.lang.code === 'fr' ? 'Demarrage' : 'Startup'}</div>
                    <div className="value" style={{ fontSize: 20 }}>{formatDurationSeconds(previewStartupSeconds)}</div>
                    <div className="trend">{text.lang.code === 'fr' ? 'Creation -> RUNNING' : 'Created -> RUNNING'}</div>
                  </div>
                  <div className="card" style={{ padding: 14 }}>
                    <div className="label">{text.lang.code === 'fr' ? 'Runtime' : 'Runtime'}</div>
                    <div className="value" style={{ fontSize: 20 }}>{formatDurationSeconds(previewRuntimeSeconds)}</div>
                    <div className="trend">{text.lang.code === 'fr' ? 'RUNNING -> STOPPED' : 'RUNNING -> STOPPED'}</div>
                  </div>
                </div>
                <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 10, marginTop: 10 }}>
                  <div className="docStep">
                    <h4>{text.lang.code === 'fr' ? 'Creee le' : 'Created at'}</h4>
                    <p>{formatDateTime(selectedPreview?.createdAt)}</p>
                  </div>
                  <div className="docStep">
                    <h4>{text.lang.code === 'fr' ? 'Accessible le' : 'Reachable at'}</h4>
                    <p>{formatDateTime(selectedPreview?.startedAt)}</p>
                  </div>
                  <div className="docStep">
                    <h4>{text.lang.code === 'fr' ? 'Arretee le' : 'Stopped at'}</h4>
                    <p>{formatDateTime(selectedPreview?.stoppedAt)}</p>
                  </div>
                </div>
                {previewError ? <div className="errorBox" style={{ marginTop: 10 }}>{previewError}</div> : null}
                {previewErrorHint && previewErrorHint !== previewError ? <div className="infoBox" style={{ marginTop: 10 }}>{previewErrorHint}</div> : null}
                {previewDiagnostics ? (
                  <>
                    <div className="hr" />
                    <div className="panelHeader" style={{ marginBottom: 10 }}>
                      <div>
                        <h3 className="panelTitle">{text.lang.code === 'fr' ? 'Diagnostic hote' : 'Host diagnostics'}</h3>
                        <p className="panelText">
                          {text.lang.code === 'fr'
                            ? `Verification prealable du manager pour lancer la preview avec ${previewDiagnostics.containerRuntime ?? 'container runtime'}.`
                            : `Manager-side readiness checks to launch preview with ${previewDiagnostics.containerRuntime ?? 'the container runtime'}.`}
                        </p>
                      </div>
                      <span className={previewDiagnostics.hostReady ? 'badge good' : 'badge warn'}>
                        {previewDiagnostics.hostReady
                          ? (text.lang.code === 'fr' ? 'Pret' : 'Ready')
                          : (text.lang.code === 'fr' ? 'Action requise' : 'Action required')}
                      </span>
                    </div>
                    <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 10 }}>
                      <div className="docStep">
                        <h4>{text.lang.code === 'fr' ? 'Generation terminee' : 'Generation done'}</h4>
                        <p>{previewDiagnostics.generationDone ? 'OK' : 'KO'}</p>
                      </div>
                      <div className="docStep">
                        <h4>{text.lang.code === 'fr' ? 'Config preview' : 'Preview config'}</h4>
                        <p>{previewDiagnostics.previewConfigAvailable ? 'OK' : 'KO'}</p>
                      </div>
                      <div className="docStep">
                        <h4>{text.lang.code === 'fr' ? 'ZIP persiste' : 'Persisted ZIP'}</h4>
                        <p>{previewDiagnostics.zipAvailable ? 'OK' : 'KO'}</p>
                      </div>
                    </div>
                    <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 10, marginTop: 10 }}>
                      {previewDiagnostics.hostChecks.map((check) => (
                        <div className="docStep" key={check.key}>
                          <h4>{hostCheckLabel(check.key)}</h4>
                          <p>
                            <span className={check.ok ? 'badge good' : 'badge bad'}>
                              {check.ok ? 'OK' : 'KO'}
                            </span>
                          </p>
                          <p>{hostCheckDetails(check.key, check.ok, previewDiagnostics.containerRuntime)}</p>
                        </div>
                      ))}
                    </div>
                    {previewRecommendedAction ? (
                      <div className="infoBox" style={{ marginTop: 10 }}>
                        {previewRecommendedAction}
                      </div>
                    ) : null}
                  </>
                ) : null}
                <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 10 }}>
                  <a className="btn" href="/app/docs#preview-api">{text.lang.code === 'fr' ? 'Guide preview' : 'Preview guide'}</a>
                  <a className="btn" href="/app/docs#common-errors">{text.lang.code === 'fr' ? 'Corriger un blocage' : 'Fix a blocker'}</a>
                </div>
              </div>
              {previewIsRunning && previewActionBaseUrl ? (
                <div className="figmaGeneratorCallout">
                  <div className="panelHeader" style={{ marginBottom: 0 }}>
                    <div>
                      <h3 className="panelTitle">{text.lang.code === 'fr' ? 'URL preview' : 'Preview URL'}</h3>
                      <p className="panelText" style={{ fontFamily: 'var(--mono)' }}>{previewActionBaseUrl}</p>
                    </div>
                    <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                      <a className="btn" href={previewActionBaseUrl} target="_blank" rel="noreferrer">
                        {text.lang.code === 'fr' ? 'Ouvrir' : 'Open'}
                      </a>
                      {!previewUsesLoopback && previewProxyUrl ? (
                        <a className="btn" href={previewProxyUrl} target="_blank" rel="noreferrer">
                          Proxy
                        </a>
                      ) : null}
                      <a className="btn primary" href={joinUrl(previewActionBaseUrl, '/swagger-ui/index.html')} target="_blank" rel="noreferrer">
                        Swagger
                      </a>
                      {!previewUsesLoopback && previewProxyUrl ? (
                        <a className="btn primary" href={`${previewProxyUrl}/swagger-ui/index.html`} target="_blank" rel="noreferrer">
                          {text.lang.code === 'fr' ? 'Swagger Proxy' : 'Swagger Proxy'}
                        </a>
                      ) : null}
                    </div>
                  </div>
                </div>
              ) : null}

              {!previewIsRunning ? (
                <div className="infoBox" style={{ marginTop: 10 }}>
                  {env.dockerRuntimeBlocked
                    ? t.dockerTemporarilyUnavailable
                    : (text.lang.code === 'fr'
                        ? 'La preview n est pas en cours. Demarre la preview pour ouvrir Swagger ou verifier sa sante.'
                        : 'Preview is not running yet. Start it before opening Swagger or checking health.')}
                </div>
              ) : null}

              <div className="panelHeader">
                <div>
                  <h3 className="panelTitle panelTitleWithIcon"><VisualIcon name="logs" />{text.lang.code === 'fr' ? 'Logs preview' : 'Preview logs'}</h3>
                  <p className="panelText">
                    {text.lang.code === 'fr' ? 'Logs Docker live de la preview.' : 'Live Docker logs for the preview.'}
                  </p>
                </div>
                <span className="pill">{previewLogs.length} {t.lines}</span>
              </div>
              <pre className="logsPanel figmaTerminal">{previewLogs.length ? previewLogs.join('\n') : t.noLogs}</pre>

              {generatedApiIsRunning && apiActionBaseUrl ? (
                <div className="figmaGeneratorCallout">
                  <div className="panelHeader" style={{ marginBottom: 0 }}>
                    <div>
                      <h3 className="panelTitle">{t.apiAvailable}</h3>
                      <p className="panelText" style={{ fontFamily: 'var(--mono)' }}>{apiUrl}</p>
                    </div>
                    <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                      <a className="btn" href={joinUrl(apiActionBaseUrl, '/actuator/health')} target="_blank" rel="noreferrer">Health</a>
                      <a className="btn primary" href={joinUrl(apiActionBaseUrl, '/swagger-ui/index.html')} target="_blank" rel="noreferrer">Swagger</a>
                    </div>
                  </div>
                </div>
              ) : null}

              {rawApiUrl && generatedApiUsesLoopback ? (
                <div className="infoBox" style={{ marginTop: 10 }}>
                  {text.lang.code === 'fr'
                    ? 'Cette generation utilise une URL localhost interne. Ouvre la preview depuis la machine hote ou regenere l API avec un port local expose.'
                    : 'This generation uses an internal localhost URL. Open the preview from the host machine or regenerate the API with an exposed local port.'}
                </div>
              ) : null}

              {!generatedApiIsRunning ? (
                <div className="infoBox" style={{ marginTop: 10 }}>
                  {env.dockerRuntimeBlocked
                    ? t.dockerTemporarilyUnavailable
                    : (text.lang.code === 'fr'
                        ? 'Le build est termine, mais aucune API live n est exposee. Utilise la preview ou active le deploiement Docker pour obtenir Swagger et Health.'
                        : 'The build is complete, but no live API is exposed. Use preview or enable Docker deployment to get Swagger and Health.')}
                </div>
              ) : null}

              <div className="panelHeader">
                <div>
                  <h3 className="panelTitle panelTitleWithIcon"><VisualIcon name="logs" />{t.logs}</h3>
                  <p className="panelText">{text.lang.code === 'fr' ? 'Logs persistants pour le polling.' : 'Persistent logs for polling.'}</p>
                </div>
                <span className="pill">{logLineCount} {t.lines}</span>
              </div>
              <pre className="logsPanel figmaTerminal">{selectedLogs || t.noLogs}</pre>
            </>
          ) : (
            <div className="grid" style={{ gap: 12 }}>
              <div className="panelText">
                {text.lang.code === 'fr' ? 'Selectionne une generation depuis l historique.' : 'Select a generation from the history.'}
              </div>
              <div className="figmaGeneratorCallout">
                <div className="panelHeader" style={{ marginBottom: 0 }}>
                  <div>
                    <h3 className="panelTitle">{t.selectionHelpTitle}</h3>
                    <p className="panelText">{t.selectionHelpText}</p>
                  </div>
                  <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                    <a className="btn" href="/app/docs#get-started">{t.helpGetStarted}</a>
                    <a className="btn primary" href="/app/docs#generate-api">{t.helpGenerate}</a>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>

        <div className="figmaGeneratorPanel figmaGeneratorPanelHistory">
          <div className="panelHeader">
            <div>
              <h3 className="panelTitle panelTitleWithIcon"><VisualIcon name="history" />{text.lang.code === 'fr' ? 'Historique' : 'History'}</h3>
              <p className="panelText">{text.lang.code === 'fr' ? 'Generations persistantes liees a ton compte.' : 'Persistent generations linked to your account.'}</p>
            </div>
            <span className="pill">{apis.length}</span>
          </div>
          {apis.length ? (
            <div className="grid" style={{ gap: 10 }}>
              {apis.map((project) => (
                <div key={project.id} className="figmaHistoryCard" style={{ padding: 16 }}>
                  <div className="panelHeader" style={{ marginBottom: 8 }}>
                    <div>
                      <h3 className="panelTitle">{project.name}</h3>
                      <p className="panelText">{project.createdAt ? new Date(project.createdAt).toLocaleString() : '-'}</p>
                    </div>
                    <span className={persistentStatusClass(project.status)}>{persistentStatusLabel(project.status)}</span>
                  </div>

                  {project.errorMessage ? <div className="errorBox">{translateError(project.errorMessage)}</div> : null}

                  <div className="grid" style={{ gap: 8 }}>
                    <p className="panelText" style={{ margin: 0 }}>
                      {text.lang.code === 'fr' ? 'Type BDD' : 'DB type'}: {project.dbType || '-'}
                    </p>
                    <p className="panelText" style={{ margin: 0 }}>
                      {text.lang.code === 'fr' ? 'Progression' : 'Progress'}: {project.progress ?? 0}%
                    </p>
                    <p className="panelText" style={{ margin: 0 }}>
                      {text.lang.code === 'fr' ? 'Terminee' : 'Finished'}: {project.finishedAt ? new Date(project.finishedAt).toLocaleString() : '-'}
                    </p>
                    {project.jobId ? (
                      <p className="panelText" style={{ margin: 0, fontFamily: 'var(--mono)' }}>
                        Job: {project.jobId}
                      </p>
                    ) : null}
                  </div>

                  <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 12 }}>
                    <button
                      className="btn"
                      disabled={actionBusy}
                      onClick={() => selectProject(project)}
                    >
                      {text.lang.code === 'fr' ? 'Suivre' : 'Track'}
                    </button>
                    {project.status === 'DEPLOYED' && project.apiBaseUrl ? (
                      <a className="btn" href={joinUrl(project.apiBaseUrl, '/swagger-ui/index.html')} target="_blank" rel="noreferrer">
                        Swagger
                      </a>
                    ) : null}
                    {project.downloadUrl ? (
                      <button
                        className="btn"
                        disabled={actionBusy || !!project.zipDownloadedAt || project.status !== 'DONE'}
                        onClick={() => downloadPersistedApi(project)}
                        title={project.zipDownloadedAt
                          ? (text.lang.code === 'fr' ? 'ZIP deja telecharge' : 'ZIP already downloaded')
                          : t.zip}
                      >
                        {t.zip}
                      </button>
                    ) : null}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="grid" style={{ gap: 12 }}>
              <div className="panelText">{text.lang.code === 'fr' ? 'Aucune generation historique.' : 'No persisted generation yet.'}</div>
              <div className="figmaGeneratorCallout">
                <div className="panelHeader" style={{ marginBottom: 0 }}>
                  <div>
                    <h3 className="panelTitle">{t.emptyHistoryTitle}</h3>
                    <p className="panelText">{t.emptyHistoryText}</p>
                  </div>
                  <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                    <a className="btn" href="/app/docs#get-started">{t.helpGetStarted}</a>
                    <a className="btn" href="/app/docs#generate-api">{t.helpGenerate}</a>
                    <a className="btn" href="/app/docs#common-errors">{t.helpErrors}</a>
                  </div>
                </div>
              </div>
            </div>
          )}

          {zipJobs.length ? (
            <>
              <div className="hr" />
              <div className="panelHeader">
                <div>
                  <h3 className="panelTitle panelTitleWithIcon"><VisualIcon name="zip" />ZIP</h3>
                  <p className="panelText">Telechargement par generation</p>
                </div>
                <span className="pill">{zipJobs.length}</span>
              </div>
              <div className="grid" style={{ gap: 8 }}>
                {zipJobs.map((item) => (
                  <div key={item.jobId} className="panelHeader" style={{ marginBottom: 0 }}>
                    <p className="panelText" style={{ fontFamily: 'var(--mono)', margin: 0 }}>{item.jobId}</p>
                    <button
                      className="btn"
                      disabled={actionBusy || !!item.zipDownloadedAt || !isZipReady(item.jobId)}
                      onClick={() => downloadZip(item.jobId)}
                      title={item.zipDownloadedAt ? 'ZIP deja telecharge' : t.zip}
                    >
                      {t.zip}
                    </button>
                  </div>
                ))}
              </div>
            </>
          ) : null}
        </div>
      </div>
    </Shell>
  )
}

