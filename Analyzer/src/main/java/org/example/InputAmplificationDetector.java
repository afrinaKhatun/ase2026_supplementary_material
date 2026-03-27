package org.example;

import analyzer.LiteralPreservingNormalizer;
import analyzer.MethodCall;
import analyzer.MethodCallSignature;
import org.checkerframework.checker.units.qual.A;

import java.util.*;
import java.util.stream.Collectors;

public class InputAmplificationDetector {
    public class MethodCallAmplificationResult {
        public List<MethodCallSignature> deletedCalls;
        public List<List<MethodCallSignature>> duplicatedCalls;
        public List<MethodCallSignature> addedCalls;
        public List<List<MethodCallSignature>> sameCalls;

        public MethodCallAmplificationResult(List<MethodCallSignature> deletedCalls,
                                             List<MethodCallSignature> addedCalls,
                                             List<List<MethodCallSignature>> duplicatedCalls,
                                             List<List<MethodCallSignature>> sameCalls) {
            this.deletedCalls = deletedCalls;
            this.duplicatedCalls = duplicatedCalls;
            this.addedCalls = addedCalls;
            this.sameCalls = sameCalls;
        }

        private String formatCalls(List<MethodCallSignature> calls) {
            return calls.stream()
                    .map(sig -> sig.receiverName + "." + sig.calls.get(0).toString())
                    .collect(Collectors.joining(", ", "[", "]"));
        }

        @Override
        public String toString() {
            return "MethodCallAmplificationResult{\n" +
                    "  deletedCalls=" + formatCalls(deletedCalls) + ",\n" +
                    "  duplicatedCalls=" + duplicatedCalls.toString() + ",\n" +
                    "  addedCalls=" + formatCalls(addedCalls) + "\n}" + ",\n"+
                    "  sameCalls=" + sameCalls.toString() + "\n}";
        }
    }

    public MethodCallAmplificationResult checkMethodCallAmplification(NormalizationResult existing_method_result,
                                                                      NormalizationResult temp_method_result){
        Map<String,List<MethodCallSignature>> exisitng_method_calls = new HashMap<>();
        Map<String,List<MethodCallSignature>> temp_method_calls = new HashMap<>();
        List<MethodCallSignature> removedCalls = new ArrayList<>();
        List<List<MethodCallSignature>> duplicatedCalls = new ArrayList<>();
        List<MethodCallSignature> addedCalls = new ArrayList<>();
        List<List<MethodCallSignature>> sameCalls = new ArrayList<>();

        for(MethodCallSignature obj : existing_method_result.regularMethodCalls){
            if(obj.isFromAssertion){continue;}
            String callSequence = "";
            for(MethodCall call : obj.calls){
                callSequence+= "."+call.methodName+"()";
            }
            exisitng_method_calls.computeIfAbsent(obj.receiverType+callSequence, k -> new ArrayList<>()).add(obj);
        }
        for(MethodCallSignature obj : temp_method_result.regularMethodCalls){
            if(obj.isFromAssertion){continue;}
            String callSequence = "";
            for(MethodCall call : obj.calls){
                callSequence+= "."+call.methodName+"()";
            }
            temp_method_calls.computeIfAbsent(obj.receiverType+callSequence, k -> new ArrayList<>()).add(obj);
        }
        // Detect deleted calls (absent or reduced count in modified)
        for (Map.Entry<String, List<MethodCallSignature>> entry : exisitng_method_calls.entrySet()) {
            String call = entry.getKey();
            List<MethodCallSignature> originalSig = entry.getValue();
            List<MethodCallSignature> modifiedSig = temp_method_calls.getOrDefault(call, Collections.emptyList());
            if (originalSig.size() > modifiedSig.size()) {
                int removes = originalSig.size() - modifiedSig.size();
                // Add the call to deletedCalls for each instance removed
                for (int i = 0; i < modifiedSig.size(); i++) {
                    //removedCalls.add(originalSig.get(i));
                    List<MethodCallSignature> samePair = new ArrayList<>();
                    samePair.add(originalSig.get(i));
                    samePair.add(modifiedSig.get(i));
                    sameCalls.add(samePair);
                }
                for(int k=modifiedSig.size();k<originalSig.size();k++){
                    removedCalls.add(originalSig.get(k));
                }
            }
        }
        // Detect duplicated and added calls
        for (Map.Entry<String, List<MethodCallSignature>> entry : temp_method_calls.entrySet()) {
            String call = entry.getKey();
            List<MethodCallSignature> modifiedSig = entry.getValue();
            List<MethodCallSignature> originalSig = exisitng_method_calls.getOrDefault(call, Collections.emptyList());

            if (originalSig.size() == 0) {
                // New call (added)
                for (int i = 0; i < modifiedSig.size(); i++) {
                    addedCalls.add(modifiedSig.get(i));
                }
            } else if (modifiedSig.size() > originalSig.size()) {
                // Duplicated call
                if(originalSig.size() == 1){
                    for (int i = 1; i < modifiedSig.size() ; i++) {
                        List<MethodCallSignature> duplicatePair = new ArrayList<>();
                        duplicatePair.add(originalSig.get(0));
                        duplicatePair.add(modifiedSig.get(i));
                        duplicatedCalls.add(duplicatePair);
                    }
                }
                else{
                    int extras = modifiedSig.size()-originalSig.size();
                    for(int j=0;j<originalSig.size();j++){
                            List<MethodCallSignature> samePair = new ArrayList<>();
                            samePair.add(originalSig.get(j));
                            samePair.add(modifiedSig.get(j));
                            sameCalls.add(samePair);
                    }
                    for(int k=originalSig.size();k<modifiedSig.size();k++){
                        List<MethodCallSignature> duplicatePair = new ArrayList<>();
                        duplicatePair.add(originalSig.get(originalSig.size()-1));
                        duplicatePair.add(modifiedSig.get(k));
                        duplicatedCalls.add(duplicatePair);
                    }
                }
            }
            else if(modifiedSig.size() == originalSig.size()){
                for(int j=0;j<originalSig.size();j++){
                        List<MethodCallSignature> samePair = new ArrayList<>();
                        samePair.add(originalSig.get(j));
                        samePair.add(modifiedSig.get(j));
                        sameCalls.add(samePair);
                }
            }
        }
        return new MethodCallAmplificationResult(removedCalls, addedCalls, duplicatedCalls, sameCalls);
    }

    public void checkArgumentAmplification(){

    }

}
