export interface ApiError {
  code: string
  message: string
  field: string | null
}

export interface ApiEnvelope<T> {
  data: T
  meta: unknown
  errors: ApiError[]
}
