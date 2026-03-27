package org.example;

import java.util.ArrayList;
import java.util.List;

public class AssertedValue {
    public String type;  // e.g., org.jsoup.Element
    public String value;
    public List<ArgumentStructure> arguments = new ArrayList<>();// e.g., p.text() or p.name
}
