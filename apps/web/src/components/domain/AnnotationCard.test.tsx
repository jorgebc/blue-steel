import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { AnnotationCard } from './AnnotationCard'
import type { Annotation } from '@/types/annotation'

const annotation: Annotation = {
  id: 'an1',
  entityType: 'actor',
  entityId: 'e1',
  authorId: 'abcdef12-3456-7890-aaaa-bbbbbbbbbbbb',
  content: 'A suspicious figure lurking by the gate.',
  createdAt: '2026-06-05T10:30:00Z',
}

describe('AnnotationCard', () => {
  it('renders the annotation content', () => {
    render(<AnnotationCard annotation={annotation} canDelete={false} onDelete={vi.fn()} />)
    expect(screen.getByText(/suspicious figure/i)).toBeInTheDocument()
  })

  it('does not render an edit control (annotations are immutable)', () => {
    render(<AnnotationCard annotation={annotation} canDelete onDelete={vi.fn()} />)
    expect(screen.queryByRole('button', { name: /edit/i })).not.toBeInTheDocument()
  })

  it('shows the delete button only when canDelete is true', () => {
    const { rerender } = render(
      <AnnotationCard annotation={annotation} canDelete={false} onDelete={vi.fn()} />
    )
    expect(screen.queryByRole('button', { name: /delete annotation/i })).not.toBeInTheDocument()

    rerender(<AnnotationCard annotation={annotation} canDelete onDelete={vi.fn()} />)
    expect(screen.getByRole('button', { name: /delete annotation/i })).toBeInTheDocument()
  })

  it('calls onDelete when the delete button is clicked', async () => {
    const onDelete = vi.fn()
    render(<AnnotationCard annotation={annotation} canDelete onDelete={onDelete} />)
    await userEvent.click(screen.getByRole('button', { name: /delete annotation/i }))
    expect(onDelete).toHaveBeenCalledOnce()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(
      <AnnotationCard annotation={annotation} canDelete onDelete={vi.fn()} />
    )
    expect(await axe(container)).toHaveNoViolations()
  })
})
