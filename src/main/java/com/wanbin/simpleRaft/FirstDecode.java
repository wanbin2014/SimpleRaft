package com.wanbin.simpleRaft;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;

import java.util.List;


public class FirstDecode extends ReplayingDecoder<Void> {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        Integer type = in.readInt();
        if (type.equals(1)) {
            ctx.pipeline().addLast(new RequestVoteDecode());
            ctx.pipeline().remove(this);
        } else if (type.equals(2)) {
            ctx.pipeline().addLast(new RequestAppendEntriesDecode());
            ctx.pipeline().remove(this);
        } else if (type.equals(3)) {
            ctx.pipeline().addLast(new ClientAddRequestDecode());
            ctx.pipeline().remove(this);
        } else if (type.equals(4)) {
            ctx.pipeline().addLast(new ClientLsRequestDecode());
            ctx.pipeline().remove(this);
        }

    }
}
