# Development seed data

Development/demo data belongs in this directory and must not be applied to production.

The existing `V20`–`V25` files remain under `db/migration` because they are already part
of the committed Flyway history on the `develop` baseline. Removing or renumbering them
would break databases that have recorded those versions. A future clean baseline may
move their data here after a documented database migration plan.
