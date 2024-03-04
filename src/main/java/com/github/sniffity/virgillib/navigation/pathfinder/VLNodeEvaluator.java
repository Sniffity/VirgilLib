package com.github.sniffity.virgillib.navigation.pathfinder;

import com.github.sniffity.virgillib.navigation.VLPathNavigationRegion;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;

public abstract class VLNodeEvaluator {
    protected VLPathNavigationRegion level;
    protected Mob mob;
    protected final Int2ObjectMap<VLNode> nodes = new Int2ObjectOpenHashMap<>();
    protected int entityWidth;
    protected int entityHeight;
    protected int entityDepth;
    protected boolean canPassDoors;
    protected boolean canOpenDoors;
    protected boolean canFloat;
    protected boolean canWalkOverFences;

    public void prepare(VLPathNavigationRegion pLevel, Mob pMob) {
        this.level = pLevel;
        this.mob = pMob;
        this.nodes.clear();
        this.entityWidth = Mth.floor(pMob.getBbWidth() + 1.0F);
        this.entityHeight = Mth.floor(pMob.getBbHeight() + 1.0F);
        this.entityDepth = Mth.floor(pMob.getBbWidth() + 1.0F);
    }

    /**
     * This method is called when all nodes have been processed and PathEntity is created.
     */
    public void done() {
        this.level = null;
        this.mob = null;
    }

    protected VLNode getNode(BlockPos pPos) {
        return this.getNode(pPos.getX(), pPos.getY(), pPos.getZ());
    }

    /**
     * Returns a mapped point or creates and adds one
     */
    protected VLNode getNode(int pX, int pY, int pZ) {
        return this.nodes.computeIfAbsent(Node.createHash(pX, pY, pZ), p_77332_ -> new VLNode(pX, pY, pZ));
    }

    public abstract VLNode getStart();

    public abstract VLTarget getGoal(double pX, double pY, double pZ);

    protected VLTarget getTargetFromNode(VLNode pNode) {
        return new VLTarget(pNode);
    }

    public abstract int getNeighbors(VLNode[] pOutputArray, VLNode pNode);

    public abstract BlockPathTypes getBlockPathType(BlockGetter pLevel, int pX, int pY, int pZ, Mob pMob);

    /**
     * Returns the node type at the specified postion taking the block below into account
     */
    public abstract BlockPathTypes getBlockPathType(BlockGetter pLevel, int pX, int pY, int pZ);

    public void setCanPassDoors(boolean pCanEnterDoors) {
        this.canPassDoors = pCanEnterDoors;
    }

    public void setCanOpenDoors(boolean pCanOpenDoors) {
        this.canOpenDoors = pCanOpenDoors;
    }

    public void setCanFloat(boolean pCanFloat) {
        this.canFloat = pCanFloat;
    }

    public void setCanWalkOverFences(boolean pCanWalkOverFences) {
        this.canWalkOverFences = pCanWalkOverFences;
    }

    public boolean canPassDoors() {
        return this.canPassDoors;
    }

    public boolean canOpenDoors() {
        return this.canOpenDoors;
    }

    public boolean canFloat() {
        return this.canFloat;
    }

    public boolean canWalkOverFences() {
        return this.canWalkOverFences;
    }
}