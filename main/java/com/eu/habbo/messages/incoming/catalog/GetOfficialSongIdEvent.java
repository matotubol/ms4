package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.SoundTrack;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.jukebox.OfficialSongIdMessageComposer;

public class GetOfficialSongIdEvent extends MessageHandler {
    @Override
    public void handle() {
        String songName = this.packet.readString();

        final SoundTrack track = Emulator.getGameEnvironment().getItemManager().getSoundTrack(songName);

        if (track != null) {
            this.client.sendResponse(new OfficialSongIdMessageComposer(track));
        }
    }
}
