package com.eu.habbo.habbohotel.items.rares;

import lombok.Getter;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
public class RareItemData {

    private double weight;
    private int count;
    private final int rareItemId;

    public RareItemData(int rareItemId, double weight, int count) {
        this.rareItemId = rareItemId;
        this.setWeight(weight);  // Use setWeight to ensure the weight is rounded
        this.count = count;
    }

    public void setWeight(double weight) {
        // Round the weight to 5 decimals
        BigDecimal roundedWeight = BigDecimal.valueOf(weight).setScale(5, RoundingMode.HALF_UP);
        this.weight = roundedWeight.doubleValue();
    }

    public void setCount(int count) {
        this.count = count;
    }
}
