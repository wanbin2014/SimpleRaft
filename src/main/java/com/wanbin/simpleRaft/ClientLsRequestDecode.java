package com.wanbin.simpleRaft;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.util.CharsetUtil;

import java.util.List;

public class ClientLsRequestDecode extends ReplayingDecoder<Void> {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int j = 0;

        for(long i = State.commitIndex ; i >= 0; i-- ) {
            if (j < 100) {
                ctx.channel().write(Unpooled.copiedBuffer(State.log.get((int)i).toString(), CharsetUtil.UTF_8));
                j++;
            }
        }
        ctx.channel().flush();
    }
}
