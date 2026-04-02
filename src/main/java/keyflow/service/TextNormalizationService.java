package keyflow.service;

import keyflow.model.RuleMode;
import keyflow.model.TextRule;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class TextNormalizationService {

    private static final Pattern MULTI_WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern SPACE_BEFORE_PUNCTUATION_PATTERN = Pattern.compile("\\s+([,.;:!?])");
    private static final String RULE_DELIMITER = "\t";

    public String sanitizeForOutput(String value, String transformationRules) {
        if (value == null) {
            return "";
        }

        String result = value;
        for (CompiledTextRule rule : parseRules(transformationRules)) {
            String replacement = rule.mode() == RuleMode.REMOVE ? "" : rule.replacement();
            result = rule.pattern().matcher(result).replaceAll(replacement);
        }

        return cleanupSpacing(result);
    }

    public List<TextRule> parseRulesForDisplay(String transformationRules) {
        return parseRules(transformationRules).stream()
                .map(rule -> new TextRule(rule.mode(), rule.source(), rule.replacement()))
                .toList();
    }

    private List<CompiledTextRule> parseRules(String transformationRules) {
        if (transformationRules == null || transformationRules.isBlank()) {
            return List.of();
        }

        List<CompiledTextRule> rules = new ArrayList<>();
        String[] lines = transformationRules.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split(RULE_DELIMITER, -1);
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid rule on line " + (i + 1) + ".");
            }

            RuleMode mode;
            try {
                mode = RuleMode.valueOf(parts[0].trim());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid rule mode on line " + (i + 1) + ": " + parts[0]);
            }

            String patternSource = parts[1];
            String replacement = parts.length >= 3 ? parts[2] : "";
            if (patternSource.isBlank()) {
                throw new IllegalArgumentException("Pattern is empty on line " + (i + 1) + ".");
            }

            try {
                rules.add(new CompiledTextRule(mode, patternSource, Pattern.compile(patternSource), replacement));
            } catch (PatternSyntaxException ex) {
                throw new IllegalArgumentException("Invalid regex on line " + (i + 1) + ": " + patternSource);
            }
        }
        return rules;
    }

    private String cleanupSpacing(String text) {
        String result = MULTI_WHITESPACE_PATTERN.matcher(text).replaceAll(" ");
        result = result.replaceAll("\\(\\s+", "(");
        result = result.replaceAll("\\s+\\)", ")");
        result = result.replaceAll("\\[\\s+", "[");
        result = result.replaceAll("\\s+\\]", "]");
        result = result.replaceAll("\\{\\s+", "{");
        result = result.replaceAll("\\s+\\}", "}");
        result = SPACE_BEFORE_PUNCTUATION_PATTERN.matcher(result).replaceAll("$1");
        result = result.replaceAll("\\(\\)", "");
        result = result.replaceAll("\\[\\]", "");
        result = result.replaceAll("\\{\\}", "");
        result = MULTI_WHITESPACE_PATTERN.matcher(result).replaceAll(" ");
        return result.trim();
    }

    public String normalizeForComparison(String value, boolean ignoreWhitespace, boolean ignoreCase) {
        String normalized = value == null ? "" : value;
        if (ignoreWhitespace) {
            normalized = MULTI_WHITESPACE_PATTERN.matcher(normalized.trim()).replaceAll(" ");
        }
        if (ignoreCase) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        }
        return normalized;
    }

    private record CompiledTextRule(RuleMode mode, String source, Pattern pattern, String replacement) {
    }
}
