import { QueryClient } from '@tanstack/react-query'

/**
 * Builds a fresh QueryClient for a test, with retries off so rejected queries
 * surface their error state immediately instead of being retried.
 */
export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
}
