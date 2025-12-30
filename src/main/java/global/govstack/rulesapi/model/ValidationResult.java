package global.govstack.rulesapi.model;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of validating a Rules Script.
 */
public class ValidationResult {

    private boolean valid = true;
    private int ruleCount = 0;
    private List<Rule> rules = new ArrayList<>();
    private List<JSONObject> rulesSummary = new ArrayList<>();
    private List<JSONObject> errors = new ArrayList<>();
    private List<JSONObject> warnings = new ArrayList<>();

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public int getRuleCount() {
        return ruleCount;
    }

    public void setRuleCount(int ruleCount) {
        this.ruleCount = ruleCount;
    }

    /**
     * Get the parsed rules (for compilation).
     */
    public List<Rule> getRules() {
        return rules;
    }

    /**
     * Add a parsed rule.
     */
    public void addRule(Rule rule) {
        this.rules.add(rule);
    }

    public List<JSONObject> getRulesSummary() {
        return rulesSummary;
    }

    public void addRuleSummary(String ruleName, String ruleCode, String ruleType, int conditionCount) {
        JSONObject rule = new JSONObject();
        rule.put("ruleName", ruleName);
        rule.put("ruleCode", ruleCode);
        rule.put("ruleType", ruleType);
        rule.put("conditionCount", conditionCount);
        this.rulesSummary.add(rule);
    }

    public List<JSONObject> getErrors() {
        return errors;
    }

    public void addError(int line, int column, String message, String severity) {
        JSONObject error = new JSONObject();
        error.put("line", line);
        error.put("column", column);
        error.put("message", message);
        error.put("severity", severity);
        this.errors.add(error);
    }

    public List<JSONObject> getWarnings() {
        return warnings;
    }

    public void addWarning(int line, int column, String message) {
        JSONObject warning = new JSONObject();
        warning.put("line", line);
        warning.put("column", column);
        warning.put("message", message);
        warning.put("severity", "WARNING");
        this.warnings.add(warning);
    }
}
