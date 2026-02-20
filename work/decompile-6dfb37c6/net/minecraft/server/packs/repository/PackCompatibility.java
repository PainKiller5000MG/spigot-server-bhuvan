package net.minecraft.server.packs.repository;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.util.InclusiveRange;

public enum PackCompatibility {

    TOO_OLD("old"), TOO_NEW("new"), UNKNOWN("unknown"), COMPATIBLE("compatible");

    public static final int UNKNOWN_VERSION = Integer.MAX_VALUE;
    private final Component description;
    private final Component confirmation;

    private PackCompatibility(String key) {
        this.description = Component.translatable("pack.incompatible." + key).withStyle(ChatFormatting.GRAY);
        this.confirmation = Component.translatable("pack.incompatible.confirm." + key);
    }

    public boolean isCompatible() {
        return this == PackCompatibility.COMPATIBLE;
    }

    public static PackCompatibility forVersion(InclusiveRange<PackFormat> packDeclaredVersions, PackFormat gameSupportedVersion) {
        return ((PackFormat) packDeclaredVersions.minInclusive()).major() == Integer.MAX_VALUE ? PackCompatibility.UNKNOWN : (((PackFormat) packDeclaredVersions.maxInclusive()).compareTo(gameSupportedVersion) < 0 ? PackCompatibility.TOO_OLD : (gameSupportedVersion.compareTo(packDeclaredVersions.minInclusive()) < 0 ? PackCompatibility.TOO_NEW : PackCompatibility.COMPATIBLE));
    }

    public Component getDescription() {
        return this.description;
    }

    public Component getConfirmation() {
        return this.confirmation;
    }
}
