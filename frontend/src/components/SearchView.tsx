import { useEffect, useState, type FormEvent } from 'react';
import { ArrowRight, Loader2, Minus, Plus, Search } from 'lucide-react';
import {
  api,
  CLASS_NAMES,
  duration,
  hhmm,
  rupees,
  type Booking,
  type NewPassenger,
  type SearchResult,
  type Station,
  type User,
} from '../api';

const today = () => new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);
const plusDays = (iso: string, n: number) => {
  const d = new Date(`${iso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
};

interface Props {
  user: User | null;
  onNeedLogin: () => void;
  onBooked: (b: Booking) => void;
}

export default function SearchView({ user, onNeedLogin, onBooked }: Props) {
  const [stations, setStations] = useState<Station[]>([]);
  const [from, setFrom] = useState('MAS');
  const [to, setTo] = useState('SBC');
  const [date, setDate] = useState(plusDays(today(), 1));
  const [results, setResults] = useState<SearchResult[] | null>(null);
  const [selected, setSelected] = useState<{ train: SearchResult; classCode: string } | null>(null);
  const [passengers, setPassengers] = useState<NewPassenger[]>([{ name: '', age: 30, gender: 'F' }]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.stations().then(setStations, (e) => setError(e.message));
  }, []);

  const search = async (e?: FormEvent) => {
    e?.preventDefault();
    setBusy(true);
    setError(null);
    setSelected(null);
    try {
      setResults(await api.search(from, to, date));
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
      setResults(null);
    } finally {
      setBusy(false);
    }
  };

  const book = async (e: FormEvent) => {
    e.preventDefault();
    if (!selected) return;
    if (!user) {
      onNeedLogin();
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const booking = await api.book(selected.train.train.id, date, selected.classCode, passengers);
      setSelected(null);
      setPassengers([{ name: '', age: 30, gender: 'F' }]);
      onBooked(booking);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  const updatePassenger = (i: number, patch: Partial<NewPassenger>) =>
    setPassengers((ps) => ps.map((p, j) => (j === i ? { ...p, ...patch } : p)));

  const cls = selected?.train.classes.find((c) => c.code === selected.classCode);

  return (
    <div className="space-y-4">
      <form onSubmit={search} className="panel grid grid-cols-1 gap-3 sm:grid-cols-[1fr_1fr_1fr_auto] sm:items-end" aria-label="Search trains">
        <label>
          <span className="label">From</span>
          <select className="input" value={from} onChange={(e) => setFrom(e.target.value)}>
            {stations.map((s) => (
              <option key={s.code} value={s.code}>
                {s.name} ({s.code})
              </option>
            ))}
          </select>
        </label>
        <label>
          <span className="label">To</span>
          <select className="input" value={to} onChange={(e) => setTo(e.target.value)}>
            {stations.map((s) => (
              <option key={s.code} value={s.code}>
                {s.name} ({s.code})
              </option>
            ))}
          </select>
        </label>
        <label>
          <span className="label">Journey date</span>
          <input type="date" className="input" value={date} min={today()} max={plusDays(today(), 120)} onChange={(e) => setDate(e.target.value)} required />
        </label>
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy && !selected ? <Loader2 size={16} className="animate-spin" aria-hidden="true" /> : <Search size={16} aria-hidden="true" />}
          Search
        </button>
      </form>

      {error && (
        <p role="alert" className="rounded-xl border border-red-400/40 bg-red-400/10 px-4 py-3 text-sm text-red-200">
          {error}
        </p>
      )}

      {results && results.length === 0 && <p className="panel text-sm text-fog-300">No direct trains run on this route on the chosen day.</p>}

      {results?.map((r) => (
        <article key={r.train.id} className="panel" aria-label={`${r.train.number} ${r.train.name}`}>
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h3 className="font-semibold text-fog-100">
              {r.train.number} · {r.train.name}
            </h3>
            <span className="text-xs text-fog-400">Runs {r.train.runDays.split('').map((d, i) => (d === '1' ? 'MTWTFSS'[i] : '·')).join(' ')}</span>
          </div>
          <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-fog-300">
            <span className="font-medium text-fog-100">{hhmm(r.train.departs)}</span> {r.train.source}
            <ArrowRight size={14} aria-hidden="true" />
            <span className="font-medium text-fog-100">{hhmm(r.train.arrives)}</span> {r.train.destination}
            {r.train.arrivalDayOffset > 0 && <span className="text-xs text-amber-200">+{r.train.arrivalDayOffset} day</span>}
            <span className="text-xs text-fog-400">({duration(r.train.durationMinutes)})</span>
          </p>
          {r.departed && <p className="mt-2 text-sm text-amber-200">This train has already departed today.</p>}
          <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-4">
            {r.classes.map((c) => {
              const active = selected?.train.train.id === r.train.id && selected.classCode === c.code;
              return (
                <button
                  key={c.code}
                  type="button"
                  disabled={!c.bookable}
                  onClick={() => setSelected({ train: r, classCode: c.code })}
                  aria-pressed={active}
                  className={`rounded-xl border p-3 text-left text-sm disabled:cursor-not-allowed disabled:opacity-50 ${
                    active ? 'border-accent-400 bg-accent-400/10' : 'border-ink-700 hover:bg-ink-800'
                  }`}
                >
                  <span className="block font-medium text-fog-100">
                    {c.code} <span className="text-xs font-normal text-fog-400">{CLASS_NAMES[c.code]}</span>
                  </span>
                  <span className={`block text-xs ${c.available > 0 ? 'text-emerald-300' : 'text-amber-200'}`}>{c.label}</span>
                  <span className="block text-xs text-fog-300">{rupees(c.fare)} / passenger</span>
                </button>
              );
            })}
          </div>

          {isSelectedTrain(selected, r) && cls && (
            <form onSubmit={book} className="mt-4 space-y-3 border-t border-ink-700 pt-4" aria-label="Passenger details">
              <h4 className="text-sm font-semibold text-fog-100">
                Passengers · {cls.code} · {date}
              </h4>
              {passengers.map((p, i) => (
                <fieldset key={i} className="grid grid-cols-1 gap-2 sm:grid-cols-[2fr_1fr_1fr_auto] sm:items-end">
                  <legend className="sr-only">Passenger {i + 1}</legend>
                  <label>
                    <span className="label">Name</span>
                    <input className="input" value={p.name} maxLength={80} required onChange={(e) => updatePassenger(i, { name: e.target.value })} />
                  </label>
                  <label>
                    <span className="label">Age</span>
                    <input type="number" className="input" min={1} max={120} value={p.age} required onChange={(e) => updatePassenger(i, { age: Number(e.target.value) })} />
                  </label>
                  <label>
                    <span className="label">Gender</span>
                    <select className="input" value={p.gender} onChange={(e) => updatePassenger(i, { gender: e.target.value as NewPassenger['gender'] })}>
                      <option value="F">Female</option>
                      <option value="M">Male</option>
                      <option value="O">Other</option>
                    </select>
                  </label>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    aria-label={`Remove passenger ${i + 1}`}
                    disabled={passengers.length === 1}
                    onClick={() => setPassengers((ps) => ps.filter((_, j) => j !== i))}
                  >
                    <Minus size={14} aria-hidden="true" />
                  </button>
                </fieldset>
              ))}
              <div className="flex flex-wrap items-center gap-3">
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled={passengers.length >= 6}
                  onClick={() => setPassengers((ps) => [...ps, { name: '', age: 30, gender: 'F' }])}
                >
                  <Plus size={14} aria-hidden="true" /> Add passenger
                </button>
                <span className="text-sm text-fog-300">
                  Total {rupees(cls.fare * passengers.length)}
                  {cls.available < passengers.length && <span className="ml-2 text-amber-200">— will be waitlisted</span>}
                </span>
                <button type="submit" className="btn btn-primary ml-auto" disabled={busy}>
                  {busy && <Loader2 size={16} className="animate-spin" aria-hidden="true" />}
                  {user ? 'Confirm booking' : 'Sign in to book'}
                </button>
              </div>
            </form>
          )}
        </article>
      ))}
    </div>
  );
}

function isSelectedTrain(selected: { train: SearchResult } | null, r: SearchResult) {
  return selected?.train.train.id === r.train.id;
}
