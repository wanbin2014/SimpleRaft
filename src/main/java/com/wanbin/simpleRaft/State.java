package com.wanbin.simpleRaft;

import com.wanbin.simpleRaft.rpc.AppendEntries;
import com.wanbin.simpleRaft.rpc.RequestVote;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class State {
    final static Logger logger = LoggerFactory.getLogger(State.class);
    static volatile long currentTerm; // latest term server has been seen
    static volatile String votedFor; // candidateId that received vote in current term;
    static ArrayList<Entry> log = new ArrayList<>(); // log entries
    static int writeLogIndex = 0;

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

    static Thread applyLogThread ;
    static Thread writeLogThread;
    static Thread heartbeatThread;
    static Thread leaderFreshThread;
    static int connectSucess;
    static int appendEntrySuccess;


    public static void setMembers(String[] members) {
        State.members = members;

    }

    public static void setCandidateId(String candidateId) {
        State.candidateId = candidateId;
    }

    public static synchronized void load() throws IOException {
        try {
            String fileName = "./" + candidateId + ".meta";
            BufferedReader input = new BufferedReader(new FileReader(fileName));
            String[] fields = input.readLine().toString().split(",");
            currentTerm = Long.parseLong(fields[0]);
            votedFor = fields[1];
            input.close();
        } catch (FileNotFoundException e) {
            currentTerm = 0;
            votedFor = null;
        }
        try {
            String fileName = "./" + candidateId + ".log";
            BufferedReader input = new BufferedReader(new FileReader(fileName));
            String line;
            long idx = 0;
            while ((line = input.readLine()) != null) {
                String[] fields = line.split(",");
                log.add(new Entry(Long.valueOf(fields[0]), fields[1]));
                idx++;
            }
            input.close();

            commitIndex = 0;
            for(int i = 0; i < members.length; i++) {
                nextIndex.put(members[i], idx);
                matchIndex.put(members[i],0L);
            }
        } catch (FileNotFoundException e) {
            currentTerm = 0;
            votedFor = null;

            commitIndex = 0;
            for(int i = 0; i < members.length; i++) {
                nextIndex.put(members[i], 0L);
                matchIndex.put(members[i],0L);
            }
        }

        ApplyLog applyLog = new ApplyLog();
        applyLogThread = new Thread(applyLog);
        applyLogThread.start();

        WriteLog writeLog = new WriteLog();
        writeLogThread = new Thread(writeLog);
        writeLogThread.start();


        logger.info("Load meta data and log successfully");

    }

    public static void shutdown() {
        applyLogThread.interrupt();
        writeLogThread.interrupt();

        while (applyLogThread.isAlive() || writeLogThread.isAlive()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (heartbeatThread != null) {
            heartbeatThread.interrupted();
        }
        while (heartbeatThread.isAlive() ) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }



    public static class WriteLog implements Runnable {

        private void flush() {
            synchronized (State.class) {
                try {
                    String fileName = "./" + candidateId + ".meta";
                    BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));
                    String s = currentTerm + "," + votedFor;
                    writer.write(s);
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    String fileName = "./" + candidateId + ".log";
                    BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true));

                    for (int i = writeLogIndex; i < State.log.size(); i++) {
                        String line = State.log.get(i).toString();
                        writer.write(line);
                    }
                    writer.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


        @Override
        public synchronized void run() {
            while (true) {
                flush();
                try {
                    wait(1000);
                } catch (InterruptedException e) {
                    flush();
                    break;
                }


            }
        }

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
                .group(SimpleRaftServer.workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addFirst("ReadTimeoutHandler", new ReadTimeoutHandler(3000));
                        ch.pipeline().addLast("WriteTimeoutHandler", new WriteTimeoutHandler(3000));
                    }
                });

        ChannelFuture f = b.connect(ip, port).addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                //RPC type. 1 for RequestVote, 2 for AppendEntries
                future.channel().write(Unpooled.copyInt(2));
                future.channel().write(Unpooled.copyLong(appendEntries.getTerm()));
                future.channel().write(Unpooled.copyInt(appendEntries.getLeaderId()
                        .getBytes(StandardCharsets.UTF_8).length));
                future.channel().write(Unpooled.copiedBuffer(appendEntries.getLeaderId(),CharsetUtil.UTF_8));
                future.channel().write(Unpooled.copyLong(appendEntries.getPrevLogIndex()));
                future.channel().write(Unpooled.copyLong(appendEntries.getPrevLogTerm()));
                long replicatedLogIndex = 0;
                if (appendEntries.getEntries() == null || appendEntries.getEntries().size() == 0) {
                    future.channel().write(Unpooled.copyLong(0));
                    replicatedLogIndex = appendEntries.getPrevLogIndex();
                } else {
                    future.channel().write(Unpooled.copyLong(appendEntries.getEntries().size()));
                    for(int i = 0; i < appendEntries.getEntries().size(); i++) {
                        future.channel().write(Unpooled.copyInt(appendEntries.getEntries().get(i)
                                .getBytes(StandardCharsets.UTF_8).length));

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
                .group(SimpleRaftServer.workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addFirst("ReadTimeoutHandler", new ReadTimeoutHandler(300));
                        ch.pipeline().addLast("WriteTimeoutHandler", new WriteTimeoutHandler(300));
                        ch.pipeline().addLast(new ReplyVoteDecode());
                    }
                });

        ChannelFuture f = b.connect(ip, port).addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                connectSucess++;
                //RPC type. 1 for RequestVote, 2 for AppendEntries
                future.channel().write(Unpooled.copyInt(1));
                future.channel().write(Unpooled.copyLong(requestVote.getTerm()));
                future.channel().write(Unpooled.copyInt(requestVote.getCandidateId().getBytes(StandardCharsets.UTF_8).length));
                future.channel().write(Unpooled.copiedBuffer(
                        requestVote.getCandidateId(), CharsetUtil.UTF_8));
                future.channel().write(Unpooled.copyLong(requestVote.getLastLogIndex()));
                future.channel().write(Unpooled.copyLong(requestVote.getLastTerm()));
                future.channel().flush();
            }
        });


    }

    /*

     */
    static class Heartbeat implements Runnable {

        long timeout;

        public Heartbeat(long timeout) {
            this.timeout = timeout;
        }

        @Override
        public synchronized  void run() {
            while (leaderId.equals(candidateId)) {
                logger.info("Send heart beat to all server");
                long startTime = System.nanoTime();
                appendEntrySuccess = 0;
                callAllAppendEntries(false);
                try {
                    while (appendEntrySuccess <= members.length / 2.0) {
                        long endTime =  System.nanoTime();
                        long residual = timeout - (endTime - startTime) / 1000000;
                        if (residual <= 0) {
                            break;
                        }
                        wait(residual > 1000 ? 1000 : residual);
                    }
                } catch (InterruptedException e) {
                    logger.info("The process of sending heartbeat occur interrupted");
                    break;
                }
                try {
                    long endTime =  System.nanoTime();
                    long residual = timeout - (endTime - startTime) / 1000000;
                    if (residual > 0) {
                        wait(residual);
                    }
                } catch (InterruptedException e) {
                    logger.info("The process of sending heartbeat occur interrupted");
                    break;
                }

            }
        }
    }

    public static synchronized void callAllAppendEntries(boolean containEntries) {
        for(int i = 0; i < members.length; i++) {
            long prevLogIndex = State.nextIndex.get(members[i]);
            long prevLogTerm = 0;
            if (State.log.size() == 0) {
                prevLogTerm = 0;
            } else {
                prevLogTerm = State.log.get((int) prevLogIndex).term;
            }

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
    public  static synchronized void startElection(int random)  {

        try {
            State.class.wait(random * 1000);
            if (leaderId == null) {
                currentTerm += 1;
            }
            while(true) {

                if (leaderId == null) {
                    logger.info("No valid leader, so start a election");
                    if (leaderFreshThread != null) {
                        leaderFreshThread.interrupted();
                        leaderFreshThread = null;
                    }
                    if ((connectSucess + 1) > (float) (members.length / 2.0))
                        currentTerm += 1;
                    votedFor = candidateId;

                    voteCount = 0;
                    connectSucess = 0;
                    for (int i = 0; i < members.length; i++) {
                        String[] fields = members[i].split(":");
                        RequestVote requestVote = null;
                        if (log.size() > 0) {
                            requestVote = new RequestVote(currentTerm, candidateId, log.size() - 1,
                                    log.get(log.size() - 1).getTerm());
                        } else {
                            requestVote = new RequestVote(currentTerm, candidateId, 0,
                                    0);
                        }
                        State.callRequestVote(requestVote, fields[0], Integer.valueOf(fields[1]));
                    }

                    logger.info("CurrentTerm:{}, request vote to all server", currentTerm);
                    State.class.wait(10000);
                    logger.info("CurrentTerm:{}, receive {} votes", currentTerm, voteCount);


                    //received a majority of votes , upgrade to leader
                    if ((voteCount + 1) > (float) (members.length / 2.0) && leaderId == null) {
                        logger.info("Succeed in election");
                        leaderId = candidateId;
                        heartbeatThread = new Thread(new Heartbeat(5000));
                        heartbeatThread.start();
                    }
                    State.class.wait(random * 1000);

                } else {
                    State.class.wait(1000);
                }
            }
        } catch (InterruptedException e) {
            logger.info("The loop of processing election interrupt");
            return;
        }
    }
}
