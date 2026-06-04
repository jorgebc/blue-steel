import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { ReactFlowProvider } from '@xyflow/react'
import { axe } from 'vitest-axe'
import { type ComponentProps } from 'react'
import { RelationNode } from './RelationNode'
import type { NodeData } from '@/types/relation'

function renderNode(data: NodeData) {
  // The component reads only `data`; the remaining NodeProps fields are irrelevant here.
  const props = { data } as unknown as ComponentProps<typeof RelationNode>
  return render(
    <ReactFlowProvider>
      <MemoryRouter initialEntries={['/campaigns/c1/explore/relations']}>
        <Routes>
          <Route
            path="/campaigns/:campaignId/explore/relations"
            element={<RelationNode {...props} />}
          />
        </Routes>
      </MemoryRouter>
    </ReactFlowProvider>
  )
}

describe('RelationNode', () => {
  it('links an actor node to its entity profile route', () => {
    renderNode({ entityId: 'a1', entityType: 'actor', name: 'Mira' })

    const link = screen.getByRole('link', { name: /Mira/i })
    expect(link).toHaveAttribute('href', '/campaigns/c1/explore/entities/a1')
  })

  it('links a space node to its space profile route', () => {
    renderNode({ entityId: 's1', entityType: 'space', name: 'Thornwick' })

    const link = screen.getByRole('link', { name: /Thornwick/i })
    expect(link).toHaveAttribute('href', '/campaigns/c1/explore/spaces/s1')
  })

  it('has no accessibility violations', async () => {
    const { container } = renderNode({ entityId: 'a1', entityType: 'actor', name: 'Mira' })
    expect(await axe(container)).toHaveNoViolations()
  })
})
