package com.portfolio.railway.service;

import com.portfolio.railway.config.RailwayProperties;
import com.portfolio.railway.dao.UserDao;
import com.portfolio.railway.error.ApiException;
import com.portfolio.railway.model.Models.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/** Password login with opaque bearer tokens; only a SHA-256 hash of each token is stored. */
@Service
public class AuthService {
    private static final int MAX_FAILURES = 5;
    private static final Duration LOCKOUT = Duration.ofMinutes(5);

    private final UserDao users;
    private final BCryptPasswordEncoder encoder;
    private final RailwayProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    // Compared against when the email is unknown so response time does not reveal which accounts exist.
    private final String dummyHash;

    private record Failures(int count, Instant lockedUntil) {
    }

    public record Session(String token, User user) {
    }

    public AuthService(UserDao users, BCryptPasswordEncoder encoder, RailwayProperties props, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public User register(String name, String email, String password) {
        String normalised = normaliseEmail(email);
        try {
            users.insert(name.trim(), normalised, encoder.encode(password), "PASSENGER");
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("An account with this email already exists.");
        }
        return users.findByEmail(normalised).orElseThrow();
    }

    public Session login(String email, String password) {
        String key = normaliseEmail(email);
        Instant now = clock.instant();
        Failures f = failures.get(key);
        if (f != null && f.lockedUntil() != null && f.lockedUntil().isAfter(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Try again in a few minutes.");
        }
        User user = users.findByEmail(key).orElse(null);
        boolean ok = encoder.matches(password, user == null ? dummyHash : user.passwordHash()) && user != null;
        if (!ok) {
            int count = f == null ? 1 : f.count() + 1;
            failures.put(key, new Failures(count, count >= MAX_FAILURES ? now.plus(LOCKOUT) : null));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect email or password.");
        }
        failures.remove(key);
        users.deleteExpiredSessions(now);
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        users.createSession(hash(token), user.id(), now.plus(Duration.ofHours(props.sessionHours())));
        return new Session(token, user);
    }

    public void logout(String authorization) {
        String token = bearer(authorization);
        if (token != null) {
            users.deleteSession(hash(token));
        }
    }

    public User require(String authorization) {
        String token = bearer(authorization);
        if (token == null) {
            throw ApiException.unauthorized();
        }
        return users.findBySession(hash(token), clock.instant()).orElseThrow(ApiException::unauthorized);
    }

    public User requireAdmin(String authorization) {
        User user = require(authorization);
        if (!user.isAdmin()) {
            throw ApiException.forbidden();
        }
        return user;
    }

    private static String bearer(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() || token.length() > 100 ? null : token;
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
