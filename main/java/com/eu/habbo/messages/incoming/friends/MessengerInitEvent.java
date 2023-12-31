package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.FriendListFragmentMessageComposer;
import com.eu.habbo.messages.outgoing.friends.MessengerInitComposer;

import java.util.ArrayList;

public class MessengerInitEvent extends MessageHandler {
    @Override
    public void handle() {
        ArrayList<ServerMessage> messages = new ArrayList<>();
        messages.add(new MessengerInitComposer(this.client.getHabbo()).compose());
        messages.addAll(FriendListFragmentMessageComposer.getMessagesForBuddyList(this.client.getHabbo().getMessenger().getFriends().values()));
        this.client.sendResponses(messages);
    }
}
