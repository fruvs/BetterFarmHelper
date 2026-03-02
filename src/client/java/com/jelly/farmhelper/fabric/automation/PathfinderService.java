package com.jelly.farmhelper.fabric.automation;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class PathfinderService {
    private static final int[][] NEIGHBOR_OFFSETS = new int[][]{
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };
    private static final int DEFAULT_MAX_NODES = 1_800;

    public List<Vec3d> findPath(MinecraftClient client, Vec3d start, Vec3d goal, int maxNodes) {
        if (client == null || client.world == null || client.player == null) {
            return List.of();
        }
        BlockPos startFeet = toFeetPos(start);
        BlockPos goalFeet = resolveWalkableFeet(client, toFeetPos(goal), 4);
        if (goalFeet == null) {
            return List.of();
        }

        int nodeBudget = maxNodes <= 0 ? DEFAULT_MAX_NODES : maxNodes;
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<BlockPos, Node> nodes = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        Node startNode = new Node(startFeet, null, 0.0, heuristic(startFeet, goalFeet));
        open.add(startNode);
        nodes.put(startFeet, startNode);
        int expanded = 0;

        while (!open.isEmpty() && expanded < nodeBudget) {
            Node current = open.poll();
            if (current == null) {
                break;
            }
            if (!closed.add(current.pos)) {
                continue;
            }
            expanded++;

            if (current.pos.isWithinDistance(goalFeet, 1.5)) {
                return buildPath(current);
            }

            for (int[] delta : NEIGHBOR_OFFSETS) {
                BlockPos candidateXZ = current.pos.add(delta[0], 0, delta[1]);
                BlockPos candidateFeet = resolveWalkableFeet(client, candidateXZ, 2);
                if (candidateFeet == null || closed.contains(candidateFeet)) {
                    continue;
                }

                double tentativeG = current.g + movementCost(current.pos, candidateFeet);
                Node known = nodes.get(candidateFeet);
                if (known != null && tentativeG >= known.g) {
                    continue;
                }

                Node next = new Node(candidateFeet, current, tentativeG, heuristic(candidateFeet, goalFeet));
                nodes.put(candidateFeet, next);
                open.add(next);
            }
        }

        return List.of();
    }

    private BlockPos toFeetPos(Vec3d pos) {
        return BlockPos.ofFloored(pos.x, pos.y, pos.z);
    }

    private BlockPos resolveWalkableFeet(MinecraftClient client, BlockPos around, int verticalScan) {
        for (int dy = 1; dy >= -verticalScan; dy--) {
            BlockPos candidate = around.add(0, dy, 0);
            if (isWalkable(client, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isWalkable(MinecraftClient client, BlockPos feetPos) {
        if (client.world == null) {
            return false;
        }
        BlockState feet = client.world.getBlockState(feetPos);
        BlockState head = client.world.getBlockState(feetPos.up());
        BlockState floor = client.world.getBlockState(feetPos.down());
        return feet.isAir() && head.isAir() && !floor.isAir();
    }

    private double heuristic(BlockPos from, BlockPos to) {
        int dx = Math.abs(from.getX() - to.getX());
        int dy = Math.abs(from.getY() - to.getY());
        int dz = Math.abs(from.getZ() - to.getZ());
        return dx + dz + dy * 1.5;
    }

    private double movementCost(BlockPos from, BlockPos to) {
        int dx = Math.abs(from.getX() - to.getX());
        int dy = Math.abs(from.getY() - to.getY());
        int dz = Math.abs(from.getZ() - to.getZ());
        return (dx == 1 && dz == 1 ? 1.4 : 1.0) + dy * 0.7;
    }

    private List<Vec3d> buildPath(Node end) {
        List<Vec3d> reversed = new ArrayList<>();
        Node cursor = end;
        while (cursor != null) {
            reversed.add(new Vec3d(
                    cursor.pos.getX() + 0.5,
                    cursor.pos.getY(),
                    cursor.pos.getZ() + 0.5
            ));
            cursor = cursor.parent;
        }
        List<Vec3d> path = new ArrayList<>(reversed.size());
        for (int i = reversed.size() - 1; i >= 0; i--) {
            path.add(reversed.get(i));
        }
        return path;
    }

    private static final class Node {
        private final BlockPos pos;
        private final Node parent;
        private final double g;
        private final double f;

        private Node(BlockPos pos, Node parent, double g, double h) {
            this.pos = pos;
            this.parent = parent;
            this.g = g;
            this.f = g + h;
        }
    }
}
