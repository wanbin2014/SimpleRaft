package com.wanbin.simpleRaft.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class AddReplyDecode extends ReplayingDecoder<Integer> {
    final static Logger logger = LoggerFactory.getLogger(AddReplyDecode.class);
    int res;
    public int getResponse() {
        return res;
    }
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        res = in.readInt();
        logger.info("Received reply from leader successfully");
        ctx.close();
    }
}
