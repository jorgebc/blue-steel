import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
}

/**
 * Last-resort boundary around the router: turns an uncaught render error into a
 * graceful recovery surface instead of a white screen. The caught error's
 * message and stack are never rendered to the DOM — they could carry internal
 * endpoints or system detail — and the diagnostic log below runs in development
 * only, so nothing is emitted to the console in a production build.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false }

  static getDerivedStateFromError(): State {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    if (import.meta.env.DEV) {
      console.error('Uncaught render error', error, info.componentStack)
    }
  }

  render() {
    if (!this.state.hasError) return this.props.children

    return (
      <div role="alert" className="flex min-h-screen items-center justify-center p-8">
        <div className="max-w-md space-y-4 text-center">
          <h1 className="text-lg font-medium text-foreground">Something went wrong</h1>
          <p className="text-sm text-muted-foreground">
            An unexpected error occurred. Reloading the page usually fixes it.
          </p>
          <Button onClick={() => window.location.reload()}>Reload</Button>
        </div>
      </div>
    )
  }
}
