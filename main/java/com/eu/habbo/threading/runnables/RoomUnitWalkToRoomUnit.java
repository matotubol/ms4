package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredHandler;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
public class RoomUnitWalkToRoomUnit implements Runnable {
    private final RoomUnit walker;
    private final RoomUnit target;
    private final Room room;
    private final List<Runnable> targetReached;
    private final List<Runnable> failedReached;
    private final int minDistance;
    private final AtomicBoolean isRunning = new AtomicBoolean(true);

    private volatile RoomTile goalTile = null; // Making it volatile ensures thread-safe reading/writing.

    // Constructor with default minDistance
    public RoomUnitWalkToRoomUnit(RoomUnit walker, RoomUnit target, Room room, List<Runnable> targetReached, List<Runnable> failedReached) {
        this(walker, target, room,
                new CopyOnWriteArrayList<>(targetReached),  // Ensure thread safety
                new CopyOnWriteArrayList<>(failedReached), 1);
    }

    @Override
    public void run() {
        if (!isRunning.get()) return;

        try {
            if (this.goalTile == null) {
                this.findNewLocation();
                scheduleNextRun(500);
            } else if (this.walker.getGoalLocation().equals(this.goalTile)) {
                if (this.walker.getCurrentLocation().distance(this.goalTile) <= this.minDistance) {
                    this.executeRunnables(this.targetReached);
                    WiredHandler.handle(WiredTriggerType.BOT_REACHED_AVTR, this.target, this.room, new Object[]{ this.walker });
                } else {
                    scheduleNextRun(500);
                }
            }
        } catch (Exception e) {
            // Log or handle the exception as appropriate for your application.
        }
    }

    private void findNewLocation() {
        if (this.walker == null || this.target == null || this.target.getCurrentLocation() == null) return;

        this.goalTile = this.walker.getClosestAdjacentTile(this.target.getCurrentLocation().getX(), this.target.getCurrentLocation().getY(), true);

        if (this.goalTile == null) {
            this.executeRunnables(this.failedReached);
        } else {
            this.walker.setGoalLocation(this.goalTile);

            if (this.walker.getPath() == null || this.walker.getPath().isEmpty()) {
                this.executeRunnables(this.failedReached);
            }
        }
    }

    private void executeRunnables(List<Runnable> runnables) {
        if (runnables != null) {
            for (Runnable r : runnables) {
                if (r != null) {
                    try {
                        Emulator.getThreading().run(r);
                    } catch (Exception e) {
                        // Log or handle the exception as appropriate for your application.
                    }
                }
            }
        }
    }

    private void scheduleNextRun(long delay) {
        if (isRunning.get()) {
            Emulator.getThreading().run(this, delay);
        }
    }

    public void stop() {
        isRunning.set(false);
    }
}
