package org.example;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

public class NewTestCoverageAnalyzer {
    private static final Logger mainLogger = Logger.getLogger(NewTestCoverageAnalyzer.class.getName());
    private static final String REPO_PATH = "/Users/afrinakhatun/IdeaProjects/jsoup";
    private static final String SOURCE_PATH = "/src/main/java";
    private static final String TEST_PATH = "src/test/java";
    private static final String TARGET_DIR = REPO_PATH + "/target";
    private static final Pattern TEST_METHOD_PATTERN = Pattern.compile(
            "(?:@Test\\s*(?:\\([^)]*\\))?\\s*)?public\\s+void\\s+(\\w+)\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w,\\s]+)?\\s*\\{",
            Pattern.MULTILINE
    );
    static final String Coverage_Folder = "/coverageData";
    static  PrintStream consoleOut = null;
    static  PrintStream fileOut = null;
    static  PrintStream bothOut = null;
    static  PrintStream bothErr = null;


    public static void main(String[] args) throws Exception {
        //JacocoReportHandler.reportParser();
        //System.exit(0);

        loggingSetUp();
        //json handler
        Path jsonFile = Paths.get("/Users/afrinakhatun/IdeaProjects/Analyzer/commit-test-change-history.ndjson");
        CommitRecordJsonHandler.resetFile(jsonFile);

        // Your large program continues here...
        int x = 0;
        int counter =1;
        int hasTestCounter = 0;
        File coverageFolder = new File(REPO_PATH + Coverage_Folder);
        if(coverageFolder.exists()){
            deleteDirectory(coverageFolder);
        }else{
            coverageFolder.mkdir();
        }


        File repoDir = new File(REPO_PATH);
        try (Git git = Git.open(repoDir)) {
            // Save the current master commit hash
            String masterCommitHash = git.getRepository().resolve("latest-xml-only-branch").getName();
            fileOut.println("Saved current local master commit: " + masterCommitHash);

            // Parse master commit properly
            RevCommit masterCommit = git.getRepository().parseCommit(ObjectId.fromString(masterCommitHash));

            // Save master pom.xml content
            String masterPomContent = getFileContent(git, masterCommit, "pom.xml");
            fileOut.println("Saved master branch pom.xml");

            git.checkout().setName("latest-xml-only-branch").call();
            fileOut.println("Checked out master branch");

            List<RevCommit> commits = listCommitsByDate(git);
            fileOut.println("Found " + commits.size() + " commits");

            for (RevCommit commit : commits) {
                /*if(counter == 500 ){
                    // Always reset hard and checkout back to master
                    git.reset().setMode(ResetCommand.ResetType.HARD).call();
                    git.checkout().setName(masterCommitHash).call();
                    fileOut.println("Restored back to saved master commit: " + masterCommitHash);
                    System.exit(0);
                }*/
                String commitHash = commit.getId().getName();
                String commitMessage = commit.getFullMessage();  // ✅ commit message
                int commitTime = commit.getCommitTime();

                Date commitDate = new Date(commitTime * 1000L);
                // Format as readable string
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String formattedTime = sdf.format(commitDate);

                Set<String> committedFiles = listJavaFilesInCommit(git,commit);
                /*RevCommit parent = commit.getParentCount() > 0 ? git.getRepository().parseCommit(commit.getParent(0).getId()) : null;
                System.out.print("parents: ");
                for(RevCommit p: commit.getParents()){
                    System.out.print(p.getId().getName()+", ");
                }
                String parentCommitHash = parent != null ? parent.getId().getName() : null;

                */


                fileOut.println("Printing: "+counter+"/"+commits.size());
                consoleOut.println("Printing: "+counter+"/"+commits.size());
                counter=counter+1;
                //System.out.println("\nProcessing commit: " + commitHash + " (" + commit.getCommitTime() + ")");

                Set<String> changedFiles = getChangedFiles(git, commit);
                //System.out.println("Changed files: " + changedFiles);

                boolean hasTestFile = changedFiles.stream().anyMatch(
                        file -> file.startsWith(TEST_PATH + "/") && file.endsWith("Test.java")
                );
                //System.out.println("Test file has changes: " + hasTestFile);

                if (hasTestFile) {
                    hasTestCounter +=1;
                    //fileOut.println("Test Changes: "+hasTestCounter);
                    consoleOut.println("Test Changes: "+hasTestCounter);
                    String originalPomContent = null;
                    try {
                        System.out.println("Checking out commit: " + commitHash);
                        git.checkout().setName(commitHash).call();
                        //System.out.println("Successfully checked out commit");

                        // Backup original pom.xml
                        RevCommit currentCommit = git.getRepository().parseCommit(ObjectId.fromString(commitHash));
                        originalPomContent = getFileContent(git, currentCommit, "pom.xml");

                        // Overwrite pom.xml with master's version
                        writeFile(REPO_PATH + "/pom.xml", masterPomContent);
                        //System.out.println("Replaced pom.xml with master version temporarily");

                        Map<String, MethodList> changedTestMethods = updatedFindChangedTestMethods(git, commitHash);


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
                                //+++++ Pausing Jacoco
                                /*for (MethodModificationInformation testMethod : entry.getValue().added_or_modified_methods) {
                                    //System.out.println(testMethod);
                                   runTestWithCoverage(testClass, testMethod.method_name, commitHash);
                                }
                                for (String existing_method : entry.getValue().previous_methods) {
                                    //System.out.println(existing_method);
                                    runTestWithCoverage(testClass, existing_method, commitHash);
                                }*/
                                if(!entry.getValue().previous_methods.isEmpty()){
                                    Map<String,StaticTestMethodBody> static_method_bodies = testParser.parsePerTestMethodForStaticAnalysis("/Users/afrinakhatun/IdeaProjects/jsoup/src/test/java",testClass);
                                    Set<String> test_setup_methods = new HashSet<>();
                                    Set<String> test_assertion_methods = new HashSet<>();
                                    Set<String> combined_test_setup_methods = new HashSet<>();
                                    // now need to update here ....
                                    /*for(String previous_m : entry.getValue().previous_methods){
                                        StaticTestMethodBody static_body = static_method_bodies.get(previous_m);
                                        if(static_body != null){
                                            combined_test_setup_methods.addAll(static_body.setup_method_calls);
                                            //test_assertion_methods.addAll(static_body.assertion_method_calls);
                                        }
                                    }*/

                                    List<MethodModificationInformation> added_or_modififed_methods = entry.getValue().added_or_modified_methods;
                                    for(MethodModificationInformation m : added_or_modififed_methods){
                                        if(m.modification_type.equals("add")){

                                            StaticTestMethodBody static_body_added = static_method_bodies.get(m.method_name);
                                            boolean duplicate_amplification = false;
                                            boolean amplification = false;
                                            boolean unique_new_method = false;

                                            if(static_body_added != null){
                                                // check with each existing method
                                                for(Map.Entry<String,PreviousMethodInfo> previous_m : entry.getValue().previous_methods.entrySet()){
                                                    StaticTestMethodBody previous_m_static_body = static_method_bodies.get(previous_m);
                                                    if(previous_m_static_body != null){
                                                        //if(!previous_m_static_body.setup_method_calls.isEmpty() && !static_body_added.setup_method_calls.isEmpty()){
                                                            if(ListComparer.haveSameElements(previous_m_static_body.setup_method_calls,static_body_added.setup_method_calls)){
                                                                //System.out.println("same methods same counts");
                                                                if(ListComparer.haveSameOrder(previous_m_static_body.setup_method_calls,static_body_added.setup_method_calls)){
                                                                    //System.out.println("Same order -- Duplicate / Amplification");
                                                                    duplicate_amplification = true;
                                                                    if(areSetsEqual(static_body_added.assertion_method_calls.method_calls, previous_m_static_body.assertion_method_calls.method_calls)
                                                                    && areSetsEqual(static_body_added.assertion_method_calls.attribute_calls, previous_m_static_body.assertion_method_calls.attribute_calls)){
                                                                        //m.test_assertion_amplification = "Dup";
                                                                    }
                                                                    else if(areSetsEqual(static_body_added.assertion_method_calls.method_calls, previous_m_static_body.assertion_method_calls.method_calls)
                                                                            || areSetsEqual(static_body_added.assertion_method_calls.attribute_calls, previous_m_static_body.assertion_method_calls.attribute_calls)){
                                                                        //m.test_assertion_amplification = "Par";
                                                                    }
                                                                    else{
                                                                        //m.test_assertion_amplification = "NA";
                                                                    }
                                                                }
                                                                else{
                                                                    //System.out.println("Diff order -- Amplification");
                                                                    amplification = true;
                                                                    if(areSetsEqual(static_body_added.assertion_method_calls.method_calls, previous_m_static_body.assertion_method_calls.method_calls)
                                                                            && areSetsEqual(static_body_added.assertion_method_calls.attribute_calls, previous_m_static_body.assertion_method_calls.attribute_calls)){
                                                                        //m.test_assertion_amplification = "Dup";
                                                                    }
                                                                    else if(areSetsEqual(static_body_added.assertion_method_calls.method_calls, previous_m_static_body.assertion_method_calls.method_calls)
                                                                            || areSetsEqual(static_body_added.assertion_method_calls.attribute_calls, previous_m_static_body.assertion_method_calls.attribute_calls)){
                                                                        //m.test_assertion_amplification = "Par";
                                                                    }
                                                                    else{
                                                                        //m.test_assertion_amplification = "NA";
                                                                    }
                                                                }
                                                            }
                                                            else{
                                                                //System.out.println("Diff methods -- New test case");
                                                                for(String new_m:static_body_added.setup_method_calls){
                                                                    if(!combined_test_setup_methods.contains(new_m)){
                                                                        unique_new_method = true;
                                                                        break;
                                                                    }
                                                                }
                                                            }
                                                        //}
                                                    }
                                                }
                                                if( !duplicate_amplification && !amplification){
                                                    if(unique_new_method){
                                                        //m.test_setup_amplification = "NT-U";
                                                    }
                                                    else{
                                                        //m.test_setup_amplification = "NT-NU";
                                                    }

                                                }
                                                else if(duplicate_amplification && !amplification){
                                                    //m.test_setup_amplification = "SM+SO";

                                                    //?? check assertion
                                                }
                                                else if(!duplicate_amplification && amplification){
                                                    //m.test_setup_amplification = "SM+DO";
                                                    //? check assertion
                                                }
                                                else{
                                                    //m.test_setup_amplification = "Mix";
                                                    //check assertion
                                                }
                                                // check test set up
                                                /*if(test_setup_methods.containsAll(static_body_added.setup_method_calls)){
                                                    System.out.println("amplification - all methods previously called");
                                                    m.test_setup_amplification ="C";
                                                }else{
                                                    Set<String> difference = new HashSet<>(static_body_added.setup_method_calls);
                                                    difference.removeAll(test_setup_methods);
                                                    Set<String> intersection = new HashSet<>(static_body_added.setup_method_calls);  // Copy to avoid modifying original
                                                    intersection.retainAll(test_setup_methods);
                                                    if(!intersection.isEmpty() && !difference.isEmpty()){
                                                        System.out.println("amplification - old and new methods");
                                                        m.test_setup_amplification = "M";
                                                    }
                                                    else if(intersection.isEmpty() && !difference.isEmpty()){
                                                        System.out.println("amplification - all new methods");
                                                        m.test_setup_amplification = "N";
                                                    }
                                                }*/
                                                //check assertions
                                                /*if(test_assertion_methods.containsAll(static_body_added.assertion_method_calls)){
                                                    System.out.println("assertion amplification - all methods previously called");
                                                    m.test_assertion_amplification = "C";
                                                }else{
                                                    Set<String> difference = new HashSet<>(static_body_added.assertion_method_calls);
                                                    difference.removeAll(test_assertion_methods);
                                                    Set<String> intersection = new HashSet<>(static_body_added.assertion_method_calls);  // Copy to avoid modifying original
                                                    intersection.retainAll(test_assertion_methods);
                                                    if(!intersection.isEmpty() && !difference.isEmpty()){
                                                        System.out.println("assertion amplification - old and new methods");
                                                        m.test_assertion_amplification = "M";
                                                    }
                                                    else if(intersection.isEmpty() && !difference.isEmpty()){
                                                        System.out.println("assertion amplification - all new methods");
                                                        m.test_assertion_amplification = "N";
                                                    }
                                                }*/
                                            }
                                        }
                                    }
                                    //look at structural change
                                    //parsePerTestMethodForStaticAnalysis("/Users/afrinakhatun/IdeaProjects/jsoup/src/", testClass);

                                }
                            }
                        } else {
                           // System.out.println("No test methods found in commit: " + commitHash);
                        }
                        //save all to json
                        CommitRecordJsonHandler.appendRecord(
                                jsonFile,
                                new CommitRecord(commitHash, changedTestMethods,committedFiles, commitMessage, formattedTime)
                        );

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
                        git.checkout().setName(masterCommitHash).call();
                        //System.out.println("Restored back to saved master commit: " + masterCommitHash);
                    }
                } else {
                   // System.out.println("No test files changed in commit: " + commitHash);
                }
            }
        }
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
                while (treeWalk.next()) {
                    changedFiles.add(treeWalk.getPathString());
                }
            }
            System.out.println("Initial commit " + commit.getId().getName() + ": treating all files as additions");
            return changedFiles;
        }

        // Parse parent commit explicitly
        RevCommit parent;
        try (RevWalk revWalk = new RevWalk(repository)) {
            parent = revWalk.parseCommit(commit.getParent(0).getId());
        } catch (Exception e) {
            System.err.println("Failed to parse parent commit for " + commit.getId().getName() + ": " + e.getMessage() + ", treating as initial commit");
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(commit.getTree());
                treeWalk.setRecursive(true);
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
                .filter(file -> file.startsWith(TEST_PATH + "/") && file.endsWith("Test.java"))
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
            try (ObjectReader reader = repository.newObjectReader()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                reader.open(objectId).copyTo(out);
                return out.toString(StandardCharsets.UTF_8);
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
        FileOutputStream fos = new FileOutputStream("logData.txt", false);
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

        cu.findAll(MethodDeclaration.class).forEach(method -> {
            for (AnnotationExpr annotation : method.getAnnotations()) {
                if (annotation.getNameAsString().equals("Test")) {
                    String methodName = method.getNameAsString();
                    String methodBody = method.toString();  // Includes full method code
                    methodMap.put(methodName, methodBody);
                    break;
                }
            }
        });

        return methodMap;
    }
    private static Map<String,MethodList> updatedFindChangedTestMethods(Git git, String commitHash) throws Exception {
        Repository repository = git.getRepository();
        RevCommit commit = repository.parseCommit(ObjectId.fromString(commitHash));
        //RevCommit parent = commit.getParentCount() > 0 ? repository.parseCommit(commit.getParent(0).getId()) : null;

        Map<String, MethodList> all_methods = new HashMap<>();
        Set<String> changedTestFiles = getChangedFiles(git, commit).stream()
                .filter(file -> file.startsWith(TEST_PATH + "/") && file.endsWith("Test.java"))
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
            Map<String,PreviousMethodInfo> existingTestMethods = new HashMap<>();

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
                                existingTestMethods.put(methodName,pm);
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

    public static RevCommit findPreviousChangeOnFile(Git git, RevCommit currentCommit, String filePath) throws Exception {

        boolean skipCurrent = false;
        Iterable<RevCommit> fileHistory = git.log()
                .add(git.getRepository().resolve("refs/heads/latest-xml-only-branch"))
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

}
