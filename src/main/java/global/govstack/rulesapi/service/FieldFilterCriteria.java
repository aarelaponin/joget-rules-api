package global.govstack.rulesapi.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Criteria for filtering field definitions.
 *
 * Supports filtering by:
 * - scopeCode (required)
 * - categories (optional, list)
 * - fieldTypes (optional, list)
 * - isGrid (optional, "Y" or "N")
 * - lookupFormId (optional)
 *
 * Uses builder pattern for fluent construction.
 * Implements equals/hashCode for cache key generation.
 */
public class FieldFilterCriteria {

    private final String scopeCode;
    private final List<String> categories;
    private final List<String> fieldTypes;
    private final String isGrid;
    private final String lookupFormId;

    private FieldFilterCriteria(Builder builder) {
        this.scopeCode = builder.scopeCode;
        this.categories = builder.categories != null
            ? Collections.unmodifiableList(new ArrayList<>(builder.categories))
            : Collections.emptyList();
        this.fieldTypes = builder.fieldTypes != null
            ? Collections.unmodifiableList(new ArrayList<>(builder.fieldTypes))
            : Collections.emptyList();
        this.isGrid = builder.isGrid;
        this.lookupFormId = builder.lookupFormId;
    }

    public String getScopeCode() {
        return scopeCode;
    }

    public List<String> getCategories() {
        return categories;
    }

    public List<String> getFieldTypes() {
        return fieldTypes;
    }

    public String getIsGrid() {
        return isGrid;
    }

    public String getLookupFormId() {
        return lookupFormId;
    }

    /**
     * Check if any filters (besides scopeCode) are active.
     */
    public boolean hasFilters() {
        return !categories.isEmpty()
            || !fieldTypes.isEmpty()
            || isGrid != null
            || lookupFormId != null;
    }

    /**
     * Generate a cache key string for this criteria.
     */
    public String toCacheKey() {
        StringBuilder sb = new StringBuilder();
        sb.append("scope=").append(scopeCode != null ? scopeCode : "ALL");

        if (!categories.isEmpty()) {
            sb.append("|cat=");
            sb.append(String.join(",", categories));
        }
        if (!fieldTypes.isEmpty()) {
            sb.append("|type=");
            sb.append(String.join(",", fieldTypes));
        }
        if (isGrid != null) {
            sb.append("|grid=").append(isGrid);
        }
        if (lookupFormId != null) {
            sb.append("|lookup=").append(lookupFormId);
        }

        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldFilterCriteria that = (FieldFilterCriteria) o;
        return Objects.equals(scopeCode, that.scopeCode)
            && Objects.equals(categories, that.categories)
            && Objects.equals(fieldTypes, that.fieldTypes)
            && Objects.equals(isGrid, that.isGrid)
            && Objects.equals(lookupFormId, that.lookupFormId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scopeCode, categories, fieldTypes, isGrid, lookupFormId);
    }

    @Override
    public String toString() {
        return "FieldFilterCriteria{" +
            "scopeCode='" + scopeCode + '\'' +
            ", categories=" + categories +
            ", fieldTypes=" + fieldTypes +
            ", isGrid='" + isGrid + '\'' +
            ", lookupFormId='" + lookupFormId + '\'' +
            '}';
    }

    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a builder with scope code.
     */
    public static Builder forScope(String scopeCode) {
        return new Builder().scopeCode(scopeCode);
    }

    /**
     * Builder for FieldFilterCriteria.
     */
    public static class Builder {
        private String scopeCode;
        private List<String> categories;
        private List<String> fieldTypes;
        private String isGrid;
        private String lookupFormId;

        public Builder scopeCode(String scopeCode) {
            this.scopeCode = scopeCode;
            return this;
        }

        public Builder categories(List<String> categories) {
            this.categories = categories;
            return this;
        }

        public Builder fieldTypes(List<String> fieldTypes) {
            this.fieldTypes = fieldTypes;
            return this;
        }

        public Builder isGrid(String isGrid) {
            this.isGrid = isGrid;
            return this;
        }

        public Builder lookupFormId(String lookupFormId) {
            this.lookupFormId = lookupFormId;
            return this;
        }

        public FieldFilterCriteria build() {
            return new FieldFilterCriteria(this);
        }
    }
}
