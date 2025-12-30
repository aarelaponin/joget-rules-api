package global.govstack.rulesapi.adapter;

import global.govstack.rulesapi.model.Rule;
import global.govstack.rulesapi.model.Rule.RuleType;

/**
 * Adapts rules-grammar Rule record to the old Rule class.
 */
public final class RuleAdapter {

    private RuleAdapter() {
        // Utility class
    }

    /**
     * Converts a rules-grammar Rule to the old Rule model.
     *
     * @param newRule the new model Rule
     * @return the old model Rule
     */
    public static Rule toOldModel(global.govstack.rules.grammar.model.Rule newRule) {
        if (newRule == null) {
            return null;
        }

        Rule oldRule = new Rule();

        oldRule.setName(newRule.name());

        if (newRule.type() != null) {
            oldRule.setType(toOldRuleType(newRule.type()));
        }

        oldRule.setCategory(newRule.category());
        oldRule.setMandatory(newRule.isMandatory());
        oldRule.setOrder(newRule.orderOrDefault());

        if (newRule.condition() != null) {
            oldRule.setCondition(ConditionAdapter.toOldModel(newRule.condition()));
        }

        if (newRule.score() != null) {
            oldRule.setScore(newRule.score().intValue());
        }

        if (newRule.weight() != null) {
            oldRule.setWeight(newRule.weight().intValue());
        }

        oldRule.setPassMessage(newRule.passMessage());
        oldRule.setFailMessage(newRule.failMessage());

        return oldRule;
    }

    private static RuleType toOldRuleType(global.govstack.rules.grammar.model.RuleType newType) {
        switch (newType) {
            case INCLUSION: return RuleType.INCLUSION;
            case EXCLUSION: return RuleType.EXCLUSION;
            case PRIORITY: return RuleType.PRIORITY;
            case BONUS: return RuleType.BONUS;
            default:
                throw new IllegalArgumentException("Unknown rule type: " + newType);
        }
    }
}
