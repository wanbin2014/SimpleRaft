package com.wanbin.simpleRaft.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.util.CharsetUtil;

import java.util.ArrayList;
import java.util.List;

enum LsReplyMsg {
    NUMBER,
    DETAILS,

}

public class LsReplyDecode extends ReplayingDecoder<LsReplyMsg> {
    int number = 0;
    List<String> details = new ArrayList<>();
    public LsReplyDecode() {
        super(LsReplyMsg.NUMBER);
    }
    public List<String> getResponse() {
        return details;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        switch (state()) {
            case NUMBER:
                number = in.readInt();
                checkpoint(LsReplyMsg.DETAILS);
            case DETAILS:
                for (int i = 0; i < number; i++) {
                    details.add(in.toString(CharsetUtil.UTF_8));
                }
                checkpoint(LsReplyMsg.NUMBER);
                break;
            default:
                throw new Error("Shouldn't reach here!");

        }
        ctx.close();

    }
}
