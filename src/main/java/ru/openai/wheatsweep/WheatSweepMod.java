package ru.openai.wheatsweep;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public final class WheatSweepMod implements ModInitializer {
    /** Maximum number of crops handled by one planting click (server safety limit). */
    private static final int MAX_PLANTS_PER_CLICK = 1024;

    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.AFTER.register(WheatSweepMod::afterBlockBreak);
        UseBlockCallback.EVENT.register(WheatSweepMod::onUseBlock);
    }

    /**
     * Harvests connected mature wheat around the block the player broke.
     * Every harvested plant is replanted at age zero and drops normal wheat loot.
     */
    private static void afterBlockBreak(
            Level world,
            Player player,
            BlockPos origin,
            BlockState brokenState,
            net.minecraft.world.level.block.entity.BlockEntity blockEntity
    ) {
        if (world.isClientSide()) {
            return;
        }

        ItemStack hoe = player.getMainHandItem();
        if (!player.isCrouching()
                || !(hoe.getItem() instanceof HoeItem)
                || !brokenState.is(Blocks.WHEAT)
                || brokenState.getValue(CropBlock.AGE) != CropBlock.MAX_AGE) {
            return;
        }

        // The first crop has already dropped its vanilla loot. Replant it at age zero.
        ItemStack lootTool = hoe.copy();
        world.setBlock(origin, brokenState.setValue(CropBlock.AGE, 0), 3);

        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(origin);
        visited.add(origin);

        int additionalHarvests = 0;
        while (!queue.isEmpty()) {
            BlockPos current = queue.remove();

            // Each harvested crop checks its own 3x3 area (the eight surrounding cells).
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }

                    BlockPos targetPos = current.offset(dx, 0, dz);
                    if (!visited.add(targetPos) || !world.hasChunkAt(targetPos)) {
                        continue;
                    }

                    BlockState cropState = world.getBlockState(targetPos);
                    // Only fully grown wheat on the same Y level joins the chain.
                    if (!cropState.is(Blocks.WHEAT)
                            || cropState.getValue(CropBlock.AGE) != CropBlock.MAX_AGE) {
                        continue;
                    }

                    Block.dropResources(cropState, world, targetPos, null, player, lootTool);
                    world.setBlock(targetPos, cropState.setValue(CropBlock.AGE, 0), 3);
                    additionalHarvests++;
                    queue.add(targetPos);
                }
            }
        }

        // Vanilla already damaged the hoe for the first crop. Charge for the extra crops too.
        if (additionalHarvests > 0 && !player.getAbilities().instabuild) {
            hoe.hurtAndBreak(additionalHarvests, player, EquipmentSlot.MAINHAND);
        }
    }

    /**
     * With Shift held, wheat seeds plant a connected patch of empty farmland.
     * The clicked farmland is the first node; every planted cell checks its 3x3 neighbours.
     */
    private static InteractionResult onUseBlock(
            Player player,
            Level world,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        if (!player.isCrouching() || player.isSpectator()) {
            return InteractionResult.PASS;
        }

        ItemStack seeds = player.getItemInHand(hand);
        if (!seeds.is(Items.WHEAT_SEEDS)) {
            return InteractionResult.PASS;
        }

        BlockPos startingSoil = hitResult.getBlockPos();
        BlockState startingSoilState = world.getBlockState(startingSoil);
        if (!startingSoilState.is(Blocks.FARMLAND)) {
            return InteractionResult.PASS;
        }

        // With Shift, dry farmland is deliberately skipped instead of falling back to vanilla planting.
        if (startingSoilState.getValue(FarmBlock.MOISTURE) == 0) {
            return InteractionResult.SUCCESS;
        }
        if (!canPlantAt(world, startingSoil)) {
            return InteractionResult.PASS;
        }

        // SUCCESS on the client sends the interaction to the server. The server performs edits.
        if (world.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        boolean creative = player.getAbilities().instabuild;
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(startingSoil);
        visited.add(startingSoil);

        int planted = 0;
        while (!queue.isEmpty() && planted < MAX_PLANTS_PER_CLICK) {
            if (!creative && seeds.isEmpty()) {
                break;
            }

            BlockPos soilPos = queue.remove();
            if (!world.hasChunkAt(soilPos) || !canPlantAt(world, soilPos)) {
                continue;
            }

            BlockPos cropPos = soilPos.above();
            if (!world.setBlock(cropPos, Blocks.WHEAT.defaultBlockState(), 3)) {
                continue;
            }

            if (!creative) {
                seeds.shrink(1);
            }
            planted++;

            // Spread through eight-neighbour farmland, just like the harvesting chain.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }

                    BlockPos neighbourSoil = soilPos.offset(dx, 0, dz);
                    if (visited.add(neighbourSoil)) {
                        queue.add(neighbourSoil);
                    }
                }
            }
        }

        return InteractionResult.SUCCESS;
    }

    /** The position passed here is the farmland block; wheat is placed directly above it. */
    private static boolean canPlantAt(Level world, BlockPos soilPos) {
        if (!world.hasChunkAt(soilPos)) {
            return false;
        }

        BlockState soilState = world.getBlockState(soilPos);
        if (!soilState.is(Blocks.FARMLAND) || soilState.getValue(FarmBlock.MOISTURE) == 0) {
            return false;
        }

        BlockPos cropPos = soilPos.above();
        return world.getBlockState(cropPos).isAir()
                && Blocks.WHEAT.defaultBlockState().canSurvive(world, cropPos);
    }
}
