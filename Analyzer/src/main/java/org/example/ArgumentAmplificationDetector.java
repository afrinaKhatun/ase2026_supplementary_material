package org.example;

import analyzer.AssertArgument;
import analyzer.AssertionCall;
import analyzer.MethodCall;
import analyzer.MethodCallSignature;

public class ArgumentAmplificationDetector {
    public MethodModificationInformation.ArgumentAmplificationType IdentifyArgumentChanges(AssertArgument oldArgument,
                                                                                           AssertArgument modifiedArgument) {
        if(oldArgument.argumentType.equals("MTH") || oldArgument.argumentType.equals("FLD")
        || oldArgument.argumentType.equals("NAM") || oldArgument.argumentType.equals("EXP")){
            return MethodModificationInformation.ArgumentAmplificationType.ANY_RULE_NA;
        }
        else{
            if (oldArgument.argumentType.equals(modifiedArgument.argumentType)) {
                if (oldArgument.argumentType.equals("LIT_STR")) {
                    return followsStringAmplificationRules(oldArgument.literal_attribute_methodName,
                            modifiedArgument.literal_attribute_methodName);
                } else if (oldArgument.argumentType.equals("LIT_INT")) {
                    return followsIntegerAmplificationRules(Integer.parseInt(oldArgument.literal_attribute_methodName.trim()),
                            Integer.parseInt(modifiedArgument.literal_attribute_methodName.trim()));
                } else if (oldArgument.argumentType.equals("LIT_BOOL")) {
                    return followsBoolAmplificationRules(stringToBoolean(oldArgument.literal_attribute_methodName),
                            stringToBoolean(modifiedArgument.literal_attribute_methodName));
                } else if (oldArgument.argumentType.equals("LIT_NULL")) {
                    return followsNullAmplificationRules(oldArgument.literal_attribute_methodName,
                            modifiedArgument.literal_attribute_methodName);
                } else {
                    return MethodModificationInformation.ArgumentAmplificationType.ANY_RULE_NA;
                }
            } else {
                //System.out.println("argument type Do not Match");
                return MethodModificationInformation.ArgumentAmplificationType.ARGUMENT_TYPE_MISMATCH;
            }
        }
    }

    private MethodModificationInformation.ArgumentAmplificationType followsNullAmplificationRules(String original, String modified) {
        if (original == null && modified != null) {
            return MethodModificationInformation.ArgumentAmplificationType.REPLACE_NULL_RULE;
        } else if (original != null && modified == null) {
            return MethodModificationInformation.ArgumentAmplificationType.REPLACE_NULL_RULE;
        }
        return MethodModificationInformation.ArgumentAmplificationType.NOT_MATCH_ANY_RULE;
    }

    private static boolean stringToBoolean(String str) throws IllegalArgumentException {
        if (str == null || str.trim().isEmpty()) {
            throw new IllegalArgumentException("Input string is null or empty");
        }

        String normalized = str.trim().toLowerCase();
        if (normalized.equals("true")) {
            return true;
        } else if (normalized.equals("false")) {
            return false;
        } else {
            throw new IllegalArgumentException("Input string is not a valid boolean: " + str);
        }
    }

    private MethodModificationInformation.ArgumentAmplificationType followsStringAmplificationRules(String original, String modified) {
        // Validate inputs
        if (original != null && modified == null) {
            return MethodModificationInformation.ArgumentAmplificationType.STR_NULL;
        }
        // Mutation 1: Replace with empty string
        if (!original.isEmpty() && modified.isEmpty()) {
            return MethodModificationInformation.ArgumentAmplificationType.STR_EMPTY;
        }
        // Mutation 2: Add a random character at any position
        if (modified.length() == original.length() + 1) {
            // Check if removing one character from target results in original
            for (int i = 0; i < modified.length(); i++) {
                String prefix = modified.substring(0, i);
                String suffix = modified.substring(i + 1);
                if ((prefix + suffix).equals(original)) {
                    return MethodModificationInformation.ArgumentAmplificationType.STR_ADD_CHARACTER;
                }
            }
        }
        // Mutation 3: Delete a random character
        if (modified.length() == original.length() - 1) {
            // Check if target can be formed by removing one character from original
            for (int i = 0; i < original.length(); i++) {
                String prefix = original.substring(0, i);
                String suffix = (i < original.length() - 1) ? original.substring(i + 1) : "";
                if ((prefix + suffix).equals(modified)) {
                    return MethodModificationInformation.ArgumentAmplificationType.STR_REMOVE_CHARACTER;
                }
            }
        }
        // Mutation 4: Double the string
        String doubled = original + original;
        if (doubled.equals(modified)) {
            return MethodModificationInformation.ArgumentAmplificationType.STR_DOUBLE;
        }
        return MethodModificationInformation.ArgumentAmplificationType.NOT_MATCH_ANY_RULE;
    }

    private MethodModificationInformation.ArgumentAmplificationType followsIntegerAmplificationRules(int original, int modified) {
        // Check if modified is 0 (replace with 0)
        if (original != 0 && modified == 0) {
            return MethodModificationInformation.ArgumentAmplificationType.INT_ZERO;
        }
        // Check for +1
        if (modified == original + 1) {
            return MethodModificationInformation.ArgumentAmplificationType.INT_PLUS_ONE;
        }
        // Check for -1
        if (modified == original - 1) {
            return MethodModificationInformation.ArgumentAmplificationType.INT_MINUS_ONE;
        }
        // Check for *2
        if (modified == original * 2) {
            return MethodModificationInformation.ArgumentAmplificationType.INT_MULTIPLICATION_BY_TWO;
        }
        // Check for /2 (ensure original is divisible by 2 and matches modified)
        if (modified == original / 2) {
            return MethodModificationInformation.ArgumentAmplificationType.INT_DIVIDE_BY_TWO;
        }
        return MethodModificationInformation.ArgumentAmplificationType.NOT_MATCH_ANY_RULE;
    }

    private MethodModificationInformation.ArgumentAmplificationType followsBoolAmplificationRules(boolean original, boolean modified) {
        if (modified == !original) {
            return MethodModificationInformation.ArgumentAmplificationType.BOOLEAN_NEGATION;
        }
        return MethodModificationInformation.ArgumentAmplificationType.REMAIN_SAME;
    }

    public void IdentifyArgumentChanges(AssertionCall oldRegularMethod,
                                        AssertionCall modifiedRegularMethod) {

    }
}
