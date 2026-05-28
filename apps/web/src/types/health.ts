export type OverallStatus = 'UP' | 'DEGRADED'
export type ComponentStatus = 'UP' | 'DOWN'

export interface HealthResponse {
  status: OverallStatus
  db: ComponentStatus
}
