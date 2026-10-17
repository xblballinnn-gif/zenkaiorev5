package com.zenkaiorecycle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import java.util.Set;

/** Small helpers shared by the loop. Hotbar slot 0 (the pickaxe) is never touched. */
final class Inv {
    /** First inventory index (0-8 hotbar, 9-35 main) that the loop may fill or empty. */
    static final int FIRST_FREE_INDEX = 1;

    /** The only things allowed in slots 1-35. Everything else is dropped. */
    private static final Set<Item> KEEP = Set.of(
            Items.RAW_GOLD, Items.COAL, Items.RAW_GOLD_BLOCK, Items.COAL_BLOCK, Items.NETHERITE_PICKAXE);

    private Inv() {
    }

    /** Shulker boxes (any colour) are never dropped. */
    static boolean isShulkerBox(ItemStack s) {
        return s.getItem() instanceof BlockItem b && b.getBlock() instanceof ShulkerBoxBlock;
    }

    static int emptySlots(LocalPlayer p) {
        int n = 0;
        for (int i = FIRST_FREE_INDEX; i <= 35; i++) {
            if (p.getInventory().items.get(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** True when no empty slot is left and every stack of this ore is full, so nothing more can be picked up. */
    static boolean fullOfOre(LocalPlayer p, Item ore) {
        if (emptySlots(p) > 0) {
            return false;
        }
        for (int i = FIRST_FREE_INDEX; i <= 35; i++) {
            ItemStack s = p.getInventory().items.get(i);
            if (s.is(ore) && s.getCount() < s.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    /** Slot id in the player inventory menu for an inventory index (hotbar is 36-44 there). */
    static int menuSlot(int index) {
        return index < 9 ? 36 + index : index;
    }

    /**
     * Drops every stack in slots 1-35 (the ore area) that is not raw gold, coal or their blocks.
     * Hotbar slot 0 (the pickaxe) is never touched. Returns how many stacks were dropped.
     */
    static int dropUnwanted(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || mc.screen != null) {
            return 0;
        }
        int dropped = 0;
        for (int i = FIRST_FREE_INDEX; i <= 35; i++) {
            ItemStack s = p.getInventory().items.get(i);
            if (!s.isEmpty() && !KEEP.contains(s.getItem()) && !isShulkerBox(s)) {
                // button 1 with THROW drops the whole stack
                mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, menuSlot(i), 1, ClickType.THROW, p);
                dropped++;
            }
        }
        return dropped;
    }

    /** Durability left on the pickaxe in hotbar slot 1, or -1 when that slot holds nothing that can wear out. */
    static int pickaxeLeft(LocalPlayer p) {
        ItemStack s = p.getInventory().items.get(0);
        return s.isDamageableItem() ? s.getMaxDamage() - s.getDamageValue() : -1;
    }

    static int pickaxeMax(LocalPlayer p) {
        return p.getInventory().items.get(0).getMaxDamage();
    }

    /** True when the pickaxe in hotbar slot 1 is fully repaired (max durability). */
    static boolean pickaxeFull(LocalPlayer p) {
        ItemStack s = p.getInventory().items.get(0);
        return s.isDamageableItem() && s.getDamageValue() == 0;
    }

    static void click(Minecraft mc, AbstractContainerMenu menu, int slot, int button, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, mc.player);
    }

    static void aimAt(LocalPlayer p, Vec3 point) {
        Vec3 eye = p.getEyePosition();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float pitch = (float) (-(Mth.atan2(dy, horizontal) * 180.0 / Math.PI));
        p.setXRot(pitch);
        p.xRotO = pitch;
        if (horizontal > 0.01) {
            float yaw = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
            p.setYRot(yaw);
            p.yRotO = yaw;
        }
    }

    /** Sends a chat line (Baritone picks up lines that start with #). */
    static void chat(Minecraft mc, String line) {
        if (mc.player != null) {
            mc.player.connection.sendChat(line);
        }
    }

    /** Runs a server command, given without the leading slash. */
    static void command(Minecraft mc, String name) {
        if (mc.player != null) {
            mc.player.connection.sendCommand(name);
        }
    }

    static void tell(Minecraft mc, String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[Zenkai Ore Cycle] " + message), false);
        }
    }

    /** Best-effort check so "#mine ..." is never posted to public chat when Baritone is missing. */
    static boolean baritoneLooksInstalled() {
        if (ModList.get().isLoaded("baritone")) {
            return true;
        }
        try {
            Class.forName("baritone.api.BaritoneAPI", false, Inv.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
