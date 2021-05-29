package org.simpleRaft.simpleRaft;

public class Entry {
    long term;
    String command;

    public Entry(long term, String command) {
        this.term = term;
        this.command = command;
    }

    public long getTerm() {
        return term;
    }

    public void setTerm(long term) {
        this.term = term;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    @Override
    public String toString() {
        return term + ":" + command;
    }

    public static Entry getEntry(String line) {
        String[] fields = line.split(":");
        return new Entry(Long.valueOf(fields[0]), fields[1]);
    }

    public void commit() {


    }
}
