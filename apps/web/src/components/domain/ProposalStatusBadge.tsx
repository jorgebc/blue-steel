import { useTranslation } from 'react-i18next'
import { Badge } from '@/components/ui/badge'
import type { ProposalStatus } from '@/types/proposal'

const STATUS_CLASS: Record<ProposalStatus, string> = {
  OPEN: 'bg-info-subtle text-info-foreground',
  COSIGNED: 'bg-warning-subtle text-warning-foreground',
  APPROVED: 'bg-success-subtle text-success-foreground',
  REJECTED: 'bg-error-subtle text-error-foreground',
  EXPIRED: 'bg-muted text-muted-foreground',
}

/** Coloured pill for a proposal's lifecycle state, mirroring the session status-badge pattern. */
export function ProposalStatusBadge({ status }: { status: ProposalStatus }) {
  const { t } = useTranslation()
  return (
    <Badge className={STATUS_CLASS[status]} variant="outline">
      {t(`proposals.statusBadge.${status.toLowerCase()}`)}
    </Badge>
  )
}
