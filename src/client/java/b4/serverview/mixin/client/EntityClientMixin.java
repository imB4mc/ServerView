package b4.serverview.mixin.client;

import b4.serverview.ServerViewConfig;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityClientMixin {

    // Fix: 1.21.2+ uses Vec3d instead of 3 doubles for setPosition
    @Inject(method = "setPosition(Lnet/minecraft/util/math/Vec3d;)V", at = @At("HEAD"))
    private void serverview$onSetPosition(Vec3d pos, CallbackInfo ci) {
        if (ServerViewConfig.masterToggle && ServerViewConfig.entityFreezeEnabled) {
            // This is where you'll eventually force the entity to
            // update even if the server says it's "not ticking."
        }
    }
}