package org.example;

import analyzer.AssertionCall;
import analyzer.MethodCallSignature;

import java.util.List;

public class NormalizationResult {
        public String originalSource;
        public String assertionFull_NormalizedSource;
        public String assertionLess_LiteralPreserving_NormalizedSource;
        public List<MethodCallSignature> regularMethodCalls;
        public List<AssertionCall> assertionMethodCalls;
        public String literalValueOnly_NormalizedSource;


        public NormalizationResult(String originalSource, String normalizedSource, String assertionLessNormalizedSource, List<MethodCallSignature> regularMethodCalls, List<AssertionCall> assertionMethodCalls, String literalValueOnly_NormalizedSource) {
            this.originalSource = originalSource;
            this.assertionFull_NormalizedSource = normalizedSource;
            this.assertionLess_LiteralPreserving_NormalizedSource = assertionLessNormalizedSource;
            this.regularMethodCalls = regularMethodCalls;
            this.assertionMethodCalls = assertionMethodCalls;
            this.literalValueOnly_NormalizedSource = literalValueOnly_NormalizedSource;
        }

}
