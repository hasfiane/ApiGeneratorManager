import type { ReactNode } from 'react'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import Generators from './Generators'
import { en } from '../i18n/en'
import { vi } from 'vitest'

const mockAuthState = {
  quotas: { canDeployDocker: true },
}

vi.mock('../components/Shell', () => ({
  Shell: ({ title, subtitle, actions, children }: { title: string; subtitle?: string; actions?: ReactNode; children: ReactNode }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {actions}
      {children}
    </div>
  ),
}))

vi.mock('../i18n/LanguageProvider', () => ({
  useLanguage: () => ({ text: en, locale: 'en' }),
}))

vi.mock('../state/auth', () => ({
  useAuth: () => ({
    quotas: mockAuthState.quotas,
  }),
}))

vi.mock('../services/env', () => ({
  env: {
    apiBaseUrl: 'http://localhost:8080',
    demoAppName: 'DemoApp',
    demoBasePackage: 'com.demo.api',
    demoDatabaseType: 'postgres',
    demoJdbcUrl: 'jdbc:postgresql://localhost/demo',
    demoJdbcUsername: 'demo',
    demoJdbcPassword: 'secret',
    demoSchema: 'public',
    demoDeployDocker: false,
    demoHostPort: 18080,
    dockerRuntimeBlocked: true,
  },
}))

vi.mock('../services/api', () => ({
  api: {
    getMyApis: vi.fn(),
    getGeneratedApi: vi.fn(),
    getGeneratedApiPreview: vi.fn(),
    getGeneratedApiPreviewLogs: vi.fn(),
    getGeneratedApiPreviewDiagnostics: vi.fn(),
    getGenerationStatus: vi.fn(),
    startGeneration: vi.fn(),
    startSchemaFileGeneration: vi.fn(),
    stopGeneration: vi.fn(),
    downloadGenerationZip: vi.fn(),
    downloadFile: vi.fn(),
    startGeneratedApiPreview: vi.fn(),
    stopGeneratedApiPreview: vi.fn(),
    restartGeneratedApiPreview: vi.fn(),
  },
}))

describe('Generators', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('disables deploy docker while dockerization is temporarily blocked', async () => {
    mockAuthState.quotas = { canDeployDocker: false }
    const { api } = await import('../services/api')
    vi.mocked(api.getMyApis).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApi).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreview).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreviewLogs).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApiPreviewDiagnostics).mockResolvedValue(null as never)
    vi.mocked(api.getGenerationStatus).mockResolvedValue({
      jobId: 'job-1',
      status: 'PENDING',
      createdAt: '2026-04-23T10:00:00Z',
    } as never)

    const { container } = render(<Generators />)
    fireEvent.click(screen.getByRole('button', { name: 'Show' }))

    await waitFor(() => {
      expect(screen.getByText('Deploy Docker (beta)')).toBeInTheDocument()
    })

    const toggles = screen.getAllByRole('checkbox')
    expect(toggles[1]).toBeDisabled()
    expect(screen.getByText(en.generators.dockerTemporarilyUnavailable)).toBeInTheDocument()
    expect(container.querySelectorAll(`[data-tooltip="${en.generators.dockerTemporarilyUnavailableTooltip}"]`).length).toBeGreaterThan(0)
    mockAuthState.quotas = { canDeployDocker: true }
  })

  it('blocks preview docker controls while dockerization is temporarily disabled', async () => {
    mockAuthState.quotas = { canDeployDocker: true }
    const { api } = await import('../services/api')
    vi.mocked(api.getMyApis).mockResolvedValue([
      { id: 'api-1', name: 'Orders API', status: 'DONE', progress: 100, createdAt: '2026-04-23T10:00:00Z' },
    ] as never)
    vi.mocked(api.getGeneratedApi).mockResolvedValue({
      id: 'api-1',
      name: 'Orders API',
      status: 'DONE',
      progress: 100,
      logs: '',
    } as never)
    vi.mocked(api.getGeneratedApiPreview).mockResolvedValue({
      id: 'preview-1',
      status: 'RUNNING',
      baseUrl: 'http://127.0.0.1:18080',
      proxyUrl: '/api/account/apis/api-1/preview/proxy',
      createdAt: '2026-04-23T10:00:00Z',
      startedAt: '2026-04-23T10:00:10Z',
    } as never)
    vi.mocked(api.getGeneratedApiPreviewLogs).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApiPreviewDiagnostics).mockResolvedValue(null as never)
    vi.mocked(api.getGenerationStatus).mockResolvedValue({
      jobId: 'job-1',
      status: 'DONE',
      createdAt: '2026-04-23T10:00:00Z',
    } as never)

    const { container } = render(<Generators />)

    await waitFor(() => {
      expect(screen.getByText('Preview diagnostics')).toBeInTheDocument()
    })

    expect(screen.getByRole('button', { name: 'Start Preview' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Stop Preview' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Restart Preview' })).toBeDisabled()
    expect(container.querySelectorAll(`[data-tooltip="${en.generators.dockerTemporarilyUnavailableTooltip}"]`).length).toBeGreaterThanOrEqual(3)
  })

  it('forces deployDocker off in generation requests while dockerization is blocked', async () => {
    mockAuthState.quotas = { canDeployDocker: true }
    const { api } = await import('../services/api')
    vi.mocked(api.getMyApis).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApi).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreview).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreviewLogs).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApiPreviewDiagnostics).mockResolvedValue(null as never)
    vi.mocked(api.startGeneration).mockResolvedValue({ jobId: 'job-1' } as never)
    vi.mocked(api.getGenerationStatus).mockResolvedValue({
      jobId: 'job-1',
      status: 'PENDING',
      createdAt: '2026-04-23T10:00:00Z',
    } as never)

    render(<Generators />)
    fireEvent.click(screen.getByRole('button', { name: 'Generate' }))

    await waitFor(() => {
      expect(api.startGeneration).toHaveBeenCalled()
    })

    expect(vi.mocked(api.startGeneration).mock.calls[0][0]).toMatchObject({ deployDocker: false })
  })

  it('keeps JDBC mode visible and shows YAML upload mode', async () => {
    const { api } = await import('../services/api')
    vi.mocked(api.getMyApis).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApi).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreview).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreviewLogs).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApiPreviewDiagnostics).mockResolvedValue(null as never)

    render(<Generators />)

    expect(screen.getByRole('tablist', { name: 'Generation mode' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Connect database' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Upload YAML/YML' })).toBeInTheDocument()
    expect(screen.getByText('JDBC URL')).toBeInTheDocument()
  })

  it('accepts yaml and yml file selection', async () => {
    const { api } = await import('../services/api')
    vi.mocked(api.getMyApis).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApi).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreview).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreviewLogs).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApiPreviewDiagnostics).mockResolvedValue(null as never)

    render(<Generators />)
    fireEvent.click(screen.getByRole('tab', { name: 'Upload YAML/YML' }))
    expect(screen.getByText('Docker preview is not available for YAML schema generation in this first version.')).toBeInTheDocument()
    const input = screen.getByLabelText('YAML schema file')

    fireEvent.change(input, { target: { files: [new File(['tables: {}'], 'schema.yaml', { type: 'application/x-yaml' })] } })
    expect(screen.getByText('Selected file: schema.yaml')).toBeInTheDocument()

    fireEvent.change(input, { target: { files: [new File(['tables: {}'], 'schema.yml', { type: 'application/x-yaml' })] } })
    expect(screen.getByText('Selected file: schema.yml')).toBeInTheDocument()
  })

  it('shows an error for invalid YAML file extensions', async () => {
    const { api } = await import('../services/api')
    vi.mocked(api.getMyApis).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApi).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreview).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreviewLogs).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApiPreviewDiagnostics).mockResolvedValue(null as never)

    render(<Generators />)
    fireEvent.click(screen.getByRole('tab', { name: 'Upload YAML/YML' }))
    fireEvent.change(screen.getByLabelText('YAML schema file'), {
      target: { files: [new File(['x'], 'schema.txt', { type: 'text/plain' })] },
    })

    expect(screen.getByText('Please select a YAML or YML file.')).toBeInTheDocument()
  })

  it('submits YAML schema files to the schema-file endpoint client', async () => {
    const { api } = await import('../services/api')
    vi.mocked(api.getMyApis).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApi).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreview).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreviewLogs).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApiPreviewDiagnostics).mockResolvedValue(null as never)
    vi.mocked(api.startSchemaFileGeneration).mockResolvedValue({ jobId: 'job-1', generatedApiId: 'api-1' } as never)
    vi.mocked(api.getGenerationStatus).mockResolvedValue({
      jobId: 'job-1',
      status: 'SUCCEEDED',
      createdAt: '2026-04-23T10:00:00Z',
    } as never)

    render(<Generators />)
    fireEvent.click(screen.getByRole('tab', { name: 'Upload YAML/YML' }))
    const file = new File(['tables:\n  customers:\n    columns:\n      id:\n        type: uuid\n'], 'schema.yaml', { type: 'application/x-yaml' })
    fireEvent.change(screen.getByLabelText('YAML schema file'), { target: { files: [file] } })
    fireEvent.click(screen.getByRole('button', { name: 'Generate' }))

    await waitFor(() => {
      expect(api.startSchemaFileGeneration).toHaveBeenCalledTimes(1)
    })
    expect(vi.mocked(api.startSchemaFileGeneration).mock.calls[0][0]).toMatchObject({ file, deployDocker: false })
    expect(api.startGeneration).not.toHaveBeenCalled()
  })

  it('prevents double submit in YAML mode', async () => {
    const { api } = await import('../services/api')
    vi.mocked(api.getMyApis).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApi).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreview).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreviewLogs).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApiPreviewDiagnostics).mockResolvedValue(null as never)
    vi.mocked(api.startSchemaFileGeneration).mockResolvedValue({ jobId: 'job-1' } as never)
    vi.mocked(api.getGenerationStatus).mockResolvedValue({
      jobId: 'job-1',
      status: 'PENDING',
      createdAt: '2026-04-23T10:00:00Z',
    } as never)

    render(<Generators />)
    fireEvent.click(screen.getByRole('tab', { name: 'Upload YAML/YML' }))
    fireEvent.change(screen.getByLabelText('YAML schema file'), {
      target: { files: [new File(['tables: {}'], 'schema.yml', { type: 'application/x-yaml' })] },
    })
    const button = screen.getByRole('button', { name: 'Generate' })
    fireEvent.click(button)
    fireEvent.click(button)

    await waitFor(() => {
      expect(api.startSchemaFileGeneration).toHaveBeenCalledTimes(1)
    })
  })

  it('sends the current Docker host port when starting a generation', async () => {
    mockAuthState.quotas = { canDeployDocker: true }
    const { api } = await import('../services/api')
    const { env } = await import('../services/env')
    ;(env as { dockerRuntimeBlocked: boolean }).dockerRuntimeBlocked = false
    vi.mocked(api.getMyApis).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApi).mockResolvedValue({
      id: 'api-1',
      name: 'Orders API',
      status: 'DONE',
      progress: 100,
      apiBaseUrl: 'http://localhost:18080',
      proxyUrl: '/api/account/apis/api-1/proxy',
      logs: '',
      createdAt: '2026-04-23T10:00:00Z',
    } as never)
    vi.mocked(api.getGeneratedApiPreview).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreviewLogs).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApiPreviewDiagnostics).mockResolvedValue(null as never)
    vi.mocked(api.startGeneration).mockResolvedValue({ jobId: 'job-1' } as never)
    vi.mocked(api.getGenerationStatus).mockResolvedValue({
      jobId: 'job-1',
      status: 'PENDING',
      createdAt: '2026-04-23T10:00:00Z',
    } as never)

    render(<Generators />)
    const showButtons = screen.getAllByRole('button', { name: 'Show' })
    fireEvent.click(showButtons[showButtons.length - 1])
    fireEvent.input(screen.getByDisplayValue('18080'), { target: { value: '18082' } })
    await waitFor(() => {
      expect(screen.getByDisplayValue('18082')).toBeInTheDocument()
    })
    fireEvent.click(screen.getByRole('button', { name: 'Generate' }))

    await waitFor(() => {
      expect(api.startGeneration).toHaveBeenCalled()
    })

    const calls = vi.mocked(api.startGeneration).mock.calls
    expect(calls[calls.length - 1][0]).toMatchObject({ hostPort: 18082 })
    ;(env as { dockerRuntimeBlocked: boolean }).dockerRuntimeBlocked = true
  })

  it('re-enables generation actions when the selected persisted generation fails', async () => {
    mockAuthState.quotas = { canDeployDocker: true }
    const { api } = await import('../services/api')
    vi.mocked(api.getMyApis).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApi).mockResolvedValue({
      id: 'api-1',
      name: 'Orders API',
      status: 'FAILED',
      progress: 35,
      jobId: 'job-1',
      logs: 'ERROR: No tables were found in the configured database/schema. Prepare your database schema before generating the API.',
      errorMessage: 'No tables were found in the configured database/schema. Prepare your database schema before generating the API.',
      finishedAt: '2026-04-23T10:00:02Z',
    } as never)
    vi.mocked(api.getGeneratedApiPreview).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreviewLogs).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApiPreviewDiagnostics).mockResolvedValue(null as never)
    vi.mocked(api.startGeneration).mockResolvedValue({ jobId: 'job-1', generatedApiId: 'api-1' } as never)
    vi.mocked(api.getGenerationStatus).mockResolvedValue({
      jobId: 'job-1',
      status: 'PENDING',
      createdAt: '2026-04-23T10:00:00Z',
    } as never)

    render(<Generators />)
    fireEvent.click(screen.getByRole('button', { name: 'Generate' }))

    await waitFor(() => {
      expect(api.startGeneration).toHaveBeenCalled()
    })
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Generate' })).not.toBeDisabled()
    })
    expect(screen.getByText('No tables were found in this schema. Check that the schema exists, the JDBC user can read it, and the database contains tables.')).toBeInTheDocument()
  })

  it('shows localized host diagnostics and recommended action', async () => {
    mockAuthState.quotas = { canDeployDocker: true }
    const { api } = await import('../services/api')
    vi.mocked(api.getMyApis).mockResolvedValue([
      { id: 'api-1', name: 'Orders API', status: 'DONE', progress: 100, createdAt: '2026-04-23T10:00:00Z' },
    ] as never)
    vi.mocked(api.getGeneratedApi).mockResolvedValue({
      id: 'api-1',
      name: 'Orders API',
      status: 'DONE',
      progress: 100,
      logs: '',
    } as never)
    vi.mocked(api.getGeneratedApiPreview).mockResolvedValue({
      id: 'preview-1',
      status: 'FAILED',
      errorCode: 'HOST_RUNTIME_UNREACHABLE',
      errorMessage: 'Container runtime is not reachable: docker',
      errorHint: 'Start Docker or Podman on the manager host before launching preview.',
      createdAt: '2026-04-23T10:00:00Z',
      stoppedAt: '2026-04-23T10:01:00Z',
    } as never)
    vi.mocked(api.getGeneratedApiPreviewLogs).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApiPreviewDiagnostics).mockResolvedValue({
      generationStatus: 'DONE',
      previewStatus: 'FAILED',
      generationDone: true,
      previewConfigAvailable: true,
      zipAvailable: true,
      hostReady: false,
      containerRuntime: 'docker',
      hostChecks: [
        { key: 'containerRuntimeBinary', ok: true, details: 'binary ok' },
        { key: 'containerRuntimeReachable', ok: false, details: 'runtime unreachable' },
        { key: 'mavenCommandAvailable', ok: true, details: 'maven ok' },
      ],
      recommendedAction: {
        code: 'FIX_HOST_DIAGNOSTICS',
        message: 'Fix the failing host checks before launching preview.',
      },
    } as never)
    vi.mocked(api.getGenerationStatus).mockResolvedValue({
      jobId: 'job-1',
      status: 'DONE',
      createdAt: '2026-04-23T10:00:00Z',
    } as never)

    render(<Generators />)

    await waitFor(() => {
      expect(screen.getByText('Host diagnostics')).toBeInTheDocument()
    })

    expect(screen.getByText('The docker runtime is not reachable yet.')).toBeInTheDocument()
    expect(screen.getByText('Fix the failing host checks before launching preview.')).toBeInTheDocument()
    expect(screen.getByText('The container runtime is installed but unreachable. Start Docker or Podman before launching preview.')).toBeInTheDocument()
  })

  it('translates persisted generation errors in history', async () => {
    const { api } = await import('../services/api')
    vi.mocked(api.getMyApis).mockResolvedValue([
      {
        id: 'api-1',
        name: 'Orders API',
        status: 'FAILED',
        progress: 100,
        createdAt: '2026-04-23T10:00:00Z',
        errorMessage: 'Preview proxy request failed',
      },
    ] as never)
    vi.mocked(api.getGeneratedApi).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreview).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreviewLogs).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApiPreviewDiagnostics).mockResolvedValue(null as never)
    vi.mocked(api.getGenerationStatus).mockResolvedValue({
      jobId: 'job-1',
      status: 'FAILED',
      createdAt: '2026-04-23T10:00:00Z',
    } as never)

    render(<Generators />)

    await waitFor(() => {
      expect(screen.getByText('The preview request failed. Verify that preview is still running and healthy, then try again.')).toBeInTheDocument()
    })
  })

  it('prefers the preview proxy when the runtime is bound to loopback only', async () => {
    const { api } = await import('../services/api')
    vi.mocked(api.getMyApis).mockResolvedValue([
      { id: 'api-1', name: 'Orders API', status: 'DONE', progress: 100, createdAt: '2026-04-23T10:00:00Z' },
    ] as never)
    vi.mocked(api.getGeneratedApi).mockResolvedValue({
      id: 'api-1',
      name: 'Orders API',
      status: 'DONE',
      progress: 100,
      logs: '',
    } as never)
    vi.mocked(api.getGeneratedApiPreview).mockResolvedValue({
      id: 'preview-1',
      status: 'RUNNING',
      baseUrl: 'http://127.0.0.1:18080',
      proxyUrl: '/api/account/apis/api-1/preview/proxy',
      createdAt: '2026-04-23T10:00:00Z',
      startedAt: '2026-04-23T10:00:10Z',
    } as never)
    vi.mocked(api.getGeneratedApiPreviewLogs).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApiPreviewDiagnostics).mockResolvedValue(null as never)
    vi.mocked(api.getGenerationStatus).mockResolvedValue({
      jobId: 'job-1',
      status: 'DONE',
      createdAt: '2026-04-23T10:00:00Z',
    } as never)

    render(<Generators />)

    await waitFor(() => {
      expect(screen.getByText('/api/account/apis/api-1/preview/proxy')).toBeInTheDocument()
    })

    expect(screen.queryByText('http://127.0.0.1:18080')).not.toBeInTheDocument()
  })

  it('opens generated API swagger on the real runtime URL instead of the manager proxy', async () => {
    const { api } = await import('../services/api')
    vi.mocked(api.getMyApis).mockResolvedValue([
      {
        id: 'api-1',
        name: 'Orders API',
        status: 'DEPLOYED',
        progress: 100,
        apiBaseUrl: '/generated/apis/job-1',
        proxyUrl: '/api/account/apis/api-1/proxy',
        createdAt: '2026-04-23T10:00:00Z',
      },
    ] as never)
    vi.mocked(api.getGeneratedApi).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreview).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreviewLogs).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApiPreviewDiagnostics).mockResolvedValue(null as never)
    vi.mocked(api.getGenerationStatus).mockResolvedValue({
      jobId: 'job-1',
      status: 'DEPLOYED',
      createdAt: '2026-04-23T10:00:00Z',
    } as never)

    render(<Generators />)

    await waitFor(() => {
      expect(screen.getByRole('link', { name: 'Swagger' })).toBeInTheDocument()
    })

    const swaggerLink = screen.getByRole('link', { name: 'Swagger' })
    expect(swaggerLink).toHaveAttribute('href', 'http://localhost:8080/generated/apis/job-1/swagger-ui/index.html')
  })

  it('opens generated API swagger on a loopback Docker deployment in local development', async () => {
    const { api } = await import('../services/api')
    vi.mocked(api.getMyApis).mockResolvedValue([
      {
        id: 'api-1',
        name: 'Orders API',
        status: 'DONE',
        progress: 100,
        apiBaseUrl: 'http://localhost:18080',
        proxyUrl: '/api/account/apis/api-1/proxy',
        createdAt: '2026-04-23T10:00:00Z',
      },
    ] as never)
    vi.mocked(api.getGeneratedApi).mockResolvedValue({
      id: 'api-1',
      name: 'Orders API',
      status: 'DONE',
      progress: 100,
      apiBaseUrl: 'http://localhost:18080',
      proxyUrl: '/api/account/apis/api-1/proxy',
      logs: '',
      createdAt: '2026-04-23T10:00:00Z',
    } as never)
    vi.mocked(api.getGeneratedApiPreview).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreviewLogs).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApiPreviewDiagnostics).mockResolvedValue(null as never)
    vi.mocked(api.getGenerationStatus).mockResolvedValue({
      jobId: 'job-1',
      status: 'DEPLOYED',
      createdAt: '2026-04-23T10:00:00Z',
    } as never)

    render(<Generators />)

    await waitFor(() => expect(screen.getByRole('link', { name: 'Swagger' })).toBeInTheDocument())
    expect(screen.getByRole('link', { name: 'Swagger' })).toHaveAttribute('href', 'http://localhost:18080/swagger-ui/index.html')
  })

  it('does not expose generated API swagger when only the manager proxy URL is available', async () => {
    const { api } = await import('../services/api')
    vi.mocked(api.getMyApis).mockResolvedValue([
      {
        id: 'api-1',
        name: 'Orders API',
        status: 'DEPLOYED',
        progress: 100,
        apiBaseUrl: null,
        proxyUrl: '/api/account/apis/api-1/proxy',
        createdAt: '2026-04-23T10:00:00Z',
      },
    ] as never)
    vi.mocked(api.getGeneratedApi).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreview).mockResolvedValue(null as never)
    vi.mocked(api.getGeneratedApiPreviewLogs).mockResolvedValue([] as never)
    vi.mocked(api.getGeneratedApiPreviewDiagnostics).mockResolvedValue(null as never)
    vi.mocked(api.getGenerationStatus).mockResolvedValue({
      jobId: 'job-1',
      status: 'DEPLOYED',
      createdAt: '2026-04-23T10:00:00Z',
    } as never)

    render(<Generators />)

    await waitFor(() => {
      expect(screen.getByText('Orders API')).toBeInTheDocument()
    })

    expect(screen.queryByRole('link', { name: 'Swagger' })).not.toBeInTheDocument()
  })
})
