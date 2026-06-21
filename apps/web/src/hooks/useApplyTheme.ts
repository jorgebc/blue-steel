import { useEffect } from 'react'
import { useSettingsStore } from '@/store/settingsStore'

/**
 * Applies the user's theme preference to {@code <html>}: toggles the `dark`
 * class from {@link useSettingsStore}'s `theme`, resolving `'system'` against the
 * OS colour scheme and following live OS changes while `system` is selected.
 */
export function useApplyTheme() {
  const theme = useSettingsStore((s) => s.theme)

  useEffect(() => {
    const root = document.documentElement
    const media = window.matchMedia('(prefers-color-scheme: dark)')

    const apply = () => {
      const isDark = theme === 'dark' || (theme === 'system' && media.matches)
      root.classList.toggle('dark', isDark)
    }

    apply()

    if (theme === 'system') {
      media.addEventListener('change', apply)
      return () => media.removeEventListener('change', apply)
    }
  }, [theme])
}
