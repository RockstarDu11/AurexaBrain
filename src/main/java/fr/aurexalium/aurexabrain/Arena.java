package fr.aurexalium.aurexabrain;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Arena {

    public enum State { WAITING, STARTING, PLAYING, ENDING }

    private static class Saved {
        Location location;
        ItemStack[] contents;
        ItemStack[] armor;
        GameMode mode;
        double health;
        int food;
        int level;
        float exp;
    }

    private static class Snap {
        final int id;
        final byte data;
        Snap(int id, byte data) { this.id = id; this.data = data; }
    }

    private final AurexaBrain plugin;
    private final String name;
    private String type = "1v1";
    private int minPlayers = 2;
    private int maxPlayers = 2;

    private Location lobby, redSpawn, blueSpawn;
    private Cuboid region, redBed, blueBed;

    private State state = State.WAITING;
    private final Set<UUID> members = new LinkedHashSet<UUID>();
    private final Map<UUID, Team> teams = new HashMap<UUID, Team>();
    private final Map<UUID, Saved> saved = new HashMap<UUID, Saved>();
    private final Map<Team, Integer> scores = new EnumMap<Team, Integer>(Team.class);
    private final Map<Location, Snap> changes = new HashMap<Location, Snap>();
    private final Map<UUID, UUID> lastHit = new HashMap<UUID, UUID>();
    private final Map<UUID, Long> lastHitTime = new HashMap<UUID, Long>();
    private BukkitTask countdownTask;
    private int countdown;

    public Arena(AurexaBrain plugin, String name, String type) {
        this.plugin = plugin;
        this.name = name;
        setType(type);
    }

    // ------------------------------------------------------------
    //  Configuration de l'arene
    // ------------------------------------------------------------

    public void setType(String t) {
        if (t.equalsIgnoreCase("2v2")) {
            this.type = "2v2"; this.minPlayers = 4; this.maxPlayers = 4;
        } else if (t.equalsIgnoreCase("arene")) {
            this.type = "arene"; this.minPlayers = 2; this.maxPlayers = 8;
        } else {
            this.type = "1v1"; this.minPlayers = 2; this.maxPlayers = 2;
        }
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public int getMinPlayers() { return minPlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getPlayerCount() { return members.size(); }
    public State getState() { return state; }
    public String getStateName() { return state.name(); }

    public void setPlayers(int min, int max) { this.minPlayers = min; this.maxPlayers = max; }
    public void setLobby(Location l) { this.lobby = l; }
    public void setSpawn(Team t, Location l) { if (t == Team.RED) redSpawn = l; else blueSpawn = l; }
    public void setBed(Team t, Cuboid c) { if (t == Team.RED) redBed = c; else blueBed = c; }
    public void setRegion(Cuboid c) { this.region = c; }

    public boolean isConfigured() {
        return lobby != null && redSpawn != null && blueSpawn != null
                && region != null && redBed != null && blueBed != null;
    }

    public String info() {
        return "&7Type : &f" + type + " &7(" + minPlayers + "-" + maxPlayers + " joueurs)\n"
                + "&7Lobby : " + ok(lobby != null) + " &7Spawn rouge : " + ok(redSpawn != null)
                + " &7Spawn bleu : " + ok(blueSpawn != null) + "\n"
                + "&7Lit rouge : " + ok(redBed != null) + " &7Lit bleu : " + ok(blueBed != null)
                + " &7Zone de jeu : " + ok(region != null);
    }

    private String ok(boolean b) { return b ? "&a✔" : "&c✘"; }

    public void save(ConfigurationSection s) {
        s.set("type", type);
        s.set("min", minPlayers);
        s.set("max", maxPlayers);
        s.set("lobby", lobby);
        s.set("red-spawn", redSpawn);
        s.set("blue-spawn", blueSpawn);
        if (region != null) region.save(s.createSection("region"));
        if (redBed != null) redBed.save(s.createSection("red-bed"));
        if (blueBed != null) blueBed.save(s.createSection("blue-bed"));
    }

    public static Arena load(AurexaBrain plugin, String name, ConfigurationSection s) {
        Arena a = new Arena(plugin, name, s.getString("type", "1v1"));
        a.minPlayers = s.getInt("min", a.minPlayers);
        a.maxPlayers = s.getInt("max", a.maxPlayers);
        a.lobby = (Location) s.get("lobby");
        a.redSpawn = (Location) s.get("red-spawn");
        a.blueSpawn = (Location) s.get("blue-spawn");
        a.region = Cuboid.load(s.getConfigurationSection("region"));
        a.redBed = Cuboid.load(s.getConfigurationSection("red-bed"));
        a.blueBed = Cuboid.load(s.getConfigurationSection("blue-bed"));
        return a;
    }

    // ------------------------------------------------------------
    //  Rejoindre / quitter
    // ------------------------------------------------------------

    public void join(Player p) {
        if (!isConfigured()) {
            plugin.msg(p, "&cCette arène n'est pas encore configurée.");
            return;
        }
        if (state != State.WAITING && state != State.STARTING) {
            plugin.msg(p, "&cLa partie est déjà en cours.");
            return;
        }
        if (members.size() >= maxPlayers) {
            plugin.msg(p, "&cL'arène est pleine.");
            return;
        }

        Saved s = new Saved();
        s.location = p.getLocation();
        s.contents = p.getInventory().getContents();
        s.armor = p.getInventory().getArmorContents();
        s.mode = p.getGameMode();
        s.health = p.getHealth();
        s.food = p.getFoodLevel();
        s.level = p.getLevel();
        s.exp = p.getExp();
        saved.put(p.getUniqueId(), s);

        members.add(p.getUniqueId());
        plugin.registerPlayer(p.getUniqueId(), this);

        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        p.setGameMode(GameMode.SURVIVAL);
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setLevel(0);
        p.setExp(0f);
        p.setFireTicks(0);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.teleport(lobby);

        broadcast("&6" + p.getName() + " &erejoint la partie &7(" + members.size() + "/" + maxPlayers + ")");

        if (state == State.WAITING && members.size() >= minPlayers) {
            startCountdown();
        }
        updateScoreboards();
    }

    public void leave(Player p) {
        UUID u = p.getUniqueId();
        if (!members.contains(u)) return;

        members.remove(u);
        teams.remove(u);
        lastHit.remove(u);
        lastHitTime.remove(u);
        plugin.unregisterPlayer(u);
        restore(p);
        p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());

        broadcast("&6" + p.getName() + " &equitte la partie.");

        if (members.isEmpty()) {
            reset();
            return;
        }

        if (state == State.STARTING && members.size() < minPlayers) {
            cancelCountdown();
            state = State.WAITING;
            broadcast("&cPas assez de joueurs, le décompte est annulé.");
        } else if (state == State.PLAYING) {
            int red = 0, blue = 0;
            for (UUID m : members) {
                Team t = teams.get(m);
                if (t == Team.RED) red++;
                else if (t == Team.BLUE) blue++;
            }
            if (red == 0) win(Team.BLUE);
            else if (blue == 0) win(Team.RED);
        }
        updateScoreboards();
    }

    public void shutdown() {
        for (UUID u : new ArrayList<UUID>(members)) {
            Player p = Bukkit.getPlayer(u);
            plugin.unregisterPlayer(u);
            if (p != null) {
                restore(p);
                p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            }
        }
        reset();
    }

    // ------------------------------------------------------------
    //  Deroulement de la partie
    // ------------------------------------------------------------

    private void startCountdown() {
        state = State.STARTING;
        countdown = plugin.getConfig().getInt("start-countdown", 15);
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            public void run() {
                if (countdown <= 0) {
                    cancelCountdown();
                    start();
                    return;
                }
                if (countdown <= 5 || countdown % 10 == 0 || countdown == 15) {
                    broadcast("&eDébut dans &6" + countdown + "&e seconde(s)...");
                    playSound(Sound.NOTE_PLING);
                }
                countdown--;
            }
        }, 0L, 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void start() {
        state = State.PLAYING;
        scores.put(Team.RED, 0);
        scores.put(Team.BLUE, 0);
        changes.clear();
        lastHit.clear();
        lastHitTime.clear();

        int i = 0;
        for (UUID u : members) {
            teams.put(u, (i++ % 2 == 0) ? Team.RED : Team.BLUE);
        }
        for (UUID u : members) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            Team t = teams.get(u);
            plugin.msg(p, "&aLa partie commence ! Tu es dans l'équipe " + t.getDisplay() + "&a.");
            respawn(p);
        }
        broadcast("&eTouche le lit adverse ! Premier à &6" + plugin.getConfig().getInt("score-to-win", 5) + " &epoints gagne.");
        playSound(Sound.LEVEL_UP);
        updateScoreboards();
    }

    private void respawn(Player p) {
        Team t = teams.get(p.getUniqueId());
        if (t == null) return;
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.setVelocity(new Vector(0, 0, 0));
        p.teleport(t == Team.RED ? redSpawn : blueSpawn);
        giveKit(p);
    }

    private void giveKit(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setHelmet(unbreakable(new ItemStack(Material.IRON_HELMET)));
        inv.setChestplate(unbreakable(new ItemStack(Material.IRON_CHESTPLATE)));
        inv.setLeggings(unbreakable(new ItemStack(Material.IRON_LEGGINGS)));
        inv.setBoots(unbreakable(new ItemStack(Material.IRON_BOOTS)));
        inv.setItem(0, unbreakable(new ItemStack(Material.IRON_SWORD)));
        ItemStack pick = new ItemStack(Material.IRON_PICKAXE);
        pick.addEnchantment(Enchantment.DIG_SPEED, 2);
        inv.setItem(1, unbreakable(pick));
        inv.setItem(2, new ItemStack(Material.SANDSTONE, 64));
        inv.setItem(3, new ItemStack(Material.SANDSTONE, 64));
        inv.setItem(4, new ItemStack(Material.GOLDEN_APPLE, 3));
        p.updateInventory();
    }

    private ItemStack unbreakable(ItemStack i) {
        ItemMeta m = i.getItemMeta();
        m.spigot().setUnbreakable(true);
        i.setItemMeta(m);
        return i;
    }

    public void handleMove(Player p, Location to) {
        if (state != State.PLAYING) return;
        Team t = teams.get(p.getUniqueId());
        if (t == null) return;
        Cuboid enemyBed = (t == Team.RED) ? blueBed : redBed;
        if (enemyBed.contains(to)) {
            score(p, t);
            return;
        }
        if (to.getY() < region.getMinY()) {
            kill(p);
        }
    }

    private void score(Player scorer, Team t) {
        int s = scores.get(t) + 1;
        scores.put(t, s);
        broadcast(t.getChat() + scorer.getName() + " &ea touché le lit ! &7(&c" + scores.get(Team.RED) + " &7- &9" + scores.get(Team.BLUE) + "&7)");
        giveCoins(scorer, plugin.getConfig().getInt("coins.point", 10));
        playSound(Sound.LEVEL_UP);

        if (s >= plugin.getConfig().getInt("score-to-win", 5)) {
            win(t);
            return;
        }
        // nouvelle manche : on remet la map et tout le monde sur sa plateforme
        restoreBlocks();
        lastHit.clear();
        lastHitTime.clear();
        for (UUID u : members) {
            Player m = Bukkit.getPlayer(u);
            if (m != null) respawn(m);
        }
        updateScoreboards();
    }

    public void kill(Player victim) {
        Team vt = teams.get(victim.getUniqueId());
        if (vt == null) return;
        Player killer = null;
        UUID k = lastHit.get(victim.getUniqueId());
        Long time = lastHitTime.get(victim.getUniqueId());
        if (k != null && time != null && System.currentTimeMillis() - time < 10000L) {
            killer = Bukkit.getPlayer(k);
        }
        String vn = vt.getChat() + victim.getName();
        if (killer != null && teams.get(killer.getUniqueId()) != null) {
            broadcast(vn + " &ea été tué par " + teams.get(killer.getUniqueId()).getChat() + killer.getName() + "&e.");
            giveCoins(killer, plugin.getConfig().getInt("coins.kill", 5));
        } else {
            broadcast(vn + " &eest mort.");
        }
        lastHit.remove(victim.getUniqueId());
        lastHitTime.remove(victim.getUniqueId());
        respawn(victim);
    }

    private void win(Team t) {
        state = State.ENDING;
        broadcast("&6&l★ &eL'équipe " + t.getDisplay() + " &egagne la partie ! &7(&c" + scores.get(Team.RED) + " &7- &9" + scores.get(Team.BLUE) + "&7)");
        playSound(Sound.LEVEL_UP);
        for (UUID u : members) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            if (teams.get(u) == t) giveCoins(p, plugin.getConfig().getInt("coins.win", 100));
            else giveCoins(p, plugin.getConfig().getInt("coins.lose", 20));
        }
        updateScoreboards();
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            public void run() { finish(); }
        }, 100L);
    }

    private void finish() {
        for (UUID u : new ArrayList<UUID>(members)) {
            Player p = Bukkit.getPlayer(u);
            plugin.unregisterPlayer(u);
            if (p != null) {
                restore(p);
                p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            }
        }
        reset();
    }

    private void reset() {
        cancelCountdown();
        restoreBlocks();
        members.clear();
        teams.clear();
        scores.clear();
        lastHit.clear();
        lastHitTime.clear();
        saved.clear();
        state = State.WAITING;
    }

    private void giveCoins(Player p, int amount) {
        if (amount <= 0) return;
        String cmd = plugin.getConfig().getString("coins-command", "");
        if (cmd == null || cmd.trim().isEmpty()) return;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                cmd.replace("{player}", p.getName()).replace("{amount}", String.valueOf(amount)));
        p.sendMessage(plugin.getPrefix() + plugin.color("&a+" + amount + " coins"));
    }

    // ------------------------------------------------------------
    //  Blocs et degats
    // ------------------------------------------------------------

    public boolean canPlace(Block b) {
        Location l = b.getLocation();
        return state == State.PLAYING && region.contains(l) && !redBed.contains(l) && !blueBed.contains(l);
    }

    public boolean canBreak(Block b) {
        Location l = b.getLocation();
        return state == State.PLAYING && region.contains(l) && !redBed.contains(l) && !blueBed.contains(l);
    }

    @SuppressWarnings("deprecation")
    public void recordChange(Location loc, Material m, byte data) {
        Location key = loc.getBlock().getLocation();
        if (!changes.containsKey(key)) {
            changes.put(key, new Snap(m.getId(), data));
        }
    }

    @SuppressWarnings("deprecation")
    private void restoreBlocks() {
        for (Map.Entry<Location, Snap> e : changes.entrySet()) {
            e.getKey().getBlock().setTypeIdAndData(e.getValue().id, e.getValue().data, false);
        }
        changes.clear();
    }

    public void handleDamage(EntityDamageEvent e, Player victim) {
        if (state != State.PLAYING) {
            e.setCancelled(true);
            return;
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent) {
            Entity d = ((EntityDamageByEntityEvent) e).getDamager();
            if (d instanceof Projectile && ((Projectile) d).getShooter() instanceof Entity) {
                d = (Entity) ((Projectile) d).getShooter();
            }
            if (d instanceof Player) {
                Player a = (Player) d;
                Team at = teams.get(a.getUniqueId());
                Team vt = teams.get(victim.getUniqueId());
                if (at == null || at == vt) {
                    e.setCancelled(true);
                    return;
                }
                lastHit.put(victim.getUniqueId(), a.getUniqueId());
                lastHitTime.put(victim.getUniqueId(), System.currentTimeMillis());
            }
        }
        if (e.getFinalDamage() >= victim.getHealth()) {
            e.setCancelled(true);
            kill(victim);
        }
    }

    // ------------------------------------------------------------
    //  Divers
    // ------------------------------------------------------------

    private void restore(Player p) {
        Saved s = saved.remove(p.getUniqueId());
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.setFireTicks(0);
        if (s == null) return;
        p.getInventory().setContents(s.contents);
        p.getInventory().setArmorContents(s.armor);
        p.setGameMode(s.mode);
        p.setHealth(Math.min(s.health, p.getMaxHealth()));
        p.setFoodLevel(s.food);
        p.setLevel(s.level);
        p.setExp(s.exp);
        p.teleport(s.location);
        p.updateInventory();
    }

    private void broadcast(String s) {
        for (UUID u : members) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendMessage(plugin.getPrefix() + plugin.color(s));
        }
    }

    private void playSound(Sound sound) {
        for (UUID u : members) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.playSound(p.getLocation(), sound, 1f, 1f);
        }
    }

    private void updateScoreboards() {
        for (UUID u : members) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;

            Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective o = sb.registerNewObjective("aurexabrain", "dummy");
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
            o.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "AurexaBrain");

            // couleur des pseudos par equipe
            org.bukkit.scoreboard.Team sbRed = sb.registerNewTeam("red");
            sbRed.setPrefix(ChatColor.RED.toString());
            org.bukkit.scoreboard.Team sbBlue = sb.registerNewTeam("blue");
            sbBlue.setPrefix(ChatColor.BLUE.toString());
            for (UUID m : members) {
                Player mp = Bukkit.getPlayer(m);
                Team mt = teams.get(m);
                if (mp == null || mt == null) continue;
                if (mt == Team.RED) sbRed.addPlayer(mp);
                else sbBlue.addPlayer(mp);
            }

            List<String> lines = new ArrayList<String>();
            if (state == State.WAITING || state == State.STARTING) {
                String shortName = name.length() > 14 ? name.substring(0, 14) : name;
                lines.add(ChatColor.GRAY + "Arène :");
                lines.add(ChatColor.WHITE + shortName);
                lines.add(ChatColor.RESET.toString());
                lines.add(ChatColor.GRAY + "Joueurs :");
                lines.add(ChatColor.YELLOW + "" + members.size() + "/" + maxPlayers);
            } else {
                int toWin = plugin.getConfig().getInt("score-to-win", 5);
                Team mine = teams.get(u);
                lines.add(ChatColor.RED + "Rouge: " + ChatColor.WHITE + scores.get(Team.RED));
                lines.add(ChatColor.BLUE + "Bleu: " + ChatColor.WHITE + scores.get(Team.BLUE));
                lines.add(ChatColor.RESET.toString());
                lines.add(ChatColor.GRAY + "Objectif: " + ChatColor.WHITE + toWin);
                lines.add(ChatColor.RESET.toString() + ChatColor.RESET);
                lines.add(ChatColor.GRAY + "Ton équipe:");
                lines.add(mine == null ? ChatColor.WHITE + "-" : plugin.color(mine.getDisplay()));
            }
            int score = lines.size();
            for (String l : lines) {
                o.getScore(l).setScore(score--);
            }
            p.setScoreboard(sb);
        }
    }
}
