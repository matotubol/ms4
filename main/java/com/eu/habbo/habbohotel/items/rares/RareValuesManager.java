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

    private final Map<Integer, InitializerData> rareItemsMap = new HashMap<>();

    private RareValuesManager() {
        initialize();
    }

    public static RareValuesManager getInstance() {
        return instance;
    }

    private void initialize() {
        long millis = System.currentTimeMillis();
        fetchAllRareDataFromDatabase();
        for (InitializerData rareItem : rareItemsMap.values()) {
            LOGGER.info("item_id -> " + rareItem.getItemId() + " Supply -> " + rareItem.getSupply() + " Weight -> " + rareItem.getWeight());
        }

        LOGGER.info("RareValuesManager -> Loaded " + rareItemsMap.size() + " rares! (" + (System.currentTimeMillis() - millis) + " MS)");
    }

    private void fetchAllRareDataFromDatabase() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT item_id, supply, current_weight FROM rares");
             ResultSet set = statement.executeQuery()) {

            while (set.next()) {
                int itemId = set.getInt("item_id");
                int supply = set.getInt("supply");
                double weight = set.getDouble("current_weight");

                InitializerData rareItem = new InitializerData(itemId, supply, weight);
                rareItemsMap.put(itemId, rareItem);
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public boolean isItemRare(int itemId) {
        return rareItemsMap.containsKey(itemId);
    }

    public int getRareSupplyCount(int itemId) {
        InitializerData rareItem = rareItemsMap.get(itemId);
        return rareItem != null ? rareItem.getSupply() : 0;
    }

    public double getRareWeight(int itemId) {
        InitializerData rareItem = rareItemsMap.get(itemId);
        return rareItem != null ? rareItem.getWeight() : 0.0;
    }
}
