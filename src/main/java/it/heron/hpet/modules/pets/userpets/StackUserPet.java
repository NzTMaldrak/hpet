package it.heron.hpet.modules.pets.userpets;

import it.heron.hpet.modules.pets.pettypes.StackPetType;
import it.heron.hpet.modules.pets.userpets.fakeentities.CanHaveItemOnHead;
import org.bukkit.entity.Entity;

public class StackUserPet extends HandUserPet {


    public StackUserPet(StackPetType petType, Entity owner, int level) {
        super(petType, owner, level);
    }

    @Override
    protected void switchStack() {
        CanHaveItemOnHead entity = (CanHaveItemOnHead) this.fakeEntity;
        entity.setHeadItem(currentStack());
    }

    @Override
    protected org.bukkit.Location getNametagLocation(org.bukkit.Location petLocation) {
        double verticalScaleCompensation = (getVisualScale() - 1d) * 2d;
        return petLocation.clone()
                .add(0, getPetType().getNameHeight() + 1d + verticalScaleCompensation, 0)
                .add(getPetType().getNametagRelativeLocation());
    }

}
