import type { Booking } from '../api';
import { CLASS_NAMES, hhmm, rupees } from '../api';

export function StatusBadge({ status, waitlistFrom }: { status: Booking['status']; waitlistFrom: number | null }) {
  const styles = {
    CONFIRMED: 'border-emerald-400/40 bg-emerald-400/10 text-emerald-200',
    WAITLISTED: 'border-amber-400/40 bg-amber-400/10 text-amber-200',
    CANCELLED: 'border-fog-400/40 bg-fog-400/10 text-fog-300',
  }[status];
  return (
    <span className={`chip ${styles}`}>
      {status === 'WAITLISTED' && waitlistFrom ? `WL ${waitlistFrom}` : status}
    </span>
  );
}

interface Props {
  booking: Booking;
  onCancel?: (pnr: string) => void;
  busy?: boolean;
}

export default function BookingCard({ booking: b, onCancel, busy }: Props) {
  return (
    <article className="panel" aria-label={`Booking ${b.pnr}`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="text-xs text-fog-400">PNR</p>
          <p className="font-mono text-lg text-fog-100">{b.pnr}</p>
        </div>
        <StatusBadge status={b.status} waitlistFrom={b.waitlistFrom} />
      </div>
      <p className="mt-2 text-sm text-fog-200">
        {b.train.number} {b.train.name} · {b.train.source} {hhmm(b.train.departs)} → {b.train.destination} {hhmm(b.train.arrives)}
      </p>
      <p className="text-sm text-fog-300">
        {b.journeyDate} · {b.classCode} ({CLASS_NAMES[b.classCode]}) · {rupees(b.fareTotal)}
      </p>
      <ul className="mt-3 space-y-1 text-sm">
        {b.passengers.map((p, i) => (
          <li key={i} className="flex flex-wrap gap-x-3">
            <span className="text-fog-100">{p.name}</span>
            <span className="text-fog-400">
              {p.age} · {p.gender}
            </span>
            <span className="text-fog-300">{b.status === 'CONFIRMED' ? p.seat : b.status === 'WAITLISTED' ? 'Waitlisted' : '—'}</span>
          </li>
        ))}
      </ul>
      {onCancel && b.canCancel && (
        <button type="button" className="btn btn-danger mt-3" disabled={busy} onClick={() => onCancel(b.pnr)}>
          Cancel booking
        </button>
      )}
    </article>
  );
}
