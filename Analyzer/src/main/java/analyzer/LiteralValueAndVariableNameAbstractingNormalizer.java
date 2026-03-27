package analyzer;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.body.VariableDeclarator;

import java.util.*;
public class LiteralValueAndVariableNameAbstractingNormalizer {
    public static class NormalizationResult {
        public String originalSource;
        public String normalizedSource;

        public NormalizationResult(String originalSource, String normalizedSource) {
            this.originalSource = originalSource;
            this.normalizedSource = normalizedSource;
        }
    }

    public String normalize(MethodDeclaration method) {
        String original_method_body = method.toString();


        NormalizationVisitor normalizationVisitor = new NormalizationVisitor();
        method.accept(normalizationVisitor, null);


        String original_method_body_without_assertion = AssertionStatementRemover.withoutAssertionsAsString(method);



        return original_method_body_without_assertion;


       /* String original_method_body = method.toString();

        // Remove @Test annotation
        method.getAnnotations().removeIf(anno -> anno.getNameAsString().equals("Test"));

        // Stage 1: normalize names
        NormalizationVisitor normalizationVisitor = new NormalizationVisitor();
        method.accept(normalizationVisitor, null);

        return new NormalizationResult(original_method_body, method.toString());
        */

    }

    private class NormalizationVisitor extends ModifierVisitor<Void> {
        private final Map<String, Integer> typeCounters = new HashMap<>();
        private final Map<String, String> variableNameMap = new HashMap<>();
        private final Map<String, String> variableTypes = new HashMap<>();

        @Override
        public Visitable visit(VariableDeclarator var, Void arg) {
            String type = var.getType().asString();
            String originalName = var.getNameAsString();

            int count = typeCounters.getOrDefault(type, 0) + 1;
            typeCounters.put(type, count);

            Character[] specialChars = new Character[]{'<','>','[',']','?','@','.','|'};
            String updatedType ="";
            for(int i=0;i<type.length();i++){
                if(Arrays.asList(specialChars).contains(type.charAt(i))){
                    updatedType = updatedType + "_";
                }
                else{
                    updatedType = updatedType + type.charAt(i);
                }
            }

            String canonicalName = updatedType + count;
            variableNameMap.put(originalName, canonicalName);
            variableTypes.put(canonicalName, type); // Store type against canonocal / normalized name
            var.setName(canonicalName);
            return super.visit(var, arg);
        }

        @Override
        public Visitable visit(NameExpr nameExpr, Void arg) {
            String name = nameExpr.getNameAsString();
            if (variableNameMap.containsKey(name)) {
                nameExpr.setName(variableNameMap.get(name));
            }
            return super.visit(nameExpr, arg);
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



