package com.example.family;

import family.NodeInfo;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NodeRegistry {

    private final Set<NodeInfo> nodes = ConcurrentHashMap.newKeySet();

    public void add(NodeInfo node) {
        nodes.add(node);
    }

    public void addAll(Collection<NodeInfo> others) {
        nodes.addAll(others);
    }

    // GÜNCELLEME: Stage 6 - Liste her zaman Port numarasına göre sıralı döner.
    // Böylece Node A ve Node B aynı hesaplamayı yapar.
    public List<NodeInfo> snapshot() {
        return nodes.stream()
                .sorted(java.util.Comparator.comparingInt(NodeInfo::getPort))
                .collect(java.util.stream.Collectors.toList());
    }

    public void remove(NodeInfo node) {
        nodes.remove(node);
    }
}
