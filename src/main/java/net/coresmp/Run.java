package net.coresmp;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Runs console commands so kits/quests/events can use any vanilla command. */
public final class Run {
    private Run() {}

    public static MinecraftServer server(ServerPlayer p) {
        return ((ServerLevel) p.level()).getServer();
    }

    public static String fill(String cmd, ServerPlayer p) {
        return cmd.replace("%player%", p.getStringUUID()).replace("%name%", p.getName().getString());
    }

    public static void cmd(MinecraftServer s, String command) {
        try {
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), command);
        } catch (Exception e) {
            CoreSmp.LOG.warn("Command failed: {} ({})", command, e.toString());
        }
    }

    public static void forPlayer(ServerPlayer p, Iterable<String> commands) {
        MinecraftServer s = server(p);
        for (String c : commands) cmd(s, fill(c, p));
    }
}
