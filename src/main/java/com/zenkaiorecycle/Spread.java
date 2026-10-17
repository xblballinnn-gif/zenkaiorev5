package com.zenkaiorecycle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;

/**
 * Spreads the ore being mined over every empty slot, one item each, so no empty slot is left for cobblestone,
 * iron or anything else to be picked up into. It takes from the biggest stack, drops one item into each empty
 * slot, then puts the rest back. One inventory click per tick, only while no menu is open.
 */
final class Spread {
    private enum Step { IDLE, PICKUP, PLACE, RETURN }

    private static Step step = Step.IDLE;
    private static int source, returnTries;
    private static final ArrayDeque<Integer> targets = new ArrayDeque<>();

    private Spread() {
    }

    static boolean busy() {
        return step != Step.IDLE;
    }

    /** Starts a spread if there are empty slots and a stack big enough to share. */
    static void plan(LocalPlayer p, Item ore) {
        if (busy()) {
            return;
        }
        ArrayDeque<Integer> empties = new ArrayDeque<>();
        int best = -1;
        int bestCount = 1;
        for (int i = Inv.FIRST_FREE_INDEX; i <= 35; i++) {
            ItemStack s = p.getInventory().items.get(i);
            if (s.isEmpty()) {
                empties.add(i);
            } else if (s.is(ore) && s.getCount() > bestCount) {
                best = i;
                bestCount = s.getCount();
            }
        }
        if (empties.isEmpty() || best < 0) {
            return;
        }
        int k = Math.min(empties.size(), bestCount - 1);
        targets.clear();
        for (int n = 0; n < k; n++) {
            targets.add(empties.poll());
        }
        source = best;
        returnTries = 0;
        step = Step.PICKUP;
    }

    /** Does the next click of a running spread. */
    static void step(Minecraft mc, LocalPlayer p, Item ore) {
        switch (step) {
            case PICKUP -> {
                ItemStack s = p.getInventory().items.get(source);
                if (!s.is(ore) || s.getCount() < 2) {
                    step = Step.IDLE; // changed under us, nothing has been picked up yet
                    return;
                }
                click(mc, p, source, 0);
                step = Step.PLACE;
            }
            case PLACE -> {
                Integer t = targets.poll();
                if (t == null || p.inventoryMenu.getCarried().getCount() <= 1) {
                    step = Step.RETURN;
                    return;
                }
                // right-click on an empty slot puts exactly one item there. Never click a slot that got filled meanwhile.
                if (p.getInventory().items.get(t).isEmpty()) {
                    click(mc, p, t, 1);
                }
            }
            case RETURN -> {
                ItemStack carried = p.inventoryMenu.getCarried();
                if (carried.isEmpty()) {
                    step = Step.IDLE;
                    return;
                }
                int dest = returnSlot(p, carried.getItem());
                if (dest >= 0) {
                    click(mc, p, dest, 0);
                }
                if (p.inventoryMenu.getCarried().isEmpty() || ++returnTries > 20) {
                    step = Step.IDLE;
                }
            }
            default -> {
            }
        }
    }

    /** Called when a menu opens or the loop stops mid-spread: puts whatever is on the cursor back. */
    static void abort(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (step == Step.IDLE || p == null || mc.gameMode == null || p.containerMenu != p.inventoryMenu) {
            step = Step.IDLE;
            return;
        }
        for (int tries = 0; tries < 3 && !p.inventoryMenu.getCarried().isEmpty(); tries++) {
            int dest = returnSlot(p, p.inventoryMenu.getCarried().getItem());
            if (dest >= 0) {
                click(mc, p, dest, 0);
            }
        }
        step = Step.IDLE;
    }

    /** Where to put the cursor stack back: its own slot if still empty, else any empty slot, else a stack of the same item. */
    private static int returnSlot(LocalPlayer p, Item item) {
        if (p.getInventory().items.get(source).isEmpty()) {
            return source;
        }
        for (int i = Inv.FIRST_FREE_INDEX; i <= 35; i++) {
            if (p.getInventory().items.get(i).isEmpty()) {
                return i;
            }
        }
        for (int i = Inv.FIRST_FREE_INDEX; i <= 35; i++) {
            if (p.getInventory().items.get(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    private static void click(Minecraft mc, LocalPlayer p, int index, int button) {
        mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, Inv.menuSlot(index), button, ClickType.PICKUP, p);
    }
}
