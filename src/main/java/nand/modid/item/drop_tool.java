package nand.modid.item;

import nand.modid.game.MinecraftChessManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class drop_tool extends Item {
    public drop_tool(Settings settings) {
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
                // 포켓 영역 클릭 → 기물 선택
                // 보드 영역 클릭 → 선택된 기물을 착수
                if (!MinecraftChessManager.getInstance().handlePocketClick(pos, player)) {
                    MinecraftChessManager.getInstance().handlePlaceInteraction(pos, player);
                }
            }
        }
        return ActionResult.success(world.isClient);
    }
}
