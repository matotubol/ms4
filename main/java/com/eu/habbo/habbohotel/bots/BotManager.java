package com.eu.habbo.habbohotel.bots;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.generic.alerts.BotErrorComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.generic.alerts.NotificationDialogMessageComposer;
import com.eu.habbo.messages.outgoing.inventory.BotAddedToInventoryComposer;
import com.eu.habbo.messages.outgoing.inventory.BotRemovedFromInventoryComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUsersComposer;
import com.eu.habbo.messages.outgoing.rooms.users.UserUpdateComposer;
import com.eu.habbo.plugin.events.bots.BotPickUpEvent;
import com.eu.habbo.plugin.events.bots.BotPlacedEvent;
import gnu.trove.map.hash.THashMap;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.sql.*;
import java.util.Map;
@Slf4j
public class BotManager {

    final private static THashMap<String, Class<? extends Bot>> botDefenitions = new THashMap<>();
    public static int MINIMUM_CHAT_SPEED = 7;
    public static int MAXIMUM_CHAT_SPEED = 604800;
    public static int MAXIMUM_CHAT_LENGTH = 120;
    public static int MAXIMUM_NAME_LENGTH = 15;
    public static int MAXIMUM_BOT_INVENTORY_SIZE = 25;

    public BotManager() throws Exception {
        long millis = System.currentTimeMillis();

        addBotDefinition("generic", Bot.class);
        addBotDefinition("bartender", ButlerBot.class);
        addBotDefinition("visitor_log", VisitorBot.class);

        this.reload();

        log.info("Bot Manager -> Loaded! (" + (System.currentTimeMillis() - millis) + " MS)");
    }

    public static void addBotDefinition(String type, Class<? extends Bot> botClazz) throws Exception {
        botClazz.getDeclaredConstructor(ResultSet.class).setAccessible(true);
        botDefenitions.put(type, botClazz);
    }

    public boolean reload() {
        for (Map.Entry<String, Class<? extends Bot>> set : botDefenitions.entrySet()) {
            try {
                Method m = set.getValue().getMethod("initialise");
                m.setAccessible(true);
                m.invoke(null);
            } catch (NoSuchMethodException e) {
                log.info("Bot Manager -> Failed to execute initialise method upon bot type '" + set.getKey() + "'. No Such Method!");
                return false;
            } catch (Exception e) {
                log.info("Bot Manager -> Failed to execute initialise method upon bot type '" + set.getKey() + "'. Error: " + e.getMessage());
                return false;
            }
        }

        return true;
    }

    public Bot createBot(THashMap<String, String> data, String type) {
        Bot bot = null;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO bots (user_id, room_id, name, motto, figure, gender, type) VALUES (0, 0, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, data.get("name"));
            statement.setString(2, data.get("motto"));
            statement.setString(3, data.get("figure"));
            statement.setString(4, data.get("gender").toUpperCase());
            statement.setString(5, type);
            statement.execute();
            try (ResultSet set = statement.getGeneratedKeys()) {
                if (set.next()) {
                    try (PreparedStatement stmt = connection.prepareStatement("SELECT users.username AS owner_name, bots.* FROM bots LEFT JOIN users ON bots.user_id = users.id WHERE bots.id = ? LIMIT 1")) {
                        stmt.setInt(1, set.getInt(1));
                        try (ResultSet resultSet = stmt.executeQuery()) {
                            if (resultSet.next()) {
                                bot = this.loadBot(resultSet);
                            }
                        }
                    } catch (SQLException e) {
                        log.error("Caught SQL exception", e);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Caught SQL exception", e);
        }

        return bot;
    }

    public void placeBot(Bot bot, Habbo habbo, Room room, RoomTile location) {
        BotPlacedEvent event = new BotPlacedEvent(bot, location, habbo);
        Emulator.getPluginManager().fireEvent(event);

        if (event.isCancelled())
            return;

        if (room != null && bot != null && habbo != null) {
            if (room.getOwnerId() == habbo.getHabboInfo().getId() || habbo.hasRight(Permission.ACC_ANYROOMOWNER) || habbo.hasRight(Permission.ACC_PLACEFURNI)) {
                if (room.getCurrentBots().size() >= Room.MAXIMUM_BOTS && !habbo.hasRight(Permission.ACC_UNLIMITED_BOTS)) {
                    habbo.getClient().sendResponse(new BotErrorComposer(BotErrorComposer.ROOM_ERROR_MAX_BOTS));
                    return;
                }

                if (room.hasHabbosAt(location.getX(), location.getY()) || (!location.isWalkable() && location.getState() != RoomTileState.SIT && location.getState() != RoomTileState.LAY))
                    return;

                if (room.hasBotsAt(location.getX(), location.getY())) {
                    habbo.getClient().sendResponse(new BotErrorComposer(BotErrorComposer.ROOM_ERROR_BOTS_SELECTED_TILE_NOT_FREE));
                    return;
                }

                RoomUnit roomUnit = new RoomUnit();
                roomUnit.setRotation(RoomUserRotation.SOUTH);
                roomUnit.setLocation(location);

                double stackHeight = room.getTopHeightAt(location.getX(), location.getY());
                roomUnit.setPreviousLocationZ(stackHeight);
                roomUnit.setZ(stackHeight);

                roomUnit.setPathFinderRoom(room);
                roomUnit.setRoomUnitType(RoomUnitType.BOT);
                roomUnit.setCanWalk(room.isAllowBotsWalk());
                bot.setRoomUnit(roomUnit);
                bot.setRoom(room);
                bot.onPlaceUpdate();
                room.addBot(bot);
                Emulator.getThreading().run(bot);
                room.sendComposer(new RoomUsersComposer(bot).compose());
                room.sendComposer(new UserUpdateComposer(bot.getRoomUnit()).compose());
                habbo.getInventory().getBotsComponent().removeBot(bot);
                habbo.getClient().sendResponse(new BotRemovedFromInventoryComposer(bot));
                bot.onPlace(habbo, room);

                HabboItem topItem = room.getTopItemAt(location.getX(), location.getY());

                if (topItem != null) {
                    try {
                        topItem.onWalkOn(bot.getRoomUnit(), room, null);
                    } catch (Exception e) {
                        log.error("Caught exception", e);
                    }
                }

                bot.cycle(false);
            } else {
                habbo.getClient().sendResponse(new NotificationDialogMessageComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.getKey(), FurnitureMovementError.NO_RIGHTS.getErrorCode()));
            }
        }
    }

    public void pickUpBot(int botId, Habbo habbo) {
        if (habbo.getHabboInfo().getCurrentRoom() != null) {
            this.pickUpBot(habbo.getHabboInfo().getCurrentRoom().getBot(Math.abs(botId)), habbo);
        }
    }

    public void pickUpBot(Bot bot, Habbo habbo) {
        HabboInfo receiverInfo = habbo == null ? Emulator.getGameEnvironment().getHabboManager().getHabboInfo(bot.getOwnerId()) : habbo.getHabboInfo();

        if (bot != null) {
            BotPickUpEvent pickedUpEvent = new BotPickUpEvent(bot, habbo);
            Emulator.getPluginManager().fireEvent(pickedUpEvent);

            if (pickedUpEvent.isCancelled())
                return;

            if (habbo == null || (bot.getOwnerId() == habbo.getHabboInfo().getId() || habbo.hasRight(Permission.ACC_ANYROOMOWNER))) {
                if (habbo != null && !habbo.hasRight(Permission.ACC_UNLIMITED_BOTS) && habbo.getInventory().getBotsComponent().getBots().size() >= BotManager.MAXIMUM_BOT_INVENTORY_SIZE) {
                    habbo.alert(Emulator.getTexts().getValue("error.bots.max.inventory").replace("%amount%", BotManager.MAXIMUM_BOT_INVENTORY_SIZE + ""));
                    return;
                }

                bot.onPickUp(habbo, receiverInfo.getCurrentRoom());
                receiverInfo.getCurrentRoom().removeBot(bot);
                bot.stopFollowingHabbo();
                bot.setOwnerId(receiverInfo.getId());
                bot.setOwnerName(receiverInfo.getUsername());
                bot.needsUpdate(true);
                Emulator.getThreading().run(bot);

                Habbo receiver = habbo == null ? Emulator.getGameEnvironment().getHabboManager().getHabbo(receiverInfo.getId()) : habbo;
                if (receiver != null) {
                    receiver.getInventory().getBotsComponent().addBot(bot);
                    receiver.getClient().sendResponse(new BotAddedToInventoryComposer(bot));
                }
            }
        }
    }

    public Bot loadBot(ResultSet set) {
        try {
            String type = set.getString("type");
            Class<? extends Bot> botClazz = botDefenitions.get(type);

            if (botClazz != null)
                return botClazz.getDeclaredConstructor(ResultSet.class).newInstance(set);
            else
                log.error("Unknown Bot Type: " + type);
        } catch (SQLException e) {
            log.error("Caught SQL exception", e);
        } catch (Exception e) {
            log.error("Caught exception", e);
        }

        return null;
    }

    public void deleteBot(Bot bot) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM bots WHERE id = ? LIMIT 1")) {
            statement.setInt(1, bot.getId());
            statement.execute();
        } catch (SQLException e) {
            log.error("Caught SQL exception", e);
        }
    }

    public void dispose() {
        for (Map.Entry<String, Class<? extends Bot>> set : botDefenitions.entrySet()) {
            try {
                Method m = set.getValue().getMethod("dispose");
                m.setAccessible(true);
                m.invoke(null);
            } catch (NoSuchMethodException e) {
                log.info("Bot Manager -> Failed to execute dispose method upon bot type '" + set.getKey() + "'. No Such Method!");
            } catch (Exception e) {
                log.info("Bot Manager -> Failed to execute dispose method upon bot type '" + set.getKey() + "'. Error: " + e.getMessage());
            }
        }
    }
}
