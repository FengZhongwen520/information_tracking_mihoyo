import { useEffect, useState, type ReactNode } from 'react'
import { ImagePlaceholder } from './ImagePlaceholder'

const PROXY_EXACT_HOSTS = new Set(['static.wikia.nocookie.net'])
const PROXY_HOST_SUFFIXES = ['.hoyoverse.com', '.hoyolab.com']

function sizedSrc(src: string, width?: number): string {
  if (!width) return src
  try {
    const host = new URL(src).hostname.toLowerCase()
    if (host === 'static.wikia.nocookie.net') {
      return src.replace(/\/revision\/latest(\?|$)/, `/revision/latest/scale-to-width-down/${width}$1`)
    }
  } catch {
    // Leave the original address untouched when it cannot be parsed.
  }
  return src
}

function resolveImageSrc(src: string, width?: number): string {
  try {
    const target = sizedSrc(src, width)
    const url = new URL(target, window.location.origin)
    if (url.origin === window.location.origin) return src
    const host = url.hostname.toLowerCase()
    const proxied = PROXY_EXACT_HOSTS.has(host)
      || PROXY_HOST_SUFFIXES.some((suffix) => host.endsWith(suffix))
    return proxied ? `/api/images?url=${encodeURIComponent(target)}` : target
  } catch {
    return src
  }
}

interface Props {
  src?: string | null
  alt?: string
  className?: string
  fallback?: ReactNode
  loading?: 'eager' | 'lazy'
  width?: number
}

export function RemoteImage({
  src,
  alt = '',
  className,
  fallback = <ImagePlaceholder compact />,
  loading = 'lazy',
  width,
}: Props) {
  const [stage, setStage] = useState<'proxy' | 'direct' | 'broken'>('proxy')
  const [attempt, setAttempt] = useState(0)
  const directSrc = src ? sizedSrc(src, width) : ''
  const resolved = src ? resolveImageSrc(src, width) : ''
  const canUseDirect = Boolean(src && resolved !== src)
  const proxySrc = attempt > 0
    ? `${resolved}${resolved.includes('?') ? '&' : '?'}retry=${attempt}`
    : resolved

  useEffect(() => {
    setStage('proxy')
    setAttempt(0)
  }, [src])

  if (!src || stage === 'broken') return <>{fallback}</>

  return (
    <img
      key={`${stage}-${attempt}`}
      className={className}
      src={stage === 'direct' ? directSrc : proxySrc}
      alt={alt}
      loading={loading}
      decoding="async"
      draggable={false}
      referrerPolicy="no-referrer"
      onError={() => {
        if (stage === 'proxy' && canUseDirect && attempt === 0) {
          setAttempt(1)
          return
        }
        if (stage === 'proxy' && canUseDirect) {
          setStage('direct')
          return
        }
        setStage('broken')
      }}
    />
  )
}
