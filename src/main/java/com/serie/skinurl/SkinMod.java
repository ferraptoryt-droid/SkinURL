package com.serie.skinurl;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@Mod(SkinMod.ID)
public class SkinMod {
    public static final String ID = "skinurl";
    private static final String PROTO = "1";
    public static final SimpleChannel NET = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ID, "main"), () -> PROTO, PROTO::equals, PROTO::equals);

    public SkinMod() {
        NET.registerMessage(0, SkinPacket.class, SkinPacket::encode, SkinPacket::decode, SkinPacket::handle);
        MinecraftForge.EVENT_BUS.register(this);
    }

    /** Solo se aceptan las texturas de Mojang (las que genera mineskin.org). */
    static boolean urlOk(String s) {
        try {
            URI u = URI.create(s);
            return "https".equalsIgnoreCase(u.getScheme()) && "textures.minecraft.net".equalsIgnoreCase(u.getHost())
                    && u.getPath() != null && u.getPath().startsWith("/texture/") && s.length() < 200;
        } catch (Exception e) {
            return false;
        }
    }

    @SubscribeEvent
    public void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("skin")
                .then(Commands.literal("reset").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    set(p, "", false);
                    c.getSource().sendSuccess(() -> Component.literal("Skin restablecida."), false);
                    return 1;
                }))
                .then(Commands.argument("url", StringArgumentType.greedyString()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    String raw = StringArgumentType.getString(c, "url").trim();
                    boolean slim = false;
                    if (raw.toLowerCase().endsWith(" slim")) { slim = true; raw = raw.substring(0, raw.length() - 5).trim(); }
                    if (!urlOk(raw)) {
                        c.getSource().sendFailure(Component.literal(
                                "URL no válida: solo https://textures.minecraft.net/texture/... (generala en https://mineskin.org/)"));
                        return 0;
                    }
                    set(p, raw, slim);
                    c.getSource().sendSuccess(() -> Component.literal("Skin aplicada. La ven todos."), false);
                    return 1;
                })));
    }

    static void set(ServerPlayer p, String url, boolean slim) {
        SkinData d = SkinData.get(p.server.getLevel(Level.OVERWORLD));
        if (url.isEmpty()) d.skins.remove(p.getUUID()); else d.skins.put(p.getUUID(), new String[]{url, slim ? "1" : "0"});
        d.setDirty();
        NET.send(PacketDistributor.ALL.noArg(), new SkinPacket(p.getUUID(), url, slim));
    }

    @SubscribeEvent
    public void join(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        ServerLevel ow = sp.server.getLevel(Level.OVERWORLD);
        SkinData d = SkinData.get(ow);
        // el que entra recibe las skins de todos; todos reciben la suya
        for (Map.Entry<UUID, String[]> en : d.skins.entrySet())
            NET.send(PacketDistributor.PLAYER.with(() -> sp), new SkinPacket(en.getKey(), en.getValue()[0], "1".equals(en.getValue()[1])));
    }
}
