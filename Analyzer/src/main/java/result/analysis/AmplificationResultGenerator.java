package result.analysis;

import analyzer.PerCommitCoverageJsonHandler;
import analyzer.PerTestCoverage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.CommitRecordJsonHandler;
import org.example.DuplicateMethodInfo;
import org.example.MethodList;
import org.example.MethodModificationInformation;
import tree.analyzer.LLMSimilarityChecker;
import tree.analyzer.LlmRecommendation;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AmplificationResultGenerator {
    public static void main(String[] args){
        String projectName = "commons-io";
        String eq="eq";
        Path result_ndjson = Paths.get("/Users/afrinakhatun/IdeaProjects/Analyzer/"+projectName+"-commit-test-change-history.ndjson");
        List<String> headers = List.of("TestClass","CommitHash", "ParentHash",
                "SourceMethodName","SourceMethodBody",
                "Source-Added-NormalizedBody","AddedMethodBody","AddedMethodName",
                "RemovedCalls","DuplicateCalls","NewCalls","SameCalls",
                "TotalDupArgAmplificationPossible","ActualDupOperatorAmplification",
                "TotalSameArgAmplificationPossible","ActualSameOperatorAmplification",
                "TotalAssertionMethodAmplification","ActualAssertionMethodAmplification",
                "ActualAssertionAttributeAmplification","TotalModifications",
                "Similarity Score", "Assertion Similarity Score",
                "Commit message","Commit Files",
                "LLM Methods","LLM Message");
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(eq+"-AmplificationResult");

        // Create header row
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
        }
        int rowCount = 0;
        AtomicInteger counter = new AtomicInteger(0);
        try{
            CommitRecordJsonHandler.readRecordsStreaming(result_ndjson, commit_record -> {
                try {
                    Map<String, MethodList> methodList = commit_record.method_list;
                            for(Map.Entry<String,MethodList> testClass: methodList.entrySet()){
                                if(!testClass.getValue().previous_methods.isEmpty()){
                                    for(MethodModificationInformation method:testClass.getValue().added_or_modified_methods){
                                        if(method.modification_type.equals("add")){
                                            Set<String> distinctAmplificationSourceMethods = new HashSet<>();
                                            //distinctAmplificationSourceMethods.addAll(method.inputAmplifications.keySet());
                                            //distinctAmplificationSourceMethods.addAll(method.duplicateArgumentAmplification.keySet());
                                            distinctAmplificationSourceMethods.addAll(method.similarityScores.keySet());
                                            List<SortStructure> sortList = new ArrayList<>();
                                            Map<String, String> candidates = new HashMap<>();
                                            for(String distinctMethod : distinctAmplificationSourceMethods){
                                          /*      if(method.similarityScores.get(distinctMethod)<0.7 && method.assertionSimilarityScores.get(distinctMethod)<0.7){
                                                    continue;
                                                }*/
                                                System.out.println(counter);
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
*****/
                                                structure.distinctMethodName = distinctMethod;
                                                structure.distinctMethodBody = testClass.getValue().previous_methods.get(distinctMethod).previousMethodBody;
                                                structure.distinctMethodNormalizedBody = testClass.getValue().previous_methods.get(distinctMethod).previousMethodNormalizedBody;
                                                structure.addedMethodName = method.method_name;
                                                structure.addedMethodBody = method.method_body;
                                                structure.addedMethodNormalizedBody = method.normalized_method_body;
                                                structure.removedMethods = removedMethods;
                                                structure.duplicateMethods = duplicateAddedMethods;
                                                structure.addedMethods = newAddedMethods;
                                                structure.totalModifications = removedMethods + duplicateAddedMethods + newAddedMethods;
                                         //*****       structure.sameMethods = method.sameMethodArgumentAmplification.containsKey(distinctMethod)?method.sameMethodArgumentAmplification.get(distinctMethod).size() : 0;
                                                structure.totalDupArgumentAmp = totalDupArgumentAmp;
                                                structure.actualDupOperatorAmp = actualDupOperatorAmp;
                                                structure.totalSameArgumentAmp = totalSameArgumentAmp;
                                                structure.actualSameOperatorAmp = actualSameOperatorAmp;
                                         //*****       structure.totalAssertionMethodAmplification = method.assertionMethodAmplification.containsKey(distinctMethod)?method.assertionMethodAmplification.get(distinctMethod).size():0;
                                                structure.actualAssertionMethodAmplification = getterMethodAssertion;
                                          //*****      structure.actualAssertionAttributeAmplification = method.assertionAttributeAmplification.containsKey(distinctMethod)?method.assertionAttributeAmplification.get(distinctMethod):0;
                                                structure.similarityScore = method.similarityScores.containsKey(distinctMethod)?method.similarityScores.get(distinctMethod):0;
                                                //structure.assertionSimilarityScore = method.assertionSimilarityScores.containsKey(distinctMethod)?method.assertionSimilarityScores.get(distinctMethod):0;
                                          //*****      structure.extraObjects = method.extraObjects.containsKey(distinctMethod)?method.extraObjects.get(distinctMethod):new HashSet<String>();

                                                sortList.add(structure);

                                                // here was the writing to excel code
                                            }
                                            //Collections.sort(sortList, Comparator.comparingInt(s -> s.totalModifications));
                                            LlmRecommendation recommendation = new LlmRecommendation(new ArrayList<String>(),"No similar methods");
                                            if(candidates.size()>0){
                                                //recommendation = LLMSimilarityChecker.checkLLMSimilarity(method.method_name,method.method_body,candidates);
                                            }

                                            Collections.sort(
                                                    sortList,
                                                    Comparator
                                                            .comparingDouble((SortStructure s) ->
                                                                    Double.isNaN(s.similarityScore) ? Double.NEGATIVE_INFINITY : s.similarityScore
                                                            )
                                                            .reversed()
                                                            .thenComparingInt(s -> s.totalModifications));

                                            for(SortStructure s:sortList){
                                                Row row = sheet.createRow(counter.incrementAndGet());
                                                row.createCell(0).setCellValue(testClass.getKey());
                                                row.createCell(1).setCellValue(commit_record.commit_hash);
                                                row.createCell(2).setCellValue(testClass.getValue().parent_commit_hash);

                                                row.createCell(3).setCellValue(s.distinctMethodName);
                                                row.createCell(4).setCellValue(s.distinctMethodBody);
                                                row.createCell(5).setCellValue(s.distinctMethodNormalizedBody+"\n"+s.addedMethodNormalizedBody);
                                                row.createCell(6).setCellValue(s.addedMethodBody);
                                                row.createCell(7).setCellValue(s.addedMethodName);

                                                row.createCell(8).setCellValue(s.removedMethods);
                                                row.createCell(9).setCellValue(s.duplicateMethods);
                                                row.createCell(10).setCellValue(s.addedMethods);
                                                row.createCell(11).setCellValue(s.sameMethods);
                                                row.createCell(12).setCellValue(s.totalDupArgumentAmp);
                                                row.createCell(13).setCellValue(s.actualDupOperatorAmp);
                                                row.createCell(14).setCellValue(s.totalSameArgumentAmp);
                                                row.createCell(15).setCellValue(s.actualSameOperatorAmp);
                                                row.createCell(16).setCellValue(s.totalAssertionMethodAmplification);
                                                row.createCell(17).setCellValue(s.actualAssertionMethodAmplification);
                                                row.createCell(18).setCellValue(s.actualAssertionAttributeAmplification);
                                                row.createCell(19).setCellValue(s.totalModifications);
                                                row.createCell(20).setCellValue(s.similarityScore);
                                                //row.createCell(21).setCellValue(s.assertionSimilarityScore);
                                                row.createCell(22).setCellValue(commit_record.commit_message);
                                                row.createCell(23).setCellValue(commit_record.commited_files.toString());
                                                row.createCell(24).setCellValue(recommendation.similarMethods.toString());
                                                row.createCell(25).setCellValue(recommendation.observation);

                                            }
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
        try (FileOutputStream fos = new FileOutputStream("/Users/afrinakhatun/IdeaProjects/Analyzer/"+projectName+"-1"+"_Amp_Result.xlsx")) {
            workbook.write(fos);
            workbook.close();
            System.out.println("Excel file created successfully!");
        } catch (IOException e) {
            System.out.println("am in file exception");
            e.printStackTrace();
        }
    }
}
