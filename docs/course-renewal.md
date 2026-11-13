# Course expiry renewal

POST /api/course-purchases/{id}/renewals accepts patientId (purchaser), expectedExpiryDate, newExpiryDate (YYYY-MM-DD), and a required reason up to 1000 characters.
GET on the same URL returns old/new expiry, reason, actor user ID and timestamp.

Backend requires a database Manager role and an open course branch. Therapist renewal is not enabled.
Only Active courses with course-wide remaining visits may be renewed, including expired Active courses.
The new expiry must be after both the old expiry and today in Asia/Bangkok.

A row lock serializes expiry edits with usage and cancellation. Stale forms return 409; replaying the committed target date creates no duplicate audit.
Existing course.expiry_date and audit_log (table_name=course_renewal) are updated in one transaction. No schema migration.
The shared course expiry changes for all holders. No entitlements, payment, receipt or commission changes are made.

Manager opens “ต่ออายุ / ประวัติ” from the owner's course menu. Saving refreshes courses; failure to refresh is reported separately from renewal success.
Comments explain locking, retry handling and audit isolation from financial ledgers.

Verification: backend compile and frontend type check. No database integration, concurrent request tests or real browser workflow have been run.
