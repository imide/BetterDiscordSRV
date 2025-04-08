package com.earthpol.betterDiscordSRV;

import com.earthpol.betterDiscordSRV.commands.DiscordCommand;
import com.earthpol.betterDiscordSRV.discord.DiscordBot;
import com.earthpol.betterDiscordSRV.tasks.NotificationTask;
import com.earthpol.betterDiscordSRV.util.SQLManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class BetterDiscordSRV extends JavaPlugin {

    private DiscordBot discordBot;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        SQLManager.initialize(this);

        // Register the /discord command (ensure it's defined in plugin.yml with subcommands "link" and "linked")
        this.getCommand("discord").setExecutor(new DiscordCommand(this));

        String token = getConfig().getString("discord.token");
        if (token == null || token.isEmpty() || token.equals("YOUR_DISCORD_BOT_TOKEN")) {
            getLogger().warning("Discord token is not set properly in config.yml!");
        } else {
            discordBot = new DiscordBot(token);
            getLogger().info("Discord bot initialized successfully.");
        }

        getServer().getScheduler().runTaskTimer(this, new NotificationTask(this, discordBot), 20L * 5, 20L * 60);

        getLogger().info("BetterDiscordSRV enabled!");
    }

    @Override
    public void onDisable() {
        try {
            if (SQLManager.getConnection() != null && !SQLManager.getConnection().isClosed()) {
                SQLManager.getConnection().close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (discordBot != null) {
            discordBot.shutdown();
        }
        getLogger().info("BetterDiscordSRV disabled!");
    }
}
