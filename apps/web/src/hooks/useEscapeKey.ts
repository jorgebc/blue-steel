import { useEffect } from 'react'

/** Calls `handler` whenever the Escape key is pressed, for as long as it is mounted. */
export function useEscapeKey(handler: () => void) {
  useEffect(() => {
    const listener = (e: KeyboardEvent) => {
      if (e.key === 'Escape') handler()
    }
    document.addEventListener('keydown', listener)
    return () => document.removeEventListener('keydown', listener)
  }, [handler])
}
