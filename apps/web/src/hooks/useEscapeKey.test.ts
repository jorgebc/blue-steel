import { describe, it, expect, vi } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useEscapeKey } from './useEscapeKey'

function pressKey(key: string) {
  document.dispatchEvent(new KeyboardEvent('keydown', { key }))
}

describe('useEscapeKey', () => {
  it('calls the handler when Escape is pressed', () => {
    const handler = vi.fn()
    renderHook(() => useEscapeKey(handler))

    pressKey('Escape')

    expect(handler).toHaveBeenCalledOnce()
  })

  it('ignores other keys', () => {
    const handler = vi.fn()
    renderHook(() => useEscapeKey(handler))

    pressKey('Enter')

    expect(handler).not.toHaveBeenCalled()
  })

  it('removes the listener on unmount', () => {
    const handler = vi.fn()
    const { unmount } = renderHook(() => useEscapeKey(handler))

    unmount()
    pressKey('Escape')

    expect(handler).not.toHaveBeenCalled()
  })
})
