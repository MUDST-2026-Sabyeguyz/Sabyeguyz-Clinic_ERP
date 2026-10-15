# Pending commission migration

`V3__implement_commission_requirements.sql` came from `feat/database` (commit `28fdb46`).
It is preserved here unchanged while the v2 runtime is aligned with its schema.

The active Flyway directory already contains `V3__reference_data.sql`. The new
migration also removes columns and enum types still mapped by `clinicv2`, so it
must not be moved into `db/migration` by renaming it alone. Before activation,
assign the next available Flyway version, update the affected entities and
services, and verify migration and runtime behavior on PostgreSQL.
