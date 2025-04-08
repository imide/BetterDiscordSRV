package com.earthpol.betterDiscordSRV.tasks;

import com.earthpol.betterDiscordSRV.util.SQLManager;
import com.earthpol.betterDiscordSRV.util.SQLManager.Notification;
import com.earthpol.betterDiscordSRV.discord.DiscordBot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

public class NotificationTask implements Runnable {

    private final JavaPlugin plugin;
    private final DiscordBot discordBot;

    public NotificationTask(JavaPlugin plugin, DiscordBot discordBot) {
        this.plugin = plugin;
        this.discordBot = discordBot;
    }

    @Override
    public void run() {
        SQLManager.getPendingNotifications(notifications -> {
            for (Notification notif : notifications) {
                // Send in-game message if the Minecraft player is online.
                try {
                    Player player = Bukkit.getPlayer(UUID.fromString(notif.getUuid()));
                    if (player != null && player.isOnline()) {
                        player.sendMessage(ChatColor.GREEN + "Your Discord account " +
                                ChatColor.GOLD + notif.getDiscordUsername() +
                                ChatColor.GREEN + " is now linked to your Minecraft account " +
                                ChatColor.GOLD + notif.getMcUsername() + ChatColor.GREEN + "!");
                    }
                } catch (IllegalArgumentException e) {
                    // Invalid UUID format, ignore.
                }
                // Send DM on Discord using the Discord Bot.
                discordBot.sendDM(notif.getDiscord(),
                        "Your Discord account (" + notif.getDiscordUsername() + ") is now linked to your Minecraft account (" + notif.getMcUsername() + ").");
                // Delete the notification entry from the database.
                SQLManager.deleteNotification(notif.getId());
            }
        });
    }
}
