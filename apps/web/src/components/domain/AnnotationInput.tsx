import { useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'

/** Matches the backend `content` constraint (non-blank, max 5000 chars). */
const MAX_LENGTH = 5000

interface Props {
  onSubmit: (content: string) => void
  /** Disables the field + submit while the post mutation is in flight. */
  isPending: boolean
}

/**
 * Free-text composer for a new annotation. Submits the trimmed content and clears itself on submit;
 * the parent owns the mutation and feedback. Blank input and over-length content cannot be submitted.
 */
export function AnnotationInput({ onSubmit, isPending }: Props) {
  const [value, setValue] = useState('')
  const trimmed = value.trim()
  const canSubmit = trimmed.length > 0 && trimmed.length <= MAX_LENGTH && !isPending

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!canSubmit) return
    onSubmit(trimmed)
    setValue('')
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-2">
      <Textarea
        aria-label="Add an annotation"
        placeholder="Add a note, observation, or hypothesis…"
        value={value}
        maxLength={MAX_LENGTH}
        disabled={isPending}
        onChange={(event) => setValue(event.target.value)}
      />
      <div className="flex justify-end">
        <Button type="submit" disabled={!canSubmit}>
          Post annotation
        </Button>
      </div>
    </form>
  )
}
