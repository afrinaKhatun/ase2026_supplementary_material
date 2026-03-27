package org.example;

import org.checkerframework.checker.units.qual.A;

import java.util.ArrayList;
import java.util.List;

public class VariableStructure {
    public String name;
    public String type;
    public String constructor_signature;
    public List<ArgumentStructure> constructor_arguments = new ArrayList<>();
    public List<MethodCallStructure> method_calls = new ArrayList<>();
}
