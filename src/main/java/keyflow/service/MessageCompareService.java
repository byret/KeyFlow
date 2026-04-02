package keyflow.service;

import keyflow.model.*;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class MessageCompareService {

    private final TextNormalizationService textNormalizationService;
    private final DiffService diffService;

    public MessageCompareService(TextNormalizationService textNormalizationService, DiffService diffService) {
        this.textNormalizationService = textNormalizationService;
        this.diffService = diffService;
    }

    public ComparisonResult compare(byte[] firstFileContent, byte[] secondFileContent, CompareOptions options) throws IOException {
        List<String> ignoredTerms = parseIgnoredTerms(options.mergeIgnoreTerms());
        Map<String, String> first = loadMessages(firstFileContent, options.prefix(), ignoredTerms);
        Map<String, String> second = loadMessages(secondFileContent, options.prefix(), ignoredTerms);

        List<ComparisonRow> missingInSecond = first.keySet().stream()
                .filter(key -> !second.containsKey(key))
                .sorted()
                .map(key -> new ComparisonRow(key, prepareForOutput(first.get(key), options)))
                .toList();

        List<ComparisonRow> missingInFirst = second.keySet().stream()
                .filter(key -> !first.containsKey(key))
                .sorted()
                .map(key -> new ComparisonRow(key, prepareForOutput(second.get(key), options)))
                .toList();

        List<DifferenceRow> differentRows = first.keySet().stream()
                .filter(second::containsKey)
                .sorted()
                .map(key -> buildDifferenceRow(key, first.get(key), second.get(key), options))
                .filter(Objects::nonNull)
                .toList();

        Set<String> allKeys = new TreeSet<>();
        allKeys.addAll(first.keySet());
        allKeys.addAll(second.keySet());

        List<ComparisonRow> mergedRows = allKeys.stream()
                .map(key -> new ComparisonRow(key, chooseMergedValue(key, first, second, options)))
                .toList();

        return new ComparisonResult(
                first.size(),
                second.size(),
                missingInSecond.size(),
                missingInFirst.size(),
                differentRows.size(),
                options,
                mergedRows,
                missingInSecond,
                missingInFirst,
                differentRows
        );
    }

    public List<TextRule> parseRulesForDisplay(String transformationRules) {
        return textNormalizationService.parseRulesForDisplay(transformationRules);
    }

    private DifferenceRow buildDifferenceRow(String key, String firstValue, String secondValue, CompareOptions options) {
        String preparedFirst = prepareForOutput(firstValue, options);
        String preparedSecond = prepareForOutput(secondValue, options);

        String normalizedFirst = textNormalizationService.normalizeForComparison(preparedFirst, options.ignoreWhitespace(), options.ignoreCase());
        String normalizedSecond = textNormalizationService.normalizeForComparison(preparedSecond, options.ignoreWhitespace(), options.ignoreCase());

        if (Objects.equals(normalizedFirst, normalizedSecond)) {
            return null;
        }

        DiffSeverity severity = diffService.detectSeverity(preparedFirst, preparedSecond);
        String reason = diffService.buildSeverityReason(preparedFirst, preparedSecond, severity);
        String[] inlineHtml = diffService.buildInlineDiffHtml(preparedFirst, preparedSecond);

        return new DifferenceRow(
                key,
                preparedFirst,
                preparedSecond,
                inlineHtml[0],
                inlineHtml[1],
                severity,
                reason
        );
    }

    private String chooseMergedValue(String key,
                                     Map<String, String> first,
                                     Map<String, String> second,
                                     CompareOptions options) {
        return switch (options.mergeStrategy()) {
            case PREFER_FIRST -> prepareForOutput(first.containsKey(key) ? first.get(key) : second.get(key), options);
            case PREFER_SECOND -> second.containsKey(key) ? prepareForOutput(second.get(key), options) : prepareForOutput(first.get(key), options);
            case FIRST_ONLY_FOR_MISSING -> second.containsKey(key) ? prepareForOutput(second.get(key), options) : prepareForOutput(first.get(key), options);
            case SECOND_ONLY_FOR_MISSING -> first.containsKey(key) ? prepareForOutput(first.get(key), options) : prepareForOutput(second.get(key), options);
        };
    }

    private String prepareForOutput(String value, CompareOptions options) {
        return textNormalizationService.sanitizeForOutput(value, options.transformationRules());
    }

    private Map<String, String> loadMessages(byte[] content, String prefix, List<String> ignoredTerms) throws IOException {
        Map<String, String> result = new TreeMap<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                ParsedLine parsed = parseLine(line, prefix, ignoredTerms);
                if (parsed != null) {
                    result.put(parsed.key(), parsed.value());
                }
            }
        }
        return result;
    }

    private ParsedLine parseLine(String line, String prefix, List<String> ignoredTerms) {
        if (line == null) {
            return null;
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty() || !trimmed.startsWith(prefix) || shouldIgnoreLine(trimmed, ignoredTerms)) {
            return null;
        }

        int tabIndex = trimmed.indexOf('\t');
        if (tabIndex > 0) {
            String key = trimmed.substring(0, tabIndex).trim();
            String value = trimmed.substring(tabIndex + 1).trim();
            return key.isEmpty() ? null : new ParsedLine(key, value);
        }

        int eqIndex = trimmed.indexOf('=');
        if (eqIndex > 0) {
            String key = trimmed.substring(0, eqIndex).trim();
            String value = trimmed.substring(eqIndex + 1).trim();
            return key.isEmpty() ? null : new ParsedLine(key, value);
        }

        int whitespaceIndex = firstWhitespaceIndex(trimmed);
        if (whitespaceIndex > 0) {
            String key = trimmed.substring(0, whitespaceIndex).trim();
            String value = trimmed.substring(whitespaceIndex).trim();
            return key.isEmpty() ? null : new ParsedLine(key, value);
        }

        return null;
    }


    private List<String> parseIgnoredTerms(String mergeIgnoreTerms) {
        if (mergeIgnoreTerms == null || mergeIgnoreTerms.isBlank()) {
            return List.of();
        }
        return Arrays.stream(mergeIgnoreTerms.split("\\R"))
                .map(String::trim)
                .filter(term -> !term.isEmpty())
                .map(term -> term.toLowerCase(Locale.ROOT))
                .toList();
    }

    private boolean shouldIgnoreLine(String line, List<String> ignoredTerms) {
        if (ignoredTerms == null || ignoredTerms.isEmpty()) {
            return false;
        }
        String lowered = line.toLowerCase(Locale.ROOT);
        return ignoredTerms.stream().anyMatch(lowered::contains);
    }

    private int firstWhitespaceIndex(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
