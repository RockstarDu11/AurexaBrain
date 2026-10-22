package fr.aurexalium.aurexabrain;

import org.bukkit.Color;

public enum Team {
    RED("&cRouge", "&c", Color.RED),
    BLUE("&9Bleu", "&9", Color.BLUE);

    private final String display;
    private final String chat;
    private final Color color;

    Team(String display, String chat, Color color) {
        this.display = display;
        this.chat = chat;
        this.color = color;
    }

    public String getDisplay() { return display; }
    public String getChat() { return chat; }
    public Color getColor() { return color; }

    public Team opposite() {
        return this == RED ? BLUE : RED;
    }

    public static Team fromString(String s) {
        if (s == null) return null;
        if (s.equalsIgnoreCase("red") || s.equalsIgnoreCase("rouge")) return RED;
        if (s.equalsIgnoreCase("blue") || s.equalsIgnoreCase("bleu")) return BLUE;
        return null;
    }
}
