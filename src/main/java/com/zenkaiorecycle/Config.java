package com.zenkaiorecycle;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Settings you can change without rebuilding: config/zenkaiorecycle.properties (created on first launch). */
final class Config {
    static String vaultCommand = "vault";
    static String craftCommand = "craft";
    static int spareOre = 45;
    static String mineGold = "#mine gold_ore";
    static String mineCoal = "#mine coal_ore";
    static String stopCommand = "#stop";
    /** No new ore for this long: go home (the /home command below). */
    static int stallSeconds = 60;
    /** Teleport used when stuck, in danger, and after a reconnect (to leave the lobby). */
    static String homeCommand = "home mine";
    /** Seconds to wait after sending it, for the teleport to finish. */
    static int homeWaitSeconds = 8;
    /** Also go home when health is this low (half hearts, 8 = 4 hearts), or in lava / on fire. */
    static boolean dangerHome = true;
    static int dangerHealth = 8;
    /** Pickaxe below this durability: mine coal (mending) until it is completely repaired, then gold again. */
    static int pickMinDurability = 150;
    /** True: only gold, except while the pickaxe is being repaired on coal. False: alternate gold and coal every round. */
    static boolean goldHeavy = true;
    /** Where the panel sits: left_middle, top_left, top_right or right_middle. */
    static String hudPosition = "left_middle";
    static double hudScale = 0.85;
    static boolean reconnect = true;
    static int reconnectDelaySeconds = 15;
    static int reconnectMaxTries = 40;
    /** Only used for the money estimate on the HUD. Overridden by the Market Seller's last prices when that mod's config exists. */
    static long goldBlockPrice = 900000;
    static long coalBlockPrice = 160000;

    private Config() {
    }

    static void load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("zenkaiorecycle.properties");
        Properties p = read(path);
        vaultCommand = strip(p.getProperty("vault_command", vaultCommand));
        craftCommand = strip(p.getProperty("craft_command", craftCommand));
        mineGold = p.getProperty("mine_gold_command", mineGold).trim();
        mineCoal = p.getProperty("mine_coal_command", mineCoal).trim();
        // An older version saved commands that also mined deepslate ore. Replace those old defaults.
        if (mineGold.equals("#mine gold_ore deepslate_gold_ore")) {
            mineGold = "#mine gold_ore";
        }
        if (mineCoal.equals("#mine coal_ore deepslate_coal_ore")) {
            mineCoal = "#mine coal_ore";
        }
        stopCommand = p.getProperty("stop_command", stopCommand).trim();
        spareOre = num(p, "spare_ore", spareOre);
        stallSeconds = num(p, "stall_seconds", stallSeconds);
        homeCommand = strip(p.getProperty("home_command", homeCommand));
        homeWaitSeconds = num(p, "home_wait_seconds", homeWaitSeconds);
        dangerHome = Boolean.parseBoolean(p.getProperty("danger_home", Boolean.toString(dangerHome)).trim());
        dangerHealth = num(p, "danger_health", dangerHealth);
        pickMinDurability = num(p, "pickaxe_min_durability", pickMinDurability);
        if (pickMinDurability == 300) {
            pickMinDurability = 150; // the default of an earlier version
        }
        goldHeavy = Boolean.parseBoolean(p.getProperty("gold_heavy", Boolean.toString(goldHeavy)).trim());
        hudPosition = p.getProperty("hud_position", hudPosition).trim().toLowerCase(java.util.Locale.ROOT);
        try {
            hudScale = Math.max(0.5, Math.min(2.0, Double.parseDouble(p.getProperty("hud_scale", Double.toString(hudScale)).trim())));
        } catch (NumberFormatException ignored) {
            // keep default
        }
        reconnect = Boolean.parseBoolean(p.getProperty("reconnect", Boolean.toString(reconnect)).trim());
        reconnectDelaySeconds = num(p, "reconnect_delay_seconds", reconnectDelaySeconds);
        reconnectMaxTries = num(p, "reconnect_max_tries", reconnectMaxTries);
        goldBlockPrice = num(p, "gold_block_price", (int) goldBlockPrice);
        coalBlockPrice = num(p, "coal_block_price", (int) coalBlockPrice);

        p.clear();
        p.setProperty("vault_command", vaultCommand);
        p.setProperty("craft_command", craftCommand);
        p.setProperty("spare_ore", Integer.toString(spareOre));
        p.setProperty("mine_gold_command", mineGold);
        p.setProperty("mine_coal_command", mineCoal);
        p.setProperty("stop_command", stopCommand);
        p.setProperty("stall_seconds", Integer.toString(stallSeconds));
        p.setProperty("home_command", homeCommand);
        p.setProperty("home_wait_seconds", Integer.toString(homeWaitSeconds));
        p.setProperty("danger_home", Boolean.toString(dangerHome));
        p.setProperty("danger_health", Integer.toString(dangerHealth));
        p.setProperty("pickaxe_min_durability", Integer.toString(pickMinDurability));
        p.setProperty("gold_heavy", Boolean.toString(goldHeavy));
        p.setProperty("hud_position", hudPosition);
        p.setProperty("hud_scale", Double.toString(hudScale));
        p.setProperty("reconnect", Boolean.toString(reconnect));
        p.setProperty("reconnect_delay_seconds", Integer.toString(reconnectDelaySeconds));
        p.setProperty("reconnect_max_tries", Integer.toString(reconnectMaxTries));
        p.setProperty("gold_block_price", Long.toString(goldBlockPrice));
        p.setProperty("coal_block_price", Long.toString(coalBlockPrice));
        try (OutputStream out = Files.newOutputStream(path)) {
            p.store(out, "Zenkai Ore Cycle. spare_ore = ore kept back and stored with the blocks each cycle. "
                    + "gold_block_price / coal_block_price only feed the money estimate.");
        } catch (IOException ignored) {
            // not worth failing over
        }
        refreshPrices();
    }

    /** Takes the prices last used in the Market Seller menu, so the money estimate matches what you actually list for. */
    static void refreshPrices() {
        Properties m = read(FMLPaths.CONFIGDIR.get().resolve("zenkaimarket.properties"));
        goldBlockPrice = longOf(m.getProperty("gold_price"), goldBlockPrice);
        coalBlockPrice = longOf(m.getProperty("coal_price"), coalBlockPrice);
    }

    private static Properties read(Path path) {
        Properties p = new Properties();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                p.load(in);
            } catch (IOException ignored) {
                // use defaults
            }
        }
        return p;
    }

    private static String strip(String s) {
        String t = s.trim();
        return t.startsWith("/") ? t.substring(1) : t;
    }

    private static long longOf(String s, long fallback) {
        try {
            return s == null ? fallback : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int num(Properties p, String key, int fallback) {
        try {
            return Integer.parseInt(p.getProperty(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
