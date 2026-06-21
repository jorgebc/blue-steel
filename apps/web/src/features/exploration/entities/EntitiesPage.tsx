import { useTranslation } from 'react-i18next'
import { EntityListView } from '../components/EntityListView'

/** Exploration → Entities: the offset-paginated actor list. */
export function EntitiesPage() {
  const { t } = useTranslation()
  return (
    <EntityListView
      entityType="actor"
      title={t('exploration.entitiesTitle')}
      description={t('exploration.entitiesDescription')}
    />
  )
}
