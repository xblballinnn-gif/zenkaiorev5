package com.zenkaiorecycle;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/** Money tracker: ore mined per session and all time, valued at the block prices (9 ore = 1 block). */
final class Stats {
    static long sessionGold, sessionCoal;
    static long totalGold, totalCoal;
    static long sessionStart;
    private static boolean loaded;

    private Stats() {
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("zenkaiorecycle-stats.properties");
    }

    static void startSession() {
        if (!loaded) {
            loaded = true;
            Properties p = new Properties();
            if (Files.exists(file())) {
                try (InputStream in = Files.newInputStream(file())) {
                    p.load(in);
                } catch (IOException ignored) {
                    // start from zero
                }
            }
            totalGold = parse(p.getProperty("total_gold_ore"));
            totalCoal = parse(p.getProperty("total_coal_ore"));
        }
        sessionGold = 0;
        sessionCoal = 0;
        sessionStart = System.currentTimeMillis();
    }

    static void add(OreKind kind, int amount) {
        if (amount <= 0) {
            return;
        }
        if (kind == OreKind.GOLD) {
            sessionGold += amount;
            totalGold += amount;
        } else {
            sessionCoal += amount;
            totalCoal += amount;
        }
    }

    static void save() {
        Properties p = new Properties();
        p.setProperty("total_gold_ore", Long.toString(totalGold));
        p.setProperty("total_coal_ore", Long.toString(totalCoal));
        try (OutputStream out = Files.newOutputStream(file())) {
            p.store(out, "Zenkai Ore Cycle: all-time ore mined (used for the HUD). Delete the file to reset.");
        } catch (IOException ignored) {
            // not worth failing over
        }
    }

    static long value(long gold, long coal) {
        return gold * Config.goldBlockPrice / 9 + coal * Config.coalBlockPrice / 9;
    }

    static long sessionValue() {
        return value(sessionGold, sessionCoal);
    }

    static long totalValue() {
        return value(totalGold, totalCoal);
    }

    /** Estimated money per hour this session, or -1 in the first minute (too early to say). */
    static long perHour() {
        long secs = (System.currentTimeMillis() - sessionStart) / 1000;
        return secs < 60 ? -1 : sessionValue() * 3600 / secs;
    }

    static String money(long v) {
        return String.format(Locale.US, "%,d", v);
    }

    private static long parse(String s) {
        try {
            return s == null ? 0 : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
