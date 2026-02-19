package nand.modid;
import nand.modid.registry.ModItems;
import nand.modid.game.MinecraftChessManager;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class StasisChess implements ModInitializer {
	public static final String MOD_ID = "stasischess";
		@Override
		public void onInitialize() {
			ModItems.register();

			CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
				dispatcher.register(CommandManager.literal("chess")
					.then(CommandManager.literal("reset")
						.executes(context -> {
							ServerPlayerEntity player = context.getSource().getPlayer();
							if (player != null) {
								MinecraftChessManager.getInstance().resetGame(player);
							}
							return 1;
						})
					)
				);
			});
		}
	}