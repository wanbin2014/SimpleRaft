package com.wanbin.simpleRaft;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.util.CharsetUtil;

import java.util.List;

enum AddMsg {
    CMDLEN,
    CMD,
}


public class ClientAddRequestDecode extends ReplayingDecoder<AddMsg> {
    int len = 0;
    String command = null;
    public ClientAddRequestDecode() {
        super(AddMsg.CMDLEN);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        switch (state()) {
            case CMDLEN:
                len = in.readInt();
                checkpoint(AddMsg.CMD);
            case CMD:
                command = (String)in.readCharSequence(len,CharsetUtil.UTF_8);
                checkpoint(AddMsg.CMDLEN);
                break;
        }

        synchronized (State.class) {
            Entry entry = new Entry(State.currentTerm,command);
            State.log.add(entry);
            int n = State.log.size()-1;

            State.callAllAppendEntries(true);
            State.class.wait(3000);
            int count = 0;
            /*
            If there exists an N such that N > commitIndex, a majority
            of matchIndex[i] ≥ N, and log[N].term == currentTerm: set commitIndex = N
             */
            if (n > State.commitIndex) {
                for (int i = 0; i < State.members.length; i++) {
                    if (State.matchIndex.get(State.members[i]) >= n) {
                        count++;
                        if (count > State.members.length / 2.0) {
                            if (State.log.get(n).getTerm() == State.currentTerm) {
                                State.commitIndex = n;
                                for(long j = State.lastApplied; j <= n; j++) {
                                    State.log.get((int)j).commit();
                                }
                                State.lastApplied = n;
                                ctx.channel().write(Unpooled.copyInt(0));
                                return;
                            }
                        }
                    }
                }
            }
            ctx.channel().write(Unpooled.copyInt(1));
            return;

        }
    }
}
