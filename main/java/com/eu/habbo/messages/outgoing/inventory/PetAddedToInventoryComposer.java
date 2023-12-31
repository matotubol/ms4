package com.eu.habbo.messages.outgoing.inventory;

import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class PetAddedToInventoryComposer extends MessageComposer {
    private final Pet pet;

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.petAddedToInventoryComposer);
        this.pet.serialize(this.response);
        this.response.appendBoolean(false);
        return this.response;
    }
}
