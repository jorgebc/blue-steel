import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Loader2, UserPlus } from 'lucide-react'
import {
  useCampaignMembers,
  useChangeMemberRole,
  useInviteCampaignMember,
  useRemoveMember,
} from '@/api/members'
import { ApiClientError } from '@/api/client'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { AssignableRole, CampaignMemberResponse } from '@/types/member'
import { RemoveMemberConfirmOverlay } from './RemoveMemberConfirmOverlay'

const ASSIGNABLE_ROLES: AssignableRole[] = ['editor', 'player']

type Banner = { variant: 'success' | 'error'; message: string } | null

function messageOf(error: unknown, fallback: string): string {
  if (error instanceof ApiClientError) return error.errors[0]?.message ?? error.message
  return fallback
}

function RosterSkeleton() {
  return (
    <ul className="flex flex-col gap-2" aria-hidden>
      {[0, 1, 2].map((i) => (
        <li key={i} className="h-12 animate-pulse rounded-lg bg-muted" />
      ))}
    </ul>
  )
}

/**
 * GM-only campaign roster management: invite members by email, change a
 * member's role, and remove members. Feedback uses {@link InlineBanner}
 * (no toasts, D-083) and the remove confirmation uses a FocusedOverlay (no
 * modals, D-082). Render this only for a GM caller.
 */
export function MemberManagementPanel({ campaignId }: { campaignId: string }) {
  const { t } = useTranslation()
  const { data: members, isLoading, isError } = useCampaignMembers(campaignId)
  const invite = useInviteCampaignMember(campaignId)
  const changeRole = useChangeMemberRole(campaignId)
  const remove = useRemoveMember(campaignId)

  const [email, setEmail] = useState('')
  const [inviteRole, setInviteRole] = useState<AssignableRole>('player')
  const [banner, setBanner] = useState<Banner>(null)
  const [removeTarget, setRemoveTarget] = useState<CampaignMemberResponse | null>(null)

  function handleInvite(e: React.FormEvent) {
    e.preventDefault()
    setBanner(null)
    const trimmed = email.trim()
    if (!trimmed) {
      setBanner({ variant: 'error', message: t('campaigns.enterEmail') })
      return
    }
    invite.mutate(
      { email: trimmed, role: inviteRole },
      {
        onSuccess: () => {
          setBanner({
            variant: 'success',
            message: t('campaigns.invitationSent', { email: trimmed }),
          })
          setEmail('')
        },
        onError: (err) =>
          setBanner({
            variant: 'error',
            message: messageOf(err, t('campaigns.inviteError')),
          }),
      }
    )
  }

  function handleRoleChange(member: CampaignMemberResponse, role: AssignableRole) {
    setBanner(null)
    changeRole.mutate(
      { userId: member.userId, role },
      {
        onSuccess: () =>
          setBanner({
            variant: 'success',
            message: t('campaigns.roleChanged', { email: member.email, role }),
          }),
        onError: (err) =>
          setBanner({
            variant: 'error',
            message: messageOf(err, t('campaigns.roleChangeError')),
          }),
      }
    )
  }

  function confirmRemove() {
    if (!removeTarget) return
    const target = removeTarget
    remove.mutate(target.userId, {
      onSuccess: () => {
        setBanner({
          variant: 'success',
          message: t('campaigns.memberRemoved', { email: target.email }),
        })
        setRemoveTarget(null)
      },
      onError: (err) => {
        setBanner({
          variant: 'error',
          message: messageOf(err, t('campaigns.removeError')),
        })
        setRemoveTarget(null)
      },
    })
  }

  return (
    <section className="mt-10" aria-labelledby="members-heading">
      <h2 id="members-heading" className="text-lg font-semibold text-foreground">
        {t('campaigns.members')}
      </h2>
      <p className="mt-1 text-sm text-muted-foreground">{t('campaigns.membersDescription')}</p>

      {banner && (
        <div className="mt-4">
          <InlineBanner
            variant={banner.variant}
            message={banner.message}
            onDismiss={() => setBanner(null)}
          />
        </div>
      )}

      <form onSubmit={handleInvite} className="mt-4 flex flex-wrap items-end gap-3" noValidate>
        <div className="flex-1 space-y-1">
          <Label htmlFor="invite-email">{t('campaigns.inviteByEmail')}</Label>
          <Input
            id="invite-email"
            type="email"
            placeholder={t('campaigns.emailPlaceholder')}
            autoComplete="off"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="invite-role">{t('campaigns.roleLabel')}</Label>
          <select
            id="invite-role"
            value={inviteRole}
            onChange={(e) => setInviteRole(e.target.value as AssignableRole)}
            className="h-9 rounded-lg border border-border bg-surface px-3 text-sm capitalize"
          >
            {ASSIGNABLE_ROLES.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
        </div>
        <Button type="submit" disabled={invite.isPending} aria-disabled={invite.isPending}>
          {invite.isPending ? (
            <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />
          ) : (
            <UserPlus className="mr-2 h-4 w-4" aria-hidden />
          )}
          {t('campaigns.invite')}
        </Button>
      </form>

      <div className="mt-6">
        {isLoading ? (
          <RosterSkeleton />
        ) : isError ? (
          <InlineBanner
            variant="error"
            message={t('campaigns.loadMembersError')}
            onDismiss={() => undefined}
          />
        ) : (
          <ul className="flex flex-col gap-2">
            {(members ?? []).map((member) => (
              <li
                key={member.userId}
                className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border p-3"
              >
                <span className="text-sm text-foreground">{member.email}</span>
                {member.role === 'gm' ? (
                  <span className="rounded-full bg-accent-subtle px-3 py-1 text-xs font-medium uppercase text-accent">
                    {t('campaigns.gm')}
                  </span>
                ) : (
                  <div className="flex items-center gap-2">
                    <label className="sr-only" htmlFor={`role-${member.userId}`}>
                      {t('campaigns.roleForMember', { email: member.email })}
                    </label>
                    <select
                      id={`role-${member.userId}`}
                      value={member.role}
                      disabled={changeRole.isPending}
                      onChange={(e) => handleRoleChange(member, e.target.value as AssignableRole)}
                      className="h-8 rounded-lg border border-border bg-surface px-2 text-sm capitalize"
                    >
                      {ASSIGNABLE_ROLES.map((r) => (
                        <option key={r} value={r}>
                          {r}
                        </option>
                      ))}
                    </select>
                    <button
                      type="button"
                      onClick={() => setRemoveTarget(member)}
                      className="rounded-lg px-3 py-1 text-sm font-medium text-red-600 hover:bg-red-50"
                    >
                      {t('common.remove')}
                    </button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>

      <RemoveMemberConfirmOverlay
        open={removeTarget !== null}
        memberEmail={removeTarget?.email ?? null}
        onConfirm={confirmRemove}
        onClose={() => setRemoveTarget(null)}
        isPending={remove.isPending}
      />
    </section>
  )
}
