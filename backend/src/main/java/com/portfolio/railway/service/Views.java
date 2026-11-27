package com.portfolio.railway.service;

import com.portfolio.railway.model.Models.Booking;
import com.portfolio.railway.model.Models.Passenger;
import com.portfolio.railway.model.Models.Train;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** JSON shapes returned by the API. */
public final class Views {
    private Views() {
    }

    public record ClassAvailability(String code, BigDecimal fare, int seats, int available, int waitlisted,
                                    boolean bookable, String label) {
    }

    public record TrainSummary(int id, String number, String name, String source, String destination, LocalTime departs,
                               LocalTime arrives, int arrivalDayOffset, int durationMinutes, String runDays, boolean active) {
        public static TrainSummary of(Train t) {
            return new TrainSummary(t.id(), t.number(), t.name(), t.source(), t.destination(), t.departs(), t.arrives(),
                    t.arrivalDayOffset(), t.durationMinutes(), t.runDays(), t.active());
        }
    }

    public record SearchResult(TrainSummary train, List<ClassAvailability> classes, boolean departed) {
    }

    public record PassengerView(String name, int age, String gender, String seat) {
    }

    public record BookingView(String pnr, String status, Integer waitlistFrom, TrainSummary train, LocalDate journeyDate,
                              String classCode, BigDecimal fareTotal, Instant createdAt, List<PassengerView> passengers,
                              boolean canCancel) {
    }

    /** Public PNR enquiry: no passenger names or account details. */
    public record PnrStatus(String pnr, String status, Integer waitlistFrom, TrainSummary train, LocalDate journeyDate,
                            String classCode, int passengers, List<String> seats) {
    }

    public static String seatLabel(String classCode, Passenger p) {
        return p.seatNo() == null ? null : classCode + "-" + p.seatNo();
    }

    public static PassengerView passenger(Booking b, Passenger p) {
        return new PassengerView(p.name(), p.age(), p.gender(), seatLabel(b.classCode(), p));
    }
}
