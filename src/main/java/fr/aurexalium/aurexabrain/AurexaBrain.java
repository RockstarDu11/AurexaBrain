package fr.aurexalium.aurexabrain;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class AurexaBrain extends JavaPlugin {

    private final Map<String, Arena> arenas = new LinkedHashMap<String, Arena>();
    private final Map<UUID, Arena> playerArenas = new HashMap<UUID, Arena>();
    private final Map<UUID, Location> pos1 = new HashMap<UUID, Location>();
    private final Map<UUID, Location> pos2 = new HashMap<UUID, Location>();
    private File arenasFile;
    private String prefix;

    public static final String LEAVE_NAME = "&cQuitter la partie";
    // panneaux : "monde;x;y;z" -> nom de l'arene (en minuscules)
    private final Map<String, String> signs = new HashMap<String, String>();
    private final Map<UUID, String> pendingSigns = new HashMap<UUID, String>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        prefix = color(getConfig().getString("prefix", "&6&lAurexalium &c&l>> &e"));
        arenasFile = new File(getDataFolder(), "arenas.yml");
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        // on charge les arenes apres le chargement des mondes
        Bukkit.getScheduler().runTask(this, new Runnable() {
            public void run() { loadArenas(); }
        });
        getLogger().info("AurexaBrain active.");
    }

    @Override
    public void onDisable() {
        for (Arena a : arenas.values()) a.shutdown();
        playerArenas.clear();
    }

    // ------------------------------------------------------------
    //  Utilitaires
    // ------------------------------------------------------------

    public String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    public String getPrefix() { return prefix; }
    public void msg(CommandSender s, String text) { s.sendMessage(prefix + color(text)); }

    public Arena getArena(Player p) { return playerArenas.get(p.getUniqueId()); }
    public void registerPlayer(UUID u, Arena a) { playerArenas.put(u, a); }
    public void unregisterPlayer(UUID u) { playerArenas.remove(u); }
    private Arena findArena(String n) { return arenas.get(n.toLowerCase()); }

    // ------------------------------------------------------------
    //  Sauvegarde
    // ------------------------------------------------------------

    private void saveArenas() {
        YamlConfiguration cfg = new YamlConfiguration();
        ConfigurationSection root = cfg.createSection("arenas");
        for (Arena a : arenas.values()) {
            a.save(root.createSection(a.getName()));
        }
        List<String> signList = new ArrayList<String>();
        for (Map.Entry<String, String> e : signs.entrySet()) {
            signList.add(e.getKey() + ";" + e.getValue());
        }
        cfg.set("signs", signList);
        try {
            cfg.save(arenasFile);
        } catch (IOException e) {
            getLogger().severe("Impossible de sauvegarder arenas.yml : " + e.getMessage());
        }
    }

    private void loadArenas() {
        arenas.clear();
        if (!arenasFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(arenasFile);
        ConfigurationSection root = cfg.getConfigurationSection("arenas");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            arenas.put(key.toLowerCase(), Arena.load(this, key, root.getConfigurationSection(key)));
        }
        getLogger().info(arenas.size() + " arene(s) chargee(s).");
        signs.clear();
        for (String s : cfg.getStringList("signs")) {
            String[] p = s.split(";");
            if (p.length == 5) signs.put(p[0] + ";" + p[1] + ";" + p[2] + ";" + p[3], p[4]);
        }
        updateAllSigns();
    }

    // ------------------------------------------------------------
    //  Commandes
    // ------------------------------------------------------------

    private void help(CommandSender s) {
        msg(s, "&6/hb join <arène> &e- rejoindre une partie");
        msg(s, "&6/hb leave &e- quitter la partie");
        msg(s, "&6/hb list &e- liste des arènes");
        if (s.hasPermission("aurexabrain.admin")) {
            msg(s, "&c--- Admin ---");
            msg(s, "&6/hb create <nom> <1v1|2v2|arene>");
            msg(s, "&6/hb delete <nom>");
            msg(s, "&6/hb setlobby <nom>");
            msg(s, "&6/hb setspawn <nom> <red|blue>");
            msg(s, "&6/hb pos1 &e/ &6/hb pos2 &e(sélection)");
            msg(s, "&6/hb setbed <nom> <red|blue> &e(tiens-toi SUR le lit)");
            msg(s, "&6/hb setregion <nom> &e(zone de jeu, utilise la sélection)");
            msg(s, "&6/hb sign <nom> &e(puis clic droit sur un panneau vierge)");
            msg(s, "&6/hb setplayers <nom> <min> <max>");
            msg(s, "&6/hb info <nom>");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase();

        if (sub.equals("list")) {
            if (arenas.isEmpty()) {
                msg(sender, "&cAucune arène.");
                return true;
            }
            for (Arena a : arenas.values()) {
                msg(sender, "&6" + a.getName() + " &7[" + a.getType() + "] &f" + a.getStateName()
                        + " &7(" + a.getPlayerCount() + "/" + a.getMaxPlayers() + ")"
                        + (a.isConfigured() ? "" : " &c[non configurée]"));
            }
            return true;
        }

        if (!(sender instanceof Player)) {
            msg(sender, "&cCommande réservée aux joueurs.");
            return true;
        }
        Player p = (Player) sender;

        if (sub.equals("join")) {
            if (args.length < 2) { msg(p, "&cUtilisation : &6/hb join <arène>"); return true; }
            if (getArena(p) != null) { msg(p, "&cTu es déjà dans une partie (&6/hb leave&c)."); return true; }
            Arena a = findArena(args[1]);
            if (a == null) { msg(p, "&cArène introuvable."); return true; }
            a.join(p);
            return true;
        }
        if (sub.equals("leave")) {
            Arena a = getArena(p);
            if (a == null) { msg(p, "&cTu n'es dans aucune partie."); return true; }
            a.leave(p);
            msg(p, "&eTu as quitté la partie.");
            return true;
        }

        // ---------- commandes admin ----------
        if (!p.hasPermission("aurexabrain.admin")) {
            msg(p, "&cTu n'as pas la permission.");
            return true;
        }

        if (sub.equals("pos1")) {
            pos1.put(p.getUniqueId(), p.getLocation());
            msg(p, "&ePosition 1 définie.");
            return true;
        }
        if (sub.equals("pos2")) {
            pos2.put(p.getUniqueId(), p.getLocation());
            msg(p, "&ePosition 2 définie.");
            return true;
        }

        if (sub.equals("create")) {
            if (args.length < 3) { msg(p, "&cUtilisation : &6/hb create <nom> <1v1|2v2|arene>"); return true; }
            if (findArena(args[1]) != null) { msg(p, "&cCette arène existe déjà."); return true; }
            String type = args[2].toLowerCase();
            if (!type.equals("1v1") && !type.equals("2v2") && !type.equals("arene")) {
                msg(p, "&cType invalide : &61v1&c, &62v2 &cou &6arene&c.");
                return true;
            }
            arenas.put(args[1].toLowerCase(), new Arena(this, args[1], type));
            saveArenas();
            msg(p, "&aArène &6" + args[1] + " &acréée en &6" + type + "&a. Configure-la avec &6/hb info " + args[1]);
            return true;
        }

        if (args.length < 2) {
            help(p);
            return true;
        }
        Arena a = findArena(args[1]);
        if (a == null) {
            msg(p, "&cArène introuvable.");
            return true;
        }

        if (sub.equals("delete")) {
            a.shutdown();
            arenas.remove(args[1].toLowerCase());
            saveArenas();
            msg(p, "&eArène supprimée.");
            return true;
        }
        if (sub.equals("info")) {
            for (String line : a.info().split("\n")) msg(p, line);
            return true;
        }
        if (sub.equals("setlobby")) {
            a.setLobby(p.getLocation());
            saveArenas();
            msg(p, "&aLobby défini.");
            return true;
        }
        if (sub.equals("setspawn")) {
            Team t = args.length > 2 ? Team.fromString(args[2]) : null;
            if (t == null) { msg(p, "&cUtilisation : &6/hb setspawn <nom> <red|blue>"); return true; }
            a.setSpawn(t, p.getLocation());
            saveArenas();
            msg(p, "&aSpawn " + t.getDisplay() + " &adéfini.");
            return true;
        }
        if (sub.equals("setbed")) {
            Team t = args.length > 2 ? Team.fromString(args[2]) : null;
            if (t == null) { msg(p, "&cUtilisation : &6/hb setbed <nom> <red|blue>"); return true; }
            // le lit = uniquement les 2 blocs du lit sur lequel tu te tiens
            Block feet = p.getLocation().getBlock();
            Block bed = null;
            if (feet.getType() == Material.BED_BLOCK) bed = feet;
            else if (feet.getRelative(BlockFace.DOWN).getType() == Material.BED_BLOCK) bed = feet.getRelative(BlockFace.DOWN);
            if (bed == null) {
                msg(p, "&cTu dois te tenir SUR un lit pour le définir.");
                return true;
            }
            Block other = bed;
            BlockFace[] faces = { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST };
            for (BlockFace f : faces) {
                Block r = bed.getRelative(f);
                if (r.getType() == Material.BED_BLOCK) { other = r; break; }
            }
            Cuboid c = new Cuboid(bed.getWorld().getName(),
                    bed.getX(), bed.getY(), bed.getZ(),
                    other.getX(), other.getY() + 1, other.getZ());
            a.setBed(t, c);
            saveArenas();
            msg(p, "&aLit " + t.getDisplay() + " &adéfini (uniquement sur le lit).");
            return true;
        }
        if (sub.equals("setregion")) {
            Cuboid c = selection(p);
            if (c == null) return true;
            a.setRegion(c);
            saveArenas();
            msg(p, "&aZone de jeu définie. Hauteur de mort : sous le bloc Y=&6" + c.getMinY() + "&a.");
            return true;
        }
        if (sub.equals("sign")) {
            pendingSigns.put(p.getUniqueId(), args[1].toLowerCase());
            msg(p, "&eFais un &6clic droit &esur un panneau vierge pour le lier à &6" + a.getName() + "&e.");
            return true;
        }
        if (sub.equals("setplayers")) {
            if (args.length < 4) { msg(p, "&cUtilisation : &6/hb setplayers <nom> <min> <max>"); return true; }
            try {
                int min = Integer.parseInt(args[2]);
                int max = Integer.parseInt(args[3]);
                if (min < 2 || max < min) { msg(p, "&cMin &6>= 2 &cet max &6>= min&c."); return true; }
                a.setPlayers(min, max);
                saveArenas();
                msg(p, "&aJoueurs : &6" + min + "&a à &6" + max + "&a.");
            } catch (NumberFormatException ex) {
                msg(p, "&cNombres invalides.");
            }
            return true;
        }

        help(p);
        return true;
    }

    // ------------------------------------------------------------
    //  Panneaux et objet "quitter"
    // ------------------------------------------------------------

    public ItemStack leaveItem() {
        ItemStack i = new ItemStack(Material.BED);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(color(LEAVE_NAME));
        List<String> lore = new ArrayList<String>();
        lore.add(color("&7Clic droit pour quitter la partie"));
        meta.setLore(lore);
        i.setItemMeta(meta);
        return i;
    }

    private String signKey(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    public String takePendingSign(UUID u) { return pendingSigns.remove(u); }

    public boolean isRegisteredSign(Block b) { return signs.containsKey(signKey(b)); }

    public Arena getSignArena(Block b) {
        String k = signs.get(signKey(b));
        return k == null ? null : arenas.get(k);
    }

    public void registerSign(Block b, String arenaKey) {
        signs.put(signKey(b), arenaKey);
        saveArenas();
        Arena a = arenas.get(arenaKey);
        if (a != null) updateSign(signKey(b), a);
    }

    public void unregisterSign(Block b) {
        signs.remove(signKey(b));
        saveArenas();
    }

    public void updateSigns(Arena a) {
        String key = a.getName().toLowerCase();
        for (Map.Entry<String, String> e : new HashMap<String, String>(signs).entrySet()) {
            if (e.getValue().equals(key)) updateSign(e.getKey(), a);
        }
    }

    private void updateAllSigns() {
        for (Map.Entry<String, String> e : new HashMap<String, String>(signs).entrySet()) {
            Arena a = arenas.get(e.getValue());
            if (a != null) updateSign(e.getKey(), a);
        }
    }

    private void updateSign(String key, Arena a) {
        String[] k = key.split(";");
        World w = Bukkit.getWorld(k[0]);
        if (w == null) return;
        Block b = w.getBlockAt(Integer.parseInt(k[1]), Integer.parseInt(k[2]), Integer.parseInt(k[3]));
        BlockState st = b.getState();
        if (!(st instanceof Sign)) return;
        Sign s = (Sign) st;

        String name = a.getName();
        if (name.length() > 13) name = name.substring(0, 13);
        String state;
        if (!a.isConfigured()) {
            state = "&4Non prete";
        } else {
            switch (a.getState()) {
                case WAITING: state = "&aEn attente"; break;
                case STARTING: state = "&eDepart..."; break;
                case PLAYING: state = "&cEn jeu"; break;
                default: state = "&cFin"; break;
            }
        }
        s.setLine(0, color("&6AurexaBrain"));
        s.setLine(1, color("&f" + name));
        s.setLine(2, color(state));
        s.setLine(3, color("&e" + a.getPlayerCount() + "/" + a.getMaxPlayers() + " &7" + a.getType()));
        s.update();
    }

    private Cuboid selection(Player p) {
        Location a = pos1.get(p.getUniqueId());
        Location b = pos2.get(p.getUniqueId());
        if (a == null || b == null) {
            msg(p, "&cFais d'abord &6/hb pos1 &cet &6/hb pos2&c.");
            return null;
        }
        if (!a.getWorld().equals(b.getWorld())) {
            msg(p, "&cLes deux positions doivent être dans le même monde.");
            return null;
        }
        return new Cuboid(a, b);
    }
}
