package com.zenkaiorecycle;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Key handling. = starts/stops the gold-coal loop (see {@link CycleRunner}), X stops it and sends Baritone's #stop,
 * C drops everything in slots 2-36 except raw gold, coal and their blocks. Keys are real key bindings, so they do not fire while chat or a menu is open.
 */
public final class CycleController {
    // Defaults avoid K / J / O so they do not clash with the Auto Coal mod.
    static final KeyMapping START_KEY = new KeyMapping(
            "key.zenkaiorecycle.loop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_EQUAL, "key.categories.zenkaiorecycle");
    static final KeyMapping STOP_KEY = new KeyMapping(
            "key.zenkaiorecycle.stop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.zenkaiorecycle");
    static final KeyMapping CLEAN_KEY = new KeyMapping(
            "key.zenkaiorecycle.clean", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.zenkaiorecycle");

    static {
        Config.load();
    }

    private CycleController() {
    }

    @Mod.EventBusSubscriber(modid = ZenkaiOreCycle.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(START_KEY);
            event.register(STOP_KEY);
            event.register(CLEAN_KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = ZenkaiOreCycle.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ForgeBusEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                tick(Minecraft.getInstance());
            }
        }
    }

    private static void tick(Minecraft mc) {
        CycleRunner.tick(mc);
        if (mc.player == null || mc.level == null) {
            return;
        }
        while (START_KEY.consumeClick()) {
            CycleRunner.toggle(mc);
        }
        while (STOP_KEY.consumeClick()) {
            CycleRunner.stop(mc, "Stopped.");
        }
        while (CLEAN_KEY.consumeClick()) {
            cleanInventory(mc);
        }
    }

    private static void cleanInventory(Minecraft mc) {
        if (mc.player == null || mc.screen != null) {
            return;
        }
        int dropped = Inv.dropUnwanted(mc);
        Inv.tell(mc, "Cleanup: dropped " + dropped + " stack(s). Only raw gold, coal and their blocks are kept; "
                + "your pickaxe slot is untouched.");
    }
}
