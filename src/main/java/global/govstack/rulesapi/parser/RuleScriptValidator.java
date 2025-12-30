package global.govstack.rulesapi.parser;

import global.govstack.rulesapi.model.Condition;
import global.govstack.rulesapi.model.Rule;
import global.govstack.rulesapi.model.ValidationResult;
import global.govstack.rulesapi.service.FieldRegistryService;
import global.govstack.rulesapi.service.FieldRegistryService.FieldDefinition;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Semantic validator for rules.
 *
 * Validates parsed rules against the field registry to ensure:
 * - All referenced fields exist
 * - Operators are valid for field types
 * - Aggregation functions are valid for grid fields
 */
public class RuleScriptValidator {

    private final FieldRegistryService fieldRegistry;
    private final String scopeCode;

    public RuleScriptValidator(FieldRegistryService fieldRegistry, String scopeCode) {
        this.fieldRegistry = fieldRegistry;
        this.scopeCode = scopeCode;
    }

    /**
     * Validate a parsed rule.
     *
     * @param rule The parsed rule
     * @param result ValidationResult to add warnings/errors to
     */
    public void validateRule(Rule rule, ValidationResult result) {
        if (rule == null) return;

        // Validate condition
        if (rule.getCondition() != null) {
            validateCondition(rule.getCondition(), result);
        }

        // Validate rule-level constraints
        if (rule.getType() == Rule.RuleType.PRIORITY || rule.getType() == Rule.RuleType.BONUS) {
            if (rule.getScore() == null) {
                result.addWarning(rule.getStartLine(), 1,
                    "Rule '" + rule.getName() + "' is " + rule.getType() + " but has no SCORE");
            }
        }
    }

    /**
     * Validate a condition tree.
     */
    private void validateCondition(Condition condition, ValidationResult result) {
        if (condition == null) return;

        switch (condition.getType()) {
            case COMPARISON:
            case BETWEEN:
            case IN:
            case IS_EMPTY:
            case IS_NOT_EMPTY:
                validateFieldReference(condition.getFieldId(), condition.getLine(),
                    condition.getColumn(), result);
                break;

            case AND:
            case OR:
                validateCondition(condition.getLeft(), result);
                validateCondition(condition.getRight(), result);
                break;

            case NOT:
            case GROUP:
                validateCondition(condition.getInner(), result);
                break;

            case FUNCTION_CALL:
                validateFunctionCall(condition, result);
                break;
        }
    }

    /**
     * Validate a field reference exists in the registry.
     */
    private void validateFieldReference(String fieldId, int line, int column, ValidationResult result) {
        if (fieldId == null || fieldId.isEmpty()) return;

        // Skip if it looks like a function result (e.g., "COUNT(grid)")
        if (fieldId.contains("(")) return;

        FieldDefinition field = fieldRegistry.getField(scopeCode, fieldId);

        if (field == null) {
            result.addWarning(line, column,
                "Unknown field: '" + fieldId + "'. Field may not exist in scope '" + scopeCode + "'");
        }
    }

    /**
     * Validate a function call.
     */
    private void validateFunctionCall(Condition condition, ValidationResult result) {
        String functionArg = condition.getFunctionArg();

        if (functionArg == null || functionArg.isEmpty()) {
            result.addError(condition.getLine(), condition.getColumn(),
                "Function " + condition.getFunction() + " requires a field argument", "ERROR");
            return;
        }

        // For aggregation functions, the argument should be a grid or grid.field
        String baseField = functionArg.contains(".")
            ? functionArg.substring(0, functionArg.indexOf('.'))
            : functionArg;

        FieldDefinition field = fieldRegistry.getField(scopeCode, baseField);

        if (field == null) {
            result.addWarning(condition.getLine(), condition.getColumn(),
                "Unknown field in function: '" + baseField + "'");
            return;
        }

        // For COUNT, SUM, AVG, MIN, MAX - base field should be a grid
        switch (condition.getFunction()) {
            case COUNT:
            case SUM:
            case AVG:
            case MIN:
            case MAX:
                if (!field.isGrid() && !isGridChildField(functionArg)) {
                    result.addWarning(condition.getLine(), condition.getColumn(),
                        "Aggregation function " + condition.getFunction() +
                        " typically used with grid fields. '" + baseField + "' may not be a grid.");
                }
                break;

            case HAS_ANY:
            case HAS_ALL:
            case HAS_NONE:
                // These require grid.field notation
                if (!functionArg.contains(".")) {
                    result.addWarning(condition.getLine(), condition.getColumn(),
                        "Grid check function " + condition.getFunction() +
                        " requires grid.field notation (e.g., 'householdMembers.disability')");
                }

                // Check the full field path exists
                if (functionArg.contains(".")) {
                    FieldDefinition fullField = fieldRegistry.getField(scopeCode, functionArg);
                    if (fullField == null) {
                        result.addWarning(condition.getLine(), condition.getColumn(),
                            "Unknown grid field: '" + functionArg + "'");
                    }
                }
                break;
        }
    }

    /**
     * Check if a field reference is a grid child field (has dot notation).
     */
    private boolean isGridChildField(String fieldId) {
        return fieldId != null && fieldId.contains(".");
    }

    /**
     * Validate all rules in a parsed result.
     */
    public static void validateAll(List<Rule> rules, FieldRegistryService fieldRegistry,
                                   String scopeCode, ValidationResult result) {
        RuleScriptValidator validator = new RuleScriptValidator(fieldRegistry, scopeCode);

        Set<String> ruleNames = new HashSet<>();
        Set<String> ruleCodes = new HashSet<>();

        for (Rule rule : rules) {
            // Check for duplicate names
            if (rule.getName() != null) {
                if (ruleNames.contains(rule.getName().toLowerCase())) {
                    result.addWarning(rule.getStartLine(), 1,
                        "Duplicate rule name: '" + rule.getName() + "'");
                } else {
                    ruleNames.add(rule.getName().toLowerCase());
                }
            }

            // Check for duplicate codes
            if (rule.getCode() != null) {
                if (ruleCodes.contains(rule.getCode())) {
                    result.addWarning(rule.getStartLine(), 1,
                        "Duplicate rule code: '" + rule.getCode() + "'");
                } else {
                    ruleCodes.add(rule.getCode());
                }
            }

            // Validate rule
            validator.validateRule(rule, result);
        }
    }
}
