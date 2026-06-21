import { describe, it, expect, beforeEach } from 'vitest'
import i18n from './index'
import { useSettingsStore } from '@/store/settingsStore'

describe('i18n runtime', () => {
  beforeEach(async () => {
    useSettingsStore.setState({ uiLocale: 'en' })
    await i18n.changeLanguage('en')
  })

  it('switches language when uiLocale changes in the settings store', () => {
    useSettingsStore.setState({ uiLocale: 'es' })
    expect(i18n.language).toBe('es')
  })

  it('falls back to English when a key is missing in the active locale', async () => {
    i18n.addResource('en', 'translation', 'test.enOnly', 'English only')
    await i18n.changeLanguage('es')
    expect(i18n.t('test.enOnly')).toBe('English only')
  })
})
