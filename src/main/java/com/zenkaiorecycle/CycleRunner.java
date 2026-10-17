package com.zenkaiorecycle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;

/**
 * The loop: mine with Baritone until the inventory is full of ore, #stop, /craft blocks (keeping the reserve),
 * /vault everything into the right vault, switch ore, repeat. Starts with gold.
 */
final class CycleRunner {
    private enum State { IDLE, MINING, STOPPING, CRAFTING, DEPOSITING, CLEANUP, HOME, RECONNECTING }

    private static State state = State.IDLE;
    private static OreKind kind = OreKind.GOLD;
    private static int ticks, junkTimer, spreadTimer, lastOre, noProgress;
    private static int dangerCooldown, pickTimer;
    /** True once the pickaxe dropped below the minimum: mine coal (mending) until it is repaired. */
    private static boolean lowPick;
    private static int cycles, homes, reconnects;
    private static long startedAt;
    private static ServerData lastServer;
    private static int reconnectTimer, reconnectTries, joinWait;
    /** After a reconnect the loop only mines once /home has really moved the player away from where they joined. */
    private static boolean verifyHome;
    private static int homeRetries;
    private static double homeFromX, homeFromZ;
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> homeFromDim;

    private CycleRunner() {
    }

    static boolean running() {
        return state != State.IDLE;
    }

    static void toggle(Minecraft mc) {
        if (running()) {
            stop(mc, "Cycle stopped.");
            return;
        }
        if (!Inv.baritoneLooksInstalled()) {
            Inv.tell(mc, "Baritone was not detected, so nothing was sent. Install Baritone first.");
            return;
        }
        kind = OreKind.GOLD;
        cycles = 0;
        homes = 0;
        Stats.startSession();
        Config.refreshPrices();
        reconnects = 0;
        lowPick = false;
        dangerCooldown = 0;
        startedAt = System.currentTimeMillis();
        beginMining(mc);
    }

    /** Ends the loop (if it is running) and always sends Baritone's stop. */
    static void stop(Minecraft mc, String why) {
        state = State.IDLE;
        AutoEat.abort(mc);
        Spread.abort(mc);
        Home.abort();
        if (mc.player != null && mc.screen instanceof AbstractContainerScreen<?>) {
            mc.player.closeContainer();
        }
        if (Inv.baritoneLooksInstalled()) {
            Inv.chat(mc, Config.stopCommand);
        }
        Stats.save();
        Inv.tell(mc, why);
        if (startedAt > 0 && (Stats.sessionGold > 0 || Stats.sessionCoal > 0)) {
            Inv.tell(mc, "Session: " + Stats.sessionGold + " gold ore, " + Stats.sessionCoal + " coal ore, worth about "
                    + Stats.money(Stats.sessionValue()) + " at your block prices.");
        }
    }

    private static void beginMining(Minecraft mc) {
        state = State.MINING;
        ticks = 0;
        junkTimer = 0;
        spreadTimer = 0;
        noProgress = 0;
        lastOre = mc.player.getInventory().countItem(kind.ore);
        Inv.chat(mc, kind.mineCommand());
        Inv.tell(mc, "Mining " + kind.label + ".");
    }

    static void tick(Minecraft mc) {
        if (state == State.IDLE) {
            return;
        }
        if (state == State.RECONNECTING) {
            reconnecting(mc);
            return;
        }
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            if (Config.reconnect && lastServer != null && mc.screen instanceof DisconnectedScreen) {
                state = State.RECONNECTING; // kicked or timed out: join again
                reconnectTimer = 20 * 5;
                reconnectTries = 0;
                joinWait = 0;
            } else {
                state = State.IDLE; // left the world on purpose
            }
            return;
        }
        ServerData server = mc.getCurrentServer();
        if (server != null) {
            lastServer = server;
        }
        if (dangerCooldown > 0) {
            dangerCooldown--;
        }
        if (++pickTimer >= 20) {
            pickTimer = 0;
            int left = Inv.pickaxeLeft(p);
            if (left >= 0) {
                if (left < Config.pickMinDurability) {
                    lowPick = true;
                } else if (Inv.pickaxeFull(p)) {
                    lowPick = false;
                }
            }
        }
        if (p.isDeadOrDying()) {
            stop(mc, "Cycle stopped: you died.");
            return;
        }
        if (mc.screen == null && (state == State.MINING || state == State.STOPPING)) {
            AutoEat.tick(mc, p);
        } else {
            AutoEat.abort(mc);
        }
        ticks++;
        switch (state) {
            case MINING -> mining(mc, p);
            case STOPPING -> stopping(mc);
            case CRAFTING -> crafting(mc);
            case DEPOSITING -> depositing(mc, p);
            case CLEANUP -> cleanup(mc, p);
            case HOME -> home(mc);
            default -> { }
        }
    }

    private static void mining(Minecraft mc, LocalPlayer p) {
        if (mc.screen != null) {
            Spread.abort(mc);
            return;
        }
        if (Spread.busy()) {
            Spread.step(mc, p, kind.ore);
            return;
        }
        if (++junkTimer >= 20) {
            junkTimer = 0;
            Inv.dropUnwanted(mc);
        }
        if (++spreadTimer >= 10) {
            spreadTimer = 0;
            Spread.plan(p, kind.ore);
        }
        int ore = p.getInventory().countItem(kind.ore);
        if (ore > lastOre) {
            Stats.add(kind, ore - lastOre);
        }
        noProgress = ore > lastOre ? 0 : noProgress + 1;
        lastOre = ore;
        if (noProgress >= Config.stallSeconds * 20) {
            startHome(mc, "No new " + kind.label + " for " + Config.stallSeconds + " seconds", false);
            return;
        }
        if (Config.dangerHome && dangerCooldown <= 0
                && (p.getHealth() <= Config.dangerHealth || p.isInLava() || p.isOnFire())) {
            startHome(mc, "In danger (low health, lava or fire)", false);
            return;
        }
        if (Config.goldHeavy && kind == OreKind.GOLD && lowPick) {
            // Finish this round early (craft + vault what is held), the next round is coal so mending can repair it.
            Inv.chat(mc, Config.stopCommand);
            Inv.tell(mc, "Pickaxe is below " + Config.pickMinDurability + " durability. Storing what you have, "
                    + "then mining coal until it is fully repaired.");
            state = State.STOPPING;
            ticks = 0;
            return;
        }
        if (Config.goldHeavy && kind == OreKind.COAL && !lowPick) {
            Inv.chat(mc, Config.stopCommand);
            Inv.tell(mc, "Pickaxe is fully repaired. Storing the coal, then back to gold.");
            state = State.STOPPING;
            ticks = 0;
            return;
        }
        if (Inv.fullOfOre(p, kind.ore)) {
            Inv.chat(mc, Config.stopCommand);
            Inv.tell(mc, "Inventory is full of " + kind.label + ". Crafting blocks.");
            state = State.STOPPING;
            ticks = 0;
        }
    }

    private static void stopping(Minecraft mc) {
        if (ticks >= 25) {
            CraftStep.start(mc, kind);
            state = State.CRAFTING;
        }
    }

    private static void crafting(Minecraft mc) {
        Status s = CraftStep.tick(mc);
        if (s == Status.FAILED) {
            stop(mc, "Cycle stopped: " + CraftStep.failure);
        } else if (s == Status.DONE) {
            VaultStep.start(kind);
            state = State.DEPOSITING;
        }
    }

    private static void depositing(Minecraft mc, LocalPlayer p) {
        Status s = VaultStep.tick(mc);
        if (s == Status.FAILED) {
            stop(mc, "Cycle stopped: " + VaultStep.failure);
        } else if (s == Status.DONE) {
            Inv.dropUnwanted(mc); // last purge before the next round, items cannot be picked up again for 2 seconds
            state = State.CLEANUP;
            ticks = 0;
        }
    }

    private static void cleanup(Minecraft mc, LocalPlayer p) {
        if (ticks < 5) {
            return;
        }
        if (Inv.emptySlots(p) < 5) {
            stop(mc, "Cycle stopped: your inventory is still nearly full after storing everything and "
                    + "dropping the unwanted items.");
            return;
        }
        Inv.tell(mc, "Stored " + kind.label + " in " + kind.vaultTitle + ".");
        cycles++;
        Stats.save();
        if (Config.goldHeavy) {
            kind = lowPick ? OreKind.COAL : OreKind.GOLD; // coal only while the pickaxe is being mended
        } else {
            kind = kind.other();
            if (kind == OreKind.GOLD && lowPick) {
                kind = OreKind.COAL;
            }
        }
        beginMining(mc);
    }

    private static void startHome(Minecraft mc, String why, boolean afterReconnect) {
        verifyHome = afterReconnect;
        homeRetries = 0;
        if (mc.player != null && mc.level != null) {
            homeFromX = mc.player.getX();
            homeFromZ = mc.player.getZ();
            homeFromDim = mc.level.dimension();
        }
        Inv.chat(mc, Config.stopCommand);
        Spread.abort(mc);
        Inv.tell(mc, why + ": going home.");
        homes++;
        dangerCooldown = 20 * 60;
        Home.start(mc);
        state = State.HOME;
    }

    private static void home(Minecraft mc) {
        if (Home.tick() == Status.RUNNING) {
            return;
        }
        if (!verifyHome) {
            beginMining(mc); // stuck or in danger: just carry on from wherever we are now
            return;
        }
        // After a reconnect: only mine once the teleport really moved us (new world, or far from where we joined).
        boolean moved = false;
        if (mc.player != null && mc.level != null) {
            double dx = mc.player.getX() - homeFromX;
            double dz = mc.player.getZ() - homeFromZ;
            moved = mc.level.dimension() != homeFromDim || dx * dx + dz * dz > 50.0 * 50.0;
        }
        if (moved) {
            verifyHome = false;
            Inv.tell(mc, "/" + Config.homeCommand + " worked. Mining.");
            beginMining(mc);
        } else if (++homeRetries >= 15) {
            stop(mc, "Cycle stopped: /" + Config.homeCommand + " did not move you out of the lobby after 15 tries. "
                    + "Check home_command in config/zenkaiorecycle.properties (and that the home exists).");
        } else {
            Inv.tell(mc, "/" + Config.homeCommand + " has not moved you yet, trying again.");
            Home.start(mc);
        }
    }

    /** Kicked or disconnected while the loop ran: join the same server again, then carry on mining. */
    private static void reconnecting(Minecraft mc) {
        if (mc.player != null && mc.level != null) {
            if (++joinWait >= 20 * 10) { // give the world a few seconds to load
                joinWait = 0;
                reconnects++;
                Inv.tell(mc, "Reconnected.");
                startHome(mc, "Back on the server, leaving the lobby", true);
            }
            return;
        }
        if (mc.screen instanceof ConnectScreen) {
            return; // a connection attempt is in progress
        }
        if (--reconnectTimer > 0) {
            return;
        }
        if (++reconnectTries > Config.reconnectMaxTries) {
            state = State.IDLE;
            return;
        }
        reconnectTimer = Math.max(1, Config.reconnectDelaySeconds) * 20;
        ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc,
                ServerAddress.parseString(lastServer.ip), lastServer, false);
    }

    // ---- read-only values for the on-screen panel (Hud) ----
    static String hudStatus() {
        return switch (state) {
            case MINING -> "Mining " + kind.label;
            case STOPPING -> "Stopping Baritone";
            case CRAFTING -> "Crafting " + kind.label + " blocks";
            case DEPOSITING -> "Storing in " + kind.vaultTitle;
            case CLEANUP -> "Cleaning up";
            case HOME -> "Going home";
            case RECONNECTING -> "Reconnecting";
            default -> "Idle";
        };
    }

    static OreKind hudKind() {
        return kind;
    }

    static boolean hudMining() {
        return state == State.MINING;
    }

    static boolean hudRepairing() {
        return lowPick;
    }

    static int hudIdleSeconds() {
        return noProgress / 20;
    }

    static int hudRounds() {
        return cycles;
    }

    static int hudHomes() {
        return homes;
    }

    static int hudReconnects() {
        return reconnects;
    }

    static long hudElapsedSeconds() {
        return (System.currentTimeMillis() - startedAt) / 1000;
    }
}
