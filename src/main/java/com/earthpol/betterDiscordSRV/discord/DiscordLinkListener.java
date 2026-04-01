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

public class DiscordLinkListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isFromGuild()) {
            return;
        }

        String message = event.getMessage().getContentRaw().trim();
        if (!message.matches("\\d{6}")) {
            return;
        }

        ResultSet rs = SQLManager.getDiscordCode(message);

        try {
            if (rs == null || !rs.next()) {
                return;
            }

            String uuid = rs.getString("uuid");
            String discordId = event.getAuthor().getId();

            SQLManager.insertDiscordAccount(discordId, uuid);
            SQLManager.deleteDiscordCode(message);

            String username = SQLManager.getCachedUsername(message);
            if (username == null) {
                username = uuid;
            }

            SQLManager.removeCachedCode(message);

            event.getChannel().sendMessage(
                    "Successfully linked to Minecraft account: **" + username + "** (`" + uuid + "`)"
            ).queue();

            Player linkedPlayer = Bukkit.getPlayer(UUID.fromString(uuid));
            if (linkedPlayer != null && linkedPlayer.isOnline()) {
                linkedPlayer.sendMessage(ChatColor.GREEN + "Your Discord account "
                        + ChatColor.GOLD + event.getAuthor()
                        + ChatColor.GREEN + " is now linked to your Minecraft username "
                        + ChatColor.GOLD + username + ChatColor.GREEN + "!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
