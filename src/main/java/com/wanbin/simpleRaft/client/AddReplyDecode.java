package com.wanbin.simpleRaft.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;

import java.util.List;

public class AddReplyDecode extends ReplayingDecoder<Integer> {
    int res;
    public int getResponse() {
        return res;
    }
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        res = in.readInt();
        ctx.close();
    }
}
