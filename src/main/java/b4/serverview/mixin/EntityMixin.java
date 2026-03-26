package b4.serverview.mixin;

import b4.serverview.accessor.EntityTickAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntityMixin implements EntityTickAccessor {
    @Unique
    private boolean serverview$isTicking = true;
    @Unique
    private Vec3d serverview$lastSyncedPos = Vec3d.ZERO;
    @Unique
    private float serverview$lastSyncedYaw;
    @Unique
    private float serverview$lastSyncedPitch;
    @Unique
    private boolean serverview$hasSyncedSnapshot;
    @Unique
    private Vec3d serverview$lastSyncedVelocity = Vec3d.ZERO;
    @Unique
    private float serverview$lastSyncedRotationYaw;
    @Unique
    private float serverview$lastSyncedRotationPitch;

    @Override
    public void serverview$setTickingTruth(boolean isTicking) {
        this.serverview$isTicking = isTicking;
    }

    @Override
    public boolean serverview$isTickingTruth() {
        return this.serverview$isTicking;
    }

    @Override
    public void serverview$setLastSyncedSnapshot(Vec3d pos, float yaw, float pitch) {
        this.serverview$lastSyncedPos = pos;
        this.serverview$lastSyncedYaw = yaw;
        this.serverview$lastSyncedPitch = pitch;
        this.serverview$hasSyncedSnapshot = true;
    }

    @Override
    public boolean serverview$matchesLastSyncedSnapshot(Vec3d pos, float yaw, float pitch) {
        return this.serverview$hasSyncedSnapshot
                && this.serverview$lastSyncedPos.squaredDistanceTo(pos) < 1.0E-7
                && Float.compare(this.serverview$lastSyncedYaw, yaw) == 0
                && Float.compare(this.serverview$lastSyncedPitch, pitch) == 0;
    }

    @Override
    public void serverview$setLastSyncedVelocity(Vec3d velocity) {
        this.serverview$lastSyncedVelocity = velocity;
    }

    @Override
    public Vec3d serverview$getLastSyncedVelocity() {
        return this.serverview$lastSyncedVelocity;
    }

    @Override
    public void serverview$setLastSyncedRotation(float yaw, float pitch) {
        this.serverview$lastSyncedRotationYaw = yaw;
        this.serverview$lastSyncedRotationPitch = pitch;
    }

    @Override
    public void serverview$getLastSyncedRotation(float[] out) {
        out[0] = this.serverview$lastSyncedRotationYaw;
        out[1] = this.serverview$lastSyncedRotationPitch;
    }
}
