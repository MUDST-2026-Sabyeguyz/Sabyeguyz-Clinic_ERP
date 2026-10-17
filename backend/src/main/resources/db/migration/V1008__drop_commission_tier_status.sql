-- Agreed (settings of the commission rule and the treatment fee): a tier is not switched on or off apart from its
-- Scheme version. The state of a version comes from its dates (waiting, in force, ended), so the column goes.
ALTER TABLE commission_tier DROP COLUMN tier_status;
