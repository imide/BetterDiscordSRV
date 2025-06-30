package com.earthpol.betterDiscordSRV.discord;

import com.earthpol.betterDiscordSRV.BetterDiscordSRV;
import com.earthpol.betterDiscordSRV.util.SQLManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.*;
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

        if(event.isFromGuild()) {
            handleGuildMessage(event);
        } else {
            handlePrivateMessage(event);
        }
    }

    private void handlePrivateMessage(MessageReceivedEvent event) {
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
                                + ChatColor.GOLD + event.getAuthor()
                                + ChatColor.GREEN + " is now linked to your Minecraft username "
                                + ChatColor.GOLD + username + ChatColor.GREEN + "!");
                    }
                } else {
                    //event.getChannel().sendMessage("Invalid or expired code.").queue();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
    private void handleGuildMessage(MessageReceivedEvent event) {
        String message = event.getMessage().getContentRaw().trim();
        User user = event.getAuthor();

        JavaPlugin plugin = BetterDiscordSRV.getInstance();

        if(event.getChannel().getId().equals(plugin.getConfig().getString("minechat.channel"))) {
            // Build the chat message using Adventure components
            Component messageComponent = buildChatMessage(user, message, event);

            // Send the component-based message to all recipients
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(messageComponent);
            }
        }
    }

    private Component buildChatMessage(User user, String message, MessageReceivedEvent event) {
        // Build the final message depending on the channel
        TextComponent.Builder messageComponentBuilder = Component.text();

        JavaPlugin plugin = BetterDiscordSRV.getInstance();

        // Build the Verified role component portion
        if(plugin.getConfig().getBoolean("minechat.verified_icon")) {
            Member member = event.getMember();
            if(member.getRoles().stream()
                .anyMatch(role -> role.getId().equalsIgnoreCase(
                    plugin.getConfig().getString("minechat.verified_role")
                ))
            ) {
                Component verifiedUserHover = Component.text("This user has a\nVerified Mojang Account", NamedTextColor.AQUA);

                messageComponentBuilder.append(
                    Component.text("\uD83D\uDDF9 ", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .hoverEvent(HoverEvent.showText(verifiedUserHover))
                );
            }
        }

        // Build the [Discord] component portion
        messageComponentBuilder.append(
                Component.text("[", NamedTextColor.GRAY),
                Component.text("Discord", NamedTextColor.BLUE),
                Component.text("] ", NamedTextColor.GRAY)
        );
        messageComponentBuilder // Add badges after the tag
                .append(Component.text(user.getEffectiveName()).color(getRoleColor(event.getMember()))) // Add player name
                .append(Component.text(" • ", NamedTextColor.GRAY))
                .append(Component.text(message, NamedTextColor.WHITE)); // Local chat in white

        return messageComponentBuilder.build();
    }

    private TextColor getRoleColor(Member member) {
        JavaPlugin plugin = BetterDiscordSRV.getInstance();

        if (member != null) {
            return convertToMcColor(member.getRoles().get(0).getColor());
        }
        return TextColor.fromHexString("#ffffff");
    }

    public TextColor convertToMcColor(Color color) {
        if (color == null) return TextColor.fromHexString("#000000"); // Default to white

        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();

        String hexColor = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());

        return TextColor.fromHexString(hexColor); // Default to white if no match
    }
}
