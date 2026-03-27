package org.example;

import analyzer.*;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.shared.invoker.*;
import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import tree.analyzer.GumTreeSim;
import tree.analyzer.JavapExtracator;
import tree.analyzer.JavapMethodBlock;
import tree.analyzer.JavapMethodStruct;

public class NewTestCoverageAnalyzerUpdate {
    private static final Logger mainLogger = Logger.getLogger(NewTestCoverageAnalyzer.class.getName());

    private static String projectName = "";
    private static String branchName = "";
    //?????? HAVE YOU RESET BRANCH  ???????????


    private static String REPO_PATH = "";
    private static final String SOURCE_PATH = "/src/main/java";
    private static String TEST_PATH = "src/test/java";
    private static final String ALT_TEST_PATH = "src/test";//"src/test/java"
    private static String TARGET_DIR = "";
    private static final Pattern TEST_METHOD_PATTERN = Pattern.compile(
            "(?:@Test\\s*(?:\\([^)]*\\))?\\s*)?public\\s+void\\s+(\\w+)\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w,\\s]+)?\\s*\\{",
            Pattern.MULTILINE
    );
    private static final String[] TEST_SUFFIXES = {"Test.java", "Tests.java", "IT.java", "ITs.java","Spec.java"};

    static final String Coverage_Folder = "/coverageData";
    static  PrintStream consoleOut = null;
    static  PrintStream fileOut = null;
    static  PrintStream bothOut = null;
    static  PrintStream bothErr = null;

    // NEW (FIX 6, FIX 7): Method to log heap size with method and class name
    private static void logHeapSize() {
        // NEW (FIX 7): Get caller method and class from stack trace
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String callerMethod = stackTrace[2].getMethodName(); // Index 2 is the caller (logHeapSize caller)
        String callerClass = stackTrace[2].getClassName();

        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = Runtime.getRuntime().maxMemory();
        long remainingMemory = maxMemory - usedMemory;
        consoleOut.println("Heap "+callerClass+"."+callerMethod+" Max="+maxMemory/ (1024 * 1024)+", Total"+ totalMemory / (1024 * 1024)+"Free="+
                freeMemory / (1024 * 1024)+"Remaining= "+remainingMemory / (1024 * 1024));

    }


    public static void main(String[] args) throws Exception {
        //JacocoReportHandler.reportParser();
        //System.exit(0);

        if (args.length == 2){
            projectName = args[0];
            branchName = args[1];
        }
        projectName="commons-io";
        branchName = "afrina-branch";

        REPO_PATH = "/Users/afrinakhatun/IdeaProjects/"+projectName;
        TARGET_DIR = REPO_PATH + "/target";


        loggingSetUp();
        //json handler
        Path jsonFile = Paths.get("/Users/afrinakhatun/IdeaProjects/Analyzer/ParsedJsons/"+projectName+"-commit-test-change-history.ndjson");
        CommitRecordJsonHandler.resetFile(jsonFile);

        // Your large program continues here...
        int x = 0;
        int counter =1;
        int hasTestCounter = 0;
        int temp_skipper = 0;
        File coverageFolder = new File(REPO_PATH + Coverage_Folder);
        if(coverageFolder.exists()){
            deleteDirectory(coverageFolder);
        }else{
            coverageFolder.mkdir();
        }


        File repoDir = new File(REPO_PATH);
        try (Git git = Git.open(repoDir)) {
            // Save the current master commit hash
            String masterCommitHash = git.getRepository().resolve(branchName).getName();
            fileOut.println("Saved current local master commit: " + masterCommitHash);

            // Parse master commit properly
            RevCommit masterCommit = git.getRepository().parseCommit(ObjectId.fromString(masterCommitHash));

            // Save master pom.xml content
            String masterPomContent = getFileContent(git, masterCommit, "pom.xml");
            //System.out.println(masterPomContent);
            fileOut.println("Saved master branch pom.xml");

            git.reset().setMode(ResetCommand.ResetType.HARD).call();
            git.clean()
                    .setCleanDirectories(true)
                    .setForce(true)
                    .call();

            git.checkout().setName(branchName).call();
            fileOut.println("Checked out master branch");

            List<RevCommit> commits = listCommitsOnBranch(git,branchName);
            //List<RevCommit> commits = listCommitsByDate(git,branchName);
            fileOut.println("Found " + commits.size() + " commits");

            SingletonParser singletonParser = new SingletonParser(
                    REPO_PATH+SOURCE_PATH,
                    REPO_PATH+"/"+TEST_PATH);

            for (RevCommit commit : commits) {
                /*if(counter == 500 ){
                    // Always reset hard and checkout back to master
                    git.reset().setMode(ResetCommand.ResetType.HARD).call();
                    git.checkout().setName(masterCommitHash).call();
                    fileOut.println("Restored back to saved master commit: " + masterCommitHash);
                    System.exit(0);
                }*/
                String commitHash = commit.getId().getName();

               // if(!commitHash.equals("a8af50a01a816194de21717cf1f45d59312bda91")) continue;
                String commitMessage = commit.getFullMessage();  // ✅ commit message
                int commitTime = commit.getCommitTime();

                Date commitDate = new Date(commitTime * 1000L);
                // Format as readable string
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String formattedTime = sdf.format(commitDate);

                Set<String> committedFiles = listJavaFilesInCommit(git,commit);


                fileOut.println("Printing: "+counter+"/"+commits.size());
                consoleOut.println("Printing: "+counter+"/"+commits.size());

                counter=counter+1;
                //if(counter<2540) continue;

                //System.out.println("\nProcessing commit: " + commitHash + " (" + commit.getCommitTime() + ")");

                Set<String> changedFiles = getChangedFiles(git, commit);
                System.out.println("Changed files: " + changedFiles.size());

                boolean hasTestFile = changedFiles.stream().anyMatch(
                        file -> file.startsWith(TEST_PATH+"/") && isATestFile(file)
                );
                //System.out.println("Test file has changes: " + hasTestFile);

                if (hasTestFile) {
                    hasTestCounter +=1;
                    //fileOut.println("Test Changes: "+hasTestCounter);
                    //consoleOut.println("Test Changes: "+hasTestCounter);
                    String originalPomContent = null;
                    try {
                        System.out.println("Checking out commit: " + commitHash);
                        // 🔹 NEW: make sure the working tree is clean before checkout
                        git.reset().setMode(ResetCommand.ResetType.HARD).call();
                        git.clean()
                                .setCleanDirectories(true)
                                .setForce(true)
                                .call();
                        git.checkout().setName(commitHash).call();
                        //System.out.println("Successfully checked out commit");

                        // Backup original pom.xml
                        RevCommit currentCommit = git.getRepository().parseCommit(ObjectId.fromString(commitHash));
                        originalPomContent = getFileContent(git, currentCommit, "pom.xml");

                        // Overwrite pom.xml with master's version
                        writeFile(REPO_PATH + "/pom.xml", masterPomContent);
                        //System.out.println("Replaced pom.xml with master version temporarily");

                        //compile project, before pom rewriting
                        int exit = compileProjectForTests(REPO_PATH+"/");
                        if (exit == 0) {
                            System.out.println("✅ Compilation successful for exit code" + exit);
                        } else {
                            System.err.println("❌ Compilation failed for exit code" + exit);
                        }

                       Map<String, MethodList> changedTestMethods = updatedFindChangedTestMethods(git, commitHash);
                        logHeapSize();
                         if(exit == 0){
                            for (Map.Entry<String, MethodList> entry : changedTestMethods.entrySet()) {
                                if(!entry.getValue().previous_methods.isEmpty()) {
                                    List<String> javapContentLines = generateJavapFile(entry.getKey());
                                    if(javapContentLines.size() > 0){
                                        Map<String, JavapMethodBlock> methodBodies = JavapExtracator.getMethodInformation(javapContentLines);
                                        boolean inJUnit3TestClass = JavapExtracator.detectJUnit3TestClass(javapContentLines);
                                        Map<String, List<JavapMethodStruct>> methodCalls = new HashMap<>();
                                        for(Map.Entry<String ,JavapMethodBlock> m: methodBodies.entrySet()){
                                            List<JavapMethodStruct> calls = new ArrayList<>();
                                            for(String line: m.getValue().codeLines){
                                                if(JavapExtracator.parseFromMethodCommentLine(line,new ArrayList<>(methodBodies.values())) != null){
                                                    calls.add(JavapExtracator.parseFromMethodCommentLine(line,new ArrayList<>(methodBodies.values())));
                                                }
                                            }
                                            methodCalls.put(m.getKey(),calls);
                                        }
                                        System.out.println("done");
                                        for (MethodModificationInformation temp_method : entry.getValue().added_or_modified_methods) {
                                            if (temp_method.modification_type.equals("add")) {
                                                List<String> tempMethod = new ArrayList<>();
                                                for(JavapMethodStruct tm:methodCalls.get(temp_method.method_name)){
                                                    tempMethod.add(tm.methodName);
                                                }

                                                for (Map.Entry<String, PreviousMethodInfo> existing : entry.getValue().previous_methods.entrySet()) {
                                                    //if(!existing.getKey().equals("testWithinMultipleRanges")) continue;
                                                    List<String> existingMethod = new ArrayList<>();
                                                    for(JavapMethodStruct ex:methodCalls.get(existing.getKey())){
                                                        existingMethod.add(ex.methodName);
                                                    }
                                                    double setSimilarity = JavapInputAmplificationDetector.jaccardOnUnique(existingMethod,tempMethod);
                                                    if(setSimilarity >= 0.5){
                                                        String category = JavapInputAmplificationDetector.checkJavapAmplification(existingMethod,tempMethod);
                                                        double normalizedEidtDistance = JavapInputAmplificationDetector.getNormalizedEditDistance(existingMethod,tempMethod);
                                                        String severity = JavapInputAmplificationDetector.classifyWithEditDistance(normalizedEidtDistance);
                                                        //System.out.println("googli");

                                                        //for result writing
                                                        temp_method.normalized_method_body = tempMethod.toString();
                                                        temp_method.similarityScores.put(existing.getKey(),setSimilarity);
                                                        temp_method.editDistanceScores.put(existing.getKey(),normalizedEidtDistance);
                                                        temp_method.amplification_category.put(existing.getKey(),null);
                                                        //temp_method.amplification_severity.put(existing.getKey(),severity);
                                                        existing.getValue().previousMethodNormalizedBody = existingMethod.toString();
                                                    }
                                                }
                                            }
                                        }
                                   }

                                }
                            }// chnged method list
                            //save all to json
                            CommitRecordJsonHandler.appendRecord(
                                    jsonFile,
                                    new CommitRecord(commitHash, changedTestMethods, committedFiles, commitMessage,false)
                            );
                            //changedTestMethods.clear();
                             logHeapSize();
                        }
                        if (temp_skipper == 0) continue;
                        System.out.println("I m here");

                        if (!changedTestMethods.isEmpty()) {
                            /*if(x==1){
                                // Always reset hard and checkout back to master
                                git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).call();
                                git.checkout().setName(masterCommitHash).call();
                                System.out.println("Restored back to saved master commit: " + masterCommitHash);
                                //System.exit(0);
                            }*/
                            //x= x+1;
                            //System.out.println("Changed test methods: " + changedTestMethods);
                            for (Map.Entry<String, MethodList> entry : changedTestMethods.entrySet()) {
                                String testClass = entry.getKey();
                                //New code with GumTree

                                if(!entry.getValue().previous_methods.isEmpty()) {

                                    //Map<String, NormalizationData> normalized_bodies = testParser.getNormalizedMethodsOfClass("/Users/afrinakhatun/IdeaProjects/jsoup/src/test/java", testClass);
                                    consoleOut.println("before");
                                    logHeapSize();
                                    Map<String, NormalizationResult> normalized_bodies = testParser.getNormalizedMethodsOfClass(singletonParser,REPO_PATH+"/"+TEST_PATH, testClass);
                                    consoleOut.println("after");
                                    logHeapSize();

                                    for (MethodModificationInformation temp_method : entry.getValue().added_or_modified_methods) {
                                        if (temp_method.modification_type.equals("add")) {
                                            for (Map.Entry<String,PreviousMethodInfo> existing : entry.getValue().previous_methods.entrySet()) {
                                                //if(!existing.getKey().equals("testWithinMultipleRanges")) continue;

                                                existing.getValue().previousMethodNormalizedBody = normalized_bodies.get(existing.getKey()).assertionFull_NormalizedSource;
                                                temp_method.normalized_method_body = normalized_bodies.get(temp_method.method_name).assertionFull_NormalizedSource;


                                                double testplanSimilarity = GumTreeSim.similarityClassic(normalized_bodies.get(existing.getKey()).assertionFull_NormalizedSource,
                                                        normalized_bodies.get(temp_method.method_name).assertionFull_NormalizedSource);
                                                temp_method.similarityScores.put(existing.getKey(),testplanSimilarity);

                                                double assertionSimilarity = AssertedObjectSimilarityChecker.checkAssertedObjectSimilarity(normalized_bodies.get(existing.getKey()).assertionMethodCalls,
                                                normalized_bodies.get(temp_method.method_name).assertionMethodCalls);
                                             // *****  temp_method.assertionSimilarityScores.put(existing.getKey(),assertionSimilarity);

                                                List<MethodCallSignature> existing_method_setup = new ArrayList<>();
                                                    Set<String> existing_methods_tested_objects = get_tested_object_list(normalized_bodies.get(existing.getKey()).regularMethodCalls,
                                                                                                                          normalized_bodies.get(existing.getKey()).assertionMethodCalls);

                                                    existing.getValue().noOfMethodCalls = normalized_bodies.get(existing.getKey()).regularMethodCalls.size();
                                                    List<MethodCallSignature> temp_method_setup = new ArrayList<>();
                                                    Set<String> temp_tested_objects = get_tested_object_list(normalized_bodies.get(temp_method.method_name).regularMethodCalls,
                                                                                                    normalized_bodies.get(temp_method.method_name).assertionMethodCalls);

                                                    if(temp_tested_objects.equals(existing_methods_tested_objects)){
                                                    }
                                                    else if(temp_tested_objects.containsAll(existing_methods_tested_objects)){
                                                        Set<String> extraObjects = new HashSet<>();
                                                        for(String ob:temp_tested_objects){
                                                            if(!existing_methods_tested_objects.contains(ob)){
                                                                extraObjects.add(ob);
                                                            }
                                                        }
                                                       //***** temp_method.extraObjects.put(existing.getKey(),extraObjects);
                                                    }

                                                    InputAmplificationDetector callChangedetector = new InputAmplificationDetector();
                                                    ArgumentAmplificationDetector argumentChangeDetector = new ArgumentAmplificationDetector();
                                                    AssertionAmplificationDetector assertionChangeDetector = new AssertionAmplificationDetector();

                                                    //if(existing_methods_tested_objects.equals(temp_tested_objects)){
                                                      if(testplanSimilarity >= 0.7 || assertionSimilarity >= 0.7){

                                                        System.out.println("i hav come here");
                                                        //See if the same objects are being tested
                                                        InputAmplificationDetector.MethodCallAmplificationResult call_amplification_result = callChangedetector.checkMethodCallAmplification(normalized_bodies.get(existing.getKey()),
                                                                normalized_bodies.get(temp_method.method_name));
                                                        List<String> temp_inputAmps = new ArrayList<>();
                                                        for(MethodCallSignature m: call_amplification_result.addedCalls){
                                                            temp_inputAmps.add("A-"+m.toString());
                                                        }
                                                        int countRemoved = 0;
                                                        for(MethodCallSignature m: call_amplification_result.deletedCalls){
                                                            temp_inputAmps.add("D-"+m.toString());
                                                            countRemoved += 1;
                                                        }
                                                        if(!temp_inputAmps.isEmpty()){
                                                           //***** temp_method.inputAmplifications.put(existing.getKey(),temp_inputAmps);
                                                        }
                                                        //check Argument amplification
                                                        if(!call_amplification_result.duplicatedCalls.isEmpty()){
                                                            List<DuplicateMethodInfo> temp_duplicateArgAmps = new ArrayList<>();
                                                            for(List<MethodCallSignature> pair : call_amplification_result.duplicatedCalls){
                                                                String callKey = "";
                                                                for(int i=0; i<pair.get(0).calls.size(); i++){
                                                                    callKey += pair.get(0).calls.get(i).methodName+"()";
                                                                }
                                                                if(pair.get(0).calls.size()>1){
                                                                    System.out.println();
                                                                }
                                                                List<MethodModificationInformation.ArgumentAmplificationType> listOfTypes = new ArrayList<>();
                                                                for(int i=0; i<pair.get(0).calls.size(); i++){
                                                                    MethodCall oldMethod = pair.get(0).calls.get(i);
                                                                    MethodCall modifiedMethod = pair.get(1).calls.get(i);
                                                                    if(oldMethod.arguments.size() == modifiedMethod.arguments.size()){
                                                                        for(int a=0;a<oldMethod.arguments.size();a++){
                                                                            MethodModificationInformation.ArgumentAmplificationType type = argumentChangeDetector.IdentifyArgumentChanges(oldMethod.arguments.get(a), modifiedMethod.arguments.get(a));
                                                                            listOfTypes.add(type);
                                                                        }
                                                                    }
                                                                    else{
                                                                        listOfTypes.add(MethodModificationInformation.ArgumentAmplificationType.ARGUMENT_SIZE_MISMATCH);
                                                                    }
                                                                }
                                                                temp_duplicateArgAmps.add(new DuplicateMethodInfo(callKey,listOfTypes));
                                                            }
                                                          //*****  temp_method.duplicateArgumentAmplification.put(existing.getKey(),temp_duplicateArgAmps);
                                                        }
                                                        if(!call_amplification_result.sameCalls.isEmpty()){
                                                            List<DuplicateMethodInfo> temp_sameArgAmps = new ArrayList<>();
                                                            for(List<MethodCallSignature> pair : call_amplification_result.sameCalls){
                                                                String callKey = "";
                                                                for(int i=0; i<pair.get(0).calls.size(); i++){
                                                                    callKey += pair.get(0).calls.get(i).methodName+"()";
                                                                }
                                                                List<MethodModificationInformation.ArgumentAmplificationType> listOfTypes = new ArrayList<>();
                                                                for(int i=0; i<pair.get(0).calls.size(); i++){
                                                                    MethodCall oldMethod = pair.get(0).calls.get(i);
                                                                    MethodCall modifiedMethod = pair.get(1).calls.get(i);
                                                                    if(oldMethod.arguments.size() == modifiedMethod.arguments.size()){
                                                                        for(int a=0;a<oldMethod.arguments.size();a++){
                                                                            MethodModificationInformation.ArgumentAmplificationType type = argumentChangeDetector.IdentifyArgumentChanges(oldMethod.arguments.get(a),
                                                                                    modifiedMethod.arguments.get(a));
                                                                            listOfTypes.add(type);
                                                                        }
                                                                    }
                                                                    else{
                                                                        listOfTypes.add(MethodModificationInformation.ArgumentAmplificationType.ARGUMENT_SIZE_MISMATCH);
                                                                    }
                                                                }
                                                                temp_sameArgAmps.add(new DuplicateMethodInfo(callKey, listOfTypes));
                                                            }
                                                          //*****  temp_method.sameMethodArgumentAmplification.put(existing.getKey(),temp_sameArgAmps);
                                                        }

                                                        //check Assertion Amplification();
                                                        AssertionAmplificationDetector.AssertionAmplificationResult assertionAmplificationResult = assertionChangeDetector.checkAssertionAmplification(normalized_bodies.get(existing.getKey()),
                                                                normalized_bodies.get(temp_method.method_name));
                                                        List<Character> assertionMethodAmp = new ArrayList<>();
                                                        int assertionAttrAmp = 0;
                                                        for (Map.Entry<String, List<AssertionAmplificationDetector.TempAssertionCall>> key : assertionAmplificationResult.addedMethodObservations.entrySet()){
                                                            for(AssertionAmplificationDetector.TempAssertionCall tempCall:key.getValue()){
                                                                for(AssertArgument arg:tempCall.assertionCall.arguments){
                                                                    if(arg.argumentType.equals("MTH")){
                                                                        if(arg.isGetterMethod){
                                                                            assertionMethodAmp.add('G');
                                                                        }
                                                                        else{
                                                                            assertionMethodAmp.add('N');
                                                                        }
                                                                    }
                                                                }

                                                            }
                                                        }
                                                        if(!assertionMethodAmp.isEmpty()){
                                                        //*****    temp_method.assertionMethodAmplification.put(existing.getKey(), assertionMethodAmp);
                                                        }
                                                        for (Map.Entry<String, List<AssertionAmplificationDetector.TempAssertionCall>> key : assertionAmplificationResult.addedAttributeObservations.entrySet()){
                                                            for(AssertionAmplificationDetector.TempAssertionCall tempCall:key.getValue()){
                                                                assertionAttrAmp += 1;
                                                            }
                                                        }
                                                        if(assertionAttrAmp!=0){
                                                            //*****  temp_method.assertionAttributeAmplification.put(existing.getKey(),assertionAttrAmp);
                                                        }

                                                    }
                                                    //else if(temp_tested_objects.containsAll(existing_methods_tested_objects)){
                                                        //System.out.println("i hav come here added");

                                                    //}
                                                    //else{
                                                        //System.out.println("matching sets working ???");
                                                   // }
                                            }

                                        }
                                    }

                                }
                            }
                        } else {
                            // System.out.println("No test methods found in commit: " + commitHash);
                        }
                        //save all to json
                        CommitRecordJsonHandler.appendRecord(
                                jsonFile,
                                new CommitRecord(commitHash, changedTestMethods, committedFiles, commitMessage,false)
                        );
                        changedTestMethods.clear();


                    } catch (Exception e) {
                        System.err.println("Error while processing commit " + commitHash + ": " + e.getMessage());
                    } finally {
                        // Always restore original pom.xml if we had backed it up
                        if (originalPomContent != null) {
                            try {
                                writeFile(REPO_PATH + "/pom.xml", originalPomContent);
                                //System.out.println("Restored original pom.xml after processing commit");
                            } catch (Exception e) {
                                System.err.println("Failed to restore original pom.xml: " + e.getMessage());
                            }
                        }
                        // Always reset hard and checkout back to master
                        git.reset().setMode(ResetCommand.ResetType.HARD).call();
                        git.clean()
                                .setCleanDirectories(true)
                                .setIgnore(false)
                                .setForce(true)
                                .call();

                        // 3) Extra safety: explicitly delete that file if it still exists
                        Path pkgHtml = Paths.get(
                                REPO_PATH,
                                "src/main/java/org/apache/commons/io/serialization/package.html"
                        );
                        try {
                            java.nio.file.Files.deleteIfExists(pkgHtml);
                            System.out.println("THIS IS HAPPENING !!!");
                        } catch (IOException ioe) {
                            System.out.println("Could not delete package.html: " + ioe.getMessage());
                        }

                        try{
                            git.checkout().setName(masterCommitHash).setForce(true).call();
                        }
                        catch (Exception e){
                            System.out.println("MASTER checking out issue");
                            e.printStackTrace();
                        }

                        //System.out.println("Restored back to saved master commit: " + masterCommitHash);
                    }
                } else {
                    // System.out.println("No test files changed in commit: " + commitHash);
                }
            }
        }
        System.gc();
    }

    public static boolean areSetsEqual(Set<String> set1, Set<String> set2) {
        if (set1.size() != set2.size()) return false;

        for (String s : set1) {
            if (!set2.contains(s)) return false;
        }

        return true;
    }

    public static void deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directoryToBeDeleted.delete();
    }

    private static void writeFile(String filePath, String content) throws Exception {
        Files.write(new File(filePath).toPath(), content.getBytes(StandardCharsets.UTF_8));
    }


    private static List<RevCommit> listCommitsByDate(Git git) throws Exception {
        Iterable<RevCommit> commits = git.log().all().call();
        List<RevCommit> commitList = new ArrayList<>();
        for (RevCommit commit : commits) {
            commitList.add(commit);
        }
        // Sort by commit date (ascending: oldest first)
        commitList.sort(Comparator.comparingLong(RevCommit::getCommitTime));
        return commitList;
    }
    // NEW: restrict traversal to the given branch only, newest → oldest
    private static List<RevCommit> listCommitsOnBranch(Git git, String branchName) throws Exception { // NEW
        // Resolve the branch head explicitly as a ref
        ObjectId branchHead = git.getRepository().resolve("refs/heads/" + branchName);
        if (branchHead == null) {
            throw new IllegalStateException("Branch not found: " + branchName);
        }

        Iterable<RevCommit> logs = git.log()
                .add(branchHead)   // only commits reachable from this branch head
                .call();

        List<RevCommit> commitList = new ArrayList<>();
        for (RevCommit commit : logs) {
            commitList.add(commit);
        }

        // JGit returns newest → oldest by default (reverse chronological).
        // If at some point you want oldest → newest, you can:
        // Collections.reverse(commitList);
        commitList.sort(Comparator.comparingLong(RevCommit::getCommitTime));
        //Collections.reverse(commitList);
        return commitList;
    }

    private static Set<String> getChangedFiles(Git git, RevCommit commit) throws Exception {
        Set<String> changedFiles = new HashSet<>();
        Repository repository = git.getRepository();

        // Validate commit tree
        if (commit.getTree() == null) {
            System.err.println("Commit " + commit.getId().getName() + " has no tree, skipping");
            return changedFiles;
        }

        // Handle initial commit (no parents)
        if (commit.getParentCount() == 0) {
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(commit.getTree());
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathSuffixFilter.create(".java")); // Filter for .java files
                while (treeWalk.next()) {
                    String path = treeWalk.getPathString();
                    if (path.startsWith(TEST_PATH + "/") && isATestFile(path)) { //path.endsWith(".java")
                        changedFiles.add(path);
                    }
                }
                treeWalk.reset();
            }
            System.out.println("Initial commit " + commit.getId().getName() + ": treating all files as additions");
            return changedFiles;
        }

        // Parse parent commit explicitly
        RevCommit parent;
        try (RevWalk revWalk = new RevWalk(repository)) {
            parent = revWalk.parseCommit(commit.getParent(0).getId());
        } catch (Exception e) {
            System.out.println("Failed to parse parent commit for " + commit.getId().getName() + ": " + e.getMessage() + ", treating as initial commit");
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(commit.getTree());
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathSuffixFilter.create(".java")); // Filter for .java files
                while (treeWalk.next()) {
                    changedFiles.add(treeWalk.getPathString());
                }
            }
            return changedFiles;
        }

        // Validate parent tree
        if (parent.getTree() == null) {
            System.err.println("Parent commit " + parent.getId().getName() + " has no tree, treating as initial commit");
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(commit.getTree());
                treeWalk.setRecursive(true);
                while (treeWalk.next()) {
                    changedFiles.add(treeWalk.getPathString());
                }
            }
            return changedFiles;
        }

        try (ObjectReader reader = repository.newObjectReader()) {
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            oldTreeIter.reset(reader, parent.getTree());
            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, commit.getTree());

            List<DiffEntry> diffs = git.diff()
                    .setNewTree(newTreeIter)
                    .setOldTree(oldTreeIter)
                    .setPathFilter(PathSuffixFilter.create(".java")) // Filter for .java files
                    .call();

            for (DiffEntry entry : diffs) {
                String path;
                switch (entry.getChangeType()) {
                    case ADD:
                    case MODIFY:
                        path = entry.getNewPath();
                        break;
                    case DELETE:
                        path = entry.getOldPath();
                        break;
                    default:
                        path = entry.getNewPath() != null ? entry.getNewPath() : entry.getOldPath();
                }
                changedFiles.add(path);
            }
        }
        return changedFiles;
    }

    private static Map<String, Set<String>> findChangedTestMethods(Git git, String commitHash) throws Exception {
        Repository repository = git.getRepository();
        RevCommit commit = repository.parseCommit(ObjectId.fromString(commitHash));
        RevCommit parent = commit.getParentCount() > 0 ? repository.parseCommit(commit.getParent(0).getId()) : null;

        Map<String, Set<String>> changedMethods = new HashMap<>();
        Set<String> changedTestFiles = getChangedFiles(git, commit).stream()
                .filter(file -> file.startsWith(TEST_PATH + "/") && isATestFile(file))
                .collect(Collectors.toSet());

        for (String filePath : changedTestFiles) {
            String afterContent = getFileContent(git, commit, filePath);
            String beforeContent = parent != null ? getFileContent(git, parent, filePath) : "";

            Set<String> methodsBefore = parseTestMethods(beforeContent);
            Set<String> methodsAfter = parseTestMethods(afterContent);

            // Identify added, modified, or deleted methods
            Set<String> changedTestMethods = new HashSet<>();
            // Added or modified
            for (String method : methodsAfter) {
                if (!methodsBefore.contains(method)) {
                    changedTestMethods.add(method);
                }
            }
            // Deleted
            for (String method : methodsBefore) {
                if (!methodsAfter.contains(method)) {
                    changedTestMethods.add(method);
                }
            }

            if (!changedTestMethods.isEmpty()) {
                String testClass = filePath.replace(TEST_PATH + "/", "").replace(".java", "").replace("/", ".");
                changedMethods.put(testClass, changedTestMethods);
            }
        }
        System.out.println("found changed methods:"+changedMethods.size());
        return changedMethods;
    }

    private static String getFileContent(Git git, RevCommit commit, String filePath) throws Exception {
        Repository repository = git.getRepository();
        try (TreeWalk treeWalk = TreeWalk.forPath(repository, filePath, commit.getTree())) {
            if (treeWalk == null) {
                return ""; // File doesn't exist in this commit
            }
            ObjectId objectId = treeWalk.getObjectId(0);
            /*try (ObjectReader reader = repository.newObjectReader()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                reader.open(objectId).copyTo(out);
                return out.toString(StandardCharsets.UTF_8);
            }*/

            try (ObjectReader reader = repository.newObjectReader()) {
                // Stream content instead of loading into ByteArrayOutputStream
                try (InputStream inputStream = reader.open(objectId).openStream()) {
                    StringBuilder content = new StringBuilder();
                    byte[] buffer = new byte[8192]; // 8KB chunks
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        content.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
                    }
                    return content.toString();
                }
            }
        }
    }

    private static Set<String> parseTestMethods(String content) {
        Set<String> methods = new HashSet<>();
        Matcher matcher = TEST_METHOD_PATTERN.matcher(content);
        while (matcher.find()) {
            methods.add(matcher.group(1));
        }
        return methods;
    }

    private static void runTestWithCoverage(String testClass, String testMethod, String commitHash) throws Exception {
        String sanitizedTestClass = testClass.replace(".", "_");
        String customExecFileName = "jacoco-" + commitHash + "-" + sanitizedTestClass + "-" + testMethod + ".exec";
        //File customExecFile = new File(TARGET_DIR, customExecFileName);

        System.out.println("Running test: " + testClass + "#" + testMethod);
        //System.out.println("Expected JaCoCo output file: " + customExecFile.getAbsolutePath());



        // Also delete default jacoco.exec before test (clean slate)
        File defaultExecFile = new File(TARGET_DIR, "jacoco.exec");
        if (defaultExecFile.exists()) {
            defaultExecFile.delete();
        }

        try {
            // Run the test using Maven
            runMavenTest(testClass, testMethod);

            // Create your custom exec folder if not exists
            File execFolder = new File(REPO_PATH + Coverage_Folder + "/"+commitHash +"/"+ testClass);
            if (!execFolder.exists()) {
                execFolder.mkdirs();
            }

            // After test completes, move the default jacoco.exec to custom named exec file
            if (defaultExecFile.exists()) {
                File customExecFile = new File(execFolder, customExecFileName);
                Files.move(defaultExecFile.toPath(), customExecFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                //System.out.println("✅ Moved jacoco.exec to: " + customExecFile.getAbsolutePath());

                try {
                    //generate coverage report
                    JacocoReportHandler.generateXmlReport(
                            REPO_PATH + Coverage_Folder + "/" + commitHash + "/" + testClass + "/" + "jacoco-" + commitHash + "-" + sanitizedTestClass + "-" + testMethod + ".exec",
                            TARGET_DIR + "/classes",
                            REPO_PATH + SOURCE_PATH,
                            REPO_PATH + Coverage_Folder + "/" + commitHash + "/" + testClass,
                            "jacoco-" + commitHash + "-" + sanitizedTestClass + "-" + testMethod,
                            REPO_PATH + Coverage_Folder + "/" + commitHash + "/" + testClass+"/html-"+testMethod
                    );

                }catch(Exception e){
                    System.err.println("❌ Coverage could not be generated" );
                }
            } else {
                System.err.println("❌ jacoco.exec not found after running test: " + testClass + "#" + testMethod);
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to run test or move jacoco.exec for " + testClass + "#" + testMethod + ": " + e.getMessage());
        }
    }


    private static void runMavenTest(String testClass, String testMethod) throws Exception {
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(new File(REPO_PATH + "/pom.xml"));
        request.setGoals(Arrays.asList(
                "clean",
                "test"
        ));

        Properties properties = new Properties();
        properties.setProperty("test", testClass + "#" + testMethod);// specify test class and method
        properties.setProperty("surefire.printSummary", "true");
        properties.setProperty("surefire.redirectTestOutputToFile", "true");
        request.setProperties(properties);

        // Set quiet mode (-q)
        //request.setQuiet(true);

        Invoker invoker = new DefaultInvoker();
        invoker.setMavenHome(new File(System.getenv("M2_HOME") != null ? System.getenv("M2_HOME") : "/opt/homebrew/Cellar/maven/3.9.9/libexec"));

        // Suppress all Maven logs
        invoker.setOutputHandler(null);
        invoker.setErrorHandler(System.err::println);

        InvocationResult result = invoker.execute(request);

        if (result.getExitCode() != 0) {
            throw new MavenInvocationException("Maven test execution failed for " + testClass + "#" + testMethod);
        }
    }

    public static void loggingSetUp() throws FileNotFoundException, UnsupportedEncodingException {
        // Save original console output
        consoleOut = System.out;
        PrintStream consoleErr = System.err;

        // File output setup (overwrite at start)
        FileOutputStream fos = new FileOutputStream("/Users/afrinakhatun/IdeaProjects/Analyzer/LogData/"+projectName+"logData.txt", false);
        BufferedOutputStream bos = new BufferedOutputStream(fos, 8192);
        fileOut = new PrintStream(bos, true, "UTF-8");

        // Tee output: to both file and console
        bothOut = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                fileOut.write(b);
                consoleOut.write(b);
            }

            @Override
            public void flush() throws IOException {
                fileOut.flush();
                consoleOut.flush();
            }
        }, true, "UTF-8");

        // Redirect standard outputs
        System.setOut(fileOut);
        System.setErr(fileOut); // Optional — or use separate handling

        // === Usage ===

        // Goes to both console + file
        System.out.println("Both: Hello world!");

        // File only
        fileOut.println("File only: This won’t be seen on console.");

        // Console only
        consoleOut.println("Console only: For real-time tracking!");

        // Error (goes to both if using System.err)
        System.err.println("Error: also goes to both.");

        // Clean up (optional at program end)
        //fileOut.close();
    }
    private static Map<String, String> parseTestMethodsWithBodies(String content) {
        Map<String, String> methodMap = new HashMap<>();
        JavaParser parser = new JavaParser();

        ParseResult<CompilationUnit> result = parser.parse(content);
        if (!result.isSuccessful() || !result.getResult().isPresent()) {
            return methodMap;  // Return empty map on parse failure
        }

        CompilationUnit cu = result.getResult().get();

        /*cu.findAll(MethodDeclaration.class).forEach(method -> {
            for (AnnotationExpr annotation : method.getAnnotations()) {
                if (annotation.getNameAsString().equals("Test")) {
                    String methodName = method.getNameAsString();
                    String methodBody = method.toString();  // Includes full method code
                    methodMap.put(methodName, methodBody);
                    break;
                }
            }
        });*/

        List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
        for (MethodDeclaration method : methods) {
            List<AnnotationExpr> annotations = method.getAnnotations();
            boolean isTest = false;
            for (AnnotationExpr annotation : annotations) {
                if ("Test".equals(annotation.getNameAsString()) || annotation.getNameAsString().endsWith(".Test")) {
                    isTest = true;
                    break;
                }
            }
            boolean looksLikeJUnit3 =
                    method.getNameAsString().startsWith("test")
                            && method.isPublic()
                            && method.getParameters().isEmpty()
                            && method.getType().isVoidType();

            if (isTest || looksLikeJUnit3) {
                String methodName = method.getNameAsString();
                String methodBody = method.toString();  // includes full body
                methodMap.put(methodName, methodBody);
            }
        }

        return methodMap;
    }
    private static Map<String,MethodList> updatedFindChangedTestMethods(Git git, String commitHash) throws Exception {
        //Repository repository = git.getRepository();
        RevCommit commit = git.getRepository().parseCommit(ObjectId.fromString(commitHash));
        //RevCommit parent = commit.getParentCount() > 0 ? repository.parseCommit(commit.getParent(0).getId()) : null;

        Map<String, MethodList> all_methods = new HashMap<>();
        Set<String> changedTestFiles = getChangedFiles(git, commit).stream()
                .filter(file -> file.startsWith(TEST_PATH + "/") && isATestFile(file))
                .collect(Collectors.toSet());

        for (String filePath : changedTestFiles) {
            String afterContent = getFileContent(git, commit, filePath);
            RevCommit parent = findPreviousChangeOnFile(git,commit,filePath);
            String beforeContent = parent != null ? getFileContent(git, parent, filePath) : "";

            //Set<String> methodsBefore = parseTestMethods(beforeContent);
            //Set<String> methodsAfter = parseTestMethods(afterContent);

            Map<String, String> methodsBefore = parseTestMethodsWithBodies(beforeContent);
            Map<String, String> methodsAfter = parseTestMethodsWithBodies(afterContent);

            // Identify added, modified, or deleted methods
            ArrayList<MethodModificationInformation> changedTestMethods = new ArrayList<>();
            //Set<String> existingTestMethods = new HashSet<>();
            Map<String, PreviousMethodInfo> existingTestMethods = new HashMap<>();

            MethodList methods = new MethodList();

            if(methodsBefore.isEmpty()){
                if(methodsAfter.isEmpty()) {
                    String testClass = filePath.replace(TEST_PATH + "/", "").replace(".java", "").replace("/", ".");
                    all_methods.put(testClass, methods);
                }
                else{
                    for (Map.Entry<String, String> entry : methodsAfter.entrySet()) {
                        String methodName = entry.getKey();
                        changedTestMethods.add(new MethodModificationInformation(methodName,"add",entry.getValue()));
                    }

                }
            }
            else{
                if(methodsAfter.isEmpty()){
                    String testClass = filePath.replace(TEST_PATH + "/", "").replace(".java", "").replace("/", ".");
                    all_methods.put(testClass, methods);
                }
                else{
                    // Detect added or modified
                    for (Map.Entry<String, String> entry : methodsAfter.entrySet()) {
                        String methodName = entry.getKey();
                        String methodBodyAfter = entry.getValue();

                        String normalizedBefore = null;
                        if(methodsBefore.containsKey(methodName)){
                            normalizedBefore = normalizeMethod(methodsBefore.get(methodName));
                        }

                        String normalizedAfter = normalizeMethod(methodBodyAfter);


                        if (!methodsBefore.containsKey(methodName)) {
                            changedTestMethods.add(new MethodModificationInformation(methodName,"add",methodBodyAfter)); // Added
                        }
                        else{
                            if (!normalizedBefore.equals(normalizedAfter)) {
                                changedTestMethods.add(new MethodModificationInformation(methodName,"mod",methodBodyAfter)); // Modified
                            }
                            else{
                                PreviousMethodInfo pm = new PreviousMethodInfo();
                                pm.previousMethodBody = methodBodyAfter;
                                existingTestMethods.put(methodName,pm); //Unchanged
                            }
                        }
                    }
                }
            }


            // Detect deleted
            /*for (String methodName : methodsBefore.keySet()) {
                if (!methodsAfter.containsKey(methodName)) {
                    changedTestMethods.add(methodName); // Deleted
                }
            }*/

            if (!changedTestMethods.isEmpty()) {
                String testClass = filePath.replace(TEST_PATH + "/", "").replace(".java", "").replace("/", ".");
                methods.added_or_modified_methods.addAll(changedTestMethods);
                methods.previous_methods.putAll(existingTestMethods);
                methods.parent_commit_hash = parent != null ? parent.getId().getName() : null;
                all_methods.put(testClass, methods);
            }

        }
        System.out.println("found changed methods:"+all_methods.size());
        return all_methods;
    }
    private static String normalizeMethod(String methodCode) {
        // Remove whitespace, comments, and normalize indentation
        return methodCode
                .replaceAll("//.*", "")             // remove line comments
                .replaceAll("/\\*.*?\\*/", "")      // remove block comments
                .replaceAll("\\s+", "")             // remove all whitespace
                .trim();
    }
    private static Set<String> listJavaFilesInCommit(Git git, RevCommit commit) throws Exception {
        Set<String> javaFiles = new HashSet<>();
        Repository repository = git.getRepository();

        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true); // walk all files recursively

            while (treeWalk.next()) {
                String path = treeWalk.getPathString();
                if (path.endsWith(".java")) {
                    javaFiles.add(path);
                }
            }
        }
        return javaFiles;
    }
    //ulala
    public static RevCommit findPreviousChangeOnFile(Git git, RevCommit currentCommit, String filePath) throws Exception {

        boolean skipCurrent = false;
        Iterable<RevCommit> fileHistory = git.log()
                .add(git.getRepository().resolve("refs/heads/"+branchName))
                .addPath(filePath)
                .call();

        for (RevCommit commit : fileHistory) {
            if (!skipCurrent && commit.equals(currentCommit)) {
                skipCurrent = true; // skip the current commit itself
                continue;
            }

            if (skipCurrent) {
                return commit; // this is the previous commit that changed the file
            }
        }

        return null; // no previous commit found that changed the file
    }

    static class ObjectUsage {
        String type;
        String constructor;
        List<String> methodCalls = new ArrayList<>();
    }

    static class MethodStructure {
        String methodName;
        Map<String, ObjectUsage> objects = new LinkedHashMap<>();
        List<String> assertions = new ArrayList<>();
        List<String> standaloneCalls = new ArrayList<>();
        List<String> unscopedConstructors = new ArrayList<>();
    }

    public static void parsePerTestMethodForStaticAnalysis(String sourceRootPath, String className) throws IOException {
        TypeSolver typeSolver = new CombinedTypeSolver(
                new ReflectionTypeSolver(),
                new JavaParserTypeSolver(new File(sourceRootPath))
        );
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        ParserConfiguration config = new ParserConfiguration().setSymbolResolver(symbolSolver);
        JavaParser parser = new JavaParser(config);

        Path filePath = findFileByClassName(Paths.get(sourceRootPath), className);
        if (filePath == null) {
            System.err.println("Class file not found for: " + className);
            return;
        }

        CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow();

        List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
        for (int i = 0; i < methods.size(); i++) {
            MethodDeclaration method = methods.get(i);
            if (!method.isAnnotationPresent("Test")) continue;

            System.out.println("\n=== Method: " + method.getNameAsString() + " ===");

            Map<String, String> variableMap = new LinkedHashMap<>();
            Set<String> customMethodCalls = new LinkedHashSet<>();
            Set<String> assertionNestedCalls = new LinkedHashSet<>();

            // 1. Extract custom variables with their types
            List<VariableDeclarationExpr> varDecls = method.findAll(VariableDeclarationExpr.class);
            for (VariableDeclarationExpr vde : varDecls) {
                for (VariableDeclarator var : vde.getVariables()) {
                    String varName = var.getNameAsString();
                    String varType;
                    try {
                        varType = var.getType().resolve().describe();
                    } catch (Exception e) {
                        varType = var.getType().toString();
                    }
                    variableMap.put(varName, varType);
                }
            }

            // 2. Identify all method calls
            List<MethodCallExpr> allCalls = method.findAll(MethodCallExpr.class);
            for (MethodCallExpr mce : allCalls) {
                boolean insideAssert = false;
                Optional<Node> parent = mce.getParentNode();
                while (parent.isPresent()) {
                    Node p = parent.get();
                    if (p instanceof MethodCallExpr parentCall && parentCall.getNameAsString().startsWith("assert")) {
                        insideAssert = true;
                        break;
                    }
                    parent = p.getParentNode();
                }
                String resolved = resolveCustomCall(mce, variableMap);
                if (resolved != null) {
                    if (insideAssert) assertionNestedCalls.add(resolved);
                    else customMethodCalls.add(resolved);
                }
            }

            // Output
            System.out.println("\nExtracted Variable Map for method " + method.getNameAsString() + ":");
            for (Map.Entry<String, String> entry : variableMap.entrySet()) {
                System.out.println(entry.getKey() + " : " + entry.getValue());
            }

            System.out.println("\n{");
            for (String call : customMethodCalls) {
                System.out.println("  " + call + ",");
            }
            System.out.println("  assertions:{");
            for (String call : assertionNestedCalls) {
                System.out.println("    " + call + ",");
            }
            System.out.println("  }");
            System.out.println("}\n");
        }
    }

    private static String resolveCustomCall(MethodCallExpr mce, Map<String, String> variableMap) {
        try {
            ResolvedMethodDeclaration resolved = mce.resolve();
            String scopeType;
            if (mce.getScope().isPresent()) {
                Expression scope = mce.getScope().get();
                try {
                    scopeType = scope.calculateResolvedType().describe();
                } catch (Exception e) {
                    if (scope.isNameExpr()) {
                        String varName = scope.asNameExpr().getNameAsString();
                        scopeType = variableMap.getOrDefault(varName, null);
                        if (scopeType == null) return null;
                    } else return null;
                }
                if (!scopeType.startsWith("java.")) {
                    return scopeType + "#" + resolved.getName();
                }
            } else {
                String classNameResolved = resolved.getQualifiedName().replace("." + resolved.getName(), "");
                if (!classNameResolved.startsWith("java.")) {
                    return classNameResolved + "#" + resolved.getName();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }


    private static Path findFileByClassName(Path root, String className) throws IOException {
        String[] parts = className.split("\\.");
        String name = parts[parts.length - 1];

        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(name+ ".java"))
                    .findFirst()
                    .orElse(null);
        }
    }

    public void normalizeMethodBody(){

    }
     public static Set<String> get_tested_object_list(List<MethodCallSignature> regularMethodCalls,
                                                      List<AssertionCall> assertionCalls){
        Set<String> tested_object_list = new HashSet<>();
        for(MethodCallSignature callSignature: regularMethodCalls){
            tested_object_list.add(callSignature.receiverType);
        }
        for(AssertionCall assertionCall: assertionCalls){
            if(assertionCall.arguments.size() == 1){
                if(assertionCall.arguments.get(0).argumentType.equals("MTH") ||
                        assertionCall.arguments.get(0).argumentType.equals("FLD") ||
                        assertionCall.arguments.get(0).argumentType.equals("NAM")){
                    tested_object_list.add(assertionCall.arguments.get(0).receiverType);
                }
            }
            else if(assertionCall.arguments.size() > 1){
                for(int k=1;k<assertionCall.arguments.size();k++){
                    if(assertionCall.arguments.get(k).argumentType.equals("MTH") ||
                            assertionCall.arguments.get(k).argumentType.equals("FLD") ||
                            assertionCall.arguments.get(k).argumentType.equals("NAM")){
                        tested_object_list.add(assertionCall.arguments.get(k).receiverType);
                    }
                }
            }
        }
        return tested_object_list;
     }

     public static boolean isATestFile(String path){
        if(path == null){
            return false;
        }
        String fileName = new File(path).getName();
        for(String p:TEST_SUFFIXES){
            if(path.endsWith(p)){
                return true;
            }
        }
         // prefix-based detection (e.g., TestStyle.java, TestUser.java)
         if (fileName.startsWith("Test") && fileName.endsWith(".java")) {
             return true;
         }
        return false;
     }

    public static int compileProjectForTests(String repoPath)
            throws IOException, InterruptedException {
        String java17Home = "/opt/homebrew/Cellar/openjdk@17/17.0.17/libexec/openjdk.jdk/Contents/Home";
        if(projectName.equals("mybatis")){
            java17Home = "/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home";
        }
        else if(projectName.equals("commons-codec")){
            java17Home = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home";
        }
        else if(projectName.equals("commons-validator")){
            java17Home = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home";
        }
        else if(projectName.equals("commons-net")){
            java17Home = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home";
        }
        else if(projectName.equals("commons-collections")){
            java17Home = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home";
        }
        ProcessBuilder pb = new ProcessBuilder(
                "mvn",
                //"-q",
                "-DskipTests",       // skip running tests, but still compile them
                "-DskipITs",
                "-Drat.skip=true",
                "-DskipRat=true",
                "-Dproject.build.sourceEncoding=ISO-8859-1",
                "-Dmaven.compiler.encoding=ISO-8859-1",
                "-Dmaven.compiler.testEncoding=ISO-8859-1",
                "-Dfile.encoding=ISO-8859-1",//ISO-8859-1
                "clean","test-compile"
                      // compiles main + test sources
        );
        Map<String, String> env = pb.environment();
        env.put("JAVA_HOME", java17Home);

        // Prepend JDK 17 bin to PATH
        String pathVarName = "PATH";
        String existingPath = env.getOrDefault(pathVarName, System.getenv(pathVarName));
        env.put(
                pathVarName,
                java17Home + File.separator + "bin"
                        + File.pathSeparator
                        + (existingPath != null ? existingPath : "")
        );
        env.put("MAVEN_OPTS", "-Dfile.encoding=ISO-8859-1");

        pb.directory(Paths.get(repoPath).toFile());
        pb.redirectErrorStream(true); // merge stderr into stdout

        Process process = pb.start();

        boolean sawFailure = false;
        boolean sawCompilationError = false;

        // Print minimal build logs
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[mvn]: " + line);

                if (line.contains("BUILD FAILURE")) {
                    sawFailure = true;
                }
                if (line.contains("COMPILATION ERROR")){
                    sawCompilationError = true;
                }
                if (line.contains("Failed to execute goal")){
                    sawFailure = true;
                }
                if (line.contains("There are test failures")){
                    sawFailure = true;
                }
                if (line.contains("ERROR")){
                    sawFailure = true;
                }
            }
        }

        int exitCode = process.waitFor();
        // Override “0” if output proves failure
        /*if (exitCode == 0 && (sawFailure || sawCompilationError)) {
            exitCode = 1;
        }*/

        System.out.println("Maven compile exit code: " + exitCode);
        return exitCode;
    }

    public static int compileProjectWithJdk8(String repoPath)
            throws IOException, InterruptedException {

        // ✅ Mac (Homebrew Temurin 8 is usually here)
        // Check yours with: /usr/libexec/java_home -V
        String java8Home = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home";

        // If you installed "zulu8", "adoptopenjdk8", etc. update path accordingly.

        ProcessBuilder pb = new ProcessBuilder(
                "mvn",
                "-q",
                "-DskipITs",
                "-Drat.skip=true",
                "-DskipRat=true",

                // Encoding knobs (safe for old commits with weird chars)
                "-Dproject.build.sourceEncoding=ISO-8859-1",
                "-Dmaven.compiler.encoding=ISO-8859-1",
                "-Dmaven.compiler.testEncoding=ISO-8859-1",
                "-Dfile.encoding=ISO-8859-1",

                "test-compile"
        );

        Map<String, String> env = pb.environment();

        // ✅ Force JDK 8
        env.put("JAVA_HOME", java8Home);

        // ✅ Prepend JDK 8 bin to PATH
        String pathVarName = "PATH";
        String existingPath = env.getOrDefault(pathVarName, System.getenv(pathVarName));
        env.put(
                pathVarName,
                java8Home + File.separator + "bin"
                        + File.pathSeparator
                        + (existingPath != null ? existingPath : "")
        );

        // ✅ Ensure Maven forks/javac see encoding
        env.put("MAVEN_OPTS", "-Dfile.encoding=ISO-8859-1");

        pb.directory(Paths.get(repoPath).toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[mvn]: " + line);
            }
        }

        int exitCode = process.waitFor();
        System.out.println("Maven compile exit code: " + exitCode);
        return exitCode;
    }


    public static List<String> generateJavapFile(String testClass) throws IOException {
        Path root = Paths.get(REPO_PATH, "target", "test-classes");
        if (!Files.exists(root)) {
            throw new IllegalStateException("target/test-classes does not exist. Test-compile UnSuccessful.");
        }
        String dotReplacedPath = testClass.replace('.', '/');
        String testFilePath = REPO_PATH + "/target/test-classes/" + dotReplacedPath + ".class";
        int x=0;
        if (Files.exists(Paths.get(testFilePath))) {
            System.out.println("the file exists.");

            ProcessBuilder pb = new ProcessBuilder(
                    "javap",
                    "-c",                      // disassemble bytecode
                    testFilePath      // full path to .class file
            );

            // Optionally set working dir, but not required
            // pb.directory(new File("/"));

            pb.redirectErrorStream(true);
            String line;
            try{
                Process process = pb.start();

                StringBuilder out = new StringBuilder();
                List<String> lines = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {

                    while ((line = reader.readLine()) != null) {
                        //out.append(line).append(System.lineSeparator());
                        lines.add(line);
                    }
                }

                int exit = process.waitFor();
                if (exit != 0) {
                    System.err.println("javap failed for " + testFilePath + " (exit " + exit + ")");
                }
                return lines;
            }
            catch(Exception e){
                System.out.println(e.getMessage());
                return null;
            }

        }
        return null;
    }

}
