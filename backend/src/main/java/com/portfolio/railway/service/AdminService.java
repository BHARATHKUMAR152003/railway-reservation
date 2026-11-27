package com.portfolio.railway.service;

import com.portfolio.railway.dao.BookingDao;
import com.portfolio.railway.dao.StationDao;
import com.portfolio.railway.dao.TrainDao;
import com.portfolio.railway.error.ApiException;
import com.portfolio.railway.model.Models.Station;
import com.portfolio.railway.model.Models.Train;
import com.portfolio.railway.model.Models.TrainClass;
import com.portfolio.railway.service.Views.BookingView;
import com.portfolio.railway.service.Views.TrainSummary;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    public record TrainDetail(TrainSummary train, List<TrainClass> classes) {
    }

    public record OccupancyRow(int trainId, String number, String name, LocalDate date, int confirmed, int capacity) {
    }

    private final TrainDao trains;
    private final StationDao stations;
    private final BookingDao bookings;
    private final BookingService bookingService;
    private final Clock clock;

    public AdminService(TrainDao trains, StationDao stations, BookingDao bookings, BookingService bookingService, Clock clock) {
        this.trains = trains;
        this.stations = stations;
        this.bookings = bookings;
        this.bookingService = bookingService;
        this.clock = clock;
    }

    public List<TrainDetail> trains() {
        return trains.findAll().stream().map(t -> new TrainDetail(TrainSummary.of(t), t.classes())).toList();
    }

    @Transactional
    public TrainDetail createTrain(String number, String name, String source, String destination, LocalTime departs,
                                   int durationMinutes, String runDays, List<TrainClass> classes) {
        if (source.equals(destination)) {
            throw ApiException.badRequest("Origin and destination must differ.");
        }
        if (!stations.exists(source) || !stations.exists(destination)) {
            throw ApiException.badRequest("Unknown station code.");
        }
        if (!runDays.contains("1")) {
            throw ApiException.badRequest("The train must run on at least one day.");
        }
        if (new HashSet<>(classes.stream().map(TrainClass::code).toList()).size() != classes.size()) {
            throw ApiException.badRequest("Each class can only be listed once.");
        }
        if (trains.numberExists(number)) {
            throw ApiException.conflict("Train number " + number + " already exists.");
        }
        int id = trains.insert(new Train(0, number, name.trim(), source, destination, departs, durationMinutes, runDays,
                true, classes));
        Train t = trains.findById(id).orElseThrow();
        return new TrainDetail(TrainSummary.of(t), t.classes());
    }

    public TrainDetail setActive(int id, boolean active) {
        trains.findById(id).orElseThrow(() -> ApiException.notFound("Train not found."));
        trains.setActive(id, active);
        Train t = trains.findById(id).orElseThrow();
        return new TrainDetail(TrainSummary.of(t), t.classes());
    }

    public Station createStation(String code, String name, String city) {
        if (stations.exists(code)) {
            throw ApiException.conflict("Station " + code + " already exists.");
        }
        Station s = new Station(code, name.trim(), city.trim());
        stations.insert(s);
        return s;
    }

    public List<BookingView> bookings(LocalDate date, Integer trainId) {
        return bookingService.viewAll(bookings.search(date, trainId, 200));
    }

    public Map<String, Object> summary() {
        LocalDate today = LocalDate.now(clock);
        LocalDate until = today.plusDays(6);
        Map<Integer, Train> byId = new LinkedHashMap<>();
        trains.findAll().forEach(t -> byId.put(t.id(), t));
        List<OccupancyRow> occupancy = bookings.occupancy(today, until).stream().map(r -> {
            Train t = byId.get(((Number) r.get("train_id")).intValue());
            int capacity = t.classes().stream().mapToInt(TrainClass::seats).sum();
            LocalDate date = r.get("journey_date") instanceof Date d ? d.toLocalDate() : LocalDate.parse(r.get("journey_date").toString());
            return new OccupancyRow(t.id(), t.number(), t.name(), date, ((Number) r.get("passengers")).intValue(), capacity);
        }).sorted((a, b) -> a.date().equals(b.date()) ? a.number().compareTo(b.number()) : a.date().compareTo(b.date())).toList();

        Map<String, Object> totals = bookings.totals();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("trains", byId.size());
        out.put("activeTrains", byId.values().stream().filter(Train::active).count());
        out.put("stations", stations.findAll().size());
        out.put("bookings", ((Number) totals.get("bookings")).intValue());
        out.put("confirmed", ((Number) totals.get("confirmed")).intValue());
        out.put("waitlisted", ((Number) totals.get("waitlisted")).intValue());
        out.put("cancelled", ((Number) totals.get("cancelled")).intValue());
        out.put("revenue", new BigDecimal(totals.get("revenue").toString()));
        out.put("occupancyNext7Days", occupancy);
        return out;
    }
}
