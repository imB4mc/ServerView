package b4.serverview.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Payload for entity state synchronization.
 * Includes velocity to ensure perfect client-side sync when entitySyncEnabled is true.
 * Fixes entity stack separation and prevents client-side position guessing.
 */
public record EntityStatePayload(int entityId, boolean isTicking, Vec3d position, float yaw, float pitch, Vec3d velocity) implements CustomPayload {
    public static final Id<EntityStatePayload> ID = new Id<>(Identifier.of("serverview", "entity_state"));

    public static final PacketCodec<RegistryByteBuf, EntityStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, EntityStatePayload::entityId,
            PacketCodecs.BOOLEAN, EntityStatePayload::isTicking,
            PacketCodecs.DOUBLE, payload -> payload.position().x,
            PacketCodecs.DOUBLE, payload -> payload.position().y,
            PacketCodecs.DOUBLE, payload -> payload.position().z,
            PacketCodecs.FLOAT, EntityStatePayload::yaw,
            PacketCodecs.FLOAT, EntityStatePayload::pitch,
            PacketCodecs.DOUBLE, payload -> payload.velocity().x,
            PacketCodecs.DOUBLE, payload -> payload.velocity().y,
            PacketCodecs.DOUBLE, payload -> payload.velocity().z,
            EntityStatePayload::new
    );

    public EntityStatePayload(int entityId, boolean isTicking, double x, double y, double z, float yaw, float pitch, double velX, double velY, double velZ) {
        this(entityId, isTicking, new Vec3d(x, y, z), yaw, pitch, new Vec3d(velX, velY, velZ));
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
