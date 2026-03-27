package tree.analyzer;

import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.api.RefactoringType;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import java.util.*;
import java.util.stream.Collectors;

public class RefactoringMinerTesting {

    public static void main(String[] args) {
        for (RefactoringType t : RefactoringType.values()) {
            System.out.println(t.name());
        }

        String beforeContent =
                "package demo;\n" +
                        "public class A {\n" +
                        "@Test\n" +
                        "public void testBasicQuoted6() {\n" +
                        "    final String input = \"a:'b'\\\"c':d\";\n" +
                        "    final StrTokenizer token = new StrTokenizer(input, ':');\n" +
                        "    token.setQuoteMatcher(StrMatcher.quoteMatcher());\n" +
                        "    assertEquals(\"a\", token.next());\n" +
                        "    assertEquals(\"b\\\"c:d\", token.next());\n" +
                        "    assertFalse(token.hasNext());\n" +
                        "}"+
                        "}\n";

        String afterContent =
                "package demo;\n" +
                        "public class A {\n" +
                        "@Test\n" +
                        "public void testDelimString() {\n" +
                        "    final String input = \"a##b##c\";\n" +
                        "    final StrTokenizer tok = new StrTokenizer(input);\n" +
                        "    assertEquals(\"a\", tok.next());\n" +
                        "    assertEquals(\"b\", tok.next());\n" +
                        "    assertEquals(\"c\", tok.next());\n" +
                        "    assertFalse(tok.hasNext());\n" +
                        "}" +
                        "}\n";

        // dummy paths (required by RM, but don't need to exist on disk)
        String path = "src/main/java/demo/A.java";

        compareTwoFileVersions(path, beforeContent, path, afterContent);
    }

    public static void compareTwoFileVersions(
            String beforePath, String beforeContent,
            String afterPath,  String afterContent
    ) {
        Map<String, String> beforeFiles = new HashMap<>();
        Map<String, String> afterFiles  = new HashMap<>();
        beforeFiles.put(beforePath, beforeContent);
        afterFiles.put(afterPath, afterContent);

        GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();

        miner.detectAtFileContents(beforeFiles, afterFiles, new RefactoringHandler() {
            @Override
            public void handle(String commitId, List<Refactoring> refactorings) {

                // ✅ Apply your filter here
                List<Refactoring> methodRefactorings =
                        MethodRefactoringFilters.onlyMethodLevel(refactorings);

                System.out.println("=== RefactoringMiner results ===");
                System.out.println("All refactorings detected: " + refactorings.size());
                System.out.println("Method-level refactorings: " + methodRefactorings.size());
                System.out.println();

                // Print only method-level refactorings
                System.out.println("---- Method-level refactorings ----");
                if (methodRefactorings.isEmpty()) {
                    System.out.println("(none)");
                } else {
                    methodRefactorings.forEach(r ->
                            System.out.println("[" + r.getRefactoringType() + "] " + r)
                    );
                }
                System.out.println();

                // Print "type changes" summary only for method-level
                System.out.println("---- Method-level summary by RefactoringType ----");
                Map<RefactoringType, Long> byType = methodRefactorings.stream()
                        .collect(Collectors.groupingBy(
                                Refactoring::getRefactoringType,
                                TreeMap::new,
                                Collectors.counting()
                        ));

                if (byType.isEmpty()) {
                    System.out.println("(none)");
                } else {
                    byType.forEach((t, count) -> System.out.println(t + " = " + count));
                }
            }
        });
    }

    /** Put this in a separate file MethodRefactoringFilters.java if you prefer. */
    public static class MethodRefactoringFilters {

        // Adjust this set to include/exclude any refactoring type you want.
        public static final EnumSet<RefactoringType> METHOD_LEVEL_TYPES = EnumSet.of(
                // Core method refactorings
                RefactoringType.EXTRACT_OPERATION,
                RefactoringType.INLINE_OPERATION,
                RefactoringType.RENAME_METHOD,
                RefactoringType.MOVE_OPERATION,
                RefactoringType.PULL_UP_OPERATION,
                RefactoringType.PUSH_DOWN_OPERATION,
                RefactoringType.EXTRACT_AND_MOVE_OPERATION,
                RefactoringType.MOVE_AND_RENAME_OPERATION,
                RefactoringType.MOVE_AND_INLINE_OPERATION,

                // Method signature / API related
                RefactoringType.RENAME_VARIABLE,
                RefactoringType.ADD_PARAMETER,
                RefactoringType.REMOVE_PARAMETER,
                RefactoringType.REORDER_PARAMETER,
                RefactoringType.CHANGE_PARAMETER_TYPE,
                RefactoringType.CHANGE_RETURN_TYPE,

                // Throws / access / annotations (still method-level)
                RefactoringType.ADD_THROWN_EXCEPTION_TYPE,
                RefactoringType.REMOVE_THROWN_EXCEPTION_TYPE,
                RefactoringType.CHANGE_THROWN_EXCEPTION_TYPE,
                //RefactoringType.CHANGE_METHOD_ACCESS_MODIFIER,
                RefactoringType.ADD_METHOD_ANNOTATION,
                RefactoringType.REMOVE_METHOD_ANNOTATION,
                RefactoringType.MODIFY_METHOD_ANNOTATION
        );

        public static List<Refactoring> onlyMethodLevel(List<Refactoring> all) {
            return all.stream()
                    .filter(r -> METHOD_LEVEL_TYPES.contains(r.getRefactoringType()))
                    .collect(Collectors.toList());
        }
    }
}
