package global.govstack.rulesapi.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a parsed condition.
 *
 * Conditions can be:
 * 1. Simple comparison: field operator value (e.g., age >= 18)
 * 2. Compound: condition AND/OR condition
 * 3. Negation: NOT condition
 * 4. Grouped: (condition)
 * 5. Function call: COUNT(grid) > 0, HAS_ANY(grid.field, "value")
 *
 * Examples:
 * - age >= 18
 * - gender = "female" AND age >= 18
 * - (age >= 18 OR hasExemption = true) AND isActive = true
 * - COUNT(householdMembers) >= 1
 * - HAS_ANY(householdMembers.disability, "yes")
 * - district IN ("berea", "maseru", "leribe")
 * - age BETWEEN 18 AND 65
 */
public class Condition {

    /**
     * Type of condition node
     */
    public enum ConditionType {
        COMPARISON,      // field op value
        AND,             // left AND right
        OR,              // left OR right
        NOT,             // NOT condition
        GROUP,           // (condition)
        FUNCTION_CALL,   // COUNT(...), HAS_ANY(...), etc.
        BETWEEN,         // field BETWEEN min AND max
        IN,              // field IN (value1, value2, ...)
        IS_EMPTY,        // field IS EMPTY
        IS_NOT_EMPTY     // field IS NOT EMPTY
    }

    /**
     * Comparison operators
     */
    public enum Operator {
        EQ("="),
        NEQ("!="),
        GT(">"),
        GTE(">="),
        LT("<"),
        LTE("<="),
        CONTAINS("CONTAINS"),
        STARTS_WITH("STARTS WITH"),
        ENDS_WITH("ENDS WITH");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() {
            return symbol;
        }
    }

    /**
     * Aggregation/check functions
     */
    public enum FunctionType {
        COUNT,
        SUM,
        AVG,
        MIN,
        MAX,
        HAS_ANY,
        HAS_ALL,
        HAS_NONE
    }

    private ConditionType type;

    // For COMPARISON
    private String fieldId;
    private Operator operator;
    private Object value;

    // For AND, OR
    private Condition left;
    private Condition right;

    // For NOT, GROUP
    private Condition inner;

    // For FUNCTION_CALL with comparison (e.g., COUNT(x) >= 3)
    private FunctionType function;
    private String functionArg;      // e.g., "householdMembers" or "householdMembers.disability"
    private List<Object> functionValues = new ArrayList<>();  // for HAS_ANY, HAS_ALL, HAS_NONE
    private Operator functionOperator;  // for COUNT(x) >= 3
    private Object functionCompareValue; // for COUNT(x) >= 3

    // For BETWEEN
    private Object minValue;
    private Object maxValue;

    // For IN
    private List<Object> inValues = new ArrayList<>();

    // Source location
    private int line;
    private int column;

    // === Factory methods ===

    public static Condition comparison(String fieldId, Operator operator, Object value) {
        Condition c = new Condition();
        c.type = ConditionType.COMPARISON;
        c.fieldId = fieldId;
        c.operator = operator;
        c.value = value;
        return c;
    }

    public static Condition and(Condition left, Condition right) {
        Condition c = new Condition();
        c.type = ConditionType.AND;
        c.left = left;
        c.right = right;
        return c;
    }

    public static Condition or(Condition left, Condition right) {
        Condition c = new Condition();
        c.type = ConditionType.OR;
        c.left = left;
        c.right = right;
        return c;
    }

    public static Condition not(Condition inner) {
        Condition c = new Condition();
        c.type = ConditionType.NOT;
        c.inner = inner;
        return c;
    }

    public static Condition group(Condition inner) {
        Condition c = new Condition();
        c.type = ConditionType.GROUP;
        c.inner = inner;
        return c;
    }

    public static Condition functionCall(FunctionType function, String arg) {
        Condition c = new Condition();
        c.type = ConditionType.FUNCTION_CALL;
        c.function = function;
        c.functionArg = arg;
        return c;
    }

    public static Condition between(String fieldId, Object min, Object max) {
        Condition c = new Condition();
        c.type = ConditionType.BETWEEN;
        c.fieldId = fieldId;
        c.minValue = min;
        c.maxValue = max;
        return c;
    }

    public static Condition in(String fieldId, List<Object> values) {
        Condition c = new Condition();
        c.type = ConditionType.IN;
        c.fieldId = fieldId;
        c.inValues = new ArrayList<>(values);
        return c;
    }

    public static Condition isEmpty(String fieldId) {
        Condition c = new Condition();
        c.type = ConditionType.IS_EMPTY;
        c.fieldId = fieldId;
        return c;
    }

    public static Condition isNotEmpty(String fieldId) {
        Condition c = new Condition();
        c.type = ConditionType.IS_NOT_EMPTY;
        c.fieldId = fieldId;
        return c;
    }

    // === Getters and Setters ===

    public ConditionType getType() {
        return type;
    }

    public void setType(ConditionType type) {
        this.type = type;
    }

    public String getFieldId() {
        return fieldId;
    }

    public void setFieldId(String fieldId) {
        this.fieldId = fieldId;
    }

    public Operator getOperator() {
        return operator;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Condition getLeft() {
        return left;
    }

    public void setLeft(Condition left) {
        this.left = left;
    }

    public Condition getRight() {
        return right;
    }

    public void setRight(Condition right) {
        this.right = right;
    }

    public Condition getInner() {
        return inner;
    }

    public void setInner(Condition inner) {
        this.inner = inner;
    }

    public FunctionType getFunction() {
        return function;
    }

    public void setFunction(FunctionType function) {
        this.function = function;
    }

    public String getFunctionArg() {
        return functionArg;
    }

    public void setFunctionArg(String functionArg) {
        this.functionArg = functionArg;
    }

    public List<Object> getFunctionValues() {
        return functionValues;
    }

    public void setFunctionValues(List<Object> functionValues) {
        this.functionValues = functionValues;
    }

    public Operator getFunctionOperator() {
        return functionOperator;
    }

    public void setFunctionOperator(Operator functionOperator) {
        this.functionOperator = functionOperator;
    }

    public Object getFunctionCompareValue() {
        return functionCompareValue;
    }

    public void setFunctionCompareValue(Object functionCompareValue) {
        this.functionCompareValue = functionCompareValue;
    }

    /**
     * Check if this is a function with comparison (e.g., COUNT(x) >= 3)
     */
    public boolean hasFunctionComparison() {
        return type == ConditionType.FUNCTION_CALL && functionOperator != null;
    }

    public Object getMinValue() {
        return minValue;
    }

    public void setMinValue(Object minValue) {
        this.minValue = minValue;
    }

    public Object getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(Object maxValue) {
        this.maxValue = maxValue;
    }

    public List<Object> getInValues() {
        return inValues;
    }

    public void setInValues(List<Object> inValues) {
        this.inValues = inValues;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    @Override
    public String toString() {
        switch (type) {
            case COMPARISON:
                return String.format("%s %s %s", fieldId, operator.getSymbol(), value);
            case AND:
                return String.format("(%s AND %s)", left, right);
            case OR:
                return String.format("(%s OR %s)", left, right);
            case NOT:
                return String.format("NOT %s", inner);
            case GROUP:
                return String.format("(%s)", inner);
            case FUNCTION_CALL:
                if (functionValues.isEmpty()) {
                    return String.format("%s(%s)", function, functionArg);
                } else {
                    return String.format("%s(%s, %s)", function, functionArg, functionValues);
                }
            case BETWEEN:
                return String.format("%s BETWEEN %s AND %s", fieldId, minValue, maxValue);
            case IN:
                return String.format("%s IN %s", fieldId, inValues);
            case IS_EMPTY:
                return String.format("%s IS EMPTY", fieldId);
            case IS_NOT_EMPTY:
                return String.format("%s IS NOT EMPTY", fieldId);
            default:
                return "UNKNOWN";
        }
    }
}
