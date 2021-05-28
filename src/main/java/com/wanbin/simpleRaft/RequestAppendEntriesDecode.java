package com.wanbin.simpleRaft;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


enum AppendEntiesMsg {
    TERM,
    LEADERIDLEN,
    LEADERID,
    PREVLOGINDEX,
    PREVTERM,
    ENTRYNUM,
    ENTRYCONTENTLEN,
    ENTRYCONTENT,
    LEADERCOMMIT,
}

class LeaderTimeout implements Runnable{
    long timeout;

    public LeaderTimeout(long timeout) {
        this.timeout = timeout;
    }

    @Override
    public void run() {
        synchronized (State.class) {
            long startTime = System.nanoTime();
            long residual = 0;
            while(true) {
                try {
                    State.class.wait(residual == 0 ? 1000 : residual );
                } catch (InterruptedException e) {
                    return;
                }
                long endTime = System.nanoTime();
                residual = timeout - (endTime - startTime) / 1000000;
                if (residual <= 0) {
                    break;
                }
            }
            State.leaderId = null;
        }

    }
}
public class RequestAppendEntriesDecode extends ReplayingDecoder<AppendEntiesMsg> {

    final static Logger logger = LoggerFactory.getLogger(RequestAppendEntriesDecode.class);
    long term;
    int leaderIdLen;
    String leaderId;
    long prevLogIndex;
    long prevTerm;
    long entryNum;
    List<Integer> entryLen = new ArrayList<>();
    List<Entry> entries = new ArrayList<>();
    long leaderCommit;

    int entryCount;
    boolean isEnd = false;

    public RequestAppendEntriesDecode() {
        super(AppendEntiesMsg.TERM);
        entryCount = 0;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        isEnd = false;
        while(true) {
            switch (state()) {
                case TERM:
                    term = in.readLong();
                    checkpoint(AppendEntiesMsg.LEADERIDLEN);
                case LEADERIDLEN:
                    leaderIdLen = in.readInt();
                    checkpoint(AppendEntiesMsg.LEADERID);
                case LEADERID:
                    leaderId = (String) in.readCharSequence(leaderIdLen, CharsetUtil.UTF_8);
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
                        break;
                    } else {
                        checkpoint(AppendEntiesMsg.ENTRYCONTENTLEN);
                    }
                case ENTRYCONTENTLEN:
                    int len = in.readInt();
                    entryLen.add(len);
                    checkpoint(AppendEntiesMsg.ENTRYCONTENT);
                case ENTRYCONTENT:
                    String content = (String) in.readCharSequence(entryLen.get(entryLen.size() - 1),
                            CharsetUtil.UTF_8);

                    Entry entry = Entry.getEntry(content);
                    entries.add(entry);
                    entryCount += 1;
                    if (entryCount == entryNum) {
                        checkpoint(AppendEntiesMsg.LEADERCOMMIT);
                    } else {
                        checkpoint(AppendEntiesMsg.ENTRYCONTENTLEN);
                        break;
                    }
                case LEADERCOMMIT:
                    leaderCommit = in.readLong();
                    entryCount = 0;
                    checkpoint(AppendEntiesMsg.TERM);
                    isEnd = true;
                    break;
                default:
                    throw new Error("Shouldn't reach here!");

            }
            if (isEnd == true) {
                break;
            } else {
                continue;
            }
        }
        logger.info("Received a request of AppendEntries. " +
                "term={},leaderId={},prevLogIndex={},prevLogTerm={},log={},leaderCommit={}",
                term,leaderId,prevLogIndex,prevTerm,entries,leaderCommit);
        boolean success;
        //refuse if term < currentTerm
        if (term < State.currentTerm) {
            term = State.currentTerm;
            ctx.channel().write(Unpooled.copyLong(term));
            ctx.channel().write(Unpooled.copyBoolean(false));
            return;
        }
        //refuse if it doesn't contain prevLogIndex
        if (prevLogIndex != -1 && prevLogIndex > State.log.size()-1) {
            ctx.channel().write(Unpooled.copyLong(term));
            ctx.channel().write(Unpooled.copyBoolean(false));
            return;
        }
        //refuse if it doesn't match prevTerm at prevLogIndex
        if (prevLogIndex != -1 && State.log.get((int)prevLogIndex).getTerm() != term) {
            ctx.channel().write(Unpooled.copyLong(term));
            ctx.channel().write(Unpooled.copyBoolean(false));

            return;
        }
        //if an existing entry conflicts with new one, delete the existing entry and all that follow it
        synchronized (State.class) {
            if (State.currentTerm < term) {
                State.currentTerm = term;
            }
            if (State.log.size() > 0 && prevLogIndex < State.log.size() - 1) {
                int i = (int) prevLogIndex + 1;
                for (; i < State.log.size(); i++) {
                    State.log.remove(i);
                }
            }

            State.leaderId = leaderId;
            if (State.leaderFreshThread != null) {
                State.leaderFreshThread.interrupt();
                State.leaderFreshThread = null;
            }
            State.leaderFreshThread = new Thread(new LeaderTimeout(10000));
            State.leaderFreshThread.start();

            if (leaderCommit > State.commitIndex) {
                if (State.log.size() == 0) {
                    State.commitIndex = 0;
                } else if (leaderCommit < (State.log.size() - 1)) {
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





    }
}
