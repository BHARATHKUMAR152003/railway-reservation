package com.portfolio.railway.web;

import com.portfolio.railway.dao.StationDao;
import com.portfolio.railway.model.Models.Station;
import com.portfolio.railway.service.AuthService;
import com.portfolio.railway.service.BookingService;
import com.portfolio.railway.service.BookingService.NewPassenger;
import com.portfolio.railway.service.Views.BookingView;
import com.portfolio.railway.service.Views.PnrStatus;
import com.portfolio.railway.service.Views.SearchResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BookingController {
    static final String STATION = "[A-Z]{2,8}";
    static final String CLASS = "1A|2A|3A|SL|CC|2S";

    public record PassengerRequest(
            @NotBlank @Size(max = 80) @Pattern(regexp = "[\\p{L} .'-]+", message = "letters, spaces, . ' - only") String name,
            @Min(1) @Max(120) int age,
            @NotBlank @Pattern(regexp = "[MFO]", message = "must be M, F or O") String gender) {
    }

    public record BookingRequest(@NotNull Integer trainId,
                                 @NotNull LocalDate journeyDate,
                                 @NotBlank @Pattern(regexp = CLASS, message = "unknown class") String classCode,
                                 @NotEmpty @Size(max = 6) List<@Valid PassengerRequest> passengers) {
    }

    private final AuthService auth;
    private final BookingService bookings;
    private final StationDao stations;

    public BookingController(AuthService auth, BookingService bookings, StationDao stations) {
        this.auth = auth;
        this.bookings = bookings;
        this.stations = stations;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/stations")
    public List<Station> stations() {
        return stations.findAll();
    }

    @GetMapping("/trains/search")
    public List<SearchResult> search(@RequestParam @Pattern(regexp = STATION) String from,
                                     @RequestParam @Pattern(regexp = STATION) String to,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return bookings.search(from, to, date);
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingView book(@RequestHeader(value = "Authorization", required = false) String authorization,
                            @Valid @RequestBody BookingRequest req) {
        var user = auth.require(authorization);
        List<NewPassenger> people = req.passengers().stream()
                .map(p -> new NewPassenger(p.name(), p.age(), p.gender())).toList();
        return bookings.book(user, req.trainId(), req.journeyDate(), req.classCode(), people);
    }

    @GetMapping("/bookings")
    public List<BookingView> mine(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return bookings.mine(auth.require(authorization));
    }

    @PostMapping("/bookings/{pnr}/cancel")
    public BookingView cancel(@RequestHeader(value = "Authorization", required = false) String authorization,
                              @PathVariable @Pattern(regexp = "\\d{10}") String pnr) {
        return bookings.cancel(auth.require(authorization), pnr);
    }

    @GetMapping("/pnr/{pnr}")
    public PnrStatus pnr(@PathVariable @Pattern(regexp = "\\d{10}") String pnr) {
        return bookings.pnrStatus(pnr);
    }
}
