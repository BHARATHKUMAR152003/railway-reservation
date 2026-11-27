export interface Station {
  code: string;
  name: string;
  city: string;
}

export interface User {
  id: number;
  name: string;
  email: string;
  role: 'PASSENGER' | 'ADMIN';
}

export interface TrainSummary {
  id: number;
  number: string;
  name: string;
  source: string;
  destination: string;
  departs: string;
  arrives: string;
  arrivalDayOffset: number;
  durationMinutes: number;
  runDays: string;
  active: boolean;
}

export interface ClassAvailability {
  code: string;
  fare: number;
  seats: number;
  available: number;
  waitlisted: number;
  bookable: boolean;
  label: string;
}

export interface SearchResult {
  train: TrainSummary;
  classes: ClassAvailability[];
  departed: boolean;
}

export interface Booking {
  pnr: string;
  status: 'CONFIRMED' | 'WAITLISTED' | 'CANCELLED';
  waitlistFrom: number | null;
  train: TrainSummary;
  journeyDate: string;
  classCode: string;
  fareTotal: number;
  createdAt: string;
  passengers: { name: string; age: number; gender: string; seat: string | null }[];
  canCancel: boolean;
}

export interface PnrStatus {
  pnr: string;
  status: Booking['status'];
  waitlistFrom: number | null;
  train: TrainSummary;
  journeyDate: string;
  classCode: string;
  passengers: number;
  seats: string[];
}

export interface NewPassenger {
  name: string;
  age: number;
  gender: 'M' | 'F' | 'O';
}

export interface TrainDetail {
  train: TrainSummary;
  classes: { code: string; seats: number; fare: number }[];
}

export interface AdminSummary {
  trains: number;
  activeTrains: number;
  stations: number;
  bookings: number;
  confirmed: number;
  waitlisted: number;
  cancelled: number;
  revenue: number;
  occupancyNext7Days: { trainId: number; number: string; name: string; date: string; confirmed: number; capacity: number }[];
}

export interface NewTrain {
  number: string;
  name: string;
  source: string;
  destination: string;
  departs: string;
  durationMinutes: number;
  runDays: string;
  classes: { code: string; seats: number; fare: number }[];
}

const BASE = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '');
const TOKEN_KEY = 'railway-token';

export const session = {
  get: () => sessionStorage.getItem(TOKEN_KEY),
  set: (t: string) => sessionStorage.setItem(TOKEN_KEY, t),
  clear: () => sessionStorage.removeItem(TOKEN_KEY),
};

export class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

async function call<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const token = session.get();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (init.body) headers.set('Content-Type', 'application/json');
  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, { ...init, headers });
  } catch {
    throw new ApiError('Cannot reach the API. Is the backend running on port 8114?', 0);
  }
  if (!res.ok) {
    let msg = `Request failed (${res.status})`;
    try {
      const body = await res.json();
      if (typeof body.detail === 'string') msg = body.detail;
    } catch {
      /* non-JSON error body */
    }
    if (res.status === 401) session.clear();
    throw new ApiError(msg, res.status);
  }
  return (res.status === 204 ? undefined : await res.json()) as T;
}

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });

export const api = {
  stations: () => call<Station[]>('/api/stations'),
  search: (from: string, to: string, date: string) =>
    call<SearchResult[]>(`/api/trains/search?${new URLSearchParams({ from, to, date })}`),
  register: (name: string, email: string, password: string) =>
    call<{ token: string; user: User }>('/api/auth/register', json('POST', { name, email, password })),
  login: (email: string, password: string) => call<{ token: string; user: User }>('/api/auth/login', json('POST', { email, password })),
  me: () => call<{ user: User }>('/api/auth/me'),
  logout: () => call<void>('/api/auth/logout', { method: 'POST' }),
  book: (trainId: number, journeyDate: string, classCode: string, passengers: NewPassenger[]) =>
    call<Booking>('/api/bookings', json('POST', { trainId, journeyDate, classCode, passengers })),
  myBookings: () => call<Booking[]>('/api/bookings'),
  cancel: (pnr: string) => call<Booking>(`/api/bookings/${pnr}/cancel`, { method: 'POST' }),
  pnr: (pnr: string) => call<PnrStatus>(`/api/pnr/${encodeURIComponent(pnr)}`),
  admin: {
    summary: () => call<AdminSummary>('/api/admin/summary'),
    trains: () => call<TrainDetail[]>('/api/admin/trains'),
    createTrain: (t: NewTrain) => call<TrainDetail>('/api/admin/trains', json('POST', t)),
    setActive: (id: number, active: boolean) => call<TrainDetail>(`/api/admin/trains/${id}/active`, json('PUT', { active })),
    bookings: (date?: string) => call<Booking[]>(`/api/admin/bookings${date ? `?date=${date}` : ''}`),
  },
};

export const CLASS_NAMES: Record<string, string> = {
  '1A': 'First AC',
  '2A': 'AC 2-tier',
  '3A': 'AC 3-tier',
  SL: 'Sleeper',
  CC: 'AC Chair Car',
  '2S': 'Second Sitting',
};

export const hhmm = (t: string) => t.slice(0, 5);
export const duration = (m: number) => `${Math.floor(m / 60)}h ${String(m % 60).padStart(2, '0')}m`;
export const rupees = (v: number) => `₹${Number(v).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;
