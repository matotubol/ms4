package com.eu.habbo.networking.gameserver.handlers;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.outgoing.handshake.PingMessageComposer;
import com.eu.habbo.networking.gameserver.GameServerAttributes;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class IdleTimeoutHandler extends ChannelDuplexHandler {
    private static final long MIN_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(1);
    private static final Logger LOGGER = Logger.getLogger(IdleTimeoutHandler.class.getName());

    private final long pingScheduleNanos;
    private final long pongTimeoutNanos;

    volatile ScheduledFuture<?> pingScheduleFuture;
    volatile long lastPongTime;

    private volatile int state;

    public IdleTimeoutHandler(int pingScheduleSeconds, int pongTimeoutSeconds) {
        this.pingScheduleNanos = Math.max(MIN_TIMEOUT_NANOS, TimeUnit.SECONDS.toNanos(pingScheduleSeconds));
        this.pongTimeoutNanos = Math.max(MIN_TIMEOUT_NANOS, TimeUnit.SECONDS.toNanos(pongTimeoutSeconds));
    }

    private void initialize(ChannelHandlerContext ctx) {
        if (state == 1 || state == 2) return;

        state = 1;
        lastPongTime = System.nanoTime();
        schedulePing(ctx);
    }

    private void schedulePing(ChannelHandlerContext ctx) {
        if (pingScheduleNanos <= 0) return;

        try {
            pingScheduleFuture = ctx.executor().schedule(() -> {
                if (!ctx.channel().isOpen()) return;

                long currentTime = System.nanoTime();
                if (currentTime - lastPongTime > pongTimeoutNanos) {
                    ctx.close();
                } else {
                    GameClient client = ctx.channel().attr(GameServerAttributes.CLIENT).get();
                    if (client != null) {
                        client.sendResponse(new PingMessageComposer());
                    }
                }
                schedulePing(ctx);
            }, pingScheduleNanos, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            LOGGER.severe("Error scheduling ping task: " + e.getMessage());
        }
    }

    private void destroy() {
        state = 2;
        if (pingScheduleFuture != null) {
            pingScheduleFuture.cancel(false);
            pingScheduleFuture = null;
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        if (ctx.channel().isActive() && ctx.channel().isRegistered()) {
            initialize(ctx);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        destroy();
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isActive()) {
            initialize(ctx);
        }
        super.channelRegistered(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        initialize(ctx);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        destroy();
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ClientMessage) {
            ClientMessage packet = (ClientMessage) msg;
            if (packet.getMessageId() == Incoming.pongEvent) {
                lastPongTime = System.nanoTime();
            }
        }
        super.channelRead(ctx, msg);
    }
}
