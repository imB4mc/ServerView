package b4.serverview.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public record EntityStatePayload(int entityId, boolean isTicking, Vec3d position, float yaw, float pitch) implements CustomPayload {
    public static final Id<EntityStatePayload> ID = new Id<>(Identifier.of("serverview", "entity_state"));

    public static final PacketCodec<RegistryByteBuf, EntityStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, EntityStatePayload::entityId,
            PacketCodecs.BOOLEAN, EntityStatePayload::isTicking,
            PacketCodecs.DOUBLE, payload -> payload.position().x,
            PacketCodecs.DOUBLE, payload -> payload.position().y,
            PacketCodecs.DOUBLE, payload -> payload.position().z,
            PacketCodecs.FLOAT, EntityStatePayload::yaw,
            PacketCodecs.FLOAT, EntityStatePayload::pitch,
            EntityStatePayload::new
    );

    public EntityStatePayload(int entityId, boolean isTicking, double x, double y, double z, float yaw, float pitch) {
        this(entityId, isTicking, new Vec3d(x, y, z), yaw, pitch);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
