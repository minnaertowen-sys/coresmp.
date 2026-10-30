package net.coresmp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.*;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class Cmds {
    private record Req(UUID from, long expires) {}

    /** target player -> latest request addressed to them */
    private static final Map<UUID, Req> tpa = new HashMap<>();
    private static final Random RNG = new Random();

    private Cmds() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        // ---- spawn ----
        d.register(literal("spawn").executes(c -> spawn(c.getSource().getPlayerOrException())));
        d.register(literal("setspawn")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(c -> setSpawn(c.getSource().getPlayerOrException())));

        // ---- homes ----
        d.register(literal("sethome")
                .executes(c -> setHome(c.getSource().getPlayerOrException(), "home"))
                .then(argument("name", StringArgumentType.word())
                        .executes(c -> setHome(c.getSource().getPlayerOrException(),
                                StringArgumentType.getString(c, "name")))));
        d.register(literal("home")
                .executes(c -> home(c.getSource().getPlayerOrException(), "home"))
                .then(argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                Store.homes(c.getSource().getPlayerOrException().getUUID()).keySet(), b))
                        .executes(c -> home(c.getSource().getPlayerOrException(),
                                StringArgumentType.getString(c, "name")))));
        d.register(literal("delhome")
                .then(argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                Store.homes(c.getSource().getPlayerOrException().getUUID()).keySet(), b))
                        .executes(c -> delHome(c.getSource().getPlayerOrException(),
                                StringArgumentType.getString(c, "name")))));
        d.register(literal("homes").executes(c -> listHomes(c.getSource().getPlayerOrException())));

        // ---- warps ----
        d.register(literal("warp")
                .then(argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Store.warps().keySet(), b))
                        .executes(c -> warp(c.getSource().getPlayerOrException(),
                                StringArgumentType.getString(c, "name")))));
        d.register(literal("warps").executes(c -> listWarps(c.getSource().getPlayerOrException())));
        d.register(literal("setwarp")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("name", StringArgumentType.word())
                        .executes(c -> setWarp(c.getSource().getPlayerOrException(),
                                StringArgumentType.getString(c, "name")))));
        d.register(literal("delwarp")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Store.warps().keySet(), b))
                        .executes(c -> delWarp(c.getSource().getPlayerOrException(),
                                StringArgumentType.getString(c, "name")))));

        // ---- tpa ----
        d.register(literal("tpa")
                .then(argument("player", EntityArgument.player())
                        .executes(c -> tpaSend(c.getSource().getPlayerOrException(),
                                EntityArgument.getPlayer(c, "player")))));
        d.register(literal("tpaccept").executes(c -> tpaAccept(c.getSource().getPlayerOrException())));
        d.register(literal("tpdeny").executes(c -> tpaDeny(c.getSource().getPlayerOrException())));

        // ---- rtp ----
        d.register(literal("rtp").executes(c -> rtp(c.getSource().getPlayerOrException())));

    }

    // ------------------------------------------------------------ spawn
    private static int spawn(ServerPlayer p) {
        Loc s = Store.spawn();
        if (s == null) {
            Msg.send(p, "§cSpawn has not been set yet. An admin must run /setspawn.");
            return 0;
        }
        if (!cooldownOk(p, "spawn")) return 0;
        Teleports.setCooldown(p, "spawn", Config.get().spawnCooldownSeconds);
        Teleports.request(p, s, "spawn");
        return 1;
    }

    private static int setSpawn(ServerPlayer p) {
        Store.setSpawn(Loc.of(p));
        Msg.send(p, "Spawn set to your location.");
        return 1;
    }

    // ------------------------------------------------------------ homes
    private static int setHome(ServerPlayer p, String name) {
        Map<String, Loc> homes = Store.homes(p.getUUID());
        if (!homes.containsKey(name) && homes.size() >= Config.get().maxHomes) {
            Msg.send(p, "§cYou can only have §e" + Config.get().maxHomes + "§c homes. Use /delhome first.");
            return 0;
        }
        homes.put(name, Loc.of(p));
        Store.markDirty();
        Msg.send(p, "Home §e" + name + "§f set.");
        return 1;
    }

    private static int home(ServerPlayer p, String name) {
        Loc l = Store.homes(p.getUUID()).get(name);
        if (l == null) {
            Msg.send(p, "§cNo home named §e" + name + "§c. Your homes: §f" + String.join(", ", Store.homes(p.getUUID()).keySet()));
            return 0;
        }
        if (!cooldownOk(p, "home")) return 0;
        Teleports.setCooldown(p, "home", Config.get().homeCooldownSeconds);
        Teleports.request(p, l, "home " + name);
        return 1;
    }

    private static int delHome(ServerPlayer p, String name) {
        if (Store.homes(p.getUUID()).remove(name) == null) {
            Msg.send(p, "§cNo home named §e" + name + "§c.");
            return 0;
        }
        Store.markDirty();
        Msg.send(p, "Home §e" + name + "§f deleted.");
        return 1;
    }

    private static int listHomes(ServerPlayer p) {
        Set<String> names = Store.homes(p.getUUID()).keySet();
        Msg.send(p, "Homes (" + names.size() + "/" + Config.get().maxHomes + "): §e" + (names.isEmpty() ? "none" : String.join(", ", names)));
        return 1;
    }

    // ------------------------------------------------------------ warps
    private static int warp(ServerPlayer p, String name) {
        Loc l = Store.warps().get(name);
        if (l == null) {
            Msg.send(p, "§cNo warp named §e" + name + "§c.");
            return 0;
        }
        if (!cooldownOk(p, "warp")) return 0;
        Teleports.setCooldown(p, "warp", Config.get().warpCooldownSeconds);
        Teleports.request(p, l, "warp " + name);
        return 1;
    }

    private static int listWarps(ServerPlayer p) {
        Set<String> names = Store.warps().keySet();
        Msg.send(p, "Warps: §e" + (names.isEmpty() ? "none" : String.join(", ", names)));
        return 1;
    }

    private static int setWarp(ServerPlayer p, String name) {
        Store.warps().put(name, Loc.of(p));
        Store.markDirty();
        Msg.send(p, "Warp §e" + name + "§f set.");
        return 1;
    }

    private static int delWarp(ServerPlayer p, String name) {
        if (Store.warps().remove(name) == null) {
            Msg.send(p, "§cNo warp named §e" + name + "§c.");
            return 0;
        }
        Store.markDirty();
        Msg.send(p, "Warp §e" + name + "§f deleted.");
        return 1;
    }

    // ------------------------------------------------------------ tpa
    private static int tpaSend(ServerPlayer from, ServerPlayer to) {
        if (from.getUUID().equals(to.getUUID())) {
            Msg.send(from, "§cYou can't teleport to yourself.");
            return 0;
        }
        if (!cooldownOk(from, "tpa")) return 0;
        Teleports.setCooldown(from, "tpa", Config.get().tpaCooldownSeconds);
        long expires = System.currentTimeMillis() + Config.get().tpaTimeoutSeconds * 1000L;
        tpa.put(to.getUUID(), new Req(from.getUUID(), expires));
        String fromName = from.getName().getString();
        Msg.send(from, "Request sent to §e" + to.getName().getString() + "§f.");
        Msg.send(to, "§e" + fromName + "§f wants to teleport to you. §a/tpaccept §for §c/tpdeny §f(" + Config.get().tpaTimeoutSeconds + "s)");
        return 1;
    }

    private static Req takeRequest(ServerPlayer target) {
        Req r = tpa.remove(target.getUUID());
        if (r == null || r.expires() < System.currentTimeMillis()) return null;
        return r;
    }

    private static int tpaAccept(ServerPlayer target) {
        Req r = takeRequest(target);
        if (r == null) {
            Msg.send(target, "§cNo pending teleport request.");
            return 0;
        }
        MinecraftServer server = ((ServerLevel) target.level()).getServer();
        ServerPlayer from = server.getPlayerList().getPlayer(r.from());
        if (from == null) {
            Msg.send(target, "§cThat player is no longer online.");
            return 0;
        }
        Msg.send(target, "Request accepted.");
        Teleports.request(from, Loc.of(target), target.getName().getString());
        return 1;
    }

    private static int tpaDeny(ServerPlayer target) {
        Req r = takeRequest(target);
        if (r == null) {
            Msg.send(target, "§cNo pending teleport request.");
            return 0;
        }
        MinecraftServer server = ((ServerLevel) target.level()).getServer();
        ServerPlayer from = server.getPlayerList().getPlayer(r.from());
        Msg.send(target, "Request denied.");
        if (from != null) Msg.send(from, "§cYour teleport request was denied.");
        return 1;
    }

    // ------------------------------------------------------------ rtp
    private static int rtp(ServerPlayer p) {
        ServerLevel lvl = (ServerLevel) p.level();
        if (lvl.dimension() != Level.OVERWORLD) {
            Msg.send(p, "§c/rtp only works in the Overworld.");
            return 0;
        }
        if (!cooldownOk(p, "rtp")) return 0;
        Loc dest = findRtp(lvl);
        if (dest == null) {
            Msg.send(p, "§cCouldn't find a safe spot. Try again.");
            return 0;
        }
        Teleports.setCooldown(p, "rtp", Config.get().rtpCooldownSeconds);
        Teleports.request(p, dest, "a random location");
        return 1;
    }

    private static Loc findRtp(ServerLevel lvl) {
        Config cfg = Config.get();
        for (int a = 0; a < cfg.rtpAttempts; a++) {
            double ang = RNG.nextDouble() * Math.PI * 2;
            double dist = cfg.rtpMinRadius + RNG.nextDouble() * Math.max(1, cfg.rtpMaxRadius - cfg.rtpMinRadius);
            int x = cfg.rtpCenterX + (int) (Math.cos(ang) * dist);
            int z = cfg.rtpCenterZ + (int) (Math.sin(ang) * dist);
            lvl.getChunk(x >> 4, z >> 4); // loads the chunk so the heightmap is valid
            BlockPos top = lvl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (!lvl.getWorldBorder().isWithinBounds(top)) continue;
            BlockState ground = lvl.getBlockState(top.below());
            if (ground.isAir() || !ground.getFluidState().isEmpty()) continue;
            if (ground.is(Blocks.MAGMA_BLOCK) || ground.is(Blocks.CACTUS) || ground.is(Blocks.FIRE)
                    || ground.is(Blocks.CAMPFIRE) || ground.is(Blocks.SWEET_BERRY_BUSH)) continue;
            return new Loc(Loc.dimKey(lvl), x + 0.5, top.getY(), z + 0.5, RNG.nextFloat() * 360f, 0f);
        }
        return null;
    }

    // ------------------------------------------------------------ info
    private static int info(ServerPlayer p) {
        MinecraftServer server = ((ServerLevel) p.level()).getServer();
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1048576;
        long max = rt.maxMemory() / 1048576;
        Msg.send(p, String.format("TPS §e%.1f§f | MSPT §e%.1f§f | Players §e%d§f | RAM §e%d/%d MB",
                Perf.tps(), Perf.mspt(), server.getPlayerList().getPlayerCount(), used, max));
        return 1;
    }

    // ------------------------------------------------------------ helpers
    private static boolean cooldownOk(ServerPlayer p, String type) {
        long left = Teleports.cooldownLeft(p, type);
        if (left > 0) {
            Msg.send(p, "§cWait §e" + left + "s§c before using that again.");
            return false;
        }
        return true;
    }
}
