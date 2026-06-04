import type { Relation } from '@/types/relation'

interface Props {
  relations: Relation[]
  /** Endpoint id → entity name, for resolving the source/target of each relation. */
  nameById: Record<string, string>
}

/**
 * Accessible, text-only rendering of the relations graph (F4.3.11, D-030). The React Flow canvas is
 * an SVG that assistive technology cannot navigate; this list is its equivalent — every relation,
 * resolved or not, is described as "source → kind → target". Unresolved endpoints read as "Unknown".
 */
export function RelationsList({ relations, nameById }: Props) {
  if (relations.length === 0) {
    return <p className="text-sm text-slate-500">No relations yet.</p>
  }

  return (
    <ul aria-label="Relations" className="space-y-2">
      {relations.map((relation) => {
        const source = endpointName(relation.sourceEntityId, nameById)
        const target = endpointName(relation.targetEntityId, nameById)
        const label = relation.kind ?? relation.name
        return (
          <li
            key={relation.relationId}
            className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700"
          >
            <span className="font-medium text-slate-900">{source}</span>
            <span className="text-slate-400"> → </span>
            <span className="italic">{label}</span>
            <span className="text-slate-400"> → </span>
            <span className="font-medium text-slate-900">{target}</span>
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
