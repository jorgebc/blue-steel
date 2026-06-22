import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { RemoveMemberConfirmOverlay } from './RemoveMemberConfirmOverlay'
import i18n from '@/i18n'

function setup(memberEmail: string | null) {
  return render(
    <RemoveMemberConfirmOverlay
      open
      memberEmail={memberEmail}
      onConfirm={() => {}}
      onClose={() => {}}
      isPending={false}
    />
  )
}

describe('RemoveMemberConfirmOverlay', () => {
  it('renders the English confirmation with the member email bolded mid-sentence', () => {
    setup('player@example.com')

    const email = screen.getByText('player@example.com')
    expect(email.tagName).toBe('SPAN')
    expect(email).toHaveClass('font-medium')
    expect(screen.getByText(/will lose access to the campaign/i)).toBeInTheDocument()
  })

  it('falls back to "This member" when no email is provided', () => {
    setup(null)

    expect(
      screen.getByText('This member will lose access to the campaign. They can be invited again later.')
    ).toBeInTheDocument()
  })

  it('renders the Spanish confirmation, preserving the bolded email, when the UI locale is es', async () => {
    await i18n.changeLanguage('es')
    setup('player@example.com')

    const email = screen.getByText('player@example.com')
    expect(email.tagName).toBe('SPAN')
    expect(screen.getByText(/perderá el acceso a la campaña/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Quitar miembro' })).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = setup('player@example.com')
    expect(await axe(container)).toHaveNoViolations()
  })
})
