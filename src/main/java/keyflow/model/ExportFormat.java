package keyflow.model;

public enum ExportFormat {
    TSV("TSV", "Tab-separated values, good for Excel and Word tables.", "tsv", "text/tab-separated-values"),
    CSV("CSV", "Comma-separated values.", "csv", "text/csv"),
    TXT("TXT", "Plain text with tab-separated columns.", "txt", "text/plain"),
    PROPERTIES("Properties", "key=value output.", "properties", "text/plain"),
    XLSX("XLSX", "Excel workbook.", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String label;
    private final String description;
    private final String extension;
    private final String contentType;

    ExportFormat(String label, String description, String extension, String contentType) {
        this.label = label;
        this.description = description;
        this.extension = extension;
        this.contentType = contentType;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public String getExtension() {
        return extension;
    }

    public String getContentType() {
        return contentType;
    }
}
