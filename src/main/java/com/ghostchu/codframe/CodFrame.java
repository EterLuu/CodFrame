package com.ghostchu.codframe;

import com.alessiodp.lastloginapi.api.LastLogin;
import com.alessiodp.lastloginapi.api.interfaces.LastLoginAPI;
import com.alessiodp.lastloginapi.api.interfaces.LastLoginPlayer;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CodFrame extends JavaPlugin implements Listener {
    private final NamespacedKey KEY = new NamespacedKey(this, "owner");
    private final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final ConcurrentHashMap<UUID, String> nameCache = new ConcurrentHashMap<>();

    LastLoginAPI lastLoginAPI;

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();

        if (getServer().getPluginManager().getPlugin("LastLoginAPI") != null) {
            if (getServer().getPluginManager().getPlugin("LastLoginAPI").isEnabled()) {
                lastLoginAPI = LastLogin.getApi();
            }
        }

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public Optional<UUID> queryProtection(ItemFrame frame) {
        String dat = frame.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (dat == null) {
            return Optional.empty();
        }
        UUID uuid = UUID.fromString(dat);
        return Optional.of(uuid);
    }

    public boolean claimProtection(ItemFrame frame, UUID uuid) {
        frame.setFixed(true);
        frame.setInvulnerable(true);
        if (queryProtection(frame).isPresent()) {
            return false;
        }
        frame.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, uuid.toString());
        return true;
    }

    public boolean removeProtection(ItemFrame frame, UUID uuid) {
        if (!queryProtection(frame).isPresent()) {
            return false;
        }
        frame.setFixed(false);
        frame.getPersistentDataContainer().remove(KEY);
        frame.setInvulnerable(false);
        return true;
    }

    public void openBook(ItemFrame frame, Player player) {
        if (frame.getItem().getItemMeta() instanceof BookMeta meta) {
            Component author = meta.author();
            Component title = meta.title();
            if (author == null) {
                author = MINI_MESSAGE.deserialize(getConfig().getString("messages.book.anonymous", ""));
            }
            if (title == null) {
                title = MINI_MESSAGE.deserialize(getConfig().getString("messages.book.no-title", ""));
            }
            player.openBook(Book.book(title, author, meta.pages()));
        }
    }

    public void playerDoProtection(Player player, ItemFrame frame) {
        getServer().getRegionScheduler().execute(this, frame.getLocation(), () -> {
            if (claimProtection(frame, player.getUniqueId())) {
                player.sendMessage(MINI_MESSAGE.deserialize(getConfig().getString("messages.frame.success-set", "")));
            } else {
                Optional<UUID> uuid = queryProtection(frame);
                if (uuid.get().equals(player.getUniqueId()) || player.hasPermission("codframe.admin")) {
                    removeProtection(frame, player.getUniqueId());
                    player.sendMessage(MINI_MESSAGE.deserialize(getConfig().getString("messages.frame.success-remove", "")));
                } else {
                    String message = getConfig()
                            .getString("messages.frame.failed-other-protected", "")
                            .replace("<0>", getPlayerName(uuid.get()));
                    player.sendMessage(MINI_MESSAGE.deserialize(message));
                }
            }
        });
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player) {
            player.getScheduler().run(this, task -> {
                Entity target = player.getTargetEntity(4);
                if (target instanceof ItemFrame frame) {
                    playerDoProtection(player, frame);
                } else {
                    player.sendMessage(MINI_MESSAGE.deserialize(getConfig().getString("messages.frame.failed-invalid", "")));
                }
            }, null);
            return true;
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrameDropping(HangingBreakEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            if (queryProtection(frame).isPresent()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrameDamaging(EntityDamageEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            if (queryProtection(frame).isPresent()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrameDamaging(EntityDamageByBlockEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            if (queryProtection(frame).isPresent()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrameDamaging(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) {
            if (queryProtection(frame).isPresent()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void interactFrame(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof ItemFrame frame && event.getHand() == EquipmentSlot.HAND) {
            Optional<UUID> uuid = queryProtection(frame);
            if (event.getPlayer().isSneaking()) {
                event.setCancelled(true);
                playerDoProtection(event.getPlayer(), frame);
            } else if (uuid.isPresent()) {
                event.setCancelled(true);
                event.getPlayer().sendActionBar(
                        MINI_MESSAGE.deserialize(getConfig()
                                .getString("messages.general.owner-info", "")
                                .replace("<0>", getPlayerName(uuid.get()))
                        )
                );
                if (frame.getItem().hasItemMeta()) {
                    openBook(frame, event.getPlayer());
                    sendChatPreview(frame.getItem(), event.getPlayer());
                }
            }
        }
    }

    private void sendChatPreview(ItemStack item, Player player) {
        player.sendMessage(MINI_MESSAGE.deserialize(getConfig().getString("messages.general.hover-preview", "")).hoverEvent(item.asHoverEvent()));
    }

    private @NotNull String getPlayerName(UUID uuid) {
        String cached = nameCache.get(uuid);
        if (cached != null) {
            return cached;
        }
        if (lastLoginAPI != null) {
            LastLoginPlayer lastLoginPlayer = lastLoginAPI.getPlayer(uuid);
            String name = lastLoginPlayer.getName();
            if (name != null) {
                nameCache.put(uuid, name);
                return name;
            }
        }
        asyncBackfillName(uuid);
        return uuid.toString();
    }

    private void asyncBackfillName(UUID uuid) {
        if (nameCache.containsKey(uuid)) {
            return;
        }
        getServer().getAsyncScheduler().runNow(this, task -> {
            OfflinePlayer offlineOwner = Bukkit.getOfflinePlayer(uuid);
            String name = offlineOwner.getName();
            if (name != null) {
                nameCache.put(uuid, name);
            }
        });
    }
}