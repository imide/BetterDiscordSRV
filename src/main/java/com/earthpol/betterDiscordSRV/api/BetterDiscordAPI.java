package com.earthpol.betterDiscordSRV.api;

import com.earthpol.betterDiscordSRV.util.SQLManager;
import java.util.Optional;
import java.util.function.Consumer;

public class BetterDiscordAPI {

    /**
     * Given a Minecraft UUID, check if it is linked.
     * The callback returns an Optional containing the linked Discord ID if present.
     */
    public static void isMinecraftLinked(String minecraftUUID, Consumer<Optional<String>> callback) {
        SQLManager.getLinkedDiscordByMinecraft(minecraftUUID, callback);
    }

    /**
     * Given a Discord ID, check if it is linked.
     * The callback returns an Optional containing the linked Minecraft UUID if present.
     */
    public static void isDiscordLinked(String discordId, Consumer<Optional<String>> callback) {
        SQLManager.getLinkedMinecraftByDiscord(discordId, callback);
    }
}
