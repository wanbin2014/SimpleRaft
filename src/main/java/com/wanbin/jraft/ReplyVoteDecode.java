package com.wanbin.jraft;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;

import java.util.List;

enum ReplyVoteMsg {
    TERM,
    VOTEGRANTED
}
public class ReplyVoteDecode extends ReplayingDecoder<ReplyVoteMsg> {
    long term;
    boolean votegranted;

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
                votegranted = in.readBoolean();
                checkpoint(ReplyVoteMsg.TERM);
                break;
            default:
                throw new Error("Shouldn't reach here!");
        }
        if (votegranted == true) {
            synchronized (State.class) {
                State.voteCount += 1;
                if (State.voteCount > (float) (State.members.length / 2.0)) {
                    State.class.notify();
                }
            }
        }





    }
}
