package org.simpleRaft.simpleRaft.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

enum LsReplyMsg {
    NUMBER,
    LEN,
    DETAILS,

}

public class LsReplyDecode extends ReplayingDecoder<LsReplyMsg> {
    final static Logger logger = LoggerFactory.getLogger(LsReplyDecode.class);
    Long number = null;
    int len = 0;
    long count = 0;
    List<String> details = null;
    public LsReplyDecode() {
        super(LsReplyMsg.NUMBER);
    }
    public synchronized List<String> getResponse() {
        while (details == null || details.size() != number) {
            try {
                wait();
            } catch (InterruptedException e) {
                return null;
            }
        }
        return details;
    }

    @Override
    protected synchronized void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        boolean loop = true;
        while (loop) {
            switch (state()) {
                case NUMBER:
                    number = in.readLong();
                    if (number <= 0) {
                        loop = false;
                        details = new ArrayList<>();
                        logger.info("Cannot get commitIndex, commitIndex will compute after adding a log");
                        break;
                    }
                    checkpoint(LsReplyMsg.LEN);
                case LEN:
                    len = in.readInt();
                    if (len == 0) {
                        checkpoint(LsReplyMsg.LEN);
                        break;
                    }
                    checkpoint(LsReplyMsg.DETAILS);
                case DETAILS:
                    if (details == null) {
                        details = new ArrayList<>();
                    }
                    details.add((String) in.readCharSequence(len,CharsetUtil.UTF_8));
                    if (++count < number) {
                        checkpoint(LsReplyMsg.LEN);
                        continue;
                    } else {
                        loop = false;
                        if (details == null) {
                            details = new ArrayList<>();
                        }
                        break;
                    }
                default:
                    throw new Error("Shouldn't reach here!");

            }
        }
        notify();
        ctx.close();

    }
}
