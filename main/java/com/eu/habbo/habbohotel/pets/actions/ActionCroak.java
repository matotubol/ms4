package com.eu.habbo.habbohotel.pets.actions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetAction;
import com.eu.habbo.habbohotel.pets.PetTasks;
import com.eu.habbo.habbohotel.pets.PetVocalsType;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.threading.runnables.PetClearPosture;

public class ActionCroak extends ActionVocals {
    public ActionCroak() {
        super(PetTasks.SPEAK, false);
        this.minimumActionDuration = 2000;
    }

    @Override
    public boolean apply(Pet pet, Habbo habbo, String[] data) {
        pet.getRoomUnit().setStatus(RoomUnitStatus.CROAK, pet.getRoomUnit().getCurrentLocation().getStackHeight() + "");

        Emulator.getThreading().run(new PetClearPosture(pet, RoomUnitStatus.CROAK, null, false), this.minimumActionDuration);

        petSay(pet);

        return true;
    }


}
