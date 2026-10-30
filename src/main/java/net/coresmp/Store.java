package net.coresmp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Persistent data in config/coresmp/data.json. Saved off-thread. */
public final class Store {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class PlayerData {
        public int questIndex;
        public int progress;
        public int soldiers;                       // army slots earned
        public List<String> army = new ArrayList<>(); // UUIDs of active soldiers
        public Map<String, Long> kitUsed = new HashMap<>();
    }

    public static class Ban {
        public String name;
        public long until;

        public Ban() {}

        public Ban(String name, long until) {
            this.name = name;
            this.until = until;
        }
    }

    public static class Data {
        public Map<String, Map<String, Loc>> homes = new HashMap<>();
        public Loc spawn;
        public Map<String, Loc> warps = new TreeMap<>();
        public Map<String, PlayerData> players = new HashMap<>();
        public Map<String, Ban> bans = new HashMap<>();
        public boolean chatDisabled = false;
        public boolean unstableOn = true;
    }

    private static Data data = new Data();
    private static volatile boolean dirty;

    private Store() {}

    private static Path file() {
        return Config.dir().resolve("data.json");
    }

    public static void load() {
        try {
            Files.createDirectories(Config.dir());
            if (Files.exists(file())) {
                Data d = GSON.fromJson(Files.readString(file()), Data.class);
                if (d != null) {
                    if (d.homes == null) d.homes = new HashMap<>();
                    if (d.warps == null) d.warps = new TreeMap<>();
                    if (d.players == null) d.players = new HashMap<>();
                    if (d.bans == null) d.bans = new HashMap<>();
                    data = d;
                }
            }
        } catch (Exception e) {
            CoreSmp.LOG.error("Could not load data.json", e);
        }
    }

    public static Map<String, Loc> homes(UUID id) {
        return data.homes.computeIfAbsent(id.toString(), k -> new TreeMap<>());
    }

    public static Map<String, Loc> warps() {
        return data.warps;
    }

    public static Loc spawn() {
        return data.spawn;
    }

    public static void setSpawn(Loc l) {
        data.spawn = l;
        dirty = true;
    }

    public static PlayerData pd(UUID id) {
        return data.players.computeIfAbsent(id.toString(), k -> new PlayerData());
    }

    /** Like pd() but never creates an entry. */
    public static PlayerData peek(UUID id) {
        return data.players.get(id.toString());
    }

    public static Map<String, Ban> bans() {
        return data.bans;
    }

    public static boolean chatDisabled() {
        return data.chatDisabled;
    }

    public static void setChatDisabled(boolean v) {
        data.chatDisabled = v;
        dirty = true;
    }

    public static boolean unstableOn() {
        return data.unstableOn;
    }

    public static void setUnstableOn(boolean v) {
        data.unstableOn = v;
        dirty = true;
    }

    public static void markDirty() {
        dirty = true;
    }

    public static void saveAsyncIfDirty() {
        if (!dirty) return;
        dirty = false;
        final String json = GSON.toJson(data); // snapshot on the main thread
        CompletableFuture.runAsync(() -> write(json));
    }

    public static void saveNow() {
        dirty = false;
        write(GSON.toJson(data));
    }

    private static synchronized void write(String json) {
        try {
            Path tmp = file().resolveSibling("data.json.tmp");
            Files.writeString(tmp, json);
            Files.move(tmp, file(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            CoreSmp.LOG.error("Could not save data.json", e);
        }
    }
}
