package com.minebridge.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Reports gateway lifecycle events for the active JDA client. */
public class DiscordGatewayListener extends ListenerAdapter {

    private final DiscordService service;

    public DiscordGatewayListener(DiscordService service) {
        this.service = service;
    }

    @Override
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        service.onGatewayDisconnect(event.getJDA());
    }

    @Override
    public void onSessionResume(SessionResumeEvent event) {
        service.onGatewayResume(event.getJDA());
    }

    @Override
    public void onReady(ReadyEvent event) {
        service.onGatewayReady(event.getJDA());
    }
}
