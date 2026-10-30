package net.coresmp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Editable config at config/coresmp/config.json. Use /core reload after editing. */
public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance = new Config();

    // ---- teleport basics ----
    public int maxHomes = 3;
    public int teleportWarmupSeconds = 3;
    public int homeCooldownSeconds = 5;
    public int spawnCooldownSeconds = 5;
    public int warpCooldownSeconds = 5;
    public int tpaCooldownSeconds = 10;
    public int tpaTimeoutSeconds = 60;
    public int rtpCooldownSeconds = 300;
    public int rtpMinRadius = 500;
    public int rtpMaxRadius = 5000;
    public int rtpCenterX = 0;
    public int rtpCenterZ = 0;
    public int rtpAttempts = 6;
    public int autosaveSeconds = 300;
    public List<String> welcomeMessages = new ArrayList<>(List.of(
            "§6Welcome to CoreSmp, §f%player%§6!",
            "§7Type §f/core help §7for everything the server offers."
    ));

    // ---- deathban ----
    public boolean deathbanEnabled = true;
    public int deathbanMinutes = 30;
    /** Player names (any case) that are never deathbanned. Put your admins here. */
    public List<String> deathbanExempt = new ArrayList<>();

    // ---- chat disabler ----
    /** Player names that can still talk while chat is disabled. */
    public List<String> chatExempt = new ArrayList<>();

    // ---- army ----
    public int maxArmy = 10;

    // ---- anti-cheat ----
    public boolean antiCheat = true;
    public double maxAvgSpeedPerTick = 0.9;   // blocks/tick averaged over 1 second (sprint-jump is ~0.36)
    public int hoverTicks = 50;               // ticks of hovering in air before a fly flag
    public double maxReach = 6.0;             // centre-to-centre distance for melee hits
    public int maxCps = 22;                   // attacks per second
    public int kickAtViolations = 12;

    // ---- unstable extras ----
    public boolean unstableEnabled = true;
    public int unstableIntervalMinutes = 20;

    // ---- data you can edit ----
    public static class Quest {
        public String name;
        public String type;           // KILL_MOBS, MINE_BLOCKS or PLAY_MINUTES
        public int amount;
        public int rewardSoldiers;
        public List<String> rewardCommands = new ArrayList<>();

        public Quest() {}

        public Quest(String name, String type, int amount, int rewardSoldiers, String... cmds) {
            this.name = name;
            this.type = type;
            this.amount = amount;
            this.rewardSoldiers = rewardSoldiers;
            this.rewardCommands = new ArrayList<>(Arrays.asList(cmds));
        }
    }

    public static class Kit {
        public int cooldownSeconds;   // -1 = one time only
        public List<String> commands = new ArrayList<>();

        public Kit() {}

        public Kit(int cd, String... cmds) {
            this.cooldownSeconds = cd;
            this.commands = new ArrayList<>(Arrays.asList(cmds));
        }
    }

    public static class Event {
        public String announce;
        public List<String> commands = new ArrayList<>();

        public Event() {}

        public Event(String announce, String... cmds) {
            this.announce = announce;
            this.commands = new ArrayList<>(Arrays.asList(cmds));
        }
    }

    public List<Quest> quests = new ArrayList<>(List.of(
            new Quest("Monster Hunter", "KILL_MOBS", 25, 1, "give %player% minecraft:bread 16"),
            new Quest("Deep Miner", "MINE_BLOCKS", 300, 1, "give %player% minecraft:iron_ingot 16"),
            new Quest("Survivor", "PLAY_MINUTES", 60, 1, "give %player% minecraft:golden_apple 3"),
            new Quest("Slayer", "KILL_MOBS", 150, 2, "give %player% minecraft:diamond 4"),
            new Quest("Excavator", "MINE_BLOCKS", 1500, 2, "give %player% minecraft:diamond 8")
    ));

    public Map<String, Kit> kits = new LinkedHashMap<>(Map.of(
            "starter", new Kit(-1,
                    "give %player% minecraft:stone_sword 1",
                    "give %player% minecraft:stone_pickaxe 1",
                    "give %player% minecraft:cooked_beef 16"),
            "warrior", new Kit(3600,
                    "give %player% minecraft:iron_sword 1",
                    "give %player% minecraft:iron_chestplate 1",
                    "give %player% minecraft:shield 1",
                    "give %player% minecraft:golden_apple 2")
    ));

    /** %player% = the player's UUID (works as a selector), %name% = display name. */
    public List<Event> unstableEvents = new ArrayList<>(List.of(
            new Event("§5⚡ The sky is unstable! Lightning strikes around §f%name%§5!",
                    "execute at %player% run summon minecraft:lightning_bolt ~4 ~ ~4",
                    "execute at %player% run summon minecraft:lightning_bolt ~-4 ~ ~-4"),
            new Event("§bGravity warps! Everyone floats for a moment...",
                    "effect give @a minecraft:levitation 5 0",
                    "effect give @a minecraft:slow_falling 40 0"),
            new Event("§aSupply drop for §f%name%§a!",
                    "give %player% minecraft:diamond 3",
                    "give %player% minecraft:golden_apple 2"),
            new Event("§cA horde spawns near §f%name%§c!",
                    "execute at %player% run summon minecraft:zombie ~3 ~ ~3",
                    "execute at %player% run summon minecraft:zombie ~-3 ~ ~3",
                    "execute at %player% run summon minecraft:zombie ~3 ~ ~-3",
                    "execute at %player% run summon minecraft:skeleton ~-3 ~ ~-3"),
            new Event("§eSpeed storm! Everyone is fast for 60 seconds.",
                    "effect give @a minecraft:speed 60 2")
    ));

    public static Config get() {
        return instance;
    }

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("coresmp");
    }

    public static void load() {
        Path file = dir().resolve("config.json");
        try {
            Files.createDirectories(dir());
            if (Files.exists(file)) {
                Config c = GSON.fromJson(Files.readString(file), Config.class);
                if (c != null) instance = c;
            }
            Files.writeString(file, GSON.toJson(instance)); // writes defaults / new fields
        } catch (Exception e) {
            CoreSmp.LOG.error("Could not load config, using defaults", e);
        }
    }
}
