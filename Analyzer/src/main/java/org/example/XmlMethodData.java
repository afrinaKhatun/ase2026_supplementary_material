package org.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class XmlMethodData {
    String name;
    int startLine;
    Map<Integer,Boolean> lines ;
    public XmlMethodData(String n, int s){
        this.name = n;
        this.startLine = s;
        lines = new HashMap<Integer,Boolean>();
    }

    public String getName() {
        return name;
    }

    public int getStartLine() {
        return startLine;
    }
}
