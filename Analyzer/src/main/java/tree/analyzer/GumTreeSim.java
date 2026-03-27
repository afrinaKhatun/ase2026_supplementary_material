package tree.analyzer;

import com.github.gumtreediff.actions.ChawatheScriptGenerator;
import com.github.gumtreediff.actions.EditScript;
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.CompositeMatchers;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.tree.Tree;
import com.github.gumtreediff.tree.TreeContext;

import java.nio.file.Files;
import java.nio.file.Path;

public class GumTreeSim{
    public static double similarityClassic(String a, String b) throws Exception {
        Path fa = Files.createTempFile("gtA", ".java");
        Path fb = Files.createTempFile("gtB", ".java");
        Files.writeString(fa, "class __Tmp__ { " + a + " }");
        Files.writeString(fb, "class __Tmp__ { " + b + " }");

        TreeContext ca = new JdtTreeGenerator().generateFrom().file(fa.toFile());
        TreeContext cb = new JdtTreeGenerator().generateFrom().file(fb.toFile());
        //Tree tA = getMethodBody(ca.getRoot());
        //Tree tB = getMethodBody(cb.getRoot());
        Tree tA = ca.getRoot();
        Tree tB = cb.getRoot();

        CompositeMatchers.ClassicGumtree matcher = new CompositeMatchers.ClassicGumtree(); // no-arg
        MappingStore mappings = matcher.match(tA, tB);// should resolve from gumtreediff.matchers
        EditScript script = new ChawatheScriptGenerator().computeActions(mappings);

        int denom = Math.max(tA.getMetrics().size, tB.getMetrics().size);
        double score = 1.0 - (script.size() / (double) Math.max(1, denom));
        return score;
    }
    private static Tree getMethodBody(Tree fileRoot) {
        for (Tree t : fileRoot.preOrder()) {
            if (t.getType().name.equals("MethodDeclaration")) {
                for (Tree c : t.preOrder())
                    if (c.getType().name.equals("Block")) return c;
                return t; // fallback if no block
            }
        }
        return fileRoot;
    }

    public static double similarityBodyOnly(String a, String b) throws Exception {
        Path fa = Files.createTempFile("gtA", ".java");
        Path fb = Files.createTempFile("gtB", ".java");
        Files.writeString(fa, "class __Tmp__ { " + a + " }");
        Files.writeString(fb, "class __Tmp__ { " + b + " }");

        Tree rootA = new JdtTreeGenerator().generateFrom().file(fa.toFile()).getRoot();
        Tree rootB = new JdtTreeGenerator().generateFrom().file(fb.toFile()).getRoot();
        Tree bodyA = getMethodBody(rootA), bodyB = getMethodBody(rootB);

        MappingStore ms = new MappingStore(bodyA, bodyB);
        new com.github.gumtreediff.matchers.heuristic.gt.GreedySubtreeMatcher().match(bodyA,bodyB,ms);
        EditScript script = new ChawatheScriptGenerator().computeActions(ms);

        int nA = bodyA.getMetrics().size, nB = bodyB.getMetrics().size;
        return 1.0 - (2.0 * script.size()) / Math.max(1, nA + nB); // stricter than / max(nA,nB)
    }
    public static void main(String[] args) throws Exception {
        String m1 = """
                @Test
                public void testGet() {
                    Document doc = JSoup.parse("<div><p>Hello<p id=1>there<p>this<p>is<p>an<p id=last>element</div>");
                    Element p = doc.getElementById("1");
                    assertEquals("there", p.text());
                    assertEquals("Hello", p.previousElementSibling().text());
                    assertEquals("this", p.nextElementSibling().text());
                    assertEquals("Hello", p.firstElementSibling().text());
                    assertEquals("element", p.lastElementSibling().text());
                }
                """;

        String m2 = """
                  @Test
                      public void testGet() {
                          Document doc = JSoup.parse("<div><p>Hello<p id=1>there<p>this<p>is<p>an<p id=last>element</div>");
                          Element p = doc.getElementById("1");
                          assertEquals("there", p.text());
                          assertEquals("Hello", p.previousElementSibling().text());
                          assertEquals("this", p.nextElementSibling().text());
                          assertEquals("Hello", p.firstElementSibling().text());
                          assertEquals("element", p.lastElementSibling().text());
                      }
            """;
        System.out.println(similarityClassic(m1,m2));
        System.out.println(similarityBodyOnly(m1,m2));
    }
}

