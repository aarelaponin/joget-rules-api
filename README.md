# Joget Rules API

A Joget DX 8.1 API plugin that provides a **Rules Script DSL** for defining eligibility rules in social protection programs. This plugin handles parsing, validation, compilation to SQL, and CRUD operations for rulesets.

## Overview

The Rules API is the backend component of the Rule Editor system. It provides REST endpoints for:
- Validating Rules Script syntax
- Compiling rules to SQL WHERE clauses
- Managing field dictionaries for autocomplete
- Persisting and loading rulesets

**Companion Plugin:** [joget-rule-editor](https://github.com/aarelaponin/joget-rule-editor) - UI plugin with CodeMirror-based editor

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  joget-rule-editor (UI Plugin)                              │
│  • CodeMirror editor with syntax highlighting               │
│  • Field dictionary panel with filtering                    │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP REST API
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  joget-rules-api (THIS PLUGIN)                              │
│  • RulesApiProvider - REST endpoints                        │
│  • RuleScriptParser - ANTLR parser facade                   │
│  • RuleScriptCompiler - Rules → SQL compilation             │
│  • FieldRegistryService - Field dictionary from DB          │
│  • RulesetService - Ruleset CRUD                            │
└──────────────────────────┬──────────────────────────────────┘
                           │ Maven dependency
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  rules-grammar (Library)                                    │
│  • ANTLR4 grammar for Rules Script DSL                      │
│  • Lexer, Parser, AST visitor                               │
└─────────────────────────────────────────────────────────────┘
```

## Build

```bash
# Build the OSGi bundle
mvn clean package

# Output
target/joget-rules-api-8.1-SNAPSHOT.jar
```

## Installation

1. Build the JAR file
2. Upload to Joget: **Settings → Manage Plugins → Upload Plugin**
3. Create API Builder app with ID `jre`
4. Add API resource pointing to `RulesApiProvider`

## API Endpoints

Base URL: `/jw/api/jre/jre`

### Field Dictionary

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/fields` | GET | Get field definitions for autocomplete |
| `/categories` | GET | Get field categories from MDM |
| `/fields/refresh` | POST | Clear field cache |

**GET /fields** query parameters:
- `scopeCode` (required) - Field scope code
- `categories` - Comma-separated category filter
- `fieldTypes` - Comma-separated type filter (NUMBER, TEXT, LOOKUP, etc.)
- `isGrid` - Y/N filter for grid fields
- `lookupFormId` - Filter by lookup form ID

### Validation & Compilation

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/validate` | POST | Validate Rules Script syntax |
| `/compile` | POST | Compile rules to SQL WHERE clauses |

### Ruleset Management

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/saveRuleset` | POST | Save or update a ruleset |
| `/loadRuleset` | GET | Load ruleset by code |
| `/publishRuleset` | POST | Change status to PUBLISHED |

## Rules Script DSL

```
# Sample Rules Script

RULE "Adult Farmer"
  TYPE: INCLUSION
  CATEGORY: demographic
  MANDATORY: YES
  ORDER: 10
  WHEN age >= 18 AND occupation = "farmer"
  PASS MESSAGE: "Eligible as adult farmer"
  FAIL MESSAGE: "Must be 18+ and a farmer"

RULE "Income Threshold"
  TYPE: INCLUSION
  MANDATORY: YES
  ORDER: 20
  WHEN householdIncome < 50000
  FAIL MESSAGE: "Income exceeds threshold"

RULE "Female Headed Bonus"
  TYPE: BONUS
  SCORE: 10
  WHEN femaleHeadedHousehold = true
  PASS MESSAGE: "Priority for female-headed households"
```

### Rule Types
- `INCLUSION` - Must pass for eligibility
- `EXCLUSION` - Causes disqualification if matched
- `PRIORITY` - Affects ranking/ordering
- `BONUS` - Adds points to score

### Operators
- Comparison: `=`, `!=`, `>`, `>=`, `<`, `<=`
- Range: `BETWEEN x AND y`
- Set: `IN ("a", "b", "c")`
- Null: `IS EMPTY`, `IS NOT EMPTY`
- Logic: `AND`, `OR`, `NOT`, `()`

### Aggregate Functions (for grid fields)
- `COUNT(grid)` - Count rows
- `SUM(grid.field)` - Sum values
- `AVG(grid.field)` - Average
- `MIN(grid.field)`, `MAX(grid.field)`
- `HAS_ANY(grid.field, "value")` - Any row matches
- `HAS_ALL(grid.field, "value")` - All rows match
- `HAS_NONE(grid.field, "value")` - No row matches

## Joget Forms Required

| Form ID | Purpose |
|---------|---------|
| `jreFieldDefinition` | Field dictionary (available fields) |
| `jreFieldScope` | Field scopes |
| `rulesetForm` | Stored rulesets |
| `md51FieldCategory` | Field categories MDM |

## Package Structure

```
global.govstack.rulesapi/
├── Activator.java           # OSGi lifecycle
├── lib/
│   └── RulesApiProvider.java    # REST API endpoints
├── parser/
│   ├── RuleScriptParser.java    # ANTLR parser facade
│   └── RuleScriptValidator.java # Validation logic
├── compiler/
│   ├── RuleScriptCompiler.java  # Rules → SQL
│   ├── CompiledRuleset.java     # Compilation result
│   └── FieldMapping.java        # Field → table/column mapping
├── adapter/
│   ├── RuleAdapter.java         # Convert grammar → model
│   ├── ConditionAdapter.java
│   └── ValueAdapter.java
├── model/
│   ├── Rule.java
│   ├── Condition.java
│   └── ValidationResult.java
└── service/
    ├── FieldRegistryService.java    # Field dictionary
    ├── FieldFilterCriteria.java     # Filter parameters
    └── RulesetService.java          # Ruleset CRUD
```

## Dependencies

- **Joget DX 8.1** - Platform (provided)
- **rules-grammar** - ANTLR-based parser (embedded in bundle)
- **ANTLR 4 Runtime** - Parser runtime (embedded in bundle)

## License

Proprietary - GovStack

## Related Projects

- [joget-rule-editor](https://github.com/aarelaponin/joget-rule-editor) - UI plugin
- rules-grammar - ANTLR grammar library
