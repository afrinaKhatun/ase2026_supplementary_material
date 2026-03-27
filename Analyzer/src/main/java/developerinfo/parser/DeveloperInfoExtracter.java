package developerinfo.parser;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DeveloperInfoExtracter {
    static String projectName="commons-cli";

    static String xlsxPath = "/Users/afrinakhatun/IdeaProjects/Analyzer/ExcelResults/"+projectName+"_set_Result.xlsx";
    static String repoPath = "/Users/afrinakhatun/IdeaProjects/"+projectName;
     static DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    .withZone(ZoneId.systemDefault());

    public static void main(String[] args) {
        try {
            readXlsxFile(xlsxPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void readXlsxFile(String xlsxPath) throws IOException {
        Workbook outputWorkbook = new XSSFWorkbook();
        Sheet outputSheet = outputWorkbook.createSheet("Developer-Info");

        int outputRowNum = 0;

        // HEADER ROW
        Row header = outputSheet.createRow(outputRowNum++);

        header.createCell(0).setCellValue("TestClass");
        header.createCell(1).setCellValue("CommitHash");
        header.createCell(2).setCellValue("SourceMethodName");
        header.createCell(3).setCellValue("Source Method Set of Developer Names");
        header.createCell(4).setCellValue("Source Method Last Modified Developer Name");
        header.createCell(5).setCellValue("Source Method Last Modified Time");
        header.createCell(6).setCellValue("Source Method Line Range");
        header.createCell(7).setCellValue("AddedMethodName");
        header.createCell(8).setCellValue("Added Method Set of Developer Names");
        header.createCell(9).setCellValue("Added Method Last Modified Developer Name");
        header.createCell(10).setCellValue("Added Method Last Modified Time");
        header.createCell(11).setCellValue("Added Method Line Range");
        header.createCell(12).setCellValue("Common Developers");
        header.createCell(13).setCellValue("Days difference");
        header.createCell(14).setCellValue("Position Distance");
        header.createCell(15).setCellValue("NoOfMethodsInFile");
        header.createCell(16).setCellValue("Source-Added Position");


        try (FileInputStream fis = new FileInputStream(xlsxPath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            boolean isHeader = true;

            for (Row row : sheet) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                String className = formatter.formatCellValue(row.getCell(0)).trim();
                String commitHash = formatter.formatCellValue(row.getCell(1)).trim();
                //if(!commitHash.equals("57ddce119516f451168f4489f55f399fd9309b53")) continue;
                String sourceMethodName = formatter.formatCellValue(row.getCell(3)).trim();
                String addedMethodName = formatter.formatCellValue(row.getCell(9)).trim();

                int positionDistance = Integer.parseInt(formatter.formatCellValue(row.getCell(14)).trim());
                int noOfMethodInFile = Integer.parseInt(formatter.formatCellValue(row.getCell(15)).trim());
                String SourceAddedPosition = formatter.formatCellValue(row.getCell(16)).trim();;
                String filePath = "";

                String filesString = formatter.formatCellValue(row.getCell(28)).trim();
                filesString = filesString.substring(1, filesString.length() - 1);
                String[] files = filesString.split(",\\s*");
                for (String f : files) {
                    if(f.endsWith(className.replace('.','/')+".java")){
                        filePath = f;
                        break;
                    }
                }


                try {
                    String sourceCode = getFileContentAtCommit(repoPath, commitHash, filePath);
                    MethodRange sourceRange = findMethodRange(sourceCode, sourceMethodName);
                    List<BlameLineInfo> sourceBlame = runGitBlame(repoPath, commitHash, filePath, sourceRange.startLine, sourceRange.endLine);
                    for (BlameLineInfo info : sourceBlame) {
                        //System.out.println(info);
                    }
                    String sourceMethodDevelopers = getDeveloperNameSet(sourceBlame);
                    String[] sourceMethodLastDeveloperToModify = getLastModifiedDeveloperInfo(sourceBlame);

                    MethodRange addedRange = findMethodRange(sourceCode, addedMethodName);
                    List<BlameLineInfo> addedBlame = runGitBlame(repoPath, commitHash, filePath, addedRange.startLine, addedRange.endLine);
                    for (BlameLineInfo info : addedBlame) {
                        //System.out.println(info);
                    }
                    String addedMethodDevelopers = getDeveloperNameSet(addedBlame);
                    String[] addedMethodLastDeveloperToModify = getLastModifiedDeveloperInfo(addedBlame);


                    Row outRow = outputSheet.createRow(outputRowNum++);
                    outRow.createCell(0).setCellValue(className);
                    outRow.createCell(1).setCellValue(commitHash);

                    outRow.createCell(2).setCellValue(sourceMethodName);
                    outRow.createCell(3).setCellValue(sourceMethodDevelopers);
                    outRow.createCell(4).setCellValue(sourceMethodLastDeveloperToModify[0]);
                    outRow.createCell(5).setCellValue(sourceMethodLastDeveloperToModify[1]);
                    outRow.createCell(6).setCellValue(sourceRange.startLine+"-"+sourceRange.endLine);

                    outRow.createCell(7).setCellValue(addedMethodName);
                    outRow.createCell(8).setCellValue(addedMethodDevelopers);
                    outRow.createCell(9).setCellValue(addedMethodLastDeveloperToModify[0]);
                    outRow.createCell(10).setCellValue(addedMethodLastDeveloperToModify[1]);
                    outRow.createCell(11).setCellValue(addedRange.startLine+"-"+addedRange.endLine);
                    outRow.createCell(12).setCellValue(getCommonDevelopers(sourceBlame,addedBlame));
                    outRow.createCell(13).setCellValue(getDaysBetween(sourceMethodLastDeveloperToModify[1],addedMethodLastDeveloperToModify[1]));
                    outRow.createCell(14).setCellValue(positionDistance);
                    outRow.createCell(15).setCellValue(noOfMethodInFile);
                    outRow.createCell(16).setCellValue(SourceAddedPosition);

                } catch (Exception e) {
                    System.out.println("Failed for method " + addedMethodName + " in commit " + commitHash);
                    e.printStackTrace();
                }
            }
        }
        try (FileOutputStream fos = new FileOutputStream("/Users/afrinakhatun/IdeaProjects/Analyzer/DeveloperInfo/"+projectName+"_developer_info_output.xlsx")) {
            outputWorkbook.write(fos);
        }

        outputWorkbook.close();

        System.out.println("Excel file written successfully.");
    }
    private static String getFileContentAtCommit(String repoPath, String commitHash, String filePath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "git", "show", commitHash + ":" + filePath
        );
        pb.directory(new java.io.File(repoPath));
        pb.redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder sb = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }

        int exit = process.waitFor();
        if (exit != 0) {
            throw new RuntimeException("git show failed for commit=" + commitHash + ", file=" + filePath);
        }

        return sb.toString();
    }

    private static MethodRange findMethodRange(String sourceCode, String methodName) {
        com.github.javaparser.JavaParser parser = new com.github.javaparser.JavaParser();
        com.github.javaparser.ParseResult<com.github.javaparser.ast.CompilationUnit> result = parser.parse(sourceCode);

        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            throw new RuntimeException("Could not parse source file.");
        }

        com.github.javaparser.ast.CompilationUnit cu = result.getResult().get();

        for (com.github.javaparser.ast.body.MethodDeclaration method : cu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class)) {
            if (method.getNameAsString().equals(methodName) && method.getRange().isPresent()) {
                int start = method.getRange().get().begin.line;
                int end = method.getRange().get().end.line;
                return new MethodRange(start, end);
            }
        }

        throw new RuntimeException("Method not found: " + methodName);
    }

    private static List<BlameLineInfo> runGitBlame(String repoPath, String commitHash, String filePath, int startLine, int endLine)
            throws IOException, InterruptedException {

        ProcessBuilder pb = new ProcessBuilder(
                "git", "blame", "--line-porcelain", commitHash,
                "-L", startLine + "," + endLine,
                "--", filePath
        );
        pb.directory(new File(repoPath));
        pb.redirectErrorStream(true);

        Process process = pb.start();

        List<BlameLineInfo> results = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            String line;
            BlameLineInfo current = null;

            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }

                // Start of a blame block:
                // <40-char-hash> <orig-line> <final-line> <num-lines>
                if (isBlameHeaderLine(line)) {
                    if (current != null) {
                        results.add(current);
                    }

                    current = new BlameLineInfo();

                    String[] parts = line.split("\\s+");
                    current.fullCommitHash = parts[0];
                    current.finalLineNumber = Integer.parseInt(parts[2]);
                    continue;
                }

                if (current == null) {
                    continue;
                }

                if (line.startsWith("author ")) {
                    current.authorName = line.substring("author ".length()).trim();
                } else if (line.startsWith("author-mail ")) {
                    current.authorEmail = line.substring("author-mail ".length()).trim();
                } else if (line.startsWith("author-time ")) {
                    String epochStr = line.substring("author-time ".length()).trim();
                    long epoch = Long.parseLong(epochStr);
                    current.authorTime = epochStr; // raw epoch
                    current.authorDateFormatted = DATE_FORMAT.format(Instant.ofEpochSecond(epoch));
                } else if (line.startsWith("author-tz ")) {
                    current.authorTimeZone = line.substring("author-tz ".length()).trim();
                } else if (line.startsWith("\t")) {
                    current.sourceLine = line.substring(1);
                    results.add(current);
                    current = null;
                }
            }

            if (current != null) {
                results.add(current);
            }
        }

        int exit = process.waitFor();
        if (exit != 0) {
            throw new RuntimeException("git blame failed for commit=" + commitHash + ", file=" + filePath);
        }

        return results;
    }

    private static boolean isBlameHeaderLine(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length < 3) {
            return false;
        }
        return parts[0].matches("[0-9a-f]{40}")
                && parts[1].matches("\\d+")
                && parts[2].matches("\\d+");
    }
    private static String getDeveloperNameSet(List<BlameLineInfo> blameLines) {

        Set<String> developers = new LinkedHashSet<>();

        for (BlameLineInfo info : blameLines) {
            if (info.authorName != null && !info.authorName.isBlank()) {
                developers.add(info.authorName.trim());
            }
        }

        return String.join("; ", developers);
    }
    private static String[] getLastModifiedDeveloperInfo(List<BlameLineInfo> blameLines) {
        long latestTime = Long.MIN_VALUE;

        String developerName = "";
        String developerDate = "";

        for (BlameLineInfo info : blameLines) {

            if (info.authorTime == null || info.authorTime.isBlank()) {
                continue;
            }
            long t = Long.parseLong(info.authorTime);
            if (t > latestTime) {
                latestTime = t;
                developerName = info.authorName;
                developerDate = info.authorDateFormatted;
            }
        }
        return new String[]{developerName, developerDate};
    }
    private static String getCommonDevelopers(List<BlameLineInfo> sourceBlame,
                                              List<BlameLineInfo> addedBlame) {

        Set<String> sourceDevelopers = new LinkedHashSet<>();
        Set<String> addedDevelopers = new LinkedHashSet<>();

        for (BlameLineInfo info : sourceBlame) {
            if (info.authorName != null && !info.authorName.isBlank()) {
                sourceDevelopers.add(info.authorName.trim());
            }
        }

        for (BlameLineInfo info : addedBlame) {
            if (info.authorName != null && !info.authorName.isBlank()) {
                addedDevelopers.add(info.authorName.trim());
            }
        }

        // intersection
        sourceDevelopers.retainAll(addedDevelopers);

        return String.join("; ", sourceDevelopers);
    }
    private static long getDaysBetween(String date1, String date2) {

        LocalDate d1 = LocalDate.parse(date1);
        LocalDate d2 = LocalDate.parse(date2);

        return ChronoUnit.DAYS.between(d1, d2);
    }
}
