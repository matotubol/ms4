package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.PostItColor;
import com.eu.habbo.habbohotel.items.interactions.InteractionPostIt;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.Arrays;

@Slf4j
public class AddSpamWallPostItEvent extends MessageHandler {


    @Override
    public void handle() {
        int itemId = this.packet.readInt();

        this.packet.readString();
        String color = this.packet.readString();
        if (itemId == -1234) {
            if (this.client.getHabbo().hasCommand("cmd_multi")) {
                String[] commands = this.packet.readString().split("\r");

                Arrays.stream(commands)
                        .map(command -> command.replace("<br>", "\r"))
                        .forEach(command -> Emulator.getGameEnvironment().getCommandsManager().handleCommand(this.client, command));
            } else {
                log.info("Scripter Alert! " + this.client.getHabbo().getHabboInfo().getUsername() + " | " + this.packet.readString());
            }
        } else {
            String text = this.packet.readString();

            Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
            HabboItem sticky = room.getHabboItem(itemId);

            if (sticky != null && sticky.getUserId() == this.client.getHabbo().getHabboInfo().getId()) {
                sticky.setUserId(room.getOwnerId());

                if (color.equalsIgnoreCase(PostItColor.YELLOW.hexColor)) {
                    color = PostItColor.randomColorNotYellow().hexColor;
                }
                if (!InteractionPostIt.STICKYPOLE_PREFIX_TEXT.isEmpty()) {
                    text = InteractionPostIt.STICKYPOLE_PREFIX_TEXT.replace("\\r", "\r").replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()).replace("%timestamp%", LocalDate.now().toString()) + text;
                }

                sticky.setUserId(room.getOwnerId());
                sticky.setExtradata(color + " " + text);
                sticky.needsUpdate(true);
                room.updateItem(sticky);
                Emulator.getThreading().run(sticky);
            }
        }
    }
}
