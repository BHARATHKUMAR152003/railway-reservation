package com.portfolio.railway.dao;

import com.portfolio.railway.model.Models.Station;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StationDao {
    private final JdbcTemplate jdbc;

    public StationDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Station> findAll() {
        return jdbc.query("SELECT code, name, city FROM stations ORDER BY city, name",
                (rs, i) -> new Station(rs.getString("code"), rs.getString("name"), rs.getString("city")));
    }

    public Optional<Station> find(String code) {
        return jdbc.query("SELECT code, name, city FROM stations WHERE code = ?",
                (rs, i) -> new Station(rs.getString("code"), rs.getString("name"), rs.getString("city")), code)
                .stream().findFirst();
    }

    public boolean exists(String code) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM stations WHERE code = ?", Integer.class, code);
        return n != null && n > 0;
    }

    public void insert(Station s) {
        jdbc.update("INSERT INTO stations (code, name, city) VALUES (?, ?, ?)", s.code(), s.name(), s.city());
    }
}
