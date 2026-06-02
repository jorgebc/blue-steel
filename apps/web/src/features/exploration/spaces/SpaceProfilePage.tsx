import { useParams } from 'react-router-dom'
import { EntityProfileView } from '../components/EntityProfileView'

/** Exploration → space profile: current state + version history. */
export function SpaceProfilePage() {
  const { campaignId, spaceId } = useParams<{ campaignId: string; spaceId: string }>()
  return (
    <EntityProfileView
      entityType="space"
      entityId={spaceId ?? ''}
      backTo={`/campaigns/${campaignId}/explore/spaces`}
      backLabel="Back to spaces"
    />
  )
}
