package b4.serverview.mixin.client;

import b4.serverview.ServerViewConfig;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockModelRenderer.class)
public class EntityTransparencyMixin {
    @Unique
    private static final ThreadLocal<Boolean> serverview$pushedGhostMatrix = ThreadLocal.withInitial(() -> false);

    @Inject(method = "render", at = @At("HEAD"))
    private void serverview$applyGhostTransparency(
            BlockRenderView world,
            List<BlockModelPart> parts,
            BlockState state,
            BlockPos pos,
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            int overlay,
            CallbackInfo ci) {
        boolean shouldAdjust = ServerViewConfig.masterToggle
                && ServerViewConfig.highlightGhostBlocks
                && state.isOf(Blocks.DIAMOND_BLOCK);

        serverview$pushedGhostMatrix.set(shouldAdjust);

        if (shouldAdjust) {
            matrices.push();
            matrices.translate(0.5, 0.5, 0.5);
            matrices.scale(0.5f, 0.5f, 0.5f);
            matrices.translate(-0.5, -0.5, -0.5);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void serverview$cleanupMatrix(
            BlockRenderView world,
            List<BlockModelPart> parts,
            BlockState state,
            BlockPos pos,
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            int overlay,
            CallbackInfo ci) {
        if (Boolean.TRUE.equals(serverview$pushedGhostMatrix.get())) {
            matrices.pop();
        }

        serverview$pushedGhostMatrix.set(false);
    }
}
