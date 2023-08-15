package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
public class RoomUnitWalkToLocation implements Runnable {
    private final RoomUnit walker;
    private final RoomTile goalTile;
    private final Room room;
    private final List<Runnable> targetReached;
    private final List<Runnable> failedReached;

    public RoomUnitWalkToLocation(RoomUnit walker, RoomTile goalTile, Room room, Runnable targetReached, Runnable failedReached) {
        this.walker = walker;
        this.goalTile = goalTile;
        this.room = room;

        this.targetReached = new ArrayList<>();
        if (targetReached != null) this.targetReached.add(targetReached);

        this.failedReached = new ArrayList<>();
        if (failedReached != null) this.targetReached.add(failedReached);
    }


    @Override
    public void run() {
        if (this.goalTile == null || this.walker == null || this.room == null || this.walker.getRoom() == null || this.walker.getRoom().getId() != this.room.getId()) {
            onFail();
            return;
        }

        if (this.walker.getCurrentLocation().equals(this.goalTile)) {
            onSuccess();
            return;
        }

        if (!this.walker.getGoalLocation().equals(this.goalTile) || (this.walker.getPath().isEmpty() && !this.walker.hasStatus(RoomUnitStatus.MOVE))) {
            onFail();
            return;
        }

        Emulator.getThreading().run(this, 250);
    }

    private void onSuccess() {
        for (Runnable r : this.targetReached)
            Emulator.getThreading().run(r);
    }

    private void onFail() {
        for (Runnable r : this.failedReached)
            Emulator.getThreading().run(r);
    }
}
