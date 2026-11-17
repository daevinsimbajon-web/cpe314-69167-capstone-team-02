package com.triviagame.online.bank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Recursive traversal supports arbitrary category depth and stops malformed cycles. */
public final class CategoryTree {
    private final Map<UUID, List<UUID>> children = new HashMap<>();
    private final Set<UUID> nodes = new HashSet<>();

    public CategoryTree(List<CategoryBranch> branches) {
        for (CategoryBranch branch : branches) {
            nodes.add(branch.id());
            if (branch.parentId() != null) {
                children.computeIfAbsent(branch.parentId(), key -> new ArrayList<>())
                        .add(branch.id());
            }
        }
    }

    public List<UUID> descendants(UUID root) {
        if (!nodes.contains(root)) {
            throw new IllegalArgumentException("Choose one of your own categories.");
        }
        List<UUID> result = new ArrayList<>();
        visit(root, new HashSet<>(), result);
        return List.copyOf(result);
    }

    private void visit(UUID current, Set<UUID> visited, List<UUID> result) {
        if (!visited.add(current)) {
            return;
        }
        result.add(current);
        for (UUID child : children.getOrDefault(current, List.of())) {
            visit(child, visited, result);
        }
    }
}
