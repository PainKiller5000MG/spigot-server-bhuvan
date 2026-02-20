package net.minecraft.server.permissions;

public interface LevelBasedPermissionSet extends PermissionSet {

    /** @deprecated */
    @Deprecated
    LevelBasedPermissionSet ALL = create(PermissionLevel.ALL);
    LevelBasedPermissionSet MODERATOR = create(PermissionLevel.MODERATORS);
    LevelBasedPermissionSet GAMEMASTER = create(PermissionLevel.GAMEMASTERS);
    LevelBasedPermissionSet ADMIN = create(PermissionLevel.ADMINS);
    LevelBasedPermissionSet OWNER = create(PermissionLevel.OWNERS);

    PermissionLevel level();

    @Override
    default boolean hasPermission(Permission permission) {
        if (permission instanceof Permission.HasCommandLevel permission_hascommandlevel) {
            return this.level().isEqualOrHigherThan(permission_hascommandlevel.level());
        } else {
            return permission.equals(Permissions.COMMANDS_ENTITY_SELECTORS) ? this.level().isEqualOrHigherThan(PermissionLevel.GAMEMASTERS) : false;
        }
    }

    @Override
    default PermissionSet union(PermissionSet other) {
        if (other instanceof LevelBasedPermissionSet levelbasedpermissionset) {
            return this.level().isEqualOrHigherThan(levelbasedpermissionset.level()) ? levelbasedpermissionset : this;
        } else {
            return PermissionSet.super.union(other);
        }
    }

    static LevelBasedPermissionSet forLevel(PermissionLevel level) {
        LevelBasedPermissionSet levelbasedpermissionset;

        switch (level) {
            case ALL:
                levelbasedpermissionset = LevelBasedPermissionSet.ALL;
                break;
            case MODERATORS:
                levelbasedpermissionset = LevelBasedPermissionSet.MODERATOR;
                break;
            case GAMEMASTERS:
                levelbasedpermissionset = LevelBasedPermissionSet.GAMEMASTER;
                break;
            case ADMINS:
                levelbasedpermissionset = LevelBasedPermissionSet.ADMIN;
                break;
            case OWNERS:
                levelbasedpermissionset = LevelBasedPermissionSet.OWNER;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return levelbasedpermissionset;
    }

    private static LevelBasedPermissionSet create(final PermissionLevel level) {
        return new LevelBasedPermissionSet() {
            @Override
            public PermissionLevel level() {
                return level;
            }

            public String toString() {
                return "permission level: " + level.name();
            }
        };
    }
}
