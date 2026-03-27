package analyzer;

public class AssertArgument {
        public String argumentType;
        public String receiverName;
        public String receiverType;
        public String literal_attribute_methodName;
        public String returnType;
        public MethodCallSignature meth_chain = new MethodCallSignature();
        public boolean isGetterMethod;
}
