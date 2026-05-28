import { useQuery } from '@tanstack/react-query'
import { apiClient } from './client'
import type { HealthResponse } from '@/types/health'

export async function getHealth(): Promise<HealthResponse> {
  const res = await apiClient.get<HealthResponse>('/api/v1/health')
  return res.data
}

export function useHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: getHealth,
    retry: false,
  })
}
