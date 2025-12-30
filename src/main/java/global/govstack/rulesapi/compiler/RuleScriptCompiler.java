package global.govstack.rulesapi.compiler;

import global.govstack.rulesapi.model.Condition;
import global.govstack.rulesapi.model.Rule;
import global.govstack.rulesapi.compiler.CompiledRuleset.CompiledRule;
import global.govstack.rulesapi.compiler.FieldMapping.FieldInfo;
import global.govstack.rulesapi.compiler.FieldMapping.GridInfo;

import java.util.*;

/**
 * Compiles rules to SQL queries.
 *
 * Converts parsed Rule objects into efficient SQL that can be executed
 * against the database to determine eligibility and calculate scores.
 */
public class RuleScriptCompiler {

    private final FieldMapping fieldMapping;
    private CompiledRuleset output;

    // Counters for statistics
    private int inclusionCount = 0;
    private int exclusionCount = 0;
    private int bonusCount = 0;

    public RuleScriptCompiler(FieldMapping fieldMapping) {
        this.fieldMapping = fieldMapping;
    }

    /**
     * Compile a list of parsed rules into a CompiledRuleset.
     */
    public CompiledRuleset compile(List<Rule> rules, String rulesetCode, String scopeCode) {
        output = new CompiledRuleset();
        output.setRulesetCode(rulesetCode);
        output.setScopeCode(scopeCode);
        output.setMainTable(fieldMapping.getMainTableName(), fieldMapping.getMainTableAlias());
        output.setTotalRules(rules.size());

        // Reset counters
        inclusionCount = 0;
        exclusionCount = 0;
        bonusCount = 0;

        // Collect WHERE clauses by type
        List<String> inclusionClauses = new ArrayList<>();
        List<String> exclusionClauses = new ArrayList<>();

        // Compile each rule
        for (Rule rule : rules) {
            CompiledRule compiled = compileRule(rule);
            output.addCompiledRule(compiled);

            // Collect by type
            String whereClause = compiled.getWhereClause();
            if (whereClause != null && !whereClause.isEmpty()) {
                switch (rule.getType()) {
                    case INCLUSION:
                        if (rule.isMandatory()) {
                            inclusionClauses.add("(" + whereClause + ")");
                        }
                        inclusionCount++;
                        break;
                    case EXCLUSION:
                        exclusionClauses.add("(" + whereClause + ")");
                        exclusionCount++;
                        break;
                    case PRIORITY:
                    case BONUS:
                        bonusCount++;
                        // Scoring handled via SELECT expressions
                        break;
                }
            }

            // Add scoring SELECT if applicable
            if (compiled.getSelectExpression() != null) {
                output.addScoringSelectClause(compiled.getSelectExpression());
            }

            // Collect required JOINs
            for (String join : compiled.getRequiredJoins()) {
                output.addRequiredJoin(join);
            }
        }

        // Combine eligibility clauses (AND)
        if (!inclusionClauses.isEmpty()) {
            output.setEligibilityWhereClause(String.join("\n    AND ", inclusionClauses));
        }

        // Combine exclusion clauses (OR - any match excludes)
        if (!exclusionClauses.isEmpty()) {
            output.setExclusionWhereClause(String.join("\n    OR ", exclusionClauses));
        }

        // Set statistics
        output.setInclusionRules(inclusionCount);
        output.setExclusionRules(exclusionCount);
        output.setBonusRules(bonusCount);

        // Generate query templates
        output.generateEligibilityCheckQuery();
        output.generateScoringQuery();
        output.generateFullEligibilityQuery();

        return output;
    }

    /**
     * Compile a single rule.
     */
    private CompiledRule compileRule(Rule rule) {
        CompiledRule compiled = new CompiledRule(
            rule.getName(),
            rule.getCode(),
            rule.getType().name()
        );
        compiled.setMandatory(rule.isMandatory());
        compiled.setScore(rule.getScore());

        // Compile condition to WHERE clause
        if (rule.getCondition() != null) {
            String whereClause = compileCondition(rule.getCondition(), compiled);
            compiled.setWhereClause(whereClause);

            // Generate SELECT expression for rule result
            String safeCode = rule.getCode().toLowerCase().replace("-", "_");
            String selectExpr;

            if (rule.getScore() != null && rule.getScore() != 0) {
                // Scoring rule: return score if condition met
                selectExpr = String.format(
                    "CASE WHEN %s THEN %d ELSE 0 END AS score_%s",
                    whereClause, rule.getScore(), safeCode
                );
            } else {
                // Boolean rule: return 1/0
                selectExpr = String.format(
                    "CASE WHEN %s THEN 1 ELSE 0 END AS rule_%s",
                    whereClause, safeCode
                );
            }
            compiled.setSelectExpression(selectExpr);
        }

        return compiled;
    }

    /**
     * Compile a condition tree to SQL WHERE clause.
     */
    private String compileCondition(Condition condition, CompiledRule compiled) {
        if (condition == null) {
            return "1=1";
        }

        switch (condition.getType()) {
            case COMPARISON:
                return compileComparison(condition, compiled);

            case AND:
                String left = compileCondition(condition.getLeft(), compiled);
                String right = compileCondition(condition.getRight(), compiled);
                return "(" + left + " AND " + right + ")";

            case OR:
                left = compileCondition(condition.getLeft(), compiled);
                right = compileCondition(condition.getRight(), compiled);
                return "(" + left + " OR " + right + ")";

            case NOT:
                String inner = compileCondition(condition.getInner(), compiled);
                return "NOT (" + inner + ")";

            case GROUP:
                return compileCondition(condition.getInner(), compiled);

            case BETWEEN:
                return compileBetween(condition, compiled);

            case IN:
                return compileIn(condition, compiled);

            case IS_EMPTY:
                return compileIsEmpty(condition, compiled, true);

            case IS_NOT_EMPTY:
                return compileIsEmpty(condition, compiled, false);

            case FUNCTION_CALL:
                return compileFunctionCall(condition, compiled);

            default:
                output.addWarning("Unknown condition type: " + condition.getType());
                return "1=1";
        }
    }

    /**
     * Compile a simple comparison: field op value
     */
    private String compileComparison(Condition condition, CompiledRule compiled) {
        String fieldId = condition.getFieldId();
        compiled.addUsedField(fieldId);

        String sqlRef = fieldMapping.getSqlReference(fieldId);
        String sqlOp = operatorToSql(condition.getOperator());
        String sqlValue = valueToSql(condition.getValue());

        return sqlRef + " " + sqlOp + " " + sqlValue;
    }

    /**
     * Compile BETWEEN: field BETWEEN min AND max
     */
    private String compileBetween(Condition condition, CompiledRule compiled) {
        String fieldId = condition.getFieldId();
        compiled.addUsedField(fieldId);

        String sqlRef = fieldMapping.getSqlReference(fieldId);
        String minVal = valueToSql(condition.getMinValue());
        String maxVal = valueToSql(condition.getMaxValue());

        return sqlRef + " BETWEEN " + minVal + " AND " + maxVal;
    }

    /**
     * Compile IN: field IN (value1, value2, ...)
     */
    private String compileIn(Condition condition, CompiledRule compiled) {
        String fieldId = condition.getFieldId();
        compiled.addUsedField(fieldId);

        String sqlRef = fieldMapping.getSqlReference(fieldId);

        List<String> sqlValues = new ArrayList<>();
        for (Object val : condition.getInValues()) {
            sqlValues.add(valueToSql(val));
        }

        return sqlRef + " IN (" + String.join(", ", sqlValues) + ")";
    }

    /**
     * Compile IS EMPTY / IS NOT EMPTY
     */
    private String compileIsEmpty(Condition condition, CompiledRule compiled, boolean isEmpty) {
        String fieldId = condition.getFieldId();
        compiled.addUsedField(fieldId);

        String sqlRef = fieldMapping.getSqlReference(fieldId);

        if (isEmpty) {
            return "(" + sqlRef + " IS NULL OR " + sqlRef + " = '')";
        } else {
            return "(" + sqlRef + " IS NOT NULL AND " + sqlRef + " != '')";
        }
    }

    /**
     * Compile function calls: COUNT(field) >= 3, HAS_ANY(field, value), etc.
     */
    private String compileFunctionCall(Condition condition, CompiledRule compiled) {
        String functionArg = condition.getFunctionArg();
        Condition.FunctionType funcType = condition.getFunction();

        String functionSql;

        switch (funcType) {
            case COUNT:
                functionSql = compileCount(functionArg, compiled);
                break;

            case SUM:
                functionSql = compileAggregation("SUM", functionArg, compiled);
                break;

            case AVG:
                functionSql = compileAggregation("AVG", functionArg, compiled);
                break;

            case MIN:
                functionSql = compileAggregation("MIN", functionArg, compiled);
                break;

            case MAX:
                functionSql = compileAggregation("MAX", functionArg, compiled);
                break;

            case HAS_ANY:
                return compileHasAny(functionArg, condition.getFunctionValues(), compiled);

            case HAS_ALL:
                return compileHasAll(functionArg, condition.getFunctionValues(), compiled);

            case HAS_NONE:
                return compileHasNone(functionArg, condition.getFunctionValues(), compiled);

            default:
                output.addWarning("Unknown function: " + funcType);
                return "1=1";
        }

        // For aggregation functions with comparison (COUNT(x) >= 3)
        if (condition.hasFunctionComparison()) {
            String sqlOp = operatorToSql(condition.getFunctionOperator());
            String sqlValue = valueToSql(condition.getFunctionCompareValue());
            return functionSql + " " + sqlOp + " " + sqlValue;
        }

        // Without comparison, just return the function (for use in expressions)
        return functionSql;
    }

    /**
     * Compile COUNT(grid) - returns the count as subquery
     */
    private String compileCount(String gridFieldId, CompiledRule compiled) {
        // Extract base grid name
        String gridName = gridFieldId.contains(".")
            ? gridFieldId.substring(0, gridFieldId.indexOf('.'))
            : gridFieldId;

        GridInfo grid = fieldMapping.getGrid(gridName);
        if (grid == null) {
            output.addWarning("Unknown grid: " + gridName);
            return "0";
        }

        compiled.addUsedField(gridFieldId);

        // Generate subquery
        return String.format(
            "(SELECT COUNT(*) FROM %s %s WHERE %s)",
            grid.getTableName(),
            grid.getTableAlias(),
            grid.getCorrelation()
        );
    }

    /**
     * Compile SUM/AVG/MIN/MAX(grid.field)
     */
    private String compileAggregation(String aggFunc, String fieldPath, CompiledRule compiled) {
        if (!fieldPath.contains(".")) {
            output.addWarning("Aggregation requires grid.field notation: " + fieldPath);
            return "0";
        }

        String gridName = fieldPath.substring(0, fieldPath.indexOf('.'));
        String fieldName = fieldPath.substring(fieldPath.indexOf('.') + 1);

        GridInfo grid = fieldMapping.getGrid(gridName);
        if (grid == null) {
            output.addWarning("Unknown grid: " + gridName);
            return "0";
        }

        compiled.addUsedField(fieldPath);

        return String.format(
            "(SELECT %s(%s.c_%s) FROM %s %s WHERE %s)",
            aggFunc,
            grid.getTableAlias(),
            fieldName,
            grid.getTableName(),
            grid.getTableAlias(),
            grid.getCorrelation()
        );
    }

    /**
     * Compile HAS_ANY(grid.field, value1, value2, ...) - EXISTS with OR
     */
    private String compileHasAny(String fieldPath, List<Object> values, CompiledRule compiled) {
        if (!fieldPath.contains(".")) {
            output.addWarning("HAS_ANY requires grid.field notation: " + fieldPath);
            return "1=0";
        }

        String gridName = fieldPath.substring(0, fieldPath.indexOf('.'));
        String fieldName = fieldPath.substring(fieldPath.indexOf('.') + 1);

        GridInfo grid = fieldMapping.getGrid(gridName);
        if (grid == null) {
            output.addWarning("Unknown grid: " + gridName);
            return "1=0";
        }

        compiled.addUsedField(fieldPath);

        // Build value list
        List<String> sqlValues = new ArrayList<>();
        for (Object val : values) {
            sqlValues.add(valueToSql(val));
        }

        return String.format(
            "EXISTS (SELECT 1 FROM %s %s WHERE %s AND %s.c_%s IN (%s))",
            grid.getTableName(),
            grid.getTableAlias(),
            grid.getCorrelation(),
            grid.getTableAlias(),
            fieldName,
            String.join(", ", sqlValues)
        );
    }

    /**
     * Compile HAS_ALL(grid.field, value1, value2, ...) - multiple EXISTS
     */
    private String compileHasAll(String fieldPath, List<Object> values, CompiledRule compiled) {
        if (!fieldPath.contains(".")) {
            output.addWarning("HAS_ALL requires grid.field notation: " + fieldPath);
            return "1=0";
        }

        String gridName = fieldPath.substring(0, fieldPath.indexOf('.'));
        String fieldName = fieldPath.substring(fieldPath.indexOf('.') + 1);

        GridInfo grid = fieldMapping.getGrid(gridName);
        if (grid == null) {
            output.addWarning("Unknown grid: " + gridName);
            return "1=0";
        }

        compiled.addUsedField(fieldPath);

        // Each value needs its own EXISTS
        List<String> existsClauses = new ArrayList<>();
        for (Object val : values) {
            String sqlVal = valueToSql(val);
            existsClauses.add(String.format(
                "EXISTS (SELECT 1 FROM %s %s WHERE %s AND %s.c_%s = %s)",
                grid.getTableName(),
                grid.getTableAlias(),
                grid.getCorrelation(),
                grid.getTableAlias(),
                fieldName,
                sqlVal
            ));
        }

        return "(" + String.join(" AND ", existsClauses) + ")";
    }

    /**
     * Compile HAS_NONE(grid.field, value1, value2, ...) - NOT EXISTS
     */
    private String compileHasNone(String fieldPath, List<Object> values, CompiledRule compiled) {
        // HAS_NONE is just NOT HAS_ANY
        String hasAny = compileHasAny(fieldPath, values, compiled);
        return "NOT " + hasAny;
    }

    // === Helper Methods ===

    /**
     * Convert operator to SQL operator.
     */
    private String operatorToSql(Condition.Operator op) {
        switch (op) {
            case EQ: return "=";
            case NEQ: return "!=";
            case GT: return ">";
            case GTE: return ">=";
            case LT: return "<";
            case LTE: return "<=";
            case CONTAINS: return "LIKE";
            case STARTS_WITH: return "LIKE";
            case ENDS_WITH: return "LIKE";
            default: return "=";
        }
    }

    /**
     * Convert a value to SQL literal.
     */
    private String valueToSql(Object value) {
        if (value == null) {
            return "NULL";
        }

        if (value instanceof Number) {
            return value.toString();
        }

        if (value instanceof Boolean) {
            return ((Boolean) value) ? "'true'" : "'false'";
        }

        // String - escape single quotes
        String str = value.toString();
        str = str.replace("'", "''");
        return "'" + str + "'";
    }
}
