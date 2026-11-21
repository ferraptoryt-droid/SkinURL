package com.serie.skinurl;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record SkinPacket(UUID id, String url, boolean slim) {
    public static void encode(SkinPacket p, FriendlyByteBuf b) { b.writeUUID(p.id); b.writeUtf(p.url, 256); b.writeBoolean(p.slim); }
    public static SkinPacket decode(FriendlyByteBuf b) { return new SkinPacket(b.readUUID(), b.readUtf(256), b.readBoolean()); }

    public static void handle(SkinPacket p, Supplier<NetworkEvent.Context> s) {
        NetworkEvent.Context c = s.get();
        // el cliente descarga solo URLs de Mojang (aunque el servidor mande otra cosa)
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSkins.receive(p.id(), p.url(), p.slim())));
        c.setPacketHandled(true);
    }
}
