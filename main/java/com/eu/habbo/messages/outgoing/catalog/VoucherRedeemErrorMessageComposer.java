package com.eu.habbo.messages.outgoing.catalog;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class VoucherRedeemErrorMessageComposer extends MessageComposer {
    public static final int INVALID_CODE = 0;
    public static final int TECHNICAL_ERROR = 1;
    private final int code;

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.voucherRedeemErrorMessageComposer);
        this.response.appendString(this.code + "");
        return this.response;
    }
}
