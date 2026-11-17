import { useEffect, useRef } from 'react'

export function useLiveRefresh(load: (silent: boolean) => void, intervalMs = 60_000) {
  const loadRef = useRef(load)
  loadRef.current = load

  useEffect(() => {
    let timer: number | undefined
    const run = () => {
      if (document.visibilityState === 'visible') loadRef.current(true)
    }
    const schedule = () => {
      if (timer !== undefined) window.clearInterval(timer)
      timer = window.setInterval(run, intervalMs)
    }
    const onVisibility = () => {
      if (document.visibilityState === 'visible') {
        loadRef.current(true)
        schedule()
      }
    }

    loadRef.current(false)
    schedule()
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      if (timer !== undefined) window.clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [intervalMs])
}
