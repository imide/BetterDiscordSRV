package com.earthpol.betterDiscordSRV.JDA;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class Channel {
    private MessageChannel channel;

    public Channel(MessageChannel channel) {
        this.channel = channel;
    }
    public Channel() {

    }
    public void setChannel(MessageChannel channel) {
        this.channel = channel;
    }
    public MessageChannel getChannel() {
        return this.channel;
    }
}
