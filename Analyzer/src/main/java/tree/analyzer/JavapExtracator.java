package tree.analyzer;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavapExtracator {
    /*private static final Pattern METHOD_SIG_PATTERN = Pattern.compile(
            "^\\s*(?:(?:public|protected|private|static|final|abstract|synchronized|native|strictfp)\\s+)*"
                    + "([\\w$./<>?]+(?:\\[\\])*(?:\\s*\\.\\.\\.)?)\\s+"      // group(1) return type
                    + "([\\w$]+)\\s*\\([^)]*\\)\\s*"                         // group(2) method name
                    + "(?:throws\\s+[\\w$./]+(?:\\[\\])?(?:\\s*,\\s*[\\w$./]+(?:\\[\\])?)*)?\\s*;\\s*$"
    );*/

    private static final Pattern METHOD_SIG_PATTERN = Pattern.compile(
            "^\\s*(?:(?:public|protected|private|static|final|abstract|synchronized|native|strictfp)\\s+)*"
                    + "([\\w$./<>?]+(?:\\[\\])*(?:\\s*\\.\\.\\.)?)\\s+"   // group(1) return type
                    + "([\\w$<>]+)\\s*\\(([^)]*)\\)\\s*"                  // group(2) method name, group(3) params
                    + "(?:throws\\s+[\\w$./]+(?:\\[\\])?(?:\\s*,\\s*[\\w$./]+(?:\\[\\])?)*)?\\s*;\\s*$"
    );
    private static final Pattern INSTRUCTION_LINE = Pattern.compile("^\\s*\\d+:.*$");

    private static final Pattern INVOKE_PATTERN = Pattern.compile(
            "^\\s*(\\d+):\\s+" +
                    "(invoke(?:static|virtual|special|interface))\\s+" +
                    "#(\\d+)(?:,\\s*(\\d+))?\\s*" +
                    "//\\s*(?:Method|InterfaceMethod)\\s+" +
                    "(.+)\\.([\\w$<>]+):\\(([^)]*)\\)([^\\s;]+);?$"
    );
    private static final Set<String> ASSERT_OWNER_PREFIXES = new HashSet<>();
    static {
        // JUnit 4 / 5
        ASSERT_OWNER_PREFIXES.add("org/junit/");
        ASSERT_OWNER_PREFIXES.add("org/junit/jupiter/api/");

        // JUnit 3
        ASSERT_OWNER_PREFIXES.add("junit/framework/");

        // Hamcrest
        ASSERT_OWNER_PREFIXES.add("org/hamcrest/");

        // AssertJ
        ASSERT_OWNER_PREFIXES.add("org/assertj/core/api/");
    }

    public static Map<String, JavapMethodBlock> getMethodInformation(List<String> javapContentLines) {
        Map<String, JavapMethodBlock> methodsInformation = new HashMap<>();
        String currentReturnType = null;
        String currentMethodName = null;
        String currentParameterList = null;
        boolean waitingForCode = false;
        boolean isTestMethod = false;

        int i;
        for (i = 0; i < javapContentLines.size(); i++) {
            String line = javapContentLines.get(i);

            // 1) Detect a method signature
            Matcher m = METHOD_SIG_PATTERN.matcher(line);
            if (m.find()) {
                currentReturnType = m.group(1);
                currentMethodName = m.group(2);
                currentParameterList = m.group(3).trim();

                boolean isVoid = "void".equals(currentReturnType);
                boolean hasNoParams = currentParameterList.isEmpty();
                isTestMethod = isVoid && hasNoParams;

                waitingForCode = true;  // the next "Code:" (if present) belongs to this method
                continue;
            }

            // 2) If this method is a test and we hit "Code:", collect the block
            if (waitingForCode && line.trim().equals("Code:")) {
                List<String> code = collectCodeBlock(javapContentLines, i + 1);
                // store even non-tests (constructors may also appear; still fine)
                if (currentMethodName != null) {

                    methodsInformation.put(
                            currentMethodName,
                            new JavapMethodBlock(
                                    currentMethodName,
                                    currentParameterList,
                                    currentReturnType,
                                    isTestMethod,
                                    code
                            )
                    );
                }
                waitingForCode = false;
            }
        }
        return methodsInformation;
    }

    private static List<String> collectCodeBlock(List<String> lines, int startIdx) {
        List<String> block = new ArrayList<String>();
        int j;

        for (j = startIdx; j < lines.size(); j++) {
            String ln = lines.get(j);

            // Stop if a new method signature starts
            if (METHOD_SIG_PATTERN.matcher(ln).find()) {
                break;
            }

            // Typical bytecode instruction lines: "  18: invokestatic ..."
            if (INSTRUCTION_LINE.matcher(ln).matches()) {
                block.add(ln);
                continue;
            }

            // Allow empty lines inside the block
            if (ln.trim().isEmpty()) {
                block.add(ln);
                continue;
            }
        }
        return block;
    }

    public static JavapMethodStruct parseFromMethodCommentLine(String line,  List<JavapMethodBlock> javapMethods) {
        int commentIdx = line.indexOf("//");
        if (commentIdx < 0) return null;

        String comment = line.substring(commentIdx + 2).trim(); // after //
        // Accept "Method" or "InterfaceMethod"
        if (!(comment.startsWith("Method ") || comment.startsWith("InterfaceMethod "))) return null;

        // Strip the leading keyword
        String rest = comment.startsWith("Method ")
                ? comment.substring("Method ".length()).trim()
                : comment.substring("InterfaceMethod ".length()).trim();

        // Now rest looks like: org/apache/.../FuzzyDistance.compare:(Ljava/lang/CharSequence;)Ljava/lang/Integer;
        // Remove optional trailing semicolon if present
        if (rest.endsWith(";")) rest = rest.substring(0, rest.length() - 1);

        // Find the colon that precedes the descriptor
        int colon = rest.lastIndexOf(':');
        if (colon < 0 || colon + 1 >= rest.length()) return null;

        String ownerAndName = rest.substring(0, colon);   // org/apache/.../FuzzyDistance.compare
        String desc         = rest.substring(colon + 1);  // (Ljava/lang/...;)Ljava/lang/Integer

        // ---- UPDATED LOGIC HERE ----
        // Try to split owner + method name using last dot.
        int lastDot = ownerAndName.lastIndexOf('.');

        String owner;
        String mname;

        if (lastDot < 0) {
            // Case: NO OWNER (e.g., "println" or "compare")
            owner = "";                       // optional: you may set null instead
            mname = ownerAndName.trim();
        } else {
            owner = ownerAndName.substring(0, lastDot);
            mname = ownerAndName.substring(lastDot + 1);
        }
        // ---- END UPDATED LOGIC ----

        // Descriptor must be like: (args)ret
        int lpar = desc.indexOf('(');
        int rpar = desc.indexOf(')');
        if (lpar != 0 || rpar < 0 || rpar + 1 > desc.length()) return null;

        String argsDesc = desc.substring(1, rpar);       // inside (...)
        String retDesc  = desc.substring(rpar + 1);      // after )

        // Skip assertions if you want:
        if (isAssertionCall(owner, mname, javapMethods)) return null;

        return new JavapMethodStruct(owner, mname, argsDesc, retDesc);
    }

    private static boolean isAssertionCall(String owner, String methodName,List<JavapMethodBlock> javapMethods) {
        if (methodName == null) {
            return false;
        }
        boolean looksLikeAssertion = methodName.startsWith("assert") || methodName.equals("fail");

        /* ---------- CASE 1: OWNER PRESENT ---------- */
        if (owner != null && !owner.isEmpty()) {
            String normOwner = owner.replace('.', '/');
            boolean fromAssertionLib = ASSERT_OWNER_PREFIXES.stream()
                    .anyMatch(normOwner::startsWith);

            return fromAssertionLib && looksLikeAssertion;
        }

        /* ---------- CASE 2: OWNER MISSING (JUnit3 ambiguity) ---------- */
        if (!looksLikeAssertion) {
            return false; // cannot be assertion
        }

        // Check if method is declared in THIS class
        for (JavapMethodBlock m : javapMethods) {
            if (m.methodName.equals(methodName)) {
                // Declared locally
                return m.isTestMethod; // test → assertion, helper → not assertion
            }
        }

        return true;

    }

    public static boolean detectJUnit3TestClass(List<String> javapLines) {
        // javap class header often contains: "public class X extends junit.framework.TestCase"
        // Sometimes it appears in slash form depending on output.
        for (String l : javapLines) {
            String s = l.trim();
            if (s.contains("extends junit.framework.TestCase") ||
                    s.contains("extends junit/framework/TestCase") ||
                    s.contains("extends TestCase")) {
                return true;
            }
        }
        return false;
    }

}

