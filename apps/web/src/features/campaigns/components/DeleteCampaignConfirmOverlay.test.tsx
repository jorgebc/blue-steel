import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { DeleteCampaignConfirmOverlay } from './DeleteCampaignConfirmOverlay'
import i18n from '@/i18n'

function setup() {
  return render(
    <DeleteCampaignConfirmOverlay
      open
      campaignName="Curse of Strahd"
      onConfirm={() => {}}
      onClose={() => {}}
      isPending={false}
    />
  )
}

describe('DeleteCampaignConfirmOverlay', () => {
  it('renders the English confirmation with the campaign name bolded mid-sentence', () => {
    setup()

    const name = screen.getByText('Curse of Strahd')
    expect(name.tagName).toBe('SPAN')
    expect(name).toHaveClass('font-medium')
    expect(screen.getByText(/and all its data — sessions, actors/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Delete campaign' })).toBeInTheDocument()
  })

  it('renders the Spanish confirmation, preserving the bolded name, when the UI locale is es', async () => {
    await i18n.changeLanguage('es')
    setup()

    const name = screen.getByText('Curse of Strahd')
    expect(name.tagName).toBe('SPAN')
    expect(screen.getByText(/y todos sus datos — sesiones, actores/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Eliminar campaña' })).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = setup()
    expect(await axe(container)).toHaveNoViolations()
  })
})
