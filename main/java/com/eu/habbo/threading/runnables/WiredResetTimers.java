package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredHandler;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Slf4j
public class WiredResetTimers implements Runnable {
    private final Room room;

    @Override
    public void run() {
        if (room == null) {
            log.warn("Attempted to reset timers for a null room.");
            return;
        }

        if (!Emulator.isShuttingDown && Emulator.isReady) {
            try {
                WiredHandler.resetTimers(this.room);
            } catch (Exception e) {
                log.error("Failed to reset timers for room: " + room.getId(), e);
            }
        } else {
            log.info("Skipping reset timers for room: " + room.getId() + " as Emulator is not ready or is shutting down.");
        }
    }
}
