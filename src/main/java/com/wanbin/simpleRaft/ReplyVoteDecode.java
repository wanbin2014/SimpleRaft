package com.wanbin.simpleRaft;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

enum ReplyVoteMsg {
    TERM,
    VOTEGRANTED
}
public class ReplyVoteDecode extends ReplayingDecoder<ReplyVoteMsg> {
    final static Logger logger = LoggerFactory.getLogger(ReplyVoteDecode.class);
    long term;
    boolean voteGranted;

    ReplyVoteDecode() {
        super(ReplyVoteMsg.TERM);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        switch (state()) {
            case TERM:
                term = in.readLong();
                checkpoint(ReplyVoteMsg.VOTEGRANTED);
            case VOTEGRANTED:
                voteGranted = in.readBoolean();
                checkpoint(ReplyVoteMsg.TERM);
                break;
            default:
                throw new Error("Shouldn't reach here!");
        }
        logger.info("Received a reply from a request of vote, term={}, voteGranted={}", term, voteGranted);
        if (voteGranted == true) {
            synchronized (State.class) {
                State.voteCount += 1;
                if (State.voteCount > (float) (State.members.length / 2.0)) {
                    logger.info("The number of received votes upper to majority, notify related thread!");
                    State.class.notifyAll();
                }
            }
        }
        ctx.close();






    }
}
