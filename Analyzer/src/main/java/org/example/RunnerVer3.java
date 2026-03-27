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

public class RunnerVer3 {
    private static final Logger mainLogger = Logger.getLogger(NewTestCoverageAnalyzer.class.getName());

    private static String projectName = "";
    private static String branchName = "";
    //?????? HAVE YOU RESET BRANCH  ???????????


    private static String REPO_PATH = "";
    private static String SOURCE_PATH = "/src/main/java";
    private static String TEST_PATH = "src/test/java";
    private static String TARGET_DIR = "";
    private static final Pattern TEST_METHOD_PATTERN = Pattern.compile(
            "(?:@Test\\s*(?:\\([^)]*\\))?\\s*)?public\\s+void\\s+(\\w+)\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w,\\s]+)?\\s*\\{",
            Pattern.MULTILINE
    );
    private static final String[] TEST_SUFFIXES = {"Test.java", "Tests.java", "IT.java", "ITs.java", "Spec.java"};
    private static final String[] NON_PROJECT_CALLS = {"java","jdk","sun","org/sun","org/w3c","org/xml","org/ietf"};
    static final String Coverage_Folder = "/coverageData";
    static PrintStream consoleOut = null;
    static PrintStream fileOut = null;
    static PrintStream bothOut = null;
    static PrintStream bothErr = null;

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
        consoleOut.println("Heap " + callerClass + "." + callerMethod + " Max=" + maxMemory / (1024 * 1024) + ", Total" + totalMemory / (1024 * 1024) + "Free=" +
                freeMemory / (1024 * 1024) + "Remaining= " + remainingMemory / (1024 * 1024));

    }


    public static void main(String[] args) throws Exception {
        //JacocoReportHandler.reportParser();
        //System.exit(0);

        if (args.length == 2) {
            projectName = args[0];
            branchName = args[1];
        }

        //projectName = "commons-csv";
        //branchName = "afrina-branch";

        REPO_PATH = "/Users/afrinakhatun/IdeaProjects/" + projectName;
        TARGET_DIR = REPO_PATH + "/target";


        loggingSetUp();
        //json handler
        Path jsonFile = Paths.get("/Users/afrinakhatun/IdeaProjects/Analyzer/ParsedJsons/" + projectName + "-commit-test-change-history.ndjson");
        CommitRecordJsonHandler.resetFile(jsonFile);

        // Your large program continues here...
        int x = 0;
        int counter = 1;
        int hasTestCounter = 0;
        int temp_skipper = 0;
        File coverageFolder = new File(REPO_PATH + Coverage_Folder);
        if (coverageFolder.exists()) {
            deleteDirectory(coverageFolder);
        } else {
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
                    .setIgnore(false)
                    .call();

            git.checkout().setName(branchName).call();
            fileOut.println("Checked out master branch");

            List<RevCommit> commits = listCommitsOnBranch(git, branchName);
            //List<RevCommit> commits = listCommitsByDate(git,branchName);
            fileOut.println("Found " + commits.size() + " commits");

            SingletonParser singletonParser = null;

            for (RevCommit commit : commits) {
                boolean hasSourceFileChanges = false;
                /*if(counter == 500 ){
                    // Always reset hard and checkout back to master
                    git.reset().setMode(ResetCommand.ResetType.HARD).call();
                    git.checkout().setName(masterCommitHash).call();
                    fileOut.println("Restored back to saved master commit: " + masterCommitHash);
                    System.exit(0);
                }*/
                TEST_PATH = "src/test/java";
                SOURCE_PATH = "/src/main/java";
                boolean pomWasOverwritten = false;
                String originalPomOnDisk = null;
                String commitHash = commit.getId().getName();

                //if (!commitHash.equals("cb99634ab3d6143dffc90938fc68e15c7f9d25b8")) continue;
                //86c1d97c5f83cb6cc9c0d7490942b806d3a1d3be
                String commitMessage = commit.getFullMessage();  // ✅ commit message
                int commitTime = commit.getCommitTime();

                Date commitDate = new Date(commitTime * 1000L);
                // Format as readable string
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String formattedTime = sdf.format(commitDate);

                Set<String> committedFiles = listJavaFilesInCommit(git, commit);


                fileOut.println("Printing: " + counter + "/" + commits.size());
                consoleOut.println("Printing: " + counter + "/" + commits.size());

                counter = counter + 1;
                //if(counter<1900) continue;

                //System.out.println("\nProcessing commit: " + commitHash + " (" + commit.getCommitTime() + ")");


                Set<String> changedFiles = getChangedFiles(git, commit);
                System.out.println("Changed files: " + changedFiles.size());

                try {
                    System.out.println("Checking out commit: " + commitHash);
                    // 🔹 NEW: make sure the working tree is clean before checkout
                    git.reset().setMode(ResetCommand.ResetType.HARD).call();
                    git.clean()
                            .setCleanDirectories(true)
                            .setForce(true)
                            .setIgnore(false)
                            .call();
                    git.checkout().setName(commitHash).call();

                    //check main and test folders path
                    Path testPath = Paths.get(REPO_PATH, TEST_PATH);
                    if (!Files.exists(testPath)) {
                        Path altTestPath = Paths.get(REPO_PATH, "src/test/org");
                        if (Files.exists(altTestPath)) {
                            TEST_PATH = "src/test/org";
                        }
                    }
                    Path sourcePath = Paths.get(REPO_PATH, SOURCE_PATH);
                    if (!Files.exists(sourcePath)) {
                        Path altSrcPath = Paths.get(REPO_PATH, "src/java/org");
                        if (Files.exists(altSrcPath)) {
                            SOURCE_PATH = "/src/java/org";
                        }
                    }

                    singletonParser = new SingletonParser(
                            REPO_PATH + SOURCE_PATH,
                            REPO_PATH + "/" + TEST_PATH);
                    for (String file : changedFiles) {
                        if (file.contains(SOURCE_PATH)) {
                            hasSourceFileChanges = true;
                            break;
                        }
                    }

                    boolean hasTestFile = changedFiles.stream().anyMatch(
                            file -> file.startsWith(TEST_PATH + "/") && isATestFile(file)
                    );
                    //check pom.xml or build.xml
                    Path pom = Paths.get(REPO_PATH, "pom.xml");
                    Path ant = Paths.get(REPO_PATH, "build.xml");
                    boolean hasPom = Files.isRegularFile(pom);
                    boolean hasAnt = Files.isRegularFile(ant);

                    if (!hasAnt && !hasPom) {
                        continue;
                    }
                    //System.out.println("Test file has changes: " + hasTestFile);
                    if (hasTestFile) {
                        hasTestCounter += 1;
                        String originalPomContent = null;

                        // Backup original pom.xml
                        RevCommit currentCommit = git.getRepository().parseCommit(ObjectId.fromString(commitHash));
                        originalPomContent = getFileContent(git, currentCommit, "pom.xml");

                        // Overwrite pom.xml with master's version
                        //writeFile(REPO_PATH + "/pom.xml", masterPomContent);
                        //System.out.println("Replaced pom.xml with master version temporarily");

                        //compile project, before pom rewriting
                        int exit = 1;
                        Path pomPath = Paths.get(REPO_PATH, "pom.xml");
                        if (hasPom) {
                            System.out.println("Found POM");
                            originalPomOnDisk = Files.readString(pomPath, StandardCharsets.UTF_8);
                            exit = compileProjectForTests(REPO_PATH + "/");
                            if (exit == 1) {
                                System.out.println("❌ Compile failed with commit pom.xml. Retrying with master pom.xml...");
                                Files.writeString(pomPath, masterPomContent, StandardCharsets.UTF_8);
                                pomWasOverwritten = true;

                                exit = compileProjectForTests(REPO_PATH + "/");
                            }

                            if (exit == 0) {
                                System.out.println("✅ Compilation successful for exit code" + exit);
                            } else {
                                System.err.println("❌ Compilation failed for exit code" + exit);
                            }
                        } else {
                            continue;
                        }

                        Map<String, MethodList> changedTestMethods = updatedFindChangedTestMethods(git, commitHash);
                        logHeapSize();
                        if (exit == 0) {
                            for (Map.Entry<String, MethodList> entry : changedTestMethods.entrySet()) {
                                List<String> javapContentLines = new ArrayList<>();
                                if (hasPom) {
                                    if (TEST_PATH.equals("src/test/org")) {
                                        javapContentLines = generateJavapFile(entry.getKey(), "mvn", true);
                                    } else {
                                        javapContentLines = generateJavapFile(entry.getKey(), "mvn", false);
                                    }

                                    if(javapContentLines == null) {
                                        System.out.println("one class javapFile null.");
                                        continue;
                                    }

                                } else if (hasAnt) {
                                    //javapContentLines = generateJavapFile(entry.getKey(),"ant");
                                }

                                Map<String, List<JavapMethodStruct>> methodCalls = new HashMap<>();
                                if (javapContentLines.size() > 0) {
                                    Map<String, JavapMethodBlock> methodBodies = JavapExtracator.getMethodInformation(javapContentLines);

                                    for (Map.Entry<String, JavapMethodBlock> m : methodBodies.entrySet()) {
                                        List<JavapMethodStruct> calls = new ArrayList<>();
                                        for (String line : m.getValue().codeLines) {
                                            if (JavapExtracator.parseFromMethodCommentLine(line, new ArrayList<>(methodBodies.values())) != null) {
                                                calls.add(JavapExtracator.parseFromMethodCommentLine(line, new ArrayList<>(methodBodies.values())));
                                            }
                                        }
                                        methodCalls.put(m.getKey(), calls);
                                    }
                                    System.out.println("done");
                                }
                                //Getting normalized body aswell
                                //consoleOut.println("Going to Normalize!!!");
                                Map<String, NormalizationResult> normalized_bodies = testParser.getNormalizedMethodsOfClass(singletonParser, REPO_PATH + "/" + TEST_PATH, entry.getKey());
                                //consoleOut.println("Normalization Done");
                                //logHeapSize();

                                //new  methods added in clusters ??
                                List<Integer> indexes = new ArrayList<>();
                                for (MethodModificationInformation temp_method : entry.getValue().added_or_modified_methods) {
                                    if(temp_method.modification_type.equals("add")){
                                        indexes.add(entry.getValue().afterOrderedMethods.indexOf(temp_method.method_name));
                                    }
                                }
                                entry.getValue().addedMethodClusters = findClusters(indexes, entry.getValue().afterOrderedMethods);

                                // Matching added methods with previous methods
                                if (!entry.getValue().previous_methods.isEmpty()) {
                                    Set<String> alreadyTestedMethods = new HashSet<>();
                                    for (Map.Entry<String, PreviousMethodInfo> existing : entry.getValue().previous_methods.entrySet()){
                                        for (JavapMethodStruct ex : methodCalls.get(existing.getKey())) {
                                            if(ex.classPath.startsWith("java")) {
                                                continue;
                                            }
                                            alreadyTestedMethods.add(ex.classPath + "." + ex.methodName);
                                        }
                                    }

                                    for (MethodModificationInformation temp_method : entry.getValue().added_or_modified_methods) {
                                        if (temp_method.modification_type.equals("add")) {
                                            List<String> tempMethod = new ArrayList<>();
                                            for (JavapMethodStruct tm : methodCalls.get(temp_method.method_name)) {
                                                //tempMethod.add(tm.methodName);
                                                //if(!isDropAble(tm.classPath)){
                                                    tempMethod.add(tm.classPath+"."+tm.methodName);
                                                //}
                                            }
                                            //Adding distance of methods in files
                                            temp_method.positionDistanceScores = calculatePositionDistances(temp_method.method_name,
                                                    entry.getValue().previous_methods.keySet(),
                                                    entry.getValue().afterOrderedMethods);
                                            //Checking if they test any new method
                                            Set<String> newlyTestedMethods = new HashSet<>();
                                            for (String s : tempMethod) {
                                                //if (!alreadyTestedMethods.contains(s)) {
                                                    newlyTestedMethods.add(s);
                                                //}
                                            }
                                            newlyTestedMethods.removeAll(alreadyTestedMethods);
                                            temp_method.newMethodsTested = newlyTestedMethods;

                                            for (Map.Entry<String, PreviousMethodInfo> existing : entry.getValue().previous_methods.entrySet()) {
                                                //if(!existing.getKey().equals("testWithinMultipleRanges")) continue;
                                                List<String> existingMethod = new ArrayList<>();
                                                for (JavapMethodStruct ex : methodCalls.get(existing.getKey())) {
                                                    //existingMethod.add(ex.methodName);
                                                    //if(!isDropAble(ex.classPath)){
                                                        existingMethod.add(ex.classPath+"."+ex.methodName);
                                                    //}
                                                }
                                                double setSimilarity = JavapInputAmplificationDetector.jaccardOnUnique(existingMethod, tempMethod);
                                                //double setSimilarity = JavapInputAmplificationDetector.jaccardOnMultiset(existingMethod, tempMethod);
                                                //String category = JavapInputAmplificationDetector.checkJavapAmplification(existingMethod, tempMethod);
                                                JavapClassificationResult category = JavapInputAmplificationDetector.classifyUpdate(existingMethod, tempMethod);
                                                double normalizedEidtDistance = JavapInputAmplificationDetector.getNormalizedEditDistance(existingMethod, tempMethod);
                                                double methodBodyNormalizedEditDistance = JavapInputAmplificationDetector.getNormalizedEditDistance(
                                                        JavapInputAmplificationDetector.normalizeWhitespace(normalized_bodies.get(existing.getKey()).literalValueOnly_NormalizedSource),
                                                        JavapInputAmplificationDetector.normalizeWhitespace(normalized_bodies.get(temp_method.method_name).literalValueOnly_NormalizedSource));

                                                //String severity = JavapInputAmplificationDetector.classifyWithEditDistance(normalizedEidtDistance);

                                                //if (setSimilarity >= 0.5 || (1 - normalizedEidtDistance) >= 0.5) {
                                                    //System.out.println("googli");
                                                    //for result writing
                                                    temp_method.normalized_method_body = tempMethod.toString();
                                                    temp_method.similarityScores.put(existing.getKey(), setSimilarity);
                                                    temp_method.editDistanceScores.put(existing.getKey(), normalizedEidtDistance);
                                                    temp_method.methodBodyEditDistanceScores.put(existing.getKey(),methodBodyNormalizedEditDistance);
                                                    //temp_method.amplification_category.put(existing.getKey(), JavapInputAmplificationDetector.finalTagsKey(category));
                                                    temp_method.amplification_category.put(existing.getKey(), category);

                                                    temp_method.literal_value_only_normalized_body = JavapInputAmplificationDetector.normalizeWhitespace(normalized_bodies.get(temp_method.method_name).literalValueOnly_NormalizedSource);
                                                    //temp_method.amplification_severity.put(existing.getKey(), severity);
                                                    existing.getValue().previousMethodNormalizedBody = existingMethod.toString();
                                                    existing.getValue().literal_value_only_normalized_body = JavapInputAmplificationDetector.normalizeWhitespace(normalized_bodies.get(existing.getKey()).literalValueOnly_NormalizedSource);
                                                //}
                                            }
                                        }
                                    }
                                }
                                //Matching added methods with each other
                                for (MethodModificationInformation temp_method : entry.getValue().added_or_modified_methods) {
                                    if (temp_method.modification_type.equals("add")) {
                                        List<String> tempMethod = new ArrayList<>();
                                        for (JavapMethodStruct tm : methodCalls.get(temp_method.method_name)) {
                                            //tempMethod.add(tm.methodName);
                                            //if(!isDropAble(tm.classPath)){
                                                tempMethod.add(tm.classPath+"."+tm.methodName);
                                            //}
                                        }
                                        for (MethodModificationInformation peer : entry.getValue().added_or_modified_methods) {
                                            if (temp_method.method_name.equals(peer.method_name)) {
                                                continue;
                                            }
                                            List<String> peerMethod = new ArrayList<>();
                                            for (JavapMethodStruct pe : methodCalls.get(peer.method_name)) {
                                                //peerMethod.add(pe.methodName);
                                                //if(!isDropAble(pe.classPath)) {
                                                    peerMethod.add(pe.classPath+"."+pe.methodName);
                                                //}
                                            }
                                            double setSimilarity = JavapInputAmplificationDetector.jaccardOnUnique(peerMethod, tempMethod);
                                            //double setSimilarity = JavapInputAmplificationDetector.jaccardOnMultiset(peerMethod, tempMethod);
                                            //String category = JavapInputAmplificationDetector.checkJavapAmplification(peerMethod, tempMethod);
                                            JavapClassificationResult category = JavapInputAmplificationDetector.classifyUpdate(peerMethod, tempMethod);
                                            double normalizedEidtDistance = JavapInputAmplificationDetector.getNormalizedEditDistance(peerMethod, tempMethod);
                                            double methodBodyNormalizedEditDistance = JavapInputAmplificationDetector.getNormalizedEditDistance(
                                                    JavapInputAmplificationDetector.normalizeWhitespace(normalized_bodies.get(peer.method_name).literalValueOnly_NormalizedSource),
                                                    JavapInputAmplificationDetector.normalizeWhitespace(normalized_bodies.get(temp_method.method_name).literalValueOnly_NormalizedSource)
                                            );
                                            //String severity = JavapInputAmplificationDetector.classifyWithEditDistance(normalizedEidtDistance);

                                            temp_method.normalized_method_body = tempMethod.toString();
                                            temp_method.literal_value_only_normalized_body = JavapInputAmplificationDetector.normalizeWhitespace(normalized_bodies.get(temp_method.method_name).literalValueOnly_NormalizedSource);

                                            peer.normalized_method_body = peerMethod.toString();
                                            peer.literal_value_only_normalized_body = JavapInputAmplificationDetector.normalizeWhitespace(normalized_bodies.get(peer.method_name).literalValueOnly_NormalizedSource);

                                            int positionDistance = calculatePeerPositionDistance(temp_method.method_name, peer.method_name, entry.getValue().afterOrderedMethods);
                                            if (peer.modification_type.equals("add")) {
                                                temp_method.addedPeerScores.put(peer.method_name, new PeerMethodSimilarity(setSimilarity, normalizedEidtDistance, JavapInputAmplificationDetector.finalTagsKey(category), null,methodBodyNormalizedEditDistance,positionDistance));
                                            } else if (peer.modification_type.equals("mod")) {
                                                temp_method.modifiedPeerScores.put(peer.method_name, new PeerMethodSimilarity(setSimilarity, normalizedEidtDistance, JavapInputAmplificationDetector.finalTagsKey(category), null,methodBodyNormalizedEditDistance,positionDistance));
                                            }
                                        }
                                    }
                                }
                            }// chnged method list, for evry class in a commit
                            //save all to json
                            singletonParser = null;
                            CommitRecordJsonHandler.appendRecord(
                                    jsonFile,
                                    new CommitRecord(commitHash, changedTestMethods, committedFiles, commitMessage, hasSourceFileChanges)
                            );
                            //changedTestMethods.clear();
                            logHeapSize();
                        }// if compiles for a commit
                        if (temp_skipper == 0) continue;
                        System.out.println("I m here");


                        changedTestMethods.clear();
                    } else {
                        // System.out.println("No test files changed in commit: " + commitHash);
                    }
                } catch (Exception e) {
                    System.err.println("Error while processing commit " + commitHash + ": " + e.getMessage());
                } finally {
                    // Always restore original pom.xml if we had backed it up
                    if (pomWasOverwritten && originalPomOnDisk != null) {
                        try {
                            Files.writeString(Paths.get(REPO_PATH, "pom.xml"), originalPomOnDisk, StandardCharsets.UTF_8);
                            //System.out.println("Restored original pom.xml after processing commit");
                        } catch (Exception e) {
                            System.err.println("Failed to restore original pom.xml: " + e.getMessage());
                        }
                    }
                    // Force restore repo to master commit (prevents checkout conflicts)
                    try {
                        git.reset()
                                .setMode(ResetCommand.ResetType.HARD)
                                .setRef(masterCommitHash)
                                .call();

                        git.clean()
                                .setCleanDirectories(true)
                                .setIgnore(false)
                                .setForce(true)
                                .call();
                    } catch (Exception e) {
                        System.out.println("MASTER reset/checkout issue");
                        e.printStackTrace();
                    }

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

                    //System.out.println("Restored back to saved master commit: " + masterCommitHash);
                }
            }
        }
        System.gc();
    }
    private static boolean isDropAble(String classpath){
        for(String dropper: NON_PROJECT_CALLS){
            if(classpath.startsWith(dropper)){
                return true;
            }
        }
        return false;
    }

    private static int calculatePeerPositionDistance(String addedMethodName, String peerMethodName, List<String> afterOrderedMethods) {
        int addIndex = afterOrderedMethods.indexOf(addedMethodName);
        int peerIndex = afterOrderedMethods.indexOf(peerMethodName);
        if(peerIndex<addIndex){
            return addIndex-peerIndex-1;

        }
        else{
            return addIndex-peerIndex+1;
        }

    }

    private static Map<String, Integer> calculatePositionDistances(String addedMethodName, Set<String> existingMethodNames, List<String> afterOrderedMethods) {
        List<String> filteredOrderedMethod = new ArrayList<>();
        for(String m:afterOrderedMethods){
            if(existingMethodNames.contains(m) || m.equals(addedMethodName)){
                filteredOrderedMethod.add(m);
            }
        }
        Map<String,Integer> positionDistances = new HashMap<>();
        int addedMethodIndex = filteredOrderedMethod.indexOf(addedMethodName);
        for(String e:existingMethodNames){
            if(filteredOrderedMethod.indexOf(e)<addedMethodIndex){
                positionDistances.put(e,addedMethodIndex-filteredOrderedMethod.indexOf(e)-1);
            }
            else{
                positionDistances.put(e,addedMethodIndex-filteredOrderedMethod.indexOf(e)+1);
            }
        }
        return positionDistances;
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
         Collections.reverse(commitList);
        //commitList.sort(Comparator.comparingLong(RevCommit::getCommitTime));
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
        /*Set<String> changedTestFiles = getChangedFiles(git, commit).stream()
                .filter(file -> file.startsWith(TEST_PATH + "/") && isATestFile(file))
                .collect(Collectors.toSet());*/

        List<String> changedTestFiles = getChangedFiles(git, commit).stream()
                .filter(file -> file.startsWith(TEST_PATH + "/") && isATestFile(file))
                .sorted()
                .toList();

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
        System.out.println("found changed methods:" + changedMethods.size());
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
            File execFolder = new File(REPO_PATH + Coverage_Folder + "/" + commitHash + "/" + testClass);
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
                            REPO_PATH + Coverage_Folder + "/" + commitHash + "/" + testClass + "/html-" + testMethod
                    );

                } catch (Exception e) {
                    System.err.println("❌ Coverage could not be generated");
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
        FileOutputStream fos = new FileOutputStream("/Users/afrinakhatun/IdeaProjects/Analyzer/LogData/" + projectName + "logData.txt", false);
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

    private static List<String> getOrderedMethodsOnAfterFileContent(String content, List<String> afterMethods){
        List<String> orderedAfterMethods = new ArrayList<>();
        JavaParser parser = new JavaParser();
        ParseResult<CompilationUnit> result = parser.parse(content);
        if (!result.isSuccessful() || !result.getResult().isPresent()) {
            return orderedAfterMethods;  // Return empty map on parse failure
        }
        // faster lookup than list.contains
        Set<String> afterMethodSet = new HashSet<>(afterMethods);

        CompilationUnit cu = result.getResult().get();
        cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> afterMethodSet.contains(m.getNameAsString()))
                .filter(m -> m.getRange().isPresent())
                .sorted(Comparator.comparingInt(m -> m.getRange().get().begin.line))
                .forEach(m -> orderedAfterMethods.add(m.getNameAsString()));
        cu=null;
        parser=null;
        return orderedAfterMethods;
    }

    private static Map<String, String> parseTestMethodsWithBodies(String content) {
        Map<String, String> methodMap = new LinkedHashMap<>();
        JavaParser parser = new JavaParser();

        ParseResult<CompilationUnit> result = parser.parse(content);
        if (!result.isSuccessful() || !result.getResult().isPresent()) {
            return methodMap;  // Return empty map on parse failure
        }

        CompilationUnit cu = result.getResult().get();

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

    private static Map<String, MethodList> updatedFindChangedTestMethods(Git git, String commitHash) throws Exception {
        //Repository repository = git.getRepository();
        RevCommit commit = git.getRepository().parseCommit(ObjectId.fromString(commitHash));
        //RevCommit parent = commit.getParentCount() > 0 ? repository.parseCommit(commit.getParent(0).getId()) : null;

        Map<String, MethodList> all_methods = new HashMap<>();
        /*Set<String> changedTestFiles = getChangedFiles(git, commit).stream()
                .filter(file -> file.startsWith(TEST_PATH + "/") && isATestFile(file))
                .collect(Collectors.toSet());*/

        List<String> changedTestFiles = getChangedFiles(git, commit).stream()
                .filter(file -> file.startsWith(TEST_PATH + "/") && isATestFile(file))
                .sorted()
                .toList();

        for (String filePath : changedTestFiles) {
            String afterContent = getFileContent(git, commit, filePath);
            RevCommit parent = findPreviousChangeOnFile(git, commit, filePath);
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
            //List<String> afterMethodList = new ArrayList<String>(methodsAfter.keySet());
            //methods.afterOrderedMethods = getOrderedMethodsOnAfterFileContent(afterContent, afterMethodList);
            methods.afterOrderedMethods = new ArrayList<String>(methodsAfter.keySet());

            if (methodsBefore.isEmpty()) {
                if (methodsAfter.isEmpty()) {
                    String testClass = filePath.replace(TEST_PATH + "/", "").replace(".java", "").replace("/", ".");
                    all_methods.put(testClass, methods);
                } else {
                    for (Map.Entry<String, String> entry : methodsAfter.entrySet()) {
                        String methodName = entry.getKey();
                        changedTestMethods.add(new MethodModificationInformation(methodName, "add", entry.getValue()));
                    }

                }
            } else {
                if (methodsAfter.isEmpty()) {
                    String testClass = filePath.replace(TEST_PATH + "/", "").replace(".java", "").replace("/", ".");
                    all_methods.put(testClass, methods);
                } else {
                    // Detect added or modified
                    for (Map.Entry<String, String> entry : methodsAfter.entrySet()) {
                        String methodName = entry.getKey();
                        String methodBodyAfter = entry.getValue();

                        String normalizedBefore = null;
                        if (methodsBefore.containsKey(methodName)) {
                            normalizedBefore = normalizeMethod(methodsBefore.get(methodName));
                        }

                        String normalizedAfter = normalizeMethod(methodBodyAfter);


                        if (!methodsBefore.containsKey(methodName)) {
                            changedTestMethods.add(new MethodModificationInformation(methodName, "add", methodBodyAfter)); // Added
                        } else {
                            if (!normalizedBefore.equals(normalizedAfter)) {
                                changedTestMethods.add(new MethodModificationInformation(methodName, "mod", methodBodyAfter)); // Modified
                            } else {
                                PreviousMethodInfo pm = new PreviousMethodInfo();
                                pm.previousMethodBody = methodBodyAfter;
                                existingTestMethods.put(methodName, pm); //Unchanged
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
        System.out.println("found changed methods:" + all_methods.size());
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
                .add(git.getRepository().resolve("refs/heads/" + branchName))
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
        } catch (Exception ignored) {
        }
        return null;
    }


    private static Path findFileByClassName(Path root, String className) throws IOException {
        String[] parts = className.split("\\.");
        String name = parts[parts.length - 1];

        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(name + ".java"))
                    .findFirst()
                    .orElse(null);
        }
    }

    public void normalizeMethodBody() {

    }

    public static Set<String> get_tested_object_list(List<MethodCallSignature> regularMethodCalls,
                                                     List<AssertionCall> assertionCalls) {
        Set<String> tested_object_list = new HashSet<>();
        for (MethodCallSignature callSignature : regularMethodCalls) {
            tested_object_list.add(callSignature.receiverType);
        }
        for (AssertionCall assertionCall : assertionCalls) {
            if (assertionCall.arguments.size() == 1) {
                if (assertionCall.arguments.get(0).argumentType.equals("MTH") ||
                        assertionCall.arguments.get(0).argumentType.equals("FLD") ||
                        assertionCall.arguments.get(0).argumentType.equals("NAM")) {
                    tested_object_list.add(assertionCall.arguments.get(0).receiverType);
                }
            } else if (assertionCall.arguments.size() > 1) {
                for (int k = 1; k < assertionCall.arguments.size(); k++) {
                    if (assertionCall.arguments.get(k).argumentType.equals("MTH") ||
                            assertionCall.arguments.get(k).argumentType.equals("FLD") ||
                            assertionCall.arguments.get(k).argumentType.equals("NAM")) {
                        tested_object_list.add(assertionCall.arguments.get(k).receiverType);
                    }
                }
            }
        }
        return tested_object_list;
    }

    public static boolean isATestFile(String path) {
        if (path == null) {
            return false;
        }
        String fileName = new File(path).getName();
        for (String p : TEST_SUFFIXES) {
            if (path.endsWith(p)) {
                return true;
            }
        }
        // prefix-based detection (e.g., TestStyle.java, TestUser.java)
        String test = "Test";
        if ((fileName.toLowerCase().startsWith(test.toLowerCase()) || fileName.toLowerCase().contains(test.toLowerCase())) && fileName.endsWith(".java")) {
            return true;
        }
        return false;
    }

    public static int compileProjectForTests(String repoPath)
            throws IOException, InterruptedException {
        String java17Home = "/opt/homebrew/Cellar/openjdk@17/17.0.17/libexec/openjdk.jdk/Contents/Home";
        if (projectName.equals("mybatis")) {
            java17Home = "/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home";
        } else if (projectName.equals("commons-codec")) {
            java17Home = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home";
        } else if (projectName.equals("commons-validator")) {
            java17Home = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home";
        } else if (projectName.equals("commons-net")) {
            java17Home = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home";
        } else if (projectName.equals("commons-collections")) {
            java17Home = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home";
        } else if (projectName.equals("jsoup")) {
            java17Home = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home";
        } else if (projectName.equals("commons-io")) {
            java17Home = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home";
        }
        else if (projectName.equals("commons-csv")) {
            java17Home = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home";
        }
        else if (projectName.equals("commons-text")) {
            java17Home = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home";
        }
        else if (projectName.equals("commons-cli")) {
            java17Home = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home";
        }
        else if (projectName.equals("commons-lang")) {
            java17Home = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home";
        }
        else if (projectName.equals("commons-compress")) {
            java17Home = "/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home";
        }
        ProcessBuilder pb = new ProcessBuilder(
                "mvn",
                "-q",
                "-DskipTests",       // skip running tests, but still compile them
                "-DskipITs",
                "-Drat.skip=true",
                "-DskipRat=true",
                "-Dproject.build.sourceEncoding=ISO-8859-1",
                "-Dmaven.compiler.encoding=ISO-8859-1",
                "-Dmaven.compiler.testEncoding=ISO-8859-1",
                "-Dfile.encoding=ISO-8859-1",//ISO-8859-1
                "clean", "test-compile"
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
                if (line.contains("COMPILATION ERROR")) {
                    sawCompilationError = true;
                }
                if (line.contains("Failed to execute goal")) {
                    sawFailure = true;
                }
                if (line.contains("There are test failures")) {
                    sawFailure = true;
                }
                if (line.contains("ERROR")) {
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

    public static List<String> generateJavapFile(String testClass, String buildType, boolean requireOrg) throws IOException {
        String testFilePath = "";
        if (buildType.equals("mvn")) {
            Path root = Paths.get(REPO_PATH, "target", "test-classes");
            if (requireOrg) {
                root = Paths.get(REPO_PATH, "target", "test-classes", "org");
            }
            if (!Files.exists(root)) {
                throw new IllegalStateException("target/test-classes does not exist. Test-compile UnSuccessful.");
            }
            String dotReplacedPath = testClass.replace('.', '/');
            testFilePath = root + "/" + dotReplacedPath + ".class";
        } else if (buildType.equals("ant")) {
            Path root = null;
            if (projectName.equals("commons-io")) {
                root = Paths.get(REPO_PATH, "target", "tests", "org");
            }
            if (!Files.exists(root)) {
                throw new IllegalStateException(root.toString() + " does not exist. Test-compile UnSuccessful.");
            }
            String dotReplacedPath = testClass.replace('.', '/');
            testFilePath = root + "/" + dotReplacedPath + ".class";
            ;
        }


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
            try {
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
            } catch (Exception e) {
                System.out.println(e.getMessage());
                return null;
            }

        }
        return null;
    }
    public static List<List<String>> findClusters(List<Integer> methodIndices, List<String> afterOrderedMethods) {
        List<List<String>> clusters = new ArrayList<>();

        if (methodIndices == null || methodIndices.size() < 2) {
            return clusters;
        }

        // Sort indices
        List<Integer> sorted = new ArrayList<>(methodIndices);
        Collections.sort(sorted);

        List<String> currentCluster = new ArrayList<>();
        currentCluster.add(afterOrderedMethods.get(sorted.get(0)));

        for (int i = 1; i < sorted.size(); i++) {
            int prev = sorted.get(i - 1);
            int curr = sorted.get(i);

            if (curr == prev + 1) {
                // contiguous → same cluster
                currentCluster.add(afterOrderedMethods.get(curr));
            } else {
                // break in sequence
                //if (currentCluster.size() >= 2) {
                    clusters.add(new ArrayList<>(currentCluster));
                //}
                currentCluster.clear();
                currentCluster.add(afterOrderedMethods.get(curr));
            }
        }

        // Add the last cluster if valid
        //if (currentCluster.size() >= 2) {
            clusters.add(currentCluster);
        //}

        return clusters;
    }

}
