package org.example;

import java.nio.channels.Channel;
import java.util.*;

public class MethodModificationInformation {
    public String method_name;
    public String modification_type;
    //public Map<String, List<String>> inputAmplifications = new HashMap<>();
    //public Map<String,List<DuplicateMethodInfo>> duplicateArgumentAmplification = new HashMap<>();
    //public Map<String,List<DuplicateMethodInfo>> sameMethodArgumentAmplification = new HashMap<>();
    //public Map<String,List<Character>> assertionMethodAmplification = new HashMap<>();
    //public Map<String, Integer> assertionAttributeAmplification = new HashMap<>();
    public Map<String, Double> similarityScores = new HashMap<>();
    //public Map<String, String> amplification_category = new HashMap<>();
    public Map<String, JavapClassificationResult> amplification_category = new HashMap<>();
    //public Map<String, Double> assertionSimilarityScores = new HashMap<>();
    public Map<String, Double> editDistanceScores = new HashMap<>();
    public Map<String, Double> methodBodyEditDistanceScores = new HashMap<>();
    public Map<String, Integer> positionDistanceScores = new HashMap<>();
    public Set<String> newMethodsTested = new HashSet<>();
    //public Map<String, Set<String>> extraObjects = new HashMap<>();
    public String method_body;
    public String normalized_method_body; //List of method call names

    public String literal_value_only_normalized_body;
    //public Map<String, String> amplification_severity = new HashMap<>();
    public Map<String, PeerMethodSimilarity> addedPeerScores = new HashMap<>();
    public Map<String, PeerMethodSimilarity> modifiedPeerScores = new HashMap<>();


    public MethodModificationInformation(){

    }
    public MethodModificationInformation(String n, String t, String b){
        method_name = n;
        modification_type = t;
        method_body = b;

    }

    public enum ArgumentAmplificationType{
        INT_PLUS_ONE,
        INT_MINUS_ONE,
        INT_MULTIPLICATION_BY_TWO,
        INT_DIVIDE_BY_TWO,
        INT_ZERO,
        STR_NULL,
        STR_EMPTY,
        STR_ADD_CHARACTER,
        STR_REMOVE_CHARACTER,
        STR_DOUBLE,
        NOT_MATCH_ANY_RULE,
        ARGUMENT_SIZE_MISMATCH,
        ARGUMENT_TYPE_MISMATCH,
        BOOLEAN_NEGATION,
        REMAIN_SAME,
        REPLACE_NULL_RULE,
        ANY_RULE_NA
    }
    public static EnumSet<ArgumentAmplificationType> operators = EnumSet.of(
        ArgumentAmplificationType.INT_PLUS_ONE,
        ArgumentAmplificationType.INT_MINUS_ONE,
        ArgumentAmplificationType.INT_MULTIPLICATION_BY_TWO,
        ArgumentAmplificationType.INT_DIVIDE_BY_TWO,
        ArgumentAmplificationType.INT_ZERO,
        ArgumentAmplificationType.STR_NULL,
        ArgumentAmplificationType.STR_EMPTY,
        ArgumentAmplificationType.STR_ADD_CHARACTER,
        ArgumentAmplificationType.STR_REMOVE_CHARACTER,
        ArgumentAmplificationType.STR_DOUBLE,
        ArgumentAmplificationType.BOOLEAN_NEGATION,
        ArgumentAmplificationType.REPLACE_NULL_RULE
    );


}
