package com.zenkaiorecycle;

import net.minecraft.client.Minecraft;

/** Sends the home command (default "home mine") and waits for the teleport to finish. */
final class Home {
    private static boolean active;
    private static int ticks;

    private Home() {
    }

    static void start(Minecraft mc) {
        Inv.command(mc, Config.homeCommand);
        Inv.tell(mc, "Sent /" + Config.homeCommand + ".");
        active = true;
        ticks = 0;
    }

    static void abort() {
        active = false;
    }

    /** DONE once the wait after the command is over. */
    static Status tick() {
        if (!active) {
            return Status.DONE;
        }
        if (++ticks >= Math.max(1, Config.homeWaitSeconds) * 20) {
            active = false;
            return Status.DONE;
        }
        return Status.RUNNING;
    }
}
