package com.zenkaiorecycle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Runs /vault, clicks the right chest (vault #1 for coal, #2 for gold), shift-clicks every block and
 * every reserve ore of that kind into it, then closes the screen.
 */
final class VaultStep {
    private enum Step { SEND, WAIT_MAIN, CLICK, WAIT_VAULT, DEPOSIT, SETTLE, CLOSE }

    static String failure = "";

    private static Step step = Step.SEND;
    private static OreKind kind = OreKind.GOLD;
    private static int ticks, attempts, scan, pass;

    private VaultStep() {
    }

    static void start(OreKind k) {
        kind = k;
        failure = "";
        attempts = 0;
        pass = 0;
        go(Step.SEND);
    }

    private static void go(Step s) {
        step = s;
        ticks = 0;
    }

    private static Status fail(String why) {
        failure = why;
        return Status.FAILED;
    }

    private static boolean titleHas(Minecraft mc, String text) {
        return mc.screen != null && mc.screen.getTitle().getString().toLowerCase(Locale.ROOT).contains(text);
    }

    static Status tick(Minecraft mc) {
        ticks++;
        LocalPlayer p = mc.player;
        return switch (step) {
            case SEND -> send(mc, p);
            case WAIT_MAIN -> waitMain(mc);
            case CLICK -> click(mc, p);
            case WAIT_VAULT -> waitVault(mc, p);
            case DEPOSIT -> deposit(mc, p);
            case SETTLE -> settle(p);
            case CLOSE -> close(p);
        };
    }

    private static Status send(Minecraft mc, LocalPlayer p) {
        if (ticks == 1 && mc.screen != null) {
            p.closeContainer();
        }
        if (ticks >= 4) {
            Inv.command(mc, Config.vaultCommand);
            go(Step.WAIT_MAIN);
        }
        return Status.RUNNING;
    }

    private static Status waitMain(Minecraft mc) {
        if (titleHas(mc, "personal vaults") && ticks >= 3) {
            go(Step.CLICK);
        } else if (ticks > 60) {
            if (++attempts >= 3) {
                return fail("/" + Config.vaultCommand + " did not open the Personal Vaults menu. "
                        + "Check vault_command in config/zenkaiorecycle.properties.");
            }
            go(Step.SEND);
        }
        return Status.RUNNING;
    }

    private static Status click(Minecraft mc, LocalPlayer p) {
        AbstractContainerMenu menu = p.containerMenu;
        if (menu.slots.size() <= kind.vaultSlot) {
            return fail("the Personal Vaults menu looks different than expected.");
        }
        Inv.click(mc, menu, kind.vaultSlot, 0, ClickType.PICKUP);
        go(Step.WAIT_VAULT);
        return Status.RUNNING;
    }

    private static Status waitVault(Minecraft mc, LocalPlayer p) {
        if (titleHas(mc, kind.vaultTitle) && ticks >= 3) {
            scan = p.containerMenu.slots.size() - 36;
            go(Step.DEPOSIT);
        } else if (ticks > 60) {
            return fail("the " + kind.vaultTitle + " screen did not open.");
        }
        return Status.RUNNING;
    }

    /** Menu slots: the vault first, then the 27 main inventory slots, then the 9 hotbar slots. */
    private static Status deposit(Minecraft mc, LocalPlayer p) {
        AbstractContainerMenu menu = p.containerMenu;
        int total = menu.slots.size();
        int clicks = 0;
        while (scan < total && clicks < 6) {
            int i = scan++;
            if (i == total - 9) {
                continue; // hotbar slot 0 (the pickaxe) is never moved
            }
            ItemStack s = menu.getSlot(i).getItem();
            if (s.is(kind.ore) || s.is(kind.block)) {
                Inv.click(mc, menu, i, 0, ClickType.QUICK_MOVE);
                clicks++;
            }
        }
        if (scan >= total) {
            go(Step.SETTLE);
        }
        return Status.RUNNING;
    }

    private static Status settle(LocalPlayer p) {
        if (ticks < 8) {
            return Status.RUNNING;
        }
        int left = p.getInventory().countItem(kind.ore) + p.getInventory().countItem(kind.block);
        if (left == 0) {
            go(Step.CLOSE);
        } else if (pass < 2) {
            pass++;
            scan = p.containerMenu.slots.size() - 36;
            go(Step.DEPOSIT);
        } else {
            return fail(kind.vaultTitle + " is full, so " + left + " items could not be stored.");
        }
        return Status.RUNNING;
    }

    private static Status close(LocalPlayer p) {
        if (ticks == 1) {
            p.closeContainer();
        }
        return ticks >= 6 ? Status.DONE : Status.RUNNING;
    }
}
