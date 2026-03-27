package analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.ast.Node;
import org.checkerframework.checker.units.qual.A;
import org.example.ArgumentStructure;
import org.example.MethodCallStructure;
import org.example.NormalizationResult;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.example.testParser.findFileByClassName;
import static org.example.testParser.parsePerTestCase;


public class LiteralPreservingNormalizer {

    private static final String TEMP_OUTPUT_DIR = "/Users/afrinakhatun/IdeaProjects/jsoup/temp_normalized";

    public NormalizationResult normalize(MethodDeclaration method) {
        String original_method_body = method.toString();


        NormalizationVisitor normalizationVisitor = new NormalizationVisitor();
        method.accept(normalizationVisitor, null);

        StatementBasedCallExtractor extractor = new StatementBasedCallExtractor(normalizationVisitor.variableNameMap,
                normalizationVisitor.variableTypes);
        extractor.analyze(method);

        String original_method_body_without_assertion = AssertionStatementRemover.withoutAssertionsAsString(method);

        NormalizationResult literal_normalized_method_body = new NormalizationResult(original_method_body,
                method.toString(), original_method_body_without_assertion, extractor.getRegularCalls(),
                extractor.getAssertionCalls(),null);


        // Stream normalized data to a temporary file to reduce memory usage
        String methodName = method.getNameAsString();

        return literal_normalized_method_body;
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

    }

    private class StatementBasedCallExtractor {
        private final Map<String, String> variableNameMap;
        private final Map<String, String> variableTypes; // New: Store variable types
        private final List<MethodCallSignature> regularCalls = new ArrayList<>();
        private final List<AssertionCall> assertionCalls = new ArrayList<>();

        public StatementBasedCallExtractor(Map<String, String> variableNameMap,
                                           Map<String, String> variableTypes) {
            this.variableNameMap = variableNameMap;
            this.variableTypes = variableTypes;
        }

        public void analyze(MethodDeclaration method) {
            if (!method.getBody().isPresent()) return;

            BlockStmt body = method.getBody().get();
            for (Statement stmt : body.getStatements()) {
                // Collect all method calls inside this statement
                List<MethodCallExpr> methodCalls = new ArrayList<>();
                List<MethodCallExpr> assertion_methodCalls = new ArrayList<>();
                boolean isTopLevelAssert = false;
                String assertionMethodName = null;

                // ✅ Include top-level call if the statement itself is a method call
                if (stmt.isExpressionStmt()) {
                    Expression expr = stmt.asExpressionStmt().getExpression();
                    if (expr instanceof MethodCallExpr rootCall) {
                        String rootMethodName = rootCall.getNameAsString();
                        if (isAssertionMethod(rootMethodName)) {
                            isTopLevelAssert = true;
                            assertionMethodName = rootMethodName;

                            //handling inner args of assertion method call
                            AssertionCall astCall = new AssertionCall();
                            astCall.assertType = assertionMethodName;
                            for (Expression arg_of_assert : rootCall.getArguments()) {
                                if (arg_of_assert instanceof LiteralExpr) {
                                    AssertArgument argument = new AssertArgument();

                                    //specific literal type
                                    if (arg_of_assert instanceof StringLiteralExpr) {
                                        argument.argumentType = "LIT_STR";
                                    } else if (arg_of_assert instanceof IntegerLiteralExpr) {
                                        argument.argumentType = "LIT-INT";
                                    } else if (arg_of_assert instanceof BooleanLiteralExpr) {
                                        argument.argumentType = "LIT_BOOL";
                                    } else if (arg_of_assert instanceof CharLiteralExpr) {
                                        argument.argumentType = "LIT_CHAR";
                                    } else if (arg_of_assert instanceof DoubleLiteralExpr) {
                                        argument.argumentType = "LIT_DOUBLE";
                                    } else if (arg_of_assert instanceof LongLiteralExpr) {
                                        argument.argumentType = "LIT_LONG";
                                    } else if (arg_of_assert instanceof NullLiteralExpr) {
                                        argument.argumentType = "LIT_NULL";
                                    }
                                    else {
                                        argument.argumentType = "LIT_OTHER";
                                    }


                                    //argument.argumentType = "LIT";
                                    argument.literal_attribute_methodName = arg_of_assert.toString();
                                    astCall.arguments.add(argument);
                                } else if (arg_of_assert instanceof MethodCallExpr mce) {
                                    mce.accept(new VoidVisitorAdapter<Void>() {
                                        @Override
                                        public void visit(MethodCallExpr call, Void arg) {
                                            super.visit(call, arg);
                                            assertion_methodCalls.add(call);
                                        }
                                    }, null);

                                    //remove duplicates
                                    // Sort by toString().length() descending
                                    Collections.sort(assertion_methodCalls, new Comparator<MethodCallExpr>() {
                                        public int compare(MethodCallExpr m1, MethodCallExpr m2) {
                                            return Integer.compare(m2.toString().length(), m1.toString().length());
                                        }
                                    });

                                    List<MethodCallExpr> assertion_filteredMethodCalls = new ArrayList<>();
                                    for (int i = 0; i < assertion_methodCalls.size(); i++) {
                                        MethodCallExpr candidate = assertion_methodCalls.get(i);
                                        String candidateStr = candidate.toString();
                                        boolean isPrefixOfAny = false;

                                        for (int j = 0; j < assertion_filteredMethodCalls.size(); j++) {
                                            if (assertion_filteredMethodCalls.get(j).toString().startsWith(candidateStr) || assertion_filteredMethodCalls.get(j).toString().contains(candidateStr)) {
                                                isPrefixOfAny = true;
                                                break;
                                            }
                                        }
                                        if (!isPrefixOfAny) {
                                            assertion_filteredMethodCalls.add(candidate);
                                        }
                                    }
                                    // Process each method call
                                    AssertArgument argument = new AssertArgument();
                                    argument.argumentType = "MTH";
                                    for (MethodCallExpr call : assertion_filteredMethodCalls) {
                                        String methodName = call.getNameAsString();
                                        MethodCallSignature sig = extractMethodCallSignature(call);
                                        argument.receiverName = sig.receiverName;
                                        argument.receiverType = variableTypes.getOrDefault(sig.receiverName, "unknown"); //New line
                                        argument.literal_attribute_methodName = methodName;
                                        argument.meth_chain.calls = sig.calls;
                                        argument.meth_chain.receiverName = argument.receiverName;
                                        argument.meth_chain.receiverType = argument.receiverType;
                                        astCall.arguments.add(argument);
                                    }
                                    // Try to resolve the return type
                                    if(!assertion_filteredMethodCalls.isEmpty()){
                                        MethodCallExpr firstCall = assertion_filteredMethodCalls.getFirst();
                                        try{
                                            ResolvedMethodDeclaration resolvedMethod = firstCall.resolve();
                                            argument.returnType = resolvedMethod.getReturnType().describe();
                                            int y=0;
                                        }
                                        catch(Exception e){
                                            argument.returnType = "unknown";
                                        }
                                    }
                                    //  try to resolve getter method
                                    argument.isGetterMethod = isGetterMethod(argument);
                                } else if (arg_of_assert instanceof FieldAccessExpr fld) {
                                    AssertArgument argument = new AssertArgument();
                                    argument.argumentType = "FLD";
                                    argument.receiverName = fld.getScope().toString();
                                    argument.receiverType = variableTypes.getOrDefault(fld.getScope().toString(), "unknown"); //New line
                                    argument.literal_attribute_methodName = fld.getNameAsString();
                                    astCall.arguments.add(argument);
                                } else if (arg_of_assert instanceof NameExpr nam) {
                                    AssertArgument argument = new AssertArgument();
                                    argument.argumentType = "NAM";
                                    argument.literal_attribute_methodName = nam.getNameAsString();
                                    argument.receiverType = variableTypes.getOrDefault(nam.getNameAsString(), "unknown"); //New line
                                    astCall.arguments.add(argument);
                                } else {
                                    AssertArgument argument = new AssertArgument();
                                    argument.argumentType = "EXP";
                                    argument.literal_attribute_methodName = arg_of_assert.toString();
                                    astCall.arguments.add(argument);
                                }
                            }
                            assertionCalls.add(astCall);
                            //******** NEW consider method calls inside assertion aswell
                            if(astCall.arguments.size()==1){
                                if(astCall.arguments.get(0).argumentType=="MTH"){
                                    MethodCallSignature tempM = astCall.arguments.get(0).meth_chain;
                                    tempM.isFromAssertion = true;
                                    regularCalls.add(tempM);
                                }
                            }
                            else if(astCall.arguments.size()>1){
                                if(astCall.arguments.get(1).argumentType=="MTH"){
                                    MethodCallSignature tempM = astCall.arguments.get(1).meth_chain;
                                    tempM.isFromAssertion = true;
                                    regularCalls.add(tempM);
                                }
                            }
                        } else {
                            // ✅ Now collect all nested calls inside the statement
                            stmt.accept(new VoidVisitorAdapter<Void>() {
                                @Override
                                public void visit(MethodCallExpr call, Void arg) {
                                    super.visit(call, arg);
                                    if (!isAssertionMethod(call.getNameAsString()) && isGenuineMethod(call)) {
                                        methodCalls.add(call);
                                    }
                                }
                            }, null);
                            //remove duplicates
                            // Sort by toString().length() descending
                            Collections.sort(methodCalls, new Comparator<MethodCallExpr>() {
                                public int compare(MethodCallExpr m1, MethodCallExpr m2) {
                                    return Integer.compare(m2.toString().length(), m1.toString().length());
                                }
                            });

                            List<MethodCallExpr> filteredMethodCalls = new ArrayList<>();
                            for (int i = 0; i < methodCalls.size(); i++) {
                                MethodCallExpr candidate = methodCalls.get(i);
                                String candidateStr = candidate.toString();
                                boolean isPrefixOfAny = false;

                                for (int j = 0; j < filteredMethodCalls.size(); j++) {
                                    if (filteredMethodCalls.get(j).toString().startsWith(candidateStr)) {
                                        isPrefixOfAny = true;
                                        break;
                                    }
                                }
                                if (!isPrefixOfAny) {
                                    filteredMethodCalls.add(candidate);
                                }
                            }
                            // Process each method call
                            for (MethodCallExpr call : filteredMethodCalls) {
                                String methodName = call.getNameAsString();
                                MethodCallSignature sig = extractMethodCallSignature(call);
                                regularCalls.add(sig);
                            }
                            //upto this
                        }
                    }
                    else{
                        // ✅ any non-assert and direct non-method call
                        stmt.accept(new VoidVisitorAdapter<Void>() {
                            @Override
                            public void visit(MethodCallExpr call, Void arg) {
                                super.visit(call, arg);
                                if (!isAssertionMethod(call.getNameAsString()) && isGenuineMethod(call)) {
                                    methodCalls.add(call);
                                }
                            }
                        }, null);
                        //remove duplicates
                        // Sort by toString().length() descending
                        Collections.sort(methodCalls, new Comparator<MethodCallExpr>() {
                            public int compare(MethodCallExpr m1, MethodCallExpr m2) {
                                return Integer.compare(m2.toString().length(), m1.toString().length());
                            }
                        });

                        List<MethodCallExpr> filteredMethodCalls = new ArrayList<>();
                        for (int i = 0; i < methodCalls.size(); i++) {
                            MethodCallExpr candidate = methodCalls.get(i);
                            String candidateStr = candidate.toString();
                            boolean isPrefixOfAny = false;

                            for (int j = 0; j < filteredMethodCalls.size(); j++) {
                                if (filteredMethodCalls.get(j).toString().startsWith(candidateStr)) {
                                    isPrefixOfAny = true;
                                    break;
                                }
                            }
                            if (!isPrefixOfAny) {
                                filteredMethodCalls.add(candidate);
                            }
                        }
                        // Process each method call
                        for (MethodCallExpr call : filteredMethodCalls) {
                            String methodName = call.getNameAsString();
                            MethodCallSignature sig = extractMethodCallSignature(call);
                            regularCalls.add(sig);
                        }
                    }
                }

            }
        }

        private boolean isAssertionMethod(String name) {
            String lower = name.toLowerCase();
            return lower.startsWith("assert") || lower.startsWith("fail");
        }

        private static boolean isGenuineMethod(MethodCallExpr call) {
            // A method call is top-level if it’s not an argument of another method call
            if (!call.getParentNode().isPresent()) {
                return false; // No parent, not a top-level call
            }

            // Start with the immediate parent
            Node current;
            current = call.getParentNode().get();

            // Check each parent up the AST to see if it’s another method call
            while (current != null) {
                if (current instanceof MethodCallExpr && current != call) {
                    return false; // Found another method call, so this is an argument
                }
                // Move up to the next parent
                current = current.getParentNode().orElse(null);
            }

            return true; // No other method call found, so this is top-level
        }

        private String resolve(String name) {
            return variableNameMap.getOrDefault(name, name);
        }

        private MethodCallSignature  extractMethodCallSignature(Expression expr) {
            Deque<MethodCallExpr> chain = new ArrayDeque<>();
            String baseReceiver = null;

            while (expr instanceof MethodCallExpr call) {
                chain.addFirst(call);
                expr = call.getScope().orElse(null);
            }

            if (expr instanceof NameExpr nameExpr) {
                baseReceiver = resolve(nameExpr.getNameAsString());
            }

            List<MethodCall> calls = new ArrayList<>();
            for (MethodCallExpr call : chain) {
                String methodName = call.getNameAsString();

                String ret1 = call.calculateResolvedType().describe();
                String ret2 =call.resolve().getReturnType().describe();

                List<AssertArgument> args = new ArrayList<>();
                for (Expression arg : call.getArguments()) {
                    //check if the argument is of different type
                    AssertArgument argument = new AssertArgument();
                    if (arg instanceof LiteralExpr) {
                        //specific literal type
                        if (arg instanceof StringLiteralExpr) {
                            argument.argumentType = "LIT_STR";
                        } else if (arg instanceof IntegerLiteralExpr) {
                            argument.argumentType = "LIT-INT";
                        } else if (arg instanceof BooleanLiteralExpr) {
                            argument.argumentType = "LIT_BOOL";
                        } else if (arg instanceof CharLiteralExpr) {
                            argument.argumentType = "LIT_CHAR";
                        } else if (arg instanceof DoubleLiteralExpr) {
                            argument.argumentType = "LIT_DOUBLE";
                        } else if (arg instanceof LongLiteralExpr) {
                            argument.argumentType = "LIT_LONG";
                        } else if (arg instanceof NullLiteralExpr) {
                            argument.argumentType = "LIT_NULL";
                        } else {
                            argument.argumentType = "LIT_OTHER";
                        }
                        argument.literal_attribute_methodName = arg.toString();
                    }
                    else if(arg instanceof MethodCallExpr mce){
                        List<MethodCallExpr> normal_methodCalls = new ArrayList<>();
                        mce.accept(new VoidVisitorAdapter<Void>() {
                            @Override
                            public void visit(MethodCallExpr call, Void arg) {
                                super.visit(call, arg);
                                normal_methodCalls.add(call);
                            }
                        }, null);

                        //remove duplicates
                        // Sort by toString().length() descending
                        Collections.sort(normal_methodCalls, new Comparator<MethodCallExpr>() {
                            public int compare(MethodCallExpr m1, MethodCallExpr m2) {
                                return Integer.compare(m2.toString().length(), m1.toString().length());
                            }
                        });

                        List<MethodCallExpr> normal_filteredMethodCalls = new ArrayList<>();
                        for (int i = 0; i < normal_methodCalls.size(); i++) {
                            MethodCallExpr candidate = normal_methodCalls.get(i);
                            String candidateStr = candidate.toString();
                            boolean isPrefixOfAny = false;

                            for (int j = 0; j < normal_filteredMethodCalls.size(); j++) {
                                if (normal_filteredMethodCalls.get(j).toString().startsWith(candidateStr)) {
                                    isPrefixOfAny = true;
                                    break;
                                }
                            }
                            if (!isPrefixOfAny) {
                                normal_filteredMethodCalls.add(candidate);
                            }
                        }
                        // Process each method call
                        argument.argumentType = "MTH";
                        for (MethodCallExpr ncall : normal_filteredMethodCalls) {
                            MethodCallSignature sig = extractMethodCallSignature(ncall);
                            argument.receiverName = sig.receiverName;
                            argument.receiverType = variableTypes.getOrDefault(sig.receiverName,"unknown");// New line
                            //argument.literal_attribute_methodName = ncall.getNameAsString();
                            argument.meth_chain.calls = sig.calls;
                        }
                    }
                    else if (arg instanceof FieldAccessExpr fld) {
                        argument.argumentType = "FLD";
                        argument.receiverName = fld.getScope().toString();
                        argument.receiverType = variableTypes.getOrDefault(fld.getScope().toString(),"unknown");// New line
                        argument.literal_attribute_methodName = fld.getNameAsString();
                    }
                    else if (arg instanceof NameExpr nam) {
                        argument.argumentType = "NAM";
                        argument.literal_attribute_methodName = nam.getNameAsString();
                        argument.receiverType = variableTypes.getOrDefault(nam.getNameAsString(),"unknown"); //New line
                    } else {
                        argument.argumentType = "EXP";
                        argument.literal_attribute_methodName = arg.toString();
                    }
                    args.add(argument);
                }
                calls.add(new MethodCall(methodName, args, ret1));
            }
            if (baseReceiver != null && !calls.isEmpty()) {
                return new MethodCallSignature(baseReceiver, variableTypes.getOrDefault(baseReceiver,baseReceiver), calls);
            } else if(baseReceiver == null && !calls.isEmpty()){
                return new MethodCallSignature(calls);
            }
            else {
                return new MethodCallSignature();
            }
        }
        public List<MethodCallSignature> getRegularCalls() {
            return regularCalls;
        }

        public List<AssertionCall> getAssertionCalls() {
            return assertionCalls;
        }
    }

    public boolean isGetterMethod(AssertArgument arg){
        MethodCall m = arg.meth_chain.calls.getLast();
        boolean isGetterName = m.methodName.matches("get[A-Z][a-zA-Z0-9]*") ||
                m.methodName.matches("is[A-Z][a-zA-Z0-9]*");
        boolean hasParameters = false;
        if(!m.arguments.isEmpty()){
            hasParameters = true;
        }
        if(!arg.returnType.equalsIgnoreCase("void") && isGetterName && !hasParameters){
            return true;
        }
        else{
            return false;
        }
    }


    public static void main(String[] args) throws IOException {

        Map<String, NormalizationData> normalized_method_bodies = new HashMap<>();
        TypeSolver typeSolver = new CombinedTypeSolver(
                new ReflectionTypeSolver(), // 🔹 For java.lang.*, java.util.*, etc.
                new JavaParserTypeSolver(new File("/Users/afrinakhatun/IdeaProjects/jsoup/src/main/java")), // production
                new JavaParserTypeSolver(new File("/Users/afrinakhatun/IdeaProjects/jsoup/src/test/java"))  // test
        );
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        ParserConfiguration config = new ParserConfiguration().setSymbolResolver(symbolSolver);
        JavaParser parser = new JavaParser(config);

        //custom types solver
        JavaParserTypeSolver mainSolver = new JavaParserTypeSolver(new File("/Users/afrinakhatun/IdeaProjects/jsoup/src/main/java"));
        JavaParserTypeSolver testSolver = new JavaParserTypeSolver(new File("/Users/afrinakhatun/IdeaProjects/jsoup/src/test/java"));
        List<JavaParserTypeSolver> customSolvers = List.of(mainSolver, testSolver);

        Path filePath = findFileByClassName(Paths.get("/Users/afrinakhatun/IdeaProjects/jsoup/src/test/java"), "AttributesTest");

        CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow();
        //System.out.println(filePath);

        List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
        for (int i = 0; i < methods.size(); i++) {
            MethodDeclaration method = methods.get(i);
            if (!method.isAnnotationPresent("Test")) continue;
            if (method.getNameAsString().equals("testIteratorRemovable")) {
                //NormalizationData normalizationData = new NormalizationData();

                LiteralPreservingNormalizer Lnormalizer = new LiteralPreservingNormalizer();
                //NormalizationResult result = Lnormalizer.normalize(method);

                //TypeAbstractingNormalizer Tnormalizer = new TypeAbstractingNormalizer();
                //normalizationData.type_normalized_method_body = Tnormalizer.normalize(method);

                //normalized_method_bodies.put(method.getNameAsString(), normalizationData);
            }
        }
        System.out.println("Do work");

    }
}
