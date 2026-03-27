package analyzer;

import java.util.ArrayList;
import java.util.List;

public class AssertionCall {
        public String assertType;
        public List<AssertArgument> arguments;

        public AssertionCall() {
            //this.calls = new ArrayList<>();
            this.arguments = new ArrayList<>();
        }

        public AssertionCall(String assertType, String receiver, List<MethodCall> calls) {
            this.assertType = assertType;
            // this.receiver = receiver;
            //this.calls = calls;
        }

        @Override
        public String toString() {

            StringBuilder sb = new StringBuilder("-").append(assertType).append("(");
            /*sb.append(receiver).append("\n");
            for (MethodCall call : calls) {
                sb.append("method: ").append(call.methodName).append("\n");
                sb.append("arg: ").append(String.join(", ", call.arguments)).append("\n");
            }
            sb.append(")");*/
            return sb.toString();
        }
}
