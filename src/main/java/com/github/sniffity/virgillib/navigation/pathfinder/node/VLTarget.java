package com.github.sniffity.virgillib.navigation.pathfinder.node;


import net.minecraft.network.FriendlyByteBuf;

public class VLTarget extends VLNode {
    private float besthValue = Float.MAX_VALUE;
    /**
     * The nearest path point of the path that is constructed
     */
    private VLNode bestNode;
    private boolean reached;

    public VLTarget(VLNode pNode) {
        super(pNode.x, pNode.y, pNode.z);
    }

    public VLTarget(int pX, int pY, int pZ) {
        super(pX, pY, pZ);
    }

    public void updateBest(float hValue, VLNode pNode) {
        if (hValue < this.besthValue) {
            this.besthValue = hValue;
            this.bestNode = pNode;
        }
    }

    /**
     * Gets the nearest path point of the path that is constructed
     */
    public VLNode getBestNode() {
        return this.bestNode;
    }

    public void setReached() {
        this.reached = true;
    }

    public boolean isReached() {
        return this.reached;
    }

    public static VLTarget createFromStream(FriendlyByteBuf pBuffer) {
        VLTarget target = new VLTarget(pBuffer.readInt(), pBuffer.readInt(), pBuffer.readInt());
        readContents(pBuffer, target);
        return target;
    }
}
