package com.earthpol.betterDiscordSRV.commands;

import com.earthpol.betterDiscordSRV.util.SQLManager;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

public class DiscordCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    public DiscordCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Only allow players to use the command.
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use that command!");
            return true;
        }

        Player player = (Player) sender;

        // /discord link
        if (args.length > 0 && args[0].equalsIgnoreCase("link")) {

            final String uuid = player.getUniqueId().toString();
            // First, check if this account is already linked.
            SQLManager.isAlreadyLinked(uuid, linked -> {
                if (linked) {
                    player.sendMessage(ChatColor.RED + "Your Minecraft account is already linked!");
                    return;
                } else {
                    // Not linked, so generate a new code.
                    String code = String.format("%06d", new Random().nextInt(1_000_000));
                    // Expiration time: current time + 15 minutes (in milliseconds)
                    long expiration = System.currentTimeMillis() + (15 * 60 * 1000);

                    // Cache the code with the player's username.
                    SQLManager.cacheCode(code, player.getName());

                    // Store the code in the SQL database asynchronously.
                    SQLManager.insertDiscordCode(code, uuid, expiration);

                    // Build the clickable link text.
                    String rawLink = plugin.getConfig().getString("link.url",
                                    " https://earthpol.com/linking/link.php?uuid={uuid}&mc_code={code}&mc_username={mc_username}")
                            .replace("{uuid}", uuid)
                            .replace("{code}", code)
                            .replace("{mc_username}", player.getName());

                    // Inform the player (with colored messages).
                    player.sendMessage(ChatColor.AQUA + "Your Discord linking code is: " + ChatColor.GOLD + code);

                    TextComponent mainMessage = new TextComponent(ChatColor.AQUA + "Link your account by messaging this code to our Discord bot or by [");
                    TextComponent clickMe = new TextComponent(ChatColor.GREEN + "Clicking this Link");
                    TextComponent closingBracket = new TextComponent(ChatColor.AQUA + "]");

                    clickMe.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, rawLink));
                    clickMe.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new BaseComponent[]{ new TextComponent("Click to open your linking page!") }));

                    mainMessage.addExtra(clickMe);
                    mainMessage.addExtra(closingBracket);

                    player.spigot().sendMessage(mainMessage);
                }
            });
            return true;
        }

        // /discord linked <identifier>
        if (args.length > 0 && args[0].equalsIgnoreCase("linked")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /discord linked <playerName|discordID>");
                return true;
            }
            String identifier = args[1];
            // If the identifier is all digits, assume it's a Discord ID.
            if (identifier.matches("\\d+")) {
                // Query the database by the discord column.
                SQLManager.queryLinkedAccount("discord", identifier, result -> sender.sendMessage(ChatColor.AQUA + result));
            } else {
                // Assume it's a Minecraft player name; resolve to UUID.
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(identifier);
                if (offlinePlayer == null || offlinePlayer.getUniqueId() == null) {
                    sender.sendMessage(ChatColor.RED + "Could not find a player with name: " + identifier);
                } else {
                    String playerUUID = offlinePlayer.getUniqueId().toString();
                    SQLManager.queryLinkedAccount("uuid", playerUUID, result -> sender.sendMessage(ChatColor.AQUA + result));
                }
            }
            return true;
        }

        // If command is unrecognized.
        sender.sendMessage(ChatColor.RED + "Usage: /discord link OR /discord linked <playerName|discordID>");
        return true;
    }
}
