package com.earthpol.betterDiscordSRV.JDA;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.ArrayList;
import java.util.List;

public class Embed {
    private List<EmbedBuilder> embedList = new ArrayList<>();

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
        if (channel == null || embedList == null || embedList.isEmpty()) {
            return false;
        }

        List<MessageEmbed> embeds = new ArrayList<>(embedList.size());
        for (EmbedBuilder builder : embedList) {
            embeds.add(builder.build());
        }

        channel.sendMessageEmbeds(embeds).queue();
        return true;
    }
}
