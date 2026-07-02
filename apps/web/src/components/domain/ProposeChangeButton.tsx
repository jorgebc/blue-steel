import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { FocusedOverlay } from '@/components/domain/FocusedOverlay'
import { ProposalSubmitForm } from '@/components/domain/ProposalSubmitForm'
import { Button } from '@/components/ui/button'
import type { ProposalTargetType } from '@/types/proposal'

interface Props {
  targetType: ProposalTargetType
  targetId: string
  entityName: string
  /** The entity's current snapshot (latest version's full snapshot), seeding the editable fields. */
  currentSnapshot: Record<string, unknown>
}

/**
 * Opens the "propose a change" submission overlay for an actor or space (F5.7). Available to any
 * campaign member — proposals are the player-facing edit path; non-author members co-sign and the GM
 * decides.
 */
export function ProposeChangeButton({ targetType, targetId, entityName, currentSnapshot }: Props) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)

  return (
    <>
      <Button type="button" variant="outline" onClick={() => setOpen(true)}>
        {t('proposals.proposeChange')}
      </Button>
      <FocusedOverlay
        open={open}
        onClose={() => setOpen(false)}
        ariaLabel={t('proposals.proposeChangeAria', { name: entityName })}
      >
        <ProposalSubmitForm
          targetType={targetType}
          targetId={targetId}
          currentSnapshot={currentSnapshot}
          onSubmitted={() => setOpen(false)}
          onCancel={() => setOpen(false)}
        />
      </FocusedOverlay>
    </>
  )
}
