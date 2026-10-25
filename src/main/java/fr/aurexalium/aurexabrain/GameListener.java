package fr.aurexalium.aurexabrain;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
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

    @EventHandler(priority = EventPriority.HIGHEST)
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
        Arena arena = plugin.getArena((Player) e.getWhoClicked());
        // avant le debut (lobby / fin) on ne touche a rien, en jeu seulement l'armure est bloquee
        if (arena.getState() != Arena.State.PLAYING || e.getSlotType() == InventoryType.SlotType.ARMOR) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreakSign(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!plugin.isRegisteredSign(b)) return;
        if (e.getPlayer().hasPermission("aurexabrain.admin")) {
            plugin.unregisterSign(b);
            plugin.msg(e.getPlayer(), "&ePanneau AurexaBrain supprimé.");
        } else {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Action act = e.getAction();
        if (act != Action.RIGHT_CLICK_BLOCK && act != Action.RIGHT_CLICK_AIR) return;

        // clic droit sur un panneau
        if (act == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock().getState() instanceof Sign) {
            Block b = e.getClickedBlock();

            String pending = plugin.takePendingSign(p.getUniqueId());
            if (pending != null) {
                e.setCancelled(true);
                plugin.registerSign(b, pending);
                plugin.msg(p, "&aPanneau lié à l'arène &6" + pending + "&a.");
                return;
            }

            Arena target = plugin.getSignArena(b);
            if (target != null) {
                e.setCancelled(true);
                if (plugin.getArena(p) != null) {
                    plugin.msg(p, "&cTu es déjà dans une partie.");
                    return;
                }
                target.join(p);
                return;
            }
        }

        // objet "quitter la partie"
        ItemStack it = e.getItem();
        if (it != null && it.getType() == Material.BED && it.hasItemMeta()
                && it.getItemMeta().hasDisplayName()
                && it.getItemMeta().getDisplayName().equals(plugin.color(AurexaBrain.LEAVE_NAME))) {
            Arena a = plugin.getArena(p);
            if (a != null) {
                e.setCancelled(true);
                a.leave(p);
                plugin.msg(p, "&eTu as quitté la partie.");
            }
        }
    }
}
