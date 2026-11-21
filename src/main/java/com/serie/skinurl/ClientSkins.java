package com.serie.skinurl;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = SkinMod.ID, value = Dist.CLIENT)
public class ClientSkins {
    record Entry(String url, boolean slim, ResourceLocation loc) {}
    static final Map<UUID, Entry> SKINS = new ConcurrentHashMap<>();
    static Field TEX, MODEL;
    static int tick;

    static {
        try { TEX = ObfuscationReflectionHelper.findField(PlayerInfo.class, "textureLocations"); TEX.setAccessible(true); } catch (Throwable t) { t.printStackTrace(); }
        try { MODEL = ObfuscationReflectionHelper.findField(PlayerInfo.class, "skinModel"); MODEL.setAccessible(true); } catch (Throwable t) { MODEL = null; }
    }

    public static void receive(UUID id, String url, boolean slim) {
        if (url.isEmpty()) { SKINS.remove(id); return; }
        if (!SkinMod.urlOk(url)) return;
        Thread t = new Thread(() -> {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(8000); con.setReadTimeout(8000);
                try (InputStream in = con.getInputStream()) {
                    byte[] data = in.readNBytes(1_000_000);
                    NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(data));
                    if (img.getWidth() != 64 || img.getHeight() != 64) { img.close(); return; }
                    Minecraft.getInstance().execute(() -> {
                        ResourceLocation rl = new ResourceLocation(SkinMod.ID, "skin/" + id.toString().replace("-", "") + "_" + Integer.toHexString(url.hashCode()));
                        Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
                        SKINS.put(id, new Entry(url, slim, rl));
                        apply();
                    });
                }
            } catch (Exception ex) { System.err.println("[skinurl] no se pudo bajar la skin: " + ex); }
        }, "skinurl-dl");
        t.setDaemon(true); t.start();
    }

    @SuppressWarnings("unchecked")
    static void apply() {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null || TEX == null) return;
        for (var en : SKINS.entrySet()) {
            PlayerInfo info = conn.getPlayerInfo(en.getKey());
            if (info == null) continue;
            try {
                Map<MinecraftProfileTexture.Type, ResourceLocation> m = (Map<MinecraftProfileTexture.Type, ResourceLocation>) TEX.get(info);
                if (!en.getValue().loc().equals(m.get(MinecraftProfileTexture.Type.SKIN))) m.put(MinecraftProfileTexture.Type.SKIN, en.getValue().loc());
                if (MODEL != null) MODEL.set(info, en.getValue().slim() ? "slim" : "default");
            } catch (Throwable ignored) {}
        }
    }

    /** Reaplica cada segundo: el juego recarga la skin original en cuanto termina de bajarla o recrea el PlayerInfo. */
    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END && ++tick % 20 == 0) apply();
    }
}
