package com.eu.habbo.habbohotel.items.rares;

public class InitializerData {
    private final int itemId; // Made this final as itemId should not change once set
    private int supply;
    private double weight;

    public InitializerData(int itemId, int supply, double weight) {
        this.itemId = itemId;
        this.supply = supply;
        this.weight = weight;
    }

    // Getters
    public int getItemId() {
        return itemId;
    }

    public int getSupply() {
        return supply;
    }

    public double getWeight() {
        return weight;
    }

    // Setters (no setter for itemId since it's final)
    public void setSupply(int supply) {
        this.supply = supply;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }
}
