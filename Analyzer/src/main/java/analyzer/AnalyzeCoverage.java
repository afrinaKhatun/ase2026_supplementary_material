package analyzer;

import org.example.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnalyzeCoverage {
    public static void main(String[] args) {
        System.out.println("Processing Data");
        String coverageRootPath = "/Users/afrinakhatun/IdeaProjects/jsoup/coverageData/";
        Path ndjson = Paths.get("/Users/afrinakhatun/IdeaProjects/Analyzer/commit-test-change-history.ndjson");
        //coverage writer handler
        Path jsonFile = Paths.get("/Users/afrinakhatun/IdeaProjects/Analyzer/commit-test-coverage.ndjson");
        PerCommitCoverageJsonHandler.resetFile(jsonFile);
        try {
            CommitRecordJsonHandler.readRecordsStreaming(ndjson, record -> {
                // ⚙️  your processing code here
                if(CoverageFileHandler.commitFolderExists(coverageRootPath,record.commit_hash)){
                    ArrayList<XmlClassData> existing_source_classes = new ArrayList<>();
                    Map<String,XmlClassData> combined_existing_class_coverage = new HashMap<>();


                    for (Map.Entry<String, MethodList> entry : record.method_list.entrySet()) {
                        String class_name_with_package = entry.getKey();

                        MethodList all_methods = entry.getValue();

                        /*for(String method_name : all_methods.previous_methods){
                            String xml_file = coverageRootPath +
                                    record.commit_hash+"/"+
                                    class_name_with_package +
                                    "/jacoco-"+
                                    record.commit_hash+"-"+
                                    class_name_with_package.replace('.','_')+"-"+
                                    method_name+".xml";
                            //System.out.println(xml_file);
                            if(Files.exists(Paths.get(xml_file))){
                                //System.out.println("starting work");
                                existing_source_classes.addAll(JacocoReportHandler.pares_report(xml_file));
                            }
                        }*/
                    }
                    //combine class coverage for each class covered by all existing test cases
                    for(XmlClassData cls : existing_source_classes){
                        if(combined_existing_class_coverage.containsKey(cls.class_name)){
                            for(Map.Entry<Integer, Boolean> entry : cls.line_coverage.entrySet()){
                                Integer line = entry.getKey();
                                Boolean coverage = entry.getValue();
                                if(combined_existing_class_coverage.get(cls.class_name).line_coverage.get(line) == false
                                && coverage == true){
                                    combined_existing_class_coverage.get(cls.class_name).line_coverage.put(line, true);
                                }
                            }
                        }
                        else{
                            combined_existing_class_coverage.put(cls.class_name, cls);
                        }
                    }
                    //look add each added methods
                    Map<String, List<PerTestCoverage>> all_test_coverage = new HashMap<>();
                    for (Map.Entry<String, MethodList> entry : record.method_list.entrySet()) {
                        String class_name_with_package = entry.getKey();
                        MethodList all_methods = entry.getValue();
                        List<PerTestCoverage> test_coverage = new ArrayList<>();

                        for(MethodModificationInformation m : all_methods.added_or_modified_methods){
                            String xml_file = coverageRootPath +
                                    record.commit_hash+"/"+
                                    class_name_with_package +
                                    "/jacoco-"+
                                    record.commit_hash+"-"+
                                    class_name_with_package.replace('.','_')+"-"+
                                    m.method_name+".xml";
                            System.out.println(xml_file);
                            if(Files.exists(Paths.get(xml_file))){
                                if(m.modification_type.equals("add")){
                                    boolean increased_coverage = false;
                                    /*if(m.test_setup_amplification != null){
                                        if(m.test_setup_amplification.equals("SM+SO") && m.test_assertion_amplification.equals("Dup")){
                                            ArrayList<XmlClassData> add_or_modify_source_classes = JacocoReportHandler.pares_report(xml_file);
                                            boolean found = false; //increased at least one line in any class
                                            for(XmlClassData cls : add_or_modify_source_classes){
                                                if(found) break;
                                                XmlClassData combined_class = combined_existing_class_coverage.get(cls.class_name);
                                                if(combined_class != null){
                                                    for(Map.Entry<Integer, Boolean> line : cls.line_coverage.entrySet()){
                                                        if(line.getValue()==true && combined_class.line_coverage.get(line.getKey())==false){
                                                            increased_coverage = true;
                                                            found = true;
                                                            break;
                                                        }
                                                    }
                                                }
                                                else{
                                                    for(Map.Entry<Integer, Boolean> line : cls.line_coverage.entrySet()){
                                                        if(line.getValue()==true){
                                                            increased_coverage = true;
                                                            found = true;
                                                            break;
                                                        }
                                                    }
                                                }

                                            }
                                            //System.out.println(m.method_name +" has covered "+ count_new_lines_covered +" more lines "+isImprovingCoverage);
                                            PerTestCoverage p = new PerTestCoverage();
                                            p.method_name = m.method_name;
                                            //p.number_of_covered_lines = count_new_lines_covered;
                                            p.coverage_increased = increased_coverage;
                                            test_coverage.add(p);
                                        }

                                    }*/

                                }

                            }

                        }
                        all_test_coverage.put(class_name_with_package,test_coverage);
                    }
                    try {
                        PerCommitCoverageJsonHandler.appendRecord(
                                jsonFile,
                                new PerCommitCoverage(record.commit_hash,all_test_coverage)
                        );
                    } catch (IOException e) {
                        System.out.println("file error writing");
                        throw new RuntimeException(e);
                    }
                }
            });

        } catch (Exception e) {
            System.err.print("Error reading Json: ");
            e.printStackTrace();
        }
    }

}
