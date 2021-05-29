package org.simpleRaft.simpleRaft.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class AddReplyDecode extends ReplayingDecoder<Integer> {
    final static Logger logger = LoggerFactory.getLogger(AddReplyDecode.class);
    Integer res = null;
    public synchronized int getResponse() {
        while (res == null) {
            try {
                wait();
            } catch (InterruptedException e) {
                return -1;
            }
        }
        return res;

    }
    @Override
    protected synchronized void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        res = in.readInt();
        logger.info("Received reply from leader successfully");
        notify();
        ctx.close();
    }
}
