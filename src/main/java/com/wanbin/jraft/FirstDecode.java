package com.wanbin.jraft;

import com.wanbin.jraft.rpc.RequestVote;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.ReplayingDecoder;

import java.util.List;


public class FirstDecode extends ReplayingDecoder<Void> {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        Object type = in.readInt();
        if (type.equals(1)) {
            ctx.pipeline().addLast(new RequestVoteDecode());
        } else if (type.equals(2)) {
            ctx.pipeline().addLast(new RequestAppendEntriesDecode());
        } else if (type.equals(3)) {
            ctx.pipeline().addLast(new ClientRequestDecode());
        }

        // Remove the first decoder (me)
        ctx.pipeline().remove(this);
    }
}
