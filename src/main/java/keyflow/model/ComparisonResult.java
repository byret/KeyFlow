package keyflow.model;

import java.io.Serializable;
import java.util.List;

public record ComparisonResult(
        int firstCount,
        int secondCount,
        int missingInSecondCount,
        int missingInFirstCount,
        int differentCount,
        CompareOptions options,
        List<ComparisonRow> mergedRows,
        List<ComparisonRow> missingInSecondRows,
        List<ComparisonRow> missingInFirstRows,
        List<DifferenceRow> differentRows
) implements Serializable {
}
