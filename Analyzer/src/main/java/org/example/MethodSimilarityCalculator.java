package org.example;

import com.github.gumtreediff.actions.SimplifiedChawatheScriptGenerator;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.heuristic.gt.GreedySubtreeMatcher;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import com.github.gumtreediff.actions.EditScript;

import com.github.gumtreediff.gen.javaparser.JavaParserGenerator;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.Tree;
import com.github.gumtreediff.tree.TreeContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.*;

public class MethodSimilarityCalculator {
    /** Returns similarity in [0,1] ignoring assertions and literal values. */
    /** Returns AST structural similarity in [0,1]; literals & assertions ignored. */
    public static double similarity(String methodSrcA, String methodSrcB) {
        String a = cleanAndNormalize(methodSrcA);
        String b = cleanAndNormalize(methodSrcB);

        System.out.println("Normalized A:\n" + a);
        System.out.println("Normalized B:\n" + b);

        File fA = null, fB = null;
        try {
            fA = writeTempJava("GT_A", a);
            fB = writeTempJava("GT_B", b);

            JavaParserGenerator gen = new JavaParserGenerator();

            // GumTree 4.x beta2 fluent API (no args to generateFrom)
            Tree tA = gen.generateFrom().file(fA.getAbsolutePath()).getRoot();
            Tree tB = gen.generateFrom().file(fB.getAbsolutePath()).getRoot();

            System.out.println("AST A: " + tA.toTreeString());
            System.out.println("AST B: " + tB.toTreeString());

            // ------- MATCHER API CHANGE IN 4.x --------
            // OLD (2.x): Matchers.getInstance().getMatcher(tA, tB)
            // NEW (4.x): get a Matcher, then call match(tA, tB)
            // CHANGED: create matcher directly (no registry, no client dep)
            GreedySubtreeMatcher matcher = new GreedySubtreeMatcher();                  // CHANGED
            MappingStore mappings = matcher.match(tA, tB);

            EditScript script = new SimplifiedChawatheScriptGenerator().computeActions(mappings);
            int edits = script.size();



            int sizeA = countNodes(tA);
            int sizeB = countNodes(tB);

            System.out.println("AST A size: " + sizeA);
            System.out.println("AST B size: " + sizeB);
            System.out.println("Edits: " + edits);

            int norm = Math.max(sizeA, sizeB);
            if (norm == 0) return 1.0;

            double sim = 1.0 - ((double) edits / (double) norm);
            return Math.max(0.0, Math.min(1.0, sim));
        } catch (Exception e) {
            throw new RuntimeException("AST similarity failed", e);
        } finally {
            if (fA != null) fA.delete();
            if (fB != null) fB.delete();
        }
    }

    // ---------- Cleaning & normalization (structure-first) ----------

    private static String cleanAndNormalize(String methodSrc) {
        String wrapped = "class X { " + methodSrc + " }";
        ParseResult<CompilationUnit> pr = new JavaParser().parse(wrapped);
        if (!pr.isSuccessful() || pr.getResult().isEmpty()) {
            throw new IllegalArgumentException("Parse failed: " + pr.getProblems());
        }
        CompilationUnit cu = pr.getResult().get();
        MethodDeclaration m = cu.findFirst(MethodDeclaration.class)
                .orElseThrow(() -> new IllegalArgumentException("No method found"));

        stripAssertions(m);
        normalizeLiterals(m);

        // Optional: collapse call names to arity-only to be even more structure-focused
        // m.findAll(MethodCallExpr.class).forEach(mc -> mc.setName("m" + mc.getArguments().size()));

        return cu.toString();
    }

    private static final Set<String> ASSERTS = new HashSet<>(Arrays.asList(
            "assertTrue","assertFalse","assertEquals","assertNotEquals","assertNull","assertNotNull",
            "assertSame","assertNotSame","assertArrayEquals","assertThat","fail",
            "assumeTrue","assumeFalse","assumeThat","assertThrows","expectThrows"
    ));

    private static void stripAssertions(MethodDeclaration m) {
        List<Statement> remove = new ArrayList<>();
        for (ExpressionStmt es : m.findAll(ExpressionStmt.class)) {
            es.getExpression().toMethodCallExpr().ifPresent(mc -> {
                String name = mc.getNameAsString();
                boolean scopedAssert = mc.getScope()
                        .map(s -> {
                            String t = s.toString();
                            return t.endsWith(".Assert") || t.endsWith(".Assertions") || t.endsWith(".Truth")
                                    || t.equals("Assert") || t.equals("Assertions") || t.equals("Truth");
                        }).orElse(false);
                if (ASSERTS.contains(name) || name.startsWith("assert") || name.startsWith("assume") || scopedAssert) {
                    remove.add(es);
                }
            });
        }
        remove.forEach(Statement::remove);
    }

    private static void normalizeLiterals(MethodDeclaration m) {
        m.accept(new VoidVisitorAdapter<Void>() {
            @Override public void visit(StringLiteralExpr n, Void arg) { n.setString("S"); }
            @Override public void visit(IntegerLiteralExpr n, Void arg) { n.setInt(0); }
            @Override public void visit(LongLiteralExpr n, Void arg) { n.setLong(0L); }
            @Override public void visit(DoubleLiteralExpr n, Void arg) { n.setDouble(0.0); }
            @Override public void visit(CharLiteralExpr n, Void arg) { n.setChar('c'); }
            @Override public void visit(BooleanLiteralExpr n, Void arg) { n.setValue(false); }
            // keep nulls as null
        }, null);
    }

    // ---------- IO & utils ----------

    private static File writeTempJava(String className, String classText) throws Exception {
        String fixed = classText.replaceFirst("class\\s+X\\s*\\{", "public class " + className + " {");
        File f = Files.createTempFile(className, ".java").toFile();
        try (FileWriter fw = new FileWriter(f)) { fw.write(fixed); }
        return f;
    }

    private static int countNodes(Tree t) {
        int c = 1;
        for (Tree child : t.getChildren()) c += countNodes(child);
        return c;
    }

    // quick demo
    public static void main(String[] args) {
        String a = """
            @Test
            public void parsesListItems() {
                String html = "<ul><li>A</li></ul>";
                Document doc = Jsoup.parse(html);
                Elements items = doc.select("li");
                Map<String,Integer> counts = new HashMap<>();
           
                org.junit.Assert.assertTrue(counts.size() > 0);
            }
            """;
        String b = """
            @Test
            public void extractsParagraphs() {
                String input = "<div><p>X</p><p>Y</p></div>";
                Document page = Jsoup.parse(input);
                Elements blocks = page.go("p");
                Map<String,Integer> freq = new HashMap<>();
            
                org.junit.Assert.assertTrue(freq.size() > 0);
            }
            """;
        System.out.println("Similarity = " + similarity(a, b));
    }
}
