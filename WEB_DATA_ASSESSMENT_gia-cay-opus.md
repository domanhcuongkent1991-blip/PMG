# Web Data Assessment - gia-cay-opus.vercel.app

## Source
- URL: https://gia-cay-opus.vercel.app/
- Read date: 2026-04-22 (Asia/Saigon)

## Extracted data (structured)

1) System metadata (marketing-style)
- Product label: "CLAUDE OPUS // NEURAL NETWORK SYSTEM"
- Version string: "4.7.0"
- Context claim: "200K"
- Other claims: "1 TRILLION BENCHMARK SCORE", "10 VAN NAM CONTEXT WINDOW"

2) Workflow module
- Declared: 12 workflows (WF-01..WF-12)
- Examples:
  - WF-02 Multi-step reasoning
  - WF-03 Agentic loop
  - WF-05 Code generation workflow
  - WF-09 Instruction following
  - WF-12 Self-critique & revision

3) Skill matrix module
- Declared: 24 core skills
- Includes coding/reasoning/multimodal/prompt engineering/security awareness

4) Role library module
- Declared: 16 roles
- Examples:
  - RL-01 Senior Software Engineer
  - RL-09 DevOps/SRE
  - RL-12 Cybersecurity Analyst

5) System prompt module
- Declared: 18 prompt templates
- Visible examples around line ~518:
  - SP-04 Agentic Task Executor
  - SP-05 Customer Support Agent
  - SP-06 Document Analyst

## Fit assessment for this project (Android + Google Sheets)

### High fit (process-level only)
- Multi-step decomposition mindset (WF-02) -> good for task breakdown.
- Instruction compliance mindset (WF-09) -> good for strict business rules.
- Self-review before final answer (WF-12) -> good for stability and QA discipline.
- Agent checkpoint concept (SP-04) -> good for safe irreversible actions.

### Low/No fit (data-level)
- No `ma_thiet_bi`, `record_id`, `ngay_phat_hien`, `ngay_sua_chua` fields.
- No Google Sheets schema/mapping/tab-role/sheetId content.
- No Room entity schema, migration script, WorkManager sync contract.
- No Android build/toolchain setup data.

## Decision
- Use this page as a **soft process reference** only.
- Do **not** use it as source of truth for business data, app schema, or sync rules.
- Source of truth for this project remains:
  - Project docs in repository (AGENTS/BUSINESS_RULES/DATA_MODEL/SYNC_RULES)
  - Real Google Sheet data used by your team
  - Official Android + Google Sheets documentation

## Risk notes
- Several claims are promotional and not independently verified from this page alone.
- Should not be used as benchmark/security/legal authority.
