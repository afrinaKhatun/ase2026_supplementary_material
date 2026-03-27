package org.example;

import analyzer.AssertArgument;
import analyzer.AssertionCall;
import analyzer.LiteralPreservingNormalizer;
import analyzer.MethodCall;

import java.util.*;


public class AssertionAmplificationDetector {
    public static class AssertionAmplificationResult {
        public Map<String,List<TempAssertionCall>> addedMethodObservations;
        public Map<String,List<TempAssertionCall>> addedAttributeObservations;


        public AssertionAmplificationResult() {
            this.addedMethodObservations = new HashMap<>();
            this.addedAttributeObservations = new HashMap<>();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Added Assertions: ").append(addedMethodObservations).append("\n");
            sb.append("Removed Assertions: ").append(addedAttributeObservations).append("\n");
            return sb.toString();
        }
    }
    public static class TempAssertionCall{
        public String methodOrAttributeCallSequence;
        public AssertionCall assertionCall;
        public TempAssertionCall(String methodOrAttributeCallSequence, AssertionCall assertionCall){
            this.methodOrAttributeCallSequence = methodOrAttributeCallSequence;
            this.assertionCall = assertionCall;
        }
    }

    public AssertionAmplificationResult checkAssertionAmplification(NormalizationResult existing_method_result,
                                                                    NormalizationResult temp_method_result) {
        Map<String, List<TempAssertionCall>> existingMethodObservationsPoints = listMethodCalls(existing_method_result);
        Map<String, List<TempAssertionCall>> tempMethodObservationsPoints = listMethodCalls(temp_method_result);
        Map<String, List<TempAssertionCall>> existingAttributeObservationPoints = listAttributes(existing_method_result);
        Map<String, List<TempAssertionCall>> tempAttributeObservationPoints = listAttributes(temp_method_result);

        AssertionAmplificationResult result = new AssertionAmplificationResult();


        for (Map.Entry<String, List<TempAssertionCall>> entry : tempMethodObservationsPoints.entrySet()) {
            String testedObject = entry.getKey();
            if(existingMethodObservationsPoints.containsKey(testedObject)){
                List<TempAssertionCall> existingMethodCallsOfObject = existingMethodObservationsPoints.get(testedObject);
                List<String> existingMethodCalls = new ArrayList<>();
                for(TempAssertionCall existingAsstCall: existingMethodCallsOfObject){
                    existingMethodCalls.add(existingAsstCall.methodOrAttributeCallSequence);
                }

                for(TempAssertionCall tempAsstCall:entry.getValue()){
                    if(!existingMethodCalls.contains(tempAsstCall.methodOrAttributeCallSequence)){

                        if(result.addedMethodObservations.containsKey(testedObject)){
                            List<TempAssertionCall> calls = result.addedMethodObservations.get(testedObject);
                            calls.add(new TempAssertionCall(tempAsstCall.methodOrAttributeCallSequence,tempAsstCall.assertionCall));

                            result.addedMethodObservations.put(testedObject, calls);
                        }
                        else{
                            List<TempAssertionCall> calls = new ArrayList<>();
                            calls.add(new TempAssertionCall(tempAsstCall.methodOrAttributeCallSequence,tempAsstCall.assertionCall));

                            result.addedMethodObservations.put(testedObject, calls);
                        }
                    }
                }
            }
        }

        for (Map.Entry<String, List<TempAssertionCall>> entry : tempAttributeObservationPoints.entrySet()) {
            String testedObject = entry.getKey();
            if(existingAttributeObservationPoints.containsKey(testedObject)){
                List<TempAssertionCall> existingAttributesOfObject = existingAttributeObservationPoints.get(testedObject);
                List<String> existingAttributes = new ArrayList<>();
                for(TempAssertionCall existingAsstCall: existingAttributesOfObject){
                    existingAttributes.add(existingAsstCall.methodOrAttributeCallSequence);
                }

                for(TempAssertionCall tempAsstCall:entry.getValue()){
                    if(!existingAttributes.contains(tempAsstCall.methodOrAttributeCallSequence)){

                        if(result.addedAttributeObservations.containsKey(testedObject)){
                            List<TempAssertionCall> calls = result.addedAttributeObservations.get(testedObject);
                            calls.add(new TempAssertionCall(tempAsstCall.methodOrAttributeCallSequence,tempAsstCall.assertionCall));

                            result.addedAttributeObservations.put(testedObject, calls);
                        }
                        else{
                            List<TempAssertionCall> calls = new ArrayList<>();
                            calls.add(new TempAssertionCall(tempAsstCall.methodOrAttributeCallSequence,tempAsstCall.assertionCall));

                            result.addedAttributeObservations.put(testedObject, calls);
                        }
                    }
                }
            }
        }

        int x = 0;
        return result;

    }

    public Map<String, List<TempAssertionCall>> listMethodCalls(NormalizationResult method_result) {

        Map<String, List<TempAssertionCall>> observationsPoints = new HashMap<>();
        for (AssertionCall assertionMethod : method_result.assertionMethodCalls) {
            // Argument is only One
            if (assertionMethod.arguments.size() == 1) {
                AssertArgument arg = assertionMethod.arguments.get(0);
                if (arg.argumentType.equals("MTH")) {

                    String methodCalls = "";
                    for (MethodCall mCall : arg.meth_chain.calls) {
                        methodCalls += mCall.methodName + "()";
                    }

                    if (!observationsPoints.containsKey(arg.receiverType)) {
                        List<TempAssertionCall> points = new ArrayList<>();
                        points.add(new TempAssertionCall(methodCalls, assertionMethod));

                        observationsPoints.put(arg.receiverType, points);
                    } else {
                        List<TempAssertionCall> points = observationsPoints.get(arg.receiverType);
                        points.add(new TempAssertionCall(methodCalls, assertionMethod));

                        observationsPoints.put(arg.receiverType, points);
                    }
                }
            }
            // Argument is more than 1
            else if (assertionMethod.arguments.size() > 1) {
                for (int i = 1; i < assertionMethod.arguments.size(); i++) {
                    AssertArgument arg = assertionMethod.arguments.get(i);
                    String methodCalls = "";
                    if (arg.argumentType.equals("MTH")) {

                        for (MethodCall mCall : arg.meth_chain.calls) {
                            methodCalls += mCall.methodName + "()";
                        }
                        if (!observationsPoints.containsKey(arg.receiverType)) {
                            List<TempAssertionCall> points = new ArrayList<>();
                            points.add(new TempAssertionCall(methodCalls, assertionMethod));

                            observationsPoints.put(arg.receiverType, points);
                        } else {
                            List<TempAssertionCall> points = observationsPoints.get(arg.receiverType);
                            points.add(new TempAssertionCall(methodCalls, assertionMethod));

                            observationsPoints.put(arg.receiverType, points);
                        }
                    }
                }
            }

        }
        return observationsPoints;
    }

    public Map<String, List<TempAssertionCall>> listAttributes(NormalizationResult method_result) {
        Map<String, List<TempAssertionCall>> observationsPoints = new HashMap<>();
        for (AssertionCall assertionMethod : method_result.assertionMethodCalls) {
            // Argument is only One
            if (assertionMethod.arguments.size() == 1) {
                AssertArgument arg = assertionMethod.arguments.get(0);
                if (arg.argumentType.equals("FLD")) {
                    String methodCalls = arg.literal_attribute_methodName;

                    if (!observationsPoints.containsKey(arg.receiverType)) {
                        List<TempAssertionCall> points= new ArrayList<>();
                        points.add(new TempAssertionCall(methodCalls, assertionMethod));

                        observationsPoints.put(arg.receiverType, points);
                    } else {
                        List<TempAssertionCall> points = observationsPoints.get(arg.receiverType);
                        points.add(new TempAssertionCall(methodCalls, assertionMethod));

                        observationsPoints.put(arg.receiverType, points);
                    }
                }
            }
            // Argument is more than 1
            else if (assertionMethod.arguments.size() > 1) {
                for (int i = 1; i < assertionMethod.arguments.size(); i++) {
                    AssertArgument arg = assertionMethod.arguments.get(i);
                    String methodCalls = "";
                    if (arg.argumentType.equals("FLD")) {
                        methodCalls = arg.literal_attribute_methodName;

                        if (!observationsPoints.containsKey(arg.receiverType)) {
                            List<TempAssertionCall> points= new ArrayList<>();
                            points.add(new TempAssertionCall(methodCalls, assertionMethod));

                            observationsPoints.put(arg.receiverType, points);
                        } else {
                            List<TempAssertionCall> points = observationsPoints.get(arg.receiverType);
                            points.add(new TempAssertionCall(methodCalls, assertionMethod));

                            observationsPoints.put(arg.receiverType, points);
                        }
                    }
                }
            }

        }
        return observationsPoints;
    }
}
