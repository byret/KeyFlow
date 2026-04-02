package keyflow.model;

public enum MergeStrategy {
    PREFER_FIRST("Prefer file 1", "Merged output uses file 1 when the same key exists in both files."),
    PREFER_SECOND("Prefer file 2", "Merged output uses file 2 when the same key exists in both files."),
    FIRST_ONLY_FOR_MISSING("Use file 1 only for missing keys", "Keeps file 2 values for conflicts and only fills keys missing from file 2."),
    SECOND_ONLY_FOR_MISSING("Use file 2 only for missing keys", "Keeps file 1 values for conflicts and only fills keys missing from file 1.");

    private final String label;
    private final String description;

    MergeStrategy(String label, String description) {
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
