import Lenis from 'lenis'
import 'lenis/dist/lenis.css'
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'

const SmoothScrollContext = createContext<Lenis | null>(null)
const REDUCED_MOTION = '(prefers-reduced-motion: reduce)'

const LENIS_OPTIONS = {
  lerp: 0.1,
  wheelMultiplier: 1,
  touchMultiplier: 1,
  syncTouch: true,
  touchInertiaMultiplier: 0,
  smoothWheel: true,
  anchors: true,
  autoRaf: false,
  overscroll: false,
} as const

const TOUCH_SAMPLE_WINDOW = 110
const TOUCH_IDLE_LIMIT = 90
const TOUCH_GLIDE_MS = 240
const TOUCH_GLIDE_MAX = 420
const TOUCH_MIN_VELOCITY = 0.18

function canSmoothScroll() {
  return !window.matchMedia(REDUCED_MOTION).matches
}

function attachTouchGlide(instance: Lenis) {
  let samples: Array<{ x: number; y: number; t: number }> = []
  let suppressed = false

  const start = (event: TouchEvent) => {
    const target = event.target instanceof Element ? event.target : null
    suppressed = Boolean(target?.closest('[data-lenis-prevent], [data-lenis-prevent-touch]'))
    if (suppressed || event.touches.length !== 1) {
      samples = []
      return
    }
    const touch = event.touches[0]
    if (!touch) return
    samples = [{ x: touch.clientX, y: touch.clientY, t: performance.now() }]
  }

  const move = (event: TouchEvent) => {
    if (suppressed || event.touches.length !== 1) {
      samples = []
      return
    }
    const touch = event.touches[0]
    if (!touch) return
    const now = performance.now()
    samples.push({ x: touch.clientX, y: touch.clientY, t: now })
    samples = samples.filter((sample) => now - sample.t <= TOUCH_SAMPLE_WINDOW)
  }

  const end = () => {
    if (samples.length < 2) {
      samples = []
      return
    }

    const last = samples[samples.length - 1]
    if (performance.now() - last.t > TOUCH_IDLE_LIMIT) {
      samples = []
      return
    }

    const first = samples[0]
    const elapsed = last.t - first.t
    const dx = last.x - first.x
    const dy = last.y - first.y
    if (elapsed < 16 || Math.abs(dy) <= Math.abs(dx) * 1.2) {
      samples = []
      return
    }

    const velocity = -dy / elapsed
    if (Math.abs(velocity) < TOUCH_MIN_VELOCITY) {
      samples = []
      return
    }

    const glide = Math.max(-TOUCH_GLIDE_MAX, Math.min(TOUCH_GLIDE_MAX, velocity * TOUCH_GLIDE_MS))
    const target = Math.max(0, Math.min(instance.limit, instance.scroll + glide))
    samples = []

    if (Math.abs(target - instance.scroll) < 1) return
    instance.scrollTo(target, { lerp: 0.13, programmatic: false })
  }

  const cancel = () => {
    samples = []
    suppressed = false
  }

  window.addEventListener('touchstart', start, { passive: true })
  window.addEventListener('touchmove', move, { passive: true })
  window.addEventListener('touchend', end, { passive: true })
  window.addEventListener('touchcancel', cancel, { passive: true })

  return () => {
    window.removeEventListener('touchstart', start)
    window.removeEventListener('touchmove', move)
    window.removeEventListener('touchend', end)
    window.removeEventListener('touchcancel', cancel)
  }
}

export function SmoothScroll({ children }: { children: ReactNode }) {
  const [lenis, setLenis] = useState<Lenis | null>(null)
  const [enabled, setEnabled] = useState(canSmoothScroll)

  useEffect(() => {
    const motionQuery = window.matchMedia(REDUCED_MOTION)
    const sync = () => setEnabled(!motionQuery.matches)

    sync()
    motionQuery.addEventListener('change', sync)
    return () => {
      motionQuery.removeEventListener('change', sync)
    }
  }, [])

  useEffect(() => {
    if (!enabled) {
      setLenis(null)
      return
    }

    const instance = new Lenis(LENIS_OPTIONS)
    setLenis(instance)
    const detachTouchGlide = attachTouchGlide(instance)

    let frame = 0
    const raf = (time: number) => {
      instance.raf(time)
      frame = window.requestAnimationFrame(raf)
    }
    frame = window.requestAnimationFrame(raf)

    return () => {
      detachTouchGlide()
      window.cancelAnimationFrame(frame)
      instance.destroy()
    }
  }, [enabled])

  return <SmoothScrollContext.Provider value={lenis}>{children}</SmoothScrollContext.Provider>
}

export function useSmoothScroll() {
  return useContext(SmoothScrollContext)
}
