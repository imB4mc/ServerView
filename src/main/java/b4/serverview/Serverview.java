package b4.serverview;

import b4.serverview.accessor.EntityTickAccessor;
import b4.serverview.network.EntityStatePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

public class Serverview implements ModInitializer {
    @Override
    public void onInitialize() {
        // Register the S2C payload
        PayloadTypeRegistry.playS2C().register(EntityStatePayload.ID, EntityStatePayload.CODEC);

        // Sync when an entity's ticking state changes (Lazy Chunks)
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (!ServerViewConfig.masterToggle) return;

            for (Entity entity : world.iterateEntities()) {
                boolean isTicking = world.getChunkManager().isTickingFutureReady(entity.getChunkPos().toLong());

                if (entity instanceof EntityTickAccessor accessor) {
                    // If state changed OR it's been 5 seconds, sync the 'Truth'
                    if (accessor.serverview$isTickingTruth() != isTicking || world.getTime() % 100 == 0) {
                        accessor.serverview$setTickingTruth(isTicking);

                        EntityStatePayload payload = new EntityStatePayload(entity.getId(), isTicking);
                        for (ServerPlayerEntity player : PlayerLookup.tracking(entity)) {
                            ServerPlayNetworking.send(player, payload);
                        }
                    }
                }
            }
        });

        // Sync when a player starts seeing an entity (Join/Move in range)
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof EntityTickAccessor accessor) {
                ServerPlayNetworking.send(player, new EntityStatePayload(entity.getId(), accessor.serverview$isTickingTruth()));
            }
        });
    }

    private void syncToTrackers(Entity entity, boolean isTicking) {
        EntityStatePayload payload = new EntityStatePayload(entity.getId(), isTicking);
        for (ServerPlayerEntity player : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}