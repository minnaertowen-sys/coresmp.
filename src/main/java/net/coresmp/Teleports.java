package net.coresmp;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/** Warmup + cooldown handling. All state is tiny and only touched on the main thread. */
public final class Teleports {
    private static final class Pending {
        double x, y, z;
        float hp;
        Loc dest;
        String label;
        int ticks;
    }

    private static final Map<UUID, Pending> pending = new HashMap<>();
    private static final Map<String, Long> cooldowns = new HashMap<>();

    private Teleports() {}

    /** @return seconds remaining on cooldown, or 0 if free. */
    public static long cooldownLeft(ServerPlayer p, String type) {
        Long until = cooldowns.get(p.getUUID() + ":" + type);
        if (until == null) return 0;
        long left = (until - System.currentTimeMillis() + 999) / 1000;
        return Math.max(0, left);
    }

    public static void setCooldown(ServerPlayer p, String type, int seconds) {
        if (seconds <= 0) return;
        cooldowns.put(p.getUUID() + ":" + type, System.currentTimeMillis() + seconds * 1000L);
    }

    public static void request(ServerPlayer p, Loc dest, String label) {
        int warmup = Config.get().teleportWarmupSeconds;
        if (warmup <= 0) {
            go(p, dest, label);
            return;
        }
        Pending pd = new Pending();
        pd.x = p.getX();
        pd.y = p.getY();
        pd.z = p.getZ();
        pd.hp = p.getHealth();
        pd.dest = dest;
        pd.label = label;
        pd.ticks = warmup * 20;
        pending.put(p.getUUID(), pd);
        Msg.send(p, "Teleporting to §e" + label + "§f in §e" + warmup + "s§f. Don't move!");
    }

    public static void cancel(UUID id) {
        pending.remove(id);
    }

    public static void tick(MinecraftServer server) {
        if (pending.isEmpty()) return;
        Iterator<Map.Entry<UUID, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> e = it.next();
            Pending pd = e.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            if (p.distanceToSqr(pd.x, pd.y, pd.z) > 0.6 || p.getHealth() < pd.hp) {
                Msg.send(p, "§cTeleport cancelled.");
                it.remove();
                continue;
            }
            pd.ticks--;
            if (pd.ticks <= 0) {
                it.remove();
                go(p, pd.dest, pd.label);
            } else if (pd.ticks % 20 == 0) {
                Msg.bar(p, "§eTeleporting in " + (pd.ticks / 20) + "...");
            }
        }
    }

    private static void go(ServerPlayer p, Loc dest, String label) {
        ServerLevel lvl = dest.level(((ServerLevel) p.level()).getServer());
        if (lvl == null) {
            Msg.send(p, "§cThat dimension no longer exists.");
            return;
        }
        p.teleportTo(lvl, dest.x, dest.y, dest.z, Set.of(), dest.yaw, dest.pitch, true);
        Msg.send(p, "Teleported to §e" + label + "§f.");
    }
}
