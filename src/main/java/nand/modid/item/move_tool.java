package nand.modid.item;

import nand.modid.game.MinecraftChessManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class move_tool extends Item {
    public move_tool(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (!world.isClient) {
            ServerPlayerEntity player = (ServerPlayerEntity) context.getPlayer();
            BlockPos pos = context.getBlockPos();

            if (player.isSneaking()) {
                MinecraftChessManager.getInstance().endTurn(player);
            } else {
                MinecraftChessManager.getInstance().handleMoveInteraction(pos, player);
            }
        }
        return ActionResult.success(world.isClient);
    }
}
