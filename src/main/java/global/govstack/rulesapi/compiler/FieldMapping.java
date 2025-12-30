package global.govstack.rulesapi.compiler;

import java.util.*;

/**
 * Maps field IDs to SQL table and column references.
 *
 * Configuration can come from:
 * - Field definition table (enhanced with table/column info)
 * - YAML configuration file
 * - Programmatic setup
 */
public class FieldMapping {

    // Main table alias
    private String mainTableAlias = "f";
    private String mainTableName = "app_fd_farmer";

    // Field mappings: fieldId -> FieldInfo
    private Map<String, FieldInfo> fieldMappings = new HashMap<>();

    // Grid table mappings: gridFieldId -> GridInfo
    private Map<String, GridInfo> gridMappings = new HashMap<>();

    /**
     * Field information for simple fields.
     */
    public static class FieldInfo {
        private String fieldId;
        private String tableName;
        private String tableAlias;
        private String columnName;
        private String fieldType;
        private boolean isGridChild;
        private String gridParentField;

        public FieldInfo(String fieldId, String tableName, String tableAlias,
                         String columnName, String fieldType) {
            this.fieldId = fieldId;
            this.tableName = tableName;
            this.tableAlias = tableAlias;
            this.columnName = columnName;
            this.fieldType = fieldType;
            this.isGridChild = false;
        }

        public String getFieldId() { return fieldId; }
        public String getTableName() { return tableName; }
        public String getTableAlias() { return tableAlias; }
        public String getColumnName() { return columnName; }
        public String getFieldType() { return fieldType; }
        public boolean isGridChild() { return isGridChild; }
        public String getGridParentField() { return gridParentField; }

        public void setGridChild(boolean gridChild) { isGridChild = gridChild; }
        public void setGridParentField(String gridParentField) { this.gridParentField = gridParentField; }

        /**
         * Get full SQL reference: alias.column
         */
        public String getSqlReference() {
            return tableAlias + ".c_" + columnName;
        }
    }

    /**
     * Grid table information for repeating data.
     */
    public static class GridInfo {
        private String gridFieldId;
        private String tableName;
        private String tableAlias;
        private String foreignKeyColumn;
        private String parentTableAlias;

        public GridInfo(String gridFieldId, String tableName, String tableAlias,
                        String foreignKeyColumn, String parentTableAlias) {
            this.gridFieldId = gridFieldId;
            this.tableName = tableName;
            this.tableAlias = tableAlias;
            this.foreignKeyColumn = foreignKeyColumn;
            this.parentTableAlias = parentTableAlias;
        }

        public String getGridFieldId() { return gridFieldId; }
        public String getTableName() { return tableName; }
        public String getTableAlias() { return tableAlias; }
        public String getForeignKeyColumn() { return foreignKeyColumn; }
        public String getParentTableAlias() { return parentTableAlias; }

        /**
         * Get JOIN clause for this grid.
         */
        public String getJoinClause() {
            return String.format("LEFT JOIN %s %s ON %s.c_%s = %s.id",
                tableName, tableAlias, tableAlias, foreignKeyColumn, parentTableAlias);
        }

        /**
         * Get correlation condition for subqueries.
         */
        public String getCorrelation() {
            return String.format("%s.c_%s = %s.id", tableAlias, foreignKeyColumn, parentTableAlias);
        }
    }

    public FieldMapping() {
        // Default constructor - mappings added via methods
    }

    /**
     * Create default mapping for Farmer Eligibility scope.
     */
    public static FieldMapping createFarmerEligibilityMapping() {
        FieldMapping mapping = new FieldMapping();
        mapping.setMainTable("app_fd_farmer", "f");

        // Demographic fields
        mapping.addField("age", "farmer", "f", "age", "NUMBER");
        mapping.addField("gender", "farmer", "f", "gender", "LOOKUP");
        mapping.addField("maritalStatus", "farmer", "f", "maritalStatus", "LOOKUP");

        // Location fields
        mapping.addField("district", "farmer", "f", "district", "LOOKUP");
        mapping.addField("village", "farmer", "f", "village", "TEXT");

        // Agricultural fields
        mapping.addField("hasCrops", "farmer", "f", "hasCrops", "BOOLEAN");
        mapping.addField("hasLivestock", "farmer", "f", "hasLivestock", "BOOLEAN");

        // Derived fields
        mapping.addField("totalHouseholdMembers", "farmer", "f", "totalHouseholdMembers", "NUMBER");
        mapping.addField("childrenUnder5", "farmer", "f", "childrenUnder5", "NUMBER");
        mapping.addField("femaleHeadedHousehold", "farmer", "f", "femaleHeadedHousehold", "BOOLEAN");
        mapping.addField("vulnerabilityScore", "farmer", "f", "vulnerabilityScore", "DECIMAL");
        mapping.addField("averageAnnualIncome", "farmer", "f", "averageAnnualIncome", "DECIMAL");
        mapping.addField("totalCultivatedLand", "farmer", "f", "totalCultivatedLand", "DECIMAL");

        // Grid: householdMembers
        mapping.addGrid("householdMembers", "app_fd_householdMember", "hm", "farmerId", "f");
        mapping.addGridChildField("householdMembers.sex", "householdMember", "hm", "sex", "LOOKUP", "householdMembers");
        mapping.addGridChildField("householdMembers.disability", "householdMember", "hm", "disability", "LOOKUP", "householdMembers");
        mapping.addGridChildField("householdMembers.age", "householdMember", "hm", "age", "NUMBER", "householdMembers");
        mapping.addGridChildField("householdMembers.relationship", "householdMember", "hm", "relationship", "LOOKUP", "householdMembers");

        // Grid: cropManagement
        mapping.addGrid("cropManagement", "app_fd_cropManagement", "cm", "farmerId", "f");
        mapping.addGridChildField("cropManagement.cropType", "cropManagement", "cm", "cropType", "LOOKUP", "cropManagement");
        mapping.addGridChildField("cropManagement.areaCultivated", "cropManagement", "cm", "areaCultivated", "DECIMAL", "cropManagement");

        return mapping;
    }

    // === Configuration Methods ===

    public void setMainTable(String tableName, String alias) {
        this.mainTableName = tableName;
        this.mainTableAlias = alias;
    }

    public void addField(String fieldId, String tableName, String tableAlias,
                         String columnName, String fieldType) {
        fieldMappings.put(fieldId, new FieldInfo(fieldId, tableName, tableAlias, columnName, fieldType));
    }

    public void addGrid(String gridFieldId, String tableName, String tableAlias,
                        String foreignKeyColumn, String parentTableAlias) {
        gridMappings.put(gridFieldId, new GridInfo(gridFieldId, tableName, tableAlias,
                                                    foreignKeyColumn, parentTableAlias));
    }

    public void addGridChildField(String fieldId, String tableName, String tableAlias,
                                  String columnName, String fieldType, String gridParentField) {
        FieldInfo info = new FieldInfo(fieldId, tableName, tableAlias, columnName, fieldType);
        info.setGridChild(true);
        info.setGridParentField(gridParentField);
        fieldMappings.put(fieldId, info);
    }

    // === Query Methods ===

    public String getMainTableName() {
        return mainTableName;
    }

    public String getMainTableAlias() {
        return mainTableAlias;
    }

    public FieldInfo getField(String fieldId) {
        return fieldMappings.get(fieldId);
    }

    public GridInfo getGrid(String gridFieldId) {
        return gridMappings.get(gridFieldId);
    }

    /**
     * Get SQL column reference for a field.
     */
    public String getSqlReference(String fieldId) {
        FieldInfo field = fieldMappings.get(fieldId);
        if (field != null) {
            return field.getSqlReference();
        }
        // Fallback: assume it's on main table
        return mainTableAlias + ".c_" + fieldId;
    }

    /**
     * Check if a field is a grid field.
     */
    public boolean isGridField(String fieldId) {
        return gridMappings.containsKey(fieldId);
    }

    /**
     * Check if a field is a grid child field.
     */
    public boolean isGridChildField(String fieldId) {
        FieldInfo field = fieldMappings.get(fieldId);
        return field != null && field.isGridChild();
    }

    /**
     * Get grid info for a child field.
     */
    public GridInfo getGridForChildField(String fieldId) {
        FieldInfo field = fieldMappings.get(fieldId);
        if (field != null && field.isGridChild()) {
            return gridMappings.get(field.getGridParentField());
        }
        return null;
    }

    /**
     * Get all grids that are used (for generating JOINs).
     */
    public Collection<GridInfo> getAllGrids() {
        return gridMappings.values();
    }
}
