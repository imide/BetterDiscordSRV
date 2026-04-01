package com.earthpol.betterDiscordSRV.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.time.Duration;
import java.util.EnumSet;

public class DiscordBot {

    private JDA jda;

    public DiscordBot(String token) {
        try {
            // Build JDA and wait until it's ready
            jda = JDABuilder.createDefault(token)
                    .enableIntents(EnumSet.of(GatewayIntent.MESSAGE_CONTENT))
                    .build()
                    .awaitReady();
            // BetterDiscordSRV now only handles DM-based account linking.
            jda.addEventListener(new DiscordLinkListener());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Call this method on plugin disable to shutdown JDA gracefully.
    public void shutdown() {
        if (jda != null) {
            jda.shutdownNow();
            try {
                jda.awaitShutdown(Duration.ofSeconds(10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                jda = null;
            }
        }
    }

    public void sendDM(String discordId, String message) {
        jda.retrieveUserById(discordId).queue(user -> {
            user.openPrivateChannel().queue(channel -> {
                channel.sendMessage(message).queue();
            });
        });
    }

}
