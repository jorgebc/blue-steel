import { EntityListView } from '../components/EntityListView'

/** Exploration → Entities: the offset-paginated actor list. */
export function EntitiesPage() {
  return (
    <EntityListView
      entityType="actor"
      title="Entities"
      description="Actors recorded across this campaign's sessions."
    />
  )
}
