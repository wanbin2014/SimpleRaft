package com.wanbin.jraft;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.util.CharsetUtil;

import java.util.List;

enum  ReplyAppendEntriesMsg {
    TERM,
    SUCCESS,
}
public class ReplyAppendEntriesDecode extends ReplayingDecoder<ReplyAppendEntriesMsg> {
    long term;
    boolean success;
    String peer;
    long replicatedLogIndex;

    public ReplyAppendEntriesDecode(String peer, long replicatedLogIndex) {
        super(ReplyAppendEntriesMsg.TERM);
        this.peer = peer;
        this.replicatedLogIndex = replicatedLogIndex;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        switch (state()) {
            case TERM:
                term = in.readLong();
                checkpoint(ReplyAppendEntriesMsg.SUCCESS);

            case SUCCESS:
                success = in.readBoolean();
                checkpoint(ReplyAppendEntriesMsg.TERM);
                break;
            default:
                throw new Error("Shouldn't reach here!");

        }
        if (success == false) {
            if (term > State.currentTerm) {
                State.currentTerm = term;
                State.leaderId = null;
            } else {
                synchronized (State.class) {
                    //The follower's log is inconsistent with leader's, then decrements nextIndex and retries.
                    int nextIdx = State.nextIndex.get(peer).intValue();
                    nextIdx -= 1;
                    State.nextIndex.put(peer, (long) nextIdx);
                    //RPC type. 1 for RequestVote 2 for AppendEntries
                    ctx.channel().write(Unpooled.copyLong(2));
                    ctx.channel().write(Unpooled.copyLong(State.currentTerm));
                    ctx.channel().write(Unpooled.copiedBuffer(State.leaderId, CharsetUtil.UTF_8));
                    ctx.channel().write(Unpooled.copyLong(nextIdx));
                    ctx.channel().write(Unpooled.copyLong(State.log.get(nextIdx).getTerm()));

                    int num = State.log.size() - 1 - nextIdx;
                    if (num == 0) {
                        ctx.channel().write(Unpooled.copyLong(0));
                    } else {
                        ctx.channel().write(Unpooled.copyLong(num));
                        for (int i = 0; i < num; i++) {
                            ctx.channel().write(Unpooled.copyLong(i));
                            String content = String.valueOf(State.log.get(i + nextIdx).term) + ","
                                    + State.log.get(i + nextIdx).command;
                            ctx.channel().write(Unpooled.copiedBuffer(content, CharsetUtil.UTF_8));
                        }
                    }
                    ctx.channel().write(Unpooled.copyLong(State.commitIndex));
                    ctx.channel().flush();
                    ctx.channel().pipeline().addLast(new ReplyAppendEntriesDecode(peer, State.log.size() - 1));
                    ctx.channel().pipeline().remove(this);
                }
            }
        } else {
            if (this.replicatedLogIndex != 0) {
                State.appendEntriesResult.compute(this.replicatedLogIndex, (k, v) -> {
                    if (v == null) {
                        return 1;
                    } else if (v + 1 > (float) (State.members.length / 2.0)) {
                        if (this.replicatedLogIndex > State.matchIndex.get(peer).longValue()) {
                            State.matchIndex.put(peer, this.replicatedLogIndex);
                            notify();
                        }
                        return null;
                    } else {
                        return v + 1;
                    }
                });
            }
        }


    }
}
