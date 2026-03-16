package b4.serverview.mixin.client;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends net.minecraft.entity.Entity, S extends EntityRenderState> {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void serverview$applyFrozenEffect(S state, MatrixStack matrices, OrderedRenderCommandQueue renderQueue, CameraRenderState cameraState, CallbackInfo ci) {
        // 1.21.2+ Logic: We use 'state' instead of 'entity'
        // If you need to check if the entity is "frozen", that data now needs
        // to be inside the EntityRenderState.
    }
}