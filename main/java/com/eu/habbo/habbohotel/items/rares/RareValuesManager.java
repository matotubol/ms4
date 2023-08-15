package com.eu.habbo.habbohotel.items.rares;

import com.eu.habbo.Emulator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RareValuesManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RareValuesManager.class);
    private final Set<Integer> rareItemIds = new HashSet<>();

    // Initialization method to load rare items from the database
    public void initialize() {
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
