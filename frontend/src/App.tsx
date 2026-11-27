import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { LogIn, LogOut, TrainFront } from 'lucide-react';
import { api, hhmm, session, type Booking, type PnrStatus, type User } from './api';
import AdminView from './components/AdminView';
import BookingCard, { StatusBadge } from './components/BookingCard';
import SearchView from './components/SearchView';

type Tab = 'search' | 'bookings' | 'pnr' | 'admin' | 'account';

function AuthForm({ onSignedIn }: { onSignedIn: (u: User) => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = mode === 'login' ? await api.login(email, password) : await api.register(name, email, password);
      session.set(res.token);
      onSignedIn(res.user);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="panel mx-auto max-w-md space-y-3" aria-label={mode === 'login' ? 'Sign in' : 'Create account'}>
      <h2 className="panel-title">{mode === 'login' ? 'Sign in' : 'Create an account'}</h2>
      {mode === 'register' && (
        <label className="block">
          <span className="label">Name</span>
          <input className="input" required maxLength={80} value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" />
        </label>
      )}
      <label className="block">
        <span className="label">Email</span>
        <input type="email" className="input" required value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
      </label>
      <label className="block">
        <span className="label">Password {mode === 'register' && '(min. 8 characters)'}</span>
        <input
          type="password"
          className="input"
          required
          minLength={mode === 'register' ? 8 : 1}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
        />
      </label>
      {error && (
        <p role="alert" className="text-sm text-red-300">
          {error}
        </p>
      )}
      <button type="submit" className="btn btn-primary w-full" disabled={busy}>
        {mode === 'login' ? 'Sign in' : 'Create account'}
      </button>
      <button type="button" className="w-full text-sm text-accent-300 hover:underline" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
        {mode === 'login' ? 'New here? Create an account' : 'Already registered? Sign in'}
      </button>
    </form>
  );
}

function PnrView() {
  const [pnr, setPnr] = useState('');
  const [result, setResult] = useState<PnrStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  const check = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setResult(null);
    try {
      setResult(await api.pnr(pnr.trim()));
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  return (
    <div className="mx-auto max-w-xl space-y-4">
      <form onSubmit={check} className="panel flex flex-wrap items-end gap-3" aria-label="PNR enquiry">
        <label className="min-w-0 flex-1">
          <span className="label">10-digit PNR</span>
          <input className="input font-mono" inputMode="numeric" pattern="\d{10}" required value={pnr} onChange={(e) => setPnr(e.target.value)} />
        </label>
        <button type="submit" className="btn btn-primary">
          Check status
        </button>
      </form>
      {error && (
        <p role="alert" className="text-sm text-red-300">
          {error}
        </p>
      )}
      {result && (
        <section className="panel space-y-1 text-sm" aria-label="PNR status">
          <div className="flex items-center justify-between">
            <span className="font-mono text-lg text-fog-100">{result.pnr}</span>
            <StatusBadge status={result.status} waitlistFrom={result.waitlistFrom} />
          </div>
          <p>
            {result.train.number} {result.train.name} · {result.train.source} {hhmm(result.train.departs)} → {result.train.destination}
          </p>
          <p className="text-fog-300">
            {result.journeyDate} · {result.classCode} · {result.passengers} passenger{result.passengers > 1 ? 's' : ''}
          </p>
          {result.seats.length > 0 && <p className="text-fog-300">Seats: {result.seats.join(', ')}</p>}
        </section>
      )}
    </div>
  );
}

export default function App() {
  const [tab, setTab] = useState<Tab>('search');
  const [user, setUser] = useState<User | null>(null);
  const [mine, setMine] = useState<Booking[]>([]);
  const [lastBooked, setLastBooked] = useState<Booking | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (session.get()) api.me().then((r) => setUser(r.user), () => session.clear());
  }, []);

  const loadMine = useCallback(async () => {
    try {
      setMine(await api.myBookings());
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    if (tab === 'bookings' && user) void loadMine();
  }, [tab, user, loadMine]);

  const cancel = async (pnr: string) => {
    if (!window.confirm(`Cancel booking ${pnr}?`)) return;
    setBusy(true);
    setError(null);
    try {
      await api.cancel(pnr);
      await loadMine();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const logout = async () => {
    await api.logout().catch(() => undefined);
    session.clear();
    setUser(null);
    setTab('search');
  };

  const tabs: [Tab, string][] = [
    ['search', 'Book tickets'],
    ['bookings', 'My bookings'],
    ['pnr', 'PNR status'],
    ...(user?.role === 'ADMIN' ? ([['admin', 'Admin']] as [Tab, string][]) : []),
  ];

  return (
    <div className="mx-auto max-w-6xl px-4 py-6 sm:px-6">
      <header className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <span className="grid h-10 w-10 place-items-center rounded-xl bg-accent-400/15 text-accent-300">
            <TrainFront size={22} aria-hidden="true" />
          </span>
          <div>
            <h1 className="text-xl font-semibold text-fog-100">Railway Reservation System</h1>
            <p className="text-xs text-fog-400">Java · Spring Boot · JDBC · MySQL — sample timetable, no real tickets</p>
          </div>
        </div>
        {user ? (
          <div className="flex items-center gap-3 text-sm">
            <span>
              {user.name} {user.role === 'ADMIN' && <span className="chip border-accent-400/50 text-accent-300">admin</span>}
            </span>
            <button type="button" className="btn btn-secondary" onClick={() => void logout()}>
              <LogOut size={15} aria-hidden="true" /> Sign out
            </button>
          </div>
        ) : (
          <button type="button" className="btn btn-secondary" onClick={() => setTab('account')}>
            <LogIn size={15} aria-hidden="true" /> Sign in
          </button>
        )}
      </header>

      <nav className="mb-4 flex flex-wrap gap-2" aria-label="Sections">
        {tabs.map(([key, label]) => (
          <button
            key={key}
            type="button"
            aria-current={tab === key ? 'page' : undefined}
            onClick={() => setTab(key)}
            className={`btn ${tab === key ? 'btn-primary' : 'btn-secondary'}`}
          >
            {label}
          </button>
        ))}
      </nav>

      {error && (
        <p role="alert" className="mb-4 rounded-xl border border-red-400/40 bg-red-400/10 px-4 py-3 text-sm text-red-200">
          {error}
        </p>
      )}

      <main>
        {tab === 'search' && (
          <div className="space-y-4">
            {lastBooked && (
              <div role="status" className="space-y-2">
                <p className="text-sm text-emerald-200">Booking created.</p>
                <BookingCard booking={lastBooked} />
              </div>
            )}
            <SearchView
              user={user}
              onNeedLogin={() => setTab('account')}
              onBooked={(b) => {
                setLastBooked(b);
                window.scrollTo(0, 0);
              }}
            />
          </div>
        )}
        {tab === 'bookings' &&
          (user ? (
            mine.length === 0 ? (
              <p className="panel text-sm text-fog-300">No bookings yet.</p>
            ) : (
              <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                {mine.map((b) => (
                  <BookingCard key={b.pnr} booking={b} onCancel={cancel} busy={busy} />
                ))}
              </div>
            )
          ) : (
            <AuthForm onSignedIn={setUser} />
          ))}
        {tab === 'pnr' && <PnrView />}
        {tab === 'admin' && user?.role === 'ADMIN' && <AdminView />}
        {tab === 'account' &&
          (user ? (
            <p className="panel text-sm">Signed in as {user.email}.</p>
          ) : (
            <AuthForm
              onSignedIn={(u) => {
                setUser(u);
                setTab('search');
              }}
            />
          ))}
      </main>
    </div>
  );
}
