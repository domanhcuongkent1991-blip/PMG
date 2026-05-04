# PLAN GOVERNANCE GATES (PROJECT-SPECIFIC)

## Muc dich
Bien bo gate trong PLAN-WORKFLOW V3.4 thanh checklist van hanh co the dung ngay cho du an Android + Google Sheet.

## Gate board
| Gate | Dieu kien pass | Evidence bat buoc | Trang thai hien tai |
|---|---|---|---|
| G1 Goal clarity | Muc tieu MVP ro, 1 platform chinh | `PROJECT_PLAN_TIGHTENED_V3_4_2026-04-26.md` section 1-4 | PASS |
| G2 User clarity | User/role/device ro | plan section 5, 15 | PASS |
| G3 Scope clarity | Must/Should/Later/Not-in-MVP ro | plan section 8 | PASS |
| G4 Data clarity | Data objects + validation rules ro | plan section 9, 10 | PASS |
| G5 Flow clarity | Workflows + failure cases ro | plan section 12, 14 | PASS |
| G6 Platform fit | Ly do chon mobile + options reject ro | plan section 6 | PASS |
| G7 Module coverage | module planning da khai bao day du | plan section 6 | PASS |
| G8 Risk review | Khong con High risk mo | risk table + RCA log | NEED_ACTION |
| G9 Non-tech readability | Tai lieu doc duoc cho non-tech | summary + test plan da don gian hoa | PASS |
| G10 Handoff readiness | Coding workflow khong phai doan yeu cau | plan section 23 + open questions | NEED_ACTION |

## Hai diem phai dong ngay truoc khi goi PLAN_REVIEW_READY
1. Dong High risk lap lai (build-lock/RCA) bang evidence root-cause fix.
2. Chot OQ-001: release boundary (`internal only` hay `developer handoff zip`).

## Rule van hanh
1. Task lon khong duoc mo neu G1-G7 chua pass.
2. Task khong duoc dong neu G8/G10 dang `NEED_ACTION`.
3. Neu loi lap lai lan 2 trong 48h -> kich hoat stop-the-line + RCA bat buoc.

## Target status
Hien tai: `PLAN_DRAFT_READY`  
Muc tieu sau khi dong blocker: `PLAN_REVIEW_READY`

