package com.github.sniffity.virgillib.navigation.pathfinder.node;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class VLNode {
    public final int x;
    public final int y;
    public final int z;
    private final int hash;
    /**
     * The index in the PathHeap. -1 if not assigned.
     */
    public int heapIdx = -1;
    /**
     * The total cost of all path points up to this one. Corresponds to the A* g-score.
     */
    public float g;
    /**
     * The estimated cost from this path point to the target. Corresponds to the A* h-score.
     */
    public float h;
    /**
     * The total cost of the path containing this path point. Used as sort criteria in PathHeap. Corresponds to the A* f-score.
     */
    public float f;
    @Nullable
    public VLNode cameFrom;
    public boolean closed;
    public float walkedDistance;
    /**
     * The additional cost of the path point. If negative, the path point will be sorted out by NodeProcessors.
     */
    public float costMalus;
    public BlockPathTypes type = BlockPathTypes.BLOCKED;

    public VLNode(int pX, int pY, int pZ) {
        //Each Node consists of an X, Y, Z value and a hash value which identified the node
        this.x = pX;
        this.y = pY;
        this.z = pZ;
        //Hash Value: 32 bit (4-byte) Integer that identifies the node. Will usually be unique...
        this.hash = createHash(pX, pY, pZ);
    }

    //Creates a hash value for the node...
    public static int createHash(int pX, int pY, int pZ) {
        return pY & 0xFF | (pX & 32767) << 8 | (pZ & 32767) << 24 | (pX < 0 ? Integer.MIN_VALUE : 0) | (pZ < 0 ? 32768 : 0);
    }

    public VLNode cloneAndMove(int pX, int pY, int pZ) {
        VLNode node = new VLNode(pX, pY, pZ);
        node.heapIdx = this.heapIdx;
        node.g = this.g;
        node.h = this.h;
        node.f = this.f;
        node.cameFrom = this.cameFrom;
        node.closed = this.closed;
        node.walkedDistance = this.walkedDistance;
        node.costMalus = this.costMalus;
        node.type = this.type;
        return node;
    }


    /**
     * Returns the linear distance to another path point
     */
    public float distanceTo(VLNode pPoint) {
        float f = (float)(pPoint.x - this.x);
        float f1 = (float)(pPoint.y - this.y);
        float f2 = (float)(pPoint.z - this.z);
        return Mth.sqrt(f * f + f1 * f1 + f2 * f2);
    }

    public float distanceToXZ(VLNode pPoint) {
        float f = (float)(pPoint.x - this.x);
        float f1 = (float)(pPoint.z - this.z);
        return Mth.sqrt(f * f + f1 * f1);
    }

    public float distanceTo(BlockPos pPos) {
        float f = (float)(pPos.getX() - this.x);
        float f1 = (float)(pPos.getY() - this.y);
        float f2 = (float)(pPos.getZ() - this.z);
        return Mth.sqrt(f * f + f1 * f1 + f2 * f2);
    }

    /**
     * Returns the squared distance to another path point
     */
    public float distanceToSqr(VLNode pPoint) {
        float f = (float)(pPoint.x - this.x);
        float f1 = (float)(pPoint.y - this.y);
        float f2 = (float)(pPoint.z - this.z);
        return f * f + f1 * f1 + f2 * f2;
    }

    public float distanceToSqr(BlockPos pPos) {
        float f = (float)(pPos.getX() - this.x);
        float f1 = (float)(pPos.getY() - this.y);
        float f2 = (float)(pPos.getZ() - this.z);
        return f * f + f1 * f1 + f2 * f2;
    }

    public float distanceManhattan(VLNode pPoint) {
        float f = (float)Math.abs(pPoint.x - this.x);
        float f1 = (float)Math.abs(pPoint.y - this.y);
        float f2 = (float)Math.abs(pPoint.z - this.z);
        return f + f1 + f2;
    }

    public float distanceManhattan(BlockPos pPos) {
        float f = (float)Math.abs(pPos.getX() - this.x);
        float f1 = (float)Math.abs(pPos.getY() - this.y);
        float f2 = (float)Math.abs(pPos.getZ() - this.z);
        return f + f1 + f2;
    }

    public BlockPos asBlockPos() {
        return new BlockPos(this.x, this.y, this.z);
    }

    public Vec3 asVec3() {
        return new Vec3((double)this.x, (double)this.y, (double)this.z);
    }

    @Override
    public boolean equals(Object pOther) {
        if (!(pOther instanceof VLNode)) {
            return false;
        } else {
            VLNode node = (VLNode)pOther;
            return this.hash == node.hash && this.x == node.x && this.y == node.y && this.z == node.z;
        }
    }

    @Override
    public int hashCode() {
        return this.hash;
    }

    /**
     * Returns {@code true} if this point has already been assigned to a path
     */
    public boolean inOpenSet() {
        return this.heapIdx >= 0;
    }

    @Override
    public String toString() {
        return "Node{x=" + this.x + ", y=" + this.y + ", z=" + this.z + "}";
    }

    public void writeToStream(FriendlyByteBuf pBuffer) {
        pBuffer.writeInt(this.x);
        pBuffer.writeInt(this.y);
        pBuffer.writeInt(this.z);
        pBuffer.writeFloat(this.walkedDistance);
        pBuffer.writeFloat(this.costMalus);
        pBuffer.writeBoolean(this.closed);
        pBuffer.writeEnum(this.type);
        pBuffer.writeFloat(this.f);
    }

    public static VLNode createFromStream(FriendlyByteBuf pBuffer) {
        VLNode node = new VLNode(pBuffer.readInt(), pBuffer.readInt(), pBuffer.readInt());
        readContents(pBuffer, node);
        return node;
    }

    protected static void readContents(FriendlyByteBuf pBuffer, VLNode pNode) {
        pNode.walkedDistance = pBuffer.readFloat();
        pNode.costMalus = pBuffer.readFloat();
        pNode.closed = pBuffer.readBoolean();
        pNode.type = pBuffer.readEnum(BlockPathTypes.class);
        pNode.f = pBuffer.readFloat();
    }
}
