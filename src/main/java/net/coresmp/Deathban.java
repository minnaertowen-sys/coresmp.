package net.coresmp;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.*;

public final class Deathban {
    private static final Map<UUID, Integer> kickQueue = new HashMap<>();

    private Deathban() {}

    private static boolean exempt(ServerPlayer p) {
        String n = p.getName().getString();
        for (String e : Config.get().deathbanExempt) if (e.equalsIgnoreCase(n)) return true;
        return false;
    }

    public static void onDeath(ServerPlayer p) {
        if (!Config.get().deathbanEnabled || exempt(p)) return;
        long until = System.currentTimeMillis() + Config.get().deathbanMinutes * 60_000L;
        Store.bans().put(p.getUUID().toString(), new Store.Ban(p.getName().getString(), until));
        Store.markDirty();
        kickQueue.put(p.getUUID(), 40); // let them see the death screen for 2 seconds
        Msg.send(p, "§cYou died! You are deathbanned for §e" + Config.get().deathbanMinutes + " minutes§c.");
    }

    private static Component banMessage(long remainingMs) {
        long s = Math.max(1, remainingMs / 1000);
        return Component.literal("§c§lDEATHBANNED\n§7You died. Come back in §e" + Kits.fmt(s));
    }

    public static void tick(MinecraftServer server) {
        if (kickQueue.isEmpty()) return;
        Iterator<Map.Entry<UUID, Integer>> it = kickQueue.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            int left = e.getValue() - 1;
            if (left > 0) {
                e.setValue(left);
                continue;
            }
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Store.Ban b = Store.bans().get(e.getKey().toString());
            if (p != null && b != null) p.connection.disconnect(banMessage(b.until - System.currentTimeMillis()));
        }
    }

    /** @return true if the player was kicked because they are still banned. */
    public static boolean checkJoin(ServerGamePacketListenerImpl handler) {
        ServerPlayer p = handler.getPlayer();
        Store.Ban b = Store.bans().get(p.getUUID().toString());
        if (b == null) return false;
        long left = b.until - System.currentTimeMillis();
        if (left <= 0 || exempt(p)) {
            Store.bans().remove(p.getUUID().toString());
            Store.markDirty();
            return false;
        }
        handler.disconnect(banMessage(left));
        return true;
    }

    public static boolean pardon(String name) {
        Iterator<Map.Entry<String, Store.Ban>> it = Store.bans().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Store.Ban> e = it.next();
            if (e.getValue().name.equalsIgnoreCase(name)) {
                it.remove();
                Store.markDirty();
                return true;
            }
        }
        return false;
    }

    public static List<String> names() {
        List<String> out = new ArrayList<>();
        for (Store.Ban b : Store.bans().values()) out.add(b.name);
        return out;
    }
}
