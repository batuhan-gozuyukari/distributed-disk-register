package com.example.family;

import java.util.concurrent.ConcurrentHashMap;

public class InMemoryStore {
    private final ConcurrentHashMap<Integer, String> map = new ConcurrentHashMap<>();

    public void put(int id, String msg) {
        map.put(id, msg);
    }

    public String get(int id) {
        return map.get(id);
    }
}
