package org.example;

import java.util.*;

public class MethodList {
    public Map<String,PreviousMethodInfo> previous_methods = new HashMap<>();
    public ArrayList<MethodModificationInformation> added_or_modified_methods = new ArrayList<>();
    public String parent_commit_hash = null;
    public List<String> afterOrderedMethods = new ArrayList<>();
    public List<List<String>> addedMethodClusters = new ArrayList<>();
    public MethodList(){

    }

    @Override
    public String toString() {
        return "Existing: " + previous_methods + ", Modified: " + added_or_modified_methods;
    }
}
