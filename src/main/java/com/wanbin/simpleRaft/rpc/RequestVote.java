package com.wanbin.simpleRaft.rpc;

public class RequestVote {
    long term; //candidate's term
    String candidateId; //candidate requesting vote
    long lastLogIndex; //index of candidate's last log entry
    long lastTerm; //term of candidate's last log entry

    public RequestVote(long term, String candidateId, long lastLogIndex, long lastTerm) {
        this.term = term;
        this.candidateId = candidateId;
        this.lastLogIndex = lastLogIndex;
        this.lastTerm = lastTerm;
    }

    public long getTerm() {
        return term;
    }

    public void setTerm(long term) {
        this.term = term;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public long getLastLogIndex() {
        return lastLogIndex;
    }

    public void setLastLogIndex(long lastLogIndex) {
        this.lastLogIndex = lastLogIndex;
    }

    public long getLastTerm() {
        return lastTerm;
    }

    public void setLastTerm(long lastTerm) {
        this.lastTerm = lastTerm;
    }
}
