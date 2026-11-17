package com.triviagame.online;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.triviagame.online.bank.CategoryBranch;
import com.triviagame.online.bank.CategoryTree;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class CategoryTreeTest {
    @Test
    void traversalHandlesNestedBranchesCyclesAndMissingRoots() {
        UUID root = UUID.randomUUID(), child = UUID.randomUUID(), leaf = UUID.randomUUID();
        var tree =
                new CategoryTree(
                        List.of(
                                new CategoryBranch(root, "Root", leaf),
                                new CategoryBranch(child, "Child", root),
                                new CategoryBranch(leaf, "Leaf", child)));
        assertThat(tree.descendants(root)).containsExactly(root, child, leaf);
        assertThatThrownBy(() -> tree.descendants(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
