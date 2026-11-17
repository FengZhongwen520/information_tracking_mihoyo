import { STATUS_LABEL } from '../lib/format'

export function StatusBadge({
  status,
  completed,
  label,
}: {
  status: string
  completed: boolean
  label?: string
}) {
  if (completed) {
    return (
      <span className="status status-completed">
        <span className="status-dot" />
        已完成
      </span>
    )
  }
  return (
    <span className={`status status-${status}`}>
      <span className="status-dot" />
      {label ?? STATUS_LABEL[status] ?? status}
    </span>
  )
}
