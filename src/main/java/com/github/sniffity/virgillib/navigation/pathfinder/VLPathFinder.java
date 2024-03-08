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
        VLNode startNode = this.nodeEvaluator.getStart();
        if (startNode == null) {
            return null;
        } else {
            //The set of BlockPos's is mapped - each blockPos is mapped to a Target, preserving the blockPos
            Map<VLTarget, BlockPos> map = pTargetPositions.stream().collect(
                    Collectors.toMap(
                            blockPosElement -> this.nodeEvaluator
                                    .getGoal(
                                    (double)blockPosElement.getX(),
                                    (double)blockPosElement.getY(),
                                    (double)blockPosElement.getZ()),
                            Function.identity()
                    )
            );
            //Find Path is called with a target block position map, a starter node, a target node, max rnange, accuracy and search depth multiplier
            VLPath path = this.findPath(pRegion.getProfiler(), startNode, map, pMaxRange, pAccuracy, pSearchDepthMultiplier);
            this.nodeEvaluator.done();
            return path;
        }
    }

    @Nullable
    private VLPath findPath(ProfilerFiller pProfiler, VLNode startNode, Map<VLTarget, BlockPos> targetBlockPosMap, float pMaxRange, int pAccuracy, float pSearchDepthMultiplier) {
        pProfiler.push("find_path");
        pProfiler.markForCharting(MetricCategory.PATH_FINDING);
        Set<VLTarget> targetSet = targetBlockPosMap.keySet();
        /**
         * The total cost of all path points up to this one. Corresponds to the A* g-score.
         */
        //A star: g(n) = cost from startNode to n
        //Cost from start to start = 0;
        startNode.g = 0.0F;

        //A star: h(n) = cost from n to target
        startNode.h = this.getBestH(startNode, targetSet);



        /**
         * The total cost of the path containing this path point. Used as sort criteria in PathHeap. Corresponds to the A* f-score.
         */
        //f(n) = g(n)+h(n)
        startNode.f = startNode.h;
        this.openSet.clear();
        //Insert starter node...
        this.openSet.insert(startNode);
        Set<Node> set1 = ImmutableSet.of();


        int i = 0;
        //create a Set with size = number of targets
        Set<VLTarget> set2 = Sets.newHashSetWithExpectedSize(targetSet.size());
        //maxVisitedNotes = followRange * 16
        //multiplier = 1.0F, at the moment, nothing changes the value
        int multipliedMaxVisitedNotes = (int)((float)this.maxVisitedNodes * pSearchDepthMultiplier);


        while(!this.openSet.isEmpty()) {
            //If the number of nodes is too much, stop fPathFinding
            if (++i >= multipliedMaxVisitedNotes) {
                break;
            }

            //returns the first Node in the Path...
            //first, this will be starter node...
            VLNode node = this.openSet.pop();
            //Closes the return node...
            node.closed = true;
            //For each target...
            for(VLTarget target : targetSet) {
                if (node.distanceManhattan(target) <= (float)pAccuracy) {
                    //If the distance from the evaluated node to the target is less than the accuracy...
                    //Hence, if it's close enough...
                    target.setReached();
                    //Marked the target as reached
                    set2.add(target);
                }
            }
            //As soon as we reach one of the targets, stop PathFinding
            if (!set2.isEmpty()) {
                break;
            }
            //If we are still within the maxPathFindingRange
            if (!(node.distanceTo(startNode) >= pMaxRange)) {
                //get theNeighbors for the currently evaluated node
                int k = this.nodeEvaluator.getNeighbors(this.neighbors, node);

                for(int l = 0; l < k; ++l) {
                    VLNode node1 = this.neighbors[l];
                    float f = this.distance(node, node1);
                    node1.walkedDistance = node.walkedDistance + f;
                    float f1 = node.g + f + node1.costMalus;
                    if (node1.walkedDistance < pMaxRange && (!node1.inOpenSet() || f1 < node1.g)) {
                        node1.cameFrom = node;
                        node1.g = f1;
                        node1.h = this.getBestH(node1, targetSet) * 1.5F;
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
                .map(p_77454_ -> this.reconstructPath(p_77454_.getBestNode(), targetBlockPosMap.get(p_77454_), true))
                .min(Comparator.comparingInt(VLPath::getNodeCount))
                :
                targetSet.stream()
                .map(p_77451_ -> this.reconstructPath(p_77451_.getBestNode(), targetBlockPosMap.get(p_77451_), false))
                .min(Comparator.comparingDouble(VLPath::getDistToTarget).thenComparingInt(VLPath::getNodeCount));
        pProfiler.pop();
        return optional.isEmpty() ? null : optional.get();
    }

    protected float distance(VLNode pFirst, VLNode pSecond) {
        return pFirst.distanceTo(pSecond);
    }


    //Calculates the hValue from the Node to the Target
    private float getBestH(VLNode pNode, Set<VLTarget> pTargets) {
        float f = Float.MAX_VALUE;
        //hValue is based on distance to target
        //for each of the Targets, calculate distance to Target
        //then, if a set, return the distance value for the Target that's the least distance to the target
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
