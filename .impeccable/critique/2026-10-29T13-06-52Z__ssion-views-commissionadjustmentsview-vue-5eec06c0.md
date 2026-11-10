---
target: รายการปรับปรุงค่าตอบแทน
total_score: 25
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
target_identity: "file:D:\\3_y3\\Sabyeguyz-Develop\\frontend\\src\\features\\commission\\views\\CommissionAdjustmentsView.vue"
target_fingerprint: "sha256:0959805a4cb85533a84c9da383895d2d8502f05149aa821b2a085f588ebeaff6"
target_path: "D:\\3_y3\\Sabyeguyz-Develop\\frontend\\src\\features\\commission\\views\\CommissionAdjustmentsView.vue"
timestamp: 2026-10-29T13-06-52Z
slug: ssion-views-commissionadjustmentsview-vue-5eec06c0
---
# รายการปรับปรุงค่าตอบแทน — critique
Method: dual-agent (A: /root/adjustments_review_a · B: /root/adjustments_review_b)
Score: 25/40. Nielsen: status 3, real world 3, control 3, consistency 3, prevention 2, recognition 2, efficiency 2, minimalist 2, recovery 3, help 2.
Strengths: Thai status labels and six pastel colors; filters and reset; pagination.
[P1] Sorting claims unfinished first but moves only Applied to end, leaving Cancelled above action-needed records. Group actionable statuses first.
[P1] Repeated rows share staff/course/month/amount. Show adjustment ID or brief reason to distinguish before opening detail.
[P2] Cancelled rows show original positive amount without historical context. Label amount as original for cancelled entries.
[P2] Period-status column repeats 'ปิดรอบแล้ว' in every row, spending space that could identify records.
[P2] 920px minimum table uses horizontal scroll and sticky ends on narrow screens; verify and adapt to compact cards if necessary.
Cognitive load: repeated rows force detail-page memory. Manager sees cancelled item before pending; novice may read cancelled +฿100 as payable.
Detector: [] (0). Live browser redirected to login. User screenshots and source used.
