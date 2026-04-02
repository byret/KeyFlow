package keyflow.service;

import keyflow.model.*;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ExportService {

    public byte[] exportSection(ComparisonResult result,
                                String section,
                                ExportFormat format,
                                DifferentViewMode differentViewMode) throws IOException {
        return switch (section) {
            case "merged" -> exportMerged(result, format);
            case "missing-in-second" -> exportMissingInSecond(result, format);
            case "missing-in-first" -> exportMissingInFirst(result, format);
            case "different" -> exportDifferent(result, format, differentViewMode);
            case "workbook" -> exportWorkbookXlsx(result);
            default -> throw new IllegalArgumentException("Unknown section: " + section);
        };
    }

    public byte[] exportMerged(ComparisonResult result, ExportFormat format) throws IOException {
        return switch (format) {
            case TSV, TXT -> exportRowsDelimited(result.mergedRows(), "\t");
            case CSV -> exportRowsDelimited(result.mergedRows(), ",");
            case PROPERTIES -> exportProperties(result.mergedRows());
            case XLSX -> exportSingleSheetXlsx("merged", result.mergedRows(), null, DifferentViewMode.THREE_COLUMNS);
        };
    }

    public byte[] exportMissingInSecond(ComparisonResult result, ExportFormat format) throws IOException {
        return switch (format) {
            case TSV, TXT -> exportRowsDelimited(result.missingInSecondRows(), "\t");
            case CSV -> exportRowsDelimited(result.missingInSecondRows(), ",");
            case PROPERTIES -> exportProperties(result.missingInSecondRows());
            case XLSX -> exportSingleSheetXlsx("missing_in_second", result.missingInSecondRows(), null, DifferentViewMode.THREE_COLUMNS);
        };
    }

    public byte[] exportMissingInFirst(ComparisonResult result, ExportFormat format) throws IOException {
        return switch (format) {
            case TSV, TXT -> exportRowsDelimited(result.missingInFirstRows(), "\t");
            case CSV -> exportRowsDelimited(result.missingInFirstRows(), ",");
            case PROPERTIES -> exportProperties(result.missingInFirstRows());
            case XLSX -> exportSingleSheetXlsx("missing_in_first", result.missingInFirstRows(), null, DifferentViewMode.THREE_COLUMNS);
        };
    }

    public byte[] exportDifferent(ComparisonResult result, ExportFormat format, DifferentViewMode mode) throws IOException {
        return switch (format) {
            case TSV, TXT -> mode == DifferentViewMode.PRETTY_DIFF_TEXT
                    ? exportPrettyDifferent(result.differentRows())
                    : exportDifferencesDelimited(result.differentRows(), "\t");
            case CSV -> mode == DifferentViewMode.PRETTY_DIFF_TEXT
                    ? exportPrettyDifferentCsv(result.differentRows())
                    : exportDifferencesDelimited(result.differentRows(), ",");
            case PROPERTIES -> exportRowsDelimited(result.differentRows().stream()
                    .map(row -> new ComparisonRow(row.key(), row.secondValue())).toList(), "=");
            case XLSX -> exportSingleSheetXlsx("different", null, result.differentRows(), mode);
        };
    }

    public byte[] exportWorkbookXlsx(ComparisonResult result) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            writeComparisonSheet(workbook, "merged", result.mergedRows());
            writeComparisonSheet(workbook, "missing_in_second", result.missingInSecondRows());
            writeComparisonSheet(workbook, "missing_in_first", result.missingInFirstRows());
            writeDifferenceSheet(workbook, "different", result.differentRows(), DifferentViewMode.THREE_COLUMNS);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    public byte[] exportAllAsZip(ComparisonResult result) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            put(zip, "merged.tsv", exportMerged(result, ExportFormat.TSV));
            put(zip, "merged.txt", exportMerged(result, ExportFormat.TXT));
            put(zip, "merged.csv", exportMerged(result, ExportFormat.CSV));
            put(zip, "merged.properties", exportMerged(result, ExportFormat.PROPERTIES));

            put(zip, "missing-in-second.tsv", exportMissingInSecond(result, ExportFormat.TSV));
            put(zip, "missing-in-second.txt", exportMissingInSecond(result, ExportFormat.TXT));
            put(zip, "missing-in-first.tsv", exportMissingInFirst(result, ExportFormat.TSV));
            put(zip, "missing-in-first.txt", exportMissingInFirst(result, ExportFormat.TXT));

            put(zip, "different.tsv", exportDifferent(result, ExportFormat.TSV, DifferentViewMode.THREE_COLUMNS));
            put(zip, "different-pretty.txt", exportDifferent(result, ExportFormat.TXT, DifferentViewMode.PRETTY_DIFF_TEXT));
            put(zip, "comparison.xlsx", exportWorkbookXlsx(result));

            zip.finish();
            return outputStream.toByteArray();
        }
    }

    private byte[] exportSingleSheetXlsx(String sheetName,
                                         List<ComparisonRow> rows,
                                         List<DifferenceRow> differences,
                                         DifferentViewMode differentViewMode) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (rows != null) {
                writeComparisonSheet(workbook, sheetName, rows);
            } else {
                writeDifferenceSheet(workbook, sheetName, differences, differentViewMode);
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void writeComparisonSheet(XSSFWorkbook workbook, String sheetName, List<ComparisonRow> rows) {
        var sheet = workbook.createSheet(sheetName);
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("key");
        header.createCell(1).setCellValue("value");

        int rowIndex = 1;
        for (ComparisonRow rowValue : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(rowValue.key());
            row.createCell(1).setCellValue(rowValue.value());
        }
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void writeDifferenceSheet(XSSFWorkbook workbook, String sheetName, List<DifferenceRow> rows, DifferentViewMode mode) {
        var sheet = workbook.createSheet(sheetName);
        Row header = sheet.createRow(0);
        if (mode == DifferentViewMode.PRETTY_DIFF_TEXT) {
            header.createCell(0).setCellValue("key");
            header.createCell(1).setCellValue("pretty diff");
            header.createCell(2).setCellValue("severity");
            header.createCell(3).setCellValue("reason");

            int rowIndex = 1;
            for (DifferenceRow value : rows) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(value.key());
                row.createCell(1).setCellValue(buildPrettyDiffText(value));
                row.createCell(2).setCellValue(value.severity().name());
                row.createCell(3).setCellValue(value.severityReason());
            }
            for (int i = 0; i < 4; i++) {
                sheet.autoSizeColumn(i);
            }
            return;
        }

        header.createCell(0).setCellValue("key");
        header.createCell(1).setCellValue("first value");
        header.createCell(2).setCellValue("second value");
        header.createCell(3).setCellValue("severity");
        header.createCell(4).setCellValue("reason");

        int rowIndex = 1;
        for (DifferenceRow value : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(value.key());
            row.createCell(1).setCellValue(value.firstValue());
            row.createCell(2).setCellValue(value.secondValue());
            row.createCell(3).setCellValue(value.severity().name());
            row.createCell(4).setCellValue(value.severityReason());
        }
        for (int i = 0; i < 5; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private byte[] exportRowsDelimited(List<ComparisonRow> rows, String delimiter) {
        StringBuilder builder = new StringBuilder();
        for (ComparisonRow row : rows) {
            builder.append(escapeCell(row.key(), delimiter))
                    .append(delimiter)
                    .append(escapeCell(row.value(), delimiter))
                    .append("\n");
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] exportDifferencesDelimited(List<DifferenceRow> rows, String delimiter) {
        StringBuilder builder = new StringBuilder();
        for (DifferenceRow row : rows) {
            builder.append(escapeCell(row.key(), delimiter)).append(delimiter)
                    .append(escapeCell(row.firstValue(), delimiter)).append(delimiter)
                    .append(escapeCell(row.secondValue(), delimiter)).append(delimiter)
                    .append(row.severity().name()).append(delimiter)
                    .append(escapeCell(row.severityReason(), delimiter))
                    .append("\n");
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] exportPrettyDifferent(List<DifferenceRow> rows) {
        StringBuilder builder = new StringBuilder();
        for (DifferenceRow row : rows) {
            builder.append(row.key()).append("\t")
                    .append(buildPrettyDiffText(row)).append("\t")
                    .append(row.severity().name()).append("\t")
                    .append(row.severityReason())
                    .append("\n");
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] exportPrettyDifferentCsv(List<DifferenceRow> rows) {
        StringBuilder builder = new StringBuilder();
        for (DifferenceRow row : rows) {
            builder.append(escapeCell(row.key(), ",")).append(",")
                    .append(escapeCell(buildPrettyDiffText(row), ",")).append(",")
                    .append(row.severity().name()).append(",")
                    .append(escapeCell(row.severityReason(), ","))
                    .append("\n");
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] exportProperties(List<ComparisonRow> rows) {
        StringBuilder builder = new StringBuilder();
        for (ComparisonRow row : rows) {
            builder.append(row.key()).append("=").append(row.value()).append("\n");
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String buildPrettyDiffText(DifferenceRow row) {
        return "File 1: " + row.firstValue() + " || File 2: " + row.secondValue();
    }

    private String escapeCell(String value, String delimiter) {
        String safeValue = value == null ? "" : value;
        if ("\t".equals(delimiter)) {
            return safeValue.replace("\t", "    ");
        }
        boolean mustQuote = safeValue.contains(delimiter) || safeValue.contains("\"") || safeValue.contains("\n");
        if (mustQuote) {
            return "\"" + safeValue.replace("\"", "\"\"") + "\"";
        }
        return safeValue;
    }

    private void put(ZipOutputStream zip, String fileName, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(fileName));
        zip.write(content);
        zip.closeEntry();
    }
}
