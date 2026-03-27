package result.analysis;

import net.bytebuddy.TypeCache;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.checkerframework.checker.units.qual.A;
import org.example.*;
import tree.analyzer.LlmRecommendation;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.abs;

public class SetSimilarityAnalysis {
    public static void main(String[] args){
        String projectName = "commons-cli";

        Path result_ndjson = Paths.get("/Users/afrinakhatun/IdeaProjects/Analyzer/ParsedJsons/"+projectName+"-commit-test-change-history.ndjson");

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("SetResult");
        List<String> headers = List.of("TestClass","CommitHash", "ParentHash",
                "SourceMethodName","SourceMethodBody",
                "Source-Added-NormalizedBody","Source-Added-LiteralBody","MethodSets","AddedMethodBody", "AddedMethodName",
                "Similarity Score", "ExistingOrModified","BodyEditDistance", "Selector","Position Distance","NoOfMethodsInFile","Source-Added Position","NewMethodsTested",
                "MethAdded Count","Added", "MethRemoved Count","Removed","Duplicated Count","Duplicated", "Reduced Count","Reduced","Tags",
                "Commit Message","Committed Files","hasSourceChange");
        // Create header row of SetResult sheet
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
        }

        Sheet peerSimilaritySheet = workbook.createSheet("PeerSimilarity");
        List<String> peerHeaders = List.of("TestClass","CommitHash", "ParentHash",
                "Method1Name","M1Body-M2Body","M1-M2-NormalizedBody",
                "Method2Name",
                "JaccardSimilarity", "BodyEditDistance","PositionDistance", "clusters","UncommonMethods");
        // Create header row of PeerSimilarity sheet
        Row peerSimilarityHeaderRow = peerSimilaritySheet.createRow(0);
        for (int i = 0; i < peerHeaders.size(); i++) {
            Cell cell = peerSimilarityHeaderRow.createCell(i);
            cell.setCellValue(peerHeaders.get(i));
        }


        AtomicInteger noOfCommits = new AtomicInteger(0);
        AtomicInteger commitsWith_AtleastOneTestCaseAdded = new AtomicInteger(0);
        AtomicInteger commitsWithout_AnyTestCaseAdded = new AtomicInteger(0);
        AtomicInteger commitsWithAtleastOneAddHavingAmplifiedTestCase = new AtomicInteger(0);
        AtomicInteger numberOfAddedMethods = new AtomicInteger(0);
        AtomicInteger numberOfAddedMethodsWhich_AreAmplified = new AtomicInteger(0);
        AtomicInteger numberOfAddedMethods_WhichTests_NewMethods = new AtomicInteger(0);
        AtomicInteger numberOfAddedMethods_WhichTests_No_NewMethods = new AtomicInteger(0);
        AtomicInteger numberOfAmplified_Methods_WhichTests_NewMethods = new AtomicInteger(0);
        AtomicInteger numberOfAmplified_Methods_WhichTests_No_NewMethods = new AtomicInteger(0);


        AtomicInteger numberOf_IDN = new AtomicInteger(0);
        AtomicInteger numberOfORD = new AtomicInteger(0);
        AtomicInteger numberOf_Only_DUP_ADD_Single = new AtomicInteger(0);
        AtomicInteger numberOf_Only_DUP_ADD_MUL = new AtomicInteger(0);
        AtomicInteger numberOf_Only_DUP_Reduce_Single = new AtomicInteger(0);
        AtomicInteger numberOf_Only_DUP_Reduce_Mul = new AtomicInteger(0);
        AtomicInteger numberOf_Only_ADD_Single = new AtomicInteger(0);
        AtomicInteger numberOf_Only_ADD_Mul = new AtomicInteger(0);
        AtomicInteger numberOf_Only_Remove_Single = new AtomicInteger(0);
        AtomicInteger numberOf_Only_Remove_Mul = new AtomicInteger(0);
        AtomicInteger numberOf_Only_ADD_Single_REM_Single = new AtomicInteger(0);
        AtomicInteger numberOf_Only_ADD_REM_Mul = new AtomicInteger(0);
        AtomicInteger numberOf_Only_ADD_with_Dup = new AtomicInteger(0);
        AtomicInteger numberOf_Only_Remove_with_Dup = new AtomicInteger(0);
        AtomicInteger cases_Covered = new AtomicInteger(0);

        AtomicInteger numberOf_TestclassesWhereMultipleMethodsAdded_Or_Modified = new AtomicInteger(0);
        AtomicInteger numberOf_TestclassesWhereMethodsAdded_Have_One_Similar_AddedMethod = new AtomicInteger(0);
        AtomicInteger numberOf_TestclassesWhereMethodsAdded_IS_SimilarTo_ModifiedMethod = new AtomicInteger(0);




        AtomicInteger counter = new AtomicInteger(0);
        AtomicInteger peerCounter = new AtomicInteger(0);
        try{
            CommitRecordJsonHandler.readRecordsStreaming(result_ndjson, commit_record -> {
                noOfCommits.incrementAndGet();
                /*if(!commit_record.commit_hash.equals("03027d8ff28929e3e5a618a31e9bcab0c90d0c0b")){
                    return;
                }*/
                try {
                    int atleastOneTestCaseAdded_Flag  = 0; int flag1 = 0;
                    Map<String, MethodList> methodList = commit_record.method_list;

                    for(Map.Entry<String,MethodList> testClass: methodList.entrySet()){
                        int modified=0;
                        for(MethodModificationInformation method:testClass.getValue().added_or_modified_methods){
                            if(method.modification_type.equals("mod")){
                                modified = modified+1;
                                break;
                            }
                        }

                        if(!testClass.getValue().previous_methods.isEmpty() || modified!=0){
                            for(MethodModificationInformation method:testClass.getValue().added_or_modified_methods){
                                if(method.modification_type.equals("add")){
                                    if(atleastOneTestCaseAdded_Flag == 0){
                                        commitsWith_AtleastOneTestCaseAdded.incrementAndGet();
                                        atleastOneTestCaseAdded_Flag = atleastOneTestCaseAdded_Flag +1;
                                    }
                                    numberOfAddedMethods.incrementAndGet();


                                    Set<String> distinctAmplificationSourceMethods = new HashSet<>();
                                    //distinctAmplificationSourceMethods.addAll(method.inputAmplifications.keySet());
                                    //distinctAmplificationSourceMethods.addAll(method.duplicateArgumentAmplification.keySet());
                                    distinctAmplificationSourceMethods.addAll(method.similarityScores.keySet());
                                    List<SortStructure> sortList = new ArrayList<>();
                                    Map<String, String> candidates = new HashMap<>();
                                    int flag3 = 0; int newMethodTestedFlag = 0;
                                    int extremeAcceptFlag = 0; int boundaryAcceptFlag=0;
                                    // checking the added method with every existing method only
                                    for(String distinctMethod : distinctAmplificationSourceMethods){
                                        //boolean oneMetricSupports = false;
                                        String selector = "";

                                       boolean acceptAnd = ((method.methodBodyEditDistanceScores.get(distinctMethod) <= 0.21 && method.similarityScores.get(distinctMethod) >= 0.69));
                                       boolean acceptOr = ((method.methodBodyEditDistanceScores.get(distinctMethod) <= 0.21 || method.similarityScores.get(distinctMethod) >= 0.69));

                                        boolean acceptExtreme =  (method.similarityScores.get(distinctMethod) >= 0.80) || (method.methodBodyEditDistanceScores.get(distinctMethod) <= 0.10);
                                       if(!(acceptAnd || acceptExtreme)){
                                           continue;
                                        }

                                        if(method.newMethodsTested.size()>0){
                                            numberOfAddedMethods_WhichTests_NewMethods.incrementAndGet();
                                        }
                                        else{
                                            numberOfAddedMethods_WhichTests_No_NewMethods.incrementAndGet();
                                        }

                                        if(flag1 == 0){
                                            commitsWithAtleastOneAddHavingAmplifiedTestCase.incrementAndGet();
                                            flag1 = flag1 + 1;
                                        }
                                        if(flag3 == 0){
                                            numberOfAddedMethodsWhich_AreAmplified.incrementAndGet();
                                            flag3 = flag3 + 1;
                                        }
                                        if(newMethodTestedFlag == 0){
                                            if(method.newMethodsTested.size() > 0 ){
                                                numberOfAmplified_Methods_WhichTests_NewMethods.incrementAndGet();
                                            }
                                            else{
                                                numberOfAmplified_Methods_WhichTests_No_NewMethods.incrementAndGet();
                                            }
                                            newMethodTestedFlag = 1;
                                        }

                                        //candidates.put(distinctMethod,testClass.getValue().previous_methods.get(distinctMethod).previousMethodBody);

                                        SortStructure structure = new SortStructure();
                                        structure.distinctMethodName = distinctMethod;
                                        structure.distinctMethodBody = testClass.getValue().previous_methods.get(distinctMethod).previousMethodBody;
                                        structure.distinctMethodNormalizedBody = testClass.getValue().previous_methods.get(distinctMethod).previousMethodNormalizedBody;
                                        structure.distinctFullMethodNormalizedBody = testClass.getValue().previous_methods.get(distinctMethod).literal_value_only_normalized_body;
                                        structure.addedMethodName = method.method_name;
                                        structure.addedMethodBody = method.method_body;
                                        structure.addedMethodNormalizedBody = method.normalized_method_body;
                                        structure.addedFullMethodNormalizedBody= method.literal_value_only_normalized_body;
                                        structure.similarityScore = method.similarityScores.containsKey(distinctMethod)?method.similarityScores.get(distinctMethod):0;
                                        structure.category = method.amplification_category.getOrDefault(distinctMethod, null);
                                        structure.methodNameSimilarity = MethodNameSimilarityAnalysis.jaccardSimilarityForMethodNames(method.method_name, distinctMethod);
                                        //structure.editDistance = method.editDistanceScores.containsKey(distinctMethod)?method.editDistanceScores.get(distinctMethod):0;
                                        structure.bodyEditDistance = method.methodBodyEditDistanceScores.containsKey(distinctMethod)?method.methodBodyEditDistanceScores.get(distinctMethod):0;
                                        //structure.positionDistance = method.positionDistanceScores.containsKey(distinctMethod)?abs(method.positionDistanceScores.get(distinctMethod)):2000;
                                        structure.positionDistance = getPositionDistance(testClass.getValue().afterOrderedMethods,distinctMethod,method.method_name,testClass.getValue().added_or_modified_methods);
                                        structure.sourceAddedPositions = getSourceAndAddedPositions(testClass.getValue().afterOrderedMethods,distinctMethod,method.method_name,testClass.getValue().added_or_modified_methods);
                                        structure.noOfExistingMethodsInFile = testClass.getValue().afterOrderedMethods.size();
                                        structure.existingOrModified = "EXT";

                                        structure.newMethodsTested = method.newMethodsTested;
                                        structure.selector = selector;
                                        //*****   structure.severity = method.amplification_severity.getOrDefault(distinctMethod, "could not find");
                                        //structure.normalizedScore = score;
                                        sortList.add(structure);
                                        // here was the writing to excel code
                                    }
                                   //now checking it with modified method
                                    for(MethodModificationInformation modifiedMethod:testClass.getValue().added_or_modified_methods){
                                        if(modifiedMethod.modification_type.equals("mod")){
                                            boolean acceptAnd = ((method.modifiedPeerScores.get(modifiedMethod.method_name).fullBodyEditDistance <= 0.21 && method.modifiedPeerScores.get(modifiedMethod.method_name).jaccardSimilarity >= 0.69));
                                            boolean acceptOr = ((method.modifiedPeerScores.get(modifiedMethod.method_name).fullBodyEditDistance <= 0.21 || method.modifiedPeerScores.get(modifiedMethod.method_name).jaccardSimilarity >= 0.69));

                                            boolean acceptExtreme =  (method.modifiedPeerScores.get(modifiedMethod.method_name).jaccardSimilarity >= 0.80) || (method.modifiedPeerScores.get(modifiedMethod.method_name).fullBodyEditDistance <= 0.10);
                                            if(!(acceptAnd || acceptExtreme)){
                                                continue;
                                            }

                                            if(method.newMethodsTested.size()>0){
                                                numberOfAddedMethods_WhichTests_NewMethods.incrementAndGet();
                                            }
                                            else{
                                                numberOfAddedMethods_WhichTests_No_NewMethods.incrementAndGet();
                                            }

                                            if(flag1 == 0){
                                                commitsWithAtleastOneAddHavingAmplifiedTestCase.incrementAndGet();
                                                flag1 = flag1 + 1;
                                            }
                                            if(flag3 == 0){
                                                numberOfAddedMethodsWhich_AreAmplified.incrementAndGet();
                                                flag3 = flag3 + 1;
                                            }
                                            if(newMethodTestedFlag == 0){
                                                if(method.newMethodsTested.size() > 0 ){
                                                    numberOfAmplified_Methods_WhichTests_NewMethods.incrementAndGet();
                                                }
                                                else{
                                                    numberOfAmplified_Methods_WhichTests_No_NewMethods.incrementAndGet();
                                                }
                                                newMethodTestedFlag = 1;
                                            }

                                            //candidates.put(distinctMethod,testClass.getValue().previous_methods.get(distinctMethod).previousMethodBody);

                                            SortStructure structure = new SortStructure();
                                            structure.distinctMethodName = modifiedMethod.method_name;
                                            structure.distinctMethodBody = modifiedMethod.method_body;
                                            structure.distinctMethodNormalizedBody = modifiedMethod.normalized_method_body;
                                            structure.distinctFullMethodNormalizedBody = modifiedMethod.literal_value_only_normalized_body;
                                            structure.addedMethodName = method.method_name;
                                            structure.addedMethodBody = method.method_body;
                                            structure.addedMethodNormalizedBody = method.normalized_method_body;
                                            structure.addedFullMethodNormalizedBody= method.literal_value_only_normalized_body;
                                            structure.similarityScore = method.modifiedPeerScores.containsKey(modifiedMethod.method_name)?method.modifiedPeerScores.get(modifiedMethod.method_name).jaccardSimilarity:0;
                                            structure.category = method.amplification_category.getOrDefault(modifiedMethod.method_name, null);
                                            structure.methodNameSimilarity = MethodNameSimilarityAnalysis.jaccardSimilarityForMethodNames(method.method_name, modifiedMethod.method_name);
                                            //structure.editDistance = method.editDistanceScores.containsKey(distinctMethod)?method.editDistanceScores.get(distinctMethod):0;
                                            structure.bodyEditDistance = method.modifiedPeerScores.containsKey(modifiedMethod.method_name)?method.modifiedPeerScores.get(modifiedMethod.method_name).fullBodyEditDistance:0;
                                            //structure.positionDistance = method.positionDistanceScores.containsKey(distinctMethod)?abs(method.positionDistanceScores.get(distinctMethod)):2000;
                                            structure.positionDistance = getPositionDistance(testClass.getValue().afterOrderedMethods,modifiedMethod.method_name,method.method_name,testClass.getValue().added_or_modified_methods);
                                            structure.sourceAddedPositions = getSourceAndAddedPositions(testClass.getValue().afterOrderedMethods,modifiedMethod.method_name,method.method_name,testClass.getValue().added_or_modified_methods);
                                            structure.noOfExistingMethodsInFile = testClass.getValue().afterOrderedMethods.size();
                                            structure.existingOrModified = "MOD";

                                            structure.newMethodsTested = method.newMethodsTested;
                                            //structure.selector = selector;
                                            //*****   structure.severity = method.amplification_severity.getOrDefault(distinctMethod, "could not find");
                                            //structure.normalizedScore = score;
                                            sortList.add(structure);
                                            // here was the writing to excel code

                                        }
                                    }


                                    Collections.sort(
                                            sortList,
                                            Comparator
                                                    .comparingDouble((SortStructure s) ->
                                                            -(Double.isNaN(s.similarityScore) ? Double.NEGATIVE_INFINITY : s.similarityScore)
                                                    )
                                                    .thenComparingDouble((SortStructure s) ->
                                                            Double.isNaN(s.bodyEditDistance) ? Double.POSITIVE_INFINITY : s.bodyEditDistance
                                                    )




                                                    /*.thenComparingInt(s ->
                                                            s.positionDistance < 0 ? Integer.MAX_VALUE : s.positionDistance
                                                    )*/
                                    );

                                    if(sortList.size()>0) {
                                        List<SortStructure> onlyFirstElementList = new ArrayList<>();
                                         onlyFirstElementList.add(sortList.get(0));
                                        for (SortStructure s : onlyFirstElementList) {
                                            String combinedMethodBodies = s.distinctMethodBody + "\n" + s.addedMethodBody;
                                            if(combinedMethodBodies.length() > 32767){
                                                combinedMethodBodies="too long";
                                            }
                                            String combinedFullMethodBodies = s.distinctFullMethodNormalizedBody + "\n" + s.addedFullMethodNormalizedBody;
                                            if(combinedFullMethodBodies.length() > 32767){
                                                combinedFullMethodBodies="too long";
                                            }
                                            String combinedArrayBodies = s.distinctMethodNormalizedBody + "\n" + s.addedMethodNormalizedBody;
                                            if(combinedArrayBodies.length() > 32767){
                                                combinedArrayBodies="too long";
                                            }

                                            Row row = sheet.createRow(counter.incrementAndGet());
                                            row.createCell(0).setCellValue(testClass.getKey());
                                            row.createCell(1).setCellValue(commit_record.commit_hash);
                                            row.createCell(2).setCellValue(testClass.getValue().parent_commit_hash);

                                            row.createCell(3).setCellValue(s.distinctMethodName);
                                            row.createCell(4).setCellValue(s.distinctMethodBody.length()>32767?"":s.distinctMethodBody);
                                            row.createCell(5).setCellValue(combinedMethodBodies);
                                            row.createCell(6).setCellValue(combinedFullMethodBodies);
                                            row.createCell(7).setCellValue(combinedArrayBodies);
                                            row.createCell(8).setCellValue(s.addedMethodBody.length()>32767?"":s.addedMethodBody);
                                            row.createCell(9).setCellValue(s.addedMethodName);


                                            row.createCell(10).setCellValue(s.similarityScore);
                                            //row.createCell(10).setCellValue(s.methodNameSimilarity);
                                            row.createCell(11).setCellValue(s.existingOrModified);
                                            row.createCell(12).setCellValue(s.bodyEditDistance);
                                            row.createCell(13).setCellValue(s.selector);
                                            row.createCell(14).setCellValue(s.positionDistance);
                                            row.createCell(15).setCellValue(s.noOfExistingMethodsInFile);
                                            row.createCell(16).setCellValue(s.sourceAddedPositions);
                                            row.createCell(17).setCellValue(s.newMethodsTested.toString());
                                            //row.createCell(17).setCellValue(s.category);

                                            JavapClassificationResult classification = s.category;

                                            //Map<String,List<String>> formatCategory = extractCategory(s.category);
                                            //Added methods

                                            if(classification == null){
                                                row.createCell(18).setCellValue(0);
                                                row.createCell(19).setCellValue("N/A");
                                                //Removed methods
                                                row.createCell(20).setCellValue(0);
                                                row.createCell(21).setCellValue("N/A");
                                                //Duplicated Calls
                                                row.createCell(22).setCellValue(0);
                                                row.createCell(23).setCellValue("N/A");
                                                //Reduced Calls
                                                row.createCell(24).setCellValue(0);
                                                row.createCell(25).setCellValue("N/A");
                                                row.createCell(26).setCellValue("N/A");
                                            }
                                            else{
                                                row.createCell(18).setCellValue(classification.addedKinds.size());
                                                row.createCell(19).setCellValue(classification.addedKinds.toString());
                                                //Removed methods
                                                row.createCell(20).setCellValue(classification.removedKinds.size());
                                                row.createCell(21).setCellValue(classification.removedKinds.toString());
                                                //Duplicated Calls
                                                row.createCell(22).setCellValue(classification.increasedCommonKinds.size());
                                                row.createCell(23).setCellValue(classification.increasedCommonKinds.toString());
                                                //Reduced Calls
                                                row.createCell(24).setCellValue(classification.decreasedCommonKinds.size());
                                                row.createCell(25).setCellValue(classification.decreasedCommonKinds.toString());
                                                row.createCell(26).setCellValue(classification.finalTags.toString());
                                            }




                                            row.createCell(27).setCellValue(commit_record.commit_message);
                                            if(commit_record.commited_files.toString().length() > 32767){
                                                List<String> filteredFiles = new ArrayList<>();
                                                for(String file: commit_record.commited_files){
                                                    if(file.endsWith(testClass.getKey().replace('.','/')+".java")){
                                                        filteredFiles.add(file);
                                                    }

                                                }
                                                row.createCell(28).setCellValue(filteredFiles.toString());
                                            }
                                            else{
                                                row.createCell(28).setCellValue(commit_record.commited_files.toString());
                                            }

                                            row.createCell(29).setCellValue(hasSourceChanges(commit_record.commited_files));


                                        }
                                    }
// ANALYSIS   // ANALYSIS    // ANALYSIS    // ANALYSIS   // ANALYSIS// ANALYSIS  // ANALYSIS   // ANALYSIS
                                    if(sortList.size()>0){
                                        JavapClassificationResult classification = sortList.get(0).category;
                                        if(classification == null){
                                            continue;
                                        }
                                        if(classification.finalTags.contains(JavapClassificationResult.FinalTag.IDEN)){
                                           numberOf_IDN.incrementAndGet();
                                           cases_Covered.incrementAndGet();
                                        }
                                        if(classification.removedKinds.size()==0 &&
                                                classification.increasedCommonKinds.size() == 0 &&
                                                classification.decreasedCommonKinds.size() == 0 &&
                                                classification.addedKinds.size()>0){
                                            if(classification.addedKinds.size()==1){
                                                numberOf_Only_ADD_Single.incrementAndGet();
                                                cases_Covered.incrementAndGet();
                                            }
                                            else {
                                                numberOf_Only_ADD_Mul.incrementAndGet();
                                                cases_Covered.incrementAndGet();
                                            }
                                        }
                                        if(classification.removedKinds.size()>0 &&
                                                classification.increasedCommonKinds.size() == 0 &&
                                                classification.decreasedCommonKinds.size() == 0 &&
                                                classification.addedKinds.size() == 0){
                                            if(classification.removedKinds.size() == 1){
                                                numberOf_Only_Remove_Single.incrementAndGet();
                                                cases_Covered.incrementAndGet();
                                            }
                                            else{
                                                numberOf_Only_Remove_Mul.incrementAndGet();
                                                cases_Covered.incrementAndGet();
                                            }

                                        }
                                        if(classification.addedKinds.size()>0
                                        && classification.removedKinds.size()>0){
                                            if(classification.addedKinds.size() == 1 && classification.removedKinds.size()==1){
                                                numberOf_Only_ADD_Single_REM_Single.incrementAndGet();
                                                cases_Covered.incrementAndGet();
                                            }
                                            else{
                                                numberOf_Only_ADD_REM_Mul.incrementAndGet();
                                                cases_Covered.incrementAndGet();
                                            }
                                        }
                                        if(classification.removedKinds.size()==0 &&
                                                classification.increasedCommonKinds.size() > 0 &&
                                                classification.decreasedCommonKinds.size() == 0 &&
                                                classification.addedKinds.size() == 0){
                                            if(classification.increasedCommonKinds.size() == 1){
                                                numberOf_Only_DUP_ADD_Single.incrementAndGet();
                                                cases_Covered.incrementAndGet();
                                            }
                                            else{
                                                numberOf_Only_DUP_ADD_MUL.incrementAndGet();
                                                cases_Covered.incrementAndGet();
                                            }

                                        }
                                        if(classification.removedKinds.size()==0 &&
                                                classification.increasedCommonKinds.size() == 0 &&
                                                classification.decreasedCommonKinds.size() > 0 &&
                                                classification.addedKinds.size() == 0){
                                            if(classification.decreasedCommonKinds.size() == 1){
                                                numberOf_Only_DUP_Reduce_Single.incrementAndGet();
                                                cases_Covered.incrementAndGet();
                                            }
                                            else{
                                                numberOf_Only_DUP_Reduce_Mul.incrementAndGet();
                                                cases_Covered.incrementAndGet();
                                            }

                                        }
                                        if(classification.removedKinds.size()==0 &&
                                                (classification.increasedCommonKinds.size() > 0 ||
                                                classification.decreasedCommonKinds.size() > 0) &&
                                                classification.addedKinds.size() > 0){
                                            numberOf_Only_ADD_with_Dup.incrementAndGet();
                                            cases_Covered.incrementAndGet();

                                        }
                                        if(classification.removedKinds.size()>0 &&
                                                (classification.increasedCommonKinds.size() > 0 ||
                                                        classification.decreasedCommonKinds.size() > 0) &&
                                                classification.addedKinds.size()== 0){
                                            numberOf_Only_Remove_with_Dup.incrementAndGet();
                                            cases_Covered.incrementAndGet();

                                        }
                                    }


                                }
                            }
                        }

                        // ANALYSIS  // ANALYSIS  // ANALYSIS    // ANALYSIS // ANALYSIS  // ANALYSIS  // ANALYSIS  // ANALYSIS  // ANALYSIS
                        //PeerSimilarity Sheet
                        int multipleAdds = 0;
                        for(MethodModificationInformation addedMethod: testClass.getValue().added_or_modified_methods){
                            if(addedMethod.modification_type.equals("add")){
                                multipleAdds = multipleAdds +1;
                                if(multipleAdds==2){
                                    break;
                                }
                            }
                        }
                        if(multipleAdds>=2){
                            numberOf_TestclassesWhereMultipleMethodsAdded_Or_Modified.incrementAndGet();
                            Set<String> completedMethods = new HashSet<>();
                            int similarAddedMethodFlag = 0;
                            List<SortStructure> peerSortList = new ArrayList<>();
                            for(MethodModificationInformation addedMethod: testClass.getValue().added_or_modified_methods){
                                if(addedMethod.modification_type.equals("add")){
                                    completedMethods.add(addedMethod.method_name);

                                    for(Map.Entry<String,PeerMethodSimilarity> p:addedMethod.addedPeerScores.entrySet()){
                                        if(completedMethods.contains(p.getKey())){
                                            continue;
                                        }
                                        boolean accept = (p.getValue().jaccardSimilarity >= 0.80)
                                                || (p.getValue().fullBodyEditDistance <= 0.10)
                                                || ((p.getValue().fullBodyEditDistance <= 0.21 && p.getValue().jaccardSimilarity >= 0.69));

                                        if(!(accept)){
                                            continue;
                                        }
                                        if(similarAddedMethodFlag == 0) {
                                            numberOf_TestclassesWhereMethodsAdded_Have_One_Similar_AddedMethod.incrementAndGet();
                                            similarAddedMethodFlag = 1;
                                        }
                                        // sorting
                                        SortStructure structure = new SortStructure();
                                        structure.distinctMethodName = addedMethod.method_name;
                                        structure.distinctMethodBody = addedMethod.method_body;
                                        structure.distinctMethodNormalizedBody = addedMethod.normalized_method_body;
                                        //structure.distinctFullMethodNormalizedBody = addedMethod.literal_value_only_normalized_body;
                                        structure.addedMethodName =p.getKey();
                                        structure.addedMethodBody = getMethodBodyByName(testClass.getValue().added_or_modified_methods, p.getKey());
                                        structure.addedMethodNormalizedBody = getMethodNormalizedBodyByName(testClass.getValue().added_or_modified_methods, p.getKey());
                                        //structure.addedFullMethodNormalizedBody= method.literal_value_only_normalized_body;
                                        structure.similarityScore = p.getValue().jaccardSimilarity;
                                        structure.bodyEditDistance = p.getValue().fullBodyEditDistance;
                                        structure.positionDistance = abs(p.getValue().positionDistance);
                                        MethodModificationInformation peerMethod = getNormalizedBodyMethodList(testClass.getValue().added_or_modified_methods,p.getKey());
                                        structure.uncommonMethodsWithPeer = getUncommonMethods(addedMethod.normalized_method_body,peerMethod.normalized_method_body);

                                        peerSortList.add(structure);
                                    }

                                    /*Collections.sort(
                                            peerSortList,
                                            Comparator
                                                    .comparingDouble((SortStructure s) ->
                                                            Double.isNaN(s.bodyEditDistance) ? Double.POSITIVE_INFINITY : s.bodyEditDistance
                                                    )
                                                    .thenComparingDouble((SortStructure s) ->
                                                            -(Double.isNaN(s.similarityScore) ? Double.NEGATIVE_INFINITY : s.similarityScore)
                                                    )

                                    );*/
                                    /*if(peerSortList.size()>0){
                                        Random rand = new Random();
                                        SortStructure s = peerSortList.get(rand.nextInt(peerSortList.size()));
                                        //for(SortStructure s: peerSortList){
                                        //creating excel
                                        String combinedMethodBodies = addedMethod.method_body+"\n"+s.addedMethodBody;
                                        if(combinedMethodBodies.length() > 32767){
                                            combinedMethodBodies="too long";
                                        }
                                        String combinedNormalizedBodies = addedMethod.normalized_method_body+"\n"+s.addedMethodNormalizedBody;
                                        if(combinedNormalizedBodies.length() > 32767){
                                            combinedNormalizedBodies="too long";
                                        }
                                        Row row = peerSimilaritySheet.createRow(peerCounter.incrementAndGet());
                                        row.createCell(0).setCellValue(testClass.getKey());
                                        row.createCell(1).setCellValue(commit_record.commit_hash);
                                        row.createCell(2).setCellValue(testClass.getValue().parent_commit_hash);
                                        row.createCell(3).setCellValue(addedMethod.method_name);
                                        //row.createCell(4).setCellValue(addedMethod.modification_type+"\n"+getModificationTypeByName(testClass.getValue().added_or_modified_methods, p.getKey()));
                                        row.createCell(4).setCellValue(combinedMethodBodies);
                                        row.createCell(5).setCellValue(combinedNormalizedBodies);
                                        row.createCell(6).setCellValue(s.addedMethodName);
                                        row.createCell(7).setCellValue(s.similarityScore);
                                        //row.createCell(9).setCellValue(p.getValue().editDistance);
                                        row.createCell(8).setCellValue(s.bodyEditDistance);
                                        row.createCell(9).setCellValue(s.positionDistance);
                                        if(testClass.getValue().previous_methods.size()>0){
                                            row.createCell(10).setCellValue(testClass.getValue().addedMethodClusters.toString());
                                        }
                                        else{
                                            row.createCell(10).setCellValue("[]");
                                        }

                                        row.createCell(11).setCellValue(s.uncommonMethodsWithPeer.toString());
                                        //}

                                    }*/
                                }

                            }
                            if(peerSortList.size()>0){
                                Random rand = new Random();
                                SortStructure s = peerSortList.get(rand.nextInt(peerSortList.size()));
                                //for(SortStructure s: peerSortList){
                                //creating excel
                                String combinedMethodBodies = s.distinctMethodBody+"\n"+s.addedMethodBody;
                                if(combinedMethodBodies.length() > 32767){
                                    combinedMethodBodies="too long";
                                }
                                String combinedNormalizedBodies = s.distinctMethodNormalizedBody+"\n"+s.addedMethodNormalizedBody;
                                if(combinedNormalizedBodies.length() > 32767){
                                    combinedNormalizedBodies="too long";
                                }
                                Row row = peerSimilaritySheet.createRow(peerCounter.incrementAndGet());
                                row.createCell(0).setCellValue(testClass.getKey());
                                row.createCell(1).setCellValue(commit_record.commit_hash);
                                row.createCell(2).setCellValue(testClass.getValue().parent_commit_hash);
                                row.createCell(3).setCellValue(s.distinctMethodName);
                                //row.createCell(4).setCellValue(addedMethod.modification_type+"\n"+getModificationTypeByName(testClass.getValue().added_or_modified_methods, p.getKey()));
                                row.createCell(4).setCellValue(combinedMethodBodies);
                                row.createCell(5).setCellValue(combinedNormalizedBodies);
                                row.createCell(6).setCellValue(s.addedMethodName);
                                row.createCell(7).setCellValue(s.similarityScore);
                                //row.createCell(9).setCellValue(p.getValue().editDistance);
                                row.createCell(8).setCellValue(s.bodyEditDistance);
                                row.createCell(9).setCellValue(s.positionDistance);
                                if(testClass.getValue().previous_methods.size()>0){
                                    row.createCell(10).setCellValue(testClass.getValue().addedMethodClusters.toString());
                                }
                                else{
                                    row.createCell(10).setCellValue("[]");
                                }

                                row.createCell(11).setCellValue(s.uncommonMethodsWithPeer.toString());
                                //}
                            }
                        }
                    }
                    if(atleastOneTestCaseAdded_Flag == 0){
                        commitsWithout_AnyTestCaseAdded.incrementAndGet();
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
        try (FileOutputStream fos = new FileOutputStream("/Users/afrinakhatun/IdeaProjects/Analyzer/ExcelResults/"+projectName+"_set_Result.xlsx")) {
            workbook.write(fos);
            workbook.close();
            System.out.println("Excel file created successfully!");
        } catch (IOException e) {
            System.out.println("am in file exception");
            e.printStackTrace();
        }

        System.out.println("commits which compiled: "+noOfCommits.get());
        System.out.println("commits in which at least one test case added: "+commitsWith_AtleastOneTestCaseAdded.get());
        System.out.println("commits without any test case added: "+commitsWithout_AnyTestCaseAdded.get());
        System.out.println("commitsWithAtleastOneAddHavingGreaterThan70PercentSimilarity: "+commitsWithAtleastOneAddHavingAmplifiedTestCase.get());
        System.out.println();

        System.out.println("numberOfAddedMethods: "+numberOfAddedMethods.get());
        System.out.println("no of added test case which call any non-tested method: "+numberOfAddedMethods_WhichTests_NewMethods.get());
        System.out.println("no of added test case which DONOT call any non-tested method: "+numberOfAddedMethods_WhichTests_No_NewMethods.get());
        System.out.println();

        System.out.println("numberOfAddedMethodsWhichHasSimilarMethods: "+numberOfAddedMethodsWhich_AreAmplified.get());
        double result = (double) numberOfAddedMethodsWhich_AreAmplified.get() /numberOfAddedMethods.get() ;
        System.out.println("Reuse percentage: " + result*100);
        System.out.println("no of Amplified test case which call any non-tested method: "+numberOfAmplified_Methods_WhichTests_NewMethods.get());
        System.out.println("no of Amplified test case which DONOT call any non-tested method: "+numberOfAmplified_Methods_WhichTests_No_NewMethods.get());
        System.out.println();

        System.out.println("numberOfAddedMethodsWhichHasSimilarMethods: "+numberOfAddedMethodsWhich_AreAmplified.get());
        System.out.println("Identical Methods: "+numberOf_IDN.get());
        System.out.println("Method Add Only - Single: "+ numberOf_Only_ADD_Single.get());
        System.out.println("Method Add Only - Multiple: "+ numberOf_Only_ADD_Mul.get());
        System.out.println("Method Delete Only - Single: "+ numberOf_Only_Remove_Single.get());
        System.out.println("Method Delete Only - Multiple: "+ numberOf_Only_Remove_Mul.get());
        System.out.println("Method Add-Delete Only - One-One: "+ numberOf_Only_ADD_Single_REM_Single.get());
        System.out.println("Method Add-Delete Only - Multiple: "+ numberOf_Only_ADD_REM_Mul.get());
        System.out.println("Method Duplicate Add Only - Single: "+ numberOf_Only_DUP_ADD_Single.get());
        System.out.println("Method Duplicate Add Only - Multiple: "+ numberOf_Only_DUP_ADD_MUL.get());
        System.out.println("Method Duplicate Reduce Only - Single: "+ numberOf_Only_DUP_Reduce_Single.get());
        System.out.println("Method Duplicate Reduce Only - Multiple: "+ numberOf_Only_DUP_Reduce_Mul.get());
        System.out.println("Method Add with Duplicate: "+ numberOf_Only_ADD_with_Dup.get());
        System.out.println("Method Remove with Duplicate: "+ numberOf_Only_Remove_with_Dup.get());
        System.out.println("Other cases: "+ (numberOfAddedMethodsWhich_AreAmplified.get()-cases_Covered.get()));

        System.out.println("Number of test classes with multiple add: " + numberOf_TestclassesWhereMultipleMethodsAdded_Or_Modified.get());
        System.out.println("Number of test classes where one similar Add:" + numberOf_TestclassesWhereMethodsAdded_Have_One_Similar_AddedMethod.get());
        double per1 = (double) numberOf_TestclassesWhereMethodsAdded_Have_One_Similar_AddedMethod.get()/numberOf_TestclassesWhereMultipleMethodsAdded_Or_Modified.get();
        System.out.println("Percent:" + per1*100);
       // System.out.println("Number of test classes where one similar Modify:" + numberOf_TestclassesWhereMethodsAdded_IS_SimilarTo_ModifiedMethod.get());
        //double per2 = (double) numberOf_TestclassesWhereMethodsAdded_IS_SimilarTo_ModifiedMethod.get()/numberOf_TestclassesWhereMultipleMethodsAdded_Or_Modified.get();
        //System.out.println("Percent:" + per2*100);


    }

    private static String getSourceAndAddedPositions(List<String> afterOrderedMethods, String distinctMethod, String addedMethod, ArrayList<MethodModificationInformation> addedOrModifiedMethods) {
        List<String> filteredMethods = new ArrayList<>();
        for(String a: afterOrderedMethods){
            filteredMethods.add(a);
        }
        for(MethodModificationInformation m: addedOrModifiedMethods){
            if(!m.method_name.equals(addedMethod) && m.modification_type.equals("add")){
                filteredMethods.remove(m.method_name);
            }
        }
        String pos = filteredMethods.indexOf(distinctMethod) +"-"+filteredMethods.indexOf(addedMethod);
        return pos;
    }
    private static int getPositionDistance(List<String> afterOrderedMethods, String distinctMethod, String addedMethod, ArrayList<MethodModificationInformation> addedOrModifiedMethods) {
        List<String> filteredMethods = new ArrayList<>();
        for(String a: afterOrderedMethods){
            filteredMethods.add(a);
        }
        for(MethodModificationInformation m: addedOrModifiedMethods){
            if(!m.method_name.equals(addedMethod) && m.modification_type.equals("add")){
                filteredMethods.remove(m.method_name);
            }
        }
        int pos = filteredMethods.indexOf(distinctMethod) - filteredMethods.indexOf(addedMethod);
        return Math.abs(pos) - 1;
    }

    private static boolean hasSourceChanges(Set<String> commitedFiles) {
        for(String filePath : commitedFiles){
            if(filePath.contains("src/main/java") || filePath.contains("src/java/org")){
                return true;
            }
        }
        return false;
    }

    private static Set<String> getUncommonMethods(String addedMethodNormalizedMethodBodyList, String peerNormalizedMethodBodyList) {
        String addedMethod = addedMethodNormalizedMethodBodyList.substring(1, addedMethodNormalizedMethodBodyList.length() - 1);
        String[] addedMethodList = addedMethod.split(",");

        String peerMethod = peerNormalizedMethodBodyList.substring(1, peerNormalizedMethodBodyList.length()-1);
        String[] peerMethodList = peerMethod.split(",");

        Set<String> uncommons = new HashSet<>();
        if(addedMethodList.length==0 || peerMethodList.length==0){
            uncommons.add("-1");
            return uncommons;
        }

        Set<String> addedMethod_Set = new HashSet<>(Arrays.asList(addedMethodList));
        Set<String> peerMethod_Set = new HashSet<>(Arrays.asList(peerMethodList));

        uncommons.addAll(addedMethod_Set);
        uncommons.addAll(peerMethod_Set);
        Set<String> intersection = new HashSet<>(addedMethod_Set);
        intersection.retainAll(peerMethod_Set);
        uncommons.removeAll(intersection);

        return uncommons;
    }

    private static MethodModificationInformation getNormalizedBodyMethodList(ArrayList<MethodModificationInformation> addedOrModifiedMethods, String key) {
        for(MethodModificationInformation m : addedOrModifiedMethods){
            if(m.method_name.equals(key)){
                return m;
            }
        }
        return null;
    }

    public static String getMethodBodyByName(List<MethodModificationInformation> modificationInformations, String name){
        for(MethodModificationInformation m:modificationInformations){
            if(m.method_name.equals(name)){
                return m.method_body;
            }
        }
        return null;
    }
    public static String getMethodNormalizedBodyByName(List<MethodModificationInformation> modificationInformations, String name){
        for(MethodModificationInformation m:modificationInformations){
            if(m.method_name.equals(name)){
                return m.normalized_method_body;
            }
        }
        return null;
    }
    public static String getModificationTypeByName(List<MethodModificationInformation> modificationInformations, String name){
        for(MethodModificationInformation m:modificationInformations){
            if(m.method_name.equals(name)){
                return m.modification_type;
            }
        }
        return null;
    }
    public static Map<String, List<String>> extractCategory(String category){
        Map<String, List<String>> map = new HashMap<>();

        String[] parts = category.split("\\|");

        for (String part : parts) {
            part = part.trim();

            if (part.startsWith("ADD[") || part.startsWith("REM[") || part.startsWith("CMN_COUNT_INC[") || part.startsWith("CMN_COUNT_DEC[")) {

                int open = part.indexOf('[');
                int close = part.indexOf(']');

                String key = part.substring(0, open);
                String valuePart = part.substring(open + 1, close);

                String[] values = valuePart.split(",");

                List<String> list = map.get(key);
                if (list == null) {
                    list = new ArrayList<>();
                    map.put(key, list);
                }

                for (String v : values) {
                    list.add(v.trim());
                }
            }
        }
        return map;
    }

}
