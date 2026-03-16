package b4.serverview.client;

import b4.serverview.network.EntityStatePayload;
import b4.serverview.accessor.EntityTickAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.Entity;

public class ServerviewClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(EntityStatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                Entity entity = context.client().world.getEntityById(payload.entityId());
                if (entity instanceof EntityTickAccessor accessor) {
                    accessor.serverview$setTickingTruth(payload.isTicking());
                }
            });
        });
    }
}