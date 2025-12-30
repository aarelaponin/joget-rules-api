package global.govstack.rulesapi.adapter;

import global.govstack.rules.grammar.model.Value;

/**
 * Adapts rules-grammar Value sealed interface to plain Object for the old model.
 */
public final class ValueAdapter {

    private ValueAdapter() {
        // Utility class
    }

    /**
     * Converts a rules-grammar Value to a plain Object.
     *
     * @param value the new model Value
     * @return String, Double, Boolean, or String (for identifier)
     */
    public static Object toObject(Value value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Value.StringValue) {
            return ((Value.StringValue) value).value();
        } else if (value instanceof Value.NumberValue) {
            return ((Value.NumberValue) value).value();
        } else if (value instanceof Value.BooleanValue) {
            return ((Value.BooleanValue) value).value();
        } else if (value instanceof Value.IdentifierValue) {
            return ((Value.IdentifierValue) value).name();
        }

        throw new IllegalArgumentException("Unknown Value type: " + value.getClass().getName());
    }
}
