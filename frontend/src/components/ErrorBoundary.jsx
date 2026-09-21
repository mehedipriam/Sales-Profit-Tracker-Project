import { Component } from 'react'

/**
 * Catches a crash while rendering (for example an API response missing a field) so the user sees a message instead of a
 * blank page. Give it a `key` (the current path) to start fresh when they navigate elsewhere.
 */
export default class ErrorBoundary extends Component {
  state = { error: null }

  static getDerivedStateFromError(error) {
    return { error }
  }

  componentDidCatch(error, info) {
    console.error('Render error:', error, info.componentStack)
  }

  render() {
    if (!this.state.error) return this.props.children
    return (
      <div className="crash" role="alert">
        <h2>Something went wrong on this page</h2>
        <p className="muted">
          This is usually a temporary problem, or the app and the server are out of step (for example the server was
          not restarted after an update). Your data is safe.
        </p>
        <p className="crash-detail">{String(this.state.error?.message ?? this.state.error)}</p>
        <div className="actions">
          <button className="btn secondary" onClick={() => this.setState({ error: null })}>Try again</button>
          <button className="btn" onClick={() => window.location.reload()}>Reload the page</button>
        </div>
      </div>
    )
  }
}
