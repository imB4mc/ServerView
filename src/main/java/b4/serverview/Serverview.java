package b4.serverview;

import b4.serverview.accessor.EntityTickAccessor;
import b4.serverview.network.ChunkStatesPayload;
import b4.serverview.network.EntityStatePayload;
import b4.serverview.network.RemoteRodStatePayload;
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
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ChunkFilter;
import net.minecraft.server.world.ChunkLevelType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

public class Serverview implements ModInitializer {
    private static final int CHUNK_STATE_SYNC_INTERVAL_TICKS = 20;
    private static final int REMOTE_ROD_SYNC_INTERVAL_TICKS = 20;
    private static final int LAZY_CHUNK_EXTRA_RADIUS = 3;
    private static final int TICK_STALL_THRESHOLD_TICKS = 2;
    private static final int ENTITY_SYNC_INTERVAL_TICKS = 1; // Every tick for accuracy when enabled
    private static final double ENTITY_POSITION_CHANGE_THRESHOLD = 0.001; // Minimum distance to trigger sync
    private static final double ENTITY_VELOCITY_CHANGE_THRESHOLD = 0.001; // Minimum velocity change to trigger sync
    
    private static final Map<String, PlayerChunkViewState> PLAYER_CHUNK_VIEW_STATES = new HashMap<>();
    private static final Map<net.minecraft.registry.RegistryKey<World>, Map<UUID, Integer>> LAST_ENTITY_AGE_BY_WORLD = new HashMap<>();
    private static final Map<net.minecraft.registry.RegistryKey<World>, Map<UUID, Integer>> STALLED_ENTITY_TICKS_BY_WORLD = new HashMap<>();
    private static final Map<UUID, RemoteRodSyncState> PLAYER_REMOTE_ROD_SYNC_STATES = new HashMap<>();
    private static final Map<Integer, EntitySyncState> ENTITY_SYNC_STATES = new HashMap<>();

    @Override
    public void onInitialize() {
        // Register the S2C payload
        PayloadTypeRegistry.playS2C().register(ChunkStatesPayload.ID, ChunkStatesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EntityStatePayload.ID, EntityStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RemoteRodStatePayload.ID, RemoteRodStatePayload.CODEC);

        // Sync when an entity's ticking state changes (Lazy Chunks)
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (!ServerViewConfig.masterToggle) return;

            net.minecraft.registry.RegistryKey<World> worldKey = world.getRegistryKey();
            Map<UUID, Integer> lastEntityAgeByUuid = LAST_ENTITY_AGE_BY_WORLD.computeIfAbsent(worldKey, key -> new HashMap<>());
            Map<UUID, Integer> stalledEntityTicksByUuid = STALLED_ENTITY_TICKS_BY_WORLD.computeIfAbsent(worldKey, key -> new HashMap<>());
            Set<UUID> seenEntityUuids = new HashSet<>();

            for (Entity entity : world.iterateEntities()) {
                seenEntityUuids.add(entity.getUuid());
                boolean isTicking = isEntityTicking(world, entity, lastEntityAgeByUuid, stalledEntityTicksByUuid);
                Vec3d position = entity.getEntityPos();
                Vec3d velocity = entity.getVelocity();
                float yaw = entity.getYaw();
                float pitch = entity.getPitch();

                if (entity instanceof EntityTickAccessor accessor) {
                    if (ServerViewConfig.entitySyncEnabled) {
                        // Full entity sync: Always sync to ensure client sees what server knows
                        accessor.serverview$setTickingTruth(isTicking);
                        accessor.serverview$setLastSyncedSnapshot(position, yaw, pitch);
                        accessor.serverview$setLastSyncedVelocity(velocity);
                        accessor.serverview$setLastSyncedRotation(yaw, pitch);
                        
                        // Smart change detection: Only sync if state actually changed
                        EntitySyncState state = ENTITY_SYNC_STATES.get(entity.getId());
                        if (shouldSyncEntity(entity.getId(), isTicking, position, velocity, yaw, pitch, state)) {
                            syncToTrackers(entity, isTicking, position, yaw, pitch, velocity);
                            ENTITY_SYNC_STATES.put(entity.getId(), new EntitySyncState(isTicking, position, velocity, yaw, pitch));
                        }
                        continue;
                    }

                    boolean stateChanged = accessor.serverview$isTickingTruth() != isTicking;
                    boolean lazyEntityMoved = !isTicking && !accessor.serverview$matchesLastSyncedSnapshot(position, yaw, pitch);
                    boolean periodicResync = world.getTime() % 100 == 0;

                    if (stateChanged || lazyEntityMoved || periodicResync) {
                        accessor.serverview$setTickingTruth(isTicking);
                        accessor.serverview$setLastSyncedSnapshot(position, yaw, pitch);
                        syncToTrackers(entity, isTicking, position, yaw, pitch, velocity);
                    }
                }
            }

            lastEntityAgeByUuid.keySet().retainAll(seenEntityUuids);
            stalledEntityTicksByUuid.keySet().retainAll(seenEntityUuids);

            for (ServerPlayerEntity player : world.getPlayers()) {
                syncLazyChunksForPlayer(player);
                syncRemoteRodState(player, world.getTime());
            }
        });

        // Sync when a player starts seeing an entity (Join/Move in range)
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof EntityTickAccessor accessor) {
                boolean isTicking = entity.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld
                        && serverWorld.shouldTickEntityAt(entity.getBlockPos());
                Vec3d position = entity.getEntityPos();
                Vec3d velocity = entity.getVelocity();
                float yaw = entity.getYaw();
                float pitch = entity.getPitch();

                accessor.serverview$setTickingTruth(isTicking);
                accessor.serverview$setLastSyncedSnapshot(position, yaw, pitch);
                accessor.serverview$setLastSyncedVelocity(velocity);
                accessor.serverview$setLastSyncedRotation(yaw, pitch);
                
                // Track for entity sync mode change detection
                if (ServerViewConfig.entitySyncEnabled) {
                    ENTITY_SYNC_STATES.put(entity.getId(), new EntitySyncState(isTicking, position, velocity, yaw, pitch));
                }
                
                ServerPlayNetworking.send(player, new EntityStatePayload(entity.getId(), isTicking, position, yaw, pitch, velocity));
            }
        });
    }

    private void syncToTrackers(Entity entity, boolean isTicking, Vec3d position, float yaw, float pitch, Vec3d velocity) {
        EntityStatePayload payload = new EntityStatePayload(entity.getId(), isTicking, position, yaw, pitch, velocity);
        for (ServerPlayerEntity player : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private boolean isEntityTicking(
            ServerWorld world,
            Entity entity,
            Map<UUID, Integer> lastEntityAgeByUuid,
            Map<UUID, Integer> stalledEntityTicksByUuid) {
        UUID entityUuid = entity.getUuid();
        int currentAge = entity.age;
        boolean chunkTicking = world.shouldTickEntityAt(entity.getBlockPos());
        Integer previousAge = lastEntityAgeByUuid.put(entityUuid, currentAge);

        if (!chunkTicking) {
            stalledEntityTicksByUuid.remove(entityUuid);
            return false;
        }

        if (previousAge == null || previousAge != currentAge) {
            stalledEntityTicksByUuid.remove(entityUuid);
            return true;
        }

        int stalledTicks = stalledEntityTicksByUuid.merge(entityUuid, 1, Integer::sum);
        return stalledTicks < TICK_STALL_THRESHOLD_TICKS;
    }

    private void syncLazyChunksForPlayer(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        long currentTick = serverWorld.getTime();
        ChunkPos center = player.getChunkPos();
        int viewDistance = Math.min(player.getViewDistance(), serverWorld.getServer().getPlayerManager().getViewDistance());
        int effectiveViewDistance = viewDistance + LAZY_CHUNK_EXTRA_RADIUS;
        String playerKey = player.getUuidAsString();
        PlayerChunkViewState state = PLAYER_CHUNK_VIEW_STATES.get(playerKey);
        boolean viewChanged = state == null
                || state.viewDistance != viewDistance
                || state.worldKey != serverWorld.getRegistryKey()
                || state.center.x != center.x
                || state.center.z != center.z;
        boolean shouldSyncChunkStates = viewChanged || currentTick % CHUNK_STATE_SYNC_INTERVAL_TICKS == 0;
        List<WorldChunk> lazyChunksToQueue = new ArrayList<>();
        List<ChunkStatesPayload.Entry> chunkStateEntries = shouldSyncChunkStates ? new ArrayList<>() : List.of();

        if (state == null || state.viewDistance != viewDistance || state.worldKey != serverWorld.getRegistryKey()) {
            state = new PlayerChunkViewState(serverWorld.getRegistryKey(), center, viewDistance);
            PLAYER_CHUNK_VIEW_STATES.put(playerKey, state);
        } else if (viewChanged) {
            state.center = center;
            ChunkPos currentCenter = center;
            int currentViewDistance = effectiveViewDistance;
            state.syncedLazyChunks.removeIf(chunkPos -> !isWithinVisibleRange(currentCenter, currentViewDistance, new ChunkPos(chunkPos)));
        }

        if (shouldSyncChunkStates) {
            forEachVisibleChunk(center, effectiveViewDistance, pos -> collectChunkState(serverWorld, pos, chunkStateEntries));
        }

        if (ServerViewConfig.borderChunkRenderingEnabled) {
            PlayerChunkViewState currentState = state;
            forEachVisibleChunk(center, effectiveViewDistance, pos -> collectLazyChunk(
                    serverWorld,
                    pos,
                    lazyChunksToQueue,
                    currentState));
        }

        if (shouldSyncChunkStates) {
            ServerPlayNetworking.send(player, new ChunkStatesPayload(chunkStateEntries));
        }

        if (!lazyChunksToQueue.isEmpty()) {
            for (WorldChunk chunk : lazyChunksToQueue) {
                player.networkHandler.chunkDataSender.add(chunk);
            }
        }

        player.networkHandler.chunkDataSender.sendChunkBatches(player);
        state.center = center;
    }

    private void syncRemoteRodState(ServerPlayerEntity player, long currentTick) {
        FishingBobberEntity bobber = player.fishHook;
        boolean hasBobber = bobber != null && !bobber.isRemoved();
        boolean remoteActive = false;

        if (hasBobber && bobber.getEntityWorld() instanceof ServerWorld bobberWorld) {
            ChunkLevelType levelType = getChunkLevelType(bobberWorld, bobber.getChunkPos());
            remoteActive = levelType == ChunkLevelType.BLOCK_TICKING;
        }

        UUID playerUuid = player.getUuid();
        RemoteRodSyncState previousState = PLAYER_REMOTE_ROD_SYNC_STATES.get(playerUuid);
        boolean changed = previousState == null
                || previousState.hasBobber != hasBobber
                || previousState.remoteActive != remoteActive;
        boolean periodicResync = previousState == null || currentTick - previousState.lastSyncTick >= REMOTE_ROD_SYNC_INTERVAL_TICKS;

        if (changed || periodicResync) {
            ServerPlayNetworking.send(player, new RemoteRodStatePayload(hasBobber, remoteActive));
            PLAYER_REMOTE_ROD_SYNC_STATES.put(playerUuid, new RemoteRodSyncState(hasBobber, remoteActive, currentTick));
        }
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
        return ChunkFilter.isWithinDistance(center.x, center.z, viewDistance, x, z, true);
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
            List<WorldChunk> lazyChunksToQueue,
            PlayerChunkViewState state) {
        WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.x, pos.z);
        long packedChunkPos = pos.toLong();
        if (chunk == null) {
            state.syncedLazyChunks.remove(packedChunkPos);
            return;
        }

        if (isLazyChunkLevel(chunk.getLevelType())) {
            if (state.syncedLazyChunks.add(packedChunkPos)) {
                lazyChunksToQueue.add(chunk);
            }
        } else {
            state.syncedLazyChunks.remove(packedChunkPos);
        }
    }

    private boolean isLazyChunkLevel(ChunkLevelType levelType) {
        return levelType == ChunkLevelType.BLOCK_TICKING || levelType == ChunkLevelType.FULL;
    }

    private ChunkLevelType getChunkLevelType(ServerWorld world, ChunkPos pos) {
        WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.x, pos.z);
        return chunk == null ? null : chunk.getLevelType();
    }

    private static final class PlayerChunkViewState {
        private final net.minecraft.registry.RegistryKey<World> worldKey;
        private ChunkPos center;
        private final int viewDistance;
        private final Set<Long> syncedLazyChunks = new HashSet<>();

        private PlayerChunkViewState(
                net.minecraft.registry.RegistryKey<World> worldKey,
                ChunkPos center,
                int viewDistance) {
            this.worldKey = worldKey;
            this.center = center;
            this.viewDistance = viewDistance;
        }
    }

    private static final class RemoteRodSyncState {
        private final boolean hasBobber;
        private final boolean remoteActive;
        private final long lastSyncTick;

        private RemoteRodSyncState(boolean hasBobber, boolean remoteActive, long lastSyncTick) {
            this.hasBobber = hasBobber;
            this.remoteActive = remoteActive;
            this.lastSyncTick = lastSyncTick;
        }
    }

    /**
     * Tracks entity state for change detection in entity sync mode.
     * Prevents redundant network packets when nothing has changed.
     */
    private static final class EntitySyncState {
        final boolean isTicking;
        final Vec3d position;
        final Vec3d velocity;
        final float yaw;
        final float pitch;

        EntitySyncState(boolean isTicking, Vec3d position, Vec3d velocity, float yaw, float pitch) {
            this.isTicking = isTicking;
            this.position = position;
            this.velocity = velocity;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    /**
     * Determines if an entity should be synced based on state changes.
     * Returns true if ticking state changed, position moved significantly, or velocity changed.
     */
    private static boolean shouldSyncEntity(int entityId, boolean isTicking, Vec3d position, Vec3d velocity, 
                                             float yaw, float pitch, EntitySyncState lastState) {
        if (lastState == null) {
            return true; // First sync for this entity
        }
        
        if (lastState.isTicking != isTicking) {
            return true; // Ticking state changed
        }
        
        if (lastState.position.squaredDistanceTo(position) > ENTITY_POSITION_CHANGE_THRESHOLD * ENTITY_POSITION_CHANGE_THRESHOLD) {
            return true; // Position changed significantly
        }
        
        if (lastState.velocity.squaredDistanceTo(velocity) > ENTITY_VELOCITY_CHANGE_THRESHOLD * ENTITY_VELOCITY_CHANGE_THRESHOLD) {
            return true; // Velocity changed significantly
        }
        
        if (Float.compare(lastState.yaw, yaw) != 0 || Float.compare(lastState.pitch, pitch) != 0) {
            return true; // Rotation changed
        }
        
        return false; // No significant change
    }
}
