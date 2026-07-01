import { TIME_FMTS, useTimeFmt, setTimeFmt } from '../timeFormat'
import type { TimeFmt } from '../timeFormat'

/** Small dropdown to choose how timestamps are shown (applies everywhere; persisted). */
export function TimeFormatSelect() {
  const fmt = useTimeFmt()
  return (
    <select
      className="time-fmt"
      value={fmt}
      onChange={(e) => setTimeFmt(e.target.value as TimeFmt)}
      title="Timestamp format"
      onClick={(e) => e.stopPropagation()}
    >
      {TIME_FMTS.map((f) => <option key={f} value={f}>{f}</option>)}
    </select>
  )
}
