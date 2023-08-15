package com.eu.habbo.habbohotel.items.rares;
import lombok.Getter;
@Getter
public class RareItemData {
    
    private double weight;
    private int count;
    private final int rareItemId;
    public RareItemData(int rareItemId, double weight, int count) {
        this.rareItemId = rareItemId;
        this.weight = weight;
        this.count = count;
    }
    public void setWeight(double weight) {
        this.weight = weight;
    }
    public void setCount(int count) {
        this.count = count;
    }

}
