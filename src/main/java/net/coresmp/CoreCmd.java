package net.coresmp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Everything new lives under /core. */
public final class CoreCmd {
    private CoreCmd() {}

    private static void reply(CommandSourceStack s, String t) {
        s.sendSuccess(() -> Component.literal(Msg.PREFIX + t), false);
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(literal("core")
                .executes(c -> help(c.getSource()))
                .then(literal("help").executes(c -> help(c.getSource())))
                .then(literal("info").executes(c -> info(c.getSource())))
                .then(literal("reload")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(c -> {
                            Config.load();
                            reply(c.getSource(), "Config reloaded.");
                            return 1;
                        }))

                // ---- kits ----
                .then(literal("kits").executes(c -> {
                    reply(c.getSource(), "Kits: §e" + String.join(", ", Config.get().kits.keySet()));
                    return 1;
                }))
                .then(literal("kit")
                        .then(argument("name", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Config.get().kits.keySet(), b))
                                .executes(c -> Kits.give(c.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(c, "name")))))

                // ---- quests ----
                .then(literal("quest").executes(c -> {
                    Quests.status(c.getSource().getPlayerOrException());
                    return 1;
                }))

                // ---- army ----
                .then(literal("army")
                        .executes(c -> {
                            Army.status(c.getSource().getPlayerOrException());
                            return 1;
                        })
                        .then(literal("summon").executes(c -> Army.summon(c.getSource().getPlayerOrException())))
                        .then(literal("dismiss").executes(c -> Army.dismiss(c.getSource().getPlayerOrException())))
                        .then(literal("give")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(argument("player", EntityArgument.player())
                                        .then(argument("amount", IntegerArgumentType.integer(1, 100))
                                                .executes(c -> {
                                                    Army.give(EntityArgument.getPlayer(c, "player"),
                                                            IntegerArgumentType.getInteger(c, "amount"));
                                                    reply(c.getSource(), "Soldier slots granted.");
                                                    return 1;
                                                })))))

                // ---- chat disabler ----
                .then(literal("chat")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(literal("off").executes(c -> {
                            Store.setChatDisabled(true);
                            reply(c.getSource(), "Chat §cdisabled§f for everyone (except exempt names).");
                            return 1;
                        }))
                        .then(literal("on").executes(c -> {
                            Store.setChatDisabled(false);
                            reply(c.getSource(), "Chat §aenabled§f.");
                            return 1;
                        })))

                // ---- deathban ----
                .then(literal("deathban")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(literal("pardon")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Deathban.names(), b))
                                        .executes(c -> {
                                            String n = StringArgumentType.getString(c, "name");
                                            if (Deathban.pardon(n)) reply(c.getSource(), "Pardoned §e" + n + "§f.");
                                            else reply(c.getSource(), "§cNo active deathban for " + n);
                                            return 1;
                                        }))))

                // ---- anti-cheat ----
                .then(literal("ac")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(literal("alerts").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            boolean on = AntiCheat.toggleAlerts(p.getUUID());
                            Msg.send(p, "Anti-cheat alerts " + (on ? "§aON" : "§cOFF"));
                            return 1;
                        }))
                        .then(literal("status")
                                .then(argument("player", EntityArgument.player()).executes(c -> {
                                    ServerPlayer t = EntityArgument.getPlayer(c, "player");
                                    reply(c.getSource(), t.getName().getString() + " violations: §e"
                                            + AntiCheat.violations(t.getUUID()));
                                    return 1;
                                }))))

                // ---- unstable extras ----
                .then(literal("extras")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(literal("toggle").executes(c -> {
                            Store.setUnstableOn(!Store.unstableOn());
                            reply(c.getSource(), "Unstable events " + (Store.unstableOn() ? "§aON" : "§cOFF"));
                            return 1;
                        }))
                        .then(literal("trigger").executes(c -> {
                            if (!Extras.trigger(c.getSource().getServer())) reply(c.getSource(), "§cNo players online.");
                            return 1;
                        }))
                        .then(literal("strike")
                                .then(argument("player", EntityArgument.player()).executes(c -> {
                                    Extras.strike(c.getSource().getServer(), EntityArgument.getPlayer(c, "player"));
                                    return 1;
                                })))));
    }

    private static int help(CommandSourceStack s) {
        reply(s, "§e/core kit <name> §7| §e/core kits §7- claim kits");
        reply(s, "§e/core quest §7- your current quest   §e/core army [summon|dismiss] §7- your soldiers");
        reply(s, "§e/core info §7- TPS & server stats");
        reply(s, "§7Also: /spawn /home /sethome /homes /delhome /warp /warps /tpa /tpaccept /tpdeny /rtp");
        reply(s, "§cAdmin: §7/core reload | chat on/off | deathban pardon | ac alerts/status | extras toggle/trigger/strike | army give");
        return 1;
    }

    private static int info(CommandSourceStack s) {
        MinecraftServer server = s.getServer();
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1048576;
        long max = rt.maxMemory() / 1048576;
        reply(s, String.format("TPS §e%.1f§f | MSPT §e%.1f§f | Players §e%d§f | RAM §e%d/%d MB",
                Perf.tps(), Perf.mspt(), server.getPlayerList().getPlayerCount(), used, max));
        return 1;
    }
}
