package com.github.sniffity.virgillib.navigation.pathfinder.node.evaluator;

import com.github.sniffity.virgillib.navigation.VLPathNavigationRegion;
import com.github.sniffity.virgillib.navigation.pathfinder.node.VLNode;
import com.github.sniffity.virgillib.navigation.pathfinder.node.VLTarget;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class VLWalkNodeEvaluator extends VLNodeEvaluator {
    public static final double SPACE_BETWEEN_WALL_POSTS = 0.5;
    private static final double DEFAULT_MOB_JUMP_HEIGHT = 1.125;
    private final Long2ObjectMap<BlockPathTypes> pathTypesByPosCache = new Long2ObjectOpenHashMap<>();
    private final Object2BooleanMap<AABB> collisionCache = new Object2BooleanOpenHashMap<>();

    @Override
    public void prepare(VLPathNavigationRegion pLevel, Mob pMob) {
        super.prepare(pLevel, pMob);
        pMob.onPathfindingStart();
    }

    /**
     * This method is called when all nodes have been processed and PathEntity is created.
     */
    @Override
    public void done() {
        this.mob.onPathfindingDone();
        this.pathTypesByPosCache.clear();
        this.collisionCache.clear();
        super.done();
    }

    @Override
    public VLNode getStart() {
        //Y position start operations...
        //We begin by defining a mutable blockPos and the mob's y Position
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        int i = this.mob.getBlockY();
        //We begin by placing the blockPosition on the mob's starting location
        //We get the blockstate for that position...
        BlockState blockstate = this.level.getBlockState(blockpos$mutableblockpos.set(this.mob.getX(), (double)i, this.mob.getZ()));
        //If the mob is in a column of water, shift the y position to the highest water block
        if (!this.mob.canStandOnFluid(blockstate.getFluidState())) {
            if (this.canFloat() && this.mob.isInWater()) {
                while(true) {
                    if (!blockstate.is(Blocks.WATER) && blockstate.getFluidState() != Fluids.WATER.getSource(false)) {
                        --i;
                        break;
                    }

                    blockstate = this.level.getBlockState(blockpos$mutableblockpos.set(this.mob.getX(), (double)(++i), this.mob.getZ()));
                }

            } else if (this.mob.onGround()) {
                //If the mob is on ground, get the mob's yPosition floored, hence the blockPosition at which the mobi s standing
                i = Mth.floor(this.mob.getY() + 0.5);
            } else {
                //If the mob is midair...
                BlockPos blockpos = this.mob.blockPosition();
                while(
                        (this.level.getBlockState(blockpos).isAir()
                                || this.level.getBlockState(blockpos).isPathfindable(this.level, blockpos, PathComputationType.LAND)
                        ) && blockpos.getY() > this.mob.level().getMinBuildHeight()
                ) {blockpos = blockpos.below();}
                //Set Y to the first non-air block beneath the mob
                i = blockpos.above().getY();
            }
        } else {
            while(this.mob.canStandOnFluid(blockstate.getFluidState())) {
                blockstate = this.level.getBlockState(blockpos$mutableblockpos.set(this.mob.getX(), (double)(++i), this.mob.getZ()));
            }

            --i;
        }

        //Once we have a suitable y Position, find a suitable starting position within the mob's counding box
        BlockPos blockpos1 = this.mob.blockPosition();
        if (!this.canStartAt(blockpos$mutableblockpos.set(blockpos1.getX(), i, blockpos1.getZ()))) {
            AABB aabb = this.mob.getBoundingBox();
            if (this.canStartAt(blockpos$mutableblockpos.set(aabb.minX, (double)i, aabb.minZ))
                    || this.canStartAt(blockpos$mutableblockpos.set(aabb.minX, (double)i, aabb.maxZ))
                    || this.canStartAt(blockpos$mutableblockpos.set(aabb.maxX, (double)i, aabb.minZ))
                    || this.canStartAt(blockpos$mutableblockpos.set(aabb.maxX, (double)i, aabb.maxZ))) {
                return this.getStartNode(blockpos$mutableblockpos);
            }
        }

        return this.getStartNode(new BlockPos(blockpos1.getX(), i, blockpos1.getZ()));
    }


    protected VLNode getStartNode(BlockPos pPos) {
        VLNode node = this.getNode(pPos);
        node.type = this.getBlockPathType(this.mob, node.asBlockPos());
        node.costMalus = this.mob.getPathfindingMalus(node.type);
        return node;
    }

    //check for a starter block...
    //Starter block cannot be open, and must be zero or positive malus...
    protected boolean canStartAt(BlockPos pPos) {
        BlockPathTypes blockpathtypes = this.getBlockPathType(this.mob, pPos);
        return blockpathtypes != BlockPathTypes.OPEN && this.mob.getPathfindingMalus(blockpathtypes) >= 0.0F;
    }

    //function takes X, Y, Z coordinates, floors them, converts them into a Node and then gets a Target from it
    @Override
    public VLTarget getGoal(double pX, double pY, double pZ) {
        return this.getTargetFromNode(this.getNode(Mth.floor(pX), Mth.floor(pY), Mth.floor(pZ)));
    }

    @Override
    public int getNeighbors(VLNode[] pOutputArray, VLNode nodeToEvaluate) {
        int numberOfValidNeighbors = 0;
        int mobStepUpHeight = 0;
        //get the blockPathType for the node position and above the node position
        //this value is cached, hence it might already exist
        //if it doesn't we create it...
        BlockPathTypes blockPathTypeAboveNode = this.getCachedBlockType(this.mob, nodeToEvaluate.x, nodeToEvaluate.y + 1, nodeToEvaluate.z);
        BlockPathTypes blockPathTypeNode = this.getCachedBlockType(this.mob, nodeToEvaluate.x, nodeToEvaluate.y, nodeToEvaluate.z);

        //set the stepHeight for the mob...
        //a minimum of 1 is used for all step heights...
        //mobs cannot step ON TOP of honey
        //mobs can only step up if block they're stepping up INTO is accepted by the Malus
        if (this.mob.getPathfindingMalus(blockPathTypeAboveNode) >= 0.0F && blockPathTypeNode != BlockPathTypes.STICKY_HONEY) {
            mobStepUpHeight = Mth.floor(Math.max(1.0F, this.mob.getStepHeight()));
        }

        //get the FloorPosition
        double nodeFloorLevel = this.getFloorLevel(new BlockPos(nodeToEvaluate.x, nodeToEvaluate.y, nodeToEvaluate.z));

        //Now we begin evaluating the neighboring nodes for the currently evaluated nodes
        //We add each valid neighbor to the outPut Array and increase the number of elements in the array by 1
        //Of note, the neighboring noes are all on the same y level as the currently evaluated node
        VLNode nodeSouth = this.findAcceptedNode(nodeToEvaluate.x, nodeToEvaluate.y, nodeToEvaluate.z + 1, mobStepUpHeight, nodeFloorLevel, Direction.SOUTH, blockPathTypeNode);
        if (this.isNeighborValid(nodeSouth, nodeToEvaluate)) {
            pOutputArray[numberOfValidNeighbors++] = nodeSouth;
        }

        VLNode nodeWest = this.findAcceptedNode(nodeToEvaluate.x - 1, nodeToEvaluate.y, nodeToEvaluate.z, mobStepUpHeight, nodeFloorLevel, Direction.WEST, blockPathTypeNode);
        if (this.isNeighborValid(nodeWest, nodeToEvaluate)) {
            pOutputArray[numberOfValidNeighbors++] = nodeWest;
        }

        VLNode nodeEast = this.findAcceptedNode(nodeToEvaluate.x + 1, nodeToEvaluate.y, nodeToEvaluate.z, mobStepUpHeight, nodeFloorLevel, Direction.EAST, blockPathTypeNode);
        if (this.isNeighborValid(nodeEast, nodeToEvaluate)) {
            pOutputArray[numberOfValidNeighbors++] = nodeEast;
        }

        VLNode nodeNorth = this.findAcceptedNode(nodeToEvaluate.x, nodeToEvaluate.y, nodeToEvaluate.z - 1, mobStepUpHeight, nodeFloorLevel, Direction.NORTH, blockPathTypeNode);
        if (this.isNeighborValid(nodeNorth, nodeToEvaluate)) {
            pOutputArray[numberOfValidNeighbors++] = nodeNorth;
        }

        VLNode nodeNorthWest = this.findAcceptedNode(nodeToEvaluate.x - 1, nodeToEvaluate.y, nodeToEvaluate.z - 1, mobStepUpHeight, nodeFloorLevel, Direction.NORTH, blockPathTypeNode);
        if (this.isDiagonalValid(nodeToEvaluate, nodeWest, nodeNorth, nodeNorthWest)) {
            pOutputArray[numberOfValidNeighbors++] = nodeNorthWest;
        }

        VLNode nodeNorthEast = this.findAcceptedNode(nodeToEvaluate.x + 1, nodeToEvaluate.y, nodeToEvaluate.z - 1, mobStepUpHeight, nodeFloorLevel, Direction.NORTH, blockPathTypeNode);
        if (this.isDiagonalValid(nodeToEvaluate, nodeEast, nodeNorth, nodeNorthEast)) {
            pOutputArray[numberOfValidNeighbors++] = nodeNorthEast;
        }

        VLNode nodeSouthWest = this.findAcceptedNode(nodeToEvaluate.x - 1, nodeToEvaluate.y, nodeToEvaluate.z + 1, mobStepUpHeight, nodeFloorLevel, Direction.SOUTH, blockPathTypeNode);
        if (this.isDiagonalValid(nodeToEvaluate, nodeWest, nodeSouth, nodeSouthWest)) {
            pOutputArray[numberOfValidNeighbors++] = nodeSouthWest;
        }

        VLNode nodeSouthEast = this.findAcceptedNode(nodeToEvaluate.x + 1, nodeToEvaluate.y, nodeToEvaluate.z + 1, mobStepUpHeight, nodeFloorLevel, Direction.SOUTH, blockPathTypeNode);
        if (this.isDiagonalValid(nodeToEvaluate, nodeEast, nodeSouth, nodeSouthEast)) {
            pOutputArray[numberOfValidNeighbors++] = nodeSouthEast;
        }
        return numberOfValidNeighbors;
    }

    protected boolean isNeighborValid(@Nullable VLNode pNeighbor, VLNode pNode) {
        return pNeighbor != null && !pNeighbor.closed && (pNeighbor.costMalus >= 0.0F || pNode.costMalus < 0.0F);
    }

    protected boolean isDiagonalValid(VLNode pRoot, @Nullable VLNode pXNode, @Nullable VLNode pZNode, @Nullable VLNode pDiagonal) {
        if (pDiagonal == null || pZNode == null || pXNode == null) {
            return false;
        } else if (pDiagonal.closed) {
            return false;
        } else if (pZNode.y > pRoot.y || pXNode.y > pRoot.y) {
            return false;
        } else if (pXNode.type != BlockPathTypes.WALKABLE_DOOR
                && pZNode.type != BlockPathTypes.WALKABLE_DOOR
                && pDiagonal.type != BlockPathTypes.WALKABLE_DOOR) {
            boolean flag = pZNode.type == BlockPathTypes.FENCE && pXNode.type == BlockPathTypes.FENCE && (double)this.mob.getBbWidth() < 0.5;
            return pDiagonal.costMalus >= 0.0F
                    && (pZNode.y < pRoot.y || pZNode.costMalus >= 0.0F || flag)
                    && (pXNode.y < pRoot.y || pXNode.costMalus >= 0.0F || flag);
        } else {
            return false;
        }
    }

    private static boolean doesBlockHavePartialCollision(BlockPathTypes pBlockPathType) {
        return pBlockPathType == BlockPathTypes.FENCE || pBlockPathType == BlockPathTypes.DOOR_WOOD_CLOSED || pBlockPathType == BlockPathTypes.DOOR_IRON_CLOSED;
    }

    private boolean canReachWithoutCollision(VLNode pNode) {
        AABB aabb = this.mob.getBoundingBox();
        Vec3 vec3 = new Vec3(
                (double)pNode.x - this.mob.getX() + aabb.getXsize() / 2.0,
                (double)pNode.y - this.mob.getY() + aabb.getYsize() / 2.0,
                (double)pNode.z - this.mob.getZ() + aabb.getZsize() / 2.0
        );
        int i = Mth.ceil(vec3.length() / aabb.getSize());
        vec3 = vec3.scale((double)(1.0F / (float)i));

        for(int j = 1; j <= i; ++j) {
            aabb = aabb.move(vec3);
            if (this.hasCollisions(aabb)) {
                return false;
            }
        }

        return true;
    }

    protected double getFloorLevel(BlockPos pPos) {
        //the FloorLevel for the BlockPos (Node) is either Y+0.5 in water
        // Or, Y+ the block height of the block below for solid blocks
        return (this.canFloat() || this.isAmphibious()) && this.level.getFluidState(pPos).is(FluidTags.WATER)
                ? (double)pPos.getY() + 0.5
                : getFloorLevel(this.level, pPos);
    }

    public static double getFloorLevel(BlockGetter pLevel, BlockPos pPos) {
        BlockPos blockpos = pPos.below();
        VoxelShape voxelshape = pLevel.getBlockState(blockpos).getCollisionShape(pLevel, blockpos);
        return (double)blockpos.getY() + (voxelshape.isEmpty() ? 0.0 : voxelshape.max(Direction.Axis.Y));
    }

    protected boolean isAmphibious() {
        return false;
    }

    @Nullable
    protected VLNode findAcceptedNode(int pX, int pY, int pZ, int pVerticalDeltaLimit, double pNodeFloorLevel, Direction pDirection, BlockPathTypes pPathType) {
        VLNode node = null;
        //Define a mutable BlockPos
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        //set the BlockPos mutable to the 's blockPos and get its floor level
        double d0 = this.getFloorLevel(blockpos$mutableblockpos.set(pX, pY, pZ));
        //Ensure the mob can step up into the node, based on its jump height
        //The neighboring node's height shouldn't be greater than the mob's step up height
        if (d0 - pNodeFloorLevel > this.getMobJumpHeight()) {
            return null;
        } else {
            //get the BlockPathType for the node being evaluated...
            //if it already exists, retrieve value, else, create value
            BlockPathTypes blockpathtypes = this.getCachedBlockType(this.mob, pX, pY, pZ);
            float nodeMalus = this.mob.getPathfindingMalus(blockpathtypes);
            double d1 = (double)this.mob.getBbWidth() / 2.0;

            //If the block is pathFindable, update its cost BlockPathType and its malus...
            if (nodeMalus >= 0.0F) {
                node = this.getNodeAndUpdateCostToMax(pX, pY, pZ, blockpathtypes, nodeMalus);
            }

            //ToDo: We are here
            //ToDo: We are here
            //ToDo: We are here
            //ToDo: We are here
            //ToDo: We are here
            //null the node if...
            if (doesBlockHavePartialCollision(pPathType) && node != null && node.costMalus >= 0.0F && !this.canReachWithoutCollision(node)) {
                node = null;
            }

            
            if (blockpathtypes != BlockPathTypes.WALKABLE && (!this.isAmphibious() || blockpathtypes != BlockPathTypes.WATER)) {
                if ((node == null || node.costMalus < 0.0F)
                        && pVerticalDeltaLimit > 0
                        && (blockpathtypes != BlockPathTypes.FENCE || this.canWalkOverFences())
                        && blockpathtypes != BlockPathTypes.UNPASSABLE_RAIL
                        && blockpathtypes != BlockPathTypes.TRAPDOOR
                        && blockpathtypes != BlockPathTypes.POWDER_SNOW) {
                    node = this.findAcceptedNode(pX, pY + 1, pZ, pVerticalDeltaLimit - 1, pNodeFloorLevel, pDirection, pPathType);
                    if (node != null && (node.type == BlockPathTypes.OPEN || node.type == BlockPathTypes.WALKABLE) && this.mob.getBbWidth() < 1.0F) {
                        double d2 = (double)(pX - pDirection.getStepX()) + 0.5;
                        double d3 = (double)(pZ - pDirection.getStepZ()) + 0.5;
                        AABB aabb = new AABB(
                                d2 - d1,
                                this.getFloorLevel(blockpos$mutableblockpos.set(d2, (double)(pY + 1), d3)) + 0.001,
                                d3 - d1,
                                d2 + d1,
                                (double)this.mob.getBbHeight()
                                        + this.getFloorLevel(blockpos$mutableblockpos.set((double)node.x, (double)node.y, (double)node.z))
                                        - 0.002,
                                d3 + d1
                        );
                        if (this.hasCollisions(aabb)) {
                            node = null;
                        }
                    }
                }

                if (!this.isAmphibious() && blockpathtypes == BlockPathTypes.WATER && !this.canFloat()) {
                    if (this.getCachedBlockType(this.mob, pX, pY - 1, pZ) != BlockPathTypes.WATER) {
                        return node;
                    }

                    while(pY > this.mob.level().getMinBuildHeight()) {
                        blockpathtypes = this.getCachedBlockType(this.mob, pX, --pY, pZ);
                        if (blockpathtypes != BlockPathTypes.WATER) {
                            return node;
                        }

                        node = this.getNodeAndUpdateCostToMax(pX, pY, pZ, blockpathtypes, this.mob.getPathfindingMalus(blockpathtypes));
                    }
                }

                if (blockpathtypes == BlockPathTypes.OPEN) {
                    int j = 0;
                    int i = pY;

                    while(blockpathtypes == BlockPathTypes.OPEN) {
                        if (--pY < this.mob.level().getMinBuildHeight()) {
                            return this.getBlockedNode(pX, i, pZ);
                        }

                        if (j++ >= this.mob.getMaxFallDistance()) {
                            return this.getBlockedNode(pX, pY, pZ);
                        }

                        blockpathtypes = this.getCachedBlockType(this.mob, pX, pY, pZ);
                        nodeMalus = this.mob.getPathfindingMalus(blockpathtypes);
                        if (blockpathtypes != BlockPathTypes.OPEN && nodeMalus >= 0.0F) {
                            node = this.getNodeAndUpdateCostToMax(pX, pY, pZ, blockpathtypes, nodeMalus);
                            break;
                        }

                        if (nodeMalus < 0.0F) {
                            return this.getBlockedNode(pX, pY, pZ);
                        }
                    }
                }

                if (doesBlockHavePartialCollision(blockpathtypes) && node == null) {
                    node = this.getNode(pX, pY, pZ);
                    node.closed = true;
                    node.type = blockpathtypes;
                    node.costMalus = blockpathtypes.getMalus();
                }

                return node;
            } else {
                return node;
            }
        }
    }

    private double getMobJumpHeight() {
        return Math.max(1.125, (double)this.mob.getStepHeight());
    }

    private VLNode getNodeAndUpdateCostToMax(int pX, int pY, int pZ, BlockPathTypes pType, float pCostMalus) {
        VLNode node = this.getNode(pX, pY, pZ);
        node.type = pType;
        node.costMalus = Math.max(node.costMalus, pCostMalus);
        return node;
    }

    private VLNode getBlockedNode(int pX, int pY, int pZ) {
        VLNode node = this.getNode(pX, pY, pZ);
        node.type = BlockPathTypes.BLOCKED;
        node.costMalus = -1.0F;
        return node;
    }

    private boolean hasCollisions(AABB pBoundingBox) {
        return this.collisionCache.computeIfAbsent(pBoundingBox, p_192973_ -> !this.level.noCollision(this.mob, pBoundingBox));
    }

    @Override
    //ToDo: further evaluate here!!!!!!
    public BlockPathTypes getBlockPathType(BlockGetter pLevel, int pX, int pY, int pZ, Mob pMob) {
        EnumSet<BlockPathTypes> enumset = EnumSet.noneOf(BlockPathTypes.class);
        BlockPathTypes blockpathtypes = BlockPathTypes.BLOCKED;
        blockpathtypes = this.getBlockPathTypes(pLevel, pX, pY, pZ, enumset, blockpathtypes, pMob.blockPosition());
        if (enumset.contains(BlockPathTypes.FENCE)) {
            return BlockPathTypes.FENCE;
        } else if (enumset.contains(BlockPathTypes.UNPASSABLE_RAIL)) {
            return BlockPathTypes.UNPASSABLE_RAIL;
        } else {
            BlockPathTypes blockpathtypes1 = BlockPathTypes.BLOCKED;

            for(BlockPathTypes blockpathtypes2 : enumset) {
                if (pMob.getPathfindingMalus(blockpathtypes2) < 0.0F) {
                    return blockpathtypes2;
                }

                if (pMob.getPathfindingMalus(blockpathtypes2) >= pMob.getPathfindingMalus(blockpathtypes1)) {
                    blockpathtypes1 = blockpathtypes2;
                }
            }

            return blockpathtypes == BlockPathTypes.OPEN && pMob.getPathfindingMalus(blockpathtypes1) == 0.0F && this.entityWidth <= 1
                    ? BlockPathTypes.OPEN
                    : blockpathtypes1;
        }
    }

    public BlockPathTypes getBlockPathTypes(
            BlockGetter pLevel, int pXOffset, int pYOffset, int pZOffset, EnumSet<BlockPathTypes> pOutput, BlockPathTypes pFallbackPathType, BlockPos pPos
    ) {
        for(int i = 0; i < this.entityWidth; ++i) {
            for(int j = 0; j < this.entityHeight; ++j) {
                for(int k = 0; k < this.entityDepth; ++k) {
                    int l = i + pXOffset;
                    int i1 = j + pYOffset;
                    int j1 = k + pZOffset;
                    BlockPathTypes blockpathtypes = this.getBlockPathType(pLevel, l, i1, j1);
                    blockpathtypes = this.evaluateBlockPathType(pLevel, pPos, blockpathtypes);
                    if (i == 0 && j == 0 && k == 0) {
                        pFallbackPathType = blockpathtypes;
                    }

                    pOutput.add(blockpathtypes);
                }
            }
        }

        return pFallbackPathType;
    }

    protected BlockPathTypes evaluateBlockPathType(BlockGetter pLevel, BlockPos pPos, BlockPathTypes pPathTypes) {
        boolean flag = this.canPassDoors();
        if (pPathTypes == BlockPathTypes.DOOR_WOOD_CLOSED && this.canOpenDoors() && flag) {
            pPathTypes = BlockPathTypes.WALKABLE_DOOR;
        }

        if (pPathTypes == BlockPathTypes.DOOR_OPEN && !flag) {
            pPathTypes = BlockPathTypes.BLOCKED;
        }

        if (pPathTypes == BlockPathTypes.RAIL
                && !(pLevel.getBlockState(pPos).getBlock() instanceof BaseRailBlock)
                && !(pLevel.getBlockState(pPos.below()).getBlock() instanceof BaseRailBlock)) {
            pPathTypes = BlockPathTypes.UNPASSABLE_RAIL;
        }

        return pPathTypes;
    }

    /**
     * Returns a significant cached path node type for specified position or calculates it
     */
    protected BlockPathTypes getBlockPathType(Mob pEntityliving, BlockPos pPos) {
        return this.getCachedBlockType(pEntityliving, pPos.getX(), pPos.getY(), pPos.getZ());
    }

    /**
     * Returns a cached path node type for specified position or calculates it
     */
    //Attempt to get a cachedBlockPathType for the position
    //If none exists, create one
    protected BlockPathTypes getCachedBlockType(Mob pEntity, int pX, int pY, int pZ) {
        return this.pathTypesByPosCache
                .computeIfAbsent(
                        BlockPos.asLong(pX, pY, pZ),
                        blockPosLongKey -> this.getBlockPathType(this.level, pX, pY, pZ, pEntity)
                );
    }

    /**
     * Returns the node type at the specified postion taking the block below into account
     */
    @Override
    public BlockPathTypes getBlockPathType(BlockGetter pLevel, int pX, int pY, int pZ) {
        return getBlockPathTypeStatic(pLevel, new BlockPos.MutableBlockPos(pX, pY, pZ));
    }

    /**
     * Returns the node type at the specified postion taking the block below into account
     */
    public static BlockPathTypes getBlockPathTypeStatic(BlockGetter pLevel, BlockPos.MutableBlockPos pPos) {
        int i = pPos.getX();
        int j = pPos.getY();
        int k = pPos.getZ();
        BlockPathTypes blockpathtypes = getBlockPathTypeRaw(pLevel, pPos);
        if (blockpathtypes == BlockPathTypes.OPEN && j >= pLevel.getMinBuildHeight() + 1) {
            return switch(getBlockPathTypeRaw(pLevel, pPos.set(i, j - 1, k))) {
                case OPEN, WATER, LAVA, WALKABLE -> BlockPathTypes.OPEN;
                case DAMAGE_FIRE -> BlockPathTypes.DAMAGE_FIRE;
                case DAMAGE_OTHER -> BlockPathTypes.DAMAGE_OTHER;
                case STICKY_HONEY -> BlockPathTypes.STICKY_HONEY;
                case POWDER_SNOW -> BlockPathTypes.DANGER_POWDER_SNOW;
                case DAMAGE_CAUTIOUS -> BlockPathTypes.DAMAGE_CAUTIOUS;
                case TRAPDOOR -> BlockPathTypes.DANGER_TRAPDOOR;
                default -> checkNeighbourBlocks(pLevel, pPos.set(i, j, k), BlockPathTypes.WALKABLE);
            };
        } else {
            return blockpathtypes;
        }
    }

    /**
     * Returns possible dangers in a 3x3 cube, otherwise nodeType
     */
    public static BlockPathTypes checkNeighbourBlocks(BlockGetter pLevel, BlockPos.MutableBlockPos pCenterPos, BlockPathTypes pNodeType) {
        int i = pCenterPos.getX();
        int j = pCenterPos.getY();
        int k = pCenterPos.getZ();

        for(int l = -1; l <= 1; ++l) {
            for(int i1 = -1; i1 <= 1; ++i1) {
                for(int j1 = -1; j1 <= 1; ++j1) {
                    if (l != 0 || j1 != 0) {
                        pCenterPos.set(i + l, j + i1, k + j1);
                        BlockState blockstate = pLevel.getBlockState(pCenterPos);
                        BlockPathTypes blockPathType = blockstate.getAdjacentBlockPathType(pLevel, pCenterPos, null, pNodeType);
                        if (blockPathType != null) return blockPathType;
                        FluidState fluidState = blockstate.getFluidState();
                        BlockPathTypes fluidPathType = fluidState.getAdjacentBlockPathType(pLevel, pCenterPos, null, pNodeType);
                        if (fluidPathType != null) return fluidPathType;
                        if (blockstate.is(Blocks.CACTUS) || blockstate.is(Blocks.SWEET_BERRY_BUSH)) {
                            return BlockPathTypes.DANGER_OTHER;
                        }

                        if (isBurningBlock(blockstate)) {
                            return BlockPathTypes.DANGER_FIRE;
                        }

                        if (pLevel.getFluidState(pCenterPos).is(FluidTags.WATER)) {
                            return BlockPathTypes.WATER_BORDER;
                        }

                        if (blockstate.is(Blocks.WITHER_ROSE) || blockstate.is(Blocks.POINTED_DRIPSTONE)) {
                            return BlockPathTypes.DAMAGE_CAUTIOUS;
                        }
                    }
                }
            }
        }

        return pNodeType;
    }

    protected static BlockPathTypes getBlockPathTypeRaw(BlockGetter pLevel, BlockPos pPos) {
        BlockState blockstate = pLevel.getBlockState(pPos);
        BlockPathTypes type = blockstate.getBlockPathType(pLevel, pPos, null);
        if (type != null) return type;
        Block block = blockstate.getBlock();
        if (blockstate.isAir()) {
            return BlockPathTypes.OPEN;
        } else if (blockstate.is(BlockTags.TRAPDOORS) || blockstate.is(Blocks.LILY_PAD) || blockstate.is(Blocks.BIG_DRIPLEAF)) {
            return BlockPathTypes.TRAPDOOR;
        } else if (blockstate.is(Blocks.POWDER_SNOW)) {
            return BlockPathTypes.POWDER_SNOW;
        } else if (blockstate.is(Blocks.CACTUS) || blockstate.is(Blocks.SWEET_BERRY_BUSH)) {
            return BlockPathTypes.DAMAGE_OTHER;
        } else if (blockstate.is(Blocks.HONEY_BLOCK)) {
            return BlockPathTypes.STICKY_HONEY;
        } else if (blockstate.is(Blocks.COCOA)) {
            return BlockPathTypes.COCOA;
        } else if (!blockstate.is(Blocks.WITHER_ROSE) && !blockstate.is(Blocks.POINTED_DRIPSTONE)) {
            FluidState fluidstate = pLevel.getFluidState(pPos);
            BlockPathTypes nonLoggableFluidPathType = fluidstate.getBlockPathType(pLevel, pPos, null, false);
            if (nonLoggableFluidPathType != null) return nonLoggableFluidPathType;
            if (fluidstate.is(FluidTags.LAVA)) {
                return BlockPathTypes.LAVA;
            } else if (isBurningBlock(blockstate)) {
                return BlockPathTypes.DAMAGE_FIRE;
            } else if (block instanceof DoorBlock doorblock) {
                if (blockstate.getValue(DoorBlock.OPEN)) {
                    return BlockPathTypes.DOOR_OPEN;
                } else {
                    return doorblock.type().canOpenByHand() ? BlockPathTypes.DOOR_WOOD_CLOSED : BlockPathTypes.DOOR_IRON_CLOSED;
                }
            } else if (block instanceof BaseRailBlock) {
                return BlockPathTypes.RAIL;
            } else if (block instanceof LeavesBlock) {
                return BlockPathTypes.LEAVES;
            } else if (!blockstate.is(BlockTags.FENCES)
                    && !blockstate.is(BlockTags.WALLS)
                    && (!(block instanceof FenceGateBlock) || blockstate.getValue(FenceGateBlock.OPEN))) {
                if (!blockstate.isPathfindable(pLevel, pPos, PathComputationType.LAND)) {
                    return BlockPathTypes.BLOCKED;
                } else {
                    BlockPathTypes loggableFluidPathType = fluidstate.getBlockPathType(pLevel, pPos, null, true);
                    if (loggableFluidPathType != null) return loggableFluidPathType;
                    return fluidstate.is(FluidTags.WATER) ? BlockPathTypes.WATER : BlockPathTypes.OPEN;
                }
            } else {
                return BlockPathTypes.FENCE;
            }
        } else {
            return BlockPathTypes.DAMAGE_CAUTIOUS;
        }
    }

    /**
     * Checks whether the specified block state can cause burn damage
     */
    public static boolean isBurningBlock(BlockState pState) {
        return pState.is(BlockTags.FIRE)
                || pState.is(Blocks.LAVA)
                || pState.is(Blocks.MAGMA_BLOCK)
                || CampfireBlock.isLitCampfire(pState)
                || pState.is(Blocks.LAVA_CAULDRON);
    }
}
