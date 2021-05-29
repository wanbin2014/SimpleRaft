package org.simpleRaft.simpleRaft;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

enum RequestVoteMsg {
    TERM,
    CANDIDATEIDLEN,
    CANDIDATEID,
    LASTLOGINDEX,
    LASTTERM,
}
public class RequestVoteDecode extends ReplayingDecoder<RequestVoteMsg> {

    final static Logger logger = LoggerFactory.getLogger(RequestVoteDecode.class);
    long term;
    int candidateIdLen;
    String candidateId;
    long lastLogIndex;
    long lastTerm;

    public RequestVoteDecode() {
        super(RequestVoteMsg.TERM);
    }
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        switch (state()) {
            case TERM:
                term = in.readLong();
                checkpoint(RequestVoteMsg.CANDIDATEIDLEN);
            case CANDIDATEIDLEN:
                candidateIdLen = in.readInt();
                checkpoint(RequestVoteMsg.CANDIDATEID);
            case CANDIDATEID:
                candidateId = (String)in.readCharSequence( candidateIdLen, CharsetUtil.UTF_8);
                checkpoint(RequestVoteMsg.LASTLOGINDEX);
            case LASTLOGINDEX:
                lastLogIndex = in.readLong();
                checkpoint(RequestVoteMsg.LASTTERM);
            case LASTTERM:
                lastTerm = in.readLong();
                checkpoint(RequestVoteMsg.TERM);
                break;
            default:
                throw new Error("Shouldn't reach here!");
        }
        State.WriteLog.flush();
        logger.info("Received a request for vote from {}, " +
                "term={}, lastLogIndex={}, lastTerm", candidateId, term, lastLogIndex, lastTerm);
        //Receiver implementation:
        /*
        1.Reply false if item  < currentItem
        2.If votedFor is null or candidateId, and candidate’s log is at
          least as up-to-date as receiver’s log, grant vote
         */
        boolean votedGranted = false;
        synchronized (State.class) {

            if (term < State.currentTerm) {
                votedGranted = false;
                term = State.currentTerm;
            } else if (term > State.currentTerm){
                State.currentTerm = term;
                State.votedFor = null;
                State.voteCount = 0;
            }
            if (State.votedFor == null || State.votedFor.equals(candidateId)) {

                if (State.log.size() == 0) {
                    votedGranted = true;
                    State.votedFor = candidateId;
                } else if (lastTerm > State.log.get(State.log.size() - 1).getTerm()) {
                    votedGranted = true;
                    State.votedFor = candidateId;
                } else if (lastTerm == State.log.get(State.log.size() - 1).getTerm()
                        && lastLogIndex >= State.log.size() - 1) {
                    votedGranted = true;
                    State.votedFor = candidateId;
                }
            }
        }

        ctx.channel().write(Unpooled.copyLong(term));
        ctx.channel().write(Unpooled.copyBoolean(votedGranted));
        ctx.channel().flush();

        ctx.channel().pipeline().addLast(new ReplyVoteDecode());

    }
}
