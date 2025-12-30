# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the OSGi bundle
mvn clean package

# Run tests
mvn test

# Run a single test
mvn test -Dtest=TestClassName#methodName
```

The build produces an OSGi bundle JAR at `target/joget-rules-api-8.1-SNAPSHOT.jar` that can be deployed to Joget DX 8.1.

## Project Overview

This is a Joget DX 8.1 API plugin that provides a Rules Script DSL for defining eligibility rules. The plugin parses a custom rules language, compiles rules to SQL, and provides CRUD operations for rulesets.

**Key dependency**: Uses `rules-grammar` (ANTLR-based parser) for parsing the Rules Script DSL.

## Architecture

### Package Structure

- **`lib/RulesApiProvider`** - Main API plugin class exposing REST endpoints. Extends `ApiPluginAbstract` for Joget's API framework.
- **`parser/`** - Wraps the ANTLR parser from `rules-grammar` and adapts results to internal models
- **`compiler/`** - Compiles parsed rules to SQL (WHERE clauses, scoring queries)
- **`adapter/`** - Converts between `rules-grammar` model objects and internal model objects
- **`model/`** - Domain objects: `Rule`, `Condition`, `ValidationResult`
- **`service/`** - Database services using Joget's `FormDataDao`

### API Endpoints

All endpoints are under `/jw/api/jre/`:

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/jre/validate` | POST | Validate Rules Script syntax |
| `/jre/compile` | POST | Compile rules to SQL queries |
| `/jre/fields` | GET | Get field definitions for autocomplete |
| `/jre/categories` | GET | Get field categories |
| `/jre/fields/refresh` | POST | Clear field cache |
| `/jre/saveRuleset` | POST | Save/update a ruleset |
| `/jre/loadRuleset` | GET | Load a ruleset by code |
| `/jre/publishRuleset` | POST | Change ruleset status to PUBLISHED |

### Rules Script DSL

The DSL defines eligibility rules with this structure:
```
RULE "Rule Name"
  TYPE: INCLUSION | EXCLUSION | PRIORITY | BONUS
  CATEGORY: category_name
  MANDATORY: YES | NO
  ORDER: number
  WHEN condition
  SCORE: number
  PASS MESSAGE: "message"
  FAIL MESSAGE: "message"
```

Conditions support: comparisons (`=`, `!=`, `>`, `>=`, `<`, `<=`), `BETWEEN`, `IN`, `IS EMPTY`, `IS NOT EMPTY`, `AND`, `OR`, `NOT`, and aggregate functions (`COUNT`, `SUM`, `AVG`, `MIN`, `MAX`, `HAS_ANY`, `HAS_ALL`, `HAS_NONE`).

### Data Flow

1. **Parse**: `RuleScriptParser` → `rules-grammar` ANTLR parser → `RuleAdapter` → internal `Rule`/`Condition` models
2. **Compile**: `RuleScriptCompiler` converts rules to SQL using `FieldMapping` for table/column mappings
3. **Persist**: `RulesetService` stores rulesets in Joget forms via `FormDataDao`
4. **Fields**: `FieldRegistryService` loads field definitions from `jreFieldDefinition` form

### Joget Forms Used

- `jreFieldDefinition` - Field dictionary (available fields for rules)
- `jreFieldScope` - Field scopes
- `rulesetForm` - Stored rulesets
- `md51FieldCategory` - Field categories MDM

### OSGi Bundle Configuration

The plugin is packaged as an OSGi bundle. Key configurations in `pom.xml`:
- Bundle activator: `global.govstack.rulesapi.Activator`
- Embeds `rules-grammar` and `antlr4-runtime` into the bundle
- Imports Joget packages as provided dependencies
