package com.portfolio.railway.dao;

import com.portfolio.railway.model.Models;
import com.portfolio.railway.model.Models.Booking;
import com.portfolio.railway.model.Models.Passenger;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class BookingDao {
    private static final String COLUMNS =
            "b.id, b.pnr, b.user_id, b.train_id, b.journey_date, b.class_code, b.status, b.fare_total, b.created_at";

    private final JdbcTemplate jdbc;

    public BookingDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private List<Booking> load(String where, Object... args) {
        List<Booking> bare = jdbc.query("SELECT " + COLUMNS + " FROM bookings b " + where,
                (rs, i) -> new Booking(rs.getInt("id"), rs.getString("pnr"), rs.getInt("user_id"), rs.getInt("train_id"),
                        rs.getDate("journey_date").toLocalDate(), rs.getString("class_code"), rs.getString("status"),
                        rs.getBigDecimal("fare_total"), rs.getTimestamp("created_at").toInstant(), List.of()), args);
        if (bare.isEmpty()) {
            return bare;
        }
        String ids = bare.stream().map(b -> String.valueOf(b.id())).collect(Collectors.joining(","));
        Map<Integer, List<Passenger>> passengers = jdbc.query(
                "SELECT id, booking_id, name, age, gender, seat_no FROM passengers WHERE booking_id IN (" + ids + ") ORDER BY id",
                (rs, i) -> Map.entry(rs.getInt("booking_id"), new Passenger(rs.getInt("id"), rs.getString("name"),
                        rs.getInt("age"), rs.getString("gender"), (Integer) rs.getObject("seat_no"))))
                .stream().collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        return bare.stream().map(b -> new Booking(b.id(), b.pnr(), b.userId(), b.trainId(), b.journeyDate(), b.classCode(),
                b.status(), b.fareTotal(), b.createdAt(), passengers.getOrDefault(b.id(), List.of()))).toList();
    }

    public Optional<Booking> findByPnr(String pnr) {
        return load("WHERE b.pnr = ?", pnr).stream().findFirst();
    }

    public List<Booking> findByUser(int userId) {
        return load("WHERE b.user_id = ? ORDER BY b.journey_date DESC, b.id DESC", userId);
    }

    public List<Booking> search(LocalDate date, Integer trainId, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1 = 1");
        if (date != null) {
            where.append(" AND b.journey_date = ?");
            args.add(Date.valueOf(date));
        }
        if (trainId != null) {
            where.append(" AND b.train_id = ?");
            args.add(trainId);
        }
        where.append(" ORDER BY b.created_at DESC, b.id DESC LIMIT ").append(limit);
        return load(where.toString(), args.toArray());
    }

    /** Waitlisted bookings for one train, date and class in queue order (oldest first). */
    public List<Booking> waitlist(int trainId, LocalDate date, String classCode) {
        return load("WHERE b.train_id = ? AND b.journey_date = ? AND b.class_code = ? AND b.status = '"
                + Models.WAITLISTED + "' ORDER BY b.created_at, b.id", trainId, Date.valueOf(date), classCode);
    }

    public Set<Integer> takenSeats(int trainId, LocalDate date, String classCode) {
        return new HashSet<>(jdbc.queryForList("SELECT p.seat_no FROM passengers p JOIN bookings b ON b.id = p.booking_id"
                + " WHERE b.status = '" + Models.CONFIRMED + "' AND b.train_id = ? AND b.journey_date = ? AND b.class_code = ?"
                + " AND p.seat_no IS NOT NULL", Integer.class, trainId, Date.valueOf(date), classCode));
    }

    public int waitlistedPassengers(int trainId, LocalDate date, String classCode) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM passengers p JOIN bookings b ON b.id = p.booking_id"
                + " WHERE b.status = '" + Models.WAITLISTED + "' AND b.train_id = ? AND b.journey_date = ? AND b.class_code = ?",
                Integer.class, trainId, Date.valueOf(date), classCode);
        return n == null ? 0 : n;
    }

    public boolean pnrExists(String pnr) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE pnr = ?", Integer.class, pnr);
        return n != null && n > 0;
    }

    public int insert(String pnr, int userId, int trainId, LocalDate date, String classCode, String status,
                      BigDecimal fare, List<Passenger> passengers) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO bookings (pnr, user_id, train_id, journey_date,"
                    + " class_code, status, fare_total) VALUES (?, ?, ?, ?, ?, ?, ?)", new String[] {"id"});
            ps.setString(1, pnr);
            ps.setInt(2, userId);
            ps.setInt(3, trainId);
            ps.setDate(4, Date.valueOf(date));
            ps.setString(5, classCode);
            ps.setString(6, status);
            ps.setBigDecimal(7, fare);
            return ps;
        }, keys);
        int id = keys.getKey().intValue();
        for (Passenger p : passengers) {
            jdbc.update("INSERT INTO passengers (booking_id, name, age, gender, seat_no) VALUES (?, ?, ?, ?, ?)",
                    id, p.name(), p.age(), p.gender(), p.seatNo());
        }
        return id;
    }

    public void cancel(int bookingId) {
        jdbc.update("UPDATE bookings SET status = '" + Models.CANCELLED + "', cancelled_at = CURRENT_TIMESTAMP WHERE id = ?",
                bookingId);
    }

    public void confirm(int bookingId, Map<Integer, Integer> seatByPassenger) {
        jdbc.update("UPDATE bookings SET status = '" + Models.CONFIRMED + "' WHERE id = ?", bookingId);
        seatByPassenger.forEach((passengerId, seat) ->
                jdbc.update("UPDATE passengers SET seat_no = ? WHERE id = ?", seat, passengerId));
    }

    public Map<String, Object> totals() {
        return jdbc.queryForMap("SELECT COUNT(*) AS bookings,"
                + " COALESCE(SUM(CASE WHEN status = '" + Models.CONFIRMED + "' THEN 1 ELSE 0 END), 0) AS confirmed,"
                + " COALESCE(SUM(CASE WHEN status = '" + Models.WAITLISTED + "' THEN 1 ELSE 0 END), 0) AS waitlisted,"
                + " COALESCE(SUM(CASE WHEN status = '" + Models.CANCELLED + "' THEN 1 ELSE 0 END), 0) AS cancelled,"
                + " COALESCE(SUM(CASE WHEN status = '" + Models.CONFIRMED + "' THEN fare_total ELSE 0 END), 0) AS revenue"
                + " FROM bookings");
    }

    /** Confirmed passengers per train and date, for occupancy charts. */
    public List<Map<String, Object>> occupancy(LocalDate from, LocalDate to) {
        return jdbc.queryForList("SELECT b.train_id, b.journey_date, COUNT(p.id) AS passengers FROM bookings b"
                + " JOIN passengers p ON p.booking_id = b.id WHERE b.status = '" + Models.CONFIRMED + "'"
                + " AND b.journey_date BETWEEN ? AND ? GROUP BY b.train_id, b.journey_date", Date.valueOf(from), Date.valueOf(to));
    }
}
