package net.coresmp;

import net.minecraft.server.level.ServerPlayer;

public final class Kits {
    private Kits() {}

    public static int give(ServerPlayer p, String name) {
        Config.Kit k = Config.get().kits.get(name);
        if (k == null) {
            Msg.send(p, "§cNo kit named §e" + name + "§c. Kits: §f" + String.join(", ", Config.get().kits.keySet()));
            return 0;
        }
        Store.PlayerData d = Store.pd(p.getUUID());
        long now = System.currentTimeMillis();
        Long last = d.kitUsed.get(name);
        if (last != null) {
            if (k.cooldownSeconds < 0) {
                Msg.send(p, "§cYou already claimed the §e" + name + "§c kit.");
                return 0;
            }
            long left = (last + k.cooldownSeconds * 1000L - now + 999) / 1000;
            if (left > 0) {
                Msg.send(p, "§cKit §e" + name + "§c is on cooldown for §e" + fmt(left) + "§c.");
                return 0;
            }
        }
        Run.forPlayer(p, k.commands);
        d.kitUsed.put(name, now);
        Store.markDirty();
        Msg.send(p, "You received the §e" + name + "§f kit.");
        return 1;
    }

    public static String fmt(long seconds) {
        if (seconds >= 3600) return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        if (seconds >= 60) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }
}
