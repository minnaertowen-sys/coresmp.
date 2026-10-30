package net.coresmp;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class Msg {
    public static final String PREFIX = "§6[Core] §f";

    private Msg() {}

    public static void send(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(PREFIX + text));
    }

    public static void bar(ServerPlayer p, String text) {
        p.displayClientMessage(Component.literal(text), true);
    }
}
