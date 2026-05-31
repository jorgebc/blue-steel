import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { MemberManagementPanel } from './MemberManagementPanel'
import {
  useCampaignMembers,
  useChangeMemberRole,
  useInviteCampaignMember,
  useRemoveMember,
} from '@/api/members'
import type { CampaignMemberResponse } from '@/types/member'

vi.mock('@/api/members', () => ({
  useCampaignMembers: vi.fn(),
  useInviteCampaignMember: vi.fn(),
  useChangeMemberRole: vi.fn(),
  useRemoveMember: vi.fn(),
}))

const roster: CampaignMemberResponse[] = [
  { userId: 'u1', email: 'gm@example.com', role: 'gm', joinedAt: '2026-01-01T00:00:00Z' },
  { userId: 'u2', email: 'player@example.com', role: 'player', joinedAt: '2026-01-02T00:00:00Z' },
]

const inviteMutate = vi.fn()
const changeRoleMutate = vi.fn()
const removeMutate = vi.fn()

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(useCampaignMembers).mockReturnValue({
    data: roster,
    isLoading: false,
    isError: false,
  } as ReturnType<typeof useCampaignMembers>)
  vi.mocked(useInviteCampaignMember).mockReturnValue({
    mutate: inviteMutate,
    isPending: false,
  } as unknown as ReturnType<typeof useInviteCampaignMember>)
  vi.mocked(useChangeMemberRole).mockReturnValue({
    mutate: changeRoleMutate,
    isPending: false,
  } as unknown as ReturnType<typeof useChangeMemberRole>)
  vi.mocked(useRemoveMember).mockReturnValue({
    mutate: removeMutate,
    isPending: false,
  } as unknown as ReturnType<typeof useRemoveMember>)
})

describe('MemberManagementPanel', () => {
  it('renders the roster with a GM badge and member controls', () => {
    render(<MemberManagementPanel campaignId="c1" />)

    expect(screen.getByText('gm@example.com')).toBeInTheDocument()
    expect(screen.getByText('player@example.com')).toBeInTheDocument()
    // the player row exposes a role selector; the GM row does not
    expect(screen.getByLabelText(/role for player@example.com/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/role for gm@example.com/i)).not.toBeInTheDocument()
  })

  it('invites a member with the chosen email and role', () => {
    render(<MemberManagementPanel campaignId="c1" />)

    fireEvent.change(screen.getByLabelText(/invite by email/i), {
      target: { value: 'new@example.com' },
    })
    fireEvent.click(screen.getByRole('button', { name: /invite/i }))

    expect(inviteMutate).toHaveBeenCalledWith(
      { email: 'new@example.com', role: 'player' },
      expect.anything()
    )
  })

  it('confirms before removing a member', () => {
    render(<MemberManagementPanel campaignId="c1" />)

    fireEvent.click(screen.getByRole('button', { name: /^remove$/i }))
    // a confirmation overlay appears
    expect(screen.getByRole('dialog', { name: /remove member/i })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /remove member/i }))
    expect(removeMutate).toHaveBeenCalledWith('u2', expect.anything())
  })

  it('changes a member role via the role selector', () => {
    render(<MemberManagementPanel campaignId="c1" />)

    fireEvent.change(screen.getByLabelText(/role for player@example.com/i), {
      target: { value: 'editor' },
    })

    expect(changeRoleMutate).toHaveBeenCalledWith(
      { userId: 'u2', role: 'editor' },
      expect.anything()
    )
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<MemberManagementPanel campaignId="c1" />)

    expect(await axe(container)).toHaveNoViolations()
  })
})
