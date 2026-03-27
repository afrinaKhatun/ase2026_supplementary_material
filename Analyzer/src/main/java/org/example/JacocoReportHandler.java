package org.example;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;


import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.html.HTMLFormatter;
import org.jacoco.report.xml.XMLFormatter;
import org.jacoco.report.DirectorySourceFileLocator;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;


import java.util.*;
import java.util.stream.Collectors;

public class JacocoReportHandler {
    public static void generateXmlReport(String execFilePath, String classesDirPath, String sourceDirPath, String outputDirPath, String reportName, String htmlReportOutputDir) throws IOException {
        // Load exec data
        ExecFileLoader loader = new ExecFileLoader();
        loader.load(new File(execFilePath));

        // Prepare output folder
        File outputDir = new File(outputDirPath);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // Create coverage builder and analyzer
        CoverageBuilder coverageBuilder = new CoverageBuilder();
        Analyzer analyzer = new Analyzer(loader.getExecutionDataStore(), coverageBuilder);

        // Analyze compiled classes
        analyzer.analyzeAll(new File(classesDirPath));

        // Set up XML report writer
        XMLFormatter xmlFormatter = new XMLFormatter();
        File reportFile = new File(outputDir, reportName + ".xml");

        try (FileOutputStream fos = new FileOutputStream(reportFile)) {
            IReportVisitor visitor = xmlFormatter.createVisitor(fos);

            visitor.visitInfo(loader.getSessionInfoStore().getInfos(), loader.getExecutionDataStore().getContents());

            visitor.visitBundle(
                    coverageBuilder.getBundle(reportName),
                    new DirectorySourceFileLocator(new File(sourceDirPath), "utf-8", 4)
            );

            visitor.visitEnd();
        }

        System.out.println("✅ XML generated");

        // Create HTML report writer
        /*
        HTMLFormatter htmlFormatter = new HTMLFormatter();
        File htmlReportDir = new File(htmlReportOutputDir);
        if (!htmlReportDir.exists()) {
            htmlReportDir.mkdirs();
        }

        IReportVisitor htmlVisitor = htmlFormatter.createVisitor(new org.jacoco.report.FileMultiReportOutput(htmlReportDir));

        htmlVisitor.visitInfo(loader.getSessionInfoStore().getInfos(), loader.getExecutionDataStore().getContents());

        htmlVisitor.visitBundle(
                coverageBuilder.getBundle(reportName),
                new DirectorySourceFileLocator(new File(sourceDirPath), "utf-8", 4)
        );

        htmlVisitor.visitEnd();

        System.out.println("✅ HTML Report generated at: " + htmlReportDir.getAbsolutePath());
        */
    }

    public static ArrayList<XmlClassData> pares_report(String filePath){
        File jacocoXml = new File(filePath); // change path as needed
        SAXBuilder builder = new SAXBuilder();
        builder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        ArrayList<XmlClassData> source_classes = new ArrayList<>();

        Document document = null;
        try {
            document = builder.build(jacocoXml);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Element root = document.getRootElement(); // <report>
        List<Element> packages = root.getChildren("package");

        for (Element pkg : packages) {
            String packageName = pkg.getAttributeValue("name");

            Map<String, Element> sourceFiles = new HashMap<String, Element>();
            for (Element s : pkg.getChildren("sourcefile")) {
                sourceFiles.put(s.getAttributeValue("name"), s);
            }
            //System.out.println(sourceFiles.size());

            for (Element cla : pkg.getChildren("class")) {
                XmlClassData class_data = new XmlClassData();

                String className = cla.getAttributeValue("name").replace("/", ".");
                //System.out.println("\nClass: " + className);
                class_data.class_name = className;
                class_data.package_name = packageName.replace("/",".");


                Element sourceFile = sourceFiles.get(cla.getAttributeValue("sourcefilename"));
                //System.out.println(cla.getAttributeValue("sourcefilename"));
                //System.out.println(sourceFile.getAttributeValue("name"));
                // Map to store line coverage
                Map<Integer, Boolean> lineCoverage = new HashMap<>();
                for (Element line : sourceFile.getChildren("line")) {
                    int lineNumber = Integer.parseInt(line.getAttributeValue("nr"));
                    boolean covered = Integer.parseInt(line.getAttributeValue("ci")) > 0;
                    //System.out.println(lineNumber + " " + covered);
                    lineCoverage.put(lineNumber, covered);
                }
                class_data.line_coverage.putAll(lineCoverage);
                for(Element counter: sourceFile.getChildren("counter")){
                    if(counter.getAttributeValue("type").equals("LINE")){
                        class_data.lines_covered = Integer.parseInt(counter.getAttributeValue("covered"));
                        class_data.lines_missed = Integer.parseInt(counter.getAttributeValue("missed"));
                    }
                }


                List<XmlMethodData> methodStruct = new ArrayList<XmlMethodData>();
                for (Element method : cla.getChildren("method")) {
                    String methodName = method.getAttributeValue("name");
                    int startLine = Integer.parseInt(method.getAttributeValue("line"));
                    methodStruct.add(new XmlMethodData(methodName, startLine));
                }
                Collections.sort(methodStruct, Comparator.comparingInt(XmlMethodData::getStartLine));

                for (int i = 0; i < methodStruct.size(); i++) {

                    if (i == (methodStruct.size() - 1)) {

                        for (Element method : cla.getChildren("method")) {
                            if (method.getAttributeValue("name").equalsIgnoreCase(methodStruct.get(i).name)) {
                                for (Map.Entry<Integer, Boolean> entry : lineCoverage.entrySet()) {
                                    if (entry.getKey() >= methodStruct.get(i).startLine) {
                                        methodStruct.get(i).lines.put(entry.getKey(), entry.getValue());
                                    }
                                }
                                break;
                            }
                        }
                    } else {

                        int nextMethodStartLine;
                        for (Element method : cla.getChildren("method")) {
                            if (method.getAttributeValue("name").equalsIgnoreCase(methodStruct.get(i).name)) {
                                nextMethodStartLine = methodStruct.get(i + 1).startLine;

                                for (Map.Entry<Integer, Boolean> entry : lineCoverage.entrySet()) {
                                    if (entry.getKey() >= methodStruct.get(i).startLine && entry.getKey() < nextMethodStartLine) {
                                        methodStruct.get(i).lines.put(entry.getKey(), entry.getValue());

                                    }
                                }
                                break;
                            }
                        }
                    }

                }
                class_data.methods.addAll(methodStruct);
                for (int i = 0; i < methodStruct.size(); i++) {
                    //System.out.println("Method name: " + methodStruct.get(i).name);
                    Map<Integer, Boolean> sortedMap = methodStruct.get(i).lines.entrySet()
                            .stream()
                            .sorted(Map.Entry.comparingByKey()) // ascending order
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (e1, e2) -> e1,
                                    LinkedHashMap::new // preserves the sorted order
                            ));
                    for (Map.Entry<Integer, Boolean> line : sortedMap.entrySet()) {
                        //System.out.println("Line: " + line.getKey() + " " + line.getValue());
                    }
                }

                source_classes.add(class_data);

            }
        }
        return source_classes;
    }

    public static void reportParser() {
        File jacocoXml = new File("/Users/afrinakhatun/IdeaProjects/jsoup/coverageData/a657d090e2f127ebdce131f1f97e7758a8ddc463/org.jsoup.parser.ParserTest/jacoco-a657d090e2f127ebdce131f1f97e7758a8ddc463-org_jsoup_parser_ParserTest-testParsesSimpleDocument.xml"); // change path as needed
        SAXBuilder builder = new SAXBuilder();
        builder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        Document document = null;
        try {
            document = builder.build(jacocoXml);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Element root = document.getRootElement(); // <report>
        List<Element> packages = root.getChildren("package");

        for (Element pkg : packages) {
            String packageName = pkg.getAttributeValue("name");

            Map<String, Element> sourceFiles = new HashMap<String, Element>();
            for (Element s : pkg.getChildren("sourcefile")) {
                sourceFiles.put(s.getAttributeValue("name"), s);
            }
            System.out.println(sourceFiles.size());

            for (Element cla : pkg.getChildren("class")) {
                String className = cla.getAttributeValue("name").replace("/", ".");
                System.out.println("\nClass: " + className);


                Element sourceFile = sourceFiles.get(cla.getAttributeValue("sourcefilename"));
                System.out.println(cla.getAttributeValue("sourcefilename"));
                System.out.println(sourceFile.getAttributeValue("name"));
                // Map to store line coverage
                Map<Integer, Boolean> lineCoverage = new HashMap<>();
                for (Element line : sourceFile.getChildren("line")) {
                    int lineNumber = Integer.parseInt(line.getAttributeValue("nr"));
                    boolean covered = Integer.parseInt(line.getAttributeValue("ci")) > 0;
                    //System.out.println(lineNumber + " " + covered);
                    lineCoverage.put(lineNumber, covered);
                }

                List<XmlMethodData> methodStruct = new ArrayList<XmlMethodData>();
                for (Element method : cla.getChildren("method")) {
                    String methodName = method.getAttributeValue("name");
                    int startLine = Integer.parseInt(method.getAttributeValue("line"));
                    methodStruct.add(new XmlMethodData(methodName, startLine));
                }
                Collections.sort(methodStruct, Comparator.comparingInt(XmlMethodData::getStartLine));

                for (int i = 0; i < methodStruct.size(); i++) {

                    if (i == (methodStruct.size() - 1)) {

                        for (Element method : cla.getChildren("method")) {
                            if (method.getAttributeValue("name").equalsIgnoreCase(methodStruct.get(i).name)) {
                                for (Map.Entry<Integer, Boolean> entry : lineCoverage.entrySet()) {
                                    if (entry.getKey() >= methodStruct.get(i).startLine) {
                                        methodStruct.get(i).lines.put(entry.getKey(), entry.getValue());

                                    }
                                }
                                break;
                            }
                        }
                    } else {

                        int nextMethodStartLine;
                        for (Element method : cla.getChildren("method")) {
                            if (method.getAttributeValue("name").equalsIgnoreCase(methodStruct.get(i).name)) {
                                nextMethodStartLine = methodStruct.get(i + 1).startLine;

                                for (Map.Entry<Integer, Boolean> entry : lineCoverage.entrySet()) {
                                    if (entry.getKey() >= methodStruct.get(i).startLine && entry.getKey() < nextMethodStartLine) {
                                        methodStruct.get(i).lines.put(entry.getKey(), entry.getValue());

                                    }
                                }
                                break;
                            }
                        }
                    }

                }

                for (int i = 0; i < methodStruct.size(); i++) {
                    System.out.println("Method name: " + methodStruct.get(i).name);
                    Map<Integer, Boolean> sortedMap = methodStruct.get(i).lines.entrySet()
                            .stream()
                            .sorted(Map.Entry.comparingByKey()) // ascending order
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (e1, e2) -> e1,
                                    LinkedHashMap::new // preserves the sorted order
                            ));
                    for (Map.Entry<Integer, Boolean> line : sortedMap.entrySet()) {
                        System.out.println("Line: " + line.getKey() + " " + line.getValue());
                    }
                }

            }
        }
    }
}

