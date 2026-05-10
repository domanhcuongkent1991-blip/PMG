# Workflow E2E Health Report

- Generated at: 2026-05-07T21:20:06
- Overall: **PASS**
- Passed/Total: 6/6

## Steps
| Step | Status | Exit | Duration(s) |
|---|---|---:|---:|
| plan_validate | PASS | 0 | 0.56 |
| plan_conversation_golden | PASS | 0 | 0.07 |
| skill_store_validate | PASS | 0 | 1.25 |
| skill_store_routing_tests | PASS | 0 | 0.27 |
| auto_mcp_validate | PASS | 0 | 3.61 |
| auto_mcp_regression | PASS | 0 | 3.42 |

## Output tails
### plan_validate
```text
PASS: PLAN-WORKFLOW-CODEX-PLAN-ONLY V3.4 is valid.
```
### plan_conversation_golden
```text
PASS: conversation golden fixtures validated (4 fixtures).
```
### skill_store_validate
```text
PASS
Skills indexed: 61
Skill folders: 61
Validation levels: structural + strict frontmatter + pack schema + project/store gate consistency + routing + release + manifest
No missing skill folders, index entries, references, required files, pack schema issues, handoff drift, stale release reports, version drift, or control-file consistency defects found.
```
### skill_store_routing_tests
```text
- 03-one-click-windows-local-app: 12 expected skills, 4 expected risks, 4 expected outputs, 2 forbidden checks, 7 expected groups, 23 simulated selections
- 04-external-mcp-tool: 9 expected skills, 4 expected risks, 3 expected outputs, 3 forbidden checks, 6 expected groups, 13 simulated selections
- 05-secret-leak-response: 9 expected skills, 4 expected risks, 3 expected outputs, 2 forbidden checks, 4 expected groups, 22 simulated selections
- 06-safe-tech-defaults-nontech: 10 expected skills, 4 expected risks, 3 expected outputs, 2 forbidden checks, 8 expected groups, 23 simulated selections
- 07-release-readiness-handoff: 10 expected skills, 4 expected risks, 4 expected outputs, 2 forbidden checks, 8 expected groups, 20 simulated selections
- 08-factory-web-windows-upload-secret-nontech: 30 expected skills, 7 expected risks, 8 expected outputs, 2 forbidden checks, 12 expected groups, 40 simulated selections
- 09-mobile-backend-proxy-release: 24 expected skills, 4 expected risks, 6 expected outputs, 2 forbidden checks, 10 expected groups, 29 simulated selections
- 10-existing-project-logs-secrets-mcp-handoff: 20 expected skills, 4 expected risks, 8 expected outputs, 2 forbidden checks, 7 expected groups, 27 simulated selections
```
### auto_mcp_validate
```text
MCP workflow validation passed.
```
### auto_mcp_regression
```text
                        "id":  "secret-escalation",
                        "status":  "passed",
                        "errors":  [

                                   ]
                    }
                ]
}
```
