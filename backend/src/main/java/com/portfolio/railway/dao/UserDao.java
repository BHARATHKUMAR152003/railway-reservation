package com.portfolio.railway.dao;

import com.portfolio.railway.model.Models.User;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserDao {
    private static final RowMapper<User> MAPPER = (rs, i) -> new User(rs.getInt("id"), rs.getString("name"),
            rs.getString("email"), rs.getString("password_hash"), rs.getString("role"));

    private final JdbcTemplate jdbc;

    public UserDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<User> findByEmail(String email) {
        return jdbc.query("SELECT id, name, email, password_hash, role FROM users WHERE email = ?", MAPPER, email)
                .stream().findFirst();
    }

    public void insert(String name, String email, String passwordHash, String role) {
        jdbc.update("INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, ?)",
                name, email, passwordHash, role);
    }

    public void createSession(String tokenHash, int userId, Instant expiresAt) {
        jdbc.update("INSERT INTO sessions (token_hash, user_id, expires_at) VALUES (?, ?, ?)",
                tokenHash, userId, Timestamp.from(expiresAt));
    }

    public Optional<User> findBySession(String tokenHash, Instant now) {
        return jdbc.query("SELECT u.id, u.name, u.email, u.password_hash, u.role FROM sessions s"
                + " JOIN users u ON u.id = s.user_id WHERE s.token_hash = ? AND s.expires_at > ?",
                MAPPER, tokenHash, Timestamp.from(now)).stream().findFirst();
    }

    public void deleteSession(String tokenHash) {
        jdbc.update("DELETE FROM sessions WHERE token_hash = ?", tokenHash);
    }

    public void deleteExpiredSessions(Instant now) {
        jdbc.update("DELETE FROM sessions WHERE expires_at <= ?", Timestamp.from(now));
    }
}
