import { useLayoutEffect } from 'react'

const REVEAL_SELECTOR = '[data-reveal]'
const INSTANT_ZONE = 0.92
const STAGGER_MS = 38
const STAGGER_CAP = 4

export function useReveal(dependencies: readonly unknown[]) {
  useLayoutEffect(() => {
    const nodes = Array.from(document.querySelectorAll<HTMLElement>(REVEAL_SELECTOR))
    if (nodes.length === 0) return

    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      for (const node of nodes) node.dataset.reveal = 'instant'
      return
    }

    const pending: HTMLElement[] = []
    const viewportLimit = window.innerHeight * INSTANT_ZONE

    for (const node of nodes) {
      if (node.dataset.reveal === 'in' || node.dataset.reveal === 'instant') continue

      const siblings = node.parentElement
        ? Array.from(node.parentElement.children).filter(
            (child) => child instanceof HTMLElement && child.hasAttribute('data-reveal'),
          )
        : []
      const index = Math.max(0, siblings.indexOf(node))
      node.style.setProperty('--reveal-delay', `${Math.min(index, STAGGER_CAP) * STAGGER_MS}ms`)

      const { top } = node.getBoundingClientRect()
      if (top < viewportLimit) {
        node.dataset.reveal = node.dataset.reveal === 'out' ? 'in' : 'instant'
        continue
      }

      node.dataset.reveal = 'out'
      pending.push(node)
    }

    if (pending.length === 0) return

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue
          const node = entry.target as HTMLElement
          node.dataset.reveal = 'in'
          observer.unobserve(node)
        }
      },
      { rootMargin: '0px 0px -10% 0px', threshold: 0.08 },
    )

    for (const node of pending) observer.observe(node)
    return () => observer.disconnect()
  }, dependencies)
}
