interface Props {
  className?: string
  compact?: boolean
}

export function ImagePlaceholder({ className = '', compact = false }: Props) {
  return (
    <span
      className={`image-placeholder ${compact ? 'is-compact' : ''} ${className}`.trim()}
      role="img"
      aria-label="图片暂时无法显示"
    >
      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <rect x="3.5" y="5" width="17" height="14" rx="2.5" stroke="currentColor" strokeWidth="1.5" />
        <circle cx="9" cy="10" r="1.6" fill="currentColor" />
        <path
          d="m5 17 4.1-4.1a1.5 1.5 0 0 1 2.1 0l3.1 3.1m-1.7-1.7 1.7-1.7a1.5 1.5 0 0 1 2.1 0L20 16"
          stroke="currentColor"
          strokeWidth="1.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      {!compact && <span>图片暂时无法显示</span>}
    </span>
  )
}
