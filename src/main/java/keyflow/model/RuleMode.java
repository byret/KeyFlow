package keyflow.model;

public enum RuleMode {
    REMOVE("Remove"),
    REPLACE("Replace");

    private final String label;

    RuleMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
