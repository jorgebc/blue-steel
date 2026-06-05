import type { Relation } from '@/types/relation'

interface Props {
  relations: Relation[]
  /** Endpoint id → entity name, for resolving the source/target of each relation. */
  nameById: Record<string, string>
  /** The currently selected relation, whose annotation thread is shown alongside (F4.4.9). */
  selectedId?: string | null
  /** Selects a relation to reveal its annotation thread. */
  onSelect?: (relationId: string) => void
}

/**
 * Accessible, text-only rendering of the relations graph (F4.3.11, D-030). The React Flow canvas is
 * an SVG that assistive technology cannot navigate; this list is its equivalent — every relation,
 * resolved or not, is described as "source → kind → target". Unresolved endpoints read as "Unknown".
 * Each relation is selectable to open its non-canonical annotation thread (F4.4.9).
 */
export function RelationsList({ relations, nameById, selectedId, onSelect }: Props) {
  if (relations.length === 0) {
    return <p className="text-sm text-slate-500">No relations yet.</p>
  }

  return (
    <ul aria-label="Relations" className="space-y-2">
      {relations.map((relation) => {
        const source = endpointName(relation.sourceEntityId, nameById)
        const target = endpointName(relation.targetEntityId, nameById)
        const label = relation.kind ?? relation.name
        const selected = selectedId === relation.relationId
        return (
          <li key={relation.relationId}>
            <button
              type="button"
              aria-pressed={selected}
              aria-label={`${source} ${label} ${target} — show annotations`}
              onClick={() => onSelect?.(relation.relationId)}
              className={[
                'w-full rounded-lg border px-3 py-2 text-left text-sm text-slate-700 transition-colors duration-200',
                selected
                  ? 'border-blue-500 bg-blue-50'
                  : 'border-slate-200 bg-white hover:border-slate-300',
              ].join(' ')}
            >
              <span className="font-medium text-slate-900">{source}</span>
              <span className="text-slate-400"> → </span>
              <span className="italic">{label}</span>
              <span className="text-slate-400"> → </span>
              <span className="font-medium text-slate-900">{target}</span>
            </button>
          </li>
        )
      })}
    </ul>
  )
}

function endpointName(entityId: string | null, nameById: Record<string, string>): string {
  if (entityId === null) {
    return 'Unknown'
  }
  return nameById[entityId] ?? 'Unknown'
}
