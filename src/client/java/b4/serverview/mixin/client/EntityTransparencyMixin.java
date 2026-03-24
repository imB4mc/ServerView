package b4.serverview.mixin.client;

import b4.serverview.ServerViewConfig;
import b4.serverview.client.GhostBlockTracker;
import java.util.List;
import net.minecraft.block.BlockState;
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
import org.spongepowered.asm.mixin.injection.ModifyArg;
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
                && GhostBlockTracker.shouldRenderGhostBlock(pos, state);

        serverview$pushedGhostMatrix.set(shouldAdjust);

        if (shouldAdjust) {
            float transparency = Math.max(0.0f, Math.min(1.0f, ServerViewConfig.ghostBlockTransparency));
            float scale = 1.0F - 0.35F * transparency;
            matrices.push();
            matrices.translate(0.5, 0.5, 0.5);
            matrices.scale(scale, scale, scale);
            matrices.translate(-0.5, -0.5, -0.5);
        }
    }

    @ModifyArg(
            method = "renderQuad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;[FFFFF[II)V"
            ),
            index = 6
    )
    private float serverview$applyGhostAlpha(float alpha) {
        if (!Boolean.TRUE.equals(serverview$pushedGhostMatrix.get())) {
            return alpha;
        }

        return Math.min(alpha, GhostBlockTracker.getGhostModelAlpha());
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
