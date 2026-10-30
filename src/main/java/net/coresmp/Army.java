package net.coresmp;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

import java.util.*;

/**
 * Player armies: quest rewards unlock soldiers (iron golems that never attack players).
 * They follow their owner, teleport to them if left behind, and attack hostile mobs the owner fights.
 */
public final class Army {
    private static final String TAG = "coresmp_army";
    private static final String TAG_NEW = "coresmp_new";
    private static final Map<UUID, Integer> misses = new HashMap<>();

    private Army() {}

    private static Mob find(MinecraftServer s, UUID id) {
        for (ServerLevel l : s.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e instanceof Mob m) return m;
        }
        return null;
    }

    public static int summon(ServerPlayer p) {
        Store.PlayerData d = Store.pd(p.getUUID());
        if (d.army.size() >= d.soldiers) {
            Msg.send(p, "§cNo free soldier slots (§e" + d.army.size() + "/" + d.soldiers
                    + "§c). Finish quests with §f/core quest§c to earn more.");
            return 0;
        }
        MinecraftServer s = Run.server(p);
        String name = p.getName().getString();
        Run.cmd(s, "execute at " + p.getStringUUID() + " run summon minecraft:iron_golem ~ ~ ~ "
                + "{Tags:[\"" + TAG_NEW + "\"],PlayerCreated:1b,PersistenceRequired:1b,CustomNameVisible:1b,"
                + "CustomName:\"" + name + "'s Soldier\"}");
        ServerLevel lvl = (ServerLevel) p.level();
        List<Mob> found = lvl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(8),
                m -> m.getTags().contains(TAG_NEW));
        if (found.isEmpty()) {
            Msg.send(p, "§cCouldn't spawn a soldier here.");
            return 0;
        }
        Mob m = found.get(0);
        m.removeTag(TAG_NEW);
        m.addTag(TAG);
        d.army.add(m.getStringUUID());
        Store.markDirty();
        Msg.send(p, "A soldier joins you. Army: §e" + d.army.size() + "/" + d.soldiers);
        return 1;
    }

    public static int dismiss(ServerPlayer p) {
        Store.PlayerData d = Store.pd(p.getUUID());
        MinecraftServer s = Run.server(p);
        int n = 0;
        for (String id : d.army) {
            Mob m = find(s, UUID.fromString(id));
            if (m != null) {
                m.discard();
                n++;
            }
        }
        d.army.clear();
        Store.markDirty();
        Msg.send(p, "Army dismissed (§e" + n + "§f soldiers removed).");
        return 1;
    }

    public static void status(ServerPlayer p) {
        Store.PlayerData d = Store.pd(p.getUUID());
        Msg.send(p, "Army: §e" + d.army.size() + "§f active / §e" + d.soldiers + "§f earned. "
                + "§7/core army summon, /core army dismiss");
    }

    public static void give(ServerPlayer target, int amount) {
        Store.PlayerData d = Store.pd(target.getUUID());
        d.soldiers = Math.min(Config.get().maxArmy, d.soldiers + amount);
        Store.markDirty();
        Msg.send(target, "You were granted §e" + amount + "§f soldier slot(s). Total: §e" + d.soldiers);
    }

    /** Called every 10 ticks. */
    public static void tick(MinecraftServer s) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            Store.PlayerData d = Store.peek(p.getUUID());
            if (d == null || d.army.isEmpty()) continue;
            Iterator<String> it = d.army.iterator();
            while (it.hasNext()) {
                UUID id = UUID.fromString(it.next());
                Mob m = find(s, id);
                if (m == null) {
                    // may just be an unloaded chunk or still loading after a restart: give it ~15s
                    if (misses.merge(id, 1, Integer::sum) >= 30) {
                        it.remove();
                        misses.remove(id);
                        Store.markDirty();
                    }
                    continue;
                }
                misses.remove(id);
                if (!m.isAlive()) {
                    it.remove();
                    Store.markDirty();
                    continue;
                }
                if (m.level() != p.level() || m.distanceTo(p) > 24) {
                    Run.cmd(s, "tp " + id + " " + p.getStringUUID());
                    continue;
                }
                LivingEntity t = p.getLastHurtByMob();
                if (t == null || !t.isAlive()) t = p.getLastHurtMob();
                if (t instanceof Enemy && t.isAlive() && t.distanceTo(p) < 20 && !t.getTags().contains(TAG)) {
                    m.setTarget(t);
                } else if (m.getTarget() == null && m.distanceTo(p) > 6) {
                    m.getNavigation().moveTo(p, 1.15);
                }
            }
        }
    }
}
