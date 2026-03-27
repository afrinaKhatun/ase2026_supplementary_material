package org.example;

import java.util.*;

public class JavapClassificationResult {
    public enum FinalTag {
        // Exact equality
        IDEN,

        // Set-level changes (unique kinds)
        ADD,                 // some kinds appear in B but not in A
        REM,               // some kinds appear in A but not in B

        // Multiplicity changes on common kinds
        CMN_COUNT_INC,     // at least one common kind has freqB > freqA
        CMN_COUNT_DEC,     // at least one common kind has freqB < freqA

        // Multiplicity on newly-added kinds (optional but useful)
        ADD_MUL,        // at least one added kind appears >= 2 times in B

        // Order tags (only emitted when comparable: same kinds + same counts)
        ORD_SAME,
        ORD_CHNG,

        NOT_CLASSIFIED
    }

    public enum KindTag {
        ADDED_KIND,        // kind in B but not A
        REMOVED_KIND,      // kind in A but not B
        COMMON_KIND,       // kind in both

        COUNT_SAME,        // only for COMMON_KIND: freq equal
        COUNT_INCREASED,   // only for COMMON_KIND: freqB > freqA
        COUNT_DECREASED,   // only for COMMON_KIND: freqB < freqA

        MULTIPLE_IN_A,     // freqA >= 2
        MULTIPLE_IN_B      // freqB >= 2
    }

    public EnumSet<FinalTag> finalTags;

    // Explainability / debugging
    public Set<String> addedKinds;
    public Set<String> removedKinds;
    public Set<String> increasedCommonKinds;
    public Set<String> decreasedCommonKinds;
    public Set<String> sameCommonKinds;

    public Map<String, Integer> freqA;
    public Map<String, Integer> freqB;
    public Map<String, Integer> deltaByKind;

    // Optional: per-kind tags
    public Map<String, EnumSet<KindTag>> kindTags;
    public JavapClassificationResult(){}
    public JavapClassificationResult(EnumSet<FinalTag> finalTags,
                                Set<String> addedKinds,
                                Set<String> removedKinds,
                                Set<String> increasedCommonKinds,
                                Set<String> decreasedCommonKinds,
                                Set<String> sameCommonKinds,
                                Map<String, Integer> freqA,
                                Map<String, Integer> freqB,
                                Map<String, Integer> deltaByKind,
                                Map<String, EnumSet<KindTag>> kindTags) {
        this.finalTags = finalTags;
        this.addedKinds = addedKinds;
        this.removedKinds = removedKinds;
        this.increasedCommonKinds = increasedCommonKinds;
        this.decreasedCommonKinds = decreasedCommonKinds;
        this.sameCommonKinds = sameCommonKinds;
        this.freqA = freqA;
        this.freqB = freqB;
        this.deltaByKind = deltaByKind;
        this.kindTags = kindTags;
    }

}
