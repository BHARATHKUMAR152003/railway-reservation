package com.portfolio.railway.service;

import com.portfolio.railway.config.RailwayProperties;
import com.portfolio.railway.dao.StationDao;
import com.portfolio.railway.dao.TrainDao;
import com.portfolio.railway.dao.UserDao;
import com.portfolio.railway.model.Models.Station;
import com.portfolio.railway.model.Models.Train;
import com.portfolio.railway.model.Models.TrainClass;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Idempotent start-up seeding: a sample timetable and, if configured, the admin account. */
@Component
public class DataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final StationDao stations;
    private final TrainDao trains;
    private final UserDao users;
    private final BCryptPasswordEncoder encoder;
    private final RailwayProperties props;

    public DataSeeder(StationDao stations, TrainDao trains, UserDao users, BCryptPasswordEncoder encoder, RailwayProperties props) {
        this.stations = stations;
        this.trains = trains;
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    private static TrainClass c(String code, int seats, int fare) {
        return new TrainClass(code, seats, BigDecimal.valueOf(fare));
    }

    private static Train t(String number, String name, String from, String to, String departs, int minutes, String days,
                           List<TrainClass> classes) {
        return new Train(0, number, name, from, to, LocalTime.parse(departs), minutes, days, true, classes);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (props.seedSampleData() && trains.count() == 0) {
            List<Station> sampleStations = List.of(
                    new Station("MAS", "Chennai Central", "Chennai"),
                    new Station("SBC", "KSR Bengaluru", "Bengaluru"),
                    new Station("CBE", "Coimbatore Junction", "Coimbatore"),
                    new Station("MDU", "Madurai Junction", "Madurai"),
                    new Station("HYB", "Hyderabad Deccan", "Hyderabad"),
                    new Station("CSMT", "Mumbai CSMT", "Mumbai"),
                    new Station("NDLS", "New Delhi", "New Delhi"));
            sampleStations.stream().filter(s -> !stations.exists(s.code())).forEach(stations::insert);
            // Fictional numbers and timings: a sample timetable, not real schedules.
            List.of(
                    t("90601", "Garden City Express", "MAS", "SBC", "06:00", 330, "1111111", List.of(c("CC", 60, 785), c("2S", 90, 220))),
                    t("90602", "Garden City Express", "SBC", "MAS", "14:30", 330, "1111111", List.of(c("CC", 60, 785), c("2S", 90, 220))),
                    t("90611", "Silicon Night Mail", "MAS", "SBC", "22:40", 375, "1111111", List.of(c("2A", 12, 1210), c("3A", 24, 865), c("SL", 48, 330))),
                    t("90612", "Silicon Night Mail", "SBC", "MAS", "23:00", 380, "1111111", List.of(c("2A", 12, 1210), c("3A", 24, 865), c("SL", 48, 330))),
                    t("90621", "Kovai Intercity", "MAS", "CBE", "07:15", 450, "1111110", List.of(c("CC", 48, 905), c("2S", 72, 265))),
                    t("90622", "Kovai Intercity", "CBE", "MAS", "15:00", 450, "1111110", List.of(c("CC", 48, 905), c("2S", 72, 265))),
                    t("90631", "Temple City Express", "MAS", "MDU", "21:10", 540, "1111111", List.of(c("2A", 12, 1385), c("3A", 24, 975), c("SL", 48, 385))),
                    t("90632", "Temple City Express", "MDU", "MAS", "20:45", 545, "1111111", List.of(c("2A", 12, 1385), c("3A", 24, 975), c("SL", 48, 385))),
                    t("90641", "Deccan Superfast", "MAS", "HYB", "16:45", 750, "1010100", List.of(c("1A", 4, 3420), c("2A", 12, 2005), c("3A", 24, 1430), c("SL", 48, 545))),
                    t("90642", "Deccan Superfast", "HYB", "MAS", "17:30", 750, "0101010", List.of(c("1A", 4, 3420), c("2A", 12, 2005), c("3A", 24, 1430), c("SL", 48, 545))),
                    t("90651", "Western Coast Mail", "SBC", "CSMT", "20:00", 1435, "1111111", List.of(c("2A", 12, 2280), c("3A", 24, 1610), c("SL", 48, 610))),
                    t("90661", "Capital Link", "SBC", "NDLS", "08:20", 2075, "1001000", List.of(c("1A", 4, 6190), c("2A", 12, 3725), c("3A", 24, 2595)))
            ).forEach(trains::insert);
            log.info("Seeded {} stations and sample timetable", sampleStations.size());
        }

        String email = props.adminEmail() == null ? "" : AuthService.normaliseEmail(props.adminEmail());
        String password = props.adminPassword() == null ? "" : props.adminPassword();
        if (email.isEmpty() || password.isEmpty()) {
            log.info("No admin account configured (set RAIL_ADMIN_EMAIL and RAIL_ADMIN_PASSWORD)");
        } else if (password.length() < 10) {
            log.warn("RAIL_ADMIN_PASSWORD must be at least 10 characters; admin account not created");
        } else if (users.findByEmail(email).isEmpty()) {
            users.insert("Administrator", email, encoder.encode(password), "ADMIN");
            log.info("Created admin account {}", email);
        }
    }
}
