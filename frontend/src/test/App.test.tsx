import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from '../App';

const train = {
  id: 1,
  number: '90601',
  name: 'Garden City Express',
  source: 'MAS',
  destination: 'SBC',
  departs: '06:00:00',
  arrives: '11:30:00',
  arrivalDayOffset: 0,
  durationMinutes: 330,
  runDays: '1111111',
  active: true,
};
const stations = [
  { code: 'MAS', name: 'Chennai Central', city: 'Chennai' },
  { code: 'SBC', name: 'KSR Bengaluru', city: 'Bengaluru' },
];
const results = [{ train, departed: false, classes: [{ code: 'CC', fare: 785, seats: 60, available: 1, waitlisted: 0, bookable: true, label: 'AVAILABLE 1' }] }];
const booking = {
  pnr: '4812345678',
  status: 'WAITLISTED',
  waitlistFrom: 1,
  train,
  journeyDate: '2026-11-02',
  classCode: 'CC',
  fareTotal: 1570,
  createdAt: '',
  passengers: [
    { name: 'Asha', age: 30, gender: 'F', seat: null },
    { name: 'Ravi', age: 31, gender: 'M', seat: null },
  ],
  canCancel: true,
};

let calls: { url: string; init?: RequestInit }[];

beforeEach(() => {
  sessionStorage.clear();
  window.scrollTo = vi.fn() as unknown as typeof window.scrollTo;
  calls = [];
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    calls.push({ url, init });
    const respond = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
    if (url === '/api/stations') return respond(stations);
    if (url === '/api/auth/me') return respond({ user: { id: 1, name: 'Asha', email: 'a@x.in', role: 'PASSENGER' } });
    if (url.startsWith('/api/trains/search')) return respond(results);
    if (url === '/api/auth/login') {
      const body = JSON.parse(String(init!.body));
      return body.password === 'right-password'
        ? respond({ token: 'tok', user: { id: 1, name: 'Asha', email: 'a@x.in', role: 'PASSENGER' } })
        : respond({ detail: 'Incorrect email or password.' }, 401);
    }
    if (url === '/api/bookings' && init?.method === 'POST') return respond(booking, 201);
    return respond({ detail: 'not found' }, 404);
  }));
});

afterEach(() => vi.unstubAllGlobals());

it('searches, requires sign-in, then books two passengers and shows the waitlist number', async () => {
  const user = userEvent.setup();
  render(<App />);
  await screen.findAllByRole('option', { name: /KSR Bengaluru/ });
  await user.click(screen.getByRole('button', { name: 'Search' }));
  const card = await screen.findByRole('article', { name: /90601/ });
  expect(within(card).getByText('AVAILABLE 1')).toBeInTheDocument();

  await user.click(within(card).getByRole('button', { name: /CC/ }));
  await user.type(screen.getByLabelText('Name'), 'Asha');
  await user.click(screen.getByRole('button', { name: /add passenger/i }));
  await user.type(screen.getAllByLabelText('Name')[1], 'Ravi');
  expect(screen.getByText(/will be waitlisted/)).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: /sign in to book/i }));
  const form = await screen.findByRole('form', { name: 'Sign in' });
  await user.type(within(form).getByLabelText('Email'), 'a@x.in');
  await user.type(within(form).getByLabelText(/password/i), 'wrong');
  await user.click(within(form).getByRole('button', { name: 'Sign in' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect email or password.');

  await user.clear(within(form).getByLabelText(/password/i));
  await user.type(within(form).getByLabelText(/password/i), 'right-password');
  await user.click(within(form).getByRole('button', { name: 'Sign in' }));
  await waitFor(() => expect(sessionStorage.getItem('railway-token')).toBe('tok'));
});

it('sends the bearer token and passenger list when booking', async () => {
  sessionStorage.setItem('railway-token', 'tok');
  const user = userEvent.setup();
  render(<App />);
  await screen.findByRole('button', { name: /sign out/i });
  await user.click(screen.getByRole('button', { name: 'Search' }));
  const card = await screen.findByRole('article', { name: /90601/ });
  await user.click(within(card).getByRole('button', { name: /CC/ }));
  await user.type(screen.getByLabelText('Name'), 'Asha');
  await user.click(screen.getByRole('button', { name: /confirm booking/i }));

  expect(await screen.findByText('WL 1')).toBeInTheDocument();
  const post = calls.find((c) => c.url === '/api/bookings')!;
  expect(new Headers(post.init!.headers).get('Authorization')).toBe('Bearer tok');
  expect(JSON.parse(String(post.init!.body))).toMatchObject({ trainId: 1, classCode: 'CC', passengers: [{ name: 'Asha', age: 30, gender: 'F' }] });
});
