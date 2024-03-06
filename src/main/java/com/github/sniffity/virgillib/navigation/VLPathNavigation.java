package com.github.sniffity.virgillib.navigation;

import com.github.sniffity.virgillib.navigation.pathfinder.node.VLNode;
import com.github.sniffity.virgillib.navigation.pathfinder.node.evaluator.VLNodeEvaluator;
import com.github.sniffity.virgillib.navigation.pathfinder.VLPath;
import com.github.sniffity.virgillib.navigation.pathfinder.VLPathFinder;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
public abstract class VLPathNavigation {
    private static final int MAX_TIME_RECOMPUTE = 20;
    private static final int STUCK_CHECK_INTERVAL = 100;
    private static final float STUCK_THRESHOLD_DISTANCE_FACTOR = 0.25F;
    protected final Mob mob;
    protected final Level level;
    @Nullable
    protected VLPath path;
    protected double speedModifier;
    protected int tick;
    protected int lastStuckCheck;
    protected Vec3 lastStuckCheckPos = Vec3.ZERO;
    protected Vec3i timeoutCachedNode = Vec3i.ZERO;
    protected long timeoutTimer;
    protected long lastTimeoutCheck;
    protected double timeoutLimit;
    protected float maxDistanceToWaypoint = 0.5F;
    /**
     * Whether the path can be changed by {@link net.minecraft.pathfinding.PathNavigate#onUpdateNavigation() onUpdateNavigation()}
     */
    protected boolean hasDelayedRecomputation;
    protected long timeLastRecompute;
    protected VLNodeEvaluator nodeEvaluator;
    @Nullable
    private BlockPos targetPos;
    /**
     * Distance in which a path point counts as target-reaching
     */
    private int reachRange;
    private float maxVisitedNodesMultiplier = 1.0F;
    private final VLPathFinder pathFinder;
    private boolean isStuck;

    public VLPathNavigation(Mob pMob, Level pLevel) {
        this.mob = pMob;
        this.level = pLevel;
        int i = Mth.floor(pMob.getAttributeValue(Attributes.FOLLOW_RANGE) * 16.0);
        //Initializes a PathFinder instance, with VisitedNotes = 16*FOLLOW_RANGE
        this.pathFinder = this.createPathFinder(i);
    }

    //Initializes a PathFinder instance, with VisitedNotes = 16*FOLLOW_RANGE
    protected abstract VLPathFinder createPathFinder(int pMaxVisitedNodes);



    // ==============================
    // VL-1: moveTo Methods
    // ==============================

    //PathNavigation begins by the following moveTo Methods being called.
    //There are two initial options:
    //Method 1: moveToXYZ
    //Method 2: moveToEntity
    //These moveTo Methods will return true if the Path is succesfully created...
    //Both Methods will eventually call a createPath Method, either to XYZ (Method1) or to an Entity (Method 2)

    //moveTo Method 1: moveTo XYZ
    public boolean moveTo(double pX, double pY, double pZ, double pSpeed) {
        return this.moveTo(this.createPath(pX, pY, pZ, 1), pSpeed);
    }

    //moveTo Method 2: moveToEntity
    public boolean moveTo(Entity pEntity, double pSpeed) {
        VLPath path = this.createPath(pEntity, 1);
        return path != null && this.moveTo(path, pSpeed);
    }

    //ToDo:
    // Method 3: Pending


    /**
     * Sets a new path. If it's different from the old path. Checks to adjust path for sun avoiding, and stores start coords.
     */
    public boolean moveTo(@Nullable VLPath pPathentity, double pSpeed) {
        if (pPathentity == null) {
            this.path = null;
            return false;
        } else {
            if (!pPathentity.sameAs(this.path)) {
                this.path = pPathentity;
            }

            if (this.isDone()) {
                return false;
            } else {
                this.trimPath();
                if (this.path.getNodeCount() <= 0) {
                    return false;
                } else {
                    this.speedModifier = pSpeed;
                    Vec3 vec3 = this.getTempMobPos();
                    this.lastStuckCheck = this.tick;
                    this.lastStuckCheckPos = vec3;
                    return true;
                }
            }
        }
    }

    // ==============================
    // VL-2: createPath Methods
    // ==============================

    //createPath Method 1: Path to XYZ
    //This method will call another method (createPath Method 1.1) that creates a path to the XYZ BlockPos, floored
    //Hence, a moveTo 10.5,11.3,12.3 will call a createPath to BlockPos 10,11,12
    @Nullable
    public final VLPath createPath(double pX, double pY, double pZ, int pAccuracy) {
        return this.createPath(BlockPos.containing(pX, pY, pZ), pAccuracy);
    }
    //createPath Method 1.1: Path to BlockPos floored
    //Method 1.1 calls createPath to an ImmutableSet with a single element, the XYZ BlockPos floored (X, Y, Z)
    //Of note, this uses pRegionOffset = 8 (vs. 16 when the method is called to an entity's position)
    //Of note, this uses pOffsetUpward = false (vs. true when the method is called to an entity's position)
    @Nullable
    public VLPath createPath(BlockPos pPos, int pAccuracy) {
        return this.createPath(ImmutableSet.of(pPos), 8, false, pAccuracy);
    }


    //createPath Method 2: Path to Entity
    //Method 2 calls createPath to an ImmutableSet with a single element, the entity's BlockPos (X, Y, Z)
    //Of note, this uses pRegionOffset = 16 (vs. 8 when the method is called to an XYZ position)
    //Of note, this uses pOffsetUpward = true (vs. false when the method is called to an XYZ position)
    @Nullable
    public VLPath createPath(Entity pEntity, int pAccuracy) {
        return this.createPath(ImmutableSet.of(pEntity.blockPosition()), 16, true, pAccuracy);
    }


    /**
     * Returns a path to one of the given targets or null
     */

    //createPath Method 3: Path to BlockPos Set
    //Method 3 calls another createPathMethod to an ImmutableSet containing, as of now, a single BlockPosition
    //It adds in the follow range of the Mob that is navigating

    @Nullable
    protected VLPath createPath(Set<BlockPos> pTargets, int pRegionOffset, boolean pOffsetUpward, int pAccuracy) {
        return this.createPath(pTargets, pRegionOffset, pOffsetUpward, pAccuracy, (float)this.mob.getAttributeValue(Attributes.FOLLOW_RANGE));
    }


    //createPath Method 4:
    //Herein, the calculations begin...
    //A mob will not create a path if:
        //A) the BlockPosition it is targeting is null
        //B) the mob's Y position is lower than the minBuildHeight
        //C) the mob cannot updatePath
        //D) It already has a non-null, non-done Path that contains the target position
            //case D: just returns the previous path
    @Nullable
    protected VLPath createPath(Set<BlockPos> pTargets, int pRegionOffset, boolean pOffsetUpward, int pAccuracy, float pFollowRange) {
        if (pTargets.isEmpty()) {
            return null;
        } else if (this.mob.getY() < (double)this.level.getMinBuildHeight()) {
            return null;
        } else if (!this.canUpdatePath()) {
            return null;
        } else if (this.path != null && !this.path.isDone() && pTargets.contains(this.targetPos)) {
            return this.path;
        } else {
            //If the conditions are met to begin PathFinding, we begin profiling the time it takes to pathfind
            this.level.getProfiler().push("pathfind");
            //We define a starting block position...
                //This will be either at the PathingMob's initial position (if navigation to XYZ)
                //Or at the PathingMob's initial position +1Y (if navigating to an entity)
            BlockPos blockpos = pOffsetUpward ? this.mob.blockPosition().above() : this.mob.blockPosition();
            //We define a PathNavigation region, in which to calculate the Path
                //This region will be a cube, with each side measuring (2*(entityFollowRange+pRegionOffset))
                //When navigating to an entity, the regionOffset is 16
                //When navigationg to an XYZ position, the regionOffset is 8
                //Hence, the PathNavigationRegion is larger if navigating to an entity vs. to an XYZ position
            int i = (int)(pFollowRange + (float)pRegionOffset);
            //ToDo: Investigate further in PathNavigationRegion
            VLPathNavigationRegion pathnavigationregion = new VLPathNavigationRegion(this.level, blockpos.offset(-i, -i, -i), blockpos.offset(i, i, i));
            //Finally, we call findPath, which actually constructs the Path
            //Of note, findPath is called ona previously initialized PathFinder instance for this mob
            VLPath path = this.pathFinder.findPath(pathnavigationregion, this.mob, pTargets, pFollowRange, pAccuracy, this.maxVisitedNodesMultiplier);
            this.level.getProfiler().pop();
            if (path != null && path.getTarget() != null) {
                this.targetPos = path.getTarget();
                this.reachRange = pAccuracy;
                this.resetStuckTimeout();
            }

            return path;
        }
    }

    /**
     * Returns a path to one of the elements of the stream or null
     */
    @Nullable
    public VLPath createPath(Stream<BlockPos> pTargets, int pAccuracy) {
        return this.createPath(pTargets.collect(Collectors.toSet()), 8, false, pAccuracy);
    }

    @Nullable
    public VLPath createPath(Set<BlockPos> pPositions, int pDistance) {
        return this.createPath(pPositions, 8, false, pDistance);
    }



    @Nullable
    public VLPath createPath(BlockPos pPos, int pRegionOffset, int pAccuracy) {
        return this.createPath(ImmutableSet.of(pPos), 8, false, pRegionOffset, (float)pAccuracy);
    }





    /**
     * Returns path to given BlockPos
     */


    public void resetMaxVisitedNodesMultiplier() {
        this.maxVisitedNodesMultiplier = 1.0F;
    }

    public void setMaxVisitedNodesMultiplier(float pMultiplier) {
        this.maxVisitedNodesMultiplier = pMultiplier;
    }

    @Nullable
    public BlockPos getTargetPos() {
        return this.targetPos;
    }

    /**
     * Sets the speed
     */
    public void setSpeedModifier(double pSpeed) {
        this.speedModifier = pSpeed;
    }

    public void recomputePath() {
        if (this.level.getGameTime() - this.timeLastRecompute > 20L) {
            if (this.targetPos != null) {
                this.path = null;
                this.path = this.createPath(this.targetPos, this.reachRange);
                this.timeLastRecompute = this.level.getGameTime();
                this.hasDelayedRecomputation = false;
            }
        } else {
            this.hasDelayedRecomputation = true;
        }
    }




    /**
     * Gets the actively used {@link net.minecraft.world.level.pathfinder.Path}.
     */
    @Nullable
    public VLPath getPath() {
        return this.path;
    }

    public void tick() {
        ++this.tick;
        if (this.hasDelayedRecomputation) {
            this.recomputePath();
        }

        if (!this.isDone()) {
            if (this.canUpdatePath()) {
                this.followThePath();
            } else if (this.path != null && !this.path.isDone()) {
                Vec3 vec3 = this.getTempMobPos();
                Vec3 vec31 = this.path.getNextEntityPos(this.mob);
                if (vec3.y > vec31.y && !this.mob.onGround() && Mth.floor(vec3.x) == Mth.floor(vec31.x) && Mth.floor(vec3.z) == Mth.floor(vec31.z)) {
                    this.path.advance();
                }
            }

            //ToDo: Commented out
            /*
            DebugPackets.sendPathFindingPacket(this.level, this.mob, this.path, this.maxDistanceToWaypoint);

             */
            if (!this.isDone()) {
                Vec3 vec32 = this.path.getNextEntityPos(this.mob);
                this.mob.getMoveControl().setWantedPosition(vec32.x, this.getGroundY(vec32), vec32.z, this.speedModifier);
            }
        }
    }

    protected double getGroundY(Vec3 pVec) {
        BlockPos blockpos = BlockPos.containing(pVec);
        return this.level.getBlockState(blockpos.below()).isAir() ? pVec.y : WalkNodeEvaluator.getFloorLevel(this.level, blockpos);
    }

    protected void followThePath() {
        Vec3 vec3 = this.getTempMobPos();
        this.maxDistanceToWaypoint = this.mob.getBbWidth() > 0.75F ? this.mob.getBbWidth() / 2.0F : 0.75F - this.mob.getBbWidth() / 2.0F;
        Vec3i vec3i = this.path.getNextNodePos();
        double d0 = Math.abs(this.mob.getX() - ((double)vec3i.getX() + (this.mob.getBbWidth() + 1) / 2D)); //Forge: Fix MC-94054
        double d1 = Math.abs(this.mob.getY() - (double)vec3i.getY());
        double d2 = Math.abs(this.mob.getZ() - ((double)vec3i.getZ() + (this.mob.getBbWidth() + 1) / 2D)); //Forge: Fix MC-94054
        boolean flag = d0 <= (double)this.maxDistanceToWaypoint && d2 <= (double)this.maxDistanceToWaypoint && d1 < 1.0D; //Forge: Fix MC-94054
        if (flag || this.canCutCorner(this.path.getNextNode().type) && this.shouldTargetNextNodeInDirection(vec3)) {
            this.path.advance();
        }

        this.doStuckDetection(vec3);
    }

    private boolean shouldTargetNextNodeInDirection(Vec3 pVec) {
        if (this.path.getNextNodeIndex() + 1 >= this.path.getNodeCount()) {
            return false;
        } else {
            Vec3 vec3 = Vec3.atBottomCenterOf(this.path.getNextNodePos());
            if (!pVec.closerThan(vec3, 2.0)) {
                return false;
            } else if (this.canMoveDirectly(pVec, this.path.getNextEntityPos(this.mob))) {
                return true;
            } else {
                Vec3 vec31 = Vec3.atBottomCenterOf(this.path.getNodePos(this.path.getNextNodeIndex() + 1));
                Vec3 vec32 = vec3.subtract(pVec);
                Vec3 vec33 = vec31.subtract(pVec);
                double d0 = vec32.lengthSqr();
                double d1 = vec33.lengthSqr();
                boolean flag = d1 < d0;
                boolean flag1 = d0 < 0.5;
                if (!flag && !flag1) {
                    return false;
                } else {
                    Vec3 vec34 = vec32.normalize();
                    Vec3 vec35 = vec33.normalize();
                    return vec35.dot(vec34) < 0.0;
                }
            }
        }
    }

    /**
     * Checks if entity haven't been moved when last checked and if so, stops the current navigation.
     */
    protected void doStuckDetection(Vec3 pPositionVec3) {
        if (this.tick - this.lastStuckCheck > 100) {
            float f = this.mob.getSpeed() >= 1.0F ? this.mob.getSpeed() : this.mob.getSpeed() * this.mob.getSpeed();
            float f1 = f * 100.0F * 0.25F;
            if (pPositionVec3.distanceToSqr(this.lastStuckCheckPos) < (double)(f1 * f1)) {
                this.isStuck = true;
                this.stop();
            } else {
                this.isStuck = false;
            }

            this.lastStuckCheck = this.tick;
            this.lastStuckCheckPos = pPositionVec3;
        }

        if (this.path != null && !this.path.isDone()) {
            Vec3i vec3i = this.path.getNextNodePos();
            long i = this.level.getGameTime();
            if (vec3i.equals(this.timeoutCachedNode)) {
                this.timeoutTimer += i - this.lastTimeoutCheck;
            } else {
                this.timeoutCachedNode = vec3i;
                double d0 = pPositionVec3.distanceTo(Vec3.atBottomCenterOf(this.timeoutCachedNode));
                this.timeoutLimit = this.mob.getSpeed() > 0.0F ? d0 / (double)this.mob.getSpeed() * 20.0 : 0.0;
            }

            if (this.timeoutLimit > 0.0 && (double)this.timeoutTimer > this.timeoutLimit * 3.0) {
                this.timeoutPath();
            }

            this.lastTimeoutCheck = i;
        }
    }

    private void timeoutPath() {
        this.resetStuckTimeout();
        this.stop();
    }

    private void resetStuckTimeout() {
        this.timeoutCachedNode = Vec3i.ZERO;
        this.timeoutTimer = 0L;
        this.timeoutLimit = 0.0;
        this.isStuck = false;
    }

    /**
     * If null path or reached the end
     */
    public boolean isDone() {
        return this.path == null || this.path.isDone();
    }

    public boolean isInProgress() {
        return !this.isDone();
    }

    /**
     * Sets the active {@link net.minecraft.world.level.pathfinder.Path} to {@code null}.
     */
    public void stop() {
        this.path = null;
    }

    protected abstract Vec3 getTempMobPos();

    /**
     * If on ground or swimming and can swim
     */
    protected abstract boolean canUpdatePath();

    /**
     * Trims path data from the end to the first sun covered block
     */
    protected void trimPath() {
        if (this.path != null) {
            for(int i = 0; i < this.path.getNodeCount(); ++i) {
                VLNode node = this.path.getNode(i);
                VLNode node1 = i + 1 < this.path.getNodeCount() ? this.path.getNode(i + 1) : null;
                BlockState blockstate = this.level.getBlockState(new BlockPos(node.x, node.y, node.z));
                if (blockstate.is(BlockTags.CAULDRONS)) {
                    this.path.replaceNode(i, node.cloneAndMove(node.x, node.y + 1, node.z));
                    if (node1 != null && node.y >= node1.y) {
                        this.path.replaceNode(i + 1, node.cloneAndMove(node1.x, node.y + 1, node1.z));
                    }
                }
            }
        }
    }

    /**
     * Checks if the specified entity can safely walk to the specified location.
     */
    protected boolean canMoveDirectly(Vec3 pPosVec31, Vec3 pPosVec32) {
        return false;
    }

    public boolean canCutCorner(BlockPathTypes pPathType) {
        return pPathType != BlockPathTypes.DANGER_FIRE && pPathType != BlockPathTypes.DANGER_OTHER && pPathType != BlockPathTypes.WALKABLE_DOOR;
    }

    protected static boolean isClearForMovementBetween(Mob pMob, Vec3 pPos1, Vec3 pPos2, boolean pAllowSwimming) {
        Vec3 vec3 = new Vec3(pPos2.x, pPos2.y + (double)pMob.getBbHeight() * 0.5, pPos2.z);
        return pMob.level()
                .clip(new ClipContext(pPos1, vec3, ClipContext.Block.COLLIDER, pAllowSwimming ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, pMob))
                .getType()
                == HitResult.Type.MISS;
    }

    public boolean isStableDestination(BlockPos pPos) {
        BlockPos blockpos = pPos.below();
        return this.level.getBlockState(blockpos).isSolidRender(this.level, blockpos);
    }

    public VLNodeEvaluator getNodeEvaluator() {
        return this.nodeEvaluator;
    }

    public void setCanFloat(boolean pCanSwim) {
        this.nodeEvaluator.setCanFloat(pCanSwim);
    }

    public boolean canFloat() {
        return this.nodeEvaluator.canFloat();
    }

    public boolean shouldRecomputePath(BlockPos pPos) {
        if (this.hasDelayedRecomputation) {
            return false;
        } else if (this.path != null && !this.path.isDone() && this.path.getNodeCount() != 0) {
            VLNode node = this.path.getEndNode();
            Vec3 vec3 = new Vec3(((double)node.x + this.mob.getX()) / 2.0, ((double)node.y + this.mob.getY()) / 2.0, ((double)node.z + this.mob.getZ()) / 2.0);
            return pPos.closerToCenterThan(vec3, (double)(this.path.getNodeCount() - this.path.getNextNodeIndex()));
        } else {
            return false;
        }
    }

    public float getMaxDistanceToWaypoint() {
        return this.maxDistanceToWaypoint;
    }

    public boolean isStuck() {
        return this.isStuck;
    }
}
