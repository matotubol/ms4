package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.users.UserUpdateComposer;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class OneWayGateActionOne implements Runnable {
    private final GameClient client;
    private final Room room;
    private final HabboItem oneWayGate;


    @Override
    public void run() {
        this.room.sendComposer(new UserUpdateComposer(this.client.getHabbo().getRoomUnit()).compose());

        RoomTile t = this.room.getLayout().getTileInFront(this.room.getLayout().getTile(this.oneWayGate.getX(), this.oneWayGate.getY()), (this.oneWayGate.getRotation() + 4) % 8);

        if (this.client.getHabbo().getRoomUnit().isAnimateWalk()) {
            this.client.getHabbo().getRoomUnit().setAnimateWalk(false);
        }

        if (t.isWalkable()) {
            if (this.room.tileWalkable(t) && this.client.getHabbo().getRoomUnit().getX() == this.oneWayGate.getX() && this.client.getHabbo().getRoomUnit().getY() == this.oneWayGate.getY()) {
                this.client.getHabbo().getRoomUnit().setGoalLocation(t);

                if (!this.oneWayGate.getExtradata().equals("0")) {
                    Emulator.getThreading().run(new HabboItemNewState(this.oneWayGate, this.room, "0"), 1000);
                }
            }
            //else if (this.client.getHabbo().getRoomUnit().getX() == this.oneWayGate.getX() && this.client.getHabbo().getRoomUnit().getY() == this.oneWayGate.getY())
            //{

            //}
            else {
                if (!this.oneWayGate.getExtradata().equals("0")) {
                    Emulator.getThreading().run(new HabboItemNewState(this.oneWayGate, this.room, "0"), 1000);
                }
            }
        }
    }
}
