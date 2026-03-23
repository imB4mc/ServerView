package b4.serverview.accessor;

import net.minecraft.util.math.Vec3d;

public interface EntityTickAccessor {
    void serverview$setTickingTruth(boolean isTicking);
    boolean serverview$isTickingTruth();
    void serverview$setLastSyncedSnapshot(Vec3d pos, float yaw, float pitch);
    boolean serverview$matchesLastSyncedSnapshot(Vec3d pos, float yaw, float pitch);
}
