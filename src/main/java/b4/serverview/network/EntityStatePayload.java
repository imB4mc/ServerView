package b4.serverview.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record EntityStatePayload(int entityId, boolean isTicking) implements CustomPayload {
    public static final Id<EntityStatePayload> ID = new Id<>(Identifier.of("serverview", "entity_state"));

    public static final PacketCodec<RegistryByteBuf, EntityStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, EntityStatePayload::entityId,
            PacketCodecs.BOOLEAN, EntityStatePayload::isTicking, // It's BOOLEAN, not BOOL
            EntityStatePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}