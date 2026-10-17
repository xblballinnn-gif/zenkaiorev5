package com.zenkaiorecycle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Optional;

/**
 * Runs /craft, which opens a crafting screen, then turns ore into blocks until only the reserve is left.
 * Everything is moved with normal inventory clicks, nothing is placed in the world.
 */
final class CraftStep {
    private enum Step { NOTHING, SEND, WAIT_OPEN, PLACE_RECIPE, WAIT_GRID, TAKE, STOW, CLOSE }

    static String failure = "";

    private static Step step = Step.NOTHING;
    private static OreKind kind = OreKind.GOLD;
    private static int ticks, attempts, remaining, takeLeft, stall;
    private static boolean quick;

    private CraftStep() {
    }

    static void start(Minecraft mc, OreKind k) {
        kind = k;
        failure = "";
        attempts = 0;
        int ore = mc.player.getInventory().countItem(k.ore);
        remaining = Math.max(0, (ore - Config.spareOre) / 9);
        go(remaining == 0 ? Step.NOTHING : Step.SEND);
    }

    private static void go(Step s) {
        step = s;
        ticks = 0;
        stall = 0;
    }

    private static Status fail(String why) {
        failure = why;
        return Status.FAILED;
    }

    static Status tick(Minecraft mc) {
        ticks++;
        LocalPlayer p = mc.player;
        return switch (step) {
            case NOTHING -> Status.DONE;
            case SEND -> send(mc, p);
            case WAIT_OPEN -> waitOpen(mc, p);
            case PLACE_RECIPE -> placeRecipe(mc, p);
            case WAIT_GRID -> waitGrid(p);
            case TAKE -> take(mc, p);
            case STOW -> stow(mc, p);
            case CLOSE -> close(p);
        };
    }

    private static Status send(Minecraft mc, LocalPlayer p) {
        if (ticks == 1 && mc.screen != null) {
            p.closeContainer();
        }
        if (ticks >= 4) {
            Inv.command(mc, Config.craftCommand);
            go(Step.WAIT_OPEN);
        }
        return Status.RUNNING;
    }

    private static Status waitOpen(Minecraft mc, LocalPlayer p) {
        if (p.containerMenu instanceof CraftingMenu && ticks >= 3) {
            go(Step.PLACE_RECIPE);
        } else if (ticks > 60) {
            if (++attempts >= 3) {
                String title = mc.screen == null ? "nothing" : "\"" + mc.screen.getTitle().getString() + "\"";
                return fail("/" + Config.craftCommand + " did not open a normal crafting table (it opened " + title
                        + "). Change craft_command in config/zenkaiorecycle.properties.");
            }
            go(Step.SEND);
        }
        return Status.RUNNING;
    }

    private static Status placeRecipe(Minecraft mc, LocalPlayer p) {
        if (remaining <= 0) {
            go(Step.CLOSE);
            return Status.RUNNING;
        }
        if (!(p.containerMenu instanceof CraftingMenu menu)) {
            return fail("the crafting screen closed unexpectedly.");
        }
        Optional<RecipeHolder<?>> r = mc.getConnection().getRecipeManager().byKey(kind.recipe);
        if (r.isEmpty()) {
            return fail("recipe " + kind.recipe + " was not found.");
        }
        mc.gameMode.handlePlaceRecipe(menu.containerId, r.get(), true);
        go(Step.WAIT_GRID);
        return Status.RUNNING;
    }

    private static Status waitGrid(LocalPlayer p) {
        if (!(p.containerMenu instanceof CraftingMenu menu)) {
            return fail("the crafting screen closed unexpectedly.");
        }
        int sets = menu.getSlot(1).getItem().getCount();
        if (sets > 0 && ticks >= 3) {
            takeLeft = Math.min(sets, remaining);
            quick = takeLeft == sets;
            go(Step.TAKE);
        } else if (ticks > 25) {
            return fail("could not fill the crafting grid. Craft one " + kind.label
                    + " block by hand once so the recipe unlocks, then start again.");
        }
        return Status.RUNNING;
    }

    private static Status take(Minecraft mc, LocalPlayer p) {
        if (!(p.containerMenu instanceof CraftingMenu menu)) {
            return fail("the crafting screen closed unexpectedly.");
        }
        if (quick) {
            // every set in the grid is wanted: one shift-click crafts them all
            if (ticks == 1) {
                Inv.click(mc, menu, 0, 0, ClickType.QUICK_MOVE);
                remaining -= takeLeft;
            }
            if (ticks >= 6) {
                go(Step.PLACE_RECIPE);
            }
            return Status.RUNNING;
        }
        if (takeLeft > 0) {
            // fewer sets wanted than are in the grid: craft one at a time so the reserve stays untouched
            if (ticks % 2 == 0) {
                if (menu.getSlot(0).hasItem()) {
                    Inv.click(mc, menu, 0, 0, ClickType.PICKUP);
                    takeLeft--;
                    remaining--;
                    stall = 0;
                } else if (++stall > 20) {
                    return fail("the crafting result did not appear.");
                }
            }
        } else if (ticks >= 3) {
            go(Step.STOW);
        }
        return Status.RUNNING;
    }

    private static Status stow(Minecraft mc, LocalPlayer p) {
        if (!(p.containerMenu instanceof CraftingMenu menu)) {
            return fail("the crafting screen closed unexpectedly.");
        }
        ItemStack cur = menu.getCarried();
        if (cur.isEmpty()) {
            go(remaining > 0 ? Step.PLACE_RECIPE : Step.CLOSE);
            return Status.RUNNING;
        }
        if (ticks % 2 == 0) {
            int slot = stowSlot(menu, cur);
            if (slot < 0) {
                return fail("no room in your inventory for the crafted blocks.");
            }
            Inv.click(mc, menu, slot, 0, ClickType.PICKUP);
        }
        return ticks > 60 ? fail("could not put the crafted blocks away.") : Status.RUNNING;
    }

    /** CraftingMenu slots: 0 result, 1-9 grid, 10-36 main inventory, 37-45 hotbar (37 is the protected pickaxe slot). */
    private static int stowSlot(CraftingMenu menu, ItemStack cur) {
        for (int i = 10; i <= 45; i++) {
            if (i == 37) {
                continue;
            }
            ItemStack s = menu.getSlot(i).getItem();
            if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, cur) && s.getCount() < s.getMaxStackSize()) {
                return i;
            }
        }
        for (int i = 10; i <= 45; i++) {
            if (i != 37 && menu.getSlot(i).getItem().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private static Status close(LocalPlayer p) {
        if (ticks == 1) {
            p.closeContainer();
        }
        return ticks >= 6 ? Status.DONE : Status.RUNNING;
    }
}
