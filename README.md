# Railway Reservation System

Search trains, book seats (with a waitlist when a class is full), cancel, check PNR status, and manage the timetable as an
administrator. Rebuilt from the Java/JDBC/MySQL project on my resume.

| Part | Port | Tech |
| --- | --- | --- |
| Frontend | 5114 | React 18, TypeScript, Vite, Tailwind CSS |
| Backend | 8114 | Java 21, Spring Boot 3 (web, validation), **JDBC via `JdbcTemplate`** (hand-written SQL, no ORM), MySQL 8 |

## Design

- **Layers**: `web` (REST controllers + validated request records) → `service` (booking rules, auth, admin) →
  `dao` (one DAO per aggregate with plain SQL) → MySQL. Domain types are immutable records in `model`.
- **Seat allocation**: inside a transaction the train-class row is locked with `SELECT … FOR UPDATE`, taken seats are
  read and the lowest free seat numbers are assigned. Transactions run at READ COMMITTED so the seat read after the
  lock sees bookings committed by the previous holder (MySQL's default REPEATABLE READ would read a stale snapshot).
  A 12-thread test confirms seats are never double-booked.
- **Waitlist**: if not every passenger fits, the whole booking is waitlisted (max 20 waitlisted passengers per
  train/date/class). Cancelling a confirmed booking promotes waitlisted bookings **strictly in queue order** while whole
  bookings fit. Waitlist positions are computed, not stored, so they never drift.
- **Rules**: journey date within 120 days, train must run on that weekday (Monday-first `1111100` mask), no booking or
  cancellation after departure, 1–6 passengers.
- **Auth**: BCrypt passwords; opaque bearer tokens (only their SHA-256 hash is stored, 12 h expiry); 5 failed logins
  lock an email for 5 minutes; unknown emails still run a BCrypt check so response time does not reveal which accounts
  exist. Roles: `PASSENGER`, `ADMIN`.
- **PNR enquiry** is public but returns no names or account data.
- Errors are always `{"detail": "..."}`; unexpected exceptions are logged and return a generic 500.

The seeded timetable (7 stations, 12 trains) uses **fictional train numbers and timings**; it is not real schedule data
and no real tickets are sold.

## Run locally

Prerequisites: JDK 21, Maven 3.9, MySQL 8.

```sql
-- as a MySQL admin (see db/create-database.sql; use your own password)
CREATE DATABASE railway CHARACTER SET utf8mb4;
CREATE USER 'rail_app'@'localhost' IDENTIFIED BY '...';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX, REFERENCES ON railway.* TO 'rail_app'@'localhost';
```

```bash
cd backend
cp config.example.properties config/application.properties   # git-ignored; set DB password + admin login
mvn package                     # runs the tests (H2 in MySQL mode) and builds target/railway-reservation.jar
java -jar target/railway-reservation.jar   # http://127.0.0.1:8114 — creates tables, seeds timetable + admin
```

```bash
cd frontend
npm install
npm run dev        # http://localhost:5114
```

Configuration (env var or `config/application.properties`): `DB_URL`, `DB_USER`, `DB_PASSWORD`, `PORT`, `HOST`,
`CORS_ORIGINS`, `RAIL_ADMIN_EMAIL`, `RAIL_ADMIN_PASSWORD` (≥ 10 chars; admin is created once), `RAIL_SEED`.

Behind a TLS-inspecting proxy Maven may fail with certificate errors; on Windows use
`MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`.

## API

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| GET | `/api/stations` | – | Stations |
| GET | `/api/trains/search?from=MAS&to=SBC&date=YYYY-MM-DD` | – | Trains running that day with per-class availability / WL |
| POST | `/api/auth/register`, `/api/auth/login` | – | Returns `{token, user}` |
| GET / POST | `/api/auth/me`, `/api/auth/logout` | user | |
| POST | `/api/bookings` | user | `{trainId, journeyDate, classCode, passengers[]}` |
| GET | `/api/bookings` | user | Own bookings |
| POST | `/api/bookings/{pnr}/cancel` | owner/admin | Cancels and promotes the waitlist |
| GET | `/api/pnr/{pnr}` | – | Status without personal data |
| GET | `/api/admin/summary`, `/api/admin/trains`, `/api/admin/bookings` | admin | Dashboard data |
| POST / PUT | `/api/admin/trains`, `/api/admin/trains/{id}/active`, `/api/admin/stations` | admin | Timetable management |

## Tests

```bash
cd backend && mvn test              # 8 tests: API flows, auth, waitlist promotion, validation, admin, concurrency
cd frontend && npx vitest run && npm run build
```

Also verified manually against MySQL 8.4: schema creation, seeding, register → search → book → PNR.

## Deployment

- **Database**: a managed MySQL 8 instance; create the database and user as above.
- **Backend**: any host that runs Java 21 (Render, Railway, Azure App Service, a VM). Build with `mvn package` and run
  `java -jar target/railway-reservation.jar` with `HOST=0.0.0.0`, `PORT`, `DB_URL`, `DB_USER`, `DB_PASSWORD`,
  `CORS_ORIGINS=<frontend URL>`, `RAIL_ADMIN_EMAIL`, `RAIL_ADMIN_PASSWORD` set as environment variables.
- **Frontend**: static site — build with `VITE_API_URL=<backend URL> npm run build` and publish `frontend/dist`.

## Limitations

- Direct trains only (no intermediate stops or quotas such as Tatkal/ladies); one fare per class.
- Payments are not simulated — a booking is confirmed or waitlisted immediately.
