package com.b1progame.adminmod.gui.search;

import com.b1progame.adminmod.gui.AdminGuiService;
import com.b1progame.adminmod.gui.browser.PlayerBrowserMode;
import com.b1progame.adminmod.gui.browser.PlayerBrowserState;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerSearchInputManager {
    private final AdminGuiService guiService;
    private final Map<UUID, PendingSignInput> pendingInputs = new HashMap<>();

    public PlayerSearchInputManager(AdminGuiService guiService) {
        this.guiService = guiService;
    }

    public synchronized void openSignInput(ServerPlayerEntity player) {
        cleanup(player);
        player.closeHandledScreen();
        World world = player.getEntityWorld();
        BlockPos pos = findInputSignPos(player);
        BlockState previous = world.getBlockState(pos);
        world.setBlockState(pos, Blocks.OAK_SIGN.getDefaultState());
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof SignBlockEntity sign) {
            this.pendingInputs.put(player.getUuid(), new PendingSignInput(pos, previous));
            player.openEditSignScreen(sign, true);
            return;
        }
        world.setBlockState(pos, previous);
        PlayerBrowserState state = this.guiService.browserSessions().getOrCreate(player.getUuid());
        state.page = 0;
        this.guiService.openPlayerList(player);
    }

    private BlockPos findInputSignPos(ServerPlayerEntity player) {
        World world = player.getEntityWorld();
        BlockPos origin = player.getBlockPos();
        for (int radius = 0; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos floor = origin.add(dx, -1, dz);
                    BlockPos signPos = floor.up();
                    if (!world.getBlockState(floor).isSolidBlock(world, floor)) {
                        continue;
                    }
                    if (!world.getBlockState(signPos).isReplaceable()) {
                        continue;
                    }
                    return signPos;
                }
            }
        }
        return origin.up();
    }

    public synchronized boolean handleSignUpdate(ServerPlayerEntity player, BlockPos pos, String[] lines) {
        PendingSignInput pending = this.pendingInputs.get(player.getUuid());
        if (pending == null || !pending.pos.equals(pos)) {
            return false;
        }
        this.pendingInputs.remove(player.getUuid());
        player.getEntityWorld().setBlockState(pending.pos, pending.previousState);
        String query = "";
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                query = line.trim();
                break;
            }
        }
        PlayerBrowserState state = this.guiService.browserSessions().getOrCreate(player.getUuid());
        state.search = query;
        state.mode = PlayerBrowserMode.ALL;
        state.page = 0;
        this.guiService.openPlayerList(player);
        return true;
    }

    public synchronized void cleanup(ServerPlayerEntity player) {
        PendingSignInput pending = this.pendingInputs.remove(player.getUuid());
        if (pending == null) {
            return;
        }
        player.getEntityWorld().setBlockState(pending.pos, pending.previousState);
    }

    private record PendingSignInput(BlockPos pos, BlockState previousState) {
    }
}
