package net.coresmp;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Conservative server-side checks (speed, hover-fly, reach, auto-click). Violations decay over time,
 * players are set back, and only repeated flags lead to a kick. It never bans.
 */
public final class AntiCheat {
    private static final class St {
        boolean init;
        double x, y, z;
        double lgx, lgy, lgz;
        boolean hasGround;
        double dist;
        int ticks, hover, grace;
        int vl;
        int decay;
        long atkWindow;
        int atkCount;
    }

    private static final Map<UUID, St> states = new HashMap<>();
    private static final Set<UUID> alerts = new HashSet<>();

    private AntiCheat() {}

    public static void remove(UUID id) {
        states.remove(id);
    }

    public static boolean toggleAlerts(UUID id) {
        if (!alerts.remove(id)) {
            alerts.add(id);
            return true;
        }
        return false;
    }

    public static int violations(UUID id) {
        St s = states.get(id);
        return s == null ? 0 : s.vl;
    }

    public static void tick(MinecraftServer server) {
        Config cfg = Config.get();
        if (!cfg.antiCheat) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            St st = states.computeIfAbsent(p.getUUID(), k -> new St());
            double x = p.getX(), y = p.getY(), z = p.getZ();
            if (st.vl > 0 && ++st.decay >= 600) {
                st.vl--;
                st.decay = 0;
            }
            if (!st.init) {
                st.init = true;
                st.x = x;
                st.y = y;
                st.z = z;
                continue;
            }
            double dx = x - st.x, dy = y - st.y, dz = z - st.z;
            double h = Math.sqrt(dx * dx + dz * dz);
            st.x = x;
            st.y = y;
            st.z = z;
            if (st.grace > 0) st.grace--;

            boolean exempt = st.grace > 0 || h > 10 || Math.abs(dy) > 10
                    || p.isSpectator() || p.isCreative() || p.getAbilities().mayfly
                    || p.isPassenger() || p.isFallFlying() || p.isInWater() || p.isInLava() || p.onClimbable();
            if (exempt) {
                st.dist = 0;
                st.ticks = 0;
                st.hover = 0;
                if (p.onGround()) setGround(st, p);
                continue;
            }
            if (p.onGround()) setGround(st, p);

            // ---- speed: average horizontal speed over 20 ticks ----
            st.dist += h;
            if (++st.ticks >= 20) {
                double avg = st.dist / 20.0;
                st.dist = 0;
                st.ticks = 0;
                if (avg > cfg.maxAvgSpeedPerTick) flag(server, p, st, "speed", String.format("%.2f b/t", avg));
            }

            // ---- fly: hovering in mid-air ----
            if (!p.onGround() && Math.abs(dy) < 0.005 && !slowingBlock((ServerLevel) p.level(), p)) {
                if (++st.hover > cfg.hoverTicks) {
                    st.hover = 0;
                    flag(server, p, st, "fly", "hovering");
                }
            } else {
                st.hover = 0;
            }
        }
    }

    private static void setGround(St st, ServerPlayer p) {
        st.hasGround = true;
        st.lgx = p.getX();
        st.lgy = p.getY();
        st.lgz = p.getZ();
    }

    private static boolean slowingBlock(ServerLevel lvl, ServerPlayer p) {
        BlockState b = lvl.getBlockState(p.blockPosition());
        return b.is(Blocks.COBWEB) || b.is(Blocks.SWEET_BERRY_BUSH) || b.is(Blocks.POWDER_SNOW)
                || b.is(Blocks.HONEY_BLOCK) || b.is(Blocks.SCAFFOLDING) || b.is(Blocks.VINE);
    }

    /** @return true if the attack should be cancelled. */
    public static boolean checkAttack(ServerPlayer p, Entity target) {
        Config cfg = Config.get();
        if (!cfg.antiCheat || p.isCreative() || p.isSpectator()) return false;
        St st = states.computeIfAbsent(p.getUUID(), k -> new St());
        MinecraftServer server = Run.server(p);
        double d = Math.sqrt(p.distanceToSqr(target));
        if (d > cfg.maxReach) {
            flag(server, p, st, "reach", String.format("%.1f blocks", d));
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - st.atkWindow > 1000) {
            st.atkWindow = now;
            st.atkCount = 0;
        }
        if (++st.atkCount > cfg.maxCps) {
            flag(server, p, st, "autoclick", st.atkCount + " hits/s");
            return true;
        }
        return false;
    }

    private static void flag(MinecraftServer server, ServerPlayer p, St st, String type, String detail) {
        st.vl++;
        String name = p.getName().getString();
        CoreSmp.LOG.warn("[AntiCheat] {} flagged for {} ({}) VL={}", name, type, detail, st.vl);
        for (UUID id : alerts) {
            ServerPlayer a = server.getPlayerList().getPlayer(id);
            if (a != null) Msg.send(a, "§c[AC] §e" + name + "§f " + type + " (" + detail + ") VL " + st.vl);
        }
        if (st.vl >= Config.get().kickAtViolations) {
            st.vl = 0;
            p.connection.disconnect(net.minecraft.network.chat.Component.literal(
                    "§cKicked by CoreSmp anti-cheat (" + type + "). If this was a mistake, rejoin."));
            return;
        }
        if ((type.equals("speed") || type.equals("fly")) && st.hasGround) {
            ServerLevel lvl = (ServerLevel) p.level();
            p.teleportTo(lvl, st.lgx, st.lgy, st.lgz, Set.of(), p.getYRot(), p.getXRot(), true);
            st.grace = 20;
        }
    }
}
