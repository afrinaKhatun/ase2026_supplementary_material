package org.example;

import analyzer.LiteralPreservingNormalizer;
import analyzer.LiteralValueAndVariableNameAbstractingNormalizer;
import analyzer.LiteralValueOnlyAbstractingNormalizer;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class testParser {
    private static final String TEMP_OUTPUT_DIR = "/Users/afrinakhatun/IdeaProjects/jsoup/temp_normalized";
    private static final int BATCH_SIZE = 5;


    public static void main(String[] args) throws IOException {
        parsePerTestCase("/Users/afrinakhatun/IdeaProjects/jsoup/src/test/java", "org.jsoup.nodes.ElementTest");
    }

    // NEW (FIX 4): Use 'elements' field for JavaParser 3.25.9
    private static void clearTypeSolverCache(TypeSolver typeSolver) {
        try {
            if (typeSolver instanceof JavaParserTypeSolver) {
                Field cacheField = JavaParserTypeSolver.class.getDeclaredField("parsedFiles");
                cacheField.setAccessible(true);
                Map<?, ?> cache = (Map<?, ?>) cacheField.get(typeSolver);
                cache.clear();
                cacheField.setAccessible(false);
            } else if (typeSolver instanceof CombinedTypeSolver) {
                // NEW (FIX 4): Changed to 'elements' based on diagnostic output
                Field solversField = CombinedTypeSolver.class.getDeclaredField("elements");
                solversField.setAccessible(true);
                List<TypeSolver> solvers = (List<TypeSolver>) solversField.get(typeSolver);
                solversField.setAccessible(false);
                for (TypeSolver solver : solvers) {
                    if (solver instanceof JavaParserTypeSolver) {
                        Field cacheField = JavaParserTypeSolver.class.getDeclaredField("parsedFiles");
                        cacheField.setAccessible(true);
                        Map<?, ?> cache = (Map<?, ?>) cacheField.get(solver);
                        cache.clear();
                        cacheField.setAccessible(false);
                    }
                }
            }
        } catch (NoSuchFieldException e) {
            // NEW (FIX 3): Log all fields for debugging
            String availableFields = Arrays.stream(CombinedTypeSolver.class.getDeclaredFields())
                    .map(Field::getName)
                    .collect(Collectors.joining(", "));
            System.out.println("Failed to clear TypeSolver cache due to missing field: " + e.getMessage() +
                    "; Available fields in CombinedTypeSolver: " + availableFields);
        } catch (Exception e) {
            System.out.println("Failed to clear TypeSolver cache: " + e.getMessage());
        }
    }


    public static Map<String, GenericTestCaseBody> parsePerTestCase(String sourceRootPath, String className) throws IOException {
        //System.out.println("i am here");
        Map<String, GenericTestCaseBody> generic_method_bodies = new HashMap<>();
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

        Path filePath = findFileByClassName(Paths.get(sourceRootPath), className);
        if (filePath == null) {
            //System.err.println("Class file not found for: " + className);
            return null;
        }

        CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow();
        //System.out.println(filePath);

        List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
        for (int i = 0; i < methods.size(); i++) {
            MethodDeclaration method = methods.get(i);
            if (!method.isAnnotationPresent("Test")) continue;

            //System.out.println("\n=== Method: " + method.getNameAsString() + " ===");

            //create a new method structure
            GenericTestCaseBody test_case_body = new GenericTestCaseBody();


            Map<String, VariableStructure> variableMap = new HashMap<>();
            List<String> customMethodCalls = new ArrayList<>();
            AssertionStructure assertionNestedCalls = new AssertionStructure();

            // 1. Extract custom variables with their types
            List<VariableDeclarationExpr> varDecls = method.findAll(VariableDeclarationExpr.class);

            for (VariableDeclarationExpr vde : varDecls) {
                for (VariableDeclarator var : vde.getVariables()) {
                    VariableStructure vs = new VariableStructure();
                    vs.name = var.getNameAsString();

                    try {
                        vs.type = var.getType().resolve().describe(); // Fully qualified type
                    } catch (Exception e) {
                        vs.type = var.getType().toString();
                    }

                    // ✅ Only extract constructor details if explicitly using `new`
                    Optional<Expression> initOpt = var.getInitializer();
                    if (initOpt.isPresent() && initOpt.get() instanceof ObjectCreationExpr creationExpr) {
                        vs.constructor_signature = creationExpr.getTypeAsString();

                        for (Expression arg : creationExpr.getArguments()) {
                            ArgumentStructure argStruct = new ArgumentStructure();
                            try {
                                argStruct.type = arg.calculateResolvedType().describe();
                            } catch (Exception e) {
                                argStruct.type = "Unresolved";
                            }

                            // Classify and store argument value
                            if (arg.isFieldAccessExpr()) {
                                argStruct.value = "FIELD:" + arg.toString();  // e.g., "doc.x"
                            } else if (arg.isLiteralExpr()) {
                                argStruct.value = "LITERAL:" + arg.toString(); // e.g., "10", "\"abc\""
                            } else if (arg.isNameExpr()) {
                                argStruct.value = "VAR:" + arg.toString(); // e.g., someVar
                            } else if (arg.isMethodCallExpr()) {
                                argStruct.value = "CALL:" + arg.toString(); // e.g., getName()
                            } else {
                                argStruct.value = "EXPR:" + arg.toString(); // fallback
                            }

                            //argStruct.value = arg.toString();
                            vs.constructor_arguments.add(argStruct);
                        }
                    }

                    variableMap.put(vs.name, vs);
                }
            }
            test_case_body.objects = variableMap;
            List<MethodCallExpr> allCalls = method.findAll(MethodCallExpr.class);
            for (MethodCallExpr mce : allCalls) {
                boolean insideAssert = false;
                MethodCallExpr assertCall = null;
                if (mce.getNameAsString().startsWith("assert")) {
                    insideAssert = true;
                    assertCall = mce;
                } else {
                    Optional<Node> parent = mce.getParentNode();
                    while (parent.isPresent()) {
                        Node p = parent.get();
                        if (p instanceof MethodCallExpr parentCall &&
                                parentCall.getNameAsString().startsWith("assert")) {
                            insideAssert = true;
                            assertCall = parentCall;
                            break;
                        }
                        parent = p.getParentNode();
                    }
                }

                // Case 1: Inside an assert statement
                if (insideAssert && assertCall != null) {
                    AssertionNewStructure assertion = new AssertionNewStructure();
                    assertion.assertion_method_name = assertCall.getNameAsString();

                    Expression actual = assertCall.getArgument(assertCall.getArguments().size() - 1);
                    AssertedValue av = new AssertedValue();

                    if (actual instanceof MethodCallExpr methodArg) {
                        Optional<Expression> scope = methodArg.getScope();
                        if (scope.isPresent() && scope.get() instanceof NameExpr) {
                            NameExpr scopeName = (NameExpr) scope.get();
                            String varName = scopeName.getNameAsString();
                            if (variableMap.containsKey(varName)) {
                                av.type = variableMap.get(varName).type;
                                av.value = varName + "." + methodArg.getNameAsString() + "()";

                                for (Expression argExpr : methodArg.getArguments()) {
                                    ArgumentStructure argStruct = new ArgumentStructure();
                                    try {
                                        argStruct.type = argExpr.calculateResolvedType().describe();
                                    } catch (Exception e) {
                                        argStruct.type = "Unresolved";
                                    }
                                    argStruct.value = argExpr.toString();
                                    av.arguments.add(argStruct);
                                }
                            }

                        } else if (actual.isFieldAccessExpr()) {
                            FieldAccessExpr fieldArg = actual.asFieldAccessExpr();
                            Expression eScope = fieldArg.getScope();
                            if (eScope instanceof NameExpr nameExpr) {
                                String varName = nameExpr.getNameAsString();
                                if (variableMap.containsKey(varName)) {
                                    av.type = variableMap.get(varName).type;
                                    av.value = varName + "." + fieldArg.getNameAsString();
                                }
                            }
                        } else if (actual.isNameExpr()) {
                            NameExpr nameExpr = actual.asNameExpr();
                            String varName = nameExpr.getNameAsString();
                            if (variableMap.containsKey(varName)) {
                                av.type = variableMap.get(varName).type;
                                av.value = varName;
                            }
                        } else {
                            av.type = "UnknownOrLiteral";
                            av.value = actual.toString();
                        }

                        assertion.asserted_object_state = av;
                        test_case_body.assertions.add(assertion);
                        //assertionNestedCalls.assertions.add(assertion);


                    }
                }

                // Case 2: Not inside an assert
                else {
                    String objType = isCallOnCustomObject(mce, null);
                    if (objType != null) {
                        if (isCustomType(objType)) {

                            /*String resolved = getChainedMethodSignature(mce, objType);
                            if (resolved != null) {
                                customMethodCalls.add(resolved);

                            }
                            */
                            NameExpr scopeNam = (NameExpr) mce.getScope().get();
                            String varName = scopeNam.getNameAsString();
                            MethodCallStructure mcs = new MethodCallStructure();
                            mcs.method_name = mce.getNameAsString();

                            //mcs.targetVar = varName;
                            //mcs.targetType = variableMap.get(varName).type;

                            for (Expression arg : mce.getArguments()) {
                                ArgumentStructure argStruct = new ArgumentStructure();
                                try {
                                    argStruct.type = arg.calculateResolvedType().describe();
                                } catch (Exception e) {
                                    argStruct.type = "Unresolved";
                                }
                                // Classify and store argument value
                                if (arg.isFieldAccessExpr()) {
                                    argStruct.value = "FIELD:" + arg.toString();  // e.g., "doc.x"
                                } else if (arg.isLiteralExpr()) {
                                    argStruct.value = "LITERAL:" + arg.toString(); // e.g., "10", "\"abc\""
                                } else if (arg.isNameExpr()) {
                                    argStruct.value = "VAR:" + arg.toString(); // e.g., someVar
                                } else if (arg.isMethodCallExpr()) {
                                    argStruct.value = "CALL:" + arg.toString(); // e.g., getName()
                                } else {
                                    argStruct.value = "EXPR:" + arg.toString(); // fallback
                                }
                                //argStruct.value = arg.toString();
                                mcs.arguments.add(argStruct);
                            }

                            variableMap.get(varName).method_calls.add(mcs);
                        }
                    }
                    //check for static
                    else {
                        String baseStaticClass = checkIsStaticMethodCall(mce, (CombinedTypeSolver) typeSolver, customSolvers);
                        if (baseStaticClass != null) {
                            /*String resolved = getChainedMethodSignature(mce, baseStaticClass);
                            if (resolved != null) {
                                customMethodCalls.add(resolved);

                            }*/
                            MethodCallStructure mcs = new MethodCallStructure();
                            mcs.method_name = mce.getNameAsString();
                            for (Expression arg : mce.getArguments()) {
                                ArgumentStructure argStruct = new ArgumentStructure();
                                try {
                                    argStruct.type = arg.calculateResolvedType().describe();
                                } catch (Exception e) {
                                    argStruct.type = "Unresolved";
                                }
                                // Classify and store argument value
                                if (arg.isFieldAccessExpr()) {
                                    argStruct.value = "FIELD:" + arg.toString();  // e.g., "doc.x"
                                } else if (arg.isLiteralExpr()) {
                                    argStruct.value = "LITERAL:" + arg.toString(); // e.g., "10", "\"abc\""
                                } else if (arg.isNameExpr()) {
                                    argStruct.value = "VAR:" + arg.toString(); // e.g., someVar
                                } else if (arg.isMethodCallExpr()) {
                                    argStruct.value = "CALL:" + arg.toString(); // e.g., getName()
                                } else {
                                    argStruct.value = "EXPR:" + arg.toString(); // fallback
                                }
                                mcs.arguments.add(argStruct);
                            }

                            test_case_body.static_method_calls.put(baseStaticClass, mcs);

                        }
                    }
                    /*if(mce.getScope().isPresent()) {
                        Expression scope = mce.getScope().get();
                        if (scope instanceof NameExpr) {
                            NameExpr scopeNam = (NameExpr) mce.getScope().get();
                            String varName = scopeNam.getNameAsString();
                            if (variableMap.containsKey(varName)) {
                                MethodCallStructure mcs = new MethodCallStructure();
                                mcs.method_name = mce.getNameAsString();

                                //mcs.targetVar = varName;
                                //mcs.targetType = variableMap.get(varName).type;

                                for (Expression arg : mce.getArguments()) {
                                    ArgumentStructure argStruct = new ArgumentStructure();
                                    try {
                                        argStruct.type = arg.calculateResolvedType().describe();
                                    } catch (Exception e) {
                                        argStruct.type = "Unresolved";
                                    }
                                    // Classify and store argument value
                                    if (arg.isFieldAccessExpr()) {
                                        argStruct.value = "FIELD:" + arg.toString();  // e.g., "doc.x"
                                    } else if (arg.isLiteralExpr()) {
                                        argStruct.value = "LITERAL:" + arg.toString(); // e.g., "10", "\"abc\""
                                    } else if (arg.isNameExpr()) {
                                        argStruct.value = "VAR:" + arg.toString(); // e.g., someVar
                                    } else if (arg.isMethodCallExpr()) {
                                        argStruct.value = "CALL:" + arg.toString(); // e.g., getName()
                                    } else {
                                        argStruct.value = "EXPR:" + arg.toString(); // fallback
                                    }
                                    //argStruct.value = arg.toString();
                                    mcs.arguments.add(argStruct);
                                }

                                variableMap.get(varName).method_calls.add(mcs);
                            }
                        } else if (scope instanceof TypeExpr typeExpr){
                            String baseStaticClass = checkIsStaticMethodCall(mce, (CombinedTypeSolver) typeSolver, customSolvers);
                            if (baseStaticClass != null) {
                                MethodCallStructure mcs = new MethodCallStructure();
                                mcs.method_name = mce.getNameAsString();
                                for (Expression arg : mce.getArguments()) {
                                    ArgumentStructure argStruct = new ArgumentStructure();
                                    try {
                                        argStruct.type = arg.calculateResolvedType().describe();
                                    } catch (Exception e) {
                                        argStruct.type = "Unresolved";
                                    }
                                    // Classify and store argument value
                                    if (arg.isFieldAccessExpr()) {
                                        argStruct.value = "FIELD:" + arg.toString();  // e.g., "doc.x"
                                    } else if (arg.isLiteralExpr()) {
                                        argStruct.value = "LITERAL:" + arg.toString(); // e.g., "10", "\"abc\""
                                    } else if (arg.isNameExpr()) {
                                        argStruct.value = "VAR:" + arg.toString(); // e.g., someVar
                                    } else if (arg.isMethodCallExpr()) {
                                        argStruct.value = "CALL:" + arg.toString(); // e.g., getName()
                                    } else {
                                        argStruct.value = "EXPR:" + arg.toString(); // fallback
                                    }
                                    mcs.arguments.add(argStruct);
                                }

                                test_case_body.static_method_calls.put(baseStaticClass, mcs);
                            }
                        }
                    }*/
                }

            }
            generic_method_bodies.put(method.getNameAsString(),test_case_body);
        }
        for (Map.Entry<String, GenericTestCaseBody> entry : generic_method_bodies.entrySet()) {
            System.out.println("Method name: "+entry.getKey());
            for (Map.Entry<String, VariableStructure> v : entry.getValue().objects.entrySet()) {
                System.out.println("Name:"+v.getValue().name);
                System.out.println("Type:"+v.getValue().type);
                System.out.println("Cons:"+v.getValue().constructor_signature);
                for( ArgumentStructure as : v.getValue().constructor_arguments){
                    System.out.println(as.type + " "+ as.value);
                }
                System.out.println("Methods:");
                for(MethodCallStructure mc: v.getValue().method_calls){
                    System.out.println(mc.method_name);
                    for( ArgumentStructure as : v.getValue().constructor_arguments){
                        System.out.println(as.type + " "+ as.value);
                    }
                }
            }

        }
        return generic_method_bodies;
    }

    public static Map<String, NormalizationResult> getNormalizedMethodsOfClass(SingletonParser singletonParser, String sourceRootPath, String className) throws IOException {
        Map<String, NormalizationResult> normalized_method_bodies = new HashMap<>();
        /*TypeSolver typeSolver = new CombinedTypeSolver(
                new ReflectionTypeSolver(), // 🔹 For java.lang.*, java.util.*, etc.
                new JavaParserTypeSolver(new File("/Users/afrinakhatun/IdeaProjects/jsoup/src/main/java")), // production
                new JavaParserTypeSolver(new File("/Users/afrinakhatun/IdeaProjects/jsoup/src/test/java"))  // test
        );
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        ParserConfiguration config = new ParserConfiguration().setSymbolResolver(symbolSolver);
        JavaParser parser = new JavaParser(config);

        */
        // Create temp directory for streaming normalized data
        File tempDir = new File(TEMP_OUTPUT_DIR);
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        Path filePath = findFileByClassName(Paths.get(sourceRootPath), className);
        if (filePath == null) {
            //System.err.println("Class file not found for: " + className);
            return null;
        }

        CompilationUnit cu ;// parser.parse(filePath).getResult().orElseThrow();
        //System.out.println(filePath);
        // NEW: Add try-catch for parsing errors
        try {
            cu = singletonParser.parser.parse(filePath).getResult().orElseThrow();
        } catch (Exception e) {
            System.out.println("Failed to parse file: " + filePath + " - " + e.getMessage());
            return normalized_method_bodies;
        }

        List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);

        for (int i = 0; i < methods.size(); i++) {
            MethodDeclaration method = methods.get(i);
           // if (!method.isAnnotationPresent("Test")) continue;

            try {
                String methodName = method.getNameAsString();

                // YOU CAN USE ANY ONE OF THE Normalizers
                //LiteralPreservingNormalizer Lnormalizer = new LiteralPreservingNormalizer();
                //NormalizationResult literal_normalized_method_body = Lnormalizer.normalize(method);

                //LiteralValueAndVariableNameAbstractingNormalizer Tnormalizer = new LiteralValueAndVariableNameAbstractingNormalizer();
                //literal_normalized_method_body.assertionFull_NormalizedSource = Tnormalizer.normalize(method);

                LiteralValueOnlyAbstractingNormalizer litValueOnlyNormalizer = new LiteralValueOnlyAbstractingNormalizer();
                NormalizationResult literal_normalized_method_body = litValueOnlyNormalizer.normalize(method);

                /*
                // Stream normalized data to a temporary file to reduce memory usage
                String methodName = method.getNameAsString();
                 File tempFile = new File(TEMP_OUTPUT_DIR, className + "_" + methodName + ".ser");
                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(tempFile))) {
                    oos.writeObject(literal_normalized_method_body);
                    oos.flush();
                }

                // Clear the large object to make it eligible for GC
                literal_normalized_method_body = null;

                // Store only the file reference in the map
                String tempFilePath = tempFile.getAbsolutePath();
                */
                cu = null;
                normalized_method_bodies.put(methodName, literal_normalized_method_body);

            } catch (Exception e) {
                System.out.println("Failed to normalize method " + method.getNameAsString() + " in " + className + ": " + e.getMessage());
            }

        }

        return  normalized_method_bodies;
    }

    public static Map<String, StaticTestMethodBody> parsePerTestMethodForStaticAnalysis(String sourceRootPath, String className) throws IOException {
        //System.out.println("i am here");
        Map<String, StaticTestMethodBody> static_method_bodies = new HashMap<>();
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

        Path filePath = findFileByClassName(Paths.get(sourceRootPath), className);
        if (filePath == null) {
            //System.err.println("Class file not found for: " + className);
            return null;
        }

        CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow();
        //System.out.println(filePath);

        List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
        for (int i = 0; i < methods.size(); i++) {
            MethodDeclaration method = methods.get(i);
            if (!method.isAnnotationPresent("Test")) continue;

            //System.out.println("\n=== Method: " + method.getNameAsString() + " ===");

            Map<String, String> variableMap = new LinkedHashMap<>();
            List<String> customMethodCalls = new ArrayList<>();
            AssertionStructure assertionNestedCalls = new AssertionStructure();

            // 1. Extract custom variables with their types
            List<VariableDeclarationExpr> varDecls = method.findAll(VariableDeclarationExpr.class);
            for (VariableDeclarationExpr vde : varDecls) {
                for (VariableDeclarator var : vde.getVariables()) {
                    String varName = var.getNameAsString();
                    String varType;
                    try {
                        varType = var.getType().resolve().describe();
                    } catch (Exception e) {
                        //System.out.println("error for following:" + varName);
                        varType = var.getType().toString();
                    }
                    variableMap.put(varName, varType);
                }
            }

            //if (method.getNameAsString().equals("testGetSiblings")) {

            // 2. Identify all method calls
            List<MethodCallExpr> allCalls = method.findAll(MethodCallExpr.class);
            for (MethodCallExpr mce : allCalls) {
                boolean insideAssert = false;
                MethodCallExpr assertCall = null;
                if (mce.getNameAsString().startsWith("assert")) {
                    insideAssert = true;
                    assertCall = mce;
                } else {
                    Optional<Node> parent = mce.getParentNode();
                    while (parent.isPresent()) {
                        Node p = parent.get();
                        if (p instanceof MethodCallExpr parentCall &&
                                parentCall.getNameAsString().startsWith("assert")) {
                            insideAssert = true;
                            assertCall = parentCall;
                            break;
                        }
                        parent = p.getParentNode();
                    }
                }

                // Case 1: Inside an assert statement
                if (insideAssert && assertCall != null) {
                    for (Expression arg : assertCall.getArguments()) {
                        if (arg instanceof MethodCallExpr paramCall) {
                            String objType = isCallOnCustomObject(mce, variableMap);   //................................
                            if (objType != null) {
                                if (isCustomType(objType)) {
                                    //String resolved = resolveCustomCall(mce, variableMap);
                                    String resolved = getChainedMethodSignature(mce, objType);
                                    if (resolved != null) {
                                        assertionNestedCalls.method_calls.add(resolved);
                                    }
                                }
                            }
                            //check for static
                            else {
                                String baseStaticClass = checkIsStaticMethodCall(mce, (CombinedTypeSolver) typeSolver, customSolvers);
                                if (baseStaticClass != null) {
                                    String resolved = getChainedMethodSignature(mce, baseStaticClass);
                                    if (resolved != null) {
                                        assertionNestedCalls.method_calls.add(resolved);
                                    }
                                }

                            }
                            // ✅ Also extract any custom object field references used as arguments (e.g., user.id)
                            for (Expression argExpr : paramCall.getArguments()) {
                                if (argExpr instanceof com.github.javaparser.ast.expr.FieldAccessExpr fieldArg) {
                                    Expression scope = fieldArg.getScope();
                                    if (scope.isNameExpr()) {
                                        String varName = scope.asNameExpr().getNameAsString();
                                        if (variableMap.containsKey(varName)) {
                                            String customType = variableMap.get(varName);
                                            if (isCustomType(customType)) {
                                                String fieldAccess = customType + "#" + varName + "." + fieldArg.getNameAsString();
                                                assertionNestedCalls.attribute_calls.add(fieldAccess);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // ✅ Case 2: FieldAccessExpr — e.g., user.name
                        for (com.github.javaparser.ast.expr.FieldAccessExpr field : arg.findAll(com.github.javaparser.ast.expr.FieldAccessExpr.class)) {
                            Expression scope = field.getScope();
                            if (scope.isNameExpr()) {
                                String varName = scope.asNameExpr().getNameAsString();
                                if (variableMap.containsKey(varName)) {
                                    String customType = variableMap.get(varName);
                                    if (isCustomType(customType)) {
                                        String fullAccess = customType + "#" + varName + "." + field.getNameAsString();
                                        assertionNestedCalls.attribute_calls.add(fullAccess);
                                    }
                                }
                            }
                        }

                        // ✅ Case 3: NameExpr — e.g., assertTrue(user.isActive)
                        for (com.github.javaparser.ast.expr.NameExpr nameExpr : arg.findAll(com.github.javaparser.ast.expr.NameExpr.class)) {
                            String varName = nameExpr.getNameAsString();
                            if (variableMap.containsKey(varName)) {
                                String customType = variableMap.get(varName);
                                if (isCustomType(customType)) {
                                    String access = customType + "#" + varName;
                                    assertionNestedCalls.attribute_calls.add(access);
                                }
                            }
                        }
                    }
                }

                // Case 2: Not inside an assert
                else {
                    String objType = isCallOnCustomObject(mce, variableMap);
                    if (objType != null) {
                        if (isCustomType(objType)) {
                            //String resolved = resolveCustomCall(mce, variableMap);
                            String resolved = getChainedMethodSignature(mce, objType);
                            if (resolved != null) {
                                customMethodCalls.add(resolved);

                            }
                        }
                    }
                    //check for static
                    else {
                        String baseStaticClass = checkIsStaticMethodCall(mce, (CombinedTypeSolver) typeSolver, customSolvers);
                        if (baseStaticClass != null) {
                            String resolved = getChainedMethodSignature(mce, baseStaticClass);
                            if (resolved != null) {
                                customMethodCalls.add(resolved);

                            }

                        }
                    }
                }
            }

            //}


            // Output
            StaticTestMethodBody static_method_body = new StaticTestMethodBody();
            static_method_body.variable_map = variableMap;
            static_method_body.setup_method_calls = deduplicateCallChains(customMethodCalls);
            static_method_body.assertion_method_calls = assertionNestedCalls;
            static_method_bodies.put(method.getNameAsString(), static_method_body);

            //System.out.println("\nExtracted Variable Map for method " + method.getNameAsString() + ":");
            for (Map.Entry<String, String> entry : variableMap.entrySet()) {
                //System.out.println(entry.getKey() + " : " + entry.getValue());
            }

            // System.out.println("\n{");
            for (String call : deduplicateCallChains(customMethodCalls)) {
                //System.out.println("  " + call + ",");
            }
            //System.out.println("  assertions methods:{");
            for (String call : assertionNestedCalls.method_calls) {
                //System.out.println("    " + call + ",");
            }
            //System.out.println("  }");
            //System.out.println("  assertions objects:{");
            for (String call : assertionNestedCalls.attribute_calls) {
                //System.out.println("    " + call + ",");
            }
            //System.out.println("  }");
            //System.out.println("}\n");
        }
        return static_method_bodies;
    }

    private static List<String> deduplicateCallChains(List<String> chains) {
        List<String> result = new ArrayList<>();
        Set<String> toRemove = new HashSet<>();

        for (int i = 0; i < chains.size(); i++) {
            String a = chains.get(i);
            for (int j = 0; j < chains.size(); j++) {
                if (i == j) continue;
                String b = chains.get(j);
                // Check: is `a` a prefix of `b`, but not equal
                if (b.startsWith(a) && !b.equals(a)) {
                    toRemove.add(a);
                }
            }
        }

        for (String c : chains) {
            if (!toRemove.contains(c)) {
                result.add(c);
            }
        }
        return result;
    }

    private static String checkIsStaticMethodCall(MethodCallExpr call, CombinedTypeSolver
            typeSolver, List<JavaParserTypeSolver> customSolvers) {
        if (call.getScope().isPresent()) {
            Expression scope = call.getScope().get();
            try {
                ResolvedType resolvedType = scope.calculateResolvedType();
                if (resolvedType.isReferenceType()) {
                    String fqcn = resolvedType.asReferenceType().getQualifiedName();

                    // Now use your full custom class check
                    if (newIsCustomType(fqcn, typeSolver, customSolvers)) {
                        //System.out.println("Jsoup is a custom class!");
                        return fqcn;
                    } else {
                        //System.out.println("Jsoup is library/builtin.");
                        return null;
                    }
                }
            } catch (UnsolvedSymbolException | UnsupportedOperationException | IllegalArgumentException e) {
                System.err.println("Unresolvable scope or unsupported expression: " + scope + " → " + e.getMessage());
                return null;
            } catch (RuntimeException e) {
                System.err.println("Runtime failure while resolving scope: " + scope + " → " + e.getMessage());
                return null;
            } catch (Exception e) {

                System.err.println("Failed to resolve scope 'Jsoup': " + e.getMessage());
                return null;
            }
        }
        return null;
    }

    private static String isCallOnCustomObject(MethodCallExpr call, Map<String, String> variableMap) {
        if (call.getScope().isPresent()) {
            Expression scope = call.getScope().get();
            while (scope != null) {
                if (scope.isMethodCallExpr()) {
                    scope = scope.asMethodCallExpr().getScope().orElse(null);
                } else if (scope.isNameExpr()) {
                    String varName = scope.asNameExpr().getNameAsString();
                    String type = variableMap.get(varName);
                    return type;
                    //return isCustomType(type);
                } else {
                    break;
                }
            }
        }
        return null;
    }

    private static String resolveCustomCall(MethodCallExpr mce, Map<String, String> variableMap) {
        try {
            ResolvedMethodDeclaration resolved = mce.resolve();
            String scopeType;
            if (mce.getScope().isPresent()) {
                Expression scope = mce.getScope().get();
                try {
                    scopeType = scope.calculateResolvedType().describe();
                } catch (Exception e) {
                    if (scope.isNameExpr()) {
                        String varName = scope.asNameExpr().getNameAsString();
                        scopeType = variableMap.getOrDefault(varName, null);
                        if (scopeType == null) return null;
                    } else return null;
                }
                if (!scopeType.startsWith("java.")) {
                    return scopeType + "#" + resolved.getName();
                }
            } else {
                String classNameResolved = resolved.getQualifiedName().replace("." + resolved.getName(), "");
                if (!classNameResolved.startsWith("java.")) {
                    return classNameResolved + "#" + resolved.getName();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    static boolean isCustomType(String type) {
        if (type == null || type.isEmpty()) return false;

        // Filter out common Java standard library packages
        return !(
                type.startsWith("java.") ||
                        type.startsWith("javax.") ||
                        type.startsWith("org.w3c.") ||
                        type.startsWith("org.xml.") ||
                        type.startsWith("com.sun.") ||
                        type.startsWith("sun.") ||
                        type.startsWith("jdk.")
        );
    }

    static boolean newIsCustomType(String typeName, CombinedTypeSolver
            typeSolver, List<JavaParserTypeSolver> customSolvers) {
        if (typeName == null || typeName.isEmpty()) return false;

        // Strip generics (e.g., List<Foo> -> List)
        int genericIndex = typeName.indexOf('<');
        if (genericIndex != -1) {
            typeName = typeName.substring(0, genericIndex).trim();
        }

        // Strip array brackets (e.g., Foo[] -> Foo)
        if (typeName.endsWith("[]")) {
            typeName = typeName.replace("[]", "").trim();
        }

        try {
            // Resolve the type using the full CombinedTypeSolver
            ResolvedReferenceTypeDeclaration resolved = typeSolver.solveType(typeName);

            // Try to re-resolve it using your custom solvers (main/test)
            for (JavaParserTypeSolver customSolver : customSolvers) {
                try {
                    ResolvedReferenceTypeDeclaration customResolved = customSolver.solveType(typeName);
                    if (resolved.getQualifiedName().equals(customResolved.getQualifiedName())) {
                        return true; // It is a custom (project) class
                    }
                } catch (Exception ignored) {
                    // This solver doesn't contain the type — continue checking others
                    System.out.println();
                    return false;
                }
            }
        } catch (Exception e) {
            System.err.println("newIsCustomType failed for type: " + typeName + " → " + e.getMessage());
            return false;
        }

        return false;
    }


    private static String getChainedMethodSignature(MethodCallExpr call, String baseObjType) {
        List<String> chain = new ArrayList<>();

        MethodCallExpr current = call;
        while (true) {
            try {
                ResolvedMethodDeclaration resolved = current.resolve();

                //String className = resolved.getQualifiedName().replace("." + resolved.getName(), "");
                String methodName = resolved.getName();

                List<String> params = new ArrayList<>();
                for (int i = 0; i < resolved.getNumberOfParams(); i++) {
                    params.add(resolved.getParam(i).getType().describe());
                }

                String methodSig = methodName + "(" + String.join(", ", params) + ")";
                // Only add class for the base (first resolved)
                chain.add(methodSig);

            } catch (Exception e) {
                chain.add(current.toString());
            }

            Optional<Expression> scope = current.getScope();
            if (scope.isPresent() && scope.get() instanceof MethodCallExpr nextCall) {
                current = nextCall;
            } else {
                break;
            }
        }

        Collections.reverse(chain); // Base to top of chain
        return baseObjType + "#" + String.join("/", chain);
    }


    public static Path findFileByClassName(Path root, String className) throws IOException {
        String[] parts = className.split("\\.");
        String name = parts[parts.length - 1];

        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(name + ".java"))
                    .findFirst()
                    .orElse(null);
        }
    }
}
