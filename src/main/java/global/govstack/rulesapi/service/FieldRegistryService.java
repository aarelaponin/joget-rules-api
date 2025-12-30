package global.govstack.rulesapi.service;

import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.util.*;

/**
 * Service for loading field definitions from the ruleFieldDefinition form.
 *
 * Uses Joget's FormDataDao to query field data from the database.
 */
public class FieldRegistryService {

    private static final String CLASS_NAME = FieldRegistryService.class.getName();

    // Form/table names - JRE tables from Phase 4
    private static final String FIELD_DEFINITION_FORM = "jreFieldDefinition";
    private static final String FIELD_DEFINITION_TABLE = "jreFieldDefinition";
    private static final String FIELD_SCOPE_FORM = "jreFieldScope";
    private static final String FIELD_SCOPE_TABLE = "jreFieldScope";

    // Cache for field definitions (cache key -> fields)
    private Map<String, List<FieldDefinition>> fieldCache = new HashMap<>();
    private Map<String, Long> cacheExpiryMap = new HashMap<>();
    private static final long CACHE_TTL = 60000; // 1 minute

    // Form for categories MDM
    private static final String FIELD_CATEGORY_FORM = "md51FieldCategory";
    private static final String FIELD_CATEGORY_TABLE = "md51FieldCategory";

    /**
     * Get all active field definitions for a given scope.
     * Backward-compatible method that delegates to filter-based method.
     *
     * @param scopeCode The scope code (e.g., "FARMER_ELIGIBILITY")
     * @return List of field definitions
     */
    public List<FieldDefinition> getFieldsForScope(String scopeCode) {
        FieldFilterCriteria criteria = FieldFilterCriteria.forScope(scopeCode).build();
        return getFieldsForScope(criteria);
    }

    /**
     * Get field definitions matching the given filter criteria.
     *
     * @param criteria The filter criteria (scope, categories, types, etc.)
     * @return List of field definitions matching the criteria
     */
    public List<FieldDefinition> getFieldsForScope(FieldFilterCriteria criteria) {
        String cacheKey = criteria.toCacheKey();

        // Check cache
        Long expiry = cacheExpiryMap.get(cacheKey);
        if (expiry != null && System.currentTimeMillis() < expiry && fieldCache.containsKey(cacheKey)) {
            return fieldCache.get(cacheKey);
        }

        List<FieldDefinition> fields = loadFieldsFromDatabase(criteria);

        // Update cache
        fieldCache.put(cacheKey, fields);
        cacheExpiryMap.put(cacheKey, System.currentTimeMillis() + CACHE_TTL);

        return fields;
    }

    /**
     * Get all active field definitions across all scopes.
     *
     * @return List of field definitions
     */
    public List<FieldDefinition> getAllFields() {
        FieldFilterCriteria criteria = FieldFilterCriteria.builder().build();
        return loadFieldsFromDatabase(criteria);
    }

    /**
     * Check if a field exists in the given scope.
     *
     * @param scopeCode The scope code
     * @param fieldId The field ID (e.g., "age" or "householdMembers.sex")
     * @return true if field exists and is active
     */
    public boolean fieldExists(String scopeCode, String fieldId) {
        List<FieldDefinition> fields = getFieldsForScope(scopeCode);
        return fields.stream().anyMatch(f -> f.getFieldId().equals(fieldId));
    }

    /**
     * Get a specific field definition.
     *
     * @param scopeCode The scope code
     * @param fieldId The field ID
     * @return FieldDefinition or null if not found
     */
    public FieldDefinition getField(String scopeCode, String fieldId) {
        List<FieldDefinition> fields = getFieldsForScope(scopeCode);
        return fields.stream()
            .filter(f -> f.getFieldId().equals(fieldId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get fields grouped by category.
     *
     * @param scopeCode The scope code
     * @return Map of category -> list of fields
     */
    public Map<String, List<FieldDefinition>> getFieldsByCategory(String scopeCode) {
        FieldFilterCriteria criteria = FieldFilterCriteria.forScope(scopeCode).build();
        return getFieldsByCategory(criteria);
    }

    /**
     * Get fields grouped by category with filter criteria.
     *
     * @param criteria The filter criteria
     * @return Map of category -> list of fields
     */
    public Map<String, List<FieldDefinition>> getFieldsByCategory(FieldFilterCriteria criteria) {
        List<FieldDefinition> fields = getFieldsForScope(criteria);
        Map<String, List<FieldDefinition>> grouped = new LinkedHashMap<>();

        for (FieldDefinition field : fields) {
            String category = field.getCategory();
            grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(field);
        }

        return grouped;
    }

    /**
     * Clear the field cache.
     */
    public void clearCache() {
        fieldCache.clear();
        cacheExpiryMap.clear();
    }

    /**
     * Get all available categories from md51FieldCategory MDM.
     *
     * @return List of category data (code, name)
     */
    public List<CategoryData> getCategories() {
        List<CategoryData> categories = new ArrayList<>();

        try {
            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext()
                .getBean("formDataDao");

            // Note: isActive can be 'Y', 'true', or '1' depending on data source
            String condition = "WHERE (c_isActive = ? OR c_isActive = ? OR c_isActive = ?) ORDER BY c_displayOrder, c_code";
            Object[] params = new Object[]{"Y", "true", "1"};

            FormRowSet rowSet = formDataDao.find(
                FIELD_CATEGORY_FORM,
                FIELD_CATEGORY_TABLE,
                condition,
                params,
                null, null, null, null
            );

            if (rowSet != null) {
                for (FormRow row : rowSet) {
                    String code = getProperty(row, "code");
                    String name = getProperty(row, "name");
                    if (code != null && !code.isEmpty()) {
                        categories.add(new CategoryData(code, name != null ? name : code));
                    }
                }
            }

            LogUtil.info(CLASS_NAME, "Loaded " + categories.size() + " categories from MDM");

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error loading categories from MDM");
        }

        return categories;
    }

    /**
     * Category data class.
     */
    public static class CategoryData {
        private final String code;
        private final String name;

        public CategoryData(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
    }

    /**
     * Load fields from database using FormDataDao (backward compatible).
     */
    private List<FieldDefinition> loadFieldsFromDatabase(String scopeCode) {
        FieldFilterCriteria criteria = FieldFilterCriteria.forScope(scopeCode).build();
        return loadFieldsFromDatabase(criteria);
    }

    /**
     * Load fields from database using FormDataDao with filter criteria.
     *
     * Builds dynamic WHERE clause:
     * c_isActive='Y' AND c_scopeCode=?
     * [AND c_category IN (?)]
     * [AND c_fieldType IN (?)]
     * [AND c_isGrid=?]
     * [AND c_lookupFormId=?]
     */
    private List<FieldDefinition> loadFieldsFromDatabase(FieldFilterCriteria criteria) {
        List<FieldDefinition> fields = new ArrayList<>();

        try {
            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext()
                .getBean("formDataDao");

            // Build dynamic condition and params
            StringBuilder condition = new StringBuilder("WHERE c_isActive = ?");
            List<Object> paramList = new ArrayList<>();
            paramList.add("Y");

            String scopeCode = criteria.getScopeCode();
            if (scopeCode != null && !scopeCode.isEmpty()) {
                condition.append(" AND c_scopeCode = ?");
                paramList.add(scopeCode);
            }

            // Categories filter
            List<String> categories = criteria.getCategories();
            if (categories != null && !categories.isEmpty()) {
                condition.append(" AND c_category IN (");
                for (int i = 0; i < categories.size(); i++) {
                    if (i > 0) condition.append(", ");
                    condition.append("?");
                    paramList.add(categories.get(i));
                }
                condition.append(")");
            }

            // Field types filter
            List<String> fieldTypes = criteria.getFieldTypes();
            if (fieldTypes != null && !fieldTypes.isEmpty()) {
                condition.append(" AND c_fieldType IN (");
                for (int i = 0; i < fieldTypes.size(); i++) {
                    if (i > 0) condition.append(", ");
                    condition.append("?");
                    paramList.add(fieldTypes.get(i));
                }
                condition.append(")");
            }

            // isGrid filter
            String isGrid = criteria.getIsGrid();
            if (isGrid != null && !isGrid.isEmpty()) {
                condition.append(" AND c_isGrid = ?");
                paramList.add(isGrid);
            }

            // lookupFormId filter
            String lookupFormId = criteria.getLookupFormId();
            if (lookupFormId != null && !lookupFormId.isEmpty()) {
                condition.append(" AND c_lookupFormId = ?");
                paramList.add(lookupFormId);
            }

            condition.append(" ORDER BY c_category, c_displayOrder");

            Object[] params = paramList.toArray();

            LogUtil.debug(CLASS_NAME, "Query condition: " + condition + ", params: " + paramList);

            // Query
            FormRowSet rowSet = formDataDao.find(
                FIELD_DEFINITION_FORM,
                FIELD_DEFINITION_TABLE,
                condition.toString(),
                params,
                null,  // sort
                null,  // desc
                null,  // start
                null   // rows
            );

            if (rowSet != null) {
                for (FormRow row : rowSet) {
                    FieldDefinition field = rowToFieldDefinition(row);
                    if (field != null) {
                        fields.add(field);
                    }
                }
            }

            LogUtil.info(CLASS_NAME, "Loaded " + fields.size() + " fields" +
                (scopeCode != null ? " for scope " + scopeCode : "") +
                (criteria.hasFilters() ? " with filters" : ""));

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error loading field definitions from database");
        }

        return fields;
    }

    /**
     * Convert a FormRow to FieldDefinition.
     */
    private FieldDefinition rowToFieldDefinition(FormRow row) {
        try {
            FieldDefinition field = new FieldDefinition();

            field.setId(row.getId());
            field.setScopeCode(getProperty(row, "scopeCode"));
            field.setFieldId(getProperty(row, "fieldId"));
            field.setFieldLabel(getProperty(row, "fieldLabel"));
            field.setCategory(getProperty(row, "category"));
            field.setFieldType(getProperty(row, "fieldType"));
            field.setApplicableOperators(parseList(getProperty(row, "applicableOperators")));
            field.setGrid("Y".equals(getProperty(row, "isGrid")));
            field.setGridParentField(getProperty(row, "gridParentField"));
            field.setAggregationFunctions(parseList(getProperty(row, "aggregationFunctions")));
            field.setLookupFormId(getProperty(row, "lookupFormId"));
            field.setLookupValues(parseList(getProperty(row, "lookupValues")));
            field.setHelpText(getProperty(row, "helpText"));

            String displayOrder = getProperty(row, "displayOrder");
            if (displayOrder != null && !displayOrder.isEmpty()) {
                try {
                    field.setDisplayOrder(Integer.parseInt(displayOrder));
                } catch (NumberFormatException e) {
                    field.setDisplayOrder(100);
                }
            }

            return field;

        } catch (Exception e) {
            LogUtil.warn(CLASS_NAME, "Error converting row to FieldDefinition: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get property from FormRow, handling null.
     */
    private String getProperty(FormRow row, String name) {
        Object value = row.getProperty(name);
        return value != null ? value.toString() : null;
    }

    /**
     * Parse a semicolon or newline separated list.
     */
    private List<String> parseList(String value) {
        if (value == null || value.isEmpty()) {
            return new ArrayList<>();
        }

        // Handle JSON array format
        if (value.startsWith("[")) {
            value = value.substring(1);
        }
        if (value.endsWith("]")) {
            value = value.substring(0, value.length() - 1);
        }

        // Split by semicolon, newline, or comma
        String[] parts = value.split("[;,\\n]+");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim().replace("\"", "");
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Field definition data class.
     */
    public static class FieldDefinition {
        private String id;
        private String scopeCode;
        private String fieldId;
        private String fieldLabel;
        private String category;
        private String fieldType;
        private List<String> applicableOperators = new ArrayList<>();
        private boolean isGrid;
        private String gridParentField;
        private List<String> aggregationFunctions = new ArrayList<>();
        private String lookupFormId;
        private List<String> lookupValues = new ArrayList<>();
        private String helpText;
        private int displayOrder = 100;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getScopeCode() { return scopeCode; }
        public void setScopeCode(String scopeCode) { this.scopeCode = scopeCode; }

        public String getFieldId() { return fieldId; }
        public void setFieldId(String fieldId) { this.fieldId = fieldId; }

        public String getFieldLabel() { return fieldLabel; }
        public void setFieldLabel(String fieldLabel) { this.fieldLabel = fieldLabel; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getFieldType() { return fieldType; }
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }

        public List<String> getApplicableOperators() { return applicableOperators; }
        public void setApplicableOperators(List<String> applicableOperators) {
            this.applicableOperators = applicableOperators;
        }

        public boolean isGrid() { return isGrid; }
        public void setGrid(boolean grid) { isGrid = grid; }

        public String getGridParentField() { return gridParentField; }
        public void setGridParentField(String gridParentField) { this.gridParentField = gridParentField; }

        public List<String> getAggregationFunctions() { return aggregationFunctions; }
        public void setAggregationFunctions(List<String> aggregationFunctions) {
            this.aggregationFunctions = aggregationFunctions;
        }

        public String getLookupFormId() { return lookupFormId; }
        public void setLookupFormId(String lookupFormId) { this.lookupFormId = lookupFormId; }

        public List<String> getLookupValues() { return lookupValues; }
        public void setLookupValues(List<String> lookupValues) { this.lookupValues = lookupValues; }

        public String getHelpText() { return helpText; }
        public void setHelpText(String helpText) { this.helpText = helpText; }

        public int getDisplayOrder() { return displayOrder; }
        public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

        /**
         * Check if this is a grid child field (has dot notation).
         */
        public boolean isGridChildField() {
            return fieldId != null && fieldId.contains(".");
        }

        /**
         * Get the parent grid field ID for a grid child field.
         */
        public String getParentGridFieldId() {
            if (!isGridChildField()) return null;
            return fieldId.substring(0, fieldId.indexOf('.'));
        }
    }
}
