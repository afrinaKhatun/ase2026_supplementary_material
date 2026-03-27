package result.analysis;

import java.util.*;
import java.util.regex.Pattern;

public class MethodNameSimilarityAnalysis {

    // Split boundaries inside a token:
    //  - lower/digit -> Upper (fooBar, version2Test)
    //  - ACRONYM -> Word (XMLParser, HTTPServerError)
    //  - letter -> digit (HTTP2Support)
    //  - digit -> letter (Version2Test)
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile(
            "(?<=[a-z0-9])(?=[A-Z])"        // fooBar -> foo | Bar, v2Test -> v2 | Test
                    + "|(?<=[A-Z])(?=[A-Z][a-z])"     // XMLParser -> XML | Parser, HTTPServer -> HTTP | Server
                    + "|(?<=[A-Za-z])(?=[0-9])"       // HTTP2Support -> HTTP | 2Support
                    + "|(?<=[0-9])(?=[A-Za-z])"       // Version2Test -> Version2 | Test
    );

    /**
     * Tokenize an identifier / method name by:
     *  - replacing '_' and '-' with spaces
     *  - splitting on non-alphanumeric
     *  - then splitting each piece on camel-case boundaries
     *  - lowercasing everything
     */
    public static List<String> tokenizeIdentifier(String name) {
        List<String> tokens = new ArrayList<>();
        if (name == null || name.isEmpty()) {
            return tokens;
        }

        // 1) Normalize underscores and dashes to spaces
        String normalized = name.replace('_', ' ')
                .replace('-', ' ');

        // 2) Split on non-alphanumeric (just in case)
        String[] coarseParts = normalized.split("[^A-Za-z0-9]+");

        for (String coarse : coarseParts) {
            if (coarse.isEmpty()) {
                continue;
            }

            // 3) Split each coarse part on camel-case / acronym / digit boundaries
            String[] camelParts = CAMEL_BOUNDARY.split(coarse);

            for (String p : camelParts) {
                String tok = p.toLowerCase(Locale.ROOT).trim();
                if (!tok.isEmpty()) {
                    tokens.add(tok);
                }
            }
        }

        return tokens;
    }

    /**
     * Compute Jaccard similarity between two method names,
     * based on their token sets.
     */
    public static double jaccardSimilarityForMethodNames(String method1, String method2) {
        List<String> t1 = tokenizeIdentifier(method1);
        List<String> t2 = tokenizeIdentifier(method2);

        if (t1.isEmpty() && t2.isEmpty()) {
            return 1.0; // or 0.0, depending on your convention
        }

        Set<String> set1 = new HashSet<>(t1);
        Set<String> set2 = new HashSet<>(t2);

        // intersection
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        // union
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size() / (double) union.size();
    }

    // small demo
    public static void main(String[] args) {
        String m1 = "testFuzzyDistance_withLongStringHTTP2Support";
        String m2 = "test_Fuzzy_Distance-with-long-string-http2_support";

        System.out.println(jaccardSimilarityForMethodNames(m1, m2));
    }
}

