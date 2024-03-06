package com.github.sniffity.virgillib.navigation.pathfinder;

import com.github.sniffity.virgillib.navigation.VLPathNavigationRegion;
import com.github.sniffity.virgillib.navigation.pathfinder.node.VLBinaryHeap;
import com.github.sniffity.virgillib.navigation.pathfinder.node.VLNode;
import com.github.sniffity.virgillib.navigation.pathfinder.node.evaluator.VLNodeEvaluator;
import com.github.sniffity.virgillib.navigation.pathfinder.node.VLTarget;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.metrics.MetricCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class VLPathFinder {

    private static final float FUDGING = 1.5F;
    private final VLNode[] neighbors = new VLNode[32];
    private final int maxVisitedNodes;
    private final VLNodeEvaluator nodeEvaluator;
    private static final boolean DEBUG = false;

    //PathFinders work via BinaryHeaps (https://www.youtube.com/watch?v=AE5I0xACpZs)
    //Each element in the BinaryHeap is a Node
    //Each Node has an X, Y and Z position
    //Each Node also has a Hash value, which is obtained by hashing the X, Y and Z positions
    //This Hash Value is a unique identifier to each node

    //Of note, a FINAL BinaryHeap is declared for each PathFinder, assigned to each mob
    private final VLBinaryHeap openSet = new VLBinaryHeap();

    public VLPathFinder(VLNodeEvaluator pNodeEvaluator, int pMaxVisitedNodes) {
        this.nodeEvaluator = pNodeEvaluator;
        this.maxVisitedNodes = pMaxVisitedNodes;
    }

    /**
     * Finds a path to one of the specified positions and post-processes it or returns null if no path could be found within given accuracy
     */
    //ToDo: Explain Method
    @Nullable
    public VLPath findPath(VLPathNavigationRegion pRegion, Mob pMob, Set<BlockPos> pTargetPositions, float pMaxRange, int pAccuracy, float pSearchDepthMultiplier) {
        //operation begins by clearing the Node BinaryHeap. When findPath is called, all previous Nodes are erased
        this.openSet.clear();
        //Now, we will begin filling the Node BinaryHeap, using the Node Evaluator...
        //We begin by clearing everything in the node evaluator, and defining the entity's parameters
        this.nodeEvaluator.prepare(pRegion, pMob);
        //We then get the start node...
        VLNode node = this.nodeEvaluator.getStart();
        if (node == null) {
            return null;
        } else {
            //Target BlockPos is now a Target
            Map<VLTarget, BlockPos> map = pTargetPositions.stream().collect(
                    Collectors.toMap(
                            p_77448_ -> this.nodeEvaluator
                                    .getGoal(
                                    (double)p_77448_.getX(),
                                    (double)p_77448_.getY(),
                                    (double)p_77448_.getZ()),
                            Function.identity()
                    )
            );
            //Find Path is called with a target block position, a starter node, a target node, max rnange, accuracy and search depth multiplier
            VLPath path = this.findPath(pRegion.getProfiler(), node, map, pMaxRange, pAccuracy, pSearchDepthMultiplier);
            this.nodeEvaluator.done();
            return path;
        }
    }

    @Nullable
    private VLPath findPath(ProfilerFiller pProfiler, VLNode pNode, Map<VLTarget, BlockPos> pTargetPos, float pMaxRange, int pAccuracy, float pSearchDepthMultiplier) {
        pProfiler.push("find_path");
        pProfiler.markForCharting(MetricCategory.PATH_FINDING);
        Set<VLTarget> set = pTargetPos.keySet();
        /**
         * The total cost of all path points up to this one. Corresponds to the A* g-score.
         */
        //Starter node g = 0.0f;
        pNode.g = 0.0F;
        /**
         * The estimated cost from this path point to the target. Corresponds to the A* h-score.
         */

        pNode.h = this.getBestH(pNode, set);
        /**
         * The total cost of the path containing this path point. Used as sort criteria in PathHeap. Corresponds to the A* f-score.
         */
        pNode.f = pNode.h;
        this.openSet.clear();
        //Insert starter node...
        this.openSet.insert(pNode);
        Set<Node> set1 = ImmutableSet.of();
        int i = 0;
        Set<VLTarget> set2 = Sets.newHashSetWithExpectedSize(set.size());
        int j = (int)((float)this.maxVisitedNodes * pSearchDepthMultiplier);

        while(!this.openSet.isEmpty()) {
            if (++i >= j) {
                break;
            }

            VLNode node = this.openSet.pop();
            node.closed = true;

            for(VLTarget target : set) {
                if (node.distanceManhattan(target) <= (float)pAccuracy) {
                    target.setReached();
                    set2.add(target);
                }
            }

            if (!set2.isEmpty()) {
                break;
            }

            if (!(node.distanceTo(pNode) >= pMaxRange)) {
                int k = this.nodeEvaluator.getNeighbors(this.neighbors, node);

                for(int l = 0; l < k; ++l) {
                    VLNode node1 = this.neighbors[l];
                    float f = this.distance(node, node1);
                    node1.walkedDistance = node.walkedDistance + f;
                    float f1 = node.g + f + node1.costMalus;
                    if (node1.walkedDistance < pMaxRange && (!node1.inOpenSet() || f1 < node1.g)) {
                        node1.cameFrom = node;
                        node1.g = f1;
                        node1.h = this.getBestH(node1, set) * 1.5F;
                        if (node1.inOpenSet()) {
                            this.openSet.changeCost(node1, node1.g + node1.h);
                        } else {
                            node1.f = node1.g + node1.h;
                            this.openSet.insert(node1);
                        }
                    }
                }
            }
        }

        Optional<VLPath> optional = !set2.isEmpty() ?
                set2.stream()
                .map(p_77454_ -> this.reconstructPath(p_77454_.getBestNode(), pTargetPos.get(p_77454_), true))
                .min(Comparator.comparingInt(VLPath::getNodeCount))
                :
                set.stream()
                .map(p_77451_ -> this.reconstructPath(p_77451_.getBestNode(), pTargetPos.get(p_77451_), false))
                .min(Comparator.comparingDouble(VLPath::getDistToTarget).thenComparingInt(VLPath::getNodeCount));
        pProfiler.pop();
        return optional.isEmpty() ? null : optional.get();
    }

    protected float distance(VLNode pFirst, VLNode pSecond) {
        return pFirst.distanceTo(pSecond);
    }

    private float getBestH(VLNode pNode, Set<VLTarget> pTargets) {
        float f = Float.MAX_VALUE;

        for(VLTarget target : pTargets) {
            float f1 = pNode.distanceTo(target);
            target.updateBest(f1, pNode);
            f = Math.min(f1, f);
        }

        return f;
    }

    /**
     * Converts a recursive path point structure into a path
     */
    private VLPath reconstructPath(VLNode pPoint, BlockPos pTargetPos, boolean pReachesTarget) {
        List<VLNode> list = Lists.newArrayList();
        VLNode node = pPoint;
        list.add(0, pPoint);

        while(node.cameFrom != null) {
            node = node.cameFrom;
            list.add(0, node);
        }

        return new VLPath(list, pTargetPos, pReachesTarget);
    }
}
