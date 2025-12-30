package global.govstack.rulesapi.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a parsed rule.
 *
 * Example Rules Script rule:
 * <pre>
 * RULE "Adult Farmer"
 *   TYPE: INCLUSION
 *   CATEGORY: demographic
 *   MANDATORY: YES
 *   ORDER: 10
 *   WHEN age >= 18
 *   FAIL MESSAGE: "Farmer must be at least 18 years old"
 * </pre>
 */
public class Rule {

    private String name;
    private String code;
    private RuleType type = RuleType.INCLUSION;
    private String category;
    private boolean mandatory = false;
    private int order = 0;
    private Condition condition;
    private Integer score;
    private Integer weight;
    private String passMessage;
    private String failMessage;

    // Source location for error reporting
    private int startLine;
    private int endLine;

    public enum RuleType {
        INCLUSION,
        EXCLUSION,
        PRIORITY,
        BONUS
    }

    public Rule() {
    }

    public Rule(String name) {
        this.name = name;
        this.code = generateCode(name);
    }

    /**
     * Generate a rule code from the name.
     * "Adult Farmer" -> "ADULT_FARMER"
     */
    private String generateCode(String name) {
        if (name == null) return null;
        return name.toUpperCase()
            .replaceAll("[^A-Z0-9]+", "_")
            .replaceAll("^_|_$", "");
    }

    // === Getters and Setters ===

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        if (this.code == null) {
            this.code = generateCode(name);
        }
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public RuleType getType() {
        return type;
    }

    public void setType(RuleType type) {
        this.type = type;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public Condition getCondition() {
        return condition;
    }

    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    public String getPassMessage() {
        return passMessage;
    }

    public void setPassMessage(String passMessage) {
        this.passMessage = passMessage;
    }

    public String getFailMessage() {
        return failMessage;
    }

    public void setFailMessage(String failMessage) {
        this.failMessage = failMessage;
    }

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    /**
     * Get all field references used in this rule's condition.
     */
    public List<String> getFieldReferences() {
        List<String> fields = new ArrayList<>();
        if (condition != null) {
            collectFieldReferences(condition, fields);
        }
        return fields;
    }

    private void collectFieldReferences(Condition cond, List<String> fields) {
        if (cond.getFieldId() != null) {
            fields.add(cond.getFieldId());
        }
        if (cond.getLeft() != null) {
            collectFieldReferences(cond.getLeft(), fields);
        }
        if (cond.getRight() != null) {
            collectFieldReferences(cond.getRight(), fields);
        }
    }

    @Override
    public String toString() {
        return String.format("Rule{name='%s', type=%s, mandatory=%s, condition=%s}",
            name, type, mandatory, condition);
    }
}
