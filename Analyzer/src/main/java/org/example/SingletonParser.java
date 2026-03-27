package org.example;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SingletonParser {
    public final CombinedTypeSolver typeSolver;
    public final JavaParser parser;
    //srcPath = "/Users/afrinakhatun/IdeaProjects/jsoup/src/main/java"
    //testPath = "/Users/afrinakhatun/IdeaProjects/jsoup/src/test/java"
    public SingletonParser(String srcPath, String testPath){
        this.typeSolver = new CombinedTypeSolver(
                new ReflectionTypeSolver(), // 🔹 For java.lang.*, java.util.*, etc.
                new JavaParserTypeSolver(new File(srcPath)), // production
                new JavaParserTypeSolver(new File(testPath))  // test
        );
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        ParserConfiguration config = new ParserConfiguration()
                .setSymbolResolver(symbolSolver)
                .setAttributeComments(false)  // reduce memory
                .setLexicalPreservationEnabled(false)
                .setStoreTokens(false);;
        this.parser = new JavaParser(config);
    }


}
