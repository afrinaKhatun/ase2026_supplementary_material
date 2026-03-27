package analyzer;

import java.util.List;

public class MethodCall {

        public String methodName;
        public List<AssertArgument> arguments;
        public String returnType;

        public MethodCall(String methodName, List<AssertArgument> arguments, String rt) {
            this.methodName = methodName;
            this.arguments = arguments;
            this.returnType = rt;
        }
}
