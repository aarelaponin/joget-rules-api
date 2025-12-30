package global.govstack.rulesapi.service;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.UuidGenerator;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Service for saving and loading rulesets from the ruleset form.
 */
public class RulesetService {

    private static final String CLASS_NAME = RulesetService.class.getName();

    // Form/table names
    private static final String RULESET_FORM = "rulesetForm";
    private static final String RULESET_TABLE = "rulesetForm";

    /**
     * Save or update a ruleset.
     *
     * @param rulesetCode The ruleset code (null for new)
     * @param rulesetName The ruleset name
     * @param script The Rules Script
     * @param contextType The context type (ELIGIBILITY, DERIVATION, etc.)
     * @param contextCode The context code (e.g., program code)
     * @param fieldScopeCode The field scope code
     * @param notes Optional change notes
     * @return The saved ruleset code
     */
    public String saveRuleset(String rulesetCode, String rulesetName, String script,
                              String contextType, String contextCode, String fieldScopeCode,
                              String notes) {
        try {
            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext()
                .getBean("formDataDao");

            FormRow row = new FormRow();
            boolean isNew = (rulesetCode == null || rulesetCode.isEmpty());

            if (isNew) {
                // Generate new code: RS-YYMMDD-XXXX
                String timestamp = new SimpleDateFormat("yyMMdd").format(new Date());
                String uuid = UuidGenerator.getInstance().getUuid().substring(0, 4).toUpperCase();
                rulesetCode = "RS-" + timestamp + "-" + uuid;

                row.setId(rulesetCode);
                row.setProperty("rulesetCode", rulesetCode);
                row.setProperty("version", "1");
                row.setProperty("status", "DRAFT");
            } else {
                // Load existing
                FormRow existing = loadRulesetRow(rulesetCode);
                if (existing != null) {
                    row = existing;
                    // Increment version
                    String currentVersion = (String) existing.getProperty("version");
                    int newVersion = 1;
                    if (currentVersion != null) {
                        try {
                            newVersion = Integer.parseInt(currentVersion) + 1;
                        } catch (NumberFormatException e) {
                            newVersion = 1;
                        }
                    }
                    row.setProperty("version", String.valueOf(newVersion));
                } else {
                    row.setId(rulesetCode);
                    row.setProperty("rulesetCode", rulesetCode);
                    row.setProperty("version", "1");
                    row.setProperty("status", "DRAFT");
                }
            }

            // Set properties
            row.setProperty("rulesetName", rulesetName);
            row.setProperty("eligibilityScript", script);
            row.setProperty("fieldScopeCode", fieldScopeCode);

            // contextType and contextCode are optional (deprecated)
            if (contextType != null && !contextType.isEmpty()) {
                row.setProperty("contextType", contextType);
            }
            if (contextCode != null && !contextCode.isEmpty()) {
                row.setProperty("contextCode", contextCode);
            }
            if (notes != null && !notes.isEmpty()) {
                row.setProperty("notes", notes);
            }

            // Save
            FormRowSet rowSet = new FormRowSet();
            rowSet.add(row);
            formDataDao.saveOrUpdate(RULESET_FORM, RULESET_TABLE, rowSet);

            LogUtil.info(CLASS_NAME, "Saved ruleset: " + rulesetCode);

            return rulesetCode;

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error saving ruleset");
            throw new RuntimeException("Failed to save ruleset: " + e.getMessage(), e);
        }
    }

    /**
     * Load a ruleset by code.
     *
     * @param rulesetCode The ruleset code
     * @return Ruleset data or null if not found
     */
    public RulesetData loadRuleset(String rulesetCode) {
        FormRow row = loadRulesetRow(rulesetCode);
        if (row == null) {
            return null;
        }
        return rowToRulesetData(row);
    }

    /**
     * Load a ruleset by context (e.g., for a specific program).
     * Returns the latest published ruleset for the context.
     *
     * @param contextType The context type
     * @param contextCode The context code
     * @return Ruleset data or null if not found
     */
    public RulesetData loadRulesetByContext(String contextType, String contextCode) {
        try {
            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext()
                .getBean("formDataDao");

            String condition = "WHERE c_contextType = ? AND c_contextCode = ? AND c_status = ? ORDER BY c_version DESC";
            Object[] params = new Object[]{contextType, contextCode, "PUBLISHED"};

            FormRowSet rowSet = formDataDao.find(
                RULESET_FORM, RULESET_TABLE, condition, params, null, null, 0, 1
            );

            if (rowSet != null && !rowSet.isEmpty()) {
                return rowToRulesetData(rowSet.get(0));
            }

            // If no published, try draft
            condition = "WHERE c_contextType = ? AND c_contextCode = ? ORDER BY c_version DESC";
            params = new Object[]{contextType, contextCode};

            rowSet = formDataDao.find(
                RULESET_FORM, RULESET_TABLE, condition, params, null, null, 0, 1
            );

            if (rowSet != null && !rowSet.isEmpty()) {
                return rowToRulesetData(rowSet.get(0));
            }

            return null;

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error loading ruleset by context");
            return null;
        }
    }

    /**
     * Update ruleset status.
     *
     * @param rulesetCode The ruleset code
     * @param status New status (DRAFT, PUBLISHED, DEPRECATED, ARCHIVED)
     */
    public void updateStatus(String rulesetCode, String status) {
        try {
            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext()
                .getBean("formDataDao");

            FormRow row = loadRulesetRow(rulesetCode);
            if (row == null) {
                throw new RuntimeException("Ruleset not found: " + rulesetCode);
            }

            row.setProperty("status", status);

            // If publishing, set effective date
            if ("PUBLISHED".equals(status)) {
                String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                if (row.getProperty("effectiveFrom") == null) {
                    row.setProperty("effectiveFrom", today);
                }
            }

            FormRowSet rowSet = new FormRowSet();
            rowSet.add(row);
            formDataDao.saveOrUpdate(RULESET_FORM, RULESET_TABLE, rowSet);

            LogUtil.info(CLASS_NAME, "Updated ruleset status: " + rulesetCode + " -> " + status);

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error updating ruleset status");
            throw new RuntimeException("Failed to update status: " + e.getMessage(), e);
        }
    }

    /**
     * Load raw FormRow for a ruleset by rulesetCode column.
     */
    private FormRow loadRulesetRow(String rulesetCode) {
        try {
            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext()
                .getBean("formDataDao");

            // Query by rulesetCode column, not by primary key
            String condition = "WHERE c_rulesetCode = ?";
            Object[] params = new Object[]{rulesetCode};

            FormRowSet rowSet = formDataDao.find(
                RULESET_FORM, RULESET_TABLE, condition, params, null, null, 0, 1
            );

            if (rowSet != null && !rowSet.isEmpty()) {
                return rowSet.get(0);
            }
            return null;

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error loading ruleset row: " + rulesetCode);
            return null;
        }
    }

    /**
     * Convert FormRow to RulesetData.
     */
    private RulesetData rowToRulesetData(FormRow row) {
        RulesetData data = new RulesetData();

        data.setRulesetCode((String) row.getProperty("rulesetCode"));
        data.setRulesetName((String) row.getProperty("rulesetName"));
        data.setScript((String) row.getProperty("eligibilityScript"));
        // status and version may not exist in all forms - provide defaults
        String status = (String) row.getProperty("status");
        data.setStatus(status != null ? status : "DRAFT");
        String version = (String) row.getProperty("version");
        data.setVersion(version != null ? version : "1");
        data.setContextType((String) row.getProperty("contextType"));
        data.setContextCode((String) row.getProperty("contextCode"));
        data.setFieldScopeCode((String) row.getProperty("fieldScopeCode"));
        data.setEffectiveFrom((String) row.getProperty("effectiveFrom"));
        data.setEffectiveTo((String) row.getProperty("effectiveTo"));
        data.setNotes((String) row.getProperty("notes"));

        return data;
    }

    /**
     * Ruleset data transfer object.
     */
    public static class RulesetData {
        private String rulesetCode;
        private String rulesetName;
        private String script;
        private String status;
        private String version;
        private String contextType;
        private String contextCode;
        private String fieldScopeCode;
        private String effectiveFrom;
        private String effectiveTo;
        private String notes;

        // Getters and setters
        public String getRulesetCode() { return rulesetCode; }
        public void setRulesetCode(String rulesetCode) { this.rulesetCode = rulesetCode; }

        public String getRulesetName() { return rulesetName; }
        public void setRulesetName(String rulesetName) { this.rulesetName = rulesetName; }

        public String getScript() { return script; }
        public void setScript(String script) { this.script = script; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public String getContextType() { return contextType; }
        public void setContextType(String contextType) { this.contextType = contextType; }

        public String getContextCode() { return contextCode; }
        public void setContextCode(String contextCode) { this.contextCode = contextCode; }

        public String getFieldScopeCode() { return fieldScopeCode; }
        public void setFieldScopeCode(String fieldScopeCode) { this.fieldScopeCode = fieldScopeCode; }

        public String getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(String effectiveFrom) { this.effectiveFrom = effectiveFrom; }

        public String getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(String effectiveTo) { this.effectiveTo = effectiveTo; }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }
}
