import { env } from "./env";

const NETWORK_ERROR_MESSAGE = "Unable to contact the server. Check the connection.";
const CSRF_ERROR_MESSAGE = "Unable to initialize CSRF protection.";
const HTML_ERROR_PATTERN = /<\s*html[\s>]/i;

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly code?: string,
    public readonly fields?: Record<string, string>
  ) {
    super(message);
    this.name = "ApiError";
    Object.setPrototypeOf(this, ApiError.prototype);
  }

  get isUnauthorized(): boolean {
    return this.status === 401;
  }

  get isForbidden(): boolean {
    return this.status === 403;
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }
}

type ApiErrorPayload = {
  message?: string;
  error?: string;
  code?: string;
  fields?: Record<string, string>;
  details?: {
    fields?: Record<string, string>;
  };
};

function parseApiErrorPayload(text: string, fallbackMessage: string): {
  message: string;
  code?: string;
  fields?: Record<string, string>;
} {
  if (HTML_ERROR_PATTERN.test(text)) {
    return { message: fallbackMessage };
  }

  try {
    const json = JSON.parse(text) as {
      message?: string;
      code?: string;
      fields?: Record<string, string>;
      details?: { fields?: Record<string, string> };
      error?: ApiErrorPayload | string;
    };

    if (json && typeof json.error === "object" && json.error !== null) {
      const nested = json.error as ApiErrorPayload;
      return {
        message: nested.message ?? fallbackMessage,
        code: nested.code,
        fields: nested.fields ?? nested.details?.fields ?? json.fields,
      };
    }

    return {
      message: (json as ApiErrorPayload).message ?? (typeof json.error === "string" ? json.error : fallbackMessage),
      code: (json as ApiErrorPayload).code,
      fields: (json as ApiErrorPayload).fields ?? (json as ApiErrorPayload).details?.fields,
    };
  } catch {
    return { message: fallbackMessage };
  }
}

export type ApiProject = {
  id: string;
  name: string;
  status?: string | null;
  progress?: number | null;
  jobId?: string | null;
  dbType?: string | null;
  downloadUrl?: string | null;
  apiBaseUrl?: string | null;
  proxyUrl?: string | null;
  errorMessage?: string | null;
  dockerImage?: string | null;
  createdAt?: string | null;
  finishedAt?: string | null;
  zipDownloadedAt?: string | null;
};

export type GeneratedApiDetail = {
  id: string;
  name: string;
  status?: string | null;
  progress?: number | null;
  jobId?: string | null;
  dbType?: string | null;
  downloadUrl?: string | null;
  apiBaseUrl?: string | null;
  proxyUrl?: string | null;
  errorMessage?: string | null;
  logs?: string | null;
  createdAt?: string | null;
  finishedAt?: string | null;
  zipDownloadedAt?: string | null;
};

export type ApiPreview = {
  id?: string | null;
  status?: string | null;
  hostPort?: number | null;
  baseUrl?: string | null;
  proxyUrl?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  errorHint?: string | null;
  createdAt?: string | null;
  startedAt?: string | null;
  stoppedAt?: string | null;
};

export type PreviewHostCheck = {
  key: string;
  ok: boolean;
  details: string;
};

export type PreviewRecommendation = {
  code?: string | null;
  message?: string | null;
};

export type PreviewDiagnostics = {
  generationStatus?: string | null;
  previewStatus?: string | null;
  generationDone: boolean;
  previewConfigAvailable: boolean;
  zipAvailable: boolean;
  hostReady: boolean;
  containerRuntime?: string | null;
  hostChecks: PreviewHostCheck[];
  recommendedAction?: PreviewRecommendation | null;
};

export type FailedPreview = {
  generatedApiId: string;
  generatedApiName: string;
  previewStatus?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  errorHint?: string | null;
  stoppedAt?: string | null;
};

export type AccountSummary = {
  totalGeneratedApis: number;
  completedGenerations: number;
  failedGenerations: number;
  averageGenerationSeconds: number;
  activePreviews: number;
  runningPreviews: number;
  previewsStarted: number;
  failedPreviews: number;
  averagePreviewStartupSeconds: number;
  averagePreviewRuntimeSeconds: number;
};

export type SecurityDeployment = {
  id: string;
  name: string;
  status: string;
};


export type AdminDashboardSummary = {
  totalUsers: number;
  totalGeneratedApis: number;
  activeJobs: number;
  generationAttempts: number;
  successfulGenerations: number;
  failedGenerations: number;
  totalPreviews: number;
  activePreviews: number;
  failedPreviews: number;
  averageGenerationSeconds: number;
  successRate: number;
  failureRate: number;
  attemptsLast24h: number;
  apisCreatedLast24h: number;
  previewsCreatedLast24h: number;
  generatedAt: string;
};

export type AdminGeneratedApi = {
  id: string;
  name: string;
  ownerEmail: string;
  status?: string | null;
  progress?: number | null;
  jobId?: string | null;
  jobStatus?: string | null;
  dbType?: string | null;
  errorMessage?: string | null;
  createdAt?: string | null;
  finishedAt?: string | null;
};

export type AdminJobAttempt = {
  jobId: string;
  generatedApiId?: string | null;
  generatedApiName?: string | null;
  ownerEmail?: string | null;
  status?: string | null;
  buildRequested: boolean;
  deployDockerRequested: boolean;
  preferredPort?: number | null;
  hostPort?: number | null;
  errorMessage?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
};

export type AdminError = {
  source: string;
  code?: string | null;
  message?: string | null;
  hint?: string | null;
  generatedApiId?: string | null;
  generatedApiName?: string | null;
  ownerEmail?: string | null;
  occurredAt?: string | null;
};


export type AdminApiCall = {
  timestamp?: string | null;
  traceId?: string | null;
  method: string;
  path: string;
  status: number;
  durationMs: number;
  principal?: string | null;
  clientIp?: string | null;
};

export type AdminDatabaseTool = {
  name: string;
  enabled: boolean;
  url: string;
  warning?: string | null;
};

export type AdminDashboard = {
  summary: AdminDashboardSummary;
  generationStatuses: Record<string, number>;
  jobStatuses: Record<string, number>;
  previewStatuses: Record<string, number>;
  recentApis: AdminGeneratedApi[];
  jobAttempts: AdminJobAttempt[];
  errors: AdminError[];
  recentApiCalls: AdminApiCall[];
  databaseTool?: AdminDatabaseTool | null;
};

export type AdminSecretRotation = {
  email: string;
  temporaryPassword: string;
  rotatedAt: string;
  message: string;
};

export type Quotas = {
  monthlyGenerationsUsed: number;
  monthlyGenerationsLimit: number;
  monthlyDockerDeploymentsUsed: number;
  monthlyDockerDeploymentsLimit: number;
  monthlyZipDownloadsUsed: number;
  monthlyZipDownloadsLimit: number;
  canBuild: boolean;
  canDeployDocker: boolean;
  canDownloadZip: boolean;
};

function buildApiUrl(path: string): string {
  const base = (env.apiBaseUrl || "").replace(/\/+$/, "");
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  const effectivePath = base.endsWith("/api") && normalizedPath.startsWith("/api/")
    ? normalizedPath.slice(4)
    : normalizedPath;
  return `${base}${effectivePath}`;
}

function pathSegment(value: string): string {
  return encodeURIComponent(value);
}

function queryParam(value: string | number): string {
  return encodeURIComponent(String(value));
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  return requestWithCsrfRetry(path, init, true);
}

async function requestWithCsrfRetry<T>(path: string, init: RequestInit | undefined, allowCsrfRetry: boolean): Promise<T> {
  const headers = new Headers(init?.headers ?? {});
  headers.set("Accept", "application/json");
  const stateChanging = isStateChanging(init?.method);

  if (stateChanging) {
    const csrf = await getCsrfToken();
    headers.set(csrf.headerName, csrf.token);
  }

  let res: Response;
  try {
    res = await fetch(buildApiUrl(path), { ...init, headers, credentials: "include" });
  } catch {
    throw new ApiError(0, NETWORK_ERROR_MESSAGE);
  }

  if (res.status === 403 && stateChanging && allowCsrfRetry) {
    csrfToken = null;
    return requestWithCsrfRetry(path, init, false);
  }

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    let message = responseFallbackMessage(res.status, res.statusText);
    let code: string | undefined;
    let fields: Record<string, string> | undefined;
    const parsed = parseApiErrorPayload(text, message);
    message = parsed.message;
    code = parsed.code;
    fields = parsed.fields;
    throw new ApiError(res.status, message, code, fields);
  }

  const ct = res.headers.get("content-type") ?? "";
  if (ct.includes("application/json")) return (await res.json()) as T;
  return (await res.text()) as unknown as T;
}

let csrfToken: { headerName: string; token: string } | null = null;

function isStateChanging(method?: string): boolean {
  const normalized = (method ?? "GET").toUpperCase();
  return normalized === "POST" || normalized === "PUT" || normalized === "PATCH" || normalized === "DELETE";
}

async function getCsrfToken(): Promise<{ headerName: string; token: string }> {
  if (csrfToken) {
    return csrfToken;
  }
  const res = await fetch(buildApiUrl("/api/auth/csrf"), {
    method: "GET",
    headers: { Accept: "application/json" },
    credentials: "include",
  });
  if (!res.ok) {
    throw new ApiError(res.status, CSRF_ERROR_MESSAGE);
  }
  csrfToken = (await res.json()) as { headerName: string; token: string };
  return csrfToken;
}

async function download(path: string): Promise<Blob> {
  let res: Response;
  try {
    res = await fetch(buildApiUrl(path), { credentials: "include" });
  } catch {
    throw new ApiError(0, NETWORK_ERROR_MESSAGE);
  }

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    let message = responseFallbackMessage(res.status, res.statusText);
    let code: string | undefined;
    let fields: Record<string, string> | undefined;
    const parsed = parseApiErrorPayload(text, message);
    message = parsed.message;
    code = parsed.code;
    fields = parsed.fields;
    throw new ApiError(res.status, message, code, fields);
  }

  return res.blob();
}

function responseFallbackMessage(status: number, statusText: string): string {
  if (status === 502 || status === 503 || status === 504) {
    return "The service is temporarily unavailable. Retry in a moment.";
  }
  return statusText || "Request failed.";
}

export const api = {
  googleAuthUrl(): string {
    const base = buildApiUrl("/").replace(/\/+$/, "");
    return `${base}/oauth2/authorization/google`;
  },

  async login(email: string, password: string): Promise<{ plan?: string; email?: string; roles?: string[] }> {
    return request("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
  },

  async register(email: string, password: string): Promise<{ message: string; email?: string }> {
    return request("/api/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
  },

  async verifyEmail(token: string): Promise<{ message: string; email: string }> {
    return request("/api/auth/verify-email", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token }),
    });
  },

  async resendVerificationEmail(email: string): Promise<{ message: string }> {
    return request("/api/auth/verify-email/resend", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email }),
    });
  },

  async requestPasswordReset(email: string): Promise<{ message: string }> {
    return request("/api/auth/password-reset/request", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email }),
    });
  },

  async resetPassword(token: string, password: string): Promise<{ message: string }> {
    return request("/api/auth/password-reset/confirm", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token, password }),
    });
  },

  async oauth2Status(): Promise<{ googleEnabled: boolean }> {
    return request("/api/auth/oauth2/status");
  },

  async getAdminDashboard(limit = 12): Promise<AdminDashboard> {
    return request(`/api/admin/dashboard?limit=${queryParam(limit)}`);
  },

  async rotateAdminPassword(): Promise<AdminSecretRotation> {
    return request("/api/admin/secrets/admin-password/rotate", { method: "POST" });
  },

  async getMe(): Promise<{ email: string; plan: string; roles?: string[]; quotas?: Quotas }> {
    return request("/api/auth/me");
  },

  async getMyApis(): Promise<ApiProject[]> {
    return request("/api/account/apis");
  },

  async getSecurityDeployments(): Promise<SecurityDeployment[]> {
    return request("/api/security/deployments");
  },

  async getGeneratedApi(id: string): Promise<GeneratedApiDetail> {
    return request(`/api/account/apis/${pathSegment(id)}`);
  },

  async getAccountSummary(): Promise<AccountSummary> {
    return request("/api/account/summary");
  },

  async getGeneratedApiPreview(id: string): Promise<ApiPreview> {
    return request(`/api/account/apis/${pathSegment(id)}/preview`);
  },

  async getRecentFailedPreviews(limit = 5): Promise<FailedPreview[]> {
    return request(`/api/account/previews/failed?limit=${queryParam(limit)}`);
  },

  async getGeneratedApiPreviewDiagnostics(id: string): Promise<PreviewDiagnostics> {
    return request(`/api/account/apis/${pathSegment(id)}/preview/diagnostics`);
  },

  async startGeneratedApiPreview(id: string): Promise<ApiPreview> {
    return request(`/api/account/apis/${pathSegment(id)}/preview/start`, { method: "POST" });
  },

  async stopGeneratedApiPreview(id: string): Promise<ApiPreview> {
    return request(`/api/account/apis/${pathSegment(id)}/preview/stop`, { method: "POST" });
  },

  async restartGeneratedApiPreview(id: string): Promise<ApiPreview> {
    return request(`/api/account/apis/${pathSegment(id)}/preview/restart`, { method: "POST" });
  },

  async getGeneratedApiPreviewLogs(id: string, tail = 200): Promise<string[]> {
    return request(`/api/account/apis/${pathSegment(id)}/preview/logs?tail=${queryParam(tail)}`);
  },

  async logout(): Promise<void> {
    try {
      await request("/api/auth/logout", { method: "POST" });
    } finally {
      csrfToken = null;
    }
  },

  async downloadGenerationZip(jobId: string): Promise<Blob> {
    return download(`/api/generate/${pathSegment(jobId)}/download`);
  },

  async downloadFile(path: string): Promise<Blob> {
    return download(path);
  },

  async startGeneration(
    payload: {
      appName: string;
      basePackage: string;
      databaseType?: string;
      jdbcUrl?: string;
      jdbcUsername?: string;
      jdbcPassword?: string;
      schema?: string;
      build?: boolean;
      deployDocker?: boolean;
      deployDb?: boolean;
      copyData?: boolean;
      hostPort?: number;
      dbPort?: number;
    },
    isAsync = true
  ): Promise<{ jobId: string; statusUrl: string; downloadUrl: string; generatedApiId?: string; generationStatus?: string }> {
    return request(`/api/generate?async=${queryParam(isAsync ? "true" : "false")}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
  },

  async startSchemaFileGeneration(
    payload: {
      file: File;
      build?: boolean;
      deployDocker?: boolean;
      hostPort?: number;
    },
    isAsync = true
  ): Promise<{ jobId: string; statusUrl: string; downloadUrl: string; generatedApiId?: string; generationStatus?: string }> {
    const form = new FormData();
    form.append("file", payload.file);
    const params = new URLSearchParams();
    params.set("async", isAsync ? "true" : "false");
    if (payload.build !== undefined) params.set("build", String(payload.build));
    if (payload.deployDocker !== undefined) params.set("deployDocker", String(payload.deployDocker));
    if (payload.hostPort !== undefined) params.set("hostPort", String(payload.hostPort));
    return request(`/api/generate/schema-file?${params.toString()}`, {
      method: "POST",
      body: form,
    });
  },


  async getGenerationStatus(jobId: string): Promise<{
    jobId: string;
    status: string;
    createdAt: string;
    error?: string;
    hostPort?: number;
    apiBaseUrl?: string;
    proxyUrl?: string;
    containerId?: string;
  }> {
    return request(`/api/generate/${pathSegment(jobId)}`);
  },

  async getGenerationLogs(jobId: string, tail = 200, audience: 'user' | 'dev' = 'user'): Promise<string[]> {
    return request(`/api/generate/${pathSegment(jobId)}/logs?tail=${queryParam(tail)}&audience=${queryParam(audience)}`);
  },

  async stopGeneration(jobId: string): Promise<void> {
    await request(`/api/generate/${pathSegment(jobId)}/stop`, { method: "POST" });
  },
};
