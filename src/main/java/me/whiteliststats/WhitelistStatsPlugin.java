package me.whiteliststats;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class WhitelistStatsPlugin extends JavaPlugin implements Listener {

    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS = 45;
    private static final int INFO = 49;
    private static final int NEXT = 53;
    private static final int BACK = 22;

    /** Marks our menus so clicks in them can be recognised. */
    private static final class Menu implements InventoryHolder {
        private Inventory inventory;
        private final int page;
        private final boolean list;
        private final List<OfflinePlayer> players = new ArrayList<>();

        private Menu(int page, boolean list) {
            this.page = page;
            this.list = list;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // Close our menus so their items can't be taken once the listener is gone.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                player.closeInventory();
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        openList(player, 0);
        return true;
    }

    private void openList(Player viewer, int requestedPage) {
        List<OfflinePlayer> whitelist = new ArrayList<>(Bukkit.getWhitelistedPlayers());
        whitelist.sort(Comparator.comparing(this::nameOf, String.CASE_INSENSITIVE_ORDER));

        int pages = Math.max(1, (whitelist.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));

        Menu menu = new Menu(page, true);
        menu.inventory = Bukkit.createInventory(menu, 54, Component.text("Whitelisted Players"));

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, whitelist.size());
        for (int i = start; i < end; i++) {
            OfflinePlayer target = whitelist.get(i);
            menu.players.add(target);
            String status = target.isOnline() ? "Online"
                    : target.hasPlayedBefore() ? "Offline" : "Never joined";
            menu.inventory.setItem(i - start, head(target, status, "Click to view statistics"));
        }

        if (page > 0) {
            menu.inventory.setItem(PREVIOUS, item(Material.ARROW, "Previous page"));
        }
        menu.inventory.setItem(INFO, item(Material.BOOK, "Page " + (page + 1) + " of " + pages,
                "Whitelisted: " + whitelist.size()));
        if (page < pages - 1) {
            menu.inventory.setItem(NEXT, item(Material.ARROW, "Next page"));
        }

        viewer.openInventory(menu.inventory);
    }

    private void openStats(Player viewer, OfflinePlayer target, int returnPage) {
        // Offline players who have never joined have no statistics.
        boolean joined = target.isOnline() || target.hasPlayedBefore();

        Menu menu = new Menu(returnPage, false);
        menu.inventory = Bukkit.createInventory(menu, 27, Component.text(nameOf(target) + "'s Stats"));
        menu.inventory.setItem(4, head(target, target.isOnline() ? "Online" : "Offline"));

        if (joined) {
            long seconds = stat(target, Statistic.PLAY_ONE_MINUTE) / 20L; // stored in ticks
            menu.inventory.setItem(10, item(Material.CLOCK, "Time",
                    "Played: " + (seconds / 3600) + "h " + (seconds % 3600 / 60) + "m",
                    "Times left the game: " + stat(target, Statistic.LEAVE_GAME)));

            menu.inventory.setItem(11, item(Material.IRON_SWORD, "Combat",
                    "Player kills: " + stat(target, Statistic.PLAYER_KILLS),
                    "Mob kills: " + stat(target, Statistic.MOB_KILLS),
                    "Deaths: " + stat(target, Statistic.DEATHS)));

            menu.inventory.setItem(12, item(Material.SHIELD, "Damage (health points)",
                    "Dealt: " + stat(target, Statistic.DAMAGE_DEALT) / 10,
                    "Taken: " + stat(target, Statistic.DAMAGE_TAKEN) / 10));

            menu.inventory.setItem(13, item(Material.LEATHER_BOOTS, "Travel",
                    "Walked: " + distance(target, Statistic.WALK_ONE_CM),
                    "Sprinted: " + distance(target, Statistic.SPRINT_ONE_CM),
                    "Swum: " + distance(target, Statistic.SWIM_ONE_CM),
                    "By boat: " + distance(target, Statistic.BOAT_ONE_CM),
                    "By elytra: " + distance(target, Statistic.AVIATE_ONE_CM),
                    "Jumps: " + stat(target, Statistic.JUMP)));

            menu.inventory.setItem(14, item(Material.DIAMOND_PICKAXE, "Mining",
                    "Diamond ore: " + (mined(target, Material.DIAMOND_ORE)
                            + mined(target, Material.DEEPSLATE_DIAMOND_ORE)),
                    "Emerald ore: " + (mined(target, Material.EMERALD_ORE)
                            + mined(target, Material.DEEPSLATE_EMERALD_ORE)),
                    "Ancient debris: " + mined(target, Material.ANCIENT_DEBRIS)));

            menu.inventory.setItem(15, item(Material.FISHING_ROD, "Activities",
                    "Fish caught: " + stat(target, Statistic.FISH_CAUGHT),
                    "Animals bred: " + stat(target, Statistic.ANIMALS_BRED),
                    "Villager trades: " + stat(target, Statistic.TRADED_WITH_VILLAGER)));
        } else {
            menu.inventory.setItem(13, item(Material.BARRIER, "No statistics",
                    "This player has never joined."));
        }

        menu.inventory.setItem(BACK, item(Material.ARROW, "Back"));
        viewer.openInventory(menu.inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }

        int slot = event.getRawSlot();
        Runnable action = null;
        if (menu.list) {
            if (slot >= 0 && slot < menu.players.size()) {
                OfflinePlayer target = menu.players.get(slot);
                action = () -> openStats(viewer, target, menu.page);
            } else if (slot == PREVIOUS && menu.page > 0) {
                action = () -> openList(viewer, menu.page - 1);
            } else if (slot == NEXT && event.getCurrentItem() != null) {
                action = () -> openList(viewer, menu.page + 1);
            }
        } else if (slot == BACK) {
            action = () -> openList(viewer, menu.page);
        }

        if (action != null) {
            // Switch menus after the click has finished processing.
            Runnable next = action;
            viewer.getScheduler().run(this, task -> {
                if (viewer.hasPermission("whiteliststats.use")) {
                    next.run();
                }
            }, null);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }

    private String nameOf(OfflinePlayer player) {
        String name = player.getName();
        return name != null ? name : player.getUniqueId().toString();
    }

    private int stat(OfflinePlayer player, Statistic statistic) {
        return player.getStatistic(statistic);
    }

    private int mined(OfflinePlayer player, Material block) {
        return player.getStatistic(Statistic.MINE_BLOCK, block);
    }

    private String distance(OfflinePlayer player, Statistic statistic) {
        return String.format(Locale.ROOT, "%.2f km", stat(player, statistic) / 100_000.0);
    }

    private ItemStack head(OfflinePlayer owner, String... lore) {
        ItemStack stack = item(Material.PLAYER_HEAD, nameOf(owner), lore);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        meta.setOwningPlayer(owner);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(text(name, NamedTextColor.YELLOW));
        meta.lore(Arrays.stream(lore).map(line -> text(line, NamedTextColor.GRAY)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private Component text(String value, TextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }
}
