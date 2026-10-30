package net.coresmp;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.Enemy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoreSmp implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("CoreSmp");
    private static int tickCounter = 0;
    private static int autosaveCounter = 0;

    @Override
    public void onInitialize() {
        Config.load();
        Store.load();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            Cmds.register(dispatcher);
            CoreCmd.register(dispatcher);
        });

        ServerTickEvents.START_SERVER_TICK.register(server -> Perf.tickStart());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Perf.tickEnd();
            Teleports.tick(server);
            AntiCheat.tick(server);
            Deathban.tick(server);
            tickCounter++;
            if (tickCounter % 10 == 0) Army.tick(server);
            if (tickCounter % 20 == 0) Extras.second(server);
            if (tickCounter % 1200 == 0) Quests.playMinute(server);
            if (++autosaveCounter >= Config.get().autosaveSeconds * 20) {
                autosaveCounter = 0;
                Store.saveAsyncIfDirty();
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (Deathban.checkJoin(handler)) return;
            ServerPlayer p = handler.getPlayer();
            String name = p.getName().getString();
            for (String line : Config.get().welcomeMessages) {
                p.sendSystemMessage(Component.literal(line.replace("%player%", name)));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Teleports.cancel(handler.getPlayer().getUUID());
            AntiCheat.remove(handler.getPlayer().getUUID());
        });

        // deaths: deathban + kill quests
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer victim) Deathban.onDeath(victim);
            if (source.getEntity() instanceof ServerPlayer killer && entity instanceof Enemy) {
                Quests.add(killer, "KILL_MOBS", 1);
            }
        });

        // mining quests
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer sp) Quests.add(sp, "MINE_BLOCKS", 1);
        });

        // anti-cheat: reach + autoclick
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer sp && AntiCheat.checkAttack(sp, entity)) return InteractionResult.FAIL;
            return InteractionResult.PASS;
        });

        // chat disabler
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (!Store.chatDisabled()) return true;
            String n = sender.getName().getString();
            for (String e : Config.get().chatExempt) if (e.equalsIgnoreCase(n)) return true;
            Msg.send(sender, "§cChat is disabled on this server.");
            return false;
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> Store.saveNow());

        LOG.info("CoreSmp loaded.");
    }
}
