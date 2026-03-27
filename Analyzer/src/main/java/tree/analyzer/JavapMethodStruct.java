package tree.analyzer;

public class JavapMethodStruct {

        public String classPath;               // e.g., org/apache/commons/text/similarity/CosineDistance
        public String methodName;                // method name: apply, <init>, etc.
        public String argsList;            // JVM args descriptor inside (...)
        public String returnType;             // JVM return descriptor after )

        public JavapMethodStruct(String c, String n, String a, String r){
            classPath = c;
            methodName = n;
            argsList = a;
            returnType = r;
        }

    }
