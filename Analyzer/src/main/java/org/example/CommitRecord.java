package org.example;

import java.util.Map;
import java.util.Set;

public class CommitRecord {
    public String commit_hash;
    public Map<String, MethodList> method_list;
    public Set<String> commited_files;
    public String commit_message;
    public String commit_time;
    public boolean hasSourceFileChanges;

    public CommitRecord(){}

    public CommitRecord(String ch, Map<String, MethodList> ml, Set<String> cf, String m, String ct) {
        this.commit_hash = ch;
        this.method_list = ml;
        this.commited_files = cf;
        this.commit_message = m;
        this.commit_time = ct;
    }
    public CommitRecord(String ch, Map<String, MethodList> ml,  String m) {
        this.commit_hash = ch;
        this.method_list = ml;

        this.commit_message = m;

    }
    public CommitRecord(String ch, Map<String, MethodList> ml,  Set<String> cf,String m, boolean s) {
        this.commit_hash = ch;
        this.method_list = ml;
        this.commited_files = cf;
        this.commit_message = m;
        this.hasSourceFileChanges = s;

    }
    @Override public String toString() {
        return commit_hash + " " + commited_files + " (" + commit_message + ")";
    }
}
