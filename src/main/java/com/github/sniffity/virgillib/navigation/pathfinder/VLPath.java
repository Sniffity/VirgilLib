package com.github.sniffity.virgillib.navigation.pathfinder;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Target;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VLPath {
    private final List<VLNode> nodes;
    @Nullable
    private VLPath.DebugData debugData;
    private int nextNodeIndex;
    private final BlockPos target;
    private final float distToTarget;
    private final boolean reached;

    public VLPath(List<VLNode> pNodes, BlockPos pTarget, boolean pReached) {
        this.nodes = pNodes;
        this.target = pTarget;
        this.distToTarget = pNodes.isEmpty() ? Float.MAX_VALUE : this.nodes.get(this.nodes.size() - 1).distanceManhattan(this.target);
        this.reached = pReached;
    }

    /**
     * Directs this path to the next point in its array
     */
    public void advance() {
        ++this.nextNodeIndex;
    }

    public boolean notStarted() {
        return this.nextNodeIndex <= 0;
    }

    /**
     * Returns {@code true} if this path has reached the end
     */
    public boolean isDone() {
        return this.nextNodeIndex >= this.nodes.size();
    }

    /**
     * Returns the last {@link net.minecraft.world.level.pathfinder.Node} of the Array.
     */
    @Nullable
    public VLNode getEndNode() {
        return !this.nodes.isEmpty() ? this.nodes.get(this.nodes.size() - 1) : null;
    }

    /**
     * Returns the {@link net.minecraft.world.level.pathfinder.Node} located at the specified index, usually the current one.
     */
    public VLNode getNode(int pIndex) {
        return this.nodes.get(pIndex);
    }

    public void truncateNodes(int pLength) {
        if (this.nodes.size() > pLength) {
            this.nodes.subList(pLength, this.nodes.size()).clear();
        }
    }

    public void replaceNode(int pIndex, VLNode pPoint) {
        this.nodes.set(pIndex, pPoint);
    }

    public int getNodeCount() {
        return this.nodes.size();
    }

    public int getNextNodeIndex() {
        return this.nextNodeIndex;
    }

    public void setNextNodeIndex(int pCurrentPathIndex) {
        this.nextNodeIndex = pCurrentPathIndex;
    }

    /**
     * Gets the vector of the {@link net.minecraft.world.level.pathfinder.Node} associated with the given index.
     */
    public Vec3 getEntityPosAtNode(Entity pEntity, int pIndex) {
        VLNode node = this.nodes.get(pIndex);
        double d0 = (double)node.x + (double)((int)(pEntity.getBbWidth() + 1.0F)) * 0.5;
        double d1 = (double)node.y;
        double d2 = (double)node.z + (double)((int)(pEntity.getBbWidth() + 1.0F)) * 0.5;
        return new Vec3(d0, d1, d2);
    }

    public BlockPos getNodePos(int pIndex) {
        return this.nodes.get(pIndex).asBlockPos();
    }

    /**
     * @return the current {@code PathEntity} target node as a {@code Vec3D}
     */
    public Vec3 getNextEntityPos(Entity pEntity) {
        return this.getEntityPosAtNode(pEntity, this.nextNodeIndex);
    }

    public BlockPos getNextNodePos() {
        return this.nodes.get(this.nextNodeIndex).asBlockPos();
    }

    public VLNode getNextNode() {
        return this.nodes.get(this.nextNodeIndex);
    }

    @Nullable
    public VLNode getPreviousNode() {
        return this.nextNodeIndex > 0 ? this.nodes.get(this.nextNodeIndex - 1) : null;
    }

    /**
     * Returns {@code true} if the EntityPath are the same. Non instance related equals.
     */
    public boolean sameAs(@Nullable VLPath pPathentity) {
        if (pPathentity == null) {
            return false;
        } else if (pPathentity.nodes.size() != this.nodes.size()) {
            return false;
        } else {
            for(int i = 0; i < this.nodes.size(); ++i) {
                VLNode node = this.nodes.get(i);
                VLNode node1 = pPathentity.nodes.get(i);
                if (node.x != node1.x || node.y != node1.y || node.z != node1.z) {
                    return false;
                }
            }

            return true;
        }
    }

    public boolean canReach() {
        return this.reached;
    }

    @VisibleForDebug
    void setDebug(VLNode[] pOpenSet, VLNode[] pClosedSet, Set<Target> pTargetNodes) {
        this.debugData = new VLPath.DebugData(pOpenSet, pClosedSet, pTargetNodes);
    }

    @Nullable
    public VLPath.DebugData debugData() {
        return this.debugData;
    }

    public void writeToStream(FriendlyByteBuf pBuffer) {
        if (this.debugData != null && !this.debugData.targetNodes.isEmpty()) {
            pBuffer.writeBoolean(this.reached);
            pBuffer.writeInt(this.nextNodeIndex);
            pBuffer.writeBlockPos(this.target);
            pBuffer.writeCollection(this.nodes, (p_294084_, p_294085_) -> p_294085_.writeToStream(p_294084_));
            this.debugData.write(pBuffer);
        }
    }

    public static VLPath createFromStream(FriendlyByteBuf pBuf) {
        boolean flag = pBuf.readBoolean();
        int i = pBuf.readInt();
        BlockPos blockpos = pBuf.readBlockPos();
        List<VLNode> list = pBuf.readList(VLNode::createFromStream);
        VLPath.DebugData path$debugdata = VLPath.DebugData.read(pBuf);
        VLPath path = new VLPath(list, blockpos, flag);
        path.debugData = path$debugdata;
        path.nextNodeIndex = i;
        return path;
    }

    @Override
    public String toString() {
        return "Path(length=" + this.nodes.size() + ")";
    }

    public BlockPos getTarget() {
        return this.target;
    }

    public float getDistToTarget() {
        return this.distToTarget;
    }

    static VLNode[] readNodeArray(FriendlyByteBuf pBuffer) {
        VLNode[] anode = new VLNode[pBuffer.readVarInt()];

        for(int i = 0; i < anode.length; ++i) {
            anode[i] = VLNode.createFromStream(pBuffer);
        }

        return anode;
    }

    static void writeNodeArray(FriendlyByteBuf pBuffer, VLNode[] pNodeArray) {
        pBuffer.writeVarInt(pNodeArray.length);

        for(VLNode node : pNodeArray) {
            node.writeToStream(pBuffer);
        }
    }

    public VLPath copy() {
        VLPath path = new VLPath(this.nodes, this.target, this.reached);
        path.debugData = this.debugData;
        path.nextNodeIndex = this.nextNodeIndex;
        return path;
    }

    public static record DebugData(VLNode[] openSet, VLNode[] closedSet, Set<Target> targetNodes) {
        public void write(FriendlyByteBuf pBuffer) {
            pBuffer.writeCollection(this.targetNodes, (p_295084_, p_294361_) -> p_294361_.writeToStream(p_295084_));
            VLPath.writeNodeArray(pBuffer, this.openSet);
            VLPath.writeNodeArray(pBuffer, this.closedSet);
        }

        public static DebugData read(FriendlyByteBuf pBuffer) {
            HashSet<Target> hashset = pBuffer.readCollection(HashSet::new, Target::createFromStream);
            VLNode[] anode = VLPath.readNodeArray(pBuffer);
            VLNode[] anode1 = VLPath.readNodeArray(pBuffer);
            return new VLPath.DebugData(anode, anode1, hashset);
        }
    }
}
