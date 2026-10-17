package com.zenkaiorecycle;

import net.minecraftforge.fml.common.Mod;

/**
 * Empty on purpose: everything lives in {@link CycleController}, which is only registered
 * on the client (Dist.CLIENT), so this mod cannot touch a dedicated server.
 */
@Mod(ZenkaiOreCycle.MOD_ID)
public class ZenkaiOreCycle {
    public static final String MOD_ID = "zenkaiorecycle";

    public ZenkaiOreCycle() {
    }
}
