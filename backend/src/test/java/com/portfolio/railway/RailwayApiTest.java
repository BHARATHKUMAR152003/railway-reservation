package com.portfolio.railway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RailwayApiTest {
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private final LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);

    private ResultActions postJson(String url, String token, Object body) throws Exception {
        var req = post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req);
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private String register() throws Exception {
        String email = "user" + SEQ.incrementAndGet() + "@test.local";
        return read(postJson("/api/auth/register", null, Map.of("name", "Test User", "email", email, "password", "password-123"))
                .andExpect(status().isCreated())).get("token").asText();
    }

    private String adminToken() throws Exception {
        return read(postJson("/api/auth/login", null, Map.of("email", "admin@test.local", "password", "admin-password-123"))
                .andExpect(status().isOk())).get("token").asText();
    }

    /** Creates a fresh daily train with a small class so seat and waitlist rules are easy to exercise. */
    private int smallTrain(int seats, String runDays) throws Exception {
        String number = String.valueOf(91000 + SEQ.incrementAndGet());
        Map<String, Object> body = Map.of("number", number, "name", "Test Express", "source", "MAS", "destination", "CBE",
                "departs", "23:50", "durationMinutes", 400, "runDays", runDays,
                "classes", List.of(Map.of("code", "CC", "seats", seats, "fare", 500)));
        return read(postJson("/api/admin/trains", adminToken(), body).andExpect(status().isCreated())).get("train").get("id").asInt();
    }

    private ResultActions book(String token, int trainId, LocalDate date, int passengers) throws Exception {
        List<Map<String, Object>> people = java.util.stream.IntStream.range(0, passengers)
                .mapToObj(i -> Map.<String, Object>of("name", "Passenger " + (char) ('A' + i), "age", 30 + i, "gender", "F")).toList();
        return postJson("/api/bookings", token, Map.of("trainId", trainId, "journeyDate", date.toString(), "classCode", "CC",
                "passengers", people));
    }

    @Test
    void searchShowsSeededTrainsWithAvailability() throws Exception {
        mvc.perform(get("/api/stations")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7));
        JsonNode results = read(mvc.perform(get("/api/trains/search").param("from", "MAS").param("to", "SBC")
                .param("date", tomorrow.toString())).andExpect(status().isOk()));
        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        JsonNode first = results.get(0);
        assertThat(first.get("train").get("source").asText()).isEqualTo("MAS");
        assertThat(first.get("classes").get(0).get("label").asText()).startsWith("AVAILABLE");
    }

    @Test
    void searchValidation() throws Exception {
        mvc.perform(get("/api/trains/search").param("from", "MAS").param("to", "MAS").param("date", tomorrow.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/trains/search").param("from", "MAS").param("to", "SBC").param("date", tomorrow.minusDays(3).toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("The journey date is in the past."));
        mvc.perform(get("/api/trains/search").param("from", "mas'--").param("to", "SBC").param("date", tomorrow.toString()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void authRules() throws Exception {
        String token = register();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("PASSENGER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
        postJson("/api/auth/login", null, Map.of("email", "admin@test.local", "password", "wrong-password"))
                .andExpect(status().isUnauthorized());
        postJson("/api/auth/register", null, Map.of("name", "X", "email", "admin@test.local", "password", "password-123"))
                .andExpect(status().isConflict());
        postJson("/api/auth/register", null, Map.of("name", "X", "email", "not-an-email", "password", "short"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/summary").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void bookingWaitlistCancellationAndPromotion() throws Exception {
        int train = smallTrain(2, "1111111");
        String alice = register();
        String bob = register();
        String carol = register();

        JsonNode a = read(book(alice, train, tomorrow, 2).andExpect(status().isCreated()));
        assertThat(a.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(a.get("fareTotal").decimalValue()).isEqualByComparingTo("1000");
        assertThat(a.get("passengers").findValuesAsText("seat")).containsExactly("CC-1", "CC-2");

        JsonNode b = read(book(bob, train, tomorrow, 1).andExpect(status().isCreated()));
        assertThat(b.get("status").asText()).isEqualTo("WAITLISTED");
        assertThat(b.get("waitlistFrom").asInt()).isEqualTo(1);
        JsonNode c = read(book(carol, train, tomorrow, 2).andExpect(status().isCreated()));
        assertThat(c.get("waitlistFrom").asInt()).isEqualTo(2);

        // Someone else cannot cancel Alice's booking.
        mvc.perform(post("/api/bookings/" + a.get("pnr").asText() + "/cancel").header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/bookings/" + a.get("pnr").asText() + "/cancel").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/bookings/" + a.get("pnr").asText() + "/cancel").header("Authorization", "Bearer " + alice))
                .andExpect(status().isConflict());

        // Bob (1 passenger) is first in the queue and fits; Carol (2) no longer fits and moves up to WL 1.
        JsonNode bobStatus = read(mvc.perform(get("/api/pnr/" + b.get("pnr").asText())).andExpect(status().isOk()));
        assertThat(bobStatus.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(bobStatus.get("seats").get(0).asText()).isEqualTo("CC-1");
        assertThat(bobStatus.toString()).doesNotContain("Passenger");
        JsonNode carolStatus = read(mvc.perform(get("/api/pnr/" + c.get("pnr").asText())));
        assertThat(carolStatus.get("status").asText()).isEqualTo("WAITLISTED");
        assertThat(carolStatus.get("waitlistFrom").asInt()).isEqualTo(1);

        JsonNode mine = read(mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + alice)));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("canCancel").asBoolean()).isFalse();
    }

    @Test
    void bookingRules() throws Exception {
        String token = register();
        int mondaysOnly = smallTrain(5, "1000000");
        LocalDate notMonday = tomorrow.getDayOfWeek() == DayOfWeek.MONDAY ? tomorrow.plusDays(1) : tomorrow;
        book(token, mondaysOnly, notMonday, 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("does not run on")));
        book(token, mondaysOnly, tomorrow.plusDays(200), 1).andExpect(status().isBadRequest());
        book(token, mondaysOnly, tomorrow, 7).andExpect(status().isUnprocessableEntity());
        book(null, mondaysOnly, tomorrow, 1).andExpect(status().isUnauthorized());
        postJson("/api/bookings", token, Map.of("trainId", mondaysOnly, "journeyDate", tomorrow.toString(), "classCode", "1A",
                "passengers", List.of(Map.of("name", "Robert'); DROP TABLE users;--", "age", 30, "gender", "M"))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/pnr/12345")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/pnr/1234567890")).andExpect(status().isNotFound());
    }

    @Test
    void waitlistLimitIsEnforced() throws Exception {
        int train = smallTrain(1, "1111111");
        String token = register();
        book(token, train, tomorrow, 1).andExpect(jsonPath("$.status").value("CONFIRMED"));
        for (int i = 0; i < 3; i++) {
            book(token, train, tomorrow, 6).andExpect(status().isCreated());
        }
        book(token, train, tomorrow, 3).andExpect(status().isConflict());
        book(token, train, tomorrow, 2).andExpect(status().isCreated());
    }

    @Test
    void adminEndpoints() throws Exception {
        String admin = adminToken();
        int train = smallTrain(3, "1111111");
        book(register(), train, tomorrow, 2);
        JsonNode summary = read(mvc.perform(get("/api/admin/summary").header("Authorization", "Bearer " + admin)).andExpect(status().isOk()));
        assertThat(summary.get("bookings").asInt()).isPositive();
        assertThat(summary.get("occupancyNext7Days").isArray()).isTrue();

        mvc.perform(put("/api/admin/trains/" + train + "/active").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.train.active").value(false));
        book(register(), train, tomorrow, 1).andExpect(status().isNotFound());

        mvc.perform(get("/api/admin/bookings").param("trainId", String.valueOf(train)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        postJson("/api/admin/trains", admin, Map.of("number", "90601", "name", "Dup", "source", "MAS", "destination", "SBC",
                "departs", "10:00", "durationMinutes", 60, "runDays", "1111111",
                "classes", List.of(Map.of("code", "CC", "seats", 5, "fare", 100)))).andExpect(status().isConflict());
    }
}
