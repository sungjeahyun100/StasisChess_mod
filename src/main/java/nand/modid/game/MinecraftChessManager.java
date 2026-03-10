package nand.modid.game;

import nand.modid.comand.ChessStackEngine;
import nand.modid.chess.core.GameState;
import nand.modid.chess.core.Piece;
import nand.modid.chess.core.Move;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import nand.modid.StasisChess;
import nand.modid.registry.ModItems;

import java.util.*;

public class MinecraftChessManager {
    private static final MinecraftChessManager INSTANCE = new MinecraftChessManager();

    private static class MoveAnimation {
        String gameId;
        String pieceId;
        double startX, startY, startZ;
        double endX, endY, endZ;
        float yaw;
        int currentTick;
        int maxTicks = 20;
        UUID playerUuid;
    }

    private final ChessStackEngine engine;
    private String activeGameId;
    private BlockPos boardOrigin;

    // true -> 기물 조작, false -> 포켓 조작
    private boolean paze = false;

    // Tracks gameId -> pieceId -> List of Display Entity UUIDs (Block and Text)
    private final Map<String, Map<String, List<UUID>>> pieceEntities = new HashMap<>();
    private final Map<String, MoveAnimation> activeAnimations = new HashMap<>();
    private UUID statusEntity;
    // 포켓 표시 엔티티: 플레이어(0=백, 1=흑) -> 디스플레이 엔티티 UUID 목록
    private final Map<Integer, List<UUID>> pocketEntities = new HashMap<>();

    // 체스판 생성 전의 블록들을 저장
    private final Map<BlockPos, BlockState> savedBlocks = new HashMap<>();

    private final List<String> moveHistory = new ArrayList<>();

    private int[] selectedSquare = null;
    private List<Move.LegalMove> currentLegalMoves = new ArrayList<>();
    private int selectedPocketIndex = -1;

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
        this.moveHistory.clear();

        player.sendMessage(Text.literal("§aNew Game Started!"), false);
        givePieceItems(player);
        syncAllPieces(player.getServerWorld());
    }

    public void startExperimentalGame(BlockPos origin, ServerPlayerEntity player) {
        this.boardOrigin = origin;
        this.activeGameId = engine.createExperimentalGame();

        this.selectedSquare = null;
        this.selectedPocketIndex = -1;
        this.moveHistory.clear();

        player.sendMessage(Text.literal("§d§lExperimental Game Started!"), false);
        givePieceItems(player);
        syncAllPieces(player.getServerWorld());
    }

    private void givePieceItems(ServerPlayerEntity player) {
        // 지급할 기본 도구 목록
        List<net.minecraft.item.Item> tools = Arrays.asList(
                ModItems.DROP_TOOL,
                ModItems.MOVE_TOOL,
                ModItems.TURN_TOOL);

        // 도구 지급 (중복 체크)
        for (net.minecraft.item.Item tool : tools) {
            if (!hasItem(player, tool)) {
                ItemStack stack = new ItemStack(tool);
                if (!player.getInventory().insertStack(stack)) {
                    player.dropItem(stack, false);
                }
            }
        }

        // 모든 기물 지급 (중복 체크)
        for (Piece.PieceKind kind : Piece.PieceKind.values()) {
            net.minecraft.item.Item pieceItem = ModItems.getPieceItem(kind);
            if (!hasItem(player, pieceItem)) {
                ItemStack stack = new ItemStack(pieceItem);
                if (!player.getInventory().insertStack(stack)) {
                    player.dropItem(stack, false);
                }
            }
        }
        player.sendMessage(Text.literal("§7[StasisChess] 필요한 도구와 기물 아이템을 지급했습니다."), false);
    }

    private boolean hasItem(ServerPlayerEntity player, net.minecraft.item.Item item) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).isOf(item)) {
                return true;
            }
        }
        return false;
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
                    if (e != null)
                        e.discard();
                }
            }
        }
        pieceEntities.clear();

        // 포켓 표시 엔티티 정리
        for (List<UUID> uuids : pocketEntities.values()) {
            for (UUID uuid : uuids) {
                Entity e = world.getEntity(uuid);
                if (e != null)
                    e.discard();
            }
        }
        pocketEntities.clear();

        if (statusEntity != null) {
            Entity e = world.getEntity(statusEntity);
            if (e != null)
                e.discard();
            statusEntity = null;
        }
    }

    private void syncAllPieces(ServerWorld world) {
        updateStatusEntity(world);
        syncPocketDisplays(world);
        if (activeGameId == null)
            return;

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
                        if (e != null)
                            e.discard();
                    }
                    it.remove();
                }
            }
        }
    }

    private void updateStatusEntity(ServerWorld world) {
        if (boardOrigin == null)
            return;

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
        String turnText;
        if (activeGameId == null) {
            turnText = "§6§lGAME OVER";
        } else {
            Move.GameResult result = engine.getGameResult(activeGameId);
            if (result != Move.GameResult.ONGOING) {
                turnText = "§6§lGAME OVER: " + result;
            } else {
                turnText = (engine.getCurrentPlayer(activeGameId) == 0 ? "§f§lWhite's Turn" : "§7§lBlack's Turn");
            }
        }
        textDisplay.setText(Text.literal(turnText));
        textDisplay.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
    }

    private void updatePieceVisuals(ServerWorld world, Piece.PieceData p) {
        if (p.pos == null)
            return;

        double x = boardOrigin.getX() + p.pos.x * 2 + 1.0;
        double z = boardOrigin.getZ() + p.pos.y * 2 + 1.0;
        double y = boardOrigin.getY() + 1.0;

        Map<String, List<UUID>> gamePieces = pieceEntities.computeIfAbsent(activeGameId, k -> new HashMap<>());
        List<UUID> uuids = gamePieces.computeIfAbsent(p.id, k -> new ArrayList<>());

        // 1. 타입을 BlockDisplayEntity에서 ItemDisplayEntity로 변경
        DisplayEntity.ItemDisplayEntity itemDisplay = null;
        DisplayEntity.TextDisplayEntity textDisplay = null;

        for (UUID uuid : uuids) {
            Entity e = world.getEntity(uuid);
            if (e instanceof DisplayEntity.ItemDisplayEntity i)
                itemDisplay = i; // 수정
            else if (e instanceof DisplayEntity.TextDisplayEntity t)
                textDisplay = t;
        }

        if (itemDisplay == null) {
            // 2. 생성 시 EntityType.ITEM_DISPLAY 사용
            itemDisplay = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
            itemDisplay.addCommandTag("sc_game_" + activeGameId);
            itemDisplay.addCommandTag("sc_piece_" + p.id);

            // 3. 아이템 크기 및 변환 설정 (필요 시)
            itemDisplay.setTransformation(new net.minecraft.util.math.AffineTransformation(
                    null, null, new org.joml.Vector3f(1.5f, 1.5f, 1.5f), null));

            world.spawnEntity(itemDisplay);
            uuids.add(itemDisplay.getUuid());
        }

        if (textDisplay == null) {
            textDisplay = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
            textDisplay.addCommandTag("sc_game_" + activeGameId);
            textDisplay.addCommandTag("sc_piece_" + p.id);
            world.spawnEntity(textDisplay);
            uuids.add(textDisplay.getUuid());
        }

        // 위치 업데이트
        if (!activeAnimations.containsKey(p.id)) {
            // 아이템 디스플레이는 블록과 피벗(중심점)이 다를 수 있어 x-0.5 대신 x를 쓸 수도 있습니다.
            itemDisplay.refreshPositionAndAngles(x, y + 0.5, z, 0, 0);
            textDisplay.refreshPositionAndAngles(x, y + 2.7, z, 0, 0);
        }

        // 4. 시각적 업데이트: setBlockState 대신 이전에 만든 getPieceItemForKind 사용
        // p.owner가 0이면 White, 1이면 Black으로 가정
        itemDisplay.setItemStack(getPieceItemForKind(p.effectiveKind(), p.owner == 0));

        // 텍스트 업데이트
        String name = String.format("%s%s [%d]", p.owner == 0 ? "§f" : "§7", p.effectiveKind().name(), p.moveStack);
        if (p.stun > 0)
            name += " §c(STUN " + p.stun + ")";
        if (p.isRoyal)
            name = "§6★ " + name;
        textDisplay.setText(Text.literal(name));
        textDisplay.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
    }

    private ItemStack getPieceBlock(Piece.PieceData p) {
        return getPieceItemForKind(p.effectiveKind(), p.owner == 0);
    }

    private ItemStack getPieceItemForKind(Piece.PieceKind kind, boolean isWhite) {
        ItemStack stack = new ItemStack(ModItems.PIECE_MODEL);

        int modelData = switch (kind) {
            case PAWN -> isWhite ? 7 : 1;
            case KNIGHT -> isWhite ? 8 : 2;
            case ROOK -> isWhite ? 9 : 3;
            case BISHOP -> isWhite ? 10 : 4;
            case QUEEN -> isWhite ? 11 : 5;
            case KING -> isWhite ? 12 : 6;
            default -> 0;
        };

        if (modelData > 0) {
            // NBT 대신 Data Component를 사용하여 Custom Model Data 설정
            stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(modelData));
        }

        return stack;
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
                if (e != null)
                    e.discard();
            }
        }
        pocketEntities.clear();

        if (activeGameId == null || boardOrigin == null)
            return;

        double y = boardOrigin.getY() + 1.0;
        // player: 0=백(남쪽), 1=흑(북쪽)
        int[] zOffsets = { -2, 17 };
        String[] titles = { "§f§lWHITE POCKET", "§7§lBLACK POCKET" };

        for (int player = 0; player < 2; player++) {
            double pocketZ = boardOrigin.getZ() + zOffsets[player];
            boolean isWhite = (player == 0);
            List<UUID> playerUuids = pocketEntities.computeIfAbsent(player, k -> new ArrayList<>());

            // 제목 텍스트 (보드 중앙 X+8.0으로 정렬)
            DisplayEntity.TextDisplayEntity titleDisplay = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY,
                    world);
            titleDisplay.addCommandTag("sc_pocket");
            titleDisplay.addCommandTag("sc_game_" + activeGameId);
            // 제목 위치를 약간 더 뒤로 밀어서 기물과 겹치지 않게 함
            double titleZ = pocketZ + (isWhite ? 1.5 : -1.5);
            titleDisplay.refreshPositionAndAngles(
                    boardOrigin.getX() + 8.0, y + 2.5, titleZ, 0, 0);
            titleDisplay.setText(Text.literal(titles[player]));
            titleDisplay.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
            world.spawnEntity(titleDisplay);
            playerUuids.add(titleDisplay.getUuid());

            // 포켓 내 기물 종류별 블록+카운트 텍스트
            Map<Piece.PieceKind, Integer> counts = getGroupedPocket(player);
            int slot = 0;
            // 현재 플레이어의 포켓에만 선택 표시 적용
            int currentPlayer = (activeGameId != null) ? engine.getCurrentPlayer(activeGameId) : -1;
            boolean isCurrentPlayer = (player == currentPlayer);
            for (Map.Entry<Piece.PieceKind, Integer> entry : counts.entrySet()) {
                Piece.PieceKind kind = entry.getKey();
                int count = entry.getValue();

                int col = slot % 6; // 한 줄에 6개씩
                int row = slot / 6;
                // 중앙 정렬 오프셋 1.75 ( (16 - (5*2.5)) / 2 )
                double slotX = boardOrigin.getX() + col * 2.5 + 1.75;
                // 백(0)은 남쪽으로(-), 흑(1)은 북쪽으로(+) 줄을 늘림. 줄 간격 3.0
                double rowZ = pocketZ + (isWhite ? -row * 3.0 : row * 3.0);

                boolean isSelected = isCurrentPlayer && (slot == selectedPocketIndex);

                // 아이템 디스플레이
                DisplayEntity.ItemDisplayEntity itemDisplay = new DisplayEntity.ItemDisplayEntity(
                        EntityType.ITEM_DISPLAY, world);
                itemDisplay.addCommandTag("sc_pocket");
                itemDisplay.addCommandTag("sc_game_" + activeGameId);

                // 커스텀 모델이 입혀진 막대기 ItemStack 설정
                itemDisplay.setItemStack(getPieceItemForKind(kind, isWhite));

                double blockY = isSelected ? y + 0.3 : y;
                // 아이템 디스플레이는 중심점 기준이 블록과 다를 수 있으니 좌표 미세조정 필요
                itemDisplay.refreshPositionAndAngles(slotX, blockY + 0.5, rowZ, 0, 0);

                world.spawnEntity(itemDisplay);
                playerUuids.add(itemDisplay.getUuid());

                // 수량 텍스트 (선택 시 황금색 강조)
                DisplayEntity.TextDisplayEntity countDisplay = new DisplayEntity.TextDisplayEntity(
                        EntityType.TEXT_DISPLAY, world);
                countDisplay.addCommandTag("sc_pocket");
                countDisplay.addCommandTag("sc_game_" + activeGameId);
                countDisplay.refreshPositionAndAngles(slotX, blockY + 2.2, rowZ, 0, 0);
                String color = isSelected ? "§6§l" : (isWhite ? "§f" : "§7");
                String label = color + kind.name() + " ×" + count + (isSelected ? " §e◀" : "");
                countDisplay.setText(Text.literal(label));
                countDisplay.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
                world.spawnEntity(countDisplay);
                playerUuids.add(countDisplay.getUuid());

                slot++;
            }
        }
    }

    private Map<Piece.PieceKind, Integer> getGroupedPocket(int player) {
        List<Piece.PieceSpec> pocket = engine.getPocket(activeGameId, player);
        Map<Piece.PieceKind, Integer> counts = new LinkedHashMap<>();
        for (Piece.PieceSpec spec : pocket) {
            counts.put(spec.kind, counts.getOrDefault(spec.kind, 0) + 1);
        }
        return counts;
    }

    private int getPocketScore(int player) {
        if (activeGameId == null)
            return 0;
        List<Piece.PieceSpec> pocket = engine.getPocket(activeGameId, player);
        int totalScore = 0;
        for (Piece.PieceSpec spec : pocket) {
            totalScore += spec.score();
        }
        return totalScore;
    }

    public void cyclePocketSelection(ServerPlayerEntity player) {
        if (activeGameId == null)
            return;
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
            player.sendMessage(Text.literal("§ePocket Selection: §l" + kind.name() + " §r(x" + count + ") ("
                    + (selectedPocketIndex + 1) + "/" + uniqueKinds.size() + ")"), false);
        }
    }

    /**
     * drop_tool로 포켓 표시 영역을 클릭했을 때 해당 슬롯의 기물을 선택한다.
     * 백 포켓: dz ∈ [-3, -1], 흑 포켓: dz ∈ [16, 18]
     *
     * @return 포켓 영역 클릭이면 true (처리됨), 아니면 false (보드 클릭으로 처리 위임)
     */
    public boolean handlePocketClick(BlockPos clickedPos, ServerPlayerEntity player) {
        if (activeGameId == null || boardOrigin == null)
            return false;

        int dx = clickedPos.getX() - boardOrigin.getX();
        int dz = clickedPos.getZ() - boardOrigin.getZ();

        // 포켓 영역 판별 (3줄 지원)
        // 백 포켓: dz ∈ [-10, -1], 흑 포켓: dz ∈ [16, 25]
        boolean isWhitePocket = dz >= -10 && dz <= -1;
        boolean isBlackPocket = dz >= 16 && dz <= 25;
        if (!isWhitePocket && !isBlackPocket)
            return false;
        // 가로 대칭 0~16블록 범위 (16블록 보드 너비와 동일하게 맞춤)
        if (dx < 0 || dx >= 16)
            return false;

        int clickedPlayer = isWhitePocket ? 0 : 1;
        int currentPlayer = engine.getCurrentPlayer(activeGameId);

        if (clickedPlayer != currentPlayer) {
            player.sendMessage(Text.literal("§cNot your turn!"), false);
            return true;
        }

        // col 계산: dx 1.75를 기준으로 2.5씩 간격 (dx - (1.75 - 1.25)) / 2.5
        int col = (int) ((dx - 0.5) / 2.5);
        if (col < 0)
            col = 0;
        if (col >= 6)
            col = 5;

        int row;
        if (isWhitePocket) {
            // Row 0: dz -2, Row 1: dz -5, Row 2: dz -8, Row 3: dz -11
            row = (Math.abs(dz - (-2)) + 1) / 3;
        } else {
            // Row 0: dz 17, Row 1: dz 20, Row 2: dz 23, Row 3: dz 26
            row = (Math.abs(dz - 17) + 1) / 3;
        }

        int slot = row * 6 + col;
        Map<Piece.PieceKind, Integer> counts = getGroupedPocket(currentPlayer);
        List<Piece.PieceKind> uniqueKinds = new ArrayList<>(counts.keySet());

        if (uniqueKinds.isEmpty()) {
            player.sendMessage(Text.literal("§cPocket is empty!"), false);
            return true;
        }
        if (slot >= uniqueKinds.size()) {
            // 슬롯에 기물이 없으면 선택 해제
            selectedPocketIndex = -1;
            player.sendMessage(Text.literal("§7Pocket Selection: None"), false);
            syncPocketDisplays(player.getServerWorld());
            return true;
        }

        selectedPocketIndex = slot;
        Piece.PieceKind kind = uniqueKinds.get(slot);
        int count = counts.get(kind);

        // 포켓 디스플레이 갱신 (선택 표시)
        syncPocketDisplays(player.getServerWorld());
        return true;
    }

    public void addPieceToPocket(ServerPlayerEntity player, Piece.PieceKind kind) {
        if (activeGameId == null || boardOrigin == null) {
            player.sendMessage(Text.literal("§cNo active game."), false);
            return;
        }
        boolean isWhite = player.getZ() < (double) boardOrigin.getZ() + 7.5;
        int playerSide = isWhite ? 0 : 1;

        final int MAX_POCKET_SCORE = 39;
        int currentScore = getPocketScore(playerSide);
        if (currentScore + kind.score() > MAX_POCKET_SCORE) {
            player.sendMessage(Text.literal("§cCannot add " + kind.name() + ". Total pocket score would exceed "
                    + MAX_POCKET_SCORE + " (Current: " + currentScore + ", Adding: " + kind.score() + ")."), false);
            return;
        }

        engine.addPieceToPocket(activeGameId, playerSide, kind);
        syncPocketDisplays(player.getServerWorld());
        player.sendMessage(Text.literal("§aAdded " + kind.name() + " to " + (isWhite ? "White" : "Black")
                + " pocket. Current score: " + (currentScore + kind.score())), false);

        String prefix = playerSide == 0 ? "w:" : "b:";
        moveHistory.add(prefix + abbrev(kind) + "+");
        saveGameLog(null);
        saveSnapshot(null);
    }

    public void removePieceFromPocket(ServerPlayerEntity player, Piece.PieceKind kind) {
        if (activeGameId == null || boardOrigin == null) {
            player.sendMessage(Text.literal("§cNo active game."), false);
            return;
        }
        // 플레이어의 Z 좌표를 보드 중심(Origin+8.0)과 비교하여 색상 결정
        boolean isWhite = player.getZ() < (double) boardOrigin.getZ() + 7.5;
        int playerSide = isWhite ? 0 : 1;

        if (engine.removePieceFromPocket(activeGameId, playerSide, kind)) {
            syncPocketDisplays(player.getServerWorld());
            player.sendMessage(
                    Text.literal("§eRemoved " + kind.name() + " from " + (isWhite ? "White" : "Black") + " pocket."),
                    false);

            String prefix = playerSide == 0 ? "w:" : "b:";
            moveHistory.add(prefix + abbrev(kind) + "-");
            saveGameLog(null);
            saveSnapshot(null);
        } else {
            player.sendMessage(
                    Text.literal("§c" + kind.name() + " not found in " + (isWhite ? "White" : "Black") + " pocket."),
                    false);
        }
    }

    public void handlePlaceInteraction(BlockPos clickedPos, ServerPlayerEntity player) {
        if (activeGameId == null || boardOrigin == null) {
            player.sendMessage(Text.literal("§cNo active game."), false);
            return;
        }
        if (!paze) {
            player.sendMessage(Text.literal("§cCan't drop."), false);
            return;
        }
        int dx = clickedPos.getX() - boardOrigin.getX();
        int dz = clickedPos.getZ() - boardOrigin.getZ();
        if (dx < 0 || dx >= 16 || dz < 0 || dz >= 16)
            return;
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

                    String prefix = currentPlayer == 0 ? "w:" : "b:";
                    moveHistory.add(prefix + abbrev(kind) + "@" + new Move.Square(boardX, boardY).toNotation());
                    saveGameLog(null);
                    saveSnapshot(null);

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
        if (activeGameId == null || boardOrigin == null)
            return;
        if (!paze) {
            player.sendMessage(Text.literal("§cCan't move."), false);
            return;
        }
        int dx = clickedPos.getX() - boardOrigin.getX();
        int dz = clickedPos.getZ() - boardOrigin.getZ();
        if (dx < 0 || dx >= 16 || dz < 0 || dz >= 16)
            return;
        int boardX = dx / 2;
        int boardY = dz / 2;

        if (selectedSquare == null) {
            Piece.PieceData piece = engine.getPieceAt(activeGameId, boardX, boardY);
            if (piece != null && piece.owner == engine.getCurrentPlayer(activeGameId)) {
                selectedSquare = new int[] { boardX, boardY };
                currentLegalMoves = engine.getLegalMoves(activeGameId, boardX, boardY);
                player.sendMessage(
                        Text.literal(
                                "§eSelected §l" + piece.kind.name() + " §7(" + currentLegalMoves.size() + " moves)"),
                        false);
            }
        } else {
            if (selectedSquare[0] == boardX && selectedSquare[1] == boardY) {
                selectedSquare = null;
                currentLegalMoves.clear();
                player.sendMessage(Text.literal("§7Deselected"), false);
            } else {
                try {
                    Piece.PieceData piece = engine.getPieceAt(activeGameId, selectedSquare[0], selectedSquare[1]);
                    if (piece != null) {
                        double startX = boardOrigin.getX() + selectedSquare[0] * 2 + 1.0;
                        double startZ = boardOrigin.getZ() + selectedSquare[1] * 2 + 1.0;
                        double startY = boardOrigin.getY() + 1.0;

                        // CATCH 여부를 이동 실행 전에 미리 확인
                        Move.Square toSq = new Move.Square(boardX, boardY);
                        boolean isCatch = currentLegalMoves.stream()
                                .anyMatch(lm -> lm.to.equals(toSq)
                                        && lm.moveType == nand.modid.chess.dsl.chessembly.AST.MoveType.CATCH);

                        engine.makeMove(activeGameId, selectedSquare[0], selectedSquare[1], boardX, boardY);

                        // CATCH는 기물이 제자리에 머무므로 애니메이션 종착지 = 출발지
                        double endX = isCatch ? startX : boardOrigin.getX() + boardX * 2 + 1.0;
                        double endZ = isCatch ? startZ : boardOrigin.getZ() + boardY * 2 + 1.0;
                        double endY = boardOrigin.getY() + 1.0;

                        MoveAnimation anim = new MoveAnimation();
                        anim.gameId = activeGameId;
                        anim.pieceId = piece.id;
                        anim.startX = startX;
                        anim.startY = startY;
                        anim.startZ = startZ;
                        anim.endX = endX;
                        anim.endY = endY;
                        anim.endZ = endZ;

                        // Calculate yaw (horizontal rotation) to face the destination
                        double adx = endX - startX;
                        double adz = endZ - startZ;
                        anim.yaw = (float) Math.toDegrees(Math.atan2(-adx, adz));

                        anim.currentTick = 0;

                        // Increase detection range to 20 blocks so player is almost always picked up
                        if (player.getPos().distanceTo(new Vec3d(startX, startY, startZ)) < 20.0) {
                            anim.playerUuid = player.getUuid();
                        }
                        activeAnimations.put(piece.id, anim);
                        player.sendMessage(Text.literal("§7(Animating piece: " + piece.effectiveKind().name() + ")"),
                                false);

                        String prefix = piece.owner == 0 ? "w:" : "b:";
                        String moveStr = prefix + abbrev(piece.effectiveKind())
                                + new Move.Square(selectedSquare[0], selectedSquare[1]).toNotation() + ">"
                                + toSq.toNotation();
                        moveHistory.add(moveStr);
                        saveGameLog(null);
                        saveSnapshot(null);
                    }
                    player.sendMessage(Text.literal("§aMoved"), false);
                } catch (Exception e) {
                    player.sendMessage(Text.literal("§c" + e.getMessage()), false);
                }
                selectedSquare = null;
                currentLegalMoves.clear();
            }
        }
        syncAllPieces(player.getServerWorld());
        checkGameResult(player);
    }

    private void checkGameResult(ServerPlayerEntity player) {
        if (activeGameId == null)
            return;
        Move.GameResult result = engine.getGameResult(activeGameId);
        if (result != Move.GameResult.ONGOING) {
            player.sendMessage(Text.literal("§6§lGAME OVER: " + result), false);
            // We keep activeGameId so resetGame() can still clean up entities.
        }
    }

    public void tick(MinecraftServer server) {
        // Show particles for legal moves if a piece is selected
        if (selectedSquare != null && !currentLegalMoves.isEmpty() && boardOrigin != null) {
            ServerWorld world = server.getOverworld(); // Defaulting to overworld for particles
            // Find world if possible, or just use first world
            for (ServerWorld w : server.getWorlds()) {
                for (Move.LegalMove lm : currentLegalMoves) {
                    double px = boardOrigin.getX() + lm.to.x * 2 + 1.0;
                    double pz = boardOrigin.getZ() + lm.to.y * 2 + 1.0;
                    double py = boardOrigin.getY() + 1.2;

                    // Show a few particles at each legal move location
                    w.spawnParticles(ParticleTypes.END_ROD, px, py, pz, 1, 0.1, 0.1, 0.1, 0.05);
                }
            }
        }

        if (activeAnimations.isEmpty())
            return;

        List<String> finished = new ArrayList<>();
        for (MoveAnimation anim : activeAnimations.values()) {
            anim.currentTick++;
            double t = (double) anim.currentTick / 20.0; // 1 second (20 ticks), linear

            double x = anim.startX + (anim.endX - anim.startX) * t;
            double y = anim.startY + (anim.endY - anim.startY) * t;
            double z = anim.startZ + (anim.endZ - anim.startZ) * t;

            double hop = Math.sin(t * Math.PI) * 1.0;
            double curY = y + hop;

            ServerPlayerEntity rider = anim.playerUuid != null ? server.getPlayerManager().getPlayer(anim.playerUuid)
                    : null;
            if (rider != null) {
                // First tick: Set to 3rd person view
                if (anim.currentTick == 1) {
                    ServerPlayNetworking.send(rider, new StasisChess.PerspectivePacketPayload(1)); // 3rd Person Back
                }

                // Teleport rider slightly above the piece to simulate riding
                // Face the movement direction (anim.yaw) and look down slightly (25 pitch)
                rider.teleport(rider.getServerWorld(), x, curY + 1.2, z,
                        Collections.emptySet(),
                        anim.yaw, 25.0f);

                // Keep the rider looking forward/down at the board or in the movement direction
                // (Already handled by keeping X_ROT/Y_ROT flags)
            }

            Map<String, List<UUID>> gamePieces = pieceEntities.get(anim.gameId);
            List<UUID> uuids = gamePieces != null ? gamePieces.get(anim.pieceId) : null;
            if (uuids != null) {
                for (ServerWorld world : server.getWorlds()) {
                    boolean foundAny = false;
                    for (UUID uuid : uuids) {
                        Entity e = world.getEntity(uuid);
                        if (e instanceof DisplayEntity de) {
                            de.setTeleportDuration(1);
                            de.refreshPositionAndAngles(x - (de instanceof DisplayEntity.BlockDisplayEntity ? 0.5 : 0),
                                    curY + (de instanceof DisplayEntity.TextDisplayEntity ? 2.7 : 0.5),
                                    z - (de instanceof DisplayEntity.BlockDisplayEntity ? 0.5 : 0), 0, 0);
                            foundAny = true;
                        }
                    }
                    if (foundAny)
                        break;
                }
            }

            if (anim.currentTick >= 20) {
                finished.add(anim.pieceId);
                // End of animation: Restore 1st person view
                if (rider != null) {
                    ServerPlayNetworking.send(rider, new StasisChess.PerspectivePacketPayload(0)); // 1st Person
                }
            }
        }

        for (String id : finished) {
            activeAnimations.remove(id);
        }
    }

    public void handleInteraction(BlockPos clickedPos, ServerPlayerEntity player) {
        if (selectedPocketIndex >= 0)
            handlePlaceInteraction(clickedPos, player);
        else
            handleMoveInteraction(clickedPos, player);
    }

    public void saveArea(ServerWorld world, BlockPos origin, int minX, int maxX, int minY, int maxY, int minZ,
            int maxZ) {
        // If there's an active game, reset it (and restore its blocks) before saving
        // the new area
        // This prevents the chess board itself from being saved as the 'original' state
        // if the player creates a new board while one is already active.
        // We use a temporary flag or check activeGameId.
        // However, resetGame is called by startNewGame too.

        // Logical flow should be:
        // 1. User clicks start_tool.
        // 2. start_tool calls resetGame(player) -> restores old blocks.
        // 3. start_tool calls saveArea(...) -> saves current (restored) blocks.
        // 4. start_tool places new blocks.
        // 5. start_tool calls startNewGame(...) -> which currently calls resetGame
        // again.

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
        if (savedBlocks.isEmpty())
            return;
        for (Map.Entry<BlockPos, BlockState> entry : savedBlocks.entrySet()) {
            world.setBlockState(entry.getKey(), entry.getValue());
        }
        savedBlocks.clear();
    }

    public void resetGame(ServerPlayerEntity player) {
        if (player == null)
            return;

        player.sendMessage(Text.literal("§c[StasisChess] 보드 삭제 및 초기화 중..."), false);

        ServerWorld world = player.getServerWorld();
        var server = player.getServer();

        // 1. Clear all chess-related entities in one pass across all worlds
        if (server != null) {
            for (ServerWorld w : server.getWorlds()) {
                for (Entity e : w.iterateEntities()) {
                    Set<String> tags = e.getCommandTags();
                    if (!tags.isEmpty()) {
                        for (String tag : tags) {
                            if (tag.startsWith("sc_game_") || tag.startsWith("sc_piece_") ||
                                    tag.startsWith("sc_pocket") || tag.startsWith("sc_status")) {
                                e.discard();
                                break; // Found a matching tag, move to next entity
                            }
                        }
                    }
                }
            }
        }

        // 2. Explicitly clear all internal entity tracking maps
        pieceEntities.clear();
        pocketEntities.clear();
        statusEntity = null;
        activeAnimations.clear();

        // 3. Restore blocks
        restoreArea(world);

        // 4. Reset state variables
        this.activeGameId = null;
        this.boardOrigin = null;
        this.selectedSquare = null;
        this.currentLegalMoves.clear();
        this.selectedPocketIndex = -1;

        player.sendMessage(Text.literal("§c[StasisChess] Game and Board Reset!"), false);
    }

    public void endTurn(ServerPlayerEntity player) {
        if (activeGameId == null)
            return;
        try {
            int currentPlayer = engine.getCurrentPlayer(activeGameId);
            String prefix = currentPlayer == 0 ? "w:" : "b:";
            moveHistory.add(prefix + "END");
            saveGameLog(null);
            saveSnapshot(null);

            engine.endTurn(activeGameId);
            if (engine.getCurrentPlayer(activeGameId) == 1) {
                this.paze = true;
            }
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

    /**
     * /chess debugmode 명령어: Chessembly 인터프리터의 디버그 모드를 토글한다.
     * 활성화 시 합법 수 계산마다 서버 콘솔에 실행 추적 로그가 출력된다.
     */
    public void toggleDebugMode(ServerPlayerEntity player) {
        if (activeGameId == null) {
            send(player, "§cNo active game.");
            return;
        }
        GameState state = engine.getGame(activeGameId);
        boolean next = !state.isDebugMode();
        state.setDebugMode(next);
        if (next) {
            // 플레이어 UUID만 보관 → 서버 틱에서 안전하게 조회
            java.util.UUID playerUuid = player.getUuid();
            state.setDebugLogger(msg -> {
                net.minecraft.server.MinecraftServer server = player.getServer();
                if (server == null)
                    return;
                net.minecraft.server.network.ServerPlayerEntity target = server.getPlayerManager()
                        .getPlayer(playerUuid);
                if (target != null) {
                    target.sendMessage(Text.literal("§8[dbg] §7" + msg), false);
                }
            });
            send(player, "§a[StasisChess] Chessembly debug mode §l§aON§r§a — 인게임 채팅으로 실행 추적이 출력됩니다.");
        } else {
            state.setDebugLogger(null);
            send(player, "§c[StasisChess] Chessembly debug mode §l§cOFF§r§c.");
        }
    }

    /**
     * /chess debug 명령어: 현재 엔진 내부의 모든 값을 플레이어 채팅에 출력한다.
     */
    public void showEngineState(ServerPlayerEntity player) {
        send(player, "§b§l========== ENGINE DEBUG STATE ==========");

        if (activeGameId == null) {
            send(player, "§c  No active game.");
            send(player, "§b§l=========================================");
            return;
        }

        nand.modid.chess.core.GameState state = engine.getGame(activeGameId);

        // ── 기본 정보 ─────────────────────────────────────
        send(player, "§e§lBasic Info");
        send(player, String.format("§7  Game ID       : §f%s", activeGameId));
        send(player, String.format("§7  Turn          : §f%s (%d)",
                state.getTurn() == 0 ? "§fWhite" : "§7Black", state.getTurn()));
        send(player, String.format("§7  Action Taken  : §f%b", state.isActionTaken()));
        String ap = state.getActivePiece();
        send(player, String.format("§7  Active Piece  : §f%s", ap != null ? ap : "none"));
        send(player, String.format("§7  Game Result   : §f%s", engine.getGameResult(activeGameId)));
        send(player, String.format("§7  Debug Mode    : §f%b", state.isDebugMode()));

        // ── 보드 기물 ──────────────────────────────────────
        send(player, "§e§lBoard Pieces (" + state.getBoardPieces().size() + ")");
        List<Piece.PieceData> boardPieces = new ArrayList<>(state.getBoardPieces());
        boardPieces.sort(Comparator.comparingInt((Piece.PieceData p) -> p.owner)
                .thenComparing(p -> p.pos == null ? "" : p.pos.toNotation()));
        for (Piece.PieceData p : boardPieces) {
            StringBuilder sb = new StringBuilder();
            sb.append(p.owner == 0 ? "§f" : "§7");
            if (p.isRoyal)
                sb.append("§6★ ");
            sb.append(p.kind.name());
            if (p.disguise != null)
                sb.append("§d(disguised as ").append(p.disguise.name()).append(")§r");
            sb.append(" @§b").append(p.pos != null ? p.pos.toNotation() : "?");
            sb.append("§7  ms=§a").append(p.moveStack);
            if (p.stun > 0)
                sb.append(" §cSTUN=").append(p.stun);
            if (p.isRoyal)
                sb.append(" §6ROYAL");
            sb.append("  §8[").append(p.id).append("]");
            send(player, "  " + sb);
        }

        // ── 보드 시각화 ────────────────────────────────────
        send(player, "§e§lBoard (8x8)");
        send(player, "§7  y\\x  a  b  c  d  e  f  g  h");
        for (int y = 7; y >= 0; y--) {
            StringBuilder row = new StringBuilder("§7  ").append(y + 1).append("  ");
            for (int x = 0; x < 8; x++) {
                Piece.PieceData p = state.getPieceAt(new Move.Square(x, y));
                if (p == null) {
                    row.append("§8. ");
                } else {
                    String abbr = abbrev(p.effectiveKind());
                    row.append(p.owner == 0 ? "§f" : "§7").append(abbr).append(" ");
                }
            }
            send(player, row.toString());
        }

        // ── 포켓 ──────────────────────────────────────────
        for (int pl = 0; pl < 2; pl++) {
            List<Piece.PieceSpec> pocket = state.getPocket(pl);
            String title = pl == 0 ? "§fWhite Pocket" : "§7Black Pocket";
            send(player, "§e§l" + title + " (" + pocket.size() + " pieces)");
            if (pocket.isEmpty()) {
                send(player, "  §8(empty)");
            } else {
                // 종류별 집계
                Map<Piece.PieceKind, Integer> counts = new LinkedHashMap<>();
                for (Piece.PieceSpec spec : pocket) {
                    counts.merge(spec.kind, 1, Integer::sum);
                }
                StringBuilder sb = new StringBuilder("  ");
                for (Map.Entry<Piece.PieceKind, Integer> e : counts.entrySet()) {
                    sb.append(e.getKey().name()).append("×").append(e.getValue()).append("  ");
                }
                send(player, sb.toString());
                // 총 점수
                int total = pocket.stream().mapToInt(Piece.PieceSpec::score).sum();
                send(player, String.format("  §7Total score: §a%d", total));
            }
        }

        // ── 이번 턴 행동 ───────────────────────────────────
        List<Move.Action> actions = state.getTurnActions();
        send(player, "§e§lTurn Actions (" + actions.size() + ")");
        if (actions.isEmpty()) {
            send(player, "  §8(none)");
        } else {
            for (Move.Action a : actions) {
                String msg = switch (a.type) {
                    case PLACE -> String.format("PLACE %s → %s", a.pieceId, a.to);
                    case MOVE -> String.format("MOVE  %s: %s → %s", a.pieceId, a.from, a.to);
                    case CROWN -> String.format("CROWN %s", a.pieceId);
                    case DISGUISE -> String.format("DISGUISE %s as %s", a.pieceId, a.asKind);
                    case STUN -> String.format("STUN %s x%d", a.pieceId, a.stunAmount);
                };
                send(player, "  §7- " + msg);
            }
        }

        // ── 전역 상태 ──────────────────────────────────────
        Map<String, Integer> globalState = state.getGlobalState();
        send(player, "§e§lGlobal State (" + globalState.size() + " entries)");
        if (globalState.isEmpty()) {
            send(player, "  §8(empty)");
        } else {
            for (Map.Entry<String, Integer> e : globalState.entrySet()) {
                send(player, String.format("  §7%s = §f%d", e.getKey(), e.getValue()));
            }
        }

        send(player, "§b§l=========================================");
    }

    /** 기물 종류의 2자리 약어 반환 */
    private static String abbrev(Piece.PieceKind kind) {
        return switch (kind) {
            case PAWN -> "P";
            case KING -> "K";
            case QUEEN -> "Q";
            case ROOK -> "R";
            case BISHOP -> "B";
            case KNIGHT -> "N";
            case AMAZON -> "A";
            case GRASSHOPPER -> "G";
            case KNIGHTRIDER -> "Nr";
            case ARCHBISHOP -> "Ar";
            case DABBABA -> "D";
            case ALFIL -> "Al";
            case FERZ -> "F";
            case CENTAUR -> "Ct";
            case CAMEL -> "Cm";
            case TEMPEST_ROOK -> "Tr";
            case CANNON -> "Cn";
            case BOUNCING_BISHOP -> "Bb";
            case EXPERIMENT -> "Ex";
            case CUSTOM -> "Cu";
            // 중립기물 — 회색 약어 사용
            case NEUTRAL_SENTINEL -> "nSe";
            case NEUTRAL_PYLON    -> "nPy";
            case NEUTRAL_WANDERER -> "nWa";
        };
    }

    /** 플레이어에게 채팅 메시지 전송 헬퍼 */
    private void send(ServerPlayerEntity player, String msg) {
        player.sendMessage(Text.literal(msg), false);
    }

    public void saveGame(String customName, ServerPlayerEntity player) {
        if (activeGameId == null) {
            send(player, "§cNo active game to save.");
            return;
        }
        String saveId = (customName == null || customName.isEmpty()) ? activeGameId : customName;
        saveGameLog(saveId);
        saveSnapshot(saveId);
        send(player, "§aGame saved as: " + saveId);
    }

    private void saveGameLog(String customId) {
        if (activeGameId == null)
            return;
        try {
            String saveId = (customId == null) ? activeGameId : customId;
            java.nio.file.Path logDir = java.nio.file.Paths.get("mods", "stasischess", "logs");
            java.nio.file.Files.createDirectories(logDir);
            java.nio.file.Path logFile = logDir.resolve(saveId + ".txt");

            StringBuilder sb = new StringBuilder();

            // White Pocket
            sb.append("w pocket : ");
            List<Piece.PieceSpec> wPocket = engine.getPocket(activeGameId, 0);
            for (int i = 0; i < wPocket.size(); i++) {
                sb.append(wPocket.get(i).kind.name());
                if (i < wPocket.size() - 1)
                    sb.append(", ");
            }
            sb.append("\n");

            // Black Pocket
            sb.append("b pocket : ");
            List<Piece.PieceSpec> bPocket = engine.getPocket(activeGameId, 1);
            for (int i = 0; i < bPocket.size(); i++) {
                sb.append(bPocket.get(i).kind.name());
                if (i < bPocket.size() - 1)
                    sb.append(", ");
            }
            sb.append("\n");

            // Move History
            for (int i = 0; i < moveHistory.size(); i++) {
                sb.append(moveHistory.get(i));
                if (i < moveHistory.size() - 1)
                    sb.append("->");
            }
            sb.append("\n");

            java.nio.file.Files.writeString(logFile, sb.toString());
        } catch (Exception e) {
            StasisChess.LOGGER.error("Failed to save chess log: " + e.getMessage());
        }
    }

    private void saveSnapshot(String customId) {
        if (activeGameId == null)
            return;
        try {
            String saveId = (customId == null) ? activeGameId : customId;
            java.nio.file.Path snapshotDir = java.nio.file.Paths.get("mods", "stasischess", "snapshots");
            java.nio.file.Files.createDirectories(snapshotDir);
            java.nio.file.Path snapshotFile = snapshotDir.resolve(saveId + ".txt");

            StringBuilder sb = new StringBuilder();

            GameState state = engine.getGame(activeGameId);

            sb.append("Current Turn: ").append(state.getTurn() == 0 ? "White" : "Black").append("\n\n");

            // Board Visualization (8x8)
            sb.append("Board (8x8):\n");
            sb.append("  y\\x  a  b  c  d  e  f  g  h\n");
            for (int y = 7; y >= 0; y--) {
                sb.append("  ").append(y + 1).append("  ");
                for (int x = 0; x < 8; x++) {
                    Piece.PieceData p = state.getPieceAt(new Move.Square(x, y));
                    if (p == null) {
                        sb.append(".  ");
                    } else {
                        String abbr = abbrev(p.effectiveKind());
                        sb.append(p.owner == 0 ? "w" : "b").append(abbr).append(" ");
                    }
                }
                sb.append("\n");
            }
            sb.append("\n");

            // Pockets
            for (int pl = 0; pl < 2; pl++) {
                List<Piece.PieceSpec> pocket = state.getPocket(pl);
                sb.append(pl == 0 ? "White Pocket: " : "Black Pocket: ");
                for (int i = 0; i < pocket.size(); i++) {
                    sb.append(pocket.get(i).kind.name());
                    if (i < pocket.size() - 1)
                        sb.append(", ");
                }
                sb.append("\n");
            }

            java.nio.file.Files.writeString(snapshotFile, sb.toString());
        } catch (Exception e) {
            StasisChess.LOGGER.error("Failed to save snapshot: " + e.getMessage());
        }
    }

    public void loadGameLog(String logId, ServerPlayerEntity player) {
        if (boardOrigin == null) {
            player.sendMessage(Text.literal("§cSet board origin first (start a new game)."), false);
            return;
        }

        try {
            java.nio.file.Path logFile = java.nio.file.Paths.get("mods", "stasischess", "logs", logId + ".txt");
            if (!java.nio.file.Files.exists(logFile)) {
                player.sendMessage(Text.literal("§cLog file not found: " + logId), false);
                return;
            }

            List<String> lines = java.nio.file.Files.readAllLines(logFile);
            String historyLine = "";
            for (String line : lines) {
                if (!line.startsWith("w pocket") && !line.startsWith("b pocket") && !line.trim().isEmpty()) {
                    historyLine = line;
                    break;
                }
            }

            if (historyLine.isEmpty()) {
                player.sendMessage(Text.literal("§cNo history found in log."), false);
                return;
            }

            // Reset current engine state
            this.activeGameId = engine.createGame(); // Fresh game
            this.moveHistory.clear();
            this.paze = false;
            this.selectedSquare = null;
            this.selectedPocketIndex = -1;

            String[] events = historyLine.split("->");
            for (String event : events) {
                if (event.isEmpty())
                    continue;

                // Format: player:Action
                int colonIdx = event.indexOf(':');
                if (colonIdx == -1)
                    continue;

                String playerSideStr = event.substring(0, colonIdx);
                String action = event.substring(colonIdx + 1);
                int side = playerSideStr.equals("w") ? 0 : 1;

                if (action.equals("END")) {
                    engine.endTurn(activeGameId);
                } else if (action.endsWith("+")) {
                    Piece.PieceKind kind = kindFromAbbrev(action.substring(0, action.length() - 1));
                    engine.addPieceToPocket(activeGameId, side, kind);
                } else if (action.endsWith("-")) {
                    Piece.PieceKind kind = kindFromAbbrev(action.substring(0, action.length() - 1));
                    engine.removePieceFromPocket(activeGameId, side, kind);
                } else if (action.contains("@")) {
                    String[] parts = action.split("@");
                    Piece.PieceKind kind = kindFromAbbrev(parts[0]);
                    Move.Square to = Move.Square.fromNotation(parts[1]);
                    engine.placePiece(activeGameId, kind.name(), to.x, to.y);
                } else if (action.contains(">")) {
                    String[] parts = action.split(">");
                    if (parts.length == 2) {
                        String abbrev = parts[0].substring(0, parts[0].length() - 2);
                        Move.Square from = Move.Square.fromNotation(parts[0].substring(parts[0].length() - 2));
                        Move.Square to = Move.Square.fromNotation(parts[1]);
                        engine.makeMove(activeGameId, from.x, from.y, to.x, to.y);
                    }
                }
                // Re-add to moveHistory so saveGameLog works correctly if we continue
                moveHistory.add(event);
            }

            player.sendMessage(Text.literal("§aGame loaded: " + logId), false);
            syncAllPieces(player.getServerWorld());
            saveSnapshot(null); // Initial snapshot after load

        } catch (Exception e) {
            player.sendMessage(Text.literal("§cLoad failed: " + e.getMessage()), false);
            StasisChess.LOGGER.error("Load failed", e);
        }
    }

    public void listSavedGames(ServerPlayerEntity player) {
        try {
            java.nio.file.Path logDir = java.nio.file.Paths.get("mods", "stasischess", "logs");
            if (!java.nio.file.Files.exists(logDir)) {
                player.sendMessage(Text.literal("§eNo saved games found."), false);
                return;
            }

            try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(logDir)) {
                List<String> games = stream
                        .filter(file -> !java.nio.file.Files.isDirectory(file))
                        .map(file -> file.getFileName().toString())
                        .filter(name -> name.endsWith(".txt"))
                        .map(name -> name.substring(0, name.length() - 4))
                        .collect(java.util.stream.Collectors.toList());

                if (games.isEmpty()) {
                    player.sendMessage(Text.literal("§eNo saved games found."), false);
                } else {
                    player.sendMessage(Text.literal("§6§lSaved Games:"), false);
                    for (String id : games) {
                        player.sendMessage(Text.literal("§7- §f" + id), false);
                    }
                }
            }
        } catch (Exception e) {
            player.sendMessage(Text.literal("§cFailed to list games: " + e.getMessage()), false);
        }
    }

    private Piece.PieceKind kindFromAbbrev(String abbrev) {
        for (Piece.PieceKind kind : Piece.PieceKind.values()) {
            if (abbrev(kind).equals(abbrev))
                return kind;
        }
        return Piece.PieceKind.PAWN; // Default
    }
}
