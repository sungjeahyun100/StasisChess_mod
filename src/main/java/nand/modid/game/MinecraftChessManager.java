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
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.*;

public class MinecraftChessManager {
    private static final MinecraftChessManager INSTANCE = new MinecraftChessManager();
    
    private static class MoveAnimation {
        String gameId;
        String pieceId;
        double startX, startY, startZ;
        double endX, endY, endZ;
        int currentTick;
        int maxTicks = 20; 
        UUID playerUuid;
    }

    private final ChessStackEngine engine;
    private String activeGameId;
    private BlockPos boardOrigin;
    
    // Tracks gameId -> pieceId -> List of Display Entity UUIDs (Block and Text)
    private final Map<String, Map<String, List<UUID>>> pieceEntities = new HashMap<>();
    private final Map<String, MoveAnimation> activeAnimations = new HashMap<>();
    private UUID statusEntity;
    // 포켓 표시 엔티티: 플레이어(0=백, 1=흑) -> 디스플레이 엔티티 UUID 목록
    private final Map<Integer, List<UUID>> pocketEntities = new HashMap<>();
    
    // 체스판 생성 전의 블록들을 저장
    private final Map<BlockPos, BlockState> savedBlocks = new HashMap<>();
    
    private int[] selectedSquare = null;
    private int selectedPocketIndex = -1;

    /** 포켓 한 줄당 최대 슬롯 수 (보드 가로 = 8칸) */
    private static final int POCKET_COLS = 8;

    private MinecraftChessManager() {
        this.engine = new ChessStackEngine();
    }

    public static MinecraftChessManager getInstance() {
        return INSTANCE;
    }

    public void startNewGame(BlockPos origin, ServerPlayerEntity player) {
        this.boardOrigin = origin;
        this.activeGameId = engine.createGame();
        
        this.selectedSquare = null;
        this.selectedPocketIndex = -1;
        
        player.sendMessage(Text.literal("§aNew Game Started!"), false);
        syncAllPieces(player.getServerWorld());
    }

    public void startExperimentalGame(BlockPos origin, ServerPlayerEntity player) {
        this.boardOrigin = origin;
        this.activeGameId = engine.createExperimentalGame();

        this.selectedSquare = null;
        this.selectedPocketIndex = -1;

        player.sendMessage(Text.literal("§d§lExperimental Game Started! (실험용 포켓)"), false);
        syncAllPieces(player.getServerWorld());
    }

    private void clearEntitiesByTag(MinecraftServer server, String tag) {
        for (ServerWorld world : server.getWorlds()) {
            for (Entity e : world.iterateEntities()) {
                if (e.getCommandTags().contains(tag)) {
                    e.discard();
                }
            }
        }
    }

    private void clearEntities(ServerWorld world) {
        for (Map<String, List<UUID>> gamePieces : pieceEntities.values()) {
            for (List<UUID> uuids : gamePieces.values()) {
                for (UUID uuid : uuids) {
                    Entity e = world.getEntity(uuid);
                    if (e != null) e.discard();
                }
            }
        }
        pieceEntities.clear();

        // 포켓 표시 엔티티 정리
        for (List<UUID> uuids : pocketEntities.values()) {
            for (UUID uuid : uuids) {
                Entity e = world.getEntity(uuid);
                if (e != null) e.discard();
            }
        }
        pocketEntities.clear();
        
        if (statusEntity != null) {
            Entity e = world.getEntity(statusEntity);
            if (e != null) e.discard();
            statusEntity = null;
        }
    }

    private void syncAllPieces(ServerWorld world) {
        updateStatusEntity(world);
        syncPocketDisplays(world);
        if (activeGameId == null) return;
        
        List<Piece.PieceData> boardPieces = engine.getBoardPieces(activeGameId);
        Set<String> currentPieceIds = new HashSet<>();
        
        for (Piece.PieceData p : boardPieces) {
            currentPieceIds.add(p.id);
            updatePieceVisuals(world, p);
        }
        
        Map<String, List<UUID>> gamePieces = pieceEntities.get(activeGameId);
        if (gamePieces != null) {
            Iterator<Map.Entry<String, List<UUID>>> it = gamePieces.entrySet().iterator();
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
            textDisplay.addCommandTag("sc_status");
            textDisplay.addCommandTag("sc_game_" + activeGameId);
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

        Map<String, List<UUID>> gamePieces = pieceEntities.computeIfAbsent(activeGameId, k -> new HashMap<>());
        List<UUID> uuids = gamePieces.computeIfAbsent(p.id, k -> new ArrayList<>());
        DisplayEntity.BlockDisplayEntity blockDisplay = null;
        DisplayEntity.TextDisplayEntity textDisplay = null;

        for (UUID uuid : uuids) {
            Entity e = world.getEntity(uuid);
            if (e instanceof DisplayEntity.BlockDisplayEntity b) blockDisplay = b;
            else if (e instanceof DisplayEntity.TextDisplayEntity t) textDisplay = t;
        }

        if (blockDisplay == null) {
            blockDisplay = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
            blockDisplay.addCommandTag("sc_game_" + activeGameId);
            blockDisplay.addCommandTag("sc_piece_" + p.id);
            world.spawnEntity(blockDisplay);
            uuids.add(blockDisplay.getUuid());
        }
        if (textDisplay == null) {
            textDisplay = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
            textDisplay.addCommandTag("sc_game_" + activeGameId);
            textDisplay.addCommandTag("sc_piece_" + p.id);
            world.spawnEntity(textDisplay);
            uuids.add(textDisplay.getUuid());
        }

        // Update Position (Only if NOT animating)
        if (!activeAnimations.containsKey(p.id)) {
            blockDisplay.refreshPositionAndAngles(x - 0.5, y, z - 0.5, 0, 0);
            textDisplay.refreshPositionAndAngles(x, y + 1.3, z, 0, 0);
        }

        // Update Visuals (Always)
        blockDisplay.setBlockState(getPieceBlock(p));
        
        String name = String.format("%s%s [%d]", p.owner == 0 ? "§f" : "§7", p.effectiveKind().name(), p.moveStack);
        if (p.stun > 0) name += " §c(STUN " + p.stun + ")";
        if (p.isRoyal) name = "§6★ " + name;
        textDisplay.setText(Text.literal(name));
        textDisplay.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
    }

    private BlockState getPieceBlock(Piece.PieceData p) {
        return getPieceBlockForKind(p.effectiveKind(), p.owner == 0);
    }

    private BlockState getPieceBlockForKind(Piece.PieceKind kind, boolean isWhite) {
        return switch (kind) {
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

    /**
     * 게임 시작/매 수마다 양옆에 포켓 표시를 갱신한다.
     * 백 포켓: 보드 남쪽(z-2), 흑 포켓: 보드 북쪽(z+17)
     */
    private void syncPocketDisplays(ServerWorld world) {
        // 기존 포켓 엔티티 제거
        for (List<UUID> uuids : pocketEntities.values()) {
            for (UUID uuid : uuids) {
                Entity e = world.getEntity(uuid);
                if (e != null) e.discard();
            }
        }
        pocketEntities.clear();

        if (activeGameId == null || boardOrigin == null) return;

        double y = boardOrigin.getY() + 1.0;
        String[] titles = { "§f§lWHITE POCKET", "§7§lBLACK POCKET" };

        for (int player = 0; player < 2; player++) {
            boolean isWhite = (player == 0);
            List<UUID> playerUuids = pocketEntities.computeIfAbsent(player, k -> new ArrayList<>());

            // 제목 텍스트 (항상 0번 줄 기준)
            DisplayEntity.TextDisplayEntity titleDisplay =
                new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
            titleDisplay.addCommandTag("sc_pocket");
            titleDisplay.addCommandTag("sc_game_" + activeGameId);
            titleDisplay.refreshPositionAndAngles(
                boardOrigin.getX() - 2.0, y + 1.5, getPocketZ(player, 0), 0, 0);
            titleDisplay.setText(Text.literal(titles[player]));
            titleDisplay.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
            world.spawnEntity(titleDisplay);
            playerUuids.add(titleDisplay.getUuid());

            // 포켓 내 기물 종류별 블록+카운트 텍스트 (줄바꿈 지원)
            Map<Piece.PieceKind, Integer> counts = getGroupedPocket(player);
            List<Map.Entry<Piece.PieceKind, Integer>> entries = new ArrayList<>(counts.entrySet());
            int currentPlayer = (activeGameId != null) ? engine.getCurrentPlayer(activeGameId) : -1;
            boolean isCurrentPlayer = (player == currentPlayer);

            for (int slot = 0; slot < entries.size(); slot++) {
                int row = slot / POCKET_COLS;
                int col = slot % POCKET_COLS;
                Piece.PieceKind kind = entries.get(slot).getKey();
                int count = entries.get(slot).getValue();

                double slotX = boardOrigin.getX() + col * 2.0 + 1.0;
                double pocketZ = getPocketZ(player, row);
                boolean isSelected = isCurrentPlayer && (slot == selectedPocketIndex);

                // 블록 디스플레이
                DisplayEntity.BlockDisplayEntity blockDisplay =
                    new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
                blockDisplay.addCommandTag("sc_pocket");
                blockDisplay.addCommandTag("sc_game_" + activeGameId);
                double blockY = isSelected ? y + 0.3 : y;
                blockDisplay.refreshPositionAndAngles(slotX - 0.5, blockY, pocketZ - 0.5, 0, 0);
                blockDisplay.setBlockState(getPieceBlockForKind(kind, isWhite));
                world.spawnEntity(blockDisplay);
                playerUuids.add(blockDisplay.getUuid());

                // 수량 텍스트 (선택 시 황금색 강조)
                DisplayEntity.TextDisplayEntity countDisplay =
                    new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
                countDisplay.addCommandTag("sc_pocket");
                countDisplay.addCommandTag("sc_game_" + activeGameId);
                countDisplay.refreshPositionAndAngles(slotX, blockY + 1.3, pocketZ, 0, 0);
                String color = isSelected ? "§6§l" : (isWhite ? "§f" : "§7");
                String label = color + kind.name() + " ×" + count + (isSelected ? " §e◀" : "");
                countDisplay.setText(Text.literal(label));
                countDisplay.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
                world.spawnEntity(countDisplay);
                playerUuids.add(countDisplay.getUuid());
            }
        }
    }

    /**
     * 포켓 표시 Z 좌표: 백(player=0)은 보드 남쪽으로, 흑(player=1)은 보드 북쪽으로 줄마다 2블록씩 확장.
     * row 0 → 백 dz=-2 / 흑 dz=+17, row 1 → 백 dz=-4 / 흑 dz=+19, ...
     */
    private double getPocketZ(int player, int row) {
        return player == 0
            ? boardOrigin.getZ() - 2 - row * 2   // 백: 남쪽으로
            : boardOrigin.getZ() + 17 + row * 2; // 흑: 북쪽으로
    }

    private Map<Piece.PieceKind, Integer> getGroupedPocket(int player) {
        List<Piece.PieceSpec> pocket = engine.getPocket(activeGameId, player);
        Map<Piece.PieceKind, Integer> counts = new LinkedHashMap<>();
        for (Piece.PieceSpec spec : pocket) {
            counts.put(spec.kind, counts.getOrDefault(spec.kind, 0) + 1);
        }
        return counts;
    }

    public void cyclePocketSelection(ServerPlayerEntity player) {
        if (activeGameId == null) return;
        int currentPlayer = engine.getCurrentPlayer(activeGameId);
        Map<Piece.PieceKind, Integer> counts = getGroupedPocket(currentPlayer);
        List<Piece.PieceKind> uniqueKinds = new ArrayList<>(counts.keySet());

        if (uniqueKinds.isEmpty()) {
            player.sendMessage(Text.literal("§cPocket is empty!"), false);
            selectedPocketIndex = -1;
            return;
        }

        selectedPocketIndex++;
        if (selectedPocketIndex >= uniqueKinds.size()) {
            selectedPocketIndex = -1;
            player.sendMessage(Text.literal("§7Pocket Selection: None"), false);
        } else {
            Piece.PieceKind kind = uniqueKinds.get(selectedPocketIndex);
            int count = counts.get(kind);
            player.sendMessage(Text.literal("§ePocket Selection: §l" + kind.name() + " §r(x" + count + ") (" + (selectedPocketIndex + 1) + "/" + uniqueKinds.size() + ")"), false);
        }
    }

    /**
     * drop_tool로 포켓 표시 영역을 클릭했을 때 해당 슬롯의 기물을 선택한다.
     * 백 포켓: dz ≤ -1 (줄마다 2블록씩 남쪽으로 확장)
     * 흑 포켓: dz ≥ 16 (줄마다 2블록씩 북쪽으로 확장)
     *
     * @return 포켓 영역 클릭이면 true (처리됨), 아니면 false (보드 클릭으로 처리 위임)
     */
    public boolean handlePocketClick(BlockPos clickedPos, ServerPlayerEntity player) {
        if (activeGameId == null || boardOrigin == null) return false;

        int dx = clickedPos.getX() - boardOrigin.getX();
        int dz = clickedPos.getZ() - boardOrigin.getZ();

        // 포켓 영역 판별 및 줄(row) 계산
        // 백: dz=-1,-2 → row 0 / dz=-3,-4 → row 1 / ...
        // 흑: dz=16,17 → row 0 / dz=18,19 → row 1 / ...
        int clickedPlayer;
        int row;
        if (dz <= -1) {
            clickedPlayer = 0;
            row = (-dz - 1) / 2;
        } else if (dz >= 16) {
            clickedPlayer = 1;
            row = (dz - 16) / 2;
        } else {
            return false;
        }

        if (dx < 0 || dx >= 16) return false;

        int currentPlayer = engine.getCurrentPlayer(activeGameId);
        if (clickedPlayer != currentPlayer) {
            player.sendMessage(Text.literal("§cNot your turn!"), false);
            return true;
        }

        Map<Piece.PieceKind, Integer> counts = getGroupedPocket(currentPlayer);
        List<Piece.PieceKind> uniqueKinds = new ArrayList<>(counts.keySet());

        if (uniqueKinds.isEmpty()) {
            player.sendMessage(Text.literal("§cPocket is empty!"), false);
            return true;
        }

        int col = dx / 2;
        int slot = row * POCKET_COLS + col;

        if (slot >= uniqueKinds.size()) {
            // 해당 슬롯에 기물이 없으면 선택 해제
            selectedPocketIndex = -1;
            player.sendMessage(Text.literal("§7Pocket Selection: None"), false);
            syncPocketDisplays(player.getServerWorld());
            return true;
        }

        selectedPocketIndex = slot;
        Piece.PieceKind kind = uniqueKinds.get(slot);
        int count = counts.get(kind);
//        player.sendMessage(Text.literal(
//            "§ePocket Selected: §l" + kind.name() + " §r(×" + count + ") ["
//            + (slot + 1) + "/" + uniqueKinds.size() + "] — 보드를 클릭해 착수"), false);

        // 포켓 디스플레이 갱신 (선택 표시)
        syncPocketDisplays(player.getServerWorld());
        return true;
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
            int currentPlayer = engine.getCurrentPlayer(activeGameId);
            Map<Piece.PieceKind, Integer> counts = getGroupedPocket(currentPlayer);
            List<Piece.PieceKind> uniqueKinds = new ArrayList<>(counts.keySet());

            if (selectedPocketIndex < uniqueKinds.size()) {
                Piece.PieceKind kind = uniqueKinds.get(selectedPocketIndex);
                try {
                    engine.placePiece(activeGameId, kind.name(), boardX, boardY);
                    player.sendMessage(Text.literal("§aPlaced " + kind.name()), false);
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
                    Piece.PieceData piece = engine.getPieceAt(activeGameId, selectedSquare[0], selectedSquare[1]);
                    if (piece != null) {
                        double startX = boardOrigin.getX() + selectedSquare[0] * 2 + 1.0;
                        double startZ = boardOrigin.getZ() + selectedSquare[1] * 2 + 1.0;
                        double startY = boardOrigin.getY() + 1.0;

                        engine.makeMove(activeGameId, selectedSquare[0], selectedSquare[1], boardX, boardY);
                        
                        double endX = boardOrigin.getX() + boardX * 2 + 1.0;
                        double endZ = boardOrigin.getZ() + boardY * 2 + 1.0;
                        double endY = boardOrigin.getY() + 1.0;

                        MoveAnimation anim = new MoveAnimation();
                        anim.gameId = activeGameId;
                        anim.pieceId = piece.id;
                        anim.startX = startX; anim.startY = startY; anim.startZ = startZ;
                        anim.endX = endX; anim.endY = endY; anim.endZ = endZ;
                        anim.currentTick = 0;
                        
                        if (player.getPos().distanceTo(new Vec3d(startX, startY, startZ)) < 5.0) {
                            anim.playerUuid = player.getUuid();
                        }
                        activeAnimations.put(piece.id, anim);
                        player.sendMessage(Text.literal("§7(Animating piece: " + piece.effectiveKind().name() + ")"), false);
                    }
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

    public void tick(MinecraftServer server) {
        if (activeAnimations.isEmpty()) return;

        List<String> finished = new ArrayList<>();
        for (MoveAnimation anim : activeAnimations.values()) {
            anim.currentTick++;
            double t = (double) anim.currentTick / 40.0; // 2 seconds, linear

            double x = anim.startX + (anim.endX - anim.startX) * t;
            double y = anim.startY + (anim.endY - anim.startY) * t;
            double z = anim.startZ + (anim.endZ - anim.startZ) * t;

            double hop = Math.sin(t * Math.PI) * 1.0;
            double curY = y + hop;

            ServerPlayerEntity rider = anim.playerUuid != null ? server.getPlayerManager().getPlayer(anim.playerUuid) : null;
            if (rider != null) {
                rider.teleport(rider.getServerWorld(), x, curY + 1.0, z, 
                               EnumSet.of(PositionFlag.X_ROT, PositionFlag.Y_ROT), 
                               0, 0);
            }

            Map<String, List<UUID>> gamePieces = pieceEntities.get(anim.gameId);
            List<UUID> uuids = gamePieces != null ? gamePieces.get(anim.pieceId) : null;
            if (uuids != null) {
                for (ServerWorld world : server.getWorlds()) {
                    boolean foundAny = false;
                    for (UUID uuid : uuids) {
                        Entity e = world.getEntity(uuid);
                        if (e != null) {
                            e.refreshPositionAndAngles(x - (e instanceof DisplayEntity.BlockDisplayEntity ? 0.5 : 0), 
                                                     curY + (e instanceof DisplayEntity.TextDisplayEntity ? 1.3 : 0), 
                                                     z - (e instanceof DisplayEntity.BlockDisplayEntity ? 0.5 : 0), 0, 0);
                            foundAny = true;
                        }
                    }
                    if (foundAny) break;
                }
            }

            if (anim.currentTick >= 40) {
                finished.add(anim.pieceId);
            }
        }

        for (String id : finished) {
            activeAnimations.remove(id);
        }
    }

    public void handleInteraction(BlockPos clickedPos, ServerPlayerEntity player) {
        if (selectedPocketIndex >= 0) handlePlaceInteraction(clickedPos, player);
        else handleMoveInteraction(clickedPos, player);
    }
    
    public void saveArea(ServerWorld world, BlockPos origin, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        // If there's an active game, reset it (and restore its blocks) before saving the new area
        // This prevents the chess board itself from being saved as the 'original' state
        // if the player creates a new board while one is already active.
        // We use a temporary flag or check activeGameId.
        // However, resetGame is called by startNewGame too.
        
        // Logical flow should be:
        // 1. User clicks start_tool.
        // 2. start_tool calls resetGame(player) -> restores old blocks.
        // 3. start_tool calls saveArea(...) -> saves current (restored) blocks.
        // 4. start_tool places new blocks.
        // 5. start_tool calls startNewGame(...) -> which currently calls resetGame again.
        
        // Let's refine the sequence in start_tool and MinecraftChessManager.
        savedBlocks.clear();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    savedBlocks.put(pos, world.getBlockState(pos));
                }
            }
        }
    }

    public void restoreArea(ServerWorld world) {
        if (savedBlocks.isEmpty()) return;
        for (Map.Entry<BlockPos, BlockState> entry : savedBlocks.entrySet()) {
            world.setBlockState(entry.getKey(), entry.getValue());
        }
        savedBlocks.clear();
    }

    public void resetGame(ServerPlayerEntity player) {
        if (activeGameId != null) {
            clearEntitiesByTag(player.getServer(), "sc_game_" + activeGameId);
            pieceEntities.remove(activeGameId);
        }
        // Always try to clear status display
        clearEntitiesByTag(player.getServer(), "sc_status");
        if (statusEntity != null) {
            Entity e = player.getServerWorld().getEntity(statusEntity);
            if (e != null) e.discard();
            statusEntity = null;
        }

        // Restore blocks
        restoreArea(player.getServerWorld());

        this.activeGameId = null;
        this.boardOrigin = null;
        this.selectedSquare = null;
        this.selectedPocketIndex = -1;
        this.activeAnimations.clear();
        player.sendMessage(Text.literal("§cGame Reset and Entities Cleared"), false);
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

    public void showTurnActions(ServerPlayerEntity player) {
        if (activeGameId == null) {
            player.sendMessage(Text.literal("§cNo active game."), false);
            return;
        }
        List<Move.Action> actions = engine.getTurnActions(activeGameId);
        if (actions.isEmpty()) {
            player.sendMessage(Text.literal("§7No actions taken this turn."), false);
            return;
        }

        player.sendMessage(Text.literal("§e§lActions this turn:"), false);
        for (Move.Action action : actions) {
            String msg = switch (action.type) {
                case PLACE -> String.format("§7- Placed piece at %s", action.to);
                case MOVE -> String.format("§7- Moved piece from %s to %s", action.from, action.to);
                case CROWN -> String.format("§7- Crowned piece %s", action.pieceId);
                case DISGUISE -> String.format("§7- Disguised piece %s as %s", action.pieceId, action.asKind);
                case STUN -> String.format("§7- Stunned piece %s (Amount: %d)", action.pieceId, action.stunAmount);
            };
            player.sendMessage(Text.literal(msg), false);
        }
    }
}

