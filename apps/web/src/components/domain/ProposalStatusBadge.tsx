import { Badge } from '@/components/ui/badge'
import type { ProposalStatus } from '@/types/proposal'

const STATUS_LABEL: Record<ProposalStatus, string> = {
  OPEN: 'Open',
  COSIGNED: 'Co-signed',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  EXPIRED: 'Expired',
}

const STATUS_CLASS: Record<ProposalStatus, string> = {
  OPEN: 'bg-blue-50 text-blue-600 dark:bg-blue-950 dark:text-blue-300',
  COSIGNED: 'bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-300',
  APPROVED: 'bg-green-50 text-green-700 dark:bg-green-950 dark:text-green-300',
  REJECTED: 'bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300',
  EXPIRED: 'bg-muted text-muted-foreground',
}

/** Coloured pill for a proposal's lifecycle state, mirroring the session status-badge pattern. */
export function ProposalStatusBadge({ status }: { status: ProposalStatus }) {
  return (
    <Badge className={STATUS_CLASS[status]} variant="outline">
      {STATUS_LABEL[status]}
    </Badge>
  )
}
