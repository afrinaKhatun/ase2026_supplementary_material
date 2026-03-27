package org.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenericTestCaseBody {
    public Map<String, VariableStructure> objects = new HashMap<>();
    public List<AssertionNewStructure> assertions = new ArrayList<>();
    public Map<String, MethodCallStructure> static_method_calls = new HashMap<>();
}
