package com.serie.skinurl;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SkinData extends SavedData {
    public final Map<UUID, String[]> skins = new HashMap<>();

    public static SkinData get(ServerLevel l) {
        return l.getDataStorage().computeIfAbsent(SkinData::load, SkinData::new, "skinurl");
    }

    public static SkinData load(CompoundTag t) {
        SkinData d = new SkinData();
        ListTag l = t.getList("s", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            d.skins.put(c.getUUID("u"), new String[]{c.getString("url"), c.getString("m")});
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag l = new ListTag();
        skins.forEach((u, v) -> { CompoundTag c = new CompoundTag(); c.putUUID("u", u); c.putString("url", v[0]); c.putString("m", v[1]); l.add(c); });
        t.put("s", l);
        return t;
    }
}
