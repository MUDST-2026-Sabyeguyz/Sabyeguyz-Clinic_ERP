-- Keep the existing branch ID, code and numeric code so patient references and HNs remain valid.
UPDATE branches
SET name = 'พระราม 9', active = TRUE
WHERE code = 'RP' AND name IN ('สาขาราชพฤกษ์', 'ราชพฤกษ์');

INSERT INTO branches (code, numeric_code, name, active)
SELECT 'BR', '02', 'แบริ่ง', TRUE
WHERE NOT EXISTS (SELECT 1 FROM branches WHERE code = 'BR');
