package fr.aurexalium.aurexabrain;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@SuppressWarnings("deprecation")
public class GameListener implements Listener {

    private final AurexaBrain plugin;

    public GameListener(AurexaBrain plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Arena a = plugin.getArena(e.getPlayer());
        if (a != null) a.leave(e.getPlayer());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Location f = e.getFrom();
        Location t = e.getTo();
        if (f.getBlockX() == t.getBlockX() && f.getBlockY() == t.getBlockY() && f.getBlockZ() == t.getBlockZ()) return;
        Arena a = plugin.getArena(e.getPlayer());
        if (a != null) a.handleMove(e.getPlayer(), t);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Arena a = plugin.getArena(e.getPlayer());
        if (a == null) return;
        if (!a.canPlace(e.getBlock())) {
            e.setCancelled(true);
            return;
        }
        BlockState old = e.getBlockReplacedState();
        a.recordChange(e.getBlock().getLocation(), old.getType(), old.getRawData());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Arena a = plugin.getArena(e.getPlayer());
        if (a == null) return;
        Block b = e.getBlock();
        if (!a.canBreak(b)) {
            e.setCancelled(true);
            return;
        }
        a.recordChange(b.getLocation(), b.getType(), b.getData());
        // on casse nous-memes le bloc pour qu'il ne drop rien
        e.setCancelled(true);
        b.setType(Material.AIR);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        Arena a = plugin.getArena(p);
        if (a != null) a.handleDamage(e, p);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player && plugin.getArena((Player) e.getEntity()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (plugin.getArena(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (plugin.getArena((Player) e.getWhoClicked()) == null) return;
        if (e.getSlotType() == InventoryType.SlotType.ARMOR) e.setCancelled(true);
    }
}
