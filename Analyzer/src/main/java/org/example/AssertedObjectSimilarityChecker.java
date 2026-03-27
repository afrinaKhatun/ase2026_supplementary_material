package org.example;

import analyzer.AssertArgument;
import analyzer.AssertionCall;
import analyzer.MethodCall;
import analyzer.MethodCallSignature;
import org.checkerframework.checker.units.qual.A;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class AssertedObjectSimilarityChecker {
    public static double checkAssertedObjectSimilarity(List<AssertionCall> existingMethod, List<AssertionCall> tempMethod){
        List<String> existingAssertedMethods = getAssertedEntity(existingMethod);
        List<String> tempAssertedMethods = getAssertedEntity(tempMethod);
        double similarity = getJaccardSimilarity(existingAssertedMethods,tempAssertedMethods);
        return similarity;

    }
    public static double getJaccardSimilarity(List<String> a, List<String> b) {
        HashSet<String> A = new HashSet<String>(a);
        HashSet<String> B = new HashSet<String>(b);

        if (A.isEmpty() && B.isEmpty()) return 1.0;

        HashSet<String> union = new HashSet<String>(A);
        union.addAll(B);

        HashSet<String> inter = new HashSet<String>(A);
        inter.retainAll(B);

        return (double) inter.size() / (double) union.size();
    }
    public static List<String> getAssertedEntity(List<AssertionCall> assertionCallList){
        List<String> assertedEntities = new ArrayList<>();
        for(AssertionCall astCall : assertionCallList){
            AssertArgument arg = null ;
            String methods="";
            if(astCall.arguments.size()==1) {
                arg = astCall.arguments.get(0);
            }else if(astCall.arguments.size()>1){
                arg = astCall.arguments.get(1);
            }

            if(arg.argumentType=="MTH"){
                methods = methods + arg.receiverType;
                for(MethodCall m:arg.meth_chain.calls){
                    methods = "."+ methods + m.methodName+ "()";

                }
            }
            else if(arg.argumentType == "FLD"){
                methods = methods + arg.receiverType+"."+arg.literal_attribute_methodName;
            }
            else if(arg.argumentType == "NAM"){
                methods = methods+ arg.receiverType;
            }
            assertedEntities.add(methods);
        }
        return assertedEntities;
    }
}
