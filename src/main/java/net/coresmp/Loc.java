package net.coresmp;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class Loc {
    public String dim;
    public double x, y, z;
    public float yaw, pitch;

    public Loc() {}

    public Loc(String dim, double x, double y, double z, float yaw, float pitch) {
        this.dim = dim;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static String dimKey(ServerLevel level) {
        return level.dimension().toString();
    }

    public static Loc of(ServerPlayer p) {
        ServerLevel lvl = (ServerLevel) p.level();
        return new Loc(dimKey(lvl), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
    }

    public ServerLevel level(MinecraftServer server) {
        for (ServerLevel l : server.getAllLevels()) {
            if (dimKey(l).equals(dim)) return l;
        }
        return null;
    }
}
