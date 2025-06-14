package com.earthpol.betterDiscordSRV.JDA;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import java.util.List;

public class Message {
    private String message;
    private List<MessageEmbed> embedList;
    private MessageCreateBuilder finalMessage;
    private MessageChannel channel;

    // () - No arguments
    public Message() {}
    // (message) - Only message argument
    public Message(String message) {
        this.message = message;
    }

    // Set ALL embeds (removes embeds and sets it as the only one
    private void setEmbed(MessageEmbed embed) {
        this.embedList.clear();
        this.embedList.add(embed);
    }
    // Add to the list of embeds
    private void addEmbed(MessageEmbed embed) {
        this.embedList.add(embed);
    }
    // Sets the message to be sent
    private void setMessage(String message) {
        this.message = message;
    }
    // Sets the channel the message is sent in
    private void setChannel(MessageChannel channel) {
        this.channel = channel;
    }
    // Build the message builder before sending (required)
    private boolean build() {
        try {
            this.finalMessage = new MessageCreateBuilder();
            this.finalMessage.addContent(this.message);
            this.finalMessage.setEmbeds(this.embedList);
            return true;
        } catch (NullPointerException e) {
            return false;
        }
    }
    // Send the message
    private void sendMessage() {
        channel.sendMessage(this.finalMessage.build()).queue();
    }
}
