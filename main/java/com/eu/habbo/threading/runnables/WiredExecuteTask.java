package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerAtSetTime;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerAtTimeLong;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredHandler;

import static org.reflections.Reflections.log;

public class WiredExecuteTask implements Runnable {
    private final InteractionWiredTrigger task;
    private final Room room;
    private final int taskId;

    public WiredExecuteTask(InteractionWiredTrigger trigger, Room room) {
        this.task = trigger;
        this.room = room;
        this.taskId = determineTaskId(trigger);
        log.info("Wired effect executed in room with ID:");
    }

    private int determineTaskId(InteractionWiredTrigger trigger) {
        if (trigger instanceof WiredTriggerAtSetTime) {
            return ((WiredTriggerAtSetTime) trigger).taskId;
        } else if (trigger instanceof WiredTriggerAtTimeLong) {
            return ((WiredTriggerAtTimeLong) trigger).taskId;
        }
        return -1; // default or invalid ID
    }

    private boolean hasTaskIdChanged() {
        if (task instanceof WiredTriggerAtSetTime && ((WiredTriggerAtSetTime) task).taskId != taskId) {
            return true;
        }
        if (task instanceof WiredTriggerAtTimeLong && ((WiredTriggerAtTimeLong) task).taskId != taskId) {
            return true;
        }
        return false;
    }

    @Override
    public void run() {
        if (Emulator.isShuttingDown || !Emulator.isReady) {
            return;
        }

        if (room != null && room.getId() == task.getRoomId() && !hasTaskIdChanged()) {
            WiredHandler.handle(task, null, room, null);
        }
    }
}
