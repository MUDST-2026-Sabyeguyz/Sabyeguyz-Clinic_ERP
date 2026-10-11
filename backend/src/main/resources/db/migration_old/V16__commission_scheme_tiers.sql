CREATE SEQUENCE commission_scheme_id_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE commission_schemes (
    id BIGINT DEFAULT nextval('commission_scheme_id_seq') PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    version VARCHAR(40) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_commission_scheme_version UNIQUE (name, version),
    CONSTRAINT chk_commission_scheme_dates CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE SEQUENCE commission_tier_id_seq START WITH 1 INCREMENT BY 1;
CREATE TABLE commission_tiers (
    id BIGINT DEFAULT nextval('commission_tier_id_seq') PRIMARY KEY,
    scheme_id BIGINT NOT NULL REFERENCES commission_schemes(id),
    minimum_sales NUMERIC(14,2) NOT NULL,
    maximum_sales NUMERIC(14,2),
    commission_rate NUMERIC(7,6) NOT NULL,
    tier_order INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_commission_tier_min CHECK (minimum_sales >= 0),
    CONSTRAINT chk_commission_tier_range CHECK (maximum_sales IS NULL OR maximum_sales >= minimum_sales),
    CONSTRAINT chk_commission_tier_rate CHECK (commission_rate >= 0 AND commission_rate <= 1),
    CONSTRAINT uq_commission_tier_order UNIQUE (scheme_id, tier_order)
);
CREATE INDEX idx_commission_schemes_effective ON commission_schemes(effective_from, effective_to);
CREATE INDEX idx_commission_tiers_scheme ON commission_tiers(scheme_id, tier_order);

INSERT INTO commission_schemes(name, version, effective_from, active)
VALUES ('DEFAULT', '1', DATE '2000-01-01', TRUE);
INSERT INTO commission_tiers(scheme_id, minimum_sales, maximum_sales, commission_rate, tier_order)
SELECT id, 0, 59999.99, 0.05, 1 FROM commission_schemes WHERE name = 'DEFAULT' AND version = '1';
INSERT INTO commission_tiers(scheme_id, minimum_sales, maximum_sales, commission_rate, tier_order)
SELECT id, 60000, 69999.99, 0.06, 2 FROM commission_schemes WHERE name = 'DEFAULT' AND version = '1';
INSERT INTO commission_tiers(scheme_id, minimum_sales, maximum_sales, commission_rate, tier_order)
SELECT id, 70000, 79999.99, 0.07, 3 FROM commission_schemes WHERE name = 'DEFAULT' AND version = '1';
INSERT INTO commission_tiers(scheme_id, minimum_sales, maximum_sales, commission_rate, tier_order)
SELECT id, 80000, 89999.99, 0.08, 4 FROM commission_schemes WHERE name = 'DEFAULT' AND version = '1';
INSERT INTO commission_tiers(scheme_id, minimum_sales, maximum_sales, commission_rate, tier_order)
SELECT id, 90000, NULL, 0.10, 5 FROM commission_schemes WHERE name = 'DEFAULT' AND version = '1';
