package nand.modid.game;

import nand.modid.comand.ChessStackEngine;
import nand.modid.chess.core.Piece;
import nand.modid.chess.core.Move;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;

import java.util.*;

public class MinecraftChessManager {
    private static final MinecraftChessManager INSTANCE = new MinecraftChessManager();
    
    private final ChessStackEngine engine;
    private String activeGameId;
    private BlockPos boardOrigin;
    
    // Tracks pieceId -> List of Display Entity UUIDs (Block and Text)
    private final Map<String, List<UUID>> pieceEntities = new HashMap<>();
    private UUID statusEntity;
    
    private int[] selectedSquare = null;
    private int selectedPocketIndex = -1;

    private MinecraftChessManager() {
        this.engine = new ChessStackEngine();
    }

    public static MinecraftChessManager getInstance() {
        return INSTANCE;
    }

    public void startNewGame(BlockPos origin, ServerPlayerEntity player) {
        clearEntities(player.getServerWorld());
        
        this.boardOrigin = origin;
        this.activeGameId = engine.createGame();
        this.selectedSquare = null;
        this.selectedPocketIndex = -1;
        
        player.sendMessage(Text.literal("§aNew Game Started!"), false);
        syncAllPieces(player.getServerWorld());
    }

    private void clearEntities(ServerWorld world) {
        for (List<UUID> uuids : pieceEntities.values()) {
            for (UUID uuid : uuids) {
                Entity e = world.getEntity(uuid);
                if (e != null) e.discard();
            }
        }
        pieceEntities.clear();
        
        if (statusEntity != null) {
            Entity e = world.getEntity(statusEntity);
            if (e != null) e.discard();
            statusEntity = null;
        }
    }

    private void syncAllPieces(ServerWorld world) {
        updateStatusEntity(world);
        if (activeGameId == null) return;
        
        List<Piece.PieceData> boardPieces = engine.getBoardPieces(activeGameId);
        Set<String> currentPieceIds = new HashSet<>();
        
        for (Piece.PieceData p : boardPieces) {
            currentPieceIds.add(p.id);
            updatePieceVisuals(world, p);
        }
        
        Iterator<Map.Entry<String, List<UUID>>> it = pieceEntities.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, List<UUID>> entry = it.next();
            if (!currentPieceIds.contains(entry.getKey())) {
                for (UUID uuid : entry.getValue()) {
                    Entity e = world.getEntity(uuid);
                    if (e != null) e.discard();
                }
                it.remove();
            }
        }
    }

    private void updateStatusEntity(ServerWorld world) {
        if (boardOrigin == null) return;

        double x = boardOrigin.getX() + 8.0;
        double z = boardOrigin.getZ() + 8.0;
        double y = boardOrigin.getY() + 4.0; // Higher above board top

        DisplayEntity.TextDisplayEntity textDisplay;
        if (statusEntity != null && world.getEntity(statusEntity) instanceof DisplayEntity.TextDisplayEntity old) {
            textDisplay = old;
        } else {
            textDisplay = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
            world.spawnEntity(textDisplay);
            statusEntity = textDisplay.getUuid();
        }

        textDisplay.refreshPositionAndAngles(x, y, z, 0, 0);
        String turnText = activeGameId == null ? "§6§lGAME OVER" : 
            (engine.getCurrentPlayer(activeGameId) == 0 ? "§f§lWhite's Turn" : "§7§lBlack's Turn");
        textDisplay.setText(Text.literal(turnText));
        textDisplay.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
    }

    private void updatePieceVisuals(ServerWorld world, Piece.PieceData p) {
        if (p.pos == null) return;

        double x = boardOrigin.getX() + p.pos.x * 2 + 1.0;
        double z = boardOrigin.getZ() + p.pos.y * 2 + 1.0;
        double y = boardOrigin.getY() + 1.0; // Top of the board block

        List<UUID> uuids = pieceEntities.computeIfAbsent(p.id, k -> new ArrayList<>());
        DisplayEntity.BlockDisplayEntity blockDisplay = null;
        DisplayEntity.TextDisplayEntity textDisplay = null;

        for (UUID uuid : uuids) {
            Entity e = world.getEntity(uuid);
            if (e instanceof DisplayEntity.BlockDisplayEntity b) blockDisplay = b;
            else if (e instanceof DisplayEntity.TextDisplayEntity t) textDisplay = t;
        }

        if (blockDisplay == null) {
            blockDisplay = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
            world.spawnEntity(blockDisplay);
            uuids.add(blockDisplay.getUuid());
        }
        if (textDisplay == null) {
            textDisplay = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
            world.spawnEntity(textDisplay);
            uuids.add(textDisplay.getUuid());
        }

        // Update Block
        blockDisplay.refreshPositionAndAngles(x - 0.5, y, z - 0.5, 0, 0);
        blockDisplay.setBlockState(getPieceBlock(p));

        // Update Text
        textDisplay.refreshPositionAndAngles(x, y + 1.3, z, 0, 0);
        String name = String.format("%s%s [%d]", p.owner == 0 ? "§f" : "§7", p.effectiveKind().name(), p.moveStack);
        if (p.stun > 0) name += " §c(STUN " + p.stun + ")";
        if (p.isRoyal) name = "§6★ " + name;
        textDisplay.setText(Text.literal(name));
        textDisplay.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
    }

    private BlockState getPieceBlock(Piece.PieceData p) {
        boolean isWhite = p.owner == 0;
        return switch (p.effectiveKind()) {
            case KING -> (isWhite ? Blocks.GOLD_BLOCK : Blocks.NETHERITE_BLOCK).getDefaultState();
            case QUEEN -> (isWhite ? Blocks.DIAMOND_BLOCK : Blocks.CRYING_OBSIDIAN).getDefaultState();
            case ROOK -> (isWhite ? Blocks.IRON_BLOCK : Blocks.OBSIDIAN).getDefaultState();
            case BISHOP -> (isWhite ? Blocks.QUARTZ_BLOCK : Blocks.COAL_BLOCK).getDefaultState();
            case KNIGHT -> (isWhite ? Blocks.WHITE_TERRACOTTA : Blocks.BLACK_TERRACOTTA).getDefaultState();
            case PAWN -> (isWhite ? Blocks.WHITE_WOOL : Blocks.BLACK_WOOL).getDefaultState();
            case AMAZON -> Blocks.PURPUR_BLOCK.getDefaultState();
            case CANNON -> Blocks.TNT.getDefaultState();
            default -> (isWhite ? Blocks.SNOW_BLOCK : Blocks.GRAY_CONCRETE).getDefaultState();
        };
    }

    public void cyclePocketSelection(ServerPlayerEntity player) {
        if (activeGameId == null) return;
        int currentPlayer = engine.getCurrentPlayer(activeGameId);
        List<Piece.PieceSpec> pocket = engine.getPocket(activeGameId, currentPlayer);
        if (pocket.isEmpty()) {
            player.sendMessage(Text.literal("§cPocket is empty!"), false);
            selectedPocketIndex = -1;
            return;
        }
        selectedPocketIndex++;
        if (selectedPocketIndex >= pocket.size()) {
            selectedPocketIndex = -1;
            player.sendMessage(Text.literal("§7Pocket Selection: None"), false);
        } else {
            Piece.PieceSpec spec = pocket.get(selectedPocketIndex);
            player.sendMessage(Text.literal("§ePocket Selection: §l" + spec.kind.name() + " §r(" + (selectedPocketIndex+1) + "/" + pocket.size() + ")"), false);
        }
    }

    public void handlePlaceInteraction(BlockPos clickedPos, ServerPlayerEntity player) {
        if (activeGameId == null || boardOrigin == null) {
            player.sendMessage(Text.literal("§cNo active game."), false);
            return;
        }
        int dx = clickedPos.getX() - boardOrigin.getX();
        int dz = clickedPos.getZ() - boardOrigin.getZ();
        if (dx < 0 || dx >= 16 || dz < 0 || dz >= 16) return;
        int boardX = dx / 2;
        int boardY = dz / 2;

        if (selectedPocketIndex >= 0) {
            List<Piece.PieceSpec> pocket = engine.getPocket(activeGameId, engine.getCurrentPlayer(activeGameId));
            if (selectedPocketIndex < pocket.size()) {
                Piece.PieceSpec spec = pocket.get(selectedPocketIndex);
                try {
                    engine.placePiece(activeGameId, spec.kind.name(), boardX, boardY);
                    player.sendMessage(Text.literal("§aPlaced " + spec.kind.name()), false);
                    selectedPocketIndex = -1; 
                } catch (Exception e) {
                    player.sendMessage(Text.literal("§c" + e.getMessage()), false);
                }
            }
        }
        syncAllPieces(player.getServerWorld());
        checkGameResult(player);
    }

    public void handleMoveInteraction(BlockPos clickedPos, ServerPlayerEntity player) {
        if (activeGameId == null || boardOrigin == null) return;
        int dx = clickedPos.getX() - boardOrigin.getX();
        int dz = clickedPos.getZ() - boardOrigin.getZ();
        if (dx < 0 || dx >= 16 || dz < 0 || dz >= 16) return;
        int boardX = dx / 2;
        int boardY = dz / 2;

        if (selectedSquare == null) {
            Piece.PieceData piece = engine.getPieceAt(activeGameId, boardX, boardY);
            if (piece != null && piece.owner == engine.getCurrentPlayer(activeGameId)) {
                selectedSquare = new int[]{boardX, boardY};
                player.sendMessage(Text.literal("§eSelected §l" + piece.kind.name()), false);
            }
        } else {
            if (selectedSquare[0] == boardX && selectedSquare[1] == boardY) {
                selectedSquare = null;
                player.sendMessage(Text.literal("§7Deselected"), false);
            } else {
                try {
                    engine.makeMove(activeGameId, selectedSquare[0], selectedSquare[1], boardX, boardY);
                    player.sendMessage(Text.literal("§aMoved"), false);
                } catch (Exception e) {
                    player.sendMessage(Text.literal("§c" + e.getMessage()), false);
                }
                selectedSquare = null;
            }
        }
        syncAllPieces(player.getServerWorld());
        checkGameResult(player);
    }

    private void checkGameResult(ServerPlayerEntity player) {
        if (activeGameId == null) return;
        Move.GameResult result = engine.getGameResult(activeGameId);
        if (result != Move.GameResult.ONGOING) {
            player.sendMessage(Text.literal("§6§lGAME OVER: " + result), false);
            activeGameId = null;
        }
    }

    public void handleInteraction(BlockPos clickedPos, ServerPlayerEntity player) {
        if (selectedPocketIndex >= 0) handlePlaceInteraction(clickedPos, player);
        else handleMoveInteraction(clickedPos, player);
    }
    
    public void resetGame(ServerPlayerEntity player) {
        clearEntities(player.getServerWorld());
        this.activeGameId = null;
        this.boardOrigin = null;
        this.selectedSquare = null;
        this.selectedPocketIndex = -1;
        player.sendMessage(Text.literal("§cReset"), false);
    }
    
    public void endTurn(ServerPlayerEntity player) {
        if (activeGameId == null) return;
        try {
            engine.endTurn(activeGameId);
            player.sendMessage(Text.literal("Turn Ended"), false);
            syncAllPieces(player.getServerWorld());
            checkGameResult(player);
        } catch (Exception e) {
             player.sendMessage(Text.literal("§c" + e.getMessage()), false);
        }
    }
}

