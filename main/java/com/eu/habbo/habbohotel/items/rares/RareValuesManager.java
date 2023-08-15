package com.eu.habbo.habbohotel.items.rares;

import com.eu.habbo.Emulator;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

                // Round the weight to 5 decimals
                BigDecimal weightValue = BigDecimal.valueOf(set.getDouble("current_weight")).setScale(5, RoundingMode.HALF_UP);
                double weight = weightValue.doubleValue();

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
    public void updateRareWeight(int itemId, double newWeight) {
        InitializerData rareItem = rareItemsMap.get(itemId);
        if (rareItem != null) {
            double currentWeight = rareItem.getWeight();
            double averageWeight = (currentWeight + newWeight) / 2;

            // Round the average weight to 5 decimals
            BigDecimal roundedWeight = BigDecimal.valueOf(averageWeight).setScale(5, RoundingMode.HALF_UP);
            rareItem.setWeight(roundedWeight.doubleValue());
        } else {
            LOGGER.warn("No rare item found for item ID: " + itemId);
        }
        logAllItemWeights();

    }
    private void logAllItemWeights() {
        LOGGER.info("Logging all item weights:");
        for (Map.Entry<Integer, InitializerData> entry : rareItemsMap.entrySet()) {
            LOGGER.info("Item ID: " + entry.getKey() + ", Weight: " + entry.getValue().getWeight());
        }
    }
}
