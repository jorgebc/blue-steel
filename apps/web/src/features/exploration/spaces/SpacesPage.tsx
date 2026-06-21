import { useTranslation } from 'react-i18next'
import { EntityListView } from '../components/EntityListView'

/** Exploration → Spaces: the offset-paginated space list. */
export function SpacesPage() {
  const { t } = useTranslation()
  return (
    <EntityListView
      entityType="space"
      title={t('exploration.spacesTitle')}
      description={t('exploration.spacesDescription')}
    />
  )
}
