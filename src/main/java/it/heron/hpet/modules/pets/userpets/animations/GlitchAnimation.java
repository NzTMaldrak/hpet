package it.heron.hpet.modules.pets.userpets.animations;

import it.heron.hpet.modules.pets.userpets.animations.abstracts.UpDownAbstractAnimation;

public final class GlitchAnimation extends UpDownAbstractAnimation {
    @Override
    protected float[] heightModifiers() {
        return new float[]{0.2f, 0.5f, 0.3f, 0.8f, 0.3f, 0.5f, 0.8f, 0.4f};
    }

    @Override
    public String name() {
        return "glitch";
    }
}
