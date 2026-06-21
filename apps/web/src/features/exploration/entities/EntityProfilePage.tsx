import { useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { EntityProfileView } from '../components/EntityProfileView'

/** Exploration → actor profile: current state + version history. */
export function EntityProfilePage() {
  const { campaignId, entityId } = useParams<{ campaignId: string; entityId: string }>()
  const { t } = useTranslation()
  return (
    <EntityProfileView
      entityType="actor"
      entityId={entityId ?? ''}
      backTo={`/campaigns/${campaignId}/explore/entities`}
      backLabel={t('exploration.backToEntities')}
    />
  )
}
