package keyflow.model;

import java.io.Serializable;

public record TextRule(RuleMode mode, String pattern, String replacement) implements Serializable {
}
