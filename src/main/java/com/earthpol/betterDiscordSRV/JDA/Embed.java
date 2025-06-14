package com.earthpol.betterDiscordSRV.JDA;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Embed {
    private List<EmbedBuilder> embedList = new ArrayList<>();
    private MessageCreateBuilder message;

    public Embed () {

    }
    public Embed(EmbedBuilder builder) {
        this.embedList.add(builder);
    }
    private void setEmbeds(List<EmbedBuilder> embeds) {
        this.embedList = embeds;
    }
    private void setEmbed(EmbedBuilder builder) {
        this.embedList.clear();
        this.embedList.add(builder);
    }
    private void addEmbed(EmbedBuilder builder) {
        this.embedList.add(builder);
    }
    private boolean sendEmbed(TextChannel channel) {
        try {
            channel.sendMessageEmbeds(embedList.getFirst().build()).queue();
            Collections embeds = (Collections) embedList;
            channel.sendMessageEmbeds((Collection<? extends MessageEmbed>) embeds);
            return true;
        } catch (NullPointerException e) {
            return false;
        }
    }
}
