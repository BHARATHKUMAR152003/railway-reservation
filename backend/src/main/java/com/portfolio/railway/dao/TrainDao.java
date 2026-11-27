package com.portfolio.railway.dao;

import com.portfolio.railway.model.Models.Train;
import com.portfolio.railway.model.Models.TrainClass;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class TrainDao {
    private static final String COLUMNS =
            "id, number, name, source, destination, departs, duration_minutes, run_days, active";
    private static final String CLASS_ORDER =
            "CASE class_code WHEN '1A' THEN 1 WHEN '2A' THEN 2 WHEN '3A' THEN 3 WHEN 'CC' THEN 4 WHEN 'SL' THEN 5 ELSE 6 END";

    private final JdbcTemplate jdbc;

    public TrainDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private Train map(ResultSet rs, List<TrainClass> classes) throws SQLException {
        return new Train(rs.getInt("id"), rs.getString("number"), rs.getString("name"), rs.getString("source"),
                rs.getString("destination"), rs.getTime("departs").toLocalTime(), rs.getInt("duration_minutes"),
                rs.getString("run_days"), rs.getBoolean("active"), classes);
    }

    private List<Train> withClasses(String sql, Object... args) {
        List<Train> bare = jdbc.query(sql, (rs, i) -> map(rs, List.of()), args);
        if (bare.isEmpty()) {
            return bare;
        }
        Map<Integer, List<TrainClass>> classes = jdbc.query(
                "SELECT train_id, class_code, seats, fare FROM train_classes ORDER BY " + CLASS_ORDER,
                (rs, i) -> Map.entry(rs.getInt("train_id"),
                        new TrainClass(rs.getString("class_code"), rs.getInt("seats"), rs.getBigDecimal("fare"))))
                .stream().collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        return bare.stream().map(t -> new Train(t.id(), t.number(), t.name(), t.source(), t.destination(), t.departs(),
                t.durationMinutes(), t.runDays(), t.active(), classes.getOrDefault(t.id(), List.of()))).toList();
    }

    public List<Train> findAll() {
        return withClasses("SELECT " + COLUMNS + " FROM trains ORDER BY number");
    }

    public Optional<Train> findById(int id) {
        return withClasses("SELECT " + COLUMNS + " FROM trains WHERE id = ?", id).stream().findFirst();
    }

    public boolean numberExists(String number) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM trains WHERE number = ?", Integer.class, number);
        return n != null && n > 0;
    }

    public List<Train> findRoute(String from, String to) {
        return withClasses("SELECT " + COLUMNS + " FROM trains WHERE source = ? AND destination = ? AND active = TRUE"
                + " ORDER BY departs", from, to);
    }

    /** Locks the class row so concurrent bookings for this train and class are allocated one at a time. */
    public Optional<TrainClass> lockClass(int trainId, String classCode) {
        return jdbc.query("SELECT class_code, seats, fare FROM train_classes WHERE train_id = ? AND class_code = ? FOR UPDATE",
                (rs, i) -> new TrainClass(rs.getString("class_code"), rs.getInt("seats"), rs.getBigDecimal("fare")),
                trainId, classCode).stream().findFirst();
    }

    public int insert(Train t) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO trains (number, name, source, destination, departs, duration_minutes, run_days, active)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)", new String[] {"id"});
            ps.setString(1, t.number());
            ps.setString(2, t.name());
            ps.setString(3, t.source());
            ps.setString(4, t.destination());
            ps.setTime(5, Time.valueOf(t.departs()));
            ps.setInt(6, t.durationMinutes());
            ps.setString(7, t.runDays());
            ps.setBoolean(8, t.active());
            return ps;
        }, keys);
        int id = keys.getKey().intValue();
        for (TrainClass c : t.classes()) {
            jdbc.update("INSERT INTO train_classes (train_id, class_code, seats, fare) VALUES (?, ?, ?, ?)",
                    id, c.code(), c.seats(), c.fare());
        }
        return id;
    }

    public void setActive(int id, boolean active) {
        jdbc.update("UPDATE trains SET active = ? WHERE id = ?", active, id);
    }

    public int count() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM trains", Integer.class);
        return n == null ? 0 : n;
    }
}
