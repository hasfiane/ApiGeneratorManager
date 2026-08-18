const isDev = import.meta.env.DEV

export const env = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  cloudbeaverUrl: import.meta.env.VITE_CLOUDBEAVER_URL ?? 'http://localhost:8978',

  // Seed demo values only for local development.
  demoAppName: import.meta.env.VITE_DEMO_APP_NAME ?? (isDev ? 'Generated API' : ''),
  demoBasePackage: import.meta.env.VITE_DEMO_BASE_PACKAGE ?? (isDev ? 'com.example.api' : ''),
  demoDatabaseType: (import.meta.env.VITE_DEMO_DB_TYPE ?? 'postgres') as 'postgres' | 'mysql' | 'h2',
  demoJdbcUrl: import.meta.env.VITE_DEMO_JDBC_URL ?? (isDev ? 'jdbc:postgresql://your-db-host:5432/your_database' : ''),
  demoJdbcUsername: import.meta.env.VITE_DEMO_DB_USERNAME ?? (isDev ? 'your_db_user' : ''),
  demoJdbcPassword: import.meta.env.VITE_DEMO_DB_PASSWORD ?? '',
  demoSchema: import.meta.env.VITE_DEMO_DB_SCHEMA ?? (isDev ? 'public' : ''),
  demoDeployDocker: String(import.meta.env.VITE_DEMO_DEPLOY_DOCKER ?? (isDev ? 'true' : 'false')).toLowerCase() === 'true',
  demoHostPort: Number(import.meta.env.VITE_DEMO_HOST_PORT ?? 18080),
  dockerRuntimeBlocked: String(import.meta.env.VITE_DOCKER_RUNTIME_BLOCKED ?? 'false').toLowerCase() === 'true',
} as const
