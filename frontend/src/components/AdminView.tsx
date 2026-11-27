import { useEffect, useState, type FormEvent } from 'react';
import { BarChart3, Loader2, Plus } from 'lucide-react';
import { api, hhmm, rupees, type AdminSummary, type Booking, type NewTrain, type Station, type TrainDetail } from '../api';
import { StatusBadge } from './BookingCard';

const EMPTY: NewTrain = {
  number: '',
  name: '',
  source: 'MAS',
  destination: 'SBC',
  departs: '09:00',
  durationMinutes: 300,
  runDays: '1111111',
  classes: [{ code: 'CC', seats: 60, fare: 750 }],
};

export default function AdminView() {
  const [summary, setSummary] = useState<AdminSummary | null>(null);
  const [trains, setTrains] = useState<TrainDetail[]>([]);
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [stations, setStations] = useState<Station[]>([]);
  const [form, setForm] = useState<NewTrain>(EMPTY);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = async () => {
    try {
      const [s, t, b, st] = await Promise.all([api.admin.summary(), api.admin.trains(), api.admin.bookings(), api.stations()]);
      setSummary(s);
      setTrains(t);
      setBookings(b);
      setStations(st);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const create = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const t = await api.admin.createTrain(form);
      setNotice(`Train ${t.train.number} created.`);
      setForm(EMPTY);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  const toggle = async (t: TrainDetail) => {
    try {
      await api.admin.setActive(t.train.id, !t.train.active);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  const cls = form.classes[0];
  const stats = summary
    ? [
        ['Trains', `${summary.activeTrains} / ${summary.trains} active`],
        ['Bookings', summary.bookings],
        ['Confirmed', summary.confirmed],
        ['Waitlisted', summary.waitlisted],
        ['Cancelled', summary.cancelled],
        ['Revenue (confirmed)', rupees(summary.revenue)],
      ]
    : [];

  return (
    <div className="space-y-4">
      {error && (
        <p role="alert" className="rounded-xl border border-red-400/40 bg-red-400/10 px-4 py-3 text-sm text-red-200">
          {error}
        </p>
      )}
      {notice && (
        <p role="status" className="rounded-xl border border-emerald-400/40 bg-emerald-400/10 px-4 py-3 text-sm text-emerald-200">
          {notice}
        </p>
      )}

      <section className="panel" aria-label="Summary">
        <h2 className="panel-title flex items-center gap-2">
          <BarChart3 size={16} aria-hidden="true" /> Overview
        </h2>
        <dl className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          {stats.map(([k, v]) => (
            <div key={k} className="rounded-xl border border-ink-700 p-3">
              <dt className="text-xs text-fog-400">{k}</dt>
              <dd className="mt-1 text-lg font-semibold text-fog-100">{v}</dd>
            </div>
          ))}
        </dl>
        {summary && summary.occupancyNext7Days.length > 0 && (
          <div className="mt-4">
            <h3 className="text-xs font-medium text-fog-300">Occupancy, next 7 days</h3>
            <ul className="mt-2 space-y-1.5">
              {summary.occupancyNext7Days.map((o) => {
                const pct = Math.round((o.confirmed / o.capacity) * 100);
                return (
                  <li key={`${o.trainId}-${o.date}`} className="grid grid-cols-[minmax(0,1fr)_120px_48px] items-center gap-2 text-xs">
                    <span className="truncate">
                      {o.date} · {o.number} {o.name}
                    </span>
                    <span className="h-2 rounded bg-ink-700" aria-hidden="true">
                      <span className="block h-2 rounded bg-accent-400" style={{ width: `${Math.min(100, pct)}%` }} />
                    </span>
                    <span className="text-right">{pct}%</span>
                  </li>
                );
              })}
            </ul>
          </div>
        )}
      </section>

      <section className="panel" aria-label="Trains">
        <h2 className="panel-title">Trains</h2>
        <div className="mt-3 overflow-x-auto">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead className="text-xs text-fog-400">
              <tr>
                <th className="py-2 pr-2 font-medium">Train</th>
                <th className="py-2 pr-2 font-medium">Route</th>
                <th className="py-2 pr-2 font-medium">Classes</th>
                <th className="py-2 pr-2 font-medium">Days</th>
                <th className="py-2 font-medium">Status</th>
              </tr>
            </thead>
            <tbody>
              {trains.map((t) => (
                <tr key={t.train.id} className="border-t border-ink-700">
                  <td className="py-2 pr-2">
                    {t.train.number} {t.train.name}
                  </td>
                  <td className="py-2 pr-2">
                    {t.train.source} {hhmm(t.train.departs)} → {t.train.destination} {hhmm(t.train.arrives)}
                  </td>
                  <td className="py-2 pr-2">{t.classes.map((c) => `${c.code}×${c.seats}`).join(', ')}</td>
                  <td className="py-2 pr-2 font-mono text-xs">{t.train.runDays}</td>
                  <td className="py-2">
                    <button type="button" className="btn btn-secondary min-h-8 px-3 text-xs" onClick={() => void toggle(t)}>
                      {t.train.active ? 'Active — deactivate' : 'Inactive — activate'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <form onSubmit={create} className="panel space-y-3" aria-label="Add train">
        <h2 className="panel-title flex items-center gap-2">
          <Plus size={16} aria-hidden="true" /> Add train
        </h2>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <label>
            <span className="label">Number (5 digits)</span>
            <input className="input" pattern="\d{5}" required value={form.number} onChange={(e) => setForm({ ...form, number: e.target.value })} />
          </label>
          <label>
            <span className="label">Name</span>
            <input className="input" required maxLength={80} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </label>
          <label>
            <span className="label">From</span>
            <select className="input" value={form.source} onChange={(e) => setForm({ ...form, source: e.target.value })}>
              {stations.map((s) => (
                <option key={s.code} value={s.code}>
                  {s.code} — {s.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span className="label">To</span>
            <select className="input" value={form.destination} onChange={(e) => setForm({ ...form, destination: e.target.value })}>
              {stations.map((s) => (
                <option key={s.code} value={s.code}>
                  {s.code} — {s.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span className="label">Departs</span>
            <input type="time" className="input" required value={form.departs} onChange={(e) => setForm({ ...form, departs: e.target.value })} />
          </label>
          <label>
            <span className="label">Duration (minutes)</span>
            <input type="number" className="input" min={10} max={4320} value={form.durationMinutes} onChange={(e) => setForm({ ...form, durationMinutes: Number(e.target.value) })} />
          </label>
          <label>
            <span className="label">Run days (Mon→Sun, 1 = runs)</span>
            <input className="input font-mono" pattern="[01]{7}" required value={form.runDays} onChange={(e) => setForm({ ...form, runDays: e.target.value })} />
          </label>
          <div className="grid grid-cols-3 gap-2">
            <label>
              <span className="label">Class</span>
              <select className="input" value={cls.code} onChange={(e) => setForm({ ...form, classes: [{ ...cls, code: e.target.value }] })}>
                {['1A', '2A', '3A', 'SL', 'CC', '2S'].map((c) => (
                  <option key={c}>{c}</option>
                ))}
              </select>
            </label>
            <label>
              <span className="label">Seats</span>
              <input type="number" className="input" min={1} max={500} value={cls.seats} onChange={(e) => setForm({ ...form, classes: [{ ...cls, seats: Number(e.target.value) }] })} />
            </label>
            <label>
              <span className="label">Fare ₹</span>
              <input type="number" className="input" min={1} value={cls.fare} onChange={(e) => setForm({ ...form, classes: [{ ...cls, fare: Number(e.target.value) }] })} />
            </label>
          </div>
        </div>
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy && <Loader2 size={16} className="animate-spin" aria-hidden="true" />} Create train
        </button>
      </form>

      <section className="panel" aria-label="Recent bookings">
        <h2 className="panel-title">Recent bookings</h2>
        {bookings.length === 0 ? (
          <p className="mt-2 text-sm text-fog-400">No bookings yet.</p>
        ) : (
          <div className="mt-3 overflow-x-auto">
            <table className="w-full min-w-[600px] text-left text-sm">
              <thead className="text-xs text-fog-400">
                <tr>
                  <th className="py-2 pr-2 font-medium">PNR</th>
                  <th className="py-2 pr-2 font-medium">Train</th>
                  <th className="py-2 pr-2 font-medium">Date</th>
                  <th className="py-2 pr-2 font-medium">Class</th>
                  <th className="py-2 pr-2 font-medium">Pax</th>
                  <th className="py-2 pr-2 font-medium">Fare</th>
                  <th className="py-2 font-medium">Status</th>
                </tr>
              </thead>
              <tbody>
                {bookings.map((b) => (
                  <tr key={b.pnr} className="border-t border-ink-700">
                    <td className="py-2 pr-2 font-mono">{b.pnr}</td>
                    <td className="py-2 pr-2">{b.train.number}</td>
                    <td className="py-2 pr-2">{b.journeyDate}</td>
                    <td className="py-2 pr-2">{b.classCode}</td>
                    <td className="py-2 pr-2">{b.passengers.length}</td>
                    <td className="py-2 pr-2">{rupees(b.fareTotal)}</td>
                    <td className="py-2">
                      <StatusBadge status={b.status} waitlistFrom={b.waitlistFrom} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
