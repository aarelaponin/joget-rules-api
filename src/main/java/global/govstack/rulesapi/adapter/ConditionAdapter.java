package global.govstack.rulesapi.adapter;

import global.govstack.rules.grammar.model.ComparisonOperator;
import global.govstack.rules.grammar.model.Condition.AggregationFunction;
import global.govstack.rules.grammar.model.Condition.GridCheckFunction;
import global.govstack.rules.grammar.model.Value;
import global.govstack.rulesapi.model.Condition;
import global.govstack.rulesapi.model.Condition.FunctionType;
import global.govstack.rulesapi.model.Condition.Operator;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Adapts rules-grammar Condition sealed interface to the old Condition class.
 */
public final class ConditionAdapter {

    private ConditionAdapter() {
        // Utility class
    }

    /**
     * Converts a rules-grammar Condition to the old Condition model.
     *
     * @param newCondition the new model Condition
     * @return the old model Condition
     */
    public static Condition toOldModel(global.govstack.rules.grammar.model.Condition newCondition) {
        if (newCondition == null) {
            return null;
        }

        if (newCondition instanceof global.govstack.rules.grammar.model.Condition.And) {
            return convertAnd((global.govstack.rules.grammar.model.Condition.And) newCondition);
        } else if (newCondition instanceof global.govstack.rules.grammar.model.Condition.Or) {
            return convertOr((global.govstack.rules.grammar.model.Condition.Or) newCondition);
        } else if (newCondition instanceof global.govstack.rules.grammar.model.Condition.Not) {
            return convertNot((global.govstack.rules.grammar.model.Condition.Not) newCondition);
        } else if (newCondition instanceof global.govstack.rules.grammar.model.Condition.SimpleComparison) {
            return convertSimpleComparison((global.govstack.rules.grammar.model.Condition.SimpleComparison) newCondition);
        } else if (newCondition instanceof global.govstack.rules.grammar.model.Condition.IsEmpty) {
            global.govstack.rules.grammar.model.Condition.IsEmpty ie =
                (global.govstack.rules.grammar.model.Condition.IsEmpty) newCondition;
            return Condition.isEmpty(ie.field().toString());
        } else if (newCondition instanceof global.govstack.rules.grammar.model.Condition.IsNotEmpty) {
            global.govstack.rules.grammar.model.Condition.IsNotEmpty ine =
                (global.govstack.rules.grammar.model.Condition.IsNotEmpty) newCondition;
            return Condition.isNotEmpty(ine.field().toString());
        } else if (newCondition instanceof global.govstack.rules.grammar.model.Condition.Between) {
            return convertBetween((global.govstack.rules.grammar.model.Condition.Between) newCondition);
        } else if (newCondition instanceof global.govstack.rules.grammar.model.Condition.In) {
            return convertIn((global.govstack.rules.grammar.model.Condition.In) newCondition);
        } else if (newCondition instanceof global.govstack.rules.grammar.model.Condition.NotIn) {
            return convertNotIn((global.govstack.rules.grammar.model.Condition.NotIn) newCondition);
        } else if (newCondition instanceof global.govstack.rules.grammar.model.Condition.Aggregation) {
            return convertAggregation((global.govstack.rules.grammar.model.Condition.Aggregation) newCondition);
        } else if (newCondition instanceof global.govstack.rules.grammar.model.Condition.GridCheck) {
            return convertGridCheck((global.govstack.rules.grammar.model.Condition.GridCheck) newCondition);
        }

        throw new IllegalArgumentException("Unknown Condition type: " + newCondition.getClass().getName());
    }

    private static Condition convertAnd(global.govstack.rules.grammar.model.Condition.And and) {
        List<global.govstack.rules.grammar.model.Condition> operands = and.operands();
        Condition result = toOldModel(operands.get(0));
        for (int i = 1; i < operands.size(); i++) {
            result = Condition.and(result, toOldModel(operands.get(i)));
        }
        return result;
    }

    private static Condition convertOr(global.govstack.rules.grammar.model.Condition.Or or) {
        List<global.govstack.rules.grammar.model.Condition> operands = or.operands();
        Condition result = toOldModel(operands.get(0));
        for (int i = 1; i < operands.size(); i++) {
            result = Condition.or(result, toOldModel(operands.get(i)));
        }
        return result;
    }

    private static Condition convertNot(global.govstack.rules.grammar.model.Condition.Not not) {
        return Condition.not(toOldModel(not.operand()));
    }

    private static Condition convertSimpleComparison(global.govstack.rules.grammar.model.Condition.SimpleComparison comp) {
        String fieldId = comp.field().toString();
        Operator op = toOldOperator(comp.operator());
        Object value = ValueAdapter.toObject(comp.value());
        return Condition.comparison(fieldId, op, value);
    }

    private static Condition convertBetween(global.govstack.rules.grammar.model.Condition.Between between) {
        String fieldId = between.field().toString();
        Object min = ValueAdapter.toObject(between.low());
        Object max = ValueAdapter.toObject(between.high());
        return Condition.between(fieldId, min, max);
    }

    private static Condition convertIn(global.govstack.rules.grammar.model.Condition.In in) {
        String fieldId = in.field().toString();
        List<Object> values = in.values().stream()
                .map(ValueAdapter::toObject)
                .collect(Collectors.toList());
        return Condition.in(fieldId, values);
    }

    private static Condition convertNotIn(global.govstack.rules.grammar.model.Condition.NotIn notIn) {
        String fieldId = notIn.field().toString();
        List<Object> values = notIn.values().stream()
                .map(ValueAdapter::toObject)
                .collect(Collectors.toList());
        Condition inCond = Condition.in(fieldId, values);
        return Condition.not(inCond);
    }

    private static Condition convertAggregation(global.govstack.rules.grammar.model.Condition.Aggregation agg) {
        FunctionType funcType = toOldAggregationFunction(agg.function());
        String fieldId = agg.field().toString();

        Condition funcCond = Condition.functionCall(funcType, fieldId);

        if (agg.hasComparison()) {
            funcCond.setFunctionOperator(toOldOperator(agg.operator()));
            funcCond.setFunctionCompareValue(ValueAdapter.toObject(agg.value()));
        }

        return funcCond;
    }

    private static Condition convertGridCheck(global.govstack.rules.grammar.model.Condition.GridCheck gc) {
        FunctionType funcType = toOldGridCheckFunction(gc.function());
        String fieldId = gc.field().toString();

        Condition funcCond = Condition.functionCall(funcType, fieldId);

        if (gc.hasValues()) {
            List<Object> values = gc.values().stream()
                    .map(ValueAdapter::toObject)
                    .collect(Collectors.toList());
            funcCond.setFunctionValues(values);
        }

        return funcCond;
    }

    private static Operator toOldOperator(ComparisonOperator op) {
        if (op == null) {
            return null;
        }
        switch (op) {
            case EQ: return Operator.EQ;
            case NEQ: return Operator.NEQ;
            case GT: return Operator.GT;
            case GTE: return Operator.GTE;
            case LT: return Operator.LT;
            case LTE: return Operator.LTE;
            case CONTAINS: return Operator.CONTAINS;
            case STARTS_WITH: return Operator.STARTS_WITH;
            case ENDS_WITH: return Operator.ENDS_WITH;
            default:
                throw new IllegalArgumentException("Unknown operator: " + op);
        }
    }

    private static FunctionType toOldAggregationFunction(AggregationFunction func) {
        switch (func) {
            case COUNT: return FunctionType.COUNT;
            case SUM: return FunctionType.SUM;
            case AVG: return FunctionType.AVG;
            case MIN: return FunctionType.MIN;
            case MAX: return FunctionType.MAX;
            default:
                throw new IllegalArgumentException("Unknown aggregation function: " + func);
        }
    }

    private static FunctionType toOldGridCheckFunction(GridCheckFunction func) {
        switch (func) {
            case HAS_ANY: return FunctionType.HAS_ANY;
            case HAS_ALL: return FunctionType.HAS_ALL;
            case HAS_NONE: return FunctionType.HAS_NONE;
            default:
                throw new IllegalArgumentException("Unknown grid check function: " + func);
        }
    }
}
