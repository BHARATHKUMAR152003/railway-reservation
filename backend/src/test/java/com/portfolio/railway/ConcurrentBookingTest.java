package com.portfolio.railway;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.railway.dao.UserDao;
import com.portfolio.railway.model.Models;
import com.portfolio.railway.model.Models.TrainClass;
import com.portfolio.railway.model.Models.User;
import com.portfolio.railway.service.AdminService;
import com.portfolio.railway.service.BookingService;
import com.portfolio.railway.service.BookingService.NewPassenger;
import com.portfolio.railway.service.Views.BookingView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentBookingTest {
    @Autowired
    BookingService bookings;
    @Autowired
    AdminService admin;
    @Autowired
    UserDao users;

    @Test
    void concurrentBookingsNeverShareASeat() throws Exception {
        int trainId = admin.createTrain("92001", "Race Express", "MAS", "MDU", LocalTime.of(23, 55), 500, "1111111",
                List.of(new TrainClass("SL", 5, BigDecimal.valueOf(300)))).train().id();
        users.insert("Racer", "racer@test.local", "x", "PASSENGER");
        User racer = users.findByEmail("racer@test.local").orElseThrow();
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<BookingView>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            tasks.add(() -> bookings.book(racer, trainId, date, "SL", List.of(new NewPassenger("Racer", 40, "M"))));
        }
        List<BookingView> results = new ArrayList<>();
        for (Future<BookingView> f : pool.invokeAll(tasks)) {
            results.add(f.get());
        }
        pool.shutdown();

        List<String> seats = results.stream().filter(r -> Models.CONFIRMED.equals(r.status()))
                .map(r -> r.passengers().get(0).seat()).toList();
        assertThat(seats).hasSize(5);
        assertThat(new HashSet<>(seats)).hasSize(5);
        assertThat(results.stream().filter(r -> Models.WAITLISTED.equals(r.status()))).hasSize(7);
    }
}
