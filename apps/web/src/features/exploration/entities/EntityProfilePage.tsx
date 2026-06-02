import { useParams } from 'react-router-dom'
import { EntityProfileView } from '../components/EntityProfileView'

/** Exploration → actor profile: current state + version history. */
export function EntityProfilePage() {
  const { campaignId, entityId } = useParams<{ campaignId: string; entityId: string }>()
  return (
    <EntityProfileView
      entityType="actor"
      entityId={entityId ?? ''}
      backTo={`/campaigns/${campaignId}/explore/entities`}
      backLabel="Back to entities"
    />
  )
}
