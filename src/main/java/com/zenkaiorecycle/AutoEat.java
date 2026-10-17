package com.zenkaiorecycle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Eats whatever is in the OFF HAND (your senzu beans) when hunger drops to 3 bars (6 food points) or lower.
 * It uses the item directly instead of clicking the world, so a bean can never be placed as a block, and it holds
 * the use key only while the bean is being eaten.
 */
final class AutoEat {
    /** Three hunger bars = 6 of 20 food points. */
    static final int EAT_AT_OR_BELOW = 6;

    private static boolean eating;
    private static boolean warned;
    private static int cooldown;

    private AutoEat() {
    }

    static void tick(Minecraft mc, LocalPlayer p) {
        if (eating) {
            if (p.isUsingItem() && p.getUsedItemHand() == InteractionHand.OFF_HAND) {
                mc.options.keyUse.setDown(true); // keep eating until it finishes
                return;
            }
            mc.options.keyUse.setDown(false); // finished (or interrupted): let go before the next tick starts another use
            eating = false;
            cooldown = 20;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (p.getFoodData().getFoodLevel() > EAT_AT_OR_BELOW || p.isUsingItem() || mc.gameMode == null) {
            return;
        }
        if (p.getOffhandItem().isEmpty()) {
            if (!warned) {
                warned = true;
                Inv.tell(mc, "Auto-eat: you are hungry but your off hand is empty.");
            }
            cooldown = 100;
            return;
        }
        warned = false;
        mc.gameMode.useItem(p, InteractionHand.OFF_HAND);
        if (p.isUsingItem()) {
            eating = true;
            mc.options.keyUse.setDown(true);
        } else {
            cooldown = 100; // nothing happened (not edible right now), try again in 5 seconds
        }
    }

    static void abort(Minecraft mc) {
        if (eating) {
            mc.options.keyUse.setDown(false);
            eating = false;
        }
    }
}
