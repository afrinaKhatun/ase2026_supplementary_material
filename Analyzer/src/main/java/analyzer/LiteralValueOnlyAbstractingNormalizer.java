package analyzer;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import org.example.NormalizationResult;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class LiteralValueOnlyAbstractingNormalizer {

    public org.example.NormalizationResult normalize(MethodDeclaration method) {
        String original_method_body = method.toString();

        LiteralValueOnlyAbstractingNormalizer.NormalizationVisitor normalizationVisitor = new LiteralValueOnlyAbstractingNormalizer.NormalizationVisitor();
        method.accept(normalizationVisitor, null);

        NormalizationResult normalized_method_body = new NormalizationResult(original_method_body,
                null, null, null,
                null,method.toString());

        return normalized_method_body;

    }

    private class NormalizationVisitor extends ModifierVisitor<Void> {

        @Override
        public Visitable visit(VariableDeclarator var, Void arg) {
            return super.visit(var, arg); // do nothing, preserve original var name
        }

        @Override
        public Visitable visit(NameExpr nameExpr, Void arg) {
            return super.visit(nameExpr, arg); // do nothing
        }

        @Override
        public Visitable visit(StringLiteralExpr expr, Void arg) {
            return new StringLiteralExpr("S");
        }

        @Override
        public Visitable visit(BooleanLiteralExpr expr, Void arg) {
            return new BooleanLiteralExpr(false);
        }

        @Override
        public Visitable visit(CharLiteralExpr expr, Void arg) {
            return new CharLiteralExpr('c');
        }

        @Override
        public Visitable visit(DoubleLiteralExpr expr, Void arg) {
            return new DoubleLiteralExpr("0.0");
        }

        @Override
        public Visitable visit(IntegerLiteralExpr expr, Void arg) {
            return new IntegerLiteralExpr("0");
        }

        @Override
        public Visitable visit(LongLiteralExpr expr, Void arg) {
            return new LongLiteralExpr("0L");
        }
        @Override
        public Visitable visit(TextBlockLiteralExpr expr, Void arg) {
            return new TextBlockLiteralExpr("\"\"\"\nS\n\"\"\"");
        }


    }


    public static void main(String[] args) {
        String testMethod = """
            @Test
            public void testIteratorRemovable() {
                Attributes a = new Attributes();
                a.put("Helldffeo", "There");
                a.put("data-name", "Jsoup");
                assertTrue(a.hasKey("Tot"));

                Iterator<Attribute> iterator = a.iterator();
                Attribute attr = iterator.next();
                assertEquals("Tot", attr.getKey());
                iterator.remove();
                assertEquals(2, a.size());
                attr = iterator.next();
                assertEquals("Hello", attr.getKey());
                assertEquals("There", attr.getValue());

                assertEquals(2, a.size());
                assertEquals("There", a.get("Hello"));
                assertFalse(a.hasKey("Tot"));
            }
        """;



    }
}




