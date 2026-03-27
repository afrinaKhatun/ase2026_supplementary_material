package org.example;

import java.util.ArrayList;
import java.util.HashMap;

public class XmlClassData {
    public String class_name;
    public String package_name;
    public HashMap<Integer,Boolean> line_coverage = new HashMap<>();
    public ArrayList<XmlMethodData> methods = new ArrayList<>();
    public int lines_covered;
    public int lines_missed;

}
