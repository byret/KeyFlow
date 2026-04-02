package keyflow.service;

import keyflow.model.DiffSeverity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class DiffService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("(\\w+|\\s+|[^\\w\\s])");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("(\\{\\d+}|\\[[^\\]]+])");
    private static final Set<String> HIGH_SIGNAL_WORDS = Set.of(
            "operator", "client", "reader", "map", "request", "response",
            "success", "failed", "error", "alarm", "reset", "arm", "disarm",
            "on", "off", "allowed", "denied", "create", "delete", "update"
    );

    public String[] buildInlineDiffHtml(String left, String right) {
        List<String> leftTokens = tokenize(left);
        List<String> rightTokens = tokenize(right);

        int[][] lcs = buildLcsTable(leftTokens, rightTokens);
        StringBuilder leftHtml = new StringBuilder();
        StringBuilder rightHtml = new StringBuilder();

        int i = 0;
        int j = 0;
        while (i < leftTokens.size() && j < rightTokens.size()) {
            if (Objects.equals(leftTokens.get(i), rightTokens.get(j))) {
                leftHtml.append(escapeHtml(leftTokens.get(i)));
                rightHtml.append(escapeHtml(rightTokens.get(j)));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                leftHtml.append("<span class=\"diff-removed\">").append(escapeHtml(leftTokens.get(i))).append("</span>");
                i++;
            } else {
                rightHtml.append("<span class=\"diff-added\">").append(escapeHtml(rightTokens.get(j))).append("</span>");
                j++;
            }
        }

        while (i < leftTokens.size()) {
            leftHtml.append("<span class=\"diff-removed\">").append(escapeHtml(leftTokens.get(i))).append("</span>");
            i++;
        }
        while (j < rightTokens.size()) {
            rightHtml.append("<span class=\"diff-added\">").append(escapeHtml(rightTokens.get(j))).append("</span>");
            j++;
        }

        return new String[]{leftHtml.toString(), rightHtml.toString()};
    }

    public DiffSeverity detectSeverity(String left, String right) {
        String leftNormalized = left == null ? "" : left.trim();
        String rightNormalized = right == null ? "" : right.trim();

        if (leftNormalized.equalsIgnoreCase(rightNormalized)) {
            return DiffSeverity.LOW;
        }
        if (hasPlaceholderDifference(leftNormalized, rightNormalized) || hasHighSignalDifference(leftNormalized, rightNormalized)) {
            return DiffSeverity.HIGH;
        }
        if (Math.abs(leftNormalized.length() - rightNormalized.length()) > 30 || changedTokenCount(leftNormalized, rightNormalized) > 6) {
            return DiffSeverity.MEDIUM;
        }
        return DiffSeverity.LOW;
    }

    public String buildSeverityReason(String left, String right, DiffSeverity severity) {
        return switch (severity) {
            case HIGH -> "Changed placeholders or important signal words, so the meaning may have shifted.";
            case MEDIUM -> "Several words changed or the sentence became noticeably different.";
            case LOW -> "Mostly wording simplification or small phrasing changes.";
        };
    }

    private boolean hasPlaceholderDifference(String left, String right) {
        return !extractMatches(left, PLACEHOLDER_PATTERN).equals(extractMatches(right, PLACEHOLDER_PATTERN));
    }

    private boolean hasHighSignalDifference(String left, String right) {
        return !importantWords(left).equals(importantWords(right));
    }

    private int changedTokenCount(String left, String right) {
        List<String> leftTokens = tokenize(left).stream().filter(token -> !token.isBlank()).toList();
        List<String> rightTokens = tokenize(right).stream().filter(token -> !token.isBlank()).toList();
        int[][] lcs = buildLcsTable(leftTokens, rightTokens);
        int unchanged = lcs[0][0];
        return (leftTokens.size() - unchanged) + (rightTokens.size() - unchanged);
    }

    private Set<String> importantWords(String input) {
        Set<String> words = new TreeSet<>();
        for (String token : tokenize(input)) {
            String normalized = token.toLowerCase(Locale.ROOT).trim();
            if (HIGH_SIGNAL_WORDS.contains(normalized)) {
                words.add(normalized);
            }
        }
        return words;
    }

    private List<String> extractMatches(String input, Pattern pattern) {
        List<String> matches = new ArrayList<>();
        var matcher = pattern.matcher(input == null ? "" : input);
        while (matcher.find()) {
            matches.add(matcher.group());
        }
        return matches;
    }

    private int[][] buildLcsTable(List<String> left, List<String> right) {
        int[][] dp = new int[left.size() + 1][right.size() + 1];
        for (int i = left.size() - 1; i >= 0; i--) {
            for (int j = right.size() - 1; j >= 0; j--) {
                if (Objects.equals(left.get(i), right.get(j))) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }
        return dp;
    }

    private List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        var matcher = TOKEN_PATTERN.matcher(input == null ? "" : input);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
