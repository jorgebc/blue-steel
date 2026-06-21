import { useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { EntityProfileView } from '../components/EntityProfileView'

/** Exploration → space profile: current state + version history. */
export function SpaceProfilePage() {
  const { campaignId, spaceId } = useParams<{ campaignId: string; spaceId: string }>()
  const { t } = useTranslation()
  return (
    <EntityProfileView
      entityType="space"
      entityId={spaceId ?? ''}
      backTo={`/campaigns/${campaignId}/explore/spaces`}
      backLabel={t('exploration.backToSpaces')}
    />
  )
}
