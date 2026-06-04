import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import { ReactFlowProvider, Position } from '@xyflow/react'
import { type ComponentProps } from 'react'
import { RelationEdge } from './RelationEdge'

function renderEdge(over: Record<string, unknown> = {}) {
  const props = {
    id: 'r1',
    sourceX: 0,
    sourceY: 0,
    targetX: 100,
    targetY: 100,
    sourcePosition: Position.Right,
    targetPosition: Position.Left,
    data: { relationId: 'r1', label: 'alliance' },
    ...over,
  } as unknown as ComponentProps<typeof RelationEdge>

  return render(
    <ReactFlowProvider>
      <svg>
        <RelationEdge {...props} />
      </svg>
    </ReactFlowProvider>
  )
}

describe('RelationEdge', () => {
  it('renders a bezier edge path from the supplied endpoints', () => {
    const { container } = renderEdge()

    const path = container.querySelector('path.react-flow__edge-path')
    expect(path).not.toBeNull()
    expect(path?.getAttribute('d')).toBeTruthy()
  })

  it('renders without crashing when the edge has no label', () => {
    const { container } = renderEdge({ data: { relationId: 'r1', label: '' } })

    expect(container.querySelector('path.react-flow__edge-path')).not.toBeNull()
  })
})
