package result.analysis;

import analyzer.PerCommitCoverage;
import analyzer.PerCommitCoverageJsonHandler;
import analyzer.PerTestCoverage;


import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.CommitRecordJsonHandler;
import org.example.MethodList;
import org.example.MethodModificationInformation;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ResultGeneration {
    public static void main(String[] args){
        Path static_ndjson = Paths.get("/Users/afrinakhatun/IdeaProjects/Analyzer/commit-test-change-history.ndjson");
        Path dynamic_ndjson = Paths.get("/Users/afrinakhatun/IdeaProjects/Analyzer/commit-test-coverage.ndjson");
        List<String> headers = List.of("MethodName", "Test Setup Amplification", "Test Assertion Amplification", "Coverage Increased");
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Result");

        // Create header row
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
        }
        int rowCount = 0;
        AtomicInteger counter = new AtomicInteger(0);
        try{
            CommitRecordJsonHandler.readRecordsStreaming(static_ndjson, change_record -> {
                try {
                    AtomicBoolean matched = new AtomicBoolean(false);
                    PerCommitCoverageJsonHandler.readRecordsStreaming(dynamic_ndjson, coverage_record -> {
                        if(change_record.commit_hash.equals(coverage_record.commit_hash)){
                            matched.set(true);
                             Map<String, MethodList> methodList = change_record.method_list;
                             for(Map.Entry<String,MethodList> cl: methodList.entrySet()){
                                 if(!cl.getValue().previous_methods.isEmpty()){
                                     for(MethodModificationInformation m:cl.getValue().added_or_modified_methods){
                                         if(m.modification_type.equals("add")){
                                            List<PerTestCoverage> per_test = coverage_record.all_test_coverage.get(cl.getKey());
                                            if(per_test.isEmpty()){
                                                Row row = sheet.createRow(counter.incrementAndGet());
                                                row.createCell(0).setCellValue(m.method_name);

                                                //row.createCell(1).setCellValue(m.test_setup_amplification);
                                                //row.createCell(2).setCellValue(m.test_assertion_amplification);
                                                row.createCell(3).setCellValue("not applicable");
                                            }
                                            else{
                                                for(PerTestCoverage test:per_test){
                                                    if(m.method_name.equals(test.method_name)){
                                                        Row row = sheet.createRow(counter.incrementAndGet());
                                                        row.createCell(0).setCellValue(m.method_name);

                                                        //row.createCell(1).setCellValue(m.test_setup_amplification);
                                                        //row.createCell(2).setCellValue(m.test_assertion_amplification);
                                                        row.createCell(3).setCellValue(test.coverage_increased);
                                                    }
                                                }
                                            }


                                         }
                                     }
                                 }
                             }

                        }

                    });
                    if (!matched.get()) {
                        Map<String, MethodList> methodList = change_record.method_list;
                        for(Map.Entry<String,MethodList> cl: methodList.entrySet()){
                            if(!cl.getValue().previous_methods.isEmpty()){
                                for(MethodModificationInformation m:cl.getValue().added_or_modified_methods){
                                    if(m.modification_type.equals("add")){
                                        //List<PerTestCoverage> per_test = coverage_record.all_test_coverage.get(cl.getKey());
                                        //for(PerTestCoverage test:per_test){
                                           // if(m.method_name.equals(test.method_name)){
                                                Row row = sheet.createRow(counter.incrementAndGet());
                                                row.createCell(0).setCellValue(m.method_name);
                                                //row.createCell(1).setCellValue(m.test_setup_amplification);
                                                //row.createCell(2).setCellValue(m.test_assertion_amplification);
                                                row.createCell(3).setCellValue("not applicable");
                                           // }
                                       // }

                                    }
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    System.out.println("am in lamda exception");
                    throw new RuntimeException(e);
                }
            });
        }
        catch (Exception e){
            System.out.println("I am here");
            e.printStackTrace();
        }
        try (FileOutputStream fos = new FileOutputStream("/Users/afrinakhatun/IdeaProjects/Analyzer/Result.xlsx")) {
            workbook.write(fos);
            workbook.close();
            System.out.println("Excel file created successfully!");
        } catch (IOException e) {
            System.out.println("am in file exception");
            e.printStackTrace();
        }
    }
}
