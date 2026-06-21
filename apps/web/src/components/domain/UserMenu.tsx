import { useNavigate, NavLink } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { LogOut, Settings } from 'lucide-react'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useAuthStore } from '@/store/authStore'
import { useSettingsStore } from '@/store/settingsStore'
import { useUpdateProfile } from '@/api/users'
import type { Theme, UiLocale } from '@/types/auth'
import { InitialsAvatar } from './InitialsAvatar'

const THEME_OPTIONS: { value: Theme; labelKey: string }[] = [
  { value: 'light', labelKey: 'userMenu.themeLight' },
  { value: 'dark', labelKey: 'userMenu.themeDark' },
  { value: 'system', labelKey: 'userMenu.themeSystem' },
]

// Language names are conventionally shown in their own language regardless of the
// active UI locale, so these labels stay literal (not translated).
const LOCALE_OPTIONS: { value: UiLocale; label: string }[] = [
  { value: 'en', label: 'English' },
  { value: 'es', label: 'Español' },
]

/** Selecting a quick toggle persists the preference but keeps the menu open for further tweaks. */
const keepOpen = (e: Event) => e.preventDefault()

/**
 * Top-right account menu (D-102): the initials/accent avatar opens a dropdown showing the user's
 * name + email, a Settings link to the global {@code /settings} route, inline theme and EN/ES
 * quick toggles (persisted via {@link useUpdateProfile}), and Log out. The toggles only persist the
 * preference here; applying the theme visually lands later (F8.7).
 */
export function UserMenu() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const currentUser = useAuthStore((s) => s.currentUser)
  const theme = useSettingsStore((s) => s.theme)
  const uiLocale = useSettingsStore((s) => s.uiLocale)
  const setTheme = useSettingsStore((s) => s.setTheme)
  const setUiLocale = useSettingsStore((s) => s.setUiLocale)
  const updateProfile = useUpdateProfile()

  if (!currentUser) return null
  const { displayName, email, avatarAccentColor } = currentUser
  const name = displayName?.trim() || email

  function handleTheme(next: string) {
    setTheme(next as Theme)
    updateProfile.mutate({ theme: next as Theme })
  }

  function handleLocale(next: string) {
    setUiLocale(next as UiLocale)
    updateProfile.mutate({ uiLocale: next as UiLocale })
  }

  function handleLogout() {
    useAuthStore.getState().logout()
    navigate('/login')
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={t('userMenu.accountMenu')}
        className="rounded-full focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-500"
      >
        <InitialsAvatar
          displayName={displayName}
          email={email}
          accentColor={avatarAccentColor}
          size="sm"
        />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel className="text-popover-foreground">
          <span className="block truncate text-sm font-semibold">{name}</span>
          <span className="block truncate text-xs font-normal text-slate-500">{email}</span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <NavLink to="/settings">
            <Settings aria-hidden />
            {t('userMenu.settings')}
          </NavLink>
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuLabel>{t('userMenu.theme')}</DropdownMenuLabel>
        <DropdownMenuRadioGroup value={theme} onValueChange={handleTheme}>
          {THEME_OPTIONS.map((option) => (
            <DropdownMenuRadioItem key={option.value} value={option.value} onSelect={keepOpen}>
              {t(option.labelKey)}
            </DropdownMenuRadioItem>
          ))}
        </DropdownMenuRadioGroup>
        <DropdownMenuSeparator />
        <DropdownMenuLabel>{t('userMenu.language')}</DropdownMenuLabel>
        <DropdownMenuRadioGroup value={uiLocale} onValueChange={handleLocale}>
          {LOCALE_OPTIONS.map((option) => (
            <DropdownMenuRadioItem key={option.value} value={option.value} onSelect={keepOpen}>
              {option.label}
            </DropdownMenuRadioItem>
          ))}
        </DropdownMenuRadioGroup>
        <DropdownMenuSeparator />
        <DropdownMenuItem variant="destructive" onSelect={handleLogout}>
          <LogOut aria-hidden />
          {t('userMenu.logOut')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
