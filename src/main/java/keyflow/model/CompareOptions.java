package keyflow.model;

import java.io.Serializable;

public record CompareOptions(
        String prefix,
        String transformationRules,
        String mergeIgnoreTerms,
        boolean ignoreWhitespace,
        boolean ignoreCase,
        MergeStrategy mergeStrategy
) implements Serializable {
}
