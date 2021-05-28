package com.wanbin.simpleRaft;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

enum AddMsg {
    CMDLEN,
    CMD,
}


public class ClientAddRequestDecode extends ReplayingDecoder<AddMsg> {
    Logger logger = LoggerFactory.getLogger(ClientAddRequestDecode.class);
    int len = 0;
    String command = null;
    long timeout;
    public ClientAddRequestDecode(long timeout ) {
        super(AddMsg.CMDLEN);
        this.timeout = timeout;
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

        logger.info("Received message from client. message:{}", command);
        synchronized (State.class) {
            Entry entry = new Entry(State.currentTerm,command);
            State.log.add(entry);
            int n = State.log.size()-1;

            State.callAllAppendEntries(true);
            logger.info("invoke AppendEntries rpc to all server");



            int count = 0;
            /*
            If there exists an N such that N > commitIndex, a majority
            of matchIndex[i] ≥ N, and log[N].term == currentTerm: set commitIndex = N
             */
            if (n > State.commitIndex) {
                long startTime = System.nanoTime();
                long residual = 0;
                while(true) {
                    for (int i = 0; i < State.members.length; i++) {
                        if (State.matchIndex.get(State.members[i]) >= n) {
                            count++;
                            if (count > State.members.length / 2.0) {
                                if (State.log.get(n).getTerm() == State.currentTerm) {
                                    State.commitIndex = n;
                                    for (long j = State.lastApplied; j <= n; j++) {
                                        State.log.get((int) j).commit();
                                    }
                                    State.lastApplied = n;
                                    ctx.channel().writeAndFlush(Unpooled.copyInt(0));
                                    return;
                                }
                            }
                        }
                    }

                    if (residual == 0) {
                        State.class.wait(100);
                    } else if (residual > 0){
                        State.class.wait(residual);
                    } else {
                        ctx.channel().writeAndFlush(Unpooled.copyInt(2));
                        return;
                    }
                    long endTime = System.nanoTime();
                    residual = timeout - (endTime - startTime) / 1000000;
                }
            } else {
                logger.info("Shouldn't reach hear n={},commitIndex={}", n, State.commitIndex);
                ctx.channel().writeAndFlush(Unpooled.copyInt(1));
                return;
            }

        }
    }
}
