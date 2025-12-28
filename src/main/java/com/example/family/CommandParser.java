package com.example.family;

import com.example.family.command.Command;
import com.example.family.command.GetCommand;
import com.example.family.command.SetCommand;

public class CommandParser {

    public Command parse(String line) {
        if (line == null) {
            throw new IllegalArgumentException("null line");
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("empty line");
        }

        String[] parts = trimmed.split("\\s+", 3);
        String op = parts[0].toUpperCase();

        if ("GET".equals(op)) {
            if (parts.length != 2) {
                throw new IllegalArgumentException("GET <id>");
            }
            int id = Integer.parseInt(parts[1]);
            return new GetCommand(id);
        }

        if ("SET".equals(op)) {
            if (parts.length < 3) {
                throw new IllegalArgumentException("SET <id> <message>");
            }
            int id = Integer.parseInt(parts[1]);
            String message = parts[2]; 
            return new SetCommand(id, message);
        }

        throw new IllegalArgumentException("Unknown command: " + op);
    }
}
