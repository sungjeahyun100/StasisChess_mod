package nand.modid;
import nand.modid.registry.ModItems;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StasisChess implements ModInitializer {
	public static final String MOD_ID = "stasischess";
		@Override
		public void onInitialize() {
			ModItems.register();
		}
	}