package it.heron.hpet.modules.pets.userpets.animations;

import it.heron.hpet.modules.pets.userpets.animations.abstracts.UpDownAbstractAnimation;

public class SlowGlideAnimation extends UpDownAbstractAnimation {
    @Override
    protected float[] heightModifiers() {
        return new float[]{0.2f, 0.2f, 0.25f, 0.25f, 0.3f, 0.3f, 0.4f, 0.4f, 0.5f, 0.5f,
                0.6f, 0.6f, 0.7f, 0.7f, 0.75f, 0.75f, 0.8f, 0.8f, 0.8f, 0.8f,
                0.75f, 0.75f, 0.7f, 0.7f, 0.6f, 0.5f, 0.5f, 0.4f, 0.4f, 0.3f,
                0.3f, 0.25f, 0.25f, 0.2f, 0.2f};
    }

    @Override
    public String name() {
        return "slow_glide";
    }
}
