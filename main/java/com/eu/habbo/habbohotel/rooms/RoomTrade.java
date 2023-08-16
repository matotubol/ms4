package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.items.rares.RareValuesManager;
import com.eu.habbo.habbohotel.items.rares.RareItemData;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.inventory.FurniListInvalidateComposer;
import com.eu.habbo.messages.outgoing.inventory.UnseenItemsComposer;
import com.eu.habbo.messages.outgoing.rooms.users.UserUpdateComposer;
import com.eu.habbo.messages.outgoing.trading.*;
import com.eu.habbo.plugin.events.trading.TradeConfirmEvent;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;
import gnu.trove.set.hash.THashSet;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class RoomTrade {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomTrade.class);

    //Configuration. Loaded from database & updated accordingly.
    public static boolean TRADING_ENABLED = true;
    public static boolean TRADING_REQUIRES_PERK = true;

    private final List<RoomTradeUser> users;
    private static final ConcurrentHashMap<Integer, Long> cooldownMap = new ConcurrentHashMap<>();
    private final Room room;

    public RareItemData RareItemData;
    RareValuesManager manager = RareValuesManager.getInstance();


    public RoomTrade(Habbo userOne, Habbo userTwo, Room room, RareItemData rareItemData) {
        this.users = new ArrayList<>();
        this.users.add(new RoomTradeUser(userOne));
        this.users.add(new RoomTradeUser(userTwo));
        this.room = room;
        this.RareItemData = rareItemData;
    }

    public void start() {
        this.initializeTradeStatus();
        this.openTrade();
    }

    protected void initializeTradeStatus() {
        for (RoomTradeUser roomTradeUser : this.users) {
            if (!roomTradeUser.getHabbo().getRoomUnit().hasStatus(RoomUnitStatus.TRADING)) {
                roomTradeUser.getHabbo().getRoomUnit().setStatus(RoomUnitStatus.TRADING, "");
                if (!roomTradeUser.getHabbo().getRoomUnit().isWalking())
                    this.room.sendComposer(new UserUpdateComposer(roomTradeUser.getHabbo().getRoomUnit()).compose());
            }
        }
    }

    protected void openTrade() {
        this.sendMessageToUsers(new TradingOpenComposer(this));
    }

    public void offerItem(Habbo habbo, HabboItem item) {
        RoomTradeUser user = this.getRoomTradeUserForHabbo(habbo);

        if (user.getItems().contains(item))
            return;

        habbo.getInventory().getItemsComponent().removeHabboItem(item);
        user.getItems().add(item);

        this.clearAccepted();
        this.updateWindow();
    }

    public void offerMultipleItems(Habbo habbo, THashSet<HabboItem> items) {
        RoomTradeUser user = this.getRoomTradeUserForHabbo(habbo);

        for (HabboItem item : items) {
            if (!user.getItems().contains(item)) {
                habbo.getInventory().getItemsComponent().removeHabboItem(item);
                user.getItems().add(item);
            }
        }

        this.clearAccepted();
        this.updateWindow();
    }

    public void removeItem(Habbo habbo, HabboItem item) {
        RoomTradeUser user = this.getRoomTradeUserForHabbo(habbo);

        if (!user.getItems().contains(item))
            return;

        habbo.getInventory().getItemsComponent().addItem(item);
        user.getItems().remove(item);

        this.clearAccepted();
        this.updateWindow();
    }

    public void accept(Habbo habbo, boolean value) {
        RoomTradeUser user = this.getRoomTradeUserForHabbo(habbo);

        user.setAccepted(value);

        this.sendMessageToUsers(new TradingAcceptComposer(user));
        boolean accepted = true;
        for (RoomTradeUser roomTradeUser : this.users) {
            if (!roomTradeUser.isAccepted()) {
                accepted = false;
                break;
            }
        }
        if (accepted) {
            this.sendMessageToUsers(new TradingConfirmationComposer());
        }
    }

    public void confirm(Habbo habbo) {
        RoomTradeUser user = this.getRoomTradeUserForHabbo(habbo);

        user.confirm();

        this.sendMessageToUsers(new TradingAcceptComposer(user));
        boolean accepted = true;
        for (RoomTradeUser roomTradeUser : this.users) {
            if (!roomTradeUser.isConfirmed()) {
                accepted = false;
                break;
            }
        }
        if (accepted) {
            if (this.tradeItems()) {
                this.closeWindow();
                this.sendMessageToUsers(new TradingNotOpenComposer());
            }

            this.room.stopTrade(this);
        }
    }

    boolean tradeItems() {

        for (RoomTradeUser roomTradeUser : this.users) {
            for (HabboItem item : roomTradeUser.getItems()) {
                if (roomTradeUser.getHabbo().getInventory().getItemsComponent().getHabboItem(item.getId()) != null) {
                    this.sendMessageToUsers(new TradingCloseComposer(roomTradeUser.getHabbo().getRoomUnit().getId(), TradingCloseComposer.ITEMS_NOT_FOUND));
                    return false;
                }
            }
        }

        RoomTradeUser userOne = this.users.get(0);
        RoomTradeUser userTwo = this.users.get(1);

        boolean tradeConfirmEventRegistered = Emulator.getPluginManager().isRegistered(TradeConfirmEvent.class, true);
        TradeConfirmEvent tradeConfirmEvent = new TradeConfirmEvent(userOne, userTwo);
        if (tradeConfirmEventRegistered) {
            Emulator.getPluginManager().fireEvent(tradeConfirmEvent);
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {

            int tradeId = 0;

            boolean logTrades = Emulator.getConfig().getBoolean("hotel.log.trades");
            if (logTrades) {
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO room_trade_log (user_one_id, user_two_id, user_one_ip, user_two_ip, timestamp, user_one_item_count, user_two_item_count) VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    statement.setInt(1, userOne.getHabbo().getHabboInfo().getId());
                    statement.setInt(2, userTwo.getHabbo().getHabboInfo().getId());
                    statement.setString(3, userOne.getHabbo().getHabboInfo().getIpLogin());
                    statement.setString(4, userTwo.getHabbo().getHabboInfo().getIpLogin());
                    statement.setInt(5, Emulator.getIntUnixTimestamp());
                    statement.setInt(6, userOne.getItems().size());
                    statement.setInt(7, userTwo.getItems().size());
                    statement.executeUpdate();
                    try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            tradeId = generatedKeys.getInt(1);
                        }
                    }
                }
            }

            int userOneId = userOne.getHabbo().getHabboInfo().getId();
            int userTwoId = userTwo.getHabbo().getHabboInfo().getId();


            try (PreparedStatement statement = connection.prepareStatement("UPDATE items SET user_id = ? WHERE id = ? LIMIT 1")) {
                try (PreparedStatement stmt = connection.prepareStatement("INSERT INTO room_trade_log_items (id, item_id, user_id) VALUES (?, ?, ?)")) {
                    try (PreparedStatement rareStatement = connection.prepareStatement("INSERT INTO rares_trading_feed (trade_id, user_id, item_id, quantity, trade_timestamp) VALUES (?, ?, ?, ?, ?)")) {

                        //TODO get the itemsCount from userTwoValue instead of new MAP not necessary
                        Map<Integer, Integer> rareItemsCountUserOne = new HashMap<>();
                        ConcurrentHashMap<Integer, RareItemData> userOneValues = new ConcurrentHashMap<>();


                        // Clear out expired cool downs for rares
                            cooldownMap.entrySet().removeIf(entry -> System.currentTimeMillis() > entry.getValue());

                        for (HabboItem item : userOne.getItems()) {

                            item.setUserId(userTwoId);
                            int catalogItemId = item.getBaseItem().getId();

                            if (manager.isItemRare(catalogItemId)) {

                                //TODO add check when trading 1 non cooled item against cooled item to not impact the economy only if or maybe weight difference ?
                                    Long cooldownEnd = cooldownMap.get(item.getId());

//                                    if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
//                                        // Item is on cool down, skip to the next item
//                                        continue;
//                                    }

                                    RareItemData details = userOneValues.getOrDefault(catalogItemId, new RareItemData(0,0.0, 0));
                                    // Update weight
                                    details.setWeight(manager.getRareWeight(catalogItemId));
                                    // Update count
                                    details.setCount(details.getCount() + 1);
                                    // Put the updated details back into the map
                                    userOneValues.put(catalogItemId, details);

                                    rareItemsCountUserOne.put(catalogItemId, rareItemsCountUserOne.getOrDefault(catalogItemId, 0) + 1);

                                    // Set the cool down for this item
                                    cooldownMap.put(item.getId(), System.currentTimeMillis() + (5 * 60 * 1000)); // 5 minutes in milliseconds

                            }

                            statement.setInt(1, userTwoId);
                            statement.setInt(2, item.getId());
                            statement.addBatch();

                            if (logTrades) {
                                stmt.setInt(1, tradeId);
                                stmt.setInt(2, item.getId());
                                stmt.setInt(3, userOneId);
                                stmt.addBatch();
                            }
                        }

                        Map<Integer, Integer> rareItemsCountUserTwo = new HashMap<>();
                        Map<Integer, RareItemData> userTwoValues = new HashMap<>();

                        // Clear out expired cool downs for rares
                        synchronized(cooldownMap) {
                            cooldownMap.entrySet().removeIf(entry -> System.currentTimeMillis() > entry.getValue());
                        }

                        for (HabboItem item : userTwo.getItems()) {

                            item.setUserId(userOneId);
                            int catalogItemId = item.getBaseItem().getId();

                            if (manager.isItemRare(catalogItemId)) {
                                synchronized(cooldownMap) {
                                    Long cooldownEnd = cooldownMap.get(item.getId());

//                                    if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
//                                        // Item is on cool down, skip to the next item
//                                        continue;
//                                    }

                                    RareItemData details = userOneValues.getOrDefault(catalogItemId, new RareItemData(0,0.0, 0));
                                    // Update weight
                                    details.setWeight(manager.getRareWeight(catalogItemId));
                                    // Update count
                                    details.setCount(details.getCount() + 1);
                                    // Put the updated details back into the map
                                    userTwoValues.put(catalogItemId, details);

                                    rareItemsCountUserTwo.put(catalogItemId, rareItemsCountUserTwo.getOrDefault(catalogItemId, 0) + 1);
                                    //TODO make this configurable through database.
                                    cooldownMap.put(item.getId(), System.currentTimeMillis() + (5 * 60 * 1000)); // 5 minutes in milliseconds
                                }
                            }
                            statement.setInt(1, userOneId);
                            statement.setInt(2, item.getId());
                            statement.addBatch();


                            if (logTrades) {
                                stmt.setInt(1, tradeId);
                                stmt.setInt(2, item.getId());
                                stmt.setInt(3, userTwoId);
                                stmt.addBatch();
                            }
                        }

                        compareDynamicValues(userOneValues, userTwoValues);

                        // Insert into rares_trading_feed for userOne's items
                        insertRareTradingFeed(tradeId, userOneId, rareStatement, rareItemsCountUserOne);

                        // Insert into rares_trading_feed for userTwo's items
                        insertRareTradingFeed(tradeId, userTwoId, rareStatement, rareItemsCountUserTwo);

                        rareStatement.executeBatch();


                        if (logTrades) {
                            stmt.executeBatch();
                        }
                    }
                }

                statement.executeBatch();
            }
        } catch (SQLException e) {
            log.error("Caught SQL exception", e);
        }

        THashSet<HabboItem> itemsUserOne = new THashSet<>(userOne.getItems());
        THashSet<HabboItem> itemsUserTwo = new THashSet<>(userTwo.getItems());

        userOne.clearItems();
        userTwo.clearItems();

        int creditsForUserTwo = 0;
        THashSet<HabboItem> creditFurniUserOne = new THashSet<>();
        for (HabboItem item : itemsUserOne) {
            int worth = RoomTrade.getCreditsByItem(item);
            if (worth > 0) {
                creditsForUserTwo += worth;
                creditFurniUserOne.add(item);
                new QueryDeleteHabboItem(item).run();
            }
        }
        itemsUserOne.removeAll(creditFurniUserOne);

        int creditsForUserOne = 0;
        THashSet<HabboItem> creditFurniUserTwo = new THashSet<>();
        for (HabboItem item : itemsUserTwo) {
            int worth = RoomTrade.getCreditsByItem(item);
            if (worth > 0) {
                creditsForUserOne += worth;
                creditFurniUserTwo.add(item);
                new QueryDeleteHabboItem(item).run();
            }
        }
        itemsUserTwo.removeAll(creditFurniUserTwo);

        userOne.getHabbo().giveCredits(creditsForUserOne);
        userTwo.getHabbo().giveCredits(creditsForUserTwo);

        userOne.getHabbo().getInventory().getItemsComponent().addItems(itemsUserTwo);
        userTwo.getHabbo().getInventory().getItemsComponent().addItems(itemsUserOne);

        userOne.getHabbo().getClient().sendResponse(new UnseenItemsComposer(itemsUserTwo));
        userTwo.getHabbo().getClient().sendResponse(new UnseenItemsComposer(itemsUserOne));

        userOne.getHabbo().getClient().sendResponse(new FurniListInvalidateComposer());
        userTwo.getHabbo().getClient().sendResponse(new FurniListInvalidateComposer());
        return true;
    }

    protected void clearAccepted() {
        for (RoomTradeUser user : this.users) {
            user.setAccepted(false);
        }
    }

    protected void updateWindow() {
        this.sendMessageToUsers(new TradingItemListComposer(this));
    }

    private void returnItems() {
        for (RoomTradeUser user : this.users) {
            user.putItemsIntoInventory();
        }
    }

    private void closeWindow() {
        this.removeStatusses();
        this.sendMessageToUsers(new TradeCloseWindowComposer());
    }

    public void stopTrade(Habbo habbo) {
        this.removeStatusses();
        this.clearAccepted();
        this.returnItems();
        for (RoomTradeUser user : this.users) {
            user.clearItems();
        }
        this.updateWindow();
        this.sendMessageToUsers(new TradingCloseComposer(habbo.getHabboInfo().getId(), TradingCloseComposer.USER_CANCEL_TRADE));
        this.room.stopTrade(this);
    }

    private void removeStatusses() {
        for (RoomTradeUser user : this.users) {
            Habbo habbo = user.getHabbo();

            if (habbo == null)
                continue;

            habbo.getRoomUnit().removeStatus(RoomUnitStatus.TRADING);
            this.room.sendComposer(new UserUpdateComposer(habbo.getRoomUnit()).compose());
        }
    }

    public RoomTradeUser getRoomTradeUserForHabbo(Habbo habbo) {
        for (RoomTradeUser roomTradeUser : this.users) {
            if (roomTradeUser.getHabbo() == habbo)
                return roomTradeUser;
        }
        return null;
    }

    public void sendMessageToUsers(MessageComposer message) {
        for (RoomTradeUser roomTradeUser : this.users) {
            roomTradeUser.getHabbo().getClient().sendResponse(message);
        }
    }

    public List<RoomTradeUser> getRoomTradeUsers() {
        return this.users;
    }

    public static int getCreditsByItem(HabboItem item) {
        if (!Emulator.getConfig().getBoolean("redeem.currency.trade")) return 0;

        if (!item.getBaseItem().getName().startsWith("CF_") && !item.getBaseItem().getName().startsWith("CFC_")) return 0;

        try {
            return Integer.parseInt(item.getBaseItem().getName().split("_")[1]);
        } catch (Exception e) {
            return 0;
        }
    }
    public void incrementTradeCountIfRare(int itemId) {

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement stmt = connection.prepareStatement("UPDATE items SET traded_count = traded_count + 1 WHERE id = ? LIMIT 1")) {

            stmt.setInt(1, itemId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            log.error("Caught SQL exception", e);
        }
    }
    private void insertRareTradingFeed(int tradeId, int userId, PreparedStatement rareStatement, Map<Integer, Integer> rareItemsCountUserTwo) throws SQLException {
        for (Map.Entry<Integer, Integer> entry : rareItemsCountUserTwo.entrySet()) {

            incrementTradeCountIfRare(entry.getKey());
            rareStatement.setInt(1, tradeId);
            rareStatement.setInt(2, userId);
            rareStatement.setInt(3, entry.getKey());
            rareStatement.setInt(4, entry.getValue());
            rareStatement.setInt(5, Emulator.getIntUnixTimestamp());
            rareStatement.addBatch();
        }
    }
    public double getCirculation(int itemId) {
        return fetchFromDatabase("SELECT COUNT(item_id) as item_count FROM items WHERE item_id = ?", "item_count", itemId, Integer.class);

    }
    private void insertWeight(int itemId, double weight) throws SQLException {
        String insertQuery = "INSERT INTO rare_values (item_id, weight, last_update) VALUES (?, ?, ?)";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement insertStmt = connection.prepareStatement(insertQuery)) {

            insertStmt.setInt(1, itemId);
            insertStmt.setDouble(2, weight);
            insertStmt.setInt(3, Emulator.getIntUnixTimestamp());
            insertStmt.executeUpdate();

        } catch (SQLException e) {
            log.error("Caught SQL exception", e);
        }
    }

    private void updateWeight(int itemId, double weight) throws SQLException {
        String updateQuery = "UPDATE rares SET current_weight = (current_weight + ?) / 2, last_update = ? WHERE item_id = ?";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement updateStmt = connection.prepareStatement(updateQuery)) {

            updateStmt.setDouble(1, weight);
            updateStmt.setInt(2, Emulator.getIntUnixTimestamp());
            updateStmt.setInt(3, itemId);
            updateStmt.executeUpdate();

            //Update current_weight in memory.
            RareValuesManager manager = RareValuesManager.getInstance();
            manager.updateRareWeight(itemId, weight);

        } catch (SQLException e) {
            log.error("Caught SQL exception", e);
        }
    }
    public void insertOrUpdateWeight(int itemId, double weight) {
        final int MAX_RETRIES = 3; // or any other reasonable number
        final int DEADLOCK_SQL_ERROR_CODE = 1213;
        int attempts = 0;

        while (attempts < MAX_RETRIES) {
            try {
                insertWeight(itemId, weight);
                updateWeight(itemId, weight);

                break; // if successful, break out of the loop
            } catch (SQLException e) {
                if (e.getErrorCode() == DEADLOCK_SQL_ERROR_CODE) {
                    attempts++;
                    if (attempts >= MAX_RETRIES) {
                        log.error("Failed to update weight after " + MAX_RETRIES + " attempts due to deadlock.", e);
                    }
                    try {
                        Thread.sleep(1000); // wait for 1 second before retrying
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Thread was interrupted during sleep", ie);
                    }
                }
            }
        }
    }
    private double calculateDynamicValue(int itemId) {
        double baseValue = manager.getRareWeight(itemId);
        double circulation = getCirculation(itemId);
        double supply = manager.getRareSupplyCount(itemId);

        // Calculate the scarcity for the item
        double scarcity = (supply - circulation) / supply;

        // Adjust the base weight based on scarcity (this is just a simple adjustment)

        return baseValue * (1 + 0.1 * scarcity);
    }

    public void compareDynamicValues(Map<Integer, RareItemData> userOneItems, Map<Integer, RareItemData> userTwoItems) {
        adjustWeights(userOneItems, userTwoItems);

        // Update the weights in the database
        for (Integer itemId : userOneItems.keySet()) {
            insertOrUpdateWeight(itemId, userOneItems.get(itemId).getWeight());
        }
        for (Integer itemId : userTwoItems.keySet()) {
            insertOrUpdateWeight(itemId, userTwoItems.get(itemId).getWeight());
        }
    }

    private void adjustWeights(Map<Integer, RareItemData> userOneItems, Map<Integer, RareItemData> userTwoItems) {
        // Calculate the dynamic value for each item
        Map<Integer, Double> userOneDynamicValues = new HashMap<>();
        for (Integer itemId : userOneItems.keySet()) {
            userOneDynamicValues.put(itemId, calculateDynamicValue(itemId) * userOneItems.get(itemId).getCount());
        }

        Map<Integer, Double> userTwoDynamicValues = new HashMap<>();
        for (Integer itemId : userTwoItems.keySet()) {
            userTwoDynamicValues.put(itemId, calculateDynamicValue(itemId) * userTwoItems.get(itemId).getCount());
        }

        double userOneAggregateWeight = userOneDynamicValues.values().stream().mapToDouble(Double::doubleValue).sum();
        double userTwoAggregateWeight = userTwoDynamicValues.values().stream().mapToDouble(Double::doubleValue).sum();

        double weightDifference = userOneAggregateWeight - userTwoAggregateWeight;

        // Calculate the adjustment based on the original weightDifference
        double adjustment = weightDifference * 0.05;

        // Ensure the adjustment doesn't exceed the threshold of 0.05 in either direction
        if (Math.abs(adjustment) > 0.05) {
            adjustment = Math.signum(adjustment) * 0.05;
        }

        if (weightDifference > 0) {
            distributeAdjustment(userOneItems, -adjustment);
            distributeAdjustment(userTwoItems, adjustment);
        } else {
            distributeAdjustment(userOneItems, adjustment);
            distributeAdjustment(userTwoItems, -adjustment);
        }
    }
    private void distributeAdjustment(Map<Integer, RareItemData> items, double totalAdjustment) {
        double totalItemCount = 0;

        // Calculate the total item count considering duplicates
        for (RareItemData details : items.values()) {
            totalItemCount += details.getCount();
        }

        double individualAdjustment = totalAdjustment / totalItemCount;

        for (Integer itemId : items.keySet()) {
            RareItemData details = items.get(itemId);
            double currentWeight = details.getWeight();

            details.setWeight(currentWeight + individualAdjustment * details.getCount());
        }
    }

    private <T> T fetchFromDatabase(String query, String columnName, int itemId, Class<T> type) {
        T value = null;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setInt(1, itemId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    if (type == Integer.class) {
                        value = type.cast(rs.getInt(columnName));
                    } else if (type == Double.class) {
                        value = type.cast(rs.getDouble(columnName));
                    } else if (type == Boolean.class) {
                        value = type.cast(rs.getBoolean(columnName));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Caught SQL exception", e);
        }
        return value;
    }

}

