package com.earthpol.betterDiscordSRV.discord;

import com.earthpol.betterDiscordSRV.util.SQLManager;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class DiscordMessageListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore messages from bots
        if (event.getAuthor().isBot()) {
            return;
        }

        // Check if the message is a 6-digit code
        String message = event.getMessage().getContentRaw().trim();
        if (message.matches("\\d{6}")) {
            String code = message;
            ResultSet rs = SQLManager.getDiscordCode(code);

            try {
                if (rs != null && rs.next()) {
                    String uuid = rs.getString("uuid");
                    String discordId = event.getAuthor().getId();

                    // Insert the final linking into discord_accounts
                    SQLManager.insertDiscordAccount(discordId, uuid);

                    // Delete the code from the database so it can't be reused
                    SQLManager.deleteDiscordCode(code);

                    // Get the Minecraft username from cache
                    String username = SQLManager.getCachedUsername(code);
                    if (username == null) {
                        // Fallback if for some reason it's missing from the cache
                        username = uuid;
                    }

                    // Remove the code from the local cache
                    SQLManager.removeCachedCode(code);

                    // Send confirmation message in Discord (now includes username + UUID)
                    event.getChannel().sendMessage(
                            "Successfully linked to Minecraft account: **" + username + "** (`" + uuid + "`)"
                    ).queue();

                    // OPTIONAL: also tell the user in-game if they're online
                    Player linkedPlayer = Bukkit.getPlayer(UUID.fromString(uuid));
                    if (linkedPlayer != null && linkedPlayer.isOnline()) {
                        linkedPlayer.sendMessage(ChatColor.GREEN + "Your Discord account "
                                + ChatColor.GOLD + event.getAuthor().getAsTag()
                                + ChatColor.GREEN + " is now linked to your Minecraft username "
                                + ChatColor.GOLD + username + ChatColor.GREEN + "!");
                    }
                } else {
                    event.getChannel().sendMessage("Invalid or expired code.").queue();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
