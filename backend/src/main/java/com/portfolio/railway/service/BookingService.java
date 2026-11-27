package com.portfolio.railway.service;

import com.portfolio.railway.config.RailwayProperties;
import com.portfolio.railway.dao.BookingDao;
import com.portfolio.railway.dao.StationDao;
import com.portfolio.railway.dao.TrainDao;
import com.portfolio.railway.error.ApiException;
import com.portfolio.railway.model.Models;
import com.portfolio.railway.model.Models.Booking;
import com.portfolio.railway.model.Models.Passenger;
import com.portfolio.railway.model.Models.Train;
import com.portfolio.railway.model.Models.TrainClass;
import com.portfolio.railway.model.Models.User;
import com.portfolio.railway.service.Views.BookingView;
import com.portfolio.railway.service.Views.ClassAvailability;
import com.portfolio.railway.service.Views.PnrStatus;
import com.portfolio.railway.service.Views.SearchResult;
import com.portfolio.railway.service.Views.TrainSummary;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    public record NewPassenger(String name, int age, String gender) {
    }

    private final TrainDao trains;
    private final StationDao stations;
    private final BookingDao bookings;
    private final RailwayProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public BookingService(TrainDao trains, StationDao stations, BookingDao bookings, RailwayProperties props, Clock clock) {
        this.trains = trains;
        this.stations = stations;
        this.bookings = bookings;
        this.props = props;
        this.clock = clock;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private boolean departed(Train t, LocalDate date) {
        return !LocalDateTime.of(date, t.departs()).isAfter(now());
    }

    private void checkDateWindow(LocalDate date) {
        LocalDate today = now().toLocalDate();
        if (date.isBefore(today)) {
            throw ApiException.badRequest("The journey date is in the past.");
        }
        if (date.isAfter(today.plusDays(props.bookingWindowDays()))) {
            throw ApiException.badRequest("Bookings open " + props.bookingWindowDays() + " days before the journey.");
        }
    }

    private ClassAvailability availability(Train t, TrainClass c, LocalDate date, boolean departed) {
        int taken = bookings.takenSeats(t.id(), date, c.code()).size();
        int available = Math.max(0, c.seats() - taken);
        int waitlisted = bookings.waitlistedPassengers(t.id(), date, c.code());
        String label;
        boolean bookable = !departed;
        if (available > 0) {
            label = "AVAILABLE " + available;
        } else if (waitlisted < props.maxWaitlist()) {
            label = "WL " + (waitlisted + 1);
        } else {
            label = "WL FULL";
            bookable = false;
        }
        return new ClassAvailability(c.code(), c.fare(), c.seats(), available, waitlisted, bookable, label);
    }

    public List<SearchResult> search(String from, String to, LocalDate date) {
        if (from.equals(to)) {
            throw ApiException.badRequest("Origin and destination must differ.");
        }
        if (!stations.exists(from) || !stations.exists(to)) {
            throw ApiException.notFound("Unknown station code.");
        }
        checkDateWindow(date);
        return trains.findRoute(from, to).stream()
                .filter(t -> t.runsOn(date.getDayOfWeek()))
                .map(t -> {
                    boolean gone = departed(t, date);
                    return new SearchResult(TrainSummary.of(t),
                            t.classes().stream().map(c -> availability(t, c, date, gone)).toList(), gone);
                })
                .toList();
    }

    private String newPnr() {
        for (int i = 0; i < 20; i++) {
            String pnr = String.format("%010d", (long) (random.nextDouble() * 9_000_000_000L) + 1_000_000_000L);
            if (!bookings.pnrExists(pnr)) {
                return pnr;
            }
        }
        throw new IllegalStateException("Could not allocate a PNR");
    }

    private static List<Integer> lowestFreeSeats(int capacity, Set<Integer> taken, int count) {
        List<Integer> seats = new ArrayList<>(count);
        for (int s = 1; s <= capacity && seats.size() < count; s++) {
            if (!taken.contains(s)) {
                seats.add(s);
            }
        }
        return seats;
    }

    // READ COMMITTED: under MySQL's default REPEATABLE READ, seat reads after the FOR UPDATE lock would see a stale snapshot.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingView book(User user, int trainId, LocalDate date, String classCode, List<NewPassenger> people) {
        if (people.isEmpty() || people.size() > props.maxPassengers()) {
            throw ApiException.badRequest("Book between 1 and " + props.maxPassengers() + " passengers.");
        }
        Train train = trains.findById(trainId).filter(Train::active)
                .orElseThrow(() -> ApiException.notFound("Train not found."));
        checkDateWindow(date);
        if (!train.runsOn(date.getDayOfWeek())) {
            String day = date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            throw ApiException.badRequest("Train " + train.number() + " does not run on " + day + ".");
        }
        if (departed(train, date)) {
            throw ApiException.badRequest("This train has already departed.");
        }
        TrainClass cls = trains.lockClass(train.id(), classCode)
                .orElseThrow(() -> ApiException.badRequest("Train " + train.number() + " has no " + classCode + " class."));

        Set<Integer> taken = bookings.takenSeats(train.id(), date, cls.code());
        List<Integer> seats = lowestFreeSeats(cls.seats(), taken, people.size());
        String status;
        if (seats.size() == people.size()) {
            status = Models.CONFIRMED;
        } else {
            int waiting = bookings.waitlistedPassengers(train.id(), date, cls.code());
            if (waiting + people.size() > props.maxWaitlist()) {
                throw ApiException.conflict("No seats left and the waitlist is full.");
            }
            status = Models.WAITLISTED;
        }
        List<Passenger> rows = new ArrayList<>();
        for (int i = 0; i < people.size(); i++) {
            NewPassenger p = people.get(i);
            Integer seat = Models.CONFIRMED.equals(status) ? seats.get(i) : null;
            rows.add(new Passenger(0, p.name().trim(), p.age(), p.gender(), seat));
        }
        BigDecimal fare = cls.fare().multiply(BigDecimal.valueOf(people.size()));
        String pnr = newPnr();
        bookings.insert(pnr, user.id(), train.id(), date, cls.code(), status, fare, rows);
        return view(bookings.findByPnr(pnr).orElseThrow(), train);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingView cancel(User user, String pnr) {
        Booking b = bookings.findByPnr(pnr).filter(x -> x.userId() == user.id() || user.isAdmin())
                .orElseThrow(() -> ApiException.notFound("Booking not found."));
        Train train = trains.findById(b.trainId()).orElseThrow();
        if (Models.CANCELLED.equals(b.status())) {
            throw ApiException.conflict("This booking is already cancelled.");
        }
        if (departed(train, b.journeyDate())) {
            throw ApiException.conflict("Bookings cannot be cancelled after departure.");
        }
        trains.lockClass(train.id(), b.classCode());
        bookings.cancel(b.id());
        if (Models.CONFIRMED.equals(b.status())) {
            promoteWaitlist(train, b.journeyDate(), b.classCode());
        }
        return view(bookings.findByPnr(pnr).orElseThrow(), train);
    }

    /** Confirms waitlisted bookings strictly in queue order while whole bookings fit the freed seats. */
    private void promoteWaitlist(Train train, LocalDate date, String classCode) {
        TrainClass cls = train.travelClass(classCode);
        Set<Integer> taken = bookings.takenSeats(train.id(), date, classCode);
        for (Booking w : bookings.waitlist(train.id(), date, classCode)) {
            List<Integer> seats = lowestFreeSeats(cls.seats(), taken, w.passengers().size());
            if (seats.size() < w.passengers().size()) {
                return;
            }
            Map<Integer, Integer> assignment = new HashMap<>();
            for (int i = 0; i < seats.size(); i++) {
                assignment.put(w.passengers().get(i).id(), seats.get(i));
            }
            bookings.confirm(w.id(), assignment);
            taken.addAll(seats);
        }
    }

    private Integer waitlistFrom(Booking b) {
        if (!Models.WAITLISTED.equals(b.status())) {
            return null;
        }
        int ahead = 0;
        for (Booking w : bookings.waitlist(b.trainId(), b.journeyDate(), b.classCode())) {
            if (w.id() == b.id()) {
                return ahead + 1;
            }
            ahead += w.passengers().size();
        }
        return null;
    }

    BookingView view(Booking b, Train t) {
        boolean canCancel = !Models.CANCELLED.equals(b.status()) && !departed(t, b.journeyDate());
        return new BookingView(b.pnr(), b.status(), waitlistFrom(b), TrainSummary.of(t), b.journeyDate(), b.classCode(),
                b.fareTotal(), b.createdAt(), b.passengers().stream().map(p -> Views.passenger(b, p)).toList(), canCancel);
    }

    public List<BookingView> viewAll(List<Booking> list) {
        Map<Integer, Train> byId = trains.findAll().stream().collect(Collectors.toMap(Train::id, Function.identity()));
        return list.stream().map(b -> view(b, byId.get(b.trainId()))).toList();
    }

    public List<BookingView> mine(User user) {
        return viewAll(bookings.findByUser(user.id()));
    }

    public PnrStatus pnrStatus(String pnr) {
        Booking b = bookings.findByPnr(pnr).orElseThrow(() -> ApiException.notFound("PNR not found."));
        Train t = trains.findById(b.trainId()).orElseThrow();
        List<String> seats = b.passengers().stream().map(p -> Views.seatLabel(b.classCode(), p))
                .filter(s -> s != null && Models.CONFIRMED.equals(b.status())).toList();
        return new PnrStatus(b.pnr(), b.status(), waitlistFrom(b), TrainSummary.of(t), b.journeyDate(), b.classCode(),
                b.passengers().size(), seats);
    }
}
