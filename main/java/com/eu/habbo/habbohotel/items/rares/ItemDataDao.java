package com.eu.habbo.habbohotel.items.rares;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import static org.reflections.Reflections.log;

public class ItemDataDao {
    private static final String SELECT_CIRCULATION_QUERY = "SELECT COUNT(item_id) as item_count FROM items WHERE item_id = ?";
    private static final String INSERT_WEIGHT_QUERY = "INSERT INTO rare_values (item_id, weight, last_update) VALUES (?, ?, ?)";
    private static final String UPDATE_WEIGHT_QUERY = "UPDATE rares SET current_weight = (current_weight + ?) / 2, last_update = ? WHERE item_id = ?";
    private static final int DEADLOCK_SQL_ERROR_CODE = 1213;
    private static final int MAX_RETRIES = 3;
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemDataDao.class);


    public int getCirculation(int itemId) {
        return executeWithPreparedStatement(SELECT_CIRCULATION_QUERY, stmt -> {
            stmt.setInt(1, itemId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("item_count");
                }
                return 0; // default value if no results
            }
        }).orElse(0); // Use default value if Optional is empty
    }

    private void insertWeight(int itemId, double weight) throws SQLException {
        executeVoidWithPreparedStatement(INSERT_WEIGHT_QUERY, stmt -> {
            stmt.setInt(1, itemId);
            stmt.setDouble(2, weight);
            stmt.setInt(3, Emulator.getIntUnixTimestamp());
            stmt.executeUpdate();
        });
    }
    private void updateWeight(int itemId, double weight) throws SQLException {
        executeVoidWithPreparedStatement(UPDATE_WEIGHT_QUERY, stmt -> {
            stmt.setDouble(1, weight);
            stmt.setInt(2, Emulator.getIntUnixTimestamp());
            stmt.setInt(3, itemId);
            stmt.executeUpdate();

            // Update current_weight in memory.
            RareValuesManager manager = RareValuesManager.getInstance();
            manager.updateRareWeight(itemId, weight);
        });
    }
    public void insertOrUpdateWeight(int itemId, double weight) {

        var attempts = 0;
        while (attempts < MAX_RETRIES) {
            try {
                insertWeight(itemId, weight);
                updateWeight(itemId, weight);
                break; // if successful, break out of the loop
            } catch (SQLException e) {
                if (e.getErrorCode() == DEADLOCK_SQL_ERROR_CODE) {
                    attempts++;
                    if (attempts >= MAX_RETRIES) {
                        LOGGER.error("Failed to update weight after " + MAX_RETRIES + " attempts due to deadlock.", e);
                    }
                    try {
                        Thread.sleep(1000); // wait for 1 second before retrying
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Thread was interrupted during sleep", ie);
                    }
                } else {
                    LOGGER.error("Caught SQL exception", e);
                    break;
                }
            }
        }
    }

    private <R> Optional<R> executeWithPreparedStatement(String query, PreparedStatementAction<R> action) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            return Optional.ofNullable(action.execute(stmt));
        } catch (SQLException e) {
            log.error("Caught SQL exception", e);
            return Optional.empty();
        }
    }
    private void executeVoidWithPreparedStatement(String query, PreparedStatementVoidAction action) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            action.execute(stmt);
        } catch (SQLException e) {
            log.error("Caught SQL exception", e);
        }
    }
}
