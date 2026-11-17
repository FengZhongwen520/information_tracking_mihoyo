import React, { type ErrorInfo, type ReactNode } from 'react'

interface Props {
  children: ReactNode
}

interface State {
  failed: boolean
}

export class ErrorBoundary extends React.Component<Props, State> {
  state: State = { failed: false }

  static getDerivedStateFromError(): State {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('页面渲染失败', error, info.componentStack)
  }

  render() {
    if (!this.state.failed) return this.props.children

    return (
      <main className="error-boundary">
        <span className="boot-mark">追</span>
        <h1>页面出了点问题</h1>
        <p>可以刷新页面重试。如果一直出现，请稍后再来。</p>
        <button type="button" className="primary-button" onClick={() => window.location.reload()}>
          刷新页面
        </button>
        <a className="text-link" href="/">返回活动看板</a>
      </main>
    )
  }
}
