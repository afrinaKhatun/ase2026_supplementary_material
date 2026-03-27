package analyzer;

import java.util.ArrayList;
import java.util.List;

public class MethodCallSignature {

        public String receiverName;
        public String receiverType;
        public List<MethodCall> calls;
        public boolean isFromAssertion;

        public MethodCallSignature(){
            this.calls = new ArrayList<>();
        }
        public MethodCallSignature(List<MethodCall> calls){
            this.calls = calls;
        }

        public MethodCallSignature(String receiverName, String receiverType, List<MethodCall> calls) {
            this.receiverName = receiverName;
            this.receiverType = receiverType;
            this.calls = calls;
        }

        @Override
        public String toString() {
            String s = receiverType+".";
            for (MethodCall call : calls) {
                s = s + call.methodName + "()";
                //sb.append("arg: ").append(String.join(", ", call.arguments)).append("\n");
            }
            return s;
        }

}
