package net.minecraft.world.level.block.state.properties;

import net.minecraft.util.StringRepresentable;

public enum SideChainPart implements StringRepresentable {

    UNCONNECTED("unconnected"), RIGHT("right"), CENTER("center"), LEFT("left");

    private final String name;

    private SideChainPart(String name) {
        this.name = name;
    }

    public String toString() {
        return this.getSerializedName();
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public boolean isConnected() {
        return this != SideChainPart.UNCONNECTED;
    }

    public boolean isConnectionTowards(SideChainPart endPart) {
        return this == SideChainPart.CENTER || this == endPart;
    }

    public boolean isChainEnd() {
        return this != SideChainPart.CENTER;
    }

    public SideChainPart whenConnectedToTheRight() {
        SideChainPart sidechainpart;

        switch (this.ordinal()) {
            case 0:
            case 3:
                sidechainpart = SideChainPart.LEFT;
                break;
            case 1:
            case 2:
                sidechainpart = SideChainPart.CENTER;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return sidechainpart;
    }

    public SideChainPart whenConnectedToTheLeft() {
        SideChainPart sidechainpart;

        switch (this.ordinal()) {
            case 0:
            case 1:
                sidechainpart = SideChainPart.RIGHT;
                break;
            case 2:
            case 3:
                sidechainpart = SideChainPart.CENTER;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return sidechainpart;
    }

    public SideChainPart whenDisconnectedFromTheRight() {
        SideChainPart sidechainpart;

        switch (this.ordinal()) {
            case 0:
            case 3:
                sidechainpart = SideChainPart.UNCONNECTED;
                break;
            case 1:
            case 2:
                sidechainpart = SideChainPart.RIGHT;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return sidechainpart;
    }

    public SideChainPart whenDisconnectedFromTheLeft() {
        SideChainPart sidechainpart;

        switch (this.ordinal()) {
            case 0:
            case 1:
                sidechainpart = SideChainPart.UNCONNECTED;
                break;
            case 2:
            case 3:
                sidechainpart = SideChainPart.LEFT;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return sidechainpart;
    }
}
