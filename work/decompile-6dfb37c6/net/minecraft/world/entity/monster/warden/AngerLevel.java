package net.minecraft.world.entity.monster.warden;

import java.util.Arrays;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

public enum AngerLevel {

    CALM(0, SoundEvents.WARDEN_AMBIENT, SoundEvents.WARDEN_LISTENING), AGITATED(40, SoundEvents.WARDEN_AGITATED, SoundEvents.WARDEN_LISTENING_ANGRY), ANGRY(80, SoundEvents.WARDEN_ANGRY, SoundEvents.WARDEN_LISTENING_ANGRY);

    private static final AngerLevel[] SORTED_LEVELS = (AngerLevel[]) Util.make(values(), (aangerlevel) -> {
        Arrays.sort(aangerlevel, (angerlevel, angerlevel1) -> {
            return Integer.compare(angerlevel1.minimumAnger, angerlevel.minimumAnger);
        });
    });
    private final int minimumAnger;
    private final SoundEvent ambientSound;
    private final SoundEvent listeningSound;

    private AngerLevel(int minimumAnger, SoundEvent ambientSound, SoundEvent listeningSound) {
        this.minimumAnger = minimumAnger;
        this.ambientSound = ambientSound;
        this.listeningSound = listeningSound;
    }

    public int getMinimumAnger() {
        return this.minimumAnger;
    }

    public SoundEvent getAmbientSound() {
        return this.ambientSound;
    }

    public SoundEvent getListeningSound() {
        return this.listeningSound;
    }

    public static AngerLevel byAnger(int anger) {
        for (AngerLevel angerlevel : AngerLevel.SORTED_LEVELS) {
            if (anger >= angerlevel.minimumAnger) {
                return angerlevel;
            }
        }

        return AngerLevel.CALM;
    }

    public boolean isAngry() {
        return this == AngerLevel.ANGRY;
    }
}
