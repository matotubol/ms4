package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionPostIt;
import com.eu.habbo.habbohotel.items.interactions.InteractionStickyPole;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.generic.alerts.NotificationDialogMessageComposer;
import com.eu.habbo.messages.outgoing.inventory.FurniListRemoveComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ItemAddMessageComposer;

public class PlacePostItEvent extends MessageHandler {
    @Override
    public void handle() {
        int itemId = this.packet.readInt();
        String location = this.packet.readString();

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room != null) {
            if (room.hasRights(this.client.getHabbo()) || !room.getRoomSpecialTypes().getItemsOfType(InteractionStickyPole.class).isEmpty()) {
                HabboItem item = this.client.getHabbo().getInventory().getItemsComponent().getHabboItem(itemId);

                if (item instanceof InteractionPostIt) {
                    if (room.getPostItNotes().size() < Room.MAXIMUM_POSTITNOTES) {
                        room.addHabboItem(item);
                        item.setExtradata("FFFF33");
                        item.setRoomId(this.client.getHabbo().getHabboInfo().getCurrentRoom().getId());
                        item.setWallPosition(location);
                        item.setUserId(this.client.getHabbo().getHabboInfo().getId());
                        item.needsUpdate(true);
                        room.sendComposer(new ItemAddMessageComposer(item, this.client.getHabbo().getHabboInfo().getUsername()).compose());
                        this.client.getHabbo().getInventory().getItemsComponent().removeHabboItem(item);
                        this.client.sendResponse(new FurniListRemoveComposer(item.getGiftAdjustedId()));
                        item.setFromGift(false);
                        Emulator.getThreading().run(item);

                        if (room.getOwnerId() != this.client.getHabbo().getHabboInfo().getId()) {
                            AchievementManager.progressAchievement(room.getOwnerId(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("NotesReceived"));
                            AchievementManager.progressAchievement(this.client.getHabbo().getHabboInfo().getId(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("NotesLeft"));
                        }

                    }
                    else {
                        this.client.sendResponse(new NotificationDialogMessageComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.getKey(), FurnitureMovementError.MAX_STICKIES.getErrorCode()));
                    }

                    //this.client.sendResponse(new PostItStickyPoleOpenComposer(item));
                }
            }
        }
    }
}
