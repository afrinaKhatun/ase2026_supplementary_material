package tree.analyzer;

import analyzer.NormalizationData;
/*import com.github.gumtreediff.actions.EditScript;
import com.github.gumtreediff.actions.EditScriptGenerator;
import com.github.gumtreediff.actions.SimplifiedChawatheScriptGenerator;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.tree.Tree;
import com.github.gumtreediff.gen.javaparser.JavaParserGenerator;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.example.testParser.findFileByClassName;

public class TreeSimilarityCalculator {
    private static final Logger logger = LoggerFactory.getLogger(TreeSimilarityCalculator.class);

    public static double computeSimilarity( String sourceRootPath, String className, String normalizedMethod1, String normalizedMethod2) {
        try {
            // Configure JavaParser with symbol solver
            TypeSolver typeSolver = new CombinedTypeSolver(
                    new ReflectionTypeSolver(),
                    new JavaParserTypeSolver(new File("/Users/afrinakhatun/IdeaProjects/jsoup/src/main/java")),
                    new JavaParserTypeSolver(new File("/Users/afrinakhatun/IdeaProjects/jsoup/src/test/java"))
            );
            JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
            ParserConfiguration config = new ParserConfiguration().setSymbolResolver(symbolSolver);
            JavaParser parser = new JavaParser(config);

            // Parse the class file
            Path filePath = findFileByClassName(Paths.get(sourceRootPath), className);
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow();

            //custom types solver
            JavaParserTypeSolver mainSolver = new JavaParserTypeSolver(new File("/Users/afrinakhatun/IdeaProjects/jsoup/src/main/java"));
            JavaParserTypeSolver testSolver = new JavaParserTypeSolver(new File("/Users/afrinakhatun/IdeaProjects/jsoup/src/test/java"));
            List<JavaParserTypeSolver> customSolvers = List.of(mainSolver, testSolver);

            // Parse normalized method bodies
            logger.debug("Parsing normalized method bodies");
            CompilationUnit methodCu1 = parser.parse("public class Temp { " + normalizedMethod1 + " }").getResult()
                    .orElseThrow(() -> new IllegalArgumentException("Failed to parse normalizedMethod1: " + normalizedMethod1));
            CompilationUnit methodCu2 = parser.parse("public class Temp { " + normalizedMethod2 + " }").getResult()
                    .orElseThrow(() -> new IllegalArgumentException("Failed to parse normalizedMethod2: " + normalizedMethod2));

            MethodDeclaration method1 = methodCu1.findFirst(MethodDeclaration.class)
                    .orElseThrow(() -> new IllegalArgumentException("No method found in normalizedMethod1"));
            MethodDeclaration method2 = methodCu2.findFirst(MethodDeclaration.class)
                    .orElseThrow(() -> new IllegalArgumentException("No method found in normalizedMethod2"));


            // Create normalized class code with original imports and class
            logger.debug("Creating normalized class code for GumTree");
            String classCode1 = createNormalizedClass(cu, method1).toString();
            String classCode2 = createNormalizedClass(cu, method2).toString();

            logger.debug("Class code 1: {}", classCode1);
            logger.debug("Class code 2: {}", classCode2);


            // Convert to GumTree trees
            logger.debug("Generating GumTree trees");
            JavaParserGenerator generator = new JavaParserGenerator();
            Tree tree1 = generator.generateFrom().string(classCode1).getRoot();
            Tree tree2 = generator.generateFrom().string(classCode2).getRoot();

            // Compute mappings
            logger.debug("Computing tree mappings");
            Matcher matcher = Matchers.getInstance().getMatcher();
            MappingStore mappings = matcher.match(tree1, tree2);

            // Compute edit script
            logger.debug("Generating edit script");
            EditScriptGenerator editScriptGenerator = new SimplifiedChawatheScriptGenerator();
            EditScript editScript = editScriptGenerator.computeActions(mappings);

            // Calculate similarity
            int tree1Size = tree1.getDescendants().size() + 1; // Include root
            int tree2Size = tree2.getDescendants().size() + 1; // Include root
            int maxNodes = Math.max(tree1Size, tree2Size);
            int editDistance = editScript.size();
            logger.debug("Edit distance: {}, Max nodes: {}", editDistance, maxNodes);
            return maxNodes == 0 ? 1.0 : 1.0 - ((double) editDistance / maxNodes);
        } catch (Exception e) {
            logger.error("Error processing methods in file {}",
                     e.getMessage(), e);
            throw new IllegalArgumentException("Error processing methods: " + e.getMessage(), e);
        }
    }


    private static CompilationUnit createNormalizedClass(CompilationUnit originalCu, MethodDeclaration method) {
        CompilationUnit normalizedCu = new CompilationUnit();

        // Copy package declaration
        originalCu.getPackageDeclaration().ifPresent(normalizedCu::setPackageDeclaration);

        // Copy imports
        originalCu.getImports().forEach(normalizedCu::addImport);

        // Copy class declaration
        ClassOrInterfaceDeclaration originalClass = originalCu.findFirst(ClassOrInterfaceDeclaration.class)
                .orElseThrow(() -> new IllegalArgumentException("No class found in CompilationUnit"));
        ClassOrInterfaceDeclaration normalizedClass = normalizedCu.addClass(originalClass.getNameAsString());
        normalizedClass.setModifiers(originalClass.getModifiers());

        // Add the normalized method
        normalizedClass.addMember(method);

        return normalizedCu;
    }

    // Helper method to find a MethodDeclaration by name in the CompilationUnit
    private static MethodDeclaration findMethodByName(CompilationUnit cu, String methodName) {
        return cu.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.getNameAsString().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Method not found: " + methodName));
    }

    private static String wrap(String methodSrc) {
        return "class __Tmp__ { " + methodSrc + " }";
    }

    // Example usage
    public static void main(String[] args) throws IOException {
        Path filePath = findFileByClassName(
                Paths.get("/Users/afrinakhatun/IdeaProjects/jsoup/src/test/java"),
                "AttributesTest"
        );
        if (filePath == null) {
            logger.error("Class file not found for AttributesTest");
            System.err.println("Class file not found for AttributesTest");
            return;
        }
        String m1 ="@Test\n" +
                "public void testByTag() {\n" +
                "    Elements Elements1 = Jsoup.parse(String).select(String);\n" +
                "    assertEquals(int, Elements1.size());\n" +
                "    assertEquals(String, Elements1.get(int).id());\n" +
                "    assertEquals(String, Elements1.get(int).id());\n" +
                "    assertEquals(String, Elements1.get(int).id());\n" +
                "    Elements Elements2 = Jsoup.parse(String).select(String);\n" +
                "    assertEquals(int, Elements2.size());\n" +
                "}";
        String m2= "@Test\n" +
                "public void testGroupOrAttribute() {\n" +
                "    String String1 = String;\n" +
                "    Elements Elements1 = Jsoup.parse(String1).select(String);\n" +
                "    assertEquals(int, Elements1.size());\n" +
                "    assertEquals(String, Elements1.get(int).id());\n" +
                "    assertEquals(String, Elements1.get(int).id());\n" +
                "    assertEquals(String, Elements1.get(int).attr(String));\n" +
                "}";
        try {
            double similarity = computeSimilarity("","",m1, m2);
            logger.info("Structural Similarity: {}", String.format("%.3f", similarity));
            System.out.printf("Structural Similarity: %.3f%n", similarity);
        } catch (IllegalArgumentException e) {
            logger.error("Error: {}", e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
        }
    }
}

 */
