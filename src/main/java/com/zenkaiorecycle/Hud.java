package com.zenkaiorecycle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Status panel. Only drawn while the loop is running (it disappears when you stop it). By default it sits at the left
 * edge in the middle of the screen, where other HUD mods rarely draw; hud_position and hud_scale in the config move/resize it.
 */
@Mod.EventBusSubscriber(modid = ZenkaiOreCycle.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class Hud {
    private static final int PAD = 5;
    private static final int GAP = 10;
    private static final int LINE = 11;
    private static final int BAR_W = 46;

    private static final int GOLD = 0xFFFFC62E;
    private static final int GREEN = 0xFF55E06A;
    private static final int YELLOW = 0xFFF2D13B;
    private static final int RED = 0xFFFF5C5C;
    private static final int AQUA = 0xFF5CD6FF;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFF9AA0AA;

    /** One line: a label on the left, a value on the right (optionally with a bar). label == null is a divider. */
    private record Row(String label, String value, int color, float bar) {
        static Row line(String label, String value, int color) {
            return new Row(label, value, color, -1f);
        }

        static Row divider() {
            return new Row(null, "", 0, -1f);
        }
    }

    private Hud() {
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!CycleRunner.running() || mc.player == null || mc.options.hideGui) {
            return;
        }
        LocalPlayer p = mc.player;
        Font font = mc.font;

        OreKind kind = CycleRunner.hudKind();
        long secs = CycleRunner.hudElapsedSeconds();
        String time = String.format("%d:%02d:%02d", secs / 3600, secs / 60 % 60, secs % 60);
        int kindColor = kind == OreKind.GOLD ? GOLD : 0xFFC4C8D0;

        List<Row> rows = new ArrayList<>();
        rows.add(Row.line("Ore", Stats.money(p.getInventory().countItem(kind.ore)) + "  (" + Inv.emptySlots(p) + " free)", WHITE));
        if (CycleRunner.hudMining()) {
            int idle = CycleRunner.hudIdleSeconds();
            float frac = Config.stallSeconds <= 0 ? 0f : idle / (float) Config.stallSeconds;
            rows.add(Row.line("Idle", idle + "s / " + Config.stallSeconds + "s", frac > 0.75f ? RED : frac > 0.4f ? YELLOW : GREEN));
        }
        int left = Inv.pickaxeLeft(p);
        if (left >= 0) {
            int max = Math.max(1, Inv.pickaxeMax(p));
            float frac = Math.min(1f, left / (float) max);
            String text = left + (CycleRunner.hudRepairing() ? " (mending)" : "");
            rows.add(new Row("Pickaxe", text, frac > 0.5f ? GREEN : frac > 0.2f ? YELLOW : RED, frac));
        } else {
            rows.add(Row.line("Pickaxe", "none in slot 1", RED));
        }
        int food = p.getFoodData().getFoodLevel();
        rows.add(Row.line("Hunger", food + " / 20", food <= 6 ? RED : food <= 12 ? YELLOW : GREEN));
        rows.add(Row.line("Rounds", CycleRunner.hudRounds() + "   Home " + CycleRunner.hudHomes()
                + "   Rejoin " + CycleRunner.hudReconnects(), GREY));
        rows.add(Row.divider());
        rows.add(Row.line("Mined", Stats.money(Stats.sessionGold) + " gold  " + Stats.money(Stats.sessionCoal) + " coal", WHITE));
        rows.add(Row.line("Worth", Stats.money(Stats.sessionValue()), GOLD));
        long perHour = Stats.perHour();
        rows.add(Row.line("Per hour", perHour < 0 ? "..." : Stats.money(perHour), GREEN));
        rows.add(Row.line("All time", Stats.money(Stats.totalValue()), AQUA));

        // --- measure ---
        String title = "ZENKAI ORE CYCLE";
        String status = CycleRunner.hudStatus();
        int labelW = 0;
        int valueW = 0;
        for (Row r : rows) {
            if (r.label() == null) {
                continue;
            }
            labelW = Math.max(labelW, font.width(r.label()));
            valueW = Math.max(valueW, font.width(r.value()) + (r.bar() >= 0 ? BAR_W + 4 : 0));
        }
        int width = Math.max(PAD * 2 + labelW + GAP + valueW,
                PAD * 2 + font.width(title) + GAP + font.width(time));
        width = Math.max(width, PAD * 2 + font.width(status) + 12);
        int height = PAD + 12 + 13 + 4;
        for (Row r : rows) {
            height += r.label() == null ? 6 : LINE;
        }
        height += PAD - 2;

        // --- place ---
        float s = (float) Config.hudScale;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        float pw = width * s;
        float ph = height * s;
        float px;
        float py;
        switch (Config.hudPosition) {
            case "top_left" -> {
                px = 4;
                py = 4;
            }
            case "top_right" -> {
                px = screenW - pw - 4;
                py = 4;
            }
            case "right_middle" -> {
                px = screenW - pw - 4;
                py = (screenH - ph) / 2f;
            }
            default -> {
                px = 4;
                py = (screenH - ph) / 2f;
            }
        }

        // --- draw ---
        GuiGraphics g = event.getGuiGraphics();
        g.pose().pushPose();
        g.pose().translate(px, py, 0);
        g.pose().scale(s, s, 1f);

        g.fill(0, 0, width, height, 0xC0101016);          // background
        g.fill(0, 0, width, 2, kindColor);                // accent stripe, gold or grey by ore
        g.fill(0, 0, 1, height, 0xFF2C2C38);              // border
        g.fill(width - 1, 0, width, height, 0xFF2C2C38);
        g.fill(0, height - 1, width, height, 0xFF2C2C38);

        int y = PAD + 1;
        g.drawString(font, title, PAD, y, GOLD, true);
        g.drawString(font, time, width - PAD - font.width(time), y, GREY, true);
        y += 12;
        g.fill(PAD, y + 1, PAD + 6, y + 7, kindColor);    // status dot
        g.drawString(font, status, PAD + 10, y, kindColor, true);
        y += 13;
        g.fill(PAD, y, width - PAD, y + 1, 0xFF3A3A48);
        y += 3;

        for (Row r : rows) {
            if (r.label() == null) {
                g.fill(PAD, y + 2, width - PAD, y + 3, 0xFF3A3A48);
                y += 6;
                continue;
            }
            g.drawString(font, r.label(), PAD, y, GREY, true);
            int vx = width - PAD - font.width(r.value());
            g.drawString(font, r.value(), vx, y, r.color(), true);
            if (r.bar() >= 0) {
                int bx = vx - BAR_W - 4;
                g.fill(bx, y + 2, bx + BAR_W, y + 8, 0xFF2A2A34);
                g.fill(bx, y + 2, bx + Math.round(BAR_W * r.bar()), y + 8, r.color());
            }
            y += LINE;
        }
        g.pose().popPose();
    }
}
