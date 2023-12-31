package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceConfigurationComposer;

public class GetMarketplaceConfigurationEvent extends MessageHandler {
    @Override
    public void handle() {
        this.client.sendResponse(new MarketplaceConfigurationComposer());
    }
}
