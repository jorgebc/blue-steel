import { useState } from 'react'
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
  const [value, setValue] = useState('')
  const trimmed = value.trim()

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!trimmed || isPending) return
    onSubmit(trimmed)
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      <label htmlFor="query-input" className="sr-only">
        Ask a question about the campaign
      </label>
      <Textarea
        id="query-input"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="What happened to Aldric after the Battle of Thornwall?"
        disabled={isPending}
        rows={3}
      />
      <Button type="submit" disabled={isPending || trimmed === ''}>
        {isPending ? 'Searching…' : 'Ask'}
      </Button>
    </form>
  )
}
