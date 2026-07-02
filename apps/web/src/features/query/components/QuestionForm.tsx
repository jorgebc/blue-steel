import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'

interface Props {
  onSubmit: (question: string) => void
  isPending: boolean
}

/**
 * Controlled question input for Query Mode. Trims the value and ignores empty submissions;
 * the textarea and submit button are disabled while a query is in flight. Submit wiring (the
 * mutation call) lives in the {@link QueryPage} container.
 */
export function QuestionForm({ onSubmit, isPending }: Props) {
  const { t } = useTranslation()
  const [value, setValue] = useState('')
  const trimmed = value.trim()

  const counterColor =
    trimmed.length >= 2000
      ? 'text-error'
      : trimmed.length >= 1800
        ? 'text-warning'
        : 'text-muted-foreground'

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!trimmed || isPending) return
    onSubmit(trimmed)
    setValue('')
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      <label htmlFor="query-input" className="sr-only">
        {t('query.form.label')}
      </label>
      <div className="space-y-1">
        <Textarea
          id="query-input"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder={t('query.form.placeholder')}
          disabled={isPending}
          rows={3}
          maxLength={2000}
        />
        <span className={`block text-right text-xs ${counterColor}`}>{trimmed.length}/2000</span>
      </div>
      <Button type="submit" disabled={isPending || trimmed === '' || trimmed.length > 2000}>
        {isPending ? t('query.form.searching') : t('query.form.ask')}
      </Button>
    </form>
  )
}
