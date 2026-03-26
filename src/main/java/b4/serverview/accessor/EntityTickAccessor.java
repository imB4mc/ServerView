package b4.serverview.accessor;

import net.minecraft.util.math.Vec3d;

public interface EntityTickAccessor {
    void serverview$setTickingTruth(boolean isTicking);
    boolean serverview$isTickingTruth();
    void serverview$setLastSyncedSnapshot(Vec3d pos, float yaw, float pitch);
    boolean serverview$matchesLastSyncedSnapshot(Vec3d pos, float yaw, float pitch);
    
    // Velocity and rotation tracking for entity sync mode
    void serverview$setLastSyncedVelocity(Vec3d velocity);
    Vec3d serverview$getLastSyncedVelocity();
    void serverview$setLastSyncedRotation(float yaw, float pitch);
    void serverview$getLastSyncedRotation(float[] out);
}
