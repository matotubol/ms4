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

    // Private constructor ensures this class can only be instantiated once
    private RareValuesManager() {
        initialize();
    }

    // Singleton pattern - provides the single instance of this class
    public static RareValuesManager getInstance() {
        return instance;
    }

    // Made this private to ensure it's only called once during object creation
    private void initialize() {
        long millis = System.currentTimeMillis();
        List<Integer> fetchedRareItemIds = fetchAllRareItemIdsFromDatabase();
        rareItemIds.addAll(fetchedRareItemIds);
        LOGGER.info("RareValuesManager -> Loaded " + fetchedRareItemIds.size() + " rares! (" + (System.currentTimeMillis() - millis) + " MS)");
    }

    private List<Integer> fetchAllRareItemIdsFromDatabase() {
        List<Integer> fetchedItemIds = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT item_id FROM rares");
             ResultSet set = statement.executeQuery()) {

            while (set.next()) {
                fetchedItemIds.add(set.getInt("item_id"));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        return fetchedItemIds;
    }

    public boolean isItemRare(int itemId) {
        return rareItemIds.contains(itemId);
    }
}
