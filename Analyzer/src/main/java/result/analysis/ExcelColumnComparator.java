package result.analysis;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;

public class ExcelColumnComparator {
    static String projectName = "commons-validator";
    static String file1Path = "/Users/afrinakhatun/Downloads/" + projectName + "_set_Result.xlsx";
    static String file2Path = "/Users/afrinakhatun/Downloads/Copy of " + projectName + "_set_Result.xlsx";
    static String outputPath = "/Users/afrinakhatun/IdeaProjects/Analyzer/AnnotationResult/" + projectName + "_Comparison_result.xlsx";
    static String sheetName = "SetResult";
    static int numOfRows = 13; // number of data rows, excluding header

    public static void main(String[] args) {

        List<String> columnsToCompare = Arrays.asList(
                "variable name",
                "literal value",
                "constructor change",
                "source method change",
                "assertion addition",
                "exception"
        );

        try {
            compareExcelFiles(file1Path, file2Path, outputPath, sheetName, columnsToCompare);
            System.out.println("Comparison completed. Output written to: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void compareExcelFiles(String file1Path,
                                         String file2Path,
                                         String outputPath,
                                         String sheetName,
                                         List<String> columnsToCompare) throws Exception {

        FileInputStream fis1 = new FileInputStream(file1Path);
        FileInputStream fis2 = new FileInputStream(file2Path);

        Workbook wb1 = WorkbookFactory.create(fis1);
        Workbook wb2 = WorkbookFactory.create(fis2);

        Sheet sheet1 = wb1.getSheet(sheetName);
        Sheet sheet2 = wb2.getSheet(sheetName);

        if (sheet1 == null || sheet2 == null) {
            throw new RuntimeException("Sheet not found in one or both files: " + sheetName);
        }

        Workbook outputWb = new XSSFWorkbook();
        Sheet outputSheet = outputWb.createSheet(sheetName);

        copySheet(sheet1, outputSheet, outputWb);

        Map<String, Integer> headerMap1 = getHeaderMap(sheet1);
        Map<String, Integer> headerMap2 = getHeaderMap(sheet2);

        Map<String, Integer> matchedCount = new LinkedHashMap<>();
        Map<String, Integer> unmatchedCount = new LinkedHashMap<>();
        Map<String, List<String>> annotator1LabelsByColumn = new LinkedHashMap<>();
        Map<String, List<String>> annotator2LabelsByColumn = new LinkedHashMap<>();

        for (String column : columnsToCompare) {
            if (!headerMap1.containsKey(column) || !headerMap2.containsKey(column)) {
                throw new RuntimeException("Column not found in one or both files: " + column);
            }
            matchedCount.put(column, 0);
            unmatchedCount.put(column, 0);
            annotator1LabelsByColumn.put(column, new ArrayList<>());
            annotator2LabelsByColumn.put(column, new ArrayList<>());
        }

        System.out.println("Comparing rows: " + numOfRows);

        DataFormatter formatter = new DataFormatter();

        for (int rowIndex = 1; rowIndex <= numOfRows; rowIndex++) {
            Row row1 = sheet1.getRow(rowIndex);
            Row row2 = sheet2.getRow(rowIndex);
            Row outputRow = outputSheet.getRow(rowIndex);

            if (row1 == null) row1 = sheet1.createRow(rowIndex);
            if (row2 == null) row2 = sheet2.createRow(rowIndex);
            if (outputRow == null) outputRow = outputSheet.createRow(rowIndex);

            for (String column : columnsToCompare) {
                int colIndex1 = headerMap1.get(column);
                int colIndex2 = headerMap2.get(column);

                Cell cell1 = row1.getCell(colIndex1);
                Cell cell2 = row2.getCell(colIndex2);
                Cell outputCell = outputRow.getCell(colIndex1);

                String value1 = normalizeLabel(column, getCellValueAsString(cell1, formatter));
                String value2 = normalizeLabel(column, getCellValueAsString(cell2, formatter));

                annotator1LabelsByColumn.get(column).add(value1);
                annotator2LabelsByColumn.get(column).add(value2);

                if (Objects.equals(value1, value2)) {
                    matchedCount.put(column, matchedCount.get(column) + 1);
                } else {
                    unmatchedCount.put(column, unmatchedCount.get(column) + 1);

                    if (outputCell == null) {
                        outputCell = outputRow.createCell(colIndex1);
                    }

                    CellStyle newStyle = outputWb.createCellStyle();
                    if (outputCell.getCellStyle() != null) {
                        newStyle.cloneStyleFrom(outputCell.getCellStyle());
                    }
                    newStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
                    newStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                    outputCell.setCellStyle(newStyle);
                }
            }
        }

        Map<String, Double> kappaByColumn = new LinkedHashMap<>();
        double totalKappa = 0.0;

        System.out.println("\nColumn-wise comparison summary:");
        for (String column : columnsToCompare) {
            List<String> labels1 = annotator1LabelsByColumn.get(column);
            List<String> labels2 = annotator2LabelsByColumn.get(column);

            double kappa = computeCohensKappa(labels1, labels2);
            kappaByColumn.put(column, kappa);
            totalKappa += kappa;

            int matched = matchedCount.get(column);
            int unmatched = unmatchedCount.get(column);
            int total = matched + unmatched;
            double rawAgreement = total == 0 ? 0.0 : (double) matched / total;

            System.out.printf(
                    "%s -> Matched: %d, Unmatched: %d, Raw Agreement: %.4f, Cohen's Kappa: %.4f%n",
                    column, matched, unmatched, rawAgreement, kappa
            );
        }

        double averageKappa = columnsToCompare.isEmpty() ? 0.0 : totalKappa / columnsToCompare.size();
        System.out.printf("%nAverage Cohen's Kappa across all columns: %.4f%n", averageKappa);

        writeSummarySheet(outputWb, matchedCount, unmatchedCount, kappaByColumn, averageKappa);

        FileOutputStream fos = new FileOutputStream(outputPath);
        outputWb.write(fos);

        fos.close();
        fis1.close();
        fis2.close();
        wb1.close();
        wb2.close();
        outputWb.close();
    }

    private static double computeCohensKappa(List<String> annotator1, List<String> annotator2) {
        if (annotator1.size() != annotator2.size()) {
            throw new IllegalArgumentException("Annotator label lists must have the same size.");
        }

        int n = annotator1.size();
        if (n == 0) {
            return 0.0;
        }

        int agreementCount = 0;
        Map<String, Integer> freq1 = new HashMap<>();
        Map<String, Integer> freq2 = new HashMap<>();

        for (int i = 0; i < n; i++) {
            String label1 = annotator1.get(i);
            String label2 = annotator2.get(i);

            if (Objects.equals(label1, label2)) {
                agreementCount++;
            }

            freq1.put(label1, freq1.getOrDefault(label1, 0) + 1);
            freq2.put(label2, freq2.getOrDefault(label2, 0) + 1);
        }

        double po = (double) agreementCount / n;

        Set<String> allLabels = new HashSet<>();
        allLabels.addAll(freq1.keySet());
        allLabels.addAll(freq2.keySet());

        double pe = 0.0;
        for (String label : allLabels) {
            double p1 = (double) freq1.getOrDefault(label, 0) / n;
            double p2 = (double) freq2.getOrDefault(label, 0) / n;
            pe += p1 * p2;
        }

        double denominator = 1.0 - pe;

        if (Math.abs(denominator) < 1e-12) {
            return po == 1.0 ? 1.0 : 0.0;
        }

        return (po - pe) / denominator;
    }

    private static String normalizeLabel(String column, String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim().toLowerCase();

        //return normalized;

        // variable name: treat "similar" and "changed" as same
        if (column.equalsIgnoreCase("variable name")) {
            if (normalized.equals("similar")) {
                return "changed";
            }
        }

        // literal value: treat "partially similar" and "changed" as same
        if (column.equalsIgnoreCase("literal value")) {
            if (normalized.equals("partially similar")) {
                return "changed";
            }
        }
        if (column.equalsIgnoreCase("constructor change")) {
            if (normalized.equals("no constructor")) {
               return "same constructor";
            }
        }
        if (column.equalsIgnoreCase("assertion addition")) {
            String[] vals = normalized.split("|");
            List<String> finalValues = new ArrayList<>();
            for(String v:vals){
                if(!v.equals("similar asserted methods/attributes")){
                    finalValues.add(v);
                }
            }
            return  finalValues.toString();
        }

        return normalized;
    }

    private static void writeSummarySheet(Workbook workbook,
                                          Map<String, Integer> matchedCount,
                                          Map<String, Integer> unmatchedCount,
                                          Map<String, Double> kappaByColumn,
                                          double averageKappa) {

        Sheet summarySheet = workbook.createSheet("AgreementSummary");

        Row header = summarySheet.createRow(0);
        header.createCell(0).setCellValue("Column");
        header.createCell(1).setCellValue("Matched");
        header.createCell(2).setCellValue("Unmatched");
        header.createCell(3).setCellValue("Raw Agreement");
        header.createCell(4).setCellValue("Cohen's Kappa");

        int rowIndex = 1;
        for (String column : matchedCount.keySet()) {
            int matched = matchedCount.get(column);
            int unmatched = unmatchedCount.get(column);
            int total = matched + unmatched;
            double rawAgreement = total == 0 ? 0.0 : (double) matched / total;
            double kappa = kappaByColumn.getOrDefault(column, 0.0);

            Row row = summarySheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(column);
            row.createCell(1).setCellValue(matched);
            row.createCell(2).setCellValue(unmatched);
            row.createCell(3).setCellValue(rawAgreement);
            row.createCell(4).setCellValue(kappa);
        }

        Row avgRow = summarySheet.createRow(rowIndex);
        avgRow.createCell(0).setCellValue("Average Kappa");
        avgRow.createCell(4).setCellValue(averageKappa);

        for (int i = 0; i <= 4; i++) {
            summarySheet.autoSizeColumn(i);
        }
    }

    private static Map<String, Integer> getHeaderMap(Sheet sheet) {
        Map<String, Integer> headerMap = new LinkedHashMap<>();
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new RuntimeException("Header row is missing.");
        }

        DataFormatter formatter = new DataFormatter();

        for (Cell cell : headerRow) {
            String header = formatter.formatCellValue(cell).trim();
            headerMap.put(header, cell.getColumnIndex());
        }

        return headerMap;
    }

    private static String getCellValueAsString(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        return formatter.formatCellValue(cell);
    }

    private static void copySheet(Sheet sourceSheet, Sheet targetSheet, Workbook targetWorkbook) {
        int maxColumnNum = 0;

        for (int i = 0; i <= sourceSheet.getLastRowNum(); i++) {
            Row sourceRow = sourceSheet.getRow(i);
            Row targetRow = targetSheet.createRow(i);

            if (sourceRow != null) {
                copyRow(sourceRow, targetRow, targetWorkbook);
                if (sourceRow.getLastCellNum() > maxColumnNum) {
                    maxColumnNum = sourceRow.getLastCellNum();
                }
            }
        }

        for (int i = 0; i < maxColumnNum; i++) {
            targetSheet.setColumnWidth(i, sourceSheet.getColumnWidth(i));
        }
    }

    private static void copyRow(Row sourceRow, Row targetRow, Workbook targetWorkbook) {
        targetRow.setHeight(sourceRow.getHeight());

        for (int j = 0; j < sourceRow.getLastCellNum(); j++) {
            Cell sourceCell = sourceRow.getCell(j);
            Cell targetCell = targetRow.createCell(j);

            if (sourceCell != null) {
                copyCell(sourceCell, targetCell, targetWorkbook);
            }
        }
    }

    private static void copyCell(Cell sourceCell, Cell targetCell, Workbook targetWorkbook) {
        CellStyle newCellStyle = targetWorkbook.createCellStyle();
        if (sourceCell.getCellStyle() != null) {
            newCellStyle.cloneStyleFrom(sourceCell.getCellStyle());
        }
        targetCell.setCellStyle(newCellStyle);

        switch (sourceCell.getCellType()) {
            case STRING:
                targetCell.setCellValue(sourceCell.getStringCellValue());
                break;
            case NUMERIC:
                targetCell.setCellValue(sourceCell.getNumericCellValue());
                break;
            case BOOLEAN:
                targetCell.setCellValue(sourceCell.getBooleanCellValue());
                break;
            case FORMULA:
                targetCell.setCellFormula(sourceCell.getCellFormula());
                break;
            case BLANK:
                targetCell.setBlank();
                break;
            default:
                targetCell.setCellValue(sourceCell.toString());
                break;
        }
    }
}