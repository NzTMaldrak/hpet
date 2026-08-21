package it.heron.hpet.modules.pets.userpets.nametags;

import lombok.AllArgsConstructor;
import net.kyori.adventure.text.Component;


public abstract @AllArgsConstructor class AbstractNametag implements INametag {

    public AbstractNametag(Component name) {
        // do validation
    }

}
