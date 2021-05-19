package com.wanbin.jraft;

import com.wanbin.jraft.rpc.AppendEntries;
import com.wanbin.jraft.rpc.RequestVote;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.util.CharsetUtil;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class State {
    static volatile long currentTerm; // latest term server has been seen
    static volatile String votedFor; // candidateId that received vote in current term;
    static ArrayList<Entry> log = new ArrayList<>(); // log entries

    static volatile long commitIndex; // index of highest log entry known to be committed
    static volatile long lastApplied; // index of highest log entry applied to state machine

    //for each server, index of the next log entry to send to that server
    //initialized to leader last log index + 1
    static Map<String, Long> nextIndex = new HashMap<>(); //Only on leader
    //for each server, index of the highest log entry known to be replicated on server
    static Map<String, Long> matchIndex = new HashMap<>(); //Only on leader

    static Map<Long,Integer> appendEntriesResult = new HashMap<>();
    static volatile String candidateId ;

    static String[] members;

    static volatile int voteCount;


    static volatile String leaderId;

    public static void setMembers(String[] members) {
        State.members = members;

    }

    public static void setCandidateId(String candidateId) {
        State.candidateId = candidateId;
    }

    public static synchronized void load() throws IOException {
        BufferedReader input = new BufferedReader(new FileReader("./meta"));
        String[] fields = input.toString().split(",");
        currentTerm = Long.parseLong(fields[0]);
        votedFor = fields[1];
        input.close();

        input = new BufferedReader(new FileReader("./log"));
        String line;
        long idx = 0;
        while ((line = input.readLine()) != null) {
            fields = line.split(",");
            log.add(new Entry(Long.valueOf(fields[0]),fields[1]));
            idx++;
        }
        commitIndex = 0;
        for(int i = 0; i < members.length; i++) {
            nextIndex.put(members[i], idx);
            matchIndex.put(members[i],0L);
        }

        ApplyLog applyLog = new ApplyLog();
        new Thread(applyLog).start();

    }

    public static class ApplyLog implements Runnable {

        @Override
        public void run() {
            while(true) {
                synchronized (State.class) {
                    if (State.commitIndex > State.lastApplied) {
                        State.log.get((int) State.lastApplied + 1).commit();
                        State.lastApplied += 1;
                    } else {
                        try {
                            State.class.wait(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

        }
    }

    public  static void callAppendEntries(AppendEntries appendEntries, String ip, int port) {

        InetSocketAddress addr = new InetSocketAddress(ip,port);
        String peer = ip + ":" + String.valueOf(port);

        Bootstrap b = new Bootstrap()
                .group(Server.workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addFirst("ReadTimeoutHandler", new ReadTimeoutHandler(30));
                        ch.pipeline().addLast("WriteTimeoutHandler", new WriteTimeoutHandler(30));
                    }
                });

        ChannelFuture f = b.connect(ip, port).addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                //RPC type. 1 for RequestVote, 2 for AppendEntries
                future.channel().write(Unpooled.copyLong(2));
                future.channel().write(Unpooled.copyLong(appendEntries.getTerm()));
                future.channel().write(Unpooled.copiedBuffer(appendEntries.getLeaderId(),CharsetUtil.UTF_8));
                future.channel().write(Unpooled.copyLong(appendEntries.getPrevLogIndex()));
                future.channel().write(Unpooled.copyLong(appendEntries.getPrevLogTerm()));
                long replicatedLogIndex = 0;
                if (appendEntries.getEntries() == null || appendEntries.getEntries().size() == 0) {
                    future.channel().write(Unpooled.copyLong(0));
                } else {
                    future.channel().write(Unpooled.copyLong(appendEntries.getEntries().size()));
                    for(int i = 0; i < appendEntries.getEntries().size(); i++) {
                        future.channel().write(Unpooled.copyLong(i));
                        future.channel().write(Unpooled.copiedBuffer(appendEntries.getEntries().get(i),
                                CharsetUtil.UTF_8));
                    }
                    replicatedLogIndex = appendEntries.getPrevLogIndex() + appendEntries.getEntries().size() - 1;
                }
                future.channel().write(Unpooled.copyLong(appendEntries.getLeaderCommit()));
                future.channel().flush();
                future.channel().pipeline().addLast(new ReplyAppendEntriesDecode(peer, replicatedLogIndex));
            }
        });
    }


    public static void callRequestVote(RequestVote requestVote, String ip, int port) {

        InetSocketAddress addr = new InetSocketAddress(ip,port);

        Bootstrap b = new Bootstrap()
                .group(Server.workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addFirst("ReadTimeoutHandler", new ReadTimeoutHandler(300));
                        ch.pipeline().addLast("WriteTimeoutHandler", new WriteTimeoutHandler(30));
                        ch.pipeline().addLast(new ReplyVoteDecode());
                    }
                });

        ChannelFuture f = b.connect(ip, port).addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                //RPC type. 1 for RequestVote, 2 for AppendEntries
                future.channel().write(Unpooled.copyLong(1));
                future.channel().write(Unpooled.copyLong(requestVote.getTerm()));
                future.channel().write(Unpooled.copiedBuffer(
                        requestVote.getCandidateId(), CharsetUtil.UTF_8));
                future.channel().write(Unpooled.copyLong(requestVote.getLastLogIndex()));
                future.channel().write(Unpooled.copyLong(requestVote.getLastTerm()));
                future.channel().flush();
            }
        });

    }

    public static synchronized void callAllAppendEntries(boolean containEntries) {
        for(int i = 0; i < members.length; i++) {
            long prevLogIndex = State.nextIndex.get(members[i]);
            long prevLogTerm = State.log.get((int) prevLogIndex).term;

            List<String> entries = null;
            if (containEntries == true) {
                entries = new ArrayList();
                int num = State.log.size() - 1 - (int) prevLogIndex;

                for (int j = 0; j < num; j++) {
                    String content = String.valueOf(State.log.get(j + (int) prevLogIndex).term) + ","
                            + State.log.get(j + (int) prevLogIndex).command;
                    entries.add(content);
                }
            }

            AppendEntries appendEntries = new AppendEntries(State.currentTerm, State.leaderId, prevLogIndex, prevLogTerm,
                    entries, State.commitIndex);

            String[] fields = members[i].split(":");
            State.callAppendEntries(appendEntries, fields[0], Integer.valueOf(fields[1]));
        }
    }
    public  static synchronized void startElection(int random) throws InterruptedException {

        State.class.wait(random * 1000);


        while (leaderId == null) {
            currentTerm += 1;
            votedFor = candidateId;

            voteCount = 0;
            for (int i = 0; i < members.length; i++) {
                String[] fields = members[i].split(":");
                RequestVote requestVote = new RequestVote(currentTerm, candidateId, log.size() - 1,
                        log.get(log.size() - 1).getTerm());
                State.callRequestVote(requestVote, fields[0], Integer.valueOf(fields[1]));
            }
            State.class.wait(1000);


            //received a majority of votes , upgrade to leader
            if (voteCount > (float) (members.length / 2.0) && leaderId == null) {
                leaderId = candidateId;
                callAllAppendEntries(false);
            }

        }

    }

}
