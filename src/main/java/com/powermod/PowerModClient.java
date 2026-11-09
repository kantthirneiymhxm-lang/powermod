package com.powermod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class PowerModClient implements ClientModInitializer {
    private static float mana = PowerMod.MAX_MANA;
    private static float shown = PowerMod.MAX_MANA;
    private static int flash = 0;
    private static final int FLASH_TICKS = 24;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(PowerMod.MANA_SYNC, (client, handler, buf, sender) -> {
            float m = buf.readFloat();
            client.execute(() -> mana = m);
        });
        ClientPlayNetworking.registerGlobalReceiver(PowerMod.FX_FLASH, (client, handler, buf, sender) ->
                client.execute(() -> flash = FLASH_TICKS));

        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (flash > 0) flash--;
            shown += (mana - shown) * 0.15f; // smooth drain animation
        });

        HudRenderCallback.EVENT.register(PowerModClient::render);
    }

    private static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();

        // red screen overlay when a power is released
        if (flash > 0) {
            float t = flash / (float) FLASH_TICKS;
            int a = (int) (t * 0.5f * 255);
            ctx.fill(0, 0, w, h, (a << 24) | 0x00B00000);
        }

        // power bar
        int x = 10, y = h - 22, bw = 100, bh = 8;
        ctx.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, 0xFF000000);
        ctx.fill(x, y, x + bw, y + bh, 0xFF2A0505);
        int fill = (int) (bw * Math.max(0f, Math.min(1f, shown / PowerMod.MAX_MANA)));
        boolean low = shown < 25f && (mc.player.age / 5) % 2 == 0;
        ctx.fill(x, y, x + fill, y + bh, low ? 0xFFFF6644 : 0xFFD01515);
        ctx.fill(x, y, x + fill, y + 2, 0x66FFFFFF);
        ctx.drawTextWithShadow(mc.textRenderer, "POWER " + (int) shown, x, y - 11, 0xFFFFFF);
    }
}
