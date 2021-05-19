package com.wanbin.jraft;

import com.sun.tools.internal.xjc.reader.xmlschema.parser.SchemaConstraintChecker;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.util.CharsetUtil;

import java.util.ArrayList;
import java.util.List;


enum AppendEntiesMsg {
    TERM,
    LEADERID,
    PREVLOGINDEX,
    PREVTERM,
    ENTRYNUM,
    ENTRYINDEX,
    ENTRYCONTENT,
    LEADERCOMMIT,
}
public class RequestAppendEntriesDecode extends ReplayingDecoder<AppendEntiesMsg> {

    long term;
    String leaderId;
    long prevLogIndex;
    long prevTerm;
    long entryNum;
    List<Entry> entries;
    long leaderCommit;

    int entryCount;

    public RequestAppendEntriesDecode() {
        super(AppendEntiesMsg.TERM);
        entryCount = 0;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        switch (state()) {
            case TERM:
                term = in.readLong();
                checkpoint(AppendEntiesMsg.LEADERID);
            case LEADERID:
                leaderId = in.toString(CharsetUtil.UTF_8);
                checkpoint(AppendEntiesMsg.PREVLOGINDEX);
            case PREVLOGINDEX:
                prevLogIndex = in.readLong();
                checkpoint(AppendEntiesMsg.PREVTERM);
            case PREVTERM:
                prevTerm = in.readLong();
                checkpoint(AppendEntiesMsg.ENTRYNUM);
            case ENTRYNUM:
                entryNum = in.readLong();
                if (entryNum == 0) {
                    checkpoint(AppendEntiesMsg.LEADERCOMMIT);
                } else {
                    checkpoint(AppendEntiesMsg.ENTRYINDEX);
                }
            case ENTRYINDEX:
                long index = in.readLong();
                checkpoint(AppendEntiesMsg.ENTRYCONTENT);
            case ENTRYCONTENT:
                String content = in.toString(CharsetUtil.UTF_8);
                if (entries == null) {
                    entries = new ArrayList<Entry>();
                }
                String[] fields = content.split(",");
                Entry entry = new Entry(Long.valueOf(fields[0]), fields[1]);
                entries.add(entry);
                entryCount += 1;
                if (entryCount == entryNum) {
                    checkpoint(AppendEntiesMsg.LEADERCOMMIT);
                } else {
                    checkpoint(AppendEntiesMsg.ENTRYINDEX);
                }
            case LEADERCOMMIT:
                leaderCommit = in.readLong();
                entryCount = 0;
                checkpoint(AppendEntiesMsg.TERM);
                break;
            default:
                throw new Error("Shouldn't reach here!");

        }
        boolean success;
        //reply false if term < currentTerm
        if (term < State.currentTerm) {
            term = State.currentTerm;
            ctx.channel().write(Unpooled.copyLong(term));
            ctx.channel().write(Unpooled.copyBoolean(false));
            return;
        }
        //reply false if doesn't contain prevLogIndex
        if (prevLogIndex > State.log.size()-1) {
            ctx.channel().write(Unpooled.copyLong(term));
            ctx.channel().write(Unpooled.copyBoolean(false));
            return;
        }
        //reply false if doesn't match prevTerm at prevLogIndex
        if (State.log.get((int)prevLogIndex).getTerm() != term) {
            ctx.channel().write(Unpooled.copyLong(term));
            ctx.channel().write(Unpooled.copyBoolean(false));
            return;
        }
        //if an existing entry conflicts with new one, delete the existing entry and all that follow it
        synchronized (State.class) {
            if (prevLogIndex < State.log.size() - 1) {
                int i = (int) prevLogIndex + 1;
                for (; i < State.log.size(); i++) {
                    State.log.remove(i);
                }
            }

            State.leaderId = leaderId;
            if (leaderCommit > State.commitIndex) {
                if (leaderCommit < (State.log.size() - 1)) {
                    State.commitIndex = leaderCommit;
                } else {
                    State.commitIndex = State.log.size() - 1;
                }
                State.class.notifyAll();
            }

            if (entryNum == 0) {
                //just heartbeat, then woke up other waiting thread
                    State.class.notifyAll();

            } else {
                //for logs replication
                State.log.addAll(entries);
            }
        }
        ctx.channel().write(Unpooled.copyLong(term));
        ctx.channel().write(Unpooled.copyBoolean(true));
        ctx.channel().flush();

        return;


    }
}
