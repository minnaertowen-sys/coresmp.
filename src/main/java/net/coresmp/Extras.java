package net.coresmp;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Random;

/** Unstable SMP extras: random world events and an "orbital strike" admin tool. */
public final class Extras {
    private static final Random RNG = new Random();
    private static int seconds = 0;

    private Extras() {}

    /** Called once per second. */
    public static void second(MinecraftServer s) {
        if (!Store.unstableOn() || !Config.get().unstableEnabled) return;
        if (++seconds < Config.get().unstableIntervalMinutes * 60) return;
        seconds = 0;
        trigger(s);
    }

    public static boolean trigger(MinecraftServer s) {
        List<ServerPlayer> players = s.getPlayerList().getPlayers();
        List<Config.Event> events = Config.get().unstableEvents;
        if (players.isEmpty() || events.isEmpty()) return false;
        Config.Event ev = events.get(RNG.nextInt(events.size()));
        ServerPlayer target = players.get(RNG.nextInt(players.size()));
        s.getPlayerList().broadcastSystemMessage(Component.literal(
                "§d[Unstable] " + ev.announce.replace("%name%", target.getName().getString())), false);
        Run.forPlayer(target, ev.commands);
        return true;
    }

    public static void strike(MinecraftServer s, ServerPlayer target) {
        String u = target.getStringUUID();
        s.getPlayerList().broadcastSystemMessage(Component.literal(
                "§4§l[ORBITAL STRIKE] §cTarget locked: §f" + target.getName().getString()), false);
        for (int i = 0; i < 3; i++) Run.cmd(s, "execute at " + u + " run summon minecraft:lightning_bolt ~ ~ ~");
        Run.cmd(s, "execute at " + u + " run summon minecraft:tnt ~ ~15 ~ {fuse:60}");
    }
}
