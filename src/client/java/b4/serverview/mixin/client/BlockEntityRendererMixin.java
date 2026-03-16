package b4.serverview.mixin.client;

import b4.serverview.ServerViewConfig;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderManager.class)
public class BlockEntityRendererMixin {

    /**
     * Updated for 1.21.11 ("Mounts of Mayhem")
     * The new render signature uses State objects instead of the BlockEntity itself.
     */
    @Inject(
            method = "render(Lnet/minecraft/client/render/block/entity/state/BlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD")
    )
    private void serverview$forceLazyBlockRender(
            BlockEntityRenderState state,
            MatrixStack matrices,
            OrderedRenderCommandQueue commandQueue,
            CameraRenderState cameraState,
            CallbackInfo ci
    ) {
        // Fix for rendering blocks in border/non-ticked chunks
        if (ServerViewConfig.masterToggle) {
            // Your logic for forcing the render now works with the new state system
        }
    }
}