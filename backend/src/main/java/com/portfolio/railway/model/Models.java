package com.portfolio.railway.model;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Domain types shared by the DAOs and services. */
public final class Models {
    private Models() {
    }

    public record Station(String code, String name, String city) {
    }

    public record TrainClass(String code, int seats, BigDecimal fare) {
    }

    public record Train(int id, String number, String name, String source, String destination, LocalTime departs,
                        int durationMinutes, String runDays, boolean active, List<TrainClass> classes) {

        /** runDays is a Monday-first 7-character mask, e.g. "1111100" = weekdays only. */
        public boolean runsOn(DayOfWeek day) {
            return runDays.charAt(day.getValue() - 1) == '1';
        }

        public LocalTime arrives() {
            return departs.plusMinutes(durationMinutes);
        }

        public int arrivalDayOffset() {
            return (departs.toSecondOfDay() / 60 + durationMinutes) / (24 * 60);
        }

        public TrainClass travelClass(String code) {
            return classes.stream().filter(c -> c.code().equals(code)).findFirst().orElse(null);
        }
    }

    public record User(int id, String name, String email, String passwordHash, String role) {
        public boolean isAdmin() {
            return "ADMIN".equals(role);
        }

        public UserView view() {
            return new UserView(id, name, email, role);
        }
    }

    public record UserView(int id, String name, String email, String role) {
    }

    public record Passenger(int id, String name, int age, String gender, Integer seatNo) {
    }

    public record Booking(int id, String pnr, int userId, int trainId, LocalDate journeyDate, String classCode,
                          String status, BigDecimal fareTotal, Instant createdAt, List<Passenger> passengers) {
    }

    public static final String CONFIRMED = "CONFIRMED";
    public static final String WAITLISTED = "WAITLISTED";
    public static final String CANCELLED = "CANCELLED";
}
