package tree.analyzer;

import java.util.ArrayList;
import java.util.List;

public class JavapMethodBlock {
    public String methodName;
    public String returnType;
    public String parameterList;
    public boolean isTestMethod;
    public List<String> codeLines = new ArrayList<>();

    public JavapMethodBlock(String m, String p, String r, boolean t, List<String> c){
        this.methodName = m;
        this.parameterList = p;
        this.returnType = r;
        this.isTestMethod = t;
        this.codeLines = c;
    }
}
