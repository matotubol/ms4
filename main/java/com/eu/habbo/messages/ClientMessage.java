package com.eu.habbo.messages;

import com.eu.habbo.util.PacketUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

@Slf4j
@Getter
public final class ClientMessage {
    private final int messageId;
    private final ByteBuf buffer;

    public ClientMessage(int messageId, ByteBuf buffer) {
        this.messageId = messageId;
        this.buffer = ((buffer == null) || (buffer.readableBytes() == 0) ? Unpooled.EMPTY_BUFFER : buffer);
    }

    @Override
    public ClientMessage clone() {
        return new ClientMessage(this.messageId, this.buffer.copy());
    }

    public int readShort() {
        try {
            return this.buffer.readShort();
        } catch (Exception e) {
            log.error("Error reading short from buffer", e);
            return 0;
        }
    }

    public int readInt() {
        try {
            return this.buffer.readInt();
        } catch (Exception e) {
            log.error("Error reading int from buffer", e);
            return 0;
        }
    }

    public boolean readBoolean() {
        try {
            return this.buffer.readByte() == 1;
        } catch (Exception e) {
            log.error("Error reading boolean from buffer", e);
            return false;
        }
    }

    public String readString() {
        try {
            int length = this.readShort();
            byte[] data = new byte[length];
            this.buffer.readBytes(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Error reading string from buffer", e);
            return "";
        }
    }

    public String getMessageBody() {
        return PacketUtils.formatPacket(this.buffer);
    }

    public int bytesAvailable() {
        return this.buffer.readableBytes();
    }

    public boolean release() {
        if (buffer.refCnt() <= 0) {
            throw new IllegalStateException("Buffer has already been released.");
        }
        return this.buffer.release();
    }
}
