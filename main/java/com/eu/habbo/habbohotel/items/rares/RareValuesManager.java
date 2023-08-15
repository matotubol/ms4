package com.eu.habbo.habbohotel.items.rares;

import com.eu.habbo.Emulator;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Getter
public class RareValuesManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RareValuesManager.class);
    private static final RareValuesManager instance = new RareValuesManager();

    private final Set<Integer> rareItemIds = new HashSet<>();
    private final Map<Integer, Integer> rareSupplyCounts = new HashMap<>();  // Map to hold item ID and its supply count

    private RareValuesManager() {
        initialize();
    }

    public static RareValuesManager getInstance() {
        return instance;
    }

    private void initialize() {
        long millis = System.currentTimeMillis();
        fetchAllRareDataFromDatabase();
        for (Map.Entry<Integer, Integer> entry : rareSupplyCounts.entrySet()) {
            LOGGER.info("item_id -> " + entry.getKey() + " Supply -> " + entry.getValue());
        }

        LOGGER.info("RareValuesManager -> Loaded " + rareItemIds.size() + " rares! (" + (System.currentTimeMillis() - millis) + " MS)");

    }

    private void fetchAllRareDataFromDatabase() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT item_id, supply FROM rares");
             ResultSet set = statement.executeQuery()) {

            while (set.next()) {
                int itemId = set.getInt("item_id");
                int supply = set.getInt("supply");

                rareItemIds.add(itemId);
                rareSupplyCounts.put(itemId, supply); // This line associates the supply count with the specific item_id
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }


    public boolean isItemRare(int itemId) {
        return rareItemIds.contains(itemId);
    }

    // Method to fetch the supply count for a rare item
    public int getRareSupplyCount(int itemId) {
        return rareSupplyCounts.getOrDefault(itemId, 0);
    }
}
