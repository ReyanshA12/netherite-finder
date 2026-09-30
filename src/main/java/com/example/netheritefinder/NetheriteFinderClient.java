package com.example.netheritefinder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;

public class NetheriteFinderClient implements ClientModInitializer {
    private static final int SEARCH_RADIUS = 64;
    private static final int SEARCH_INTERVAL_TICKS = 10;
    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("netheritefinder", "direction_arrow");

    private KeyMapping findKey;
    private BlockPos nearest;
    private double nearestDistance = Double.MAX_VALUE;
    private int ticksUntilSearch;

    @Override
    public void onInitializeClient() {
        findKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.netheritefinder.find", GLFW.GLFW_KEY_N, "category.netheritefinder"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (findKey.consumeClick()) {
                findNearest(client, true);
            }

            if (nearest != null && --ticksUntilSearch <= 0) {
                findNearest(client, false);
            }
        });

        // World-space outline around the currently selected netherite block.
        LevelRenderEvents.END_MAIN.register(context -> renderOutline(context.poseStack(), context.bufferSource()));

        // HUD arrow pointing toward the selected block.
        HudElementRegistry.addLast(HUD_ID, this::renderArrow);
    }

    private void findNearest(Minecraft client, boolean announce) {
        ticksUntilSearch = SEARCH_INTERVAL_TICKS;
        if (client.player == null || client.level == null) {
            nearest = null;
            return;
        }

        BlockPos center = client.player.blockPosition();
        BlockPos found = null;
        double foundDistance = Double.MAX_VALUE;

        int minX = center.getX() - SEARCH_RADIUS;
        int maxX = center.getX() + SEARCH_RADIUS;
        int minY = Math.max(client.level.getMinY(), center.getY() - SEARCH_RADIUS);
        int maxY = Math.min(client.level.getMaxY(), center.getY() + SEARCH_RADIUS);
        int minZ = center.getZ() - SEARCH_RADIUS;
        int maxZ = center.getZ() + SEARCH_RADIUS;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!client.level.hasChunkAt(pos)) continue;
                    if (!client.level.getBlockState(pos).is(Blocks.NETHERITE_BLOCK)) continue;

                    double distance = center.distSqr(pos);
                    if (distance < foundDistance) {
                        foundDistance = distance;
                        found = pos;
                    }
                }
            }
        }

        nearest = found;
        nearestDistance = foundDistance;

        if (announce) {
            if (nearest == null) {
                client.player.displayClientMessage(Component.literal(
                        "§cNo netherite block found in the loaded 128×128×128 area."), false);
            } else {
                client.player.displayClientMessage(Component.literal(String.format(
                        "§bNearest netherite block: §f%d %d %d §7(%.1f blocks away)",
                        nearest.getX(), nearest.getY(), nearest.getZ(), Math.sqrt(nearestDistance))), false);
            }
        }
    }

    private void renderOutline(PoseStack poseStack, MultiBufferSource bufferSource) {
        Minecraft client = Minecraft.getInstance();
        if (nearest == null || client.player == null || client.level == null || bufferSource == null) return;
        if (!client.level.hasChunkAt(nearest)) {
            nearest = null;
            return;
        }
        if (!client.level.getBlockState(nearest).is(Blocks.NETHERITE_BLOCK)) {
            nearest = null;
            return;
        }

        // Render coordinates in this phase are camera-relative.
        double cameraX = client.gameRenderer.getMainCamera().getPosition().x;
        double cameraY = client.gameRenderer.getMainCamera().getPosition().y;
        double cameraZ = client.gameRenderer.getMainCamera().getPosition().z;

        AABB box = new AABB(
                nearest.getX() - cameraX,
                nearest.getY() - cameraY,
                nearest.getZ() - cameraZ,
                nearest.getX() + 1.0 - cameraX,
                nearest.getY() + 1.0 - cameraY,
                nearest.getZ() + 1.0 - cameraZ
        ).inflate(0.003);

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());
        ShapeRenderer.renderLineBox(poseStack, buffer, box, 0.15f, 0.95f, 1.0f, 1.0f);
    }

    private void renderArrow(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (nearest == null || client.player == null || client.level == null) return;
        if (!client.level.hasChunkAt(nearest)) return;

        double dx = nearest.getX() + 0.5 - client.player.getX();
        double dz = nearest.getZ() + 0.5 - client.player.getZ();
        if (Math.abs(dx) < 0.01 && Math.abs(dz) < 0.01) return;

        // Minecraft yaw: 0 = south, 90 = west. Convert target angle into screen-space.
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float relative = Mth.wrapDegrees(targetYaw - client.player.getYRot());

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int centerX = width / 2;
        int centerY = height / 2 - 42;

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().rotate(org.joml.Matrix3x2f.rotation((float) Math.toRadians(-relative)));
        graphics.drawString(client.font, "▲", -4, -6, 0xFF66EFFF, true);
        graphics.pose().popPose();

        String distance = String.format("%.0fm", Math.sqrt(nearestDistance));
        graphics.drawString(client.font, distance, centerX - client.font.width(distance) / 2, centerY + 9, 0xFFFFFFFF, true);
    }
}
