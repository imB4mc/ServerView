package b4.serverview;

import b4.serverview.accessor.EntityTickAccessor;
import b4.serverview.network.ChunkStatesPayload;
import b4.serverview.network.EntityStatePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ChunkFilter;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

public class Serverview implements ModInitializer {
    private static final int CHUNK_STATE_SYNC_INTERVAL_TICKS = 20;
    private static final Map<String, PlayerChunkViewState> PLAYER_CHUNK_VIEW_STATES = new HashMap<>();

    @Override
    public void onInitialize() {
        // Register the S2C payload
        PayloadTypeRegistry.playS2C().register(ChunkStatesPayload.ID, ChunkStatesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EntityStatePayload.ID, EntityStatePayload.CODEC);

        // Sync when an entity's ticking state changes (Lazy Chunks)
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (!ServerViewConfig.masterToggle) return;

            for (Entity entity : world.iterateEntities()) {
                boolean isTicking = world.shouldTickEntityAt(entity.getBlockPos());
                Vec3d position = entity.getEntityPos();
                float yaw = entity.getYaw();
                float pitch = entity.getPitch();

                if (entity instanceof EntityTickAccessor accessor) {
                    boolean stateChanged = accessor.serverview$isTickingTruth() != isTicking;
                    boolean lazyEntityMoved = !isTicking && !accessor.serverview$matchesLastSyncedSnapshot(position, yaw, pitch);
                    boolean periodicResync = world.getTime() % 100 == 0;

                    if (stateChanged || lazyEntityMoved || periodicResync) {
                        accessor.serverview$setTickingTruth(isTicking);
                        accessor.serverview$setLastSyncedSnapshot(position, yaw, pitch);
                        syncToTrackers(entity, isTicking, position, yaw, pitch);
                    }
                }
            }

            for (ServerPlayerEntity player : world.getPlayers()) {
                syncLazyChunksForPlayer(player);
            }
        });

        // Sync when a player starts seeing an entity (Join/Move in range)
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof EntityTickAccessor accessor) {
                boolean isTicking = entity.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld
                        && serverWorld.shouldTickEntityAt(entity.getBlockPos());
                Vec3d position = entity.getEntityPos();
                float yaw = entity.getYaw();
                float pitch = entity.getPitch();

                accessor.serverview$setTickingTruth(isTicking);
                accessor.serverview$setLastSyncedSnapshot(position, yaw, pitch);
                ServerPlayNetworking.send(player, new EntityStatePayload(entity.getId(), isTicking, position, yaw, pitch));
            }
        });
    }

    private void syncToTrackers(Entity entity, boolean isTicking, Vec3d position, float yaw, float pitch) {
        EntityStatePayload payload = new EntityStatePayload(entity.getId(), isTicking, position, yaw, pitch);
        for (ServerPlayerEntity player : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private void syncLazyChunksForPlayer(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        long currentTick = serverWorld.getTime();
        ChunkPos center = player.getChunkPos();
        int viewDistance = Math.min(player.getViewDistance(), serverWorld.getServer().getPlayerManager().getViewDistance());
        String playerKey = player.getUuidAsString();
        PlayerChunkViewState state = PLAYER_CHUNK_VIEW_STATES.get(playerKey);
        boolean viewChanged = state == null
                || state.viewDistance != viewDistance
                || state.worldKey != serverWorld.getRegistryKey()
                || state.center.x != center.x
                || state.center.z != center.z;
        boolean shouldSyncChunkStates = viewChanged || currentTick % CHUNK_STATE_SYNC_INTERVAL_TICKS == 0;
        List<WorldChunk> blockTickingChunksToQueue = new ArrayList<>();
        List<ChunkStatesPayload.Entry> chunkStateEntries = shouldSyncChunkStates ? new ArrayList<>() : List.of();

        if (state == null || state.viewDistance != viewDistance || state.worldKey != serverWorld.getRegistryKey()) {
            state = new PlayerChunkViewState(serverWorld.getRegistryKey(), center, viewDistance);
            PLAYER_CHUNK_VIEW_STATES.put(playerKey, state);
        } else if (viewChanged) {
            state.center = center;
            ChunkPos currentCenter = center;
            int currentViewDistance = viewDistance;
            state.syncedBlockTickingChunks.removeIf(chunkPos -> !isWithinVisibleRange(currentCenter, currentViewDistance, new ChunkPos(chunkPos)));
        }

        if (shouldSyncChunkStates) {
            forEachVisibleChunk(center, viewDistance, pos -> collectChunkState(serverWorld, pos, chunkStateEntries));
        }

        if (ServerViewConfig.borderChunkRenderingEnabled) {
            PlayerChunkViewState currentState = state;
            forEachVisibleChunk(center, viewDistance, pos -> collectLazyChunk(
                    serverWorld,
                    pos,
                    blockTickingChunksToQueue,
                    currentState));
        }

        if (shouldSyncChunkStates) {
            ServerPlayNetworking.send(player, new ChunkStatesPayload(chunkStateEntries));
        }

        if (!blockTickingChunksToQueue.isEmpty()) {
            for (WorldChunk chunk : blockTickingChunksToQueue) {
                player.networkHandler.chunkDataSender.add(chunk);
            }
        }

        player.networkHandler.chunkDataSender.sendChunkBatches(player);
        state.center = center;
    }

    private void forEachVisibleChunk(ChunkPos center, int viewDistance, java.util.function.Consumer<ChunkPos> consumer) {
        for (int x = center.x - viewDistance; x <= center.x + viewDistance; x++) {
            for (int z = center.z - viewDistance; z <= center.z + viewDistance; z++) {
                if (isWithinVisibleRange(center, viewDistance, x, z)) {
                    consumer.accept(new ChunkPos(x, z));
                }
            }
        }
    }

    private boolean isWithinVisibleRange(ChunkPos center, int viewDistance, ChunkPos pos) {
        return isWithinVisibleRange(center, viewDistance, pos.x, pos.z);
    }

    private boolean isWithinVisibleRange(ChunkPos center, int viewDistance, int x, int z) {
        return ChunkFilter.isWithinDistanceExcludingEdge(center.x, center.z, viewDistance, x, z);
    }

    private void collectChunkState(ServerWorld world, ChunkPos pos, List<ChunkStatesPayload.Entry> chunkStateEntries) {
        WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.x, pos.z);
        if (chunk != null) {
            chunkStateEntries.add(new ChunkStatesPayload.Entry(pos.x, pos.z, chunk.getLevelType().ordinal()));
        }
    }

    private void collectLazyChunk(
            ServerWorld world,
            ChunkPos pos,
            List<WorldChunk> blockTickingChunksToQueue,
            PlayerChunkViewState state) {
        WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.x, pos.z);
        long packedChunkPos = pos.toLong();
        if (chunk == null) {
            state.syncedBlockTickingChunks.remove(packedChunkPos);
            return;
        }

        if (chunk.getLevelType() == net.minecraft.server.world.ChunkLevelType.BLOCK_TICKING) {
            if (state.syncedBlockTickingChunks.add(packedChunkPos)) {
                blockTickingChunksToQueue.add(chunk);
            }
        } else {
            state.syncedBlockTickingChunks.remove(packedChunkPos);
        }
    }

    private static final class PlayerChunkViewState {
        private final net.minecraft.registry.RegistryKey<World> worldKey;
        private ChunkPos center;
        private final int viewDistance;
        private final Set<Long> syncedBlockTickingChunks = new HashSet<>();

        private PlayerChunkViewState(
                net.minecraft.registry.RegistryKey<World> worldKey,
                ChunkPos center,
                int viewDistance) {
            this.worldKey = worldKey;
            this.center = center;
            this.viewDistance = viewDistance;
        }
    }
}
