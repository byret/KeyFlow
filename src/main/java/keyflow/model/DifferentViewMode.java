package keyflow.model;

public enum DifferentViewMode {
    PRETTY_DIFF_TEXT("Pretty diff text", "Shows a word-level diff with added and removed parts."),
    THREE_COLUMNS("3 columns", "Key, file 1 value, file 2 value.");

    private final String label;
    private final String description;

    DifferentViewMode(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
