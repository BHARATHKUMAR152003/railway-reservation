package com.portfolio.railway.web;

import com.portfolio.railway.model.Models.Station;
import com.portfolio.railway.model.Models.TrainClass;
import com.portfolio.railway.service.AdminService;
import com.portfolio.railway.service.AdminService.TrainDetail;
import com.portfolio.railway.service.AuthService;
import com.portfolio.railway.service.Views.BookingView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    public record ClassRequest(@NotBlank @Pattern(regexp = BookingController.CLASS, message = "unknown class") String code,
                               @Min(1) @Max(500) int seats,
                               @NotNull @DecimalMin("1") @DecimalMax("100000") BigDecimal fare) {
    }

    public record TrainRequest(@NotBlank @Pattern(regexp = "\\d{5}", message = "must be 5 digits") String number,
                               @NotBlank @Size(max = 80) String name,
                               @NotBlank @Pattern(regexp = BookingController.STATION) String source,
                               @NotBlank @Pattern(regexp = BookingController.STATION) String destination,
                               @NotNull LocalTime departs,
                               @Min(10) @Max(4320) int durationMinutes,
                               @NotBlank @Pattern(regexp = "[01]{7}", message = "must be 7 characters of 0/1, Monday first") String runDays,
                               @NotEmpty @Size(max = 6) List<@Valid ClassRequest> classes) {
    }

    public record StationRequest(@NotBlank @Pattern(regexp = BookingController.STATION) String code,
                                 @NotBlank @Size(max = 80) String name,
                                 @NotBlank @Size(max = 60) String city) {
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }

    private final AuthService auth;
    private final AdminService admin;

    public AdminController(AuthService auth, AdminService admin) {
        this.auth = auth;
        this.admin = admin;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireAdmin(authorization);
        return admin.summary();
    }

    @GetMapping("/trains")
    public List<TrainDetail> trains(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireAdmin(authorization);
        return admin.trains();
    }

    @PostMapping("/trains")
    @ResponseStatus(HttpStatus.CREATED)
    public TrainDetail createTrain(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @Valid @RequestBody TrainRequest req) {
        auth.requireAdmin(authorization);
        List<TrainClass> classes = req.classes().stream().map(c -> new TrainClass(c.code(), c.seats(), c.fare())).toList();
        return admin.createTrain(req.number(), req.name(), req.source(), req.destination(), req.departs(),
                req.durationMinutes(), req.runDays(), classes);
    }

    @PutMapping("/trains/{id}/active")
    public TrainDetail setActive(@RequestHeader(value = "Authorization", required = false) String authorization,
                                 @PathVariable int id, @Valid @RequestBody ActiveRequest req) {
        auth.requireAdmin(authorization);
        return admin.setActive(id, req.active());
    }

    @PostMapping("/stations")
    @ResponseStatus(HttpStatus.CREATED)
    public Station createStation(@RequestHeader(value = "Authorization", required = false) String authorization,
                                 @Valid @RequestBody StationRequest req) {
        auth.requireAdmin(authorization);
        return admin.createStation(req.code(), req.name(), req.city());
    }

    @GetMapping("/bookings")
    public List<BookingView> bookings(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      @RequestParam(required = false) Integer trainId) {
        auth.requireAdmin(authorization);
        return admin.bookings(date, trainId);
    }
}
