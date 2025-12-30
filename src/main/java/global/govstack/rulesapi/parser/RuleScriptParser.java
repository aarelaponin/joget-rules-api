package global.govstack.rulesapi.parser;

import global.govstack.rules.grammar.ParseError;
import global.govstack.rules.grammar.ParseResult;
import global.govstack.rules.grammar.RulesScript;
import global.govstack.rules.grammar.model.Script;
import global.govstack.rulesapi.adapter.RuleAdapter;
import global.govstack.rulesapi.model.Condition;
import global.govstack.rulesapi.model.Rule;
import global.govstack.rulesapi.model.ValidationResult;

/**
 * Parser for Rules Script.
 *
 * Uses the ANTLR-based rules-grammar library for parsing, then adapts the
 * result to the existing model classes for backwards compatibility.
 *
 * Grammar (simplified):
 * <pre>
 * script     := rule*
 * rule       := RULE STRING ruleBody
 * ruleBody   := (TYPE COLON ruleType)?
 *               (CATEGORY COLON IDENTIFIER)?
 *               (MANDATORY COLON boolValue)?
 *               (ORDER COLON NUMBER)?
 *               (WHEN condition)?
 *               (SCORE COLON NUMBER)?
 *               (PASS_MESSAGE COLON STRING)?
 *               (FAIL_MESSAGE COLON STRING)?
 * ruleType   := INCLUSION | EXCLUSION | PRIORITY | BONUS
 * boolValue  := YES | NO | TRUE | FALSE
 * condition  := orExpr
 * orExpr     := andExpr (OR andExpr)*
 * andExpr    := unaryExpr (AND unaryExpr)*
 * unaryExpr  := NOT? primaryExpr
 * primaryExpr := LPAREN condition RPAREN | comparison | functionExpr
 * comparison := IDENTIFIER operator value
 *             | IDENTIFIER BETWEEN value AND value
 *             | IDENTIFIER IN LPAREN valueList RPAREN
 *             | IDENTIFIER IS_EMPTY
 *             | IDENTIFIER IS_NOT_EMPTY
 * operator   := EQ | NEQ | GT | GTE | LT | LTE | CONTAINS | STARTS_WITH | ENDS_WITH
 * functionExpr := functionName LPAREN IDENTIFIER (COMMA valueList)? RPAREN (operator value)?
 * functionName := COUNT | SUM | AVG | MIN | MAX | HAS_ANY | HAS_ALL | HAS_NONE
 * value      := STRING | NUMBER | boolValue | IDENTIFIER
 * valueList  := value (COMMA value)*
 * </pre>
 */
public class RuleScriptParser {

    public RuleScriptParser() {
        // Default constructor
    }

    /**
     * Parse a Rules Script and return the validation result with parsed rules.
     */
    public ValidationResult parse(String script) {
        ValidationResult result = new ValidationResult();

        if (script == null || script.trim().isEmpty()) {
            result.setValid(true);
            result.setRuleCount(0);
            return result;
        }

        try {
            // Use the new ANTLR parser
            ParseResult parseResult = RulesScript.parse(script);

            // Convert errors
            if (parseResult.hasErrors()) {
                for (ParseError error : parseResult.errors()) {
                    result.addError(error.line(), error.column(), error.message(), "ERROR");
                }
                result.setValid(false);
            }

            // Convert rules using adapters
            if (parseResult.script() != null) {
                Script grammarScript = parseResult.script();
                result.setRuleCount(grammarScript.size());

                for (global.govstack.rules.grammar.model.Rule grammarRule : grammarScript.rules()) {
                    Rule oldRule = RuleAdapter.toOldModel(grammarRule);
                    result.addRule(oldRule);

                    int conditionCount = countConditions(oldRule.getCondition());
                    result.addRuleSummary(
                        oldRule.getName(),
                        oldRule.getCode(),
                        oldRule.getType().name(),
                        conditionCount
                    );
                }

                // If we successfully parsed rules and have no errors, mark as valid
                if (!parseResult.hasErrors()) {
                    result.setValid(true);
                }
            }

        } catch (Exception e) {
            result.addError(1, 1, "Unexpected error: " + e.getMessage(), "ERROR");
            result.setValid(false);
        }

        return result;
    }

    /**
     * Count conditions in a condition tree.
     */
    private int countConditions(Condition cond) {
        if (cond == null) return 0;

        switch (cond.getType()) {
            case AND:
            case OR:
                return countConditions(cond.getLeft()) + countConditions(cond.getRight());
            case NOT:
            case GROUP:
                return countConditions(cond.getInner());
            default:
                return 1;
        }
    }

    /**
     * Parser exception with location information.
     * Kept for backwards compatibility.
     */
    public static class ParseException extends Exception {
        private final int line;
        private final int column;

        public ParseException(String message, int line, int column) {
            super(message);
            this.line = line;
            this.column = column;
        }

        public int getLine() {
            return line;
        }

        public int getColumn() {
            return column;
        }
    }
}
