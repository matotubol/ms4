package com.eu.habbo.habbohotel.items.rares;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import com.eu.habbo.habbohotel.items.rares.ItemDataDao;

public class TradingAdjustmentService {

    private final ItemDataDao itemDataDao = new ItemDataDao();
    RareValuesManager manager = RareValuesManager.getInstance();

    private static final int DECIMAL_PRECISION = 5;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private double round(double value) {
        return new BigDecimal(value).setScale(DECIMAL_PRECISION, ROUNDING_MODE).doubleValue();
    }
    private double calculateDynamicValue(int itemId) {
        double baseValue = round(manager.getRareWeight(itemId));
        double circulation = round(itemDataDao.getCirculation(itemId));
        double supply = round(manager.getRareSupplyCount(itemId));

        double scarcity = round((supply - circulation) / supply);
        return round(baseValue * (1 + 0.1 * scarcity));
    }
    public void adjustAndPersistWeights(Map<Integer, RareItemData> userOneItems, Map<Integer, RareItemData> userTwoItems) {
        adjustWeights(userOneItems, userTwoItems);

        Stream.concat(userOneItems.keySet().stream(), userTwoItems.keySet().stream())
                .distinct()
                .forEach(itemId -> {
                    RareItemData data = Optional.ofNullable(userOneItems.get(itemId)).orElse(userTwoItems.get(itemId));
                    itemDataDao.insertOrUpdateWeight(itemId, data.getWeight());
                });
    }
    private void adjustWeights(Map<Integer, RareItemData> userOneItems, Map<Integer, RareItemData> userTwoItems) {
        double userOneAggregateWeight = calculateAggregateWeight(userOneItems);
        double userTwoAggregateWeight = calculateAggregateWeight(userTwoItems);

        double weightDifference = userOneAggregateWeight - userTwoAggregateWeight;
        double adjustment = calculateAdjustment(weightDifference);

        if (weightDifference > 0.1) {
            // UserOne has a significantly higher weight, so we subtract from UserOne and add to UserTwo
            distributeAdjustment(userOneItems, -adjustment);
            distributeAdjustment(userTwoItems, adjustment);
        } else if (weightDifference < -0.1) {
            // UserTwo has a significantly higher weight, so we add to UserOne and subtract from UserTwo
            distributeAdjustment(userOneItems, adjustment);
            distributeAdjustment(userTwoItems, -adjustment);
        }
        // if weightDifference is between -0.1 and 0.1, no adjustment needed
    }



    private double calculateAggregateWeight(Map<Integer, RareItemData> items) {
        return items.entrySet().stream()
                .mapToDouble(entry -> calculateDynamicValue(entry.getKey()) * entry.getValue().getCount())
                .sum();
    }

    private double calculateAdjustment(double weightDifference) {
        double adjustment;

        if (Math.abs(weightDifference) > 0.1) {
            adjustment = round(0.1 * Math.signum(weightDifference) * 0.05);
        } else {
            adjustment = round(weightDifference * 0.05);
        }
        
        return adjustment;
    }

    private void distributeAdjustment(Map<Integer, RareItemData> items, double totalAdjustment) {
        double totalItemCount = round(items.values().stream().mapToDouble(RareItemData::getCount).sum());
        double individualAdjustment = round(totalAdjustment / totalItemCount);

        items.values().forEach(itemData -> {
            double currentWeight = round(itemData.getWeight());
            itemData.setWeight(round(currentWeight + individualAdjustment * itemData.getCount()));
        });
    }
}
