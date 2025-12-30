package global.govstack.rulesapi.compiler;

import java.util.*;

/**
 * Represents the compiled SQL output from rules.
 *
 * Contains:
 * - Eligibility WHERE clause (all mandatory INCLUSION rules ANDed)
 * - Exclusion WHERE clause (any EXCLUSION rule match)
 * - Scoring SELECT expressions (CASE WHEN for BONUS/PRIORITY)
 * - Required JOINs for grid fields
 * - Individual rule SQL for debugging/display
 */
public class CompiledRuleset {

    private String rulesetCode;
    private String scopeCode;

    // Main table info
    private String mainTable;
    private String mainTableAlias;

    // Compiled SQL parts
    private String eligibilityWhereClause;      // ANDed mandatory INCLUSION rules
    private String exclusionWhereClause;        // ORed EXCLUSION rules
    private List<String> scoringSelectClauses;  // CASE WHEN expressions for scoring
    private Set<String> requiredJoins;          // JOIN clauses needed

    // Individual rule SQL (for debugging)
    private List<CompiledRule> compiledRules;

    // Full query templates
    private String eligibilityCheckQuery;       // Check if a farmer is eligible
    private String scoringQuery;                // Calculate scores for eligible farmers
    private String fullEligibilityQuery;        // Combined eligibility + scoring

    // Compilation metadata
    private Date compiledAt;
    private int totalRules;
    private int inclusionRules;
    private int exclusionRules;
    private int bonusRules;
    private List<String> compilationWarnings;

    /**
     * Represents a single compiled rule.
     */
    public static class CompiledRule {
        private String ruleName;
        private String ruleCode;
        private String ruleType;
        private boolean mandatory;
        private Integer score;
        private String whereClause;
        private String selectExpression;
        private Set<String> usedFields;
        private Set<String> requiredJoins;

        public CompiledRule(String ruleName, String ruleCode, String ruleType) {
            this.ruleName = ruleName;
            this.ruleCode = ruleCode;
            this.ruleType = ruleType;
            this.usedFields = new HashSet<>();
            this.requiredJoins = new HashSet<>();
        }

        // Getters and setters
        public String getRuleName() { return ruleName; }
        public String getRuleCode() { return ruleCode; }
        public String getRuleType() { return ruleType; }

        public boolean isMandatory() { return mandatory; }
        public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }

        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }

        public String getWhereClause() { return whereClause; }
        public void setWhereClause(String whereClause) { this.whereClause = whereClause; }

        public String getSelectExpression() { return selectExpression; }
        public void setSelectExpression(String selectExpression) { this.selectExpression = selectExpression; }

        public Set<String> getUsedFields() { return usedFields; }
        public void addUsedField(String field) { usedFields.add(field); }

        public Set<String> getRequiredJoins() { return requiredJoins; }
        public void addRequiredJoin(String join) { requiredJoins.add(join); }
    }

    public CompiledRuleset() {
        this.scoringSelectClauses = new ArrayList<>();
        this.requiredJoins = new LinkedHashSet<>();
        this.compiledRules = new ArrayList<>();
        this.compilationWarnings = new ArrayList<>();
        this.compiledAt = new Date();
    }

    // === Builder Methods ===

    public void setRulesetCode(String rulesetCode) { this.rulesetCode = rulesetCode; }
    public void setScopeCode(String scopeCode) { this.scopeCode = scopeCode; }
    public void setMainTable(String mainTable, String alias) {
        this.mainTable = mainTable;
        this.mainTableAlias = alias;
    }

    public void setEligibilityWhereClause(String clause) { this.eligibilityWhereClause = clause; }
    public void setExclusionWhereClause(String clause) { this.exclusionWhereClause = clause; }
    public void addScoringSelectClause(String clause) { this.scoringSelectClauses.add(clause); }
    public void addRequiredJoin(String join) { this.requiredJoins.add(join); }
    public void addCompiledRule(CompiledRule rule) { this.compiledRules.add(rule); }
    public void addWarning(String warning) { this.compilationWarnings.add(warning); }

    public void setTotalRules(int total) { this.totalRules = total; }
    public void setInclusionRules(int count) { this.inclusionRules = count; }
    public void setExclusionRules(int count) { this.exclusionRules = count; }
    public void setBonusRules(int count) { this.bonusRules = count; }

    // === Query Generation ===

    /**
     * Generate the full eligibility check query.
     * Returns records that pass all mandatory rules.
     */
    public String generateEligibilityCheckQuery() {
        StringBuilder sql = new StringBuilder();

        sql.append("SELECT ").append(mainTableAlias).append(".id");
        sql.append("\nFROM ").append(mainTable).append(" ").append(mainTableAlias);

        // Add JOINs if needed
        // Note: For eligibility, we typically use EXISTS subqueries instead

        sql.append("\nWHERE 1=1");

        if (eligibilityWhereClause != null && !eligibilityWhereClause.isEmpty()) {
            sql.append("\n  AND (").append(eligibilityWhereClause).append(")");
        }

        if (exclusionWhereClause != null && !exclusionWhereClause.isEmpty()) {
            sql.append("\n  AND NOT (").append(exclusionWhereClause).append(")");
        }

        this.eligibilityCheckQuery = sql.toString();
        return eligibilityCheckQuery;
    }

    /**
     * Generate scoring query for eligible records.
     * Returns IDs with their total score.
     */
    public String generateScoringQuery() {
        StringBuilder sql = new StringBuilder();

        sql.append("SELECT ").append(mainTableAlias).append(".id");

        // Add individual rule scores
        for (String selectClause : scoringSelectClauses) {
            sql.append(",\n  ").append(selectClause);
        }

        // Add total score
        if (!scoringSelectClauses.isEmpty()) {
            sql.append(",\n  (");
            List<String> scoreRefs = new ArrayList<>();
            for (CompiledRule rule : compiledRules) {
                if (rule.getScore() != null && rule.getScore() != 0) {
                    scoreRefs.add("COALESCE(score_" + rule.getRuleCode().toLowerCase() + ", 0)");
                }
            }
            sql.append(String.join(" + ", scoreRefs));
            sql.append(") AS total_score");
        }

        sql.append("\nFROM ").append(mainTable).append(" ").append(mainTableAlias);

        sql.append("\nWHERE 1=1");

        if (eligibilityWhereClause != null && !eligibilityWhereClause.isEmpty()) {
            sql.append("\n  AND (").append(eligibilityWhereClause).append(")");
        }

        if (exclusionWhereClause != null && !exclusionWhereClause.isEmpty()) {
            sql.append("\n  AND NOT (").append(exclusionWhereClause).append(")");
        }

        sql.append("\nORDER BY total_score DESC");

        this.scoringQuery = sql.toString();
        return scoringQuery;
    }

    /**
     * Generate combined eligibility + rule results query.
     * Returns IDs with pass/fail status for each rule.
     */
    public String generateFullEligibilityQuery() {
        StringBuilder sql = new StringBuilder();

        sql.append("SELECT ").append(mainTableAlias).append(".id");

        // Add pass/fail for each rule
        for (CompiledRule rule : compiledRules) {
            if (rule.getSelectExpression() != null) {
                sql.append(",\n  ").append(rule.getSelectExpression());
            }
        }

        // Overall eligibility
        sql.append(",\n  CASE WHEN ");
        if (eligibilityWhereClause != null && !eligibilityWhereClause.isEmpty()) {
            sql.append("(").append(eligibilityWhereClause).append(")");
        } else {
            sql.append("1=1");
        }
        if (exclusionWhereClause != null && !exclusionWhereClause.isEmpty()) {
            sql.append(" AND NOT (").append(exclusionWhereClause).append(")");
        }
        sql.append(" THEN 1 ELSE 0 END AS is_eligible");

        sql.append("\nFROM ").append(mainTable).append(" ").append(mainTableAlias);

        // No WHERE clause - return all records with their eligibility status

        this.fullEligibilityQuery = sql.toString();
        return fullEligibilityQuery;
    }

    // === Getters ===

    public String getRulesetCode() { return rulesetCode; }
    public String getScopeCode() { return scopeCode; }
    public String getMainTable() { return mainTable; }
    public String getMainTableAlias() { return mainTableAlias; }

    public String getEligibilityWhereClause() { return eligibilityWhereClause; }
    public String getExclusionWhereClause() { return exclusionWhereClause; }
    public List<String> getScoringSelectClauses() { return scoringSelectClauses; }
    public Set<String> getRequiredJoins() { return requiredJoins; }
    public List<CompiledRule> getCompiledRules() { return compiledRules; }

    public String getEligibilityCheckQuery() { return eligibilityCheckQuery; }
    public String getScoringQuery() { return scoringQuery; }
    public String getFullEligibilityQuery() { return fullEligibilityQuery; }

    public Date getCompiledAt() { return compiledAt; }
    public int getTotalRules() { return totalRules; }
    public int getInclusionRules() { return inclusionRules; }
    public int getExclusionRules() { return exclusionRules; }
    public int getBonusRules() { return bonusRules; }
    public List<String> getCompilationWarnings() { return compilationWarnings; }

    public boolean hasWarnings() { return !compilationWarnings.isEmpty(); }
}
