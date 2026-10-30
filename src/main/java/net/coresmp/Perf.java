package net.coresmp;

/** Lightweight TPS / MSPT tracker (rolling 100 ticks). */
public final class Perf {
    private static final int N = 100;
    private static final long[] work = new long[N];
    private static final long[] gap = new long[N];
    private static int i;
    private static long start, lastEnd;
    private static boolean filled;

    private Perf() {}

    public static void tickStart() {
        start = System.nanoTime();
    }

    public static void tickEnd() {
        long now = System.nanoTime();
        work[i % N] = now - start;
        if (lastEnd != 0) gap[i % N] = now - lastEnd;
        lastEnd = now;
        i++;
        if (i >= N) filled = true;
    }

    private static double avg(long[] a) {
        int n = filled ? N : Math.max(1, i);
        long sum = 0;
        for (int k = 0; k < n; k++) sum += a[k];
        return (double) sum / n;
    }

    public static double mspt() {
        return avg(work) / 1_000_000.0;
    }

    public static double tps() {
        double g = avg(gap);
        if (g <= 0) return 20.0;
        return Math.min(20.0, 1_000_000_000.0 / g);
    }
}
