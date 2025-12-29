package com.example.family.command;

public class GetCommand implements Command {

    private final int id;

    public GetCommand(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}
