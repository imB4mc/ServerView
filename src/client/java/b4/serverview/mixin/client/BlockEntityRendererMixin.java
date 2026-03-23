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

    // Fixed the method descriptor to match 1.21.11's internal structure
    @Inject(
            method = "render",
            at = @At("HEAD")
    )
    private void serverview$onRender(BlockEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue commandQueue, CameraRenderState cameraState, CallbackInfo ci) {
        if (ServerViewConfig.masterToggle) {
            // Logic to prevent culling will go here once the game launches
        }
    }
}