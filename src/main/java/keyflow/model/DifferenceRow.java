package keyflow.model;

import java.io.Serializable;

public record DifferenceRow(
        String key,
        String firstValue,
        String secondValue,
        String firstDiffHtml,
        String secondDiffHtml,
        DiffSeverity severity,
        String severityReason
) implements Serializable {
}
