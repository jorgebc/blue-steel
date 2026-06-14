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
  OPEN: 'bg-blue-50 text-blue-600',
  COSIGNED: 'bg-amber-50 text-amber-700',
  APPROVED: 'bg-green-50 text-green-700',
  REJECTED: 'bg-red-50 text-red-700',
  EXPIRED: 'bg-slate-100 text-slate-400',
}

/** Coloured pill for a proposal's lifecycle state, mirroring the session status-badge pattern. */
export function ProposalStatusBadge({ status }: { status: ProposalStatus }) {
  return (
    <Badge className={STATUS_CLASS[status]} variant="outline">
      {STATUS_LABEL[status]}
    </Badge>
  )
}
