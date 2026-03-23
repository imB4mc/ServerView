package b4.serverview.mixin.client;

import b4.serverview.ServerViewConfig;
import net.minecraft.entity.Entity;
import java.util.Optional;
import net.minecraft.entity.PositionInterpolator;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityClientMixin {
    @Inject(
            method = "updateTrackedPositionAndAngles(Ljava/util/Optional;Ljava/util/Optional;Ljava/util/Optional;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void serverview$applyLazyEntityPosition(
            Optional<Vec3d> pos,
            Optional<Float> yaw,
            Optional<Float> pitch,
            CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;

        if (!ServerViewConfig.masterToggle || !ServerViewConfig.entityFreezeEnabled) {
            return;
        }

        if (!(entity instanceof b4.serverview.accessor.EntityTickAccessor accessor) || accessor.serverview$isTickingTruth()) {
            return;
        }

        PositionInterpolator interpolator = entity.getInterpolator();
        Vec3d resolvedPos = pos.orElse(entity.getTrackedPosition().getPos());
        float resolvedYaw = yaw.orElse(interpolator != null ? interpolator.getLerpedYaw() : entity.getYaw());
        float resolvedPitch = pitch.orElse(interpolator != null ? interpolator.getLerpedPitch() : entity.getPitch());

        entity.refreshPositionAndAngles(resolvedPos, resolvedYaw, resolvedPitch);

        if (interpolator != null) {
            interpolator.clear();
        }

        ci.cancel();
    }
}
