package b4.serverview.client;

import b4.serverview.ServerViewConfig;
import b4.serverview.accessor.EntityTickAccessor;
import b4.serverview.network.ChunkStatesPayload;
import b4.serverview.network.EntityStatePayload;
import b4.serverview.network.RemoteRodStatePayload;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.PositionInterpolator;
import net.minecraft.server.world.ChunkLevelType;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class ServerviewClient implements ClientModInitializer {
    private static final long RECENT_CHUNK_LOAD_TTL = 80L;
    private static final Map<Long, ChunkLevelType> CHUNK_LEVEL_STATES = new HashMap<>();
    private static final Map<Long, Long> RECENT_CHUNK_LOADS = new HashMap<>();
    private static final KeyBinding TOGGLE_ALL_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.serverview.toggle_all",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            KeyBinding.Category.MISC
    ));

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ChunkStatesPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                CHUNK_LEVEL_STATES.clear();

                for (ChunkStatesPayload.Entry entry : payload.entries()) {
                    CHUNK_LEVEL_STATES.put(entry.packedChunkPos(), entry.levelType());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(EntityStatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().world == null) {
                    return;
                }

                Entity entity = context.client().world.getEntityById(payload.entityId());
                if (entity instanceof EntityTickAccessor accessor) {
                    accessor.serverview$setTickingTruth(payload.isTicking());

                    if (!payload.isTicking()) {
                        PositionInterpolator interpolator = entity.getInterpolator();
                        Vec3d serverPos = payload.position();
                        entity.getTrackedPosition().setPos(serverPos);

                        if (ServerViewConfig.masterToggle && ServerViewConfig.entityFreezeEnabled) {
                            entity.refreshPositionAndAngles(serverPos, payload.yaw(), payload.pitch());
                        }

                        if (interpolator != null) {
                            interpolator.clear();
                        }
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(RemoteRodStatePayload.ID, (payload, context) -> {
            context.client().execute(() -> RemoteRodDetector.onServerRemoteRodState(payload.hasBobber(), payload.remoteActive()));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) {
                CHUNK_LEVEL_STATES.clear();
                RECENT_CHUNK_LOADS.clear();
                ClientEntityTickStateTracker.clear();
                GhostBlockTracker.clear();
                RemoteRodDetector.clear();
            } else {
                long currentTick = client.world.getTime();
                RECENT_CHUNK_LOADS.entrySet().removeIf(entry -> currentTick - entry.getValue() > RECENT_CHUNK_LOAD_TTL);
                ClientEntityTickStateTracker.tick(client.world);
                GhostBlockTracker.tick(client.world);
                RemoteRodDetector.tick(client);
            }

            while (TOGGLE_ALL_KEY.wasPressed()) {
                ServerViewConfig.masterToggle = !ServerViewConfig.masterToggle;

                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal("ServerView " + (ServerViewConfig.masterToggle ? "enabled" : "disabled")),
                            true
                    );
                }
            }
        });
    }

    public static Map<Long, ChunkLevelType> getChunkLevelStates() {
        return CHUNK_LEVEL_STATES;
    }

    public static void noteChunkDataReceived(ChunkPos chunkPos, long worldTime) {
        RECENT_CHUNK_LOADS.put(chunkPos.toLong(), worldTime);
    }

    public static boolean wasChunkLoadedRecently(ChunkPos chunkPos, long worldTime) {
        Long lastLoadTick = RECENT_CHUNK_LOADS.get(chunkPos.toLong());
        return lastLoadTick != null && worldTime - lastLoadTick <= RECENT_CHUNK_LOAD_TTL;
    }
}
