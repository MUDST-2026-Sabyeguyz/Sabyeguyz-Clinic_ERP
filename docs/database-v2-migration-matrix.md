# Database v2 Migration Matrix

This matrix maps every clinic ERP capability from legacy schema / JPA entities to the confirmed canonical Database v2 schema (`V1__initial_schema.sql`).

> **Status key:** ✅ Implemented with cited tests | ⚠️ Implemented; direct backend test pending

| Feature | API Endpoint | Database v2 Table(s) | Key v2 Columns | Read | Write | Auth | Backend Tests | Cypress Spec | Status |
|---|---|---|---|---|---|---|---|---|---|
| **Auth & Login** | `POST /api/auth/login` | `app_user`, `user_role`, `role` | `user_id`, `username`, `password_hash`, `user_status`, `last_login_at`, `role_id`, `role_name` | ✅ | ✅ | Public | `DatabaseV2AuthServiceTest` | `auth-flow.cy.js`, `auth-guard.cy.js` | ✅ Done |
| **Branch Management** | `GET, POST, PUT, DELETE /api/branches` | `branch` | `branch_id`, `branch_code`, `branch_name`, `address`, `phone`, `branch_status` | ✅ | ✅ | Manager (write) | No dedicated test | `backend-health.cy.js` (health only) | ⚠️ Direct test pending |
| **Staff & Therapists** | `GET, POST, PUT, DELETE /api/staff`, `/api/therapists` | `staff`, `staff_group`, `staff_group_member` | `staff_id`, `staff_code`, `staff_fname`, `staff_lname`, `position` enum, `branch_id`, `staff_status` | ✅ | ✅ | Manager (write) | No dedicated test | `appointment-lifecycle.cy.js` (indirect) | ⚠️ Direct test pending |
| **Master Data & Lookups** | `GET, POST, PUT, DELETE /api/master-data`, `/api/lookups` | `master_data_type`, `master_data_value`, `system_setting` | `type_id`, `type_name`, `value_id`, `value_name`, `value_status`, `sort_order` | ✅ | ✅ | Manager (write) | No dedicated test | `patient-lifecycle.cy.js` (indirect) | ⚠️ Direct test pending |
| **Service & Room Catalog** | `GET, POST, PUT, DELETE /api/services`, `/api/resources` | `service`, `service_group`, `service_group_member`, `room` | `service_id`, `service_type` enum, `service_name`, `service_price`, `room_id`, `room_name`, `room_type` | ✅ | ✅ | Manager (write) | No dedicated test | `appointment-lifecycle.cy.js` (indirect) | ⚠️ Direct test pending |
| **Payment Method** | `GET, POST, PUT, DELETE /api/payment-methods` | `payment_method` | `payment_method_id`, `payment_method_name`, `payment_method_status` | ✅ | ✅ | Manager (write) | No dedicated test | `sales-receipt-lifecycle.cy.js` (indirect) | ⚠️ Direct test pending |
| **Patient Registration & HN** | `GET, POST, PUT /api/patients`, `/api/patients/search` | `patient`, `hn_sequence` | `patient_id`, `hn` (YYBBMMNNNN), `customer_type`, `national_id`, `passport_no`, `gender`, `birthdate`, `branch_id` | ✅ | ✅ | Authenticated | `DatabaseV2PatientControllerTest`, `DatabaseV2HnPostgresIntegrationTest` | `patient-lifecycle.cy.js` | ✅ Done |
| **Appointments & Visit Lifecycle** | `GET, POST, PATCH /api/appointments`, check-in, start, complete, cancel, no-show | `appointment`, `appointment_status_history`, `patient_visit` | `appointment_id`, `appointment_status` enum, `visit_id`, `visit_status` enum, `room_id`, `from_status`, `to_status`, `changed_by` | ✅ | ✅ | Authenticated | `DatabaseV2AppointmentControllerTest`, `DatabaseV2AppointmentPostgresIntegrationTest` | `appointment-lifecycle.cy.js` | ✅ Done |
| **Sales, Receipts & Payments** | `POST /api/payments`, `GET /api/receipts/{id}`, `POST /api/payments/{id}/reverse` | `receipt`, `sales_transaction`, `payment_transaction`, `transaction_cancellation` | `receipt_no` (RE-xxxxxxx), `transaction_type`, `treating_staff_id`, `salesperson_staff_id`, `payment_status`, `cancelled_by` | ✅ | ✅ | Authenticated | `DatabaseV2SaleControllerTest`, `DatabaseV2SalePostgresIntegrationTest` | `sales-receipt-lifecycle.cy.js` | ✅ Done |
| **Course Ledger & Transfers** | `GET, POST /api/courses`, `/api/course-purchases`, `/api/course-usages`, `/api/course-transfers` | `course`, `course_shared_member`, `course_ledger`, `course_transfer`, `course_visit_usage` | `course_id`, `course_no`, `seller_staff_id`, `case_owner_staff_id`, `remaining_visits`, `visit_usage_id`, `qty_change`, `balance_after` | ✅ | ✅ | Authenticated | `DatabaseV2CourseControllerTest`, `DatabaseV2CoursePostgresIntegrationTest` | `course-lifecycle.cy.js` | ✅ Done |
| **Treatment Records** | `GET, POST /api/treatments` | `treatment`, `patient_visit`, `service`, `sales_transaction` | `treatment_id`, `visit_id`, `service_id`, `treating_staff_id`, `notes` | ✅ | ✅ | Authenticated | `DatabaseV2TreatmentControllerTest` | `appointment-lifecycle.cy.js` | ✅ Done |
| **Commission Schemes & Closing** | `GET, POST /api/commission/...`, `/api/commission/closings`, `/api/commission/treatment-fees` | `commission_scheme`, `commission_tier`, `treatment_fee_rule`, `monthly_commission_closing`, `commission_allocation_ledger` | `scheme_id`, `commission_rate`, `minimum_monthly_sales`, `closing_month`, `closing_status`, `allocation_id`, `visit_usage_id` | ✅ | ✅ | Manager (closing/rules) | `DatabaseV2CommissionControllerTest` | `commission-lifecycle.cy.js` | ✅ Done |
| **Audit Logs** | `GET /api/audit-logs` | `audit_log` | `audit_id`, `table_name`, `record_id`, `action` enum, `old_value`, `new_value`, `changed_by`, `changed_at` | ✅ | Internal | Manager | `DatabaseV2AuditControllerTest` | `reports-dashboard.cy.js` | ✅ Done |
| **Reports & Dashboard** | `GET /api/dashboard/summary`, `/api/reports/...` | `sales_transaction`, `receipt`, `payment_transaction`, `course`, `monthly_commission_closing`, `patient_visit`, `appointment` | Aggregated views, date/branch filters | ✅ | N/A | Manager (financial) | `DatabaseV2ReportControllerTest` | `reports-dashboard.cy.js` | ✅ Done |
| **Bootstrap Admin** | Startup opt-in runner | `app_user`, `role`, `user_role`, `staff`, `branch`, `user_branch_access` | All identity & access fields | N/A | Opt-in | Env flag only | Idempotent integration test | `auth-flow.cy.js` | ✅ Done |

---

## Constraints

- Schema source of truth: `backend/src/main/resources/db/migration_new/V1__initial_schema.sql` — **never modified**
- No compatibility views, no mock/fixture data, no disabled tests
- All v2 controllers annotated `@Profile("database-v2")`; legacy annotated `@Profile("!database-v2")`
- Cypress specs under `frontend/cypress/specs/` only (no `e2e` folders)
- Bootstrap opt-in: `APP_BOOTSTRAP_ENABLED=true` with `APP_BOOTSTRAP_ADMIN_PASSWORD` supplied from the environment; an existing admin password is rotated to the supplied value.
