package b4.serverview.mixin.client; // MUST match this exactly

import b4.serverview.ServerViewConfig;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

@Mixin(BlockModelRenderer.class)
public class EntityTransparencyMixin {

    // Method signature updated for the 1.21.11 rendering refactor
    @Inject(
            method = "render(Lnet/minecraft/world/BlockRenderView;Ljava/util/List;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZI)V",
            at = @At("HEAD")
    )
    private void serverview$applyGhostTransparency(
            BlockRenderView world,
            List<BakedQuad> quads,
            BlockState state,
            BlockPos pos,
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            int overlay,
            CallbackInfo ci) {

        if (ServerViewConfig.masterToggle && ServerViewConfig.ghostBlockTransparency < 1.0) {
            // Transparency logic here
        }
    }
}