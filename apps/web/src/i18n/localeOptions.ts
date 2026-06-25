import type { UiLocale } from '@/types/auth'

// Language names are conventionally shown in their own language regardless of the
// active UI locale, so these labels stay literal (not translated).
export const LOCALE_OPTIONS: { value: UiLocale; label: string }[] = [
  { value: 'en', label: 'English' },
  { value: 'es', label: 'Español' },
]
