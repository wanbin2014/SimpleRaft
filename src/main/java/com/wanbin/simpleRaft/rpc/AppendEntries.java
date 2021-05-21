package com.wanbin.simpleRaft.rpc;

import java.util.List;

public class AppendEntries {
    long term;//leader's term
    String leaderId;
    long  prevLogIndex; //index of log entry immediately preceding new ones
    long prevLogTerm; //term of prevLogIndex entry
    List<String> entries; // log entries to store ,empty for heartbeat
    long leaderCommit; //leader's commitIndex


    public AppendEntries(long term, String leaderId, long prevLogIndex, long prevLogTerm, List<String> entries, long leaderCommit) {
        this.term = term;
        this.leaderId = leaderId;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.entries = entries;
        this.leaderCommit = leaderCommit;
    }

    public long getTerm() {
        return term;
    }

    public String getLeaderId() {
        return leaderId;
    }

    public long getPrevLogIndex() {
        return prevLogIndex;
    }

    public long getPrevLogTerm() {
        return prevLogTerm;
    }

    public List<String> getEntries() {
        return entries;
    }

    public long getLeaderCommit() {
        return leaderCommit;
    }
}
