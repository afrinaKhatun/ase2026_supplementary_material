package org.example;

import tree.analyzer.JavapMethodStruct;

import java.util.*;

public class JavapInputAmplificationDetector {
    public class MethodCallAmplificationResult {
        public List<JavapMethodStruct> deletedCalls;
        public List<List<JavapMethodStruct>> duplicatedCalls;
        public List<JavapMethodStruct> addedCalls;
        public List<List<JavapMethodStruct>> sameCalls;

        public MethodCallAmplificationResult(List<JavapMethodStruct> deletedCalls,
                                             List<JavapMethodStruct> addedCalls,
                                             List<List<JavapMethodStruct>> duplicatedCalls,
                                             List<List<JavapMethodStruct>> sameCalls) {
            this.deletedCalls = deletedCalls;
            this.duplicatedCalls = duplicatedCalls;
            this.addedCalls = addedCalls;
            this.sameCalls = sameCalls;
        }
    }
    public static String checkJavapAmplification(List<String> existing, List<String> temp){
            String classification = classify(existing,temp).toString();
            return classification;
    }
   public static enum CallSequenceRelation {
        // Set-level differences (which methods appear at all)
        NEW_ADD,                    // new method kinds appear in B
        NEW_REM,                 // some method kinds from A missing in B
        NEW_ADD_REM,         // both added and removed kinds
        NEW_DUP,             // atleast one added/removed kinds + multiplicity changes

        // Same method kinds (same set), multiplicity & order
        IDEN,                  // same elements, same counts, same order
        ORD,               // same elements, same counts, different order

        DUP_ADD,              // same method kinds, some counts increased only
        DUP_REMOVE,           // same method kinds, some counts decreased only
        DUP_ADD_REM    // same method kinds, some counts up, some down
    }
    public static enum ChangeSeverity{
        NONE,
        FEW,
        MEDIUM,
        MANY
    }
    // ---------- JACCARD ON UNIQUE ELEMENTS ----------
    public static double jaccardOnUnique(List<String> a, List<String> b) {
        if (a == null) a = Collections.emptyList();
        if (b == null) b = Collections.emptyList();

        Set<String> setA = new HashSet<>(a);
        Set<String> setB = new HashSet<>(b);

        if (setA.isEmpty() && setB.isEmpty()) return 1.0;

        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        return (double) intersection.size() / union.size();
    }
    //Multi set jaccard
    public static double jaccardOnMultiset(List<String> a, List<String> b) {
        if (a == null) a = Collections.emptyList();
        if (b == null) b = Collections.emptyList();

        if (a.isEmpty() && b.isEmpty()) return 1.0;

        // Count frequencies
        Map<String, Integer> ca = new HashMap<>();
        for (String s : a) {
            ca.put(s, ca.getOrDefault(s, 0) + 1);
        }

        Map<String, Integer> cb = new HashMap<>();
        for (String s : b) {
            cb.put(s, cb.getOrDefault(s, 0) + 1);
        }

        int intersection = 0;
        int union = 0;

        // First loop: go through A's keys
        for (String key : ca.keySet()) {
            int countA = ca.get(key);
            int countB = cb.getOrDefault(key, 0);

            intersection += Math.min(countA, countB);
            union        += Math.max(countA, countB);
        }

        // Second loop: keys only in B (not seen in A)
        for (String key : cb.keySet()) {
            if (!ca.containsKey(key)) {
                union += cb.get(key);
            }
        }

        return union == 0 ? 1.0 : (double) intersection / union;
    }
    public static double getNormalizedEditDistance(List<String> seq1, List<String> seq2) {
        int m = seq1.size();
        int n = seq2.size();
        int maxLen = Math.max(m, n);
        if (maxLen == 0) {
            return 0.0; // both empty → identical
        }
        int dist = editDistance(seq1, seq2);
        return (double) dist / maxLen;
    }

    public static int editDistance(List<String> seq1, List<String> seq2) {
        int m = seq1.size();
        int n = seq2.size();

        int[][] dp = new int[m + 1][n + 1];

        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= m; i++) {
            String t1 = seq1.get(i - 1);
            for (int j = 1; j <= n; j++) {
                String t2 = seq2.get(j - 1);
                int cost = t1.equals(t2) ? 0 : 1;

                int deletion     = dp[i - 1][j] + 1;
                int insertion    = dp[i][j - 1] + 1;
                int substitution = dp[i - 1][j - 1] + cost;

                dp[i][j] = Math.min(Math.min(deletion, insertion), substitution);
            }
        }
        return dp[m][n];
    }

    public static double getNormalizedEditDistance(String s1, String s2) {
        if (s1 == null) s1 = "";
        if (s2 == null) s2 = "";

        int m = s1.length();
        int n = s2.length();
        int maxLen = Math.max(m, n);
        if (maxLen == 0) return 0.0;

        int dist = editDistance(s1, s2);
        return (double) dist / maxLen;
    }

    /** Levenshtein edit distance on characters (insert/delete/substitute cost=1). */
    public static int editDistance(String s1, String s2) {
        if (s1 == null) s1 = "";
        if (s2 == null) s2 = "";

        int m = s1.length();
        int n = s2.length();

        // dp[j] holds dp[i][j] for current i; prevDiag holds dp[i-1][j-1]
        int[] dp = new int[n + 1];

        // base row: transforming "" -> s2[0..j)
        for (int j = 0; j <= n; j++) dp[j] = j;

        for (int i = 1; i <= m; i++) {
            int prevDiag = dp[0];     // dp[i-1][0]
            dp[0] = i;                // dp[i][0] = i deletions
            char c1 = s1.charAt(i - 1);

            for (int j = 1; j <= n; j++) {
                int temp = dp[j];     // dp[i-1][j] before overwrite
                char c2 = s2.charAt(j - 1);

                int cost = (c1 == c2) ? 0 : 1;

                int deletion = dp[j] + 1;        // dp[i-1][j] + 1
                int insertion = dp[j - 1] + 1;   // dp[i][j-1] + 1
                int substitution = prevDiag + cost; // dp[i-1][j-1] + cost

                dp[j] = Math.min(Math.min(deletion, insertion), substitution);
                prevDiag = temp;
            }
        }
        return dp[n];
    }
    /**
     * Optional helper if you want to ignore whitespace differences
     * (useful for method bodies).
     */
    public static String normalizeWhitespace(String s) {
        if (s == null) return "";
        // collapse all whitespace to single spaces and trim
        return s.replaceAll("\\s+", " ").trim();
    }
    private static ChangeSeverity severityFromNormalizedDistance(double ned) {
        if (ned <= 0.05) {         // 0–5% edits
            return ChangeSeverity.NONE;
        } else if (ned <= 0.20) {  // up to 20% edits
            return ChangeSeverity.FEW;
        } else if (ned <= 0.40) {  // 20–40% edits
            return ChangeSeverity.MEDIUM;
        } else {
            return ChangeSeverity.MANY;
        }
    }
    // ---------- CORE CLASSIFICATION (ASSUMES NO JACCARD THRESHOLD) ----------
    public static String classifyWithEditDistance(double ned) {// see below
        ChangeSeverity severity = severityFromNormalizedDistance(ned);

        return severity.toString();
    }
    public static CallSequenceRelation classify(List<String> a, List<String> b) {
        if (a == null) a = Collections.emptyList();
        if (b == null) b = Collections.emptyList();

        // 1) Unique sets
        Set<String> setA = new HashSet<>(a);
        Set<String> setB = new HashSet<>(b);

        Set<String> addedKinds = new HashSet<>(setB);
        addedKinds.removeAll(setA);

        Set<String> removedKinds = new HashSet<>(setA);
        removedKinds.removeAll(setB);

        boolean hasAdd = !addedKinds.isEmpty();
        boolean hasRemove = !removedKinds.isEmpty();

        // 2) Frequencies for multiplicity analysis
        Map<String, Integer> freqA = buildFreq(a);
        Map<String, Integer> freqB = buildFreq(b);

        // Only care about multiplicity changes on common kinds
        Set<String> commonKinds = new HashSet<>(setA);
        commonKinds.retainAll(setB);

        boolean hasIncreaseOnCommon = false;
        boolean hasDecreaseOnCommon = false;

        for (String key : commonKinds) {
            int ca = freqA.getOrDefault(key, 0);
            int cb = freqB.getOrDefault(key, 0);

            if (cb > ca) hasIncreaseOnCommon = true;
            if (cb < ca) hasDecreaseOnCommon = true;
        }

        boolean hasMultiplicityChangeOnCommon = hasIncreaseOnCommon || hasDecreaseOnCommon;

        // 3) If there are NEW kinds (added or removed)...
        if (hasAdd || hasRemove) {

            // NEW + DUP: set-level change AND multiplicity change on common methods
            if (hasMultiplicityChangeOnCommon) {
                return CallSequenceRelation.NEW_DUP;
            }

            // Pure NEW cases (no multiplicity change on common methods)
            if (hasAdd && !hasRemove) {
                return CallSequenceRelation.NEW_ADD;
            } else if (!hasAdd && hasRemove) {
                return CallSequenceRelation.NEW_REM;
            } else {
                return CallSequenceRelation.NEW_ADD_REM;
            }
        }

        // 4) No NEW kinds at all → sets are identical: pure DUP / IDEN / ORD region
        if (!hasIncreaseOnCommon && !hasDecreaseOnCommon) {
            // Same counts → check order
            if (a.equals(b)) {
                return CallSequenceRelation.IDEN;
            } else {
                return CallSequenceRelation.ORD;
            }
        }

        // 5) Multiplicity changed, same unique kinds
        if (hasIncreaseOnCommon && !hasDecreaseOnCommon) {
            return CallSequenceRelation.DUP_ADD;
        } else if (!hasIncreaseOnCommon && hasDecreaseOnCommon) {
            return CallSequenceRelation.DUP_REMOVE;
        } else {
            return CallSequenceRelation.DUP_ADD_REM;
        }
    }

    private static Map<String, Integer> buildFreq(List<String> list) {
        Map<String, Integer> freq = new HashMap<>();
        for (String s : list) {
            freq.merge(s, 1, Integer::sum);
        }
        return freq;
    }

    public static String finalTagsKey(JavapClassificationResult result) {
        List<String> names = new ArrayList<>();
        for (JavapClassificationResult.FinalTag t : result.finalTags) {
            if(t.equals(JavapClassificationResult.FinalTag.ADD)){
                names.add(t.name()+result.addedKinds.toString());
            }
            if(t.equals(JavapClassificationResult.FinalTag.REM)){
                names.add(t.name()+result.removedKinds.toString());
            }
            if(t.equals(JavapClassificationResult.FinalTag.CMN_COUNT_INC)){
                names.add(t.name()+result.increasedCommonKinds.toString());
            }
            if(t.equals(JavapClassificationResult.FinalTag.CMN_COUNT_DEC)){
                names.add(t.name()+result.decreasedCommonKinds.toString());
            }
            if(t.equals(JavapClassificationResult.FinalTag.IDEN) ||
                    t.equals(JavapClassificationResult.FinalTag.ORD_SAME) ||
                    t.equals(JavapClassificationResult.FinalTag.ORD_CHNG) ||
                    t.equals(JavapClassificationResult.FinalTag.NOT_CLASSIFIED) ||
                    t.equals(JavapClassificationResult.FinalTag.ADD_MUL)
            ){
                names.add(t.name());
            }
        };
        Collections.sort(names);
        return String.join(" | ", names);
    }
    public static JavapClassificationResult classifyUpdate(List<String> a, List<String> b) {
        if (a == null) a = Collections.emptyList();
        if (b == null) b = Collections.emptyList();

        // Fast path: identical sequences
        if (a.equals(b)) {
            EnumSet<JavapClassificationResult.FinalTag> tags = EnumSet.of(JavapClassificationResult.FinalTag.IDEN, JavapClassificationResult.FinalTag.ORD_SAME);

            Map<String, Integer> freqA = buildFrequency(a);
            Map<String, Integer> freqB = buildFrequency(b);
            Map<String, Integer> delta = buildDelta(freqA, freqB);
            Map<String, EnumSet<JavapClassificationResult.KindTag>> kindTags = buildKindTags(freqA, freqB);

            // In identical case, all kinds are common + count same
            Set<String> empty = Collections.emptySet();
            Set<String> sameCommon = new HashSet<>(freqA.keySet());

            return new JavapClassificationResult(
                    tags,
                    empty,
                    empty,
                    empty,
                    empty,
                    sameCommon,
                    freqA,
                    freqB,
                    delta,
                    kindTags
            );
        }
        // Unique sets
        Set<String> setA = new HashSet<>(a);
        Set<String> setB = new HashSet<>(b);

        Set<String> addedKinds = new HashSet<>(setB);
        addedKinds.removeAll(setA);

        Set<String> removedKinds = new HashSet<>(setA);
        removedKinds.removeAll(setB);

        // Frequencies
        Map<String, Integer> freqA = buildFrequency(a);
        Map<String, Integer> freqB = buildFrequency(b);

        Map<String, Integer> deltaByKind = buildDelta(freqA, freqB);

        // Common kinds and count changes
        Set<String> commonKinds = new HashSet<>(setA);
        commonKinds.retainAll(setB);

        Set<String> increasedCommonKinds = new HashSet<>();
        Set<String> decreasedCommonKinds = new HashSet<>();
        Set<String> sameCommonKinds = new HashSet<>();

        for (String k : commonKinds) {
            int ca = freqA.getOrDefault(k, 0);
            int cb = freqB.getOrDefault(k, 0);
            if (cb > ca) increasedCommonKinds.add(k);
            else if (cb < ca) decreasedCommonKinds.add(k);
            else sameCommonKinds.add(k);
        }

        // Final tags
        EnumSet<JavapClassificationResult.FinalTag> finalTags = EnumSet.noneOf(JavapClassificationResult.FinalTag.class);

        // Set-level tags
        if (!addedKinds.isEmpty()) finalTags.add(JavapClassificationResult.FinalTag.ADD);
        if (!removedKinds.isEmpty()) finalTags.add(JavapClassificationResult.FinalTag.REM);

        // Multiplicity on common kinds
        if (!increasedCommonKinds.isEmpty()) finalTags.add(JavapClassificationResult.FinalTag.CMN_COUNT_INC);
        if (!decreasedCommonKinds.isEmpty()) finalTags.add(JavapClassificationResult.FinalTag.CMN_COUNT_DEC);

        // Added-kind multiplicity
        if (!addedKinds.isEmpty()) {
            boolean hasAddedMultiple = false;
            for (String k : addedKinds) {
                if (freqB.getOrDefault(k, 0) >= 2) {
                    hasAddedMultiple = true;
                    break;
                }
            }
            if (hasAddedMultiple) finalTags.add(JavapClassificationResult.FinalTag.ADD_MUL);
        }
        // Order tags only when comparable:
        // comparable means same set AND same counts (i.e., no added/removed and no inc/dec on common)
        boolean comparableForOrder =
                addedKinds.isEmpty() &&
                        removedKinds.isEmpty() &&
                        increasedCommonKinds.isEmpty() &&
                        decreasedCommonKinds.isEmpty();

        if (comparableForOrder) {
            // at this point sets are identical and counts are identical, but order may differ
            if (a.equals(b)) finalTags.add(JavapClassificationResult.FinalTag.ORD_SAME);
            else finalTags.add(JavapClassificationResult.FinalTag.ORD_CHNG);
        }

        // If somehow no tags were added (shouldn't happen because identical handled above),
        // still provide something stable.
        if (finalTags.isEmpty()) {
            // This could happen only in weird cases where lists differ but are somehow treated same;
            // keep a conservative marker:
            finalTags.add(JavapClassificationResult.FinalTag.NOT_CLASSIFIED);
        }

        // Per-kind tags (optional but useful)
        Map<String, EnumSet<JavapClassificationResult.KindTag>> kindTags = buildKindTags(freqA, freqB);
        return new JavapClassificationResult(
                finalTags,
                addedKinds,
                removedKinds,
                increasedCommonKinds,
                decreasedCommonKinds,
                sameCommonKinds,
                freqA,
                freqB,
                deltaByKind,
                kindTags
        );
    }
    public static EnumSet<JavapClassificationResult.FinalTag> classifyFinalTags(List<String> a, List<String> b) {
        return classifyUpdate(a, b).finalTags;
    }
    private static Map<String, Integer> buildFrequency(List<String> list) {
        Map<String, Integer> freq = new HashMap<>();
        for (String s : list) {
            freq.merge(s, 1, Integer::sum);
        }
        return freq;
    }
    private static Map<String, Integer> buildDelta(Map<String, Integer> freqA, Map<String, Integer> freqB) {
        Set<String> union = new HashSet<>(freqA.keySet());
        union.addAll(freqB.keySet());

        Map<String, Integer> delta = new HashMap<>();
        for (String k : union) {
            int a = freqA.getOrDefault(k, 0);
            int b = freqB.getOrDefault(k, 0);
            delta.put(k, b - a);
        }
        return delta;
    }
    private static Map<String, EnumSet<JavapClassificationResult.KindTag>> buildKindTags(Map<String, Integer> freqA, Map<String, Integer> freqB) {
        Set<String> union = new HashSet<>(freqA.keySet());
        union.addAll(freqB.keySet());

        Map<String, EnumSet<JavapClassificationResult.KindTag>> out = new HashMap<>();

        for (String k : union) {
            int a = freqA.getOrDefault(k, 0);
            int b = freqB.getOrDefault(k, 0);

            EnumSet<JavapClassificationResult.KindTag> tags = EnumSet.noneOf(JavapClassificationResult.KindTag.class);

            if (a == 0 && b > 0) {
                tags.add(JavapClassificationResult.KindTag.ADDED_KIND);
            } else if (a > 0 && b == 0) {
                tags.add(JavapClassificationResult.KindTag.REMOVED_KIND);
            } else {
                tags.add(JavapClassificationResult.KindTag.COMMON_KIND);
                if (b > a) tags.add(JavapClassificationResult.KindTag.COUNT_INCREASED);
                else if (b < a) tags.add(JavapClassificationResult.KindTag.COUNT_DECREASED);
                else tags.add(JavapClassificationResult.KindTag.COUNT_SAME);
            }

            if (a >= 2) tags.add(JavapClassificationResult.KindTag.MULTIPLE_IN_A);
            if (b >= 2) tags.add(JavapClassificationResult.KindTag.MULTIPLE_IN_B);

            out.put(k, tags);
        }

        return out;
    }
}
