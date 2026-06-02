import { EntityListView } from '../components/EntityListView'

/** Exploration → Spaces: the offset-paginated space list. */
export function SpacesPage() {
  return (
    <EntityListView
      entityType="space"
      title="Spaces"
      description="Locations recorded across this campaign's sessions."
    />
  )
}
