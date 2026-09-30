package com.example.netheritefinder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class NetheriteFinderClient implements ClientModInitializer {
    private static final int SEARCH_RADIUS = 64;
    private static final int SEARCH_INTERVAL_TICKS = 10;
    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("netheritefinder", "direction_arrow");
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("netheritefinder", "main"));

    private KeyMapping findKey;
    private BlockPos nearest;
    private double nearestDistance = Double.MAX_VALUE;
    private int ticksUntilSearch;

    @Override
    public void onInitializeClient() {
        findKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.netheritefinder.find", GLFW.GLFW_KEY_N, CATEGORY));

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
                client.player.sendSystemMessage(Component.literal(
                        "\u00a7cNo netherite block found in the loaded 128x128x128 area."));
            } else {
                client.player.sendSystemMessage(Component.literal(String.format(
                        "\u00a7bNearest netherite block: \u00a7f%d %d %d \u00a77(%.1f blocks away)",
                        nearest.getX(), nearest.getY(), nearest.getZ(), Math.sqrt(nearestDistance))));
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
        Vec3 cam = client.gameRenderer.getMainCamera().position();

        AABB box = new AABB(
                nearest.getX() - cam.x,
                nearest.getY() - cam.y,
                nearest.getZ() - cam.z,
                nearest.getX() + 1.0 - cam.x,
                nearest.getY() + 1.0 - cam.y,
                nearest.getZ() + 1.0 - cam.z
        ).inflate(0.003);

        VertexConsumer buffer = bufferSource.getBuffer(RenderTypes.lines());
        ShapeRenderer.renderLineBox(poseStack.last(), buffer, box, 0.15f, 0.95f, 1.0f, 1.0f);
    }

    private void renderArrow(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
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

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) centerX, (float) centerY);
        graphics.pose().rotate((float) Math.toRadians(relative));
        graphics.text(client.font, "\u25b2", -4, -6, 0xFF66EFFF, true);
        graphics.pose().popMatrix();

        String distance = String.format("%.0fm", Math.sqrt(nearestDistance));
        graphics.text(client.font, distance, centerX - client.font.width(distance) / 2, centerY + 9, 0xFFFFFFFF, true);
    }
}
