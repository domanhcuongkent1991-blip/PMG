# Project Plan

## 1. Non-Tech Decision Summary
- Recommended first version: Android app internal-use, local-first, sync to Google Sheet.
- Why this direction is suitable: fit with current field workflow, real-device usage, and existing data source.
- What should not be built yet: iOS/web admin full stack, autonomous AI actions, complex RBAC.
- Biggest risks: repeated build environment lock, schema drift in Google Sheet, button-flow regression.
- Decisions still needed before coding:
  - finalize release boundary (`internal only` vs `developer handoff zip`);
  - confirm monthly tab expansion strategy after `T4.2026`;
  - confirm approval model for write actions to Sheet in production.

## 2. Project Name
DeviceTracker Android + Google Sheet Local-First

## 3. One-Sentence Summary
Build a stable Android app to search and manage abnormal equipment records by `ma_thiet_bi`, work offline first, then sync safely to Google Sheet.

## 4. Problem to Solve
- Current situation: operational data is tracked in multiple Google Sheet tabs and is hard to use quickly on phone.
- Pain point: hard to search by device code, hard to separate yearly/monthly/repair datasets, and easy to miss sync/state errors.
- Who is affected: field staff, technicians, and internal supervisors.
- Why this matters: slower maintenance response, inconsistent records, and unstable reporting.

## 5. Target Users
| User type | What they need | Skill level | Device used |
|---|---|---|---|
| Field operator | fast lookup and update by `ma_thiet_bi` | Basic | Android phone |
| Technician | update repair progress and dates | Basic | Android phone |
| Supervisor | monitor sync/data quality and status | Basic/Advanced | Android phone |

## 6. Recommended Project Type
```text
Recommended type: Mobile app (Android) + Google Sheet backend + Offline-first data entry
Primary planning module: mobile_app_planning.md
Supporting planning modules: data_entry_system_planning.md, offline_first_planning.md, google_sheet_backend_planning.md, internal_business_app_planning.md, release_handoff_safety_planning.md
Why this type fits: existing workflow is phone-first, Google Sheet already exists, offline is required, and business rules are already defined.
Why other types are weaker: web-only fails field usage; multi-platform now increases risk/scope significantly.
Assumptions: Android remains priority through MVP; Google Sheet ownership is stable.
Need confirmation: release boundary and monthly scaling strategy.
```

## 7. Requirements Map
| Requirement ID | Type | Requirement | Source | Status | Risk if unclear |
|---|---|---|---|---|---|
| REQ-001 | Business rule | Search centered on `ma_thiet_bi` | AGENTS.md | Locked | High |
| REQ-002 | Business rule | `record_id` unique per record, no STT as ID | AGENTS.md | Locked | High |
| REQ-003 | Business rule | repair status derived only from `ngay_sua_chua` | AGENTS.md | Locked | High |
| REQ-004 | Data scope | 4 zones: yearly DMBT, monthly DMBT, monthly repair, HGT periodic | User confirmed | Locked | High |
| REQ-005 | Offline | local-first save and delayed sync | AGENTS.md + user | Locked | High |
| REQ-006 | UX | sidebar must clearly separate zone 1/2/3/4 | User confirmed | Locked | Medium |
| REQ-007 | Validation | date format `dd/MM/yyyy` for user-facing dates | User confirmed | Locked | Medium |
| REQ-008 | Governance | plan/risk/gates must be tightened before further expansion | User request 2026-04-26 | In progress | High |

## 8. MVP Scope
### Must-have now
1. Search and filter by `ma_thiet_bi` + repair state (`all/repaired/pending`).
2. Clear zone separation in sidebar for Khu 1/2/3/4.
3. Add/edit core records with required validations.
4. HGT latest-check editable and next-check auto-calculated.
5. Local-first persistence + sync queue visibility.

### Should-have after MVP
1. Better conflict UI and per-record sync diagnostics.
2. Monthly zone auto-discovery for new tabs (not hardcoded T4.2026).
3. Expanded real-device automated regression checks.

### Nice-to-have later
1. Dashboard/reporting view for supervisor.
2. Lightweight export package for management reports.

### Do not build yet
1. iOS version, web admin, and desktop app in same phase because scope risk is high.
2. Autonomous MCP write/delete actions without explicit human approval.

## 9. Main Data Objects
| Data object | Purpose | Main fields | Who creates | Who views | Sensitive? |
|---|---|---|---|---|---|
| DeviceLog | abnormal equipment record | `record_id`, `ma_thiet_bi`, `hang_muc`, `ngay_phat_hien`, `ngay_sua_chua`, `ghi_chu` | operator/technician | all users | No (internal operational) |
| HgtCheck | periodic gearbox check | `id`, `ma_thiet_bi`, `chu_ky_ngay`, `lan_gan_nhat`, `lan_tiep_theo` | technician | all users | No (internal operational) |
| SyncQueue | deferred sync actions | `queue_id`, `record_id`, `action`, `retry_count`, `error` | system | supervisor | No |
| SheetMappingConfig | map role to `sheetId` | `role`, `sheet_id`, `schema_version` | system/admin | supervisor | Medium |

## 10. Data Model and Validation Rules
| Field | Type | Required | Allowed values | Auto-fill | Validation rule | Error message |
|---|---|---|---|---|---|---|
| `record_id` | Text | Yes | unique | Yes | must not duplicate | `Mã bản ghi bị trùng` |
| `ma_thiet_bi` | Text | Yes | non-empty | No | trim + non-empty | `Mã thiết bị là bắt buộc` |
| `ngay_phat_hien` | Date | Yes | `dd/MM/yyyy` | No | valid date format | `Ngày phát hiện phải đúng dd/MM/yyyy` |
| `ngay_sua_chua` | Date | Conditional | empty or `dd/MM/yyyy` | No | if non-empty, must be valid | `Ngày sửa chữa phải đúng dd/MM/yyyy` |
| `repair_status` | Derived | Yes | `da_sua`/`chua_sua` | Yes | derive from `ngay_sua_chua` only | `Trạng thái được tự động suy ra` |
| `chu_ky_ngay` | Number | Yes (HGT) | >0 | No | positive integer | `Chu kỳ phải là số dương` |
| `lan_gan_nhat` | Date | Yes (HGT) | `dd/MM/yyyy` | No | valid date | `Lần gần nhất phải đúng dd/MM/yyyy` |
| `lan_tiep_theo` | Date | Yes (HGT) | calculated | Yes | `lan_gan_nhat + chu_ky_ngay` | `Lần tiếp theo được tính tự động` |

## 11. Main Screens / Tools
| Screen | User | Purpose | Main actions |
|---|---|---|---|
| Search/Home | all users | search/filter/list abnormal records | search, filter state, open detail |
| Sidebar Zone Navigator | all users | separate Khu 1/2/3/4 clearly | choose yearly/monthly/repair/HGT |
| Edit Record | operator/technician | create record | input, validate, save local |
| Detail / Repair Date | technician | update repair date safely | edit date, derive status |
| HGT Screen | technician | periodic check management | search device, edit latest date |
| Sync Status | supervisor | monitor queue and failures | refresh, retry/sync now |

## 12. Main Workflows
### Workflow 1 — Search and filter by device
1. User enters `ma_thiet_bi`.
2. App filters local DB immediately.
3. User applies state filter and zone filter.
Result: user sees correct records without waiting network.
Failure cases: empty result, malformed search text.

### Workflow 2 — Add/update record offline then sync
1. User saves record locally.
2. Record marked `pending_sync`.
3. Sync worker sends when online.
Result: data survives offline and syncs later.
Failure cases: schema mismatch, auth issue, network timeout.

### Workflow 3 — HGT periodic update
1. User edits `lan_gan_nhat`.
2. App validates date and recalculates `lan_tiep_theo`.
3. Save local and queue sync.
Result: schedule remains consistent.
Failure cases: invalid date format, sync failed.

## 13. Acceptance Criteria
Feature: search/filter by device and state  
Acceptance Criteria:
- Search by `ma_thiet_bi` returns matching records from local DB.
- Filter `all/repaired/pending` follows `ngay_sua_chua` rule only.
- Zone switch updates list scope correctly.

Feature: 4-zone sidebar navigation  
Acceptance Criteria:
- Sidebar displays Khu 1, Khu 2, Khu 3, Khu 4 clearly.
- Khu 1 includes yearly DMBT options.
- Khu 2 and Khu 3 show monthly tabs currently in scope.
- Khu 4 opens HGT screen.

Feature: offline-first save + sync queue  
Acceptance Criteria:
- User can save/update records without internet.
- Pending sync state is visible.
- No local data loss after app restart.

Feature: HGT edit and auto-calc  
Acceptance Criteria:
- Editing `lan_gan_nhat` updates `lan_tiep_theo` automatically.
- Invalid date input is blocked with clear error.

## 14. Failure Scenarios & Recovery
| Failure Scenario | Impact | User-visible message | Recovery plan | Data loss risk |
|---|---|---|---|---|
| Google Sheet schema changed | sync fails | `Cấu trúc Sheet thay đổi` | stop sync, require remap by `sheetId` config | Low |
| Network unstable | delayed sync | `Đang chờ đồng bộ` | retry with backoff | Low |
| Duplicate `record_id` | incorrect write | `Mã bản ghi bị trùng` | block save, regenerate ID | Low |
| Build environment lock in dev | release delay | internal tooling alert | root-cause fix + clean CI path | None for end users |
| Invalid date | wrong status/schedule | format-specific error | enforce validation before save | Low |

## 15. Roles and Permissions
| Role | View | Add | Edit | Delete | Export | Configure |
|---|---|---|---|---|---|---|
| Operator | Yes | Yes | Limited | No | No | No |
| Technician | Yes | Yes | Yes | No | No | No |
| Supervisor | Yes | Optional | Optional | No | Optional | Yes (sync config) |

## 16. Offline / Sync / Device Needs
- Offline needed: Yes
- Sync needed: Yes
- Camera/GPS/notification needed: Not required in MVP
- Local file/printer/scanner needed: No
- AI tool permissions needed: No autonomous write in MVP

| State | Meaning | User-visible? | Recovery/next action |
|---|---|---|---|
| draft | input started, not saved | Optional | save or cancel |
| saved_local | saved on device | Yes | wait sync |
| pending_sync | queued for sync | Yes | auto/manual sync |
| syncing | in progress | Yes | wait |
| partially_synced | partial success | Yes | retry failed segment |
| synced | sync successful | Yes | done |
| sync_failed | sync error | Yes | retry/fix config |
| conflict | local/remote mismatch | Yes | manual resolve |

For file/image data (future):
```text
data_sync_status:
image_sync_status:
```

## 17. Storage / Backend / Integration Plan
- Current storage choice: Room local DB + WorkManager sync queue.
- External systems: Google Sheet (data), optional Google Drive (future attachments).
- Google Sheet plan if relevant: role-based mapping via `sheetId`, not tab order/name.
- API/integration needs: Google Sheets API with safe retry and clear fail states.
- Backup plan: periodic export/backup of Sheet owner account.
- Migration triggers: schema change, scale limits, permission complexity growth.

## 18. Security and Privacy Planning
- Sensitive data: internal operations data, device notes.
- Authentication needed: internal controlled access (MVP can defer full auth if internal-only device use).
- Authorization needed: minimal role boundaries.
- Logging/audit needed: sync attempts, validation failures, critical actions.
- Dangerous actions needing confirmation: destructive sync overrides, bulk edits.
- Secrets/API keys handling rule: never in repo/worklog/screenshots.
- Data backup/restore need: required before production rollout.

## 19. Threat Model Lite
- Assets to protect: local DB integrity, sheet correctness, sync credentials.
- Most likely risks: wrong sheet mapping, credential leakage, duplicate sync writes.
- Simple controls in MVP: strict validation, explicit sync states, least privilege account usage.
- Risks deferred: full enterprise IAM and advanced audit trails.

## 20. Assumptions
| ID | Assumption | Why assumed | Needs confirmation? |
|---|---|---|---|
| ASM-001 | Android remains primary platform for MVP | current usage pattern | No |
| ASM-002 | Current Google Sheet owner remains available | existing workflow depends on it | Yes |
| ASM-003 | Monthly tab naming remains similar short-term | current dataset shape | Yes |
| ASM-004 | Internal deployment first | user prioritizes stability/safety | Yes |

## 21. Open Questions
| ID | Question | Blocks implementation? | Priority |
|---|---|---|---|
| OQ-001 | What is exact release boundary: internal-only or developer handoff zip? | Yes | High |
| OQ-002 | How to handle new monthly tabs after T4.2026 automatically? | No | Medium |
| OQ-003 | Should supervisor role edit data or view-only? | No | Medium |

## 22. Release / Handoff Safety
- Target boundary: internal (default) until explicit handoff approval.
- Allowed to share: sanitized source and docs.
- Must not share: real secrets, local DB dumps, unsanitized logs/uploads.
- Logs/uploads/local database handling: sanitize or exclude by default.
- Secret scan expectation before zip/share/deploy/handoff: mandatory.
- Final approval needed before sharing: Yes, explicit owner approval.

## 23. Handoff Quality Score
| Area | Score 0-100 | Notes |
|---|---:|---|
| Goal clarity | 95 | clear and stable |
| User flow clarity | 90 | core flows listed |
| MVP scope clarity | 92 | strict must/should/later split |
| Data model clarity | 92 | rules and objects explicit |
| Data validation clarity | 94 | field-level constraints defined |
| Acceptance criteria clarity | 90 | per-feature criteria present |
| Offline/sync clarity | 93 | states and recovery defined |
| Security/privacy clarity | 88 | good baseline, auth finalization pending |
| Failure recovery clarity | 90 | scenario table complete |
| Release/handoff safety clarity | 88 | needs boundary confirmation |
| Open questions risk | Medium | one blocking question remains |

Final status:
```text
PLAN_DRAFT_READY
```

