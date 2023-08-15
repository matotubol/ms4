package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredHandler;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BotFollowHabbo implements Runnable {
    private final Bot bot;
    private final Habbo habbo;
    private final Room room;
    private boolean hasReached;

    @Override
    public void run() {
        // Validation checks
        if (!isValidState()) {
            return;
        }

        RoomTile target = getTargetTile();

        if (target == null) {
            return;
        }

        checkDistanceToHabbo();

        if (isValidTargetTile(target)) {
            this.bot.getRoomUnit().setGoalLocation(target);
            this.bot.getRoomUnit().setCanWalk(true);
            Emulator.getThreading().run(this, 500);
        }
    }

    private boolean isValidState() {
        return this.bot != null
                && this.habbo != null
                && this.bot.getFollowingHabboId() == this.habbo.getHabboInfo().getId()
                && this.habbo.getHabboInfo().getCurrentRoom() != null
                && this.habbo.getHabboInfo().getCurrentRoom() == this.room
                && this.habbo.getRoomUnit() != null
                && this.bot.getRoomUnit() != null;
    }

    private RoomTile getTargetTile() {
        RoomTile target = this.room.getLayout().getTileInFront(this.habbo.getRoomUnit().getCurrentLocation(), Math.abs((this.habbo.getRoomUnit().getBodyRotation().getValue() + 4)) % 8);

        if (target == null || target.getX() < 0 || target.getY() < 0) {
            target = this.room.getLayout().getTileInFront(this.habbo.getRoomUnit().getCurrentLocation(), this.habbo.getRoomUnit().getBodyRotation().getValue());
        }

        return target;
    }

    private void checkDistanceToHabbo() {
        if(this.habbo.getRoomUnit().getCurrentLocation().distance(this.bot.getRoomUnit().getCurrentLocation()) < 2) {
            if(!hasReached) {
                WiredHandler.handle(WiredTriggerType.BOT_REACHED_AVTR, bot.getRoomUnit(), room, new Object[]{});
                hasReached = true;
            }
        } else {
            hasReached = false;
        }
    }

    private boolean isValidTargetTile(RoomTile target) {
        return target != null && target.getX() >= 0 && target.getY() >= 0;
    }
}

