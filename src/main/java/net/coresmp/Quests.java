package net.coresmp;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Sequential quests. Finishing a quest earns army soldiers (and optional reward commands). */
public final class Quests {
    private Quests() {}

    public static void add(ServerPlayer p, String type, int n) {
        List<Config.Quest> qs = Config.get().quests;
        Store.PlayerData d = Store.pd(p.getUUID());
        if (d.questIndex >= qs.size()) return;
        Config.Quest q = qs.get(d.questIndex);
        if (!q.type.equalsIgnoreCase(type)) return;
        d.progress += n;
        Store.markDirty();
        if (d.progress >= q.amount) {
            d.soldiers = Math.min(Config.get().maxArmy, d.soldiers + q.rewardSoldiers);
            d.questIndex++;
            d.progress = 0;
            Run.forPlayer(p, q.rewardCommands);
            Msg.send(p, "§aQuest complete: §e" + q.name + "§a! +§e" + q.rewardSoldiers
                    + "§a army soldier(s). Use §f/core army summon§a.");
            if (d.questIndex < qs.size()) Msg.send(p, "Next quest: §e" + describe(qs.get(d.questIndex)));
            else Msg.send(p, "§6You finished every quest!");
        } else if (d.progress % 10 == 0) {
            Msg.bar(p, "§e" + q.name + ": §f" + d.progress + "/" + q.amount);
        }
    }

    public static void playMinute(MinecraftServer s) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) add(p, "PLAY_MINUTES", 1);
    }

    public static String describe(Config.Quest q) {
        String what = switch (q.type.toUpperCase()) {
            case "KILL_MOBS" -> "Kill " + q.amount + " hostile mobs";
            case "MINE_BLOCKS" -> "Mine " + q.amount + " blocks";
            case "PLAY_MINUTES" -> "Play for " + q.amount + " minutes";
            default -> q.type + " x" + q.amount;
        };
        return q.name + " - " + what + " (reward: " + q.rewardSoldiers + " soldier(s))";
    }

    public static void status(ServerPlayer p) {
        List<Config.Quest> qs = Config.get().quests;
        Store.PlayerData d = Store.pd(p.getUUID());
        if (d.questIndex >= qs.size()) {
            Msg.send(p, "§6You finished every quest! Soldiers earned: §e" + d.soldiers);
            return;
        }
        Config.Quest q = qs.get(d.questIndex);
        Msg.send(p, "Quest " + (d.questIndex + 1) + "/" + qs.size() + ": §e" + describe(q));
        Msg.send(p, "Progress: §e" + d.progress + "/" + q.amount + "§f  | Soldiers earned: §e" + d.soldiers);
    }
}
