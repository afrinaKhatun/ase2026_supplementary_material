package result.analysis;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.checkerframework.checker.units.qual.A;
import org.example.CommitRecordJsonHandler;
import org.example.DuplicateMethodInfo;
import org.example.MethodList;
import org.example.MethodModificationInformation;
import tree.analyzer.GumTreeSim;
import tree.analyzer.LLMSimilarityChecker;
import tree.analyzer.LlmRecommendation;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class RandomAnalysis {
    public static void main(String[] args){
        String projectName = "jsoup";
        String eq="eq";
        Path result_ndjson = Paths.get("/Users/afrinakhatun/IdeaProjects/Analyzer/"+projectName+"-commit-test-change-history.ndjson");



        List<AdditionAnalysis> additionList = new ArrayList<>();
        int rowCount = 0;
        AtomicInteger counter = new AtomicInteger(0);
        try{
            CommitRecordJsonHandler.readRecordsStreaming(result_ndjson, commit_record -> {
                try {
                    Map<String, MethodList> methodList = commit_record.method_list;
                    Map<String, Map<String,String>> addedMethodsInThisCommit = new HashMap<>();
                    for(Map.Entry<String,MethodList> testClass: methodList.entrySet()) {
                        if (!testClass.getValue().previous_methods.isEmpty()) {
                            for (MethodModificationInformation method : testClass.getValue().added_or_modified_methods) {
                                if (method.modification_type.equals("add")) {

                                    if(!addedMethodsInThisCommit.containsKey(testClass.getKey())){
                                        Map<String,String> temp = new HashMap<>();
                                        temp.put(method.method_name,method.normalized_method_body);
                                        addedMethodsInThisCommit.put(testClass.getKey(),temp);
                                    }else{
                                        Map<String,String> temp = addedMethodsInThisCommit.get(testClass.getKey());
                                        temp.put(method.method_name,method.normalized_method_body);
                                        addedMethodsInThisCommit.put(testClass.getKey(),temp);
                                    }
                                }
                            }
                        }
                    }

                    for(Map.Entry<String,MethodList> testClass: methodList.entrySet()){
                        if(!addedMethodsInThisCommit.containsKey(testClass.getKey())){
                            continue;
                        }
                        List<List<String>> pairs = getPairs(addedMethodsInThisCommit.get(testClass.getKey()).keySet());
                        if(!testClass.getValue().previous_methods.isEmpty()){
                            for(MethodModificationInformation method:testClass.getValue().added_or_modified_methods){
                                if(method.modification_type.equals("add")){
                                    //if(!method.method_name.equals("testAllElements")){ continue;}

                                    AdditionAnalysis a = new AdditionAnalysis();

                                    Set<String> distinctAmplificationSourceMethods = new HashSet<>();
                                    distinctAmplificationSourceMethods.addAll(method.similarityScores.keySet());


                                    List<SortStructure> sortList = new ArrayList<>();
                                    Map<String, String> candidates = new HashMap<>();
                                    for(String distinctMethod : distinctAmplificationSourceMethods){
                                        if(method.similarityScores.get(distinctMethod)<0.7){
                                            continue;
                                        }
                                        a.numberOfSimilarMethods += 1;


                                        //System.out.println(counter);
                                        candidates.put(distinctMethod,testClass.getValue().previous_methods.get(distinctMethod).previousMethodBody);
                                        int newAddedMethods = 0;
                                        int removedMethods = 0;
                                        int duplicateAddedMethods = 0;
                                        int totalDupArgumentAmp = 0;
                                        int actualDupOperatorAmp = 0;
                                        int totalSameArgumentAmp = 0;
                                        int actualSameOperatorAmp = 0;
                                        int getterMethodAssertion = 0;
                                        SortStructure structure = new SortStructure();
/*****
                                        if(method.inputAmplifications.containsKey(distinctMethod)){
                                            for(String add_or_delete_method : method.inputAmplifications.get(distinctMethod)){
                                                if(add_or_delete_method.startsWith("A-")){
                                                    newAddedMethods += 1;
                                                }
                                                else if(add_or_delete_method.startsWith("D-")){
                                                    removedMethods += 1;
                                                }
                                            }
                                        }
                                        //if(testClass.getValue().previous_methods.get(distinctMethod).noOfMethodCalls == removedMethods){continue;}

                                        if(method.duplicateArgumentAmplification.containsKey(distinctMethod)){
                                            List<DuplicateMethodInfo> dupStatements =  method.duplicateArgumentAmplification.get(distinctMethod);
                                            for(DuplicateMethodInfo ds: dupStatements){
                                                duplicateAddedMethods += 1;
                                                totalDupArgumentAmp += ds.argumentAmplificationType.size();
                                                for(MethodModificationInformation.ArgumentAmplificationType t:ds.argumentAmplificationType){
                                                    if(MethodModificationInformation.operators.contains(t)){
                                                        actualDupOperatorAmp += 1;
                                                    }
                                                }
                                            }
                                        }
                                        if(method.sameMethodArgumentAmplification.containsKey(distinctMethod)){
                                            List<DuplicateMethodInfo> sameStatements =  method.sameMethodArgumentAmplification.get(distinctMethod);
                                            for(DuplicateMethodInfo ss: sameStatements){
                                                totalSameArgumentAmp += ss.argumentAmplificationType.size();
                                                for(MethodModificationInformation.ArgumentAmplificationType t:ss.argumentAmplificationType){
                                                    if(MethodModificationInformation.operators.contains(t)){
                                                        actualSameOperatorAmp+=1;
                                                    }
                                                }
                                            }
                                        }
                                        if(method.assertionMethodAmplification.containsKey(distinctMethod)){
                                            List<Character> methAssertions = method.assertionMethodAmplification.get(distinctMethod);
                                            for(Character c:methAssertions){
                                                if(c.equals('G')){
                                                    getterMethodAssertion += 1;
                                                }
                                            }
                                        }

                                        if(method.assertionMethodAmplification.containsKey(distinctMethod)){
                                            for(Character c:method.assertionMethodAmplification.get(distinctMethod)){
                                                if(c.equals('G')){
                                                    getterMethodAssertion += 1;
                                                }
                                            }
                                        }

                                        if(method.similarityScores.get(distinctMethod) == 1){
                                            a.numberOfExactMatch +=1;
                                            if(method.assertionMethodAmplification.containsKey(distinctMethod)){
                                                if(method.assertionMethodAmplification.get(distinctMethod).size() >=1){
                                                    a.numberOfAssertionAmplification.put(distinctMethod,true);
                                                }
                                            }
                                        }
*****/
                                        for(List<String> pair:pairs){
                                            String p = "";
                                            if(pair.get(0).equals(method.method_name)){
                                                p = pair.get(1);
                                            }
                                            else if(pair.get(1).equals(method.method_name)){
                                                p = pair.get(0);
                                            }
                                            else{
                                                continue;
                                            }
                                            double sim = GumTreeSim.similarityClassic(addedMethodsInThisCommit.get(testClass.getKey()).get(method.method_name),
                                                    addedMethodsInThisCommit.get(testClass.getKey()).get(p) );
                                            if(sim>=0.7){
                                                a.similar_AddedPair.put(p,sim);
                                            }
                                        }

                                        a.methodRemoved.put(distinctMethod,removedMethods);
                                        a.methodNewAdded.put(distinctMethod,newAddedMethods);
                                        a.methodDuplicated.put(distinctMethod,duplicateAddedMethods);
                                        // here was the writing to excel code
                                    }
                                    //Collections.sort(sortList, Comparator.comparingInt(s -> s.totalModifications));
                                    additionList.add(a);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("am in lamda exception");
                    throw new RuntimeException(e);
                }
            });
        }
        catch (Exception e){
            System.out.println("I am here");
            e.printStackTrace();
        }
        int found_multipleSimilarMethods = 0;
        int found_exactSimilar = 0;
        int found_assertionAmplification = 0;
        int found_multipleMethodsBeingAdded = 0;
        int found_similarMethodsBeingAdded = 0;
        int only_removal = 0;
        int only_addition = 0;
        int only_duplicate = 0;
        int combination = 0;
        int total_Modifications = 0;
        System.out.println("Number of additions:"+additionList.size());
        for(AdditionAnalysis aa: additionList){
            if(aa.numberOfSimilarMethods > 1){
                found_multipleSimilarMethods += 1;
            }
            if(aa.numberOfExactMatch > 0){
                found_exactSimilar +=1;
                if(aa.numberOfAssertionAmplification.size() > 0){
                    found_assertionAmplification += 1;
                }
            }
            if(aa.similar_AddedPair.size()>0){
                found_multipleMethodsBeingAdded += 1;
                for(Map.Entry<String,Double> entry: aa.similar_AddedPair.entrySet()){
                    if(entry.getValue()>=0.9){
                        found_similarMethodsBeingAdded += 1;
                        break;
                    }
                }
            }
            total_Modifications += aa.methodRemoved.size();
            for(String ex : aa.methodRemoved.keySet()){
                if(aa.methodRemoved.get(ex) > 0 && aa.methodDuplicated.get(ex)  == 0 && aa.methodNewAdded.get(ex)==0){
                    only_removal +=1;
                }
                else if(aa.methodRemoved.get(ex)== 0 && aa.methodDuplicated.get(ex) > 0 && aa.methodNewAdded.get(ex)==0){
                    only_duplicate +=1;
                }
                else if(aa.methodRemoved.get(ex) == 0 && aa.methodDuplicated.get(ex) == 0 && aa.methodNewAdded.get(ex) > 0){
                    only_addition += 1;
                }
                else{
                    combination +=1;
                }
            }


        }
        System.out.println("Number of cases where multiple similarities found:"+found_multipleSimilarMethods+" "+(found_multipleSimilarMethods/additionList.size()));
        System.out.println("Number of cases where exact Match found:"+found_exactSimilar+" "+(found_exactSimilar/additionList.size()));
        System.out.println("Number of exact cases where assertion amplification found:"+" "+found_assertionAmplification);
        System.out.println("During this addition multiple medthos were added: "+found_multipleMethodsBeingAdded);
        System.out.println("During this addition similar medthos were added: "+found_similarMethodsBeingAdded);
        System.out.println("Similar Methods had only removal : "+only_removal+"/"+total_Modifications);
        System.out.println("Similar Methods had only duplicate : "+only_duplicate +"/"+total_Modifications);
        System.out.println("Similar Methods had only new addition : "+only_addition+"/"+total_Modifications);
        System.out.println("Similar Methods had combination of removal, addition, deletion: "+combination+"/"+total_Modifications);

    }

    public static List<List<String>> getPairs(Set<String> items) {


        List<String> list = new ArrayList<>(items);  // to index it
        List<List<String>> pairs = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                pairs.add(Arrays.asList(list.get(i), list.get(j)));
            }
        }
        return pairs;
    }
}
