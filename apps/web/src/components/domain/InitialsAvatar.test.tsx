import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { InitialsAvatar } from './InitialsAvatar'

describe('InitialsAvatar', () => {
  it('derives two-letter initials from a multi-word display name', () => {
    render(<InitialsAvatar displayName="Jorge Buffa" email="gm@example.com" />)
    expect(screen.getByText('JB')).toBeInTheDocument()
  })

  it('uses the first letter only for a single-word display name', () => {
    render(<InitialsAvatar displayName="Strahd" email="gm@example.com" />)
    expect(screen.getByText('S')).toBeInTheDocument()
  })

  it('falls back to the email initial when no display name is set', () => {
    render(<InitialsAvatar displayName={null} email="gm@example.com" />)
    expect(screen.getByText('G')).toBeInTheDocument()
  })

  it('labels the avatar with the display name when present', () => {
    render(<InitialsAvatar displayName="Jorge Buffa" email="gm@example.com" />)
    expect(screen.getByRole('img', { name: 'Jorge Buffa' })).toBeInTheDocument()
  })

  it('labels the avatar with the email when no display name is set', () => {
    render(<InitialsAvatar displayName={null} email="gm@example.com" />)
    expect(screen.getByRole('img', { name: 'gm@example.com' })).toBeInTheDocument()
  })

  it('applies the chosen accent color as the background', () => {
    render(
      <InitialsAvatar displayName="Jorge Buffa" email="gm@example.com" accentColor="#16a34a" />
    )
    expect(screen.getByRole('img')).toHaveStyle({ backgroundColor: '#16a34a' })
  })

  it('has no accessibility violations', async () => {
    const { container } = render(
      <InitialsAvatar displayName="Jorge Buffa" email="gm@example.com" />
    )
    expect(await axe(container)).toHaveNoViolations()
  })
})
