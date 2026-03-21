package nand.modid;

import nand.modid.registry.ModItems;
import nand.modid.game.MinecraftChessManager;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.util.Identifier;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.RegistryByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StasisChess implements ModInitializer {
	public static final String MOD_ID = "stasischess";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public record PerspectivePacketPayload(int perspective) implements CustomPayload {
		public static final CustomPayload.Id<PerspectivePacketPayload> ID = new CustomPayload.Id<>(
				Identifier.of(MOD_ID, "set_perspective"));
		public static final PacketCodec<RegistryByteBuf, PerspectivePacketPayload> CODEC = PacketCodec.tuple(
				PacketCodecs.INTEGER, PerspectivePacketPayload::perspective,
				PerspectivePacketPayload::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record ReloadPacketPayload() implements CustomPayload {
		public static final CustomPayload.Id<ReloadPacketPayload> ID = new CustomPayload.Id<>(
				Identifier.of(MOD_ID, "reload_assets"));
		public static final PacketCodec<RegistryByteBuf, ReloadPacketPayload> CODEC = PacketCodec
				.unit(new ReloadPacketPayload());

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	@Override
	public void onInitialize() {
		ModItems.register();

		// Register the payload types
		PayloadTypeRegistry.playS2C().register(PerspectivePacketPayload.ID, PerspectivePacketPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(PerspectivePacketPayload.ID, PerspectivePacketPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ReloadPacketPayload.ID, ReloadPacketPayload.CODEC);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("chess")
					.then(CommandManager.literal("reset")
							.executes(context -> {
								ServerPlayerEntity player = context.getSource().getPlayer();
								if (player != null) {
									MinecraftChessManager.getInstance().resetGame(player);
								}
								return 1;
							}))
					.then(CommandManager.literal("reload")
							.executes(context -> {
								// 모든 플레이어에게 텍스처 리로드 신호 전송
								if (context.getSource().getServer() != null) {
									for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager()
											.getPlayerList()) {
										net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
												new ReloadPacketPayload());
									}
								}
								context.getSource().sendMessage(
										Text.literal("§a[StasisChess] Textures reloaded!"));
								return 1;
							}))
					.then(CommandManager.literal("debug")
							.executes(context -> {
								ServerPlayerEntity player = context.getSource().getPlayer();
								if (player != null) {
									MinecraftChessManager.getInstance().showEngineState(player);
								}
								return 1;
							}))
					.then(CommandManager.literal("debugmode")
							.executes(context -> {
								ServerPlayerEntity player = context.getSource().getPlayer();
								if (player != null) {
									MinecraftChessManager.getInstance().toggleDebugMode(player);
								}
								return 1;
							}))
					.then(CommandManager.literal("load")
							.then(CommandManager
									.argument("id", com.mojang.brigadier.arguments.StringArgumentType.string())
									.executes(context -> {
										ServerPlayerEntity player = context.getSource().getPlayer();
										if (player != null) {
											String id = com.mojang.brigadier.arguments.StringArgumentType.getString(
													context,
													"id");
											MinecraftChessManager.getInstance().loadGameLog(id, player);
										}
										return 1;
									})))
					.then(CommandManager.literal("save")
							.executes(context -> {
								ServerPlayerEntity player = context.getSource().getPlayer();
								if (player != null) {
									MinecraftChessManager.getInstance().saveGame(null, player);
								}
								return 1;
							})
							.then(CommandManager
									.argument("name", com.mojang.brigadier.arguments.StringArgumentType.string())
									.executes(context -> {
										ServerPlayerEntity player = context.getSource().getPlayer();
										if (player != null) {
											String name = com.mojang.brigadier.arguments.StringArgumentType.getString(
													context,
													"name");
											MinecraftChessManager.getInstance().saveGame(name, player);
										}
										return 1;
									})))
					.then(CommandManager.literal("list")
							.executes(context -> {
								ServerPlayerEntity player = context.getSource().getPlayer();
								if (player != null) {
									MinecraftChessManager.getInstance().listSavedGames(player);
								}
								return 1;
							})));
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			MinecraftChessManager.getInstance().tick(server);
		});
	}
}