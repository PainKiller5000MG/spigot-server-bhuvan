package net.minecraft.server.permissions;

public interface PermissionSet {

    PermissionSet NO_PERMISSIONS = (permission) -> {
        return false;
    };
    PermissionSet ALL_PERMISSIONS = (permission) -> {
        return true;
    };

    boolean hasPermission(Permission permission);

    default PermissionSet union(PermissionSet other) {
        return (PermissionSet) (other instanceof PermissionSetUnion ? other.union(this) : new PermissionSetUnion(this, other));
    }
}
