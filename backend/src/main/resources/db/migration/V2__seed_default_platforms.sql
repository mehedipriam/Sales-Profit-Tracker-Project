-- Existing workspaces get the two starting platforms; new workspaces are seeded at registration.
INSERT INTO platforms (tenant_id, name)
SELECT t.id, p.name
FROM tenants t
CROSS JOIN (SELECT 'Facebook Page' AS name UNION ALL SELECT 'Daraz') p
WHERE NOT EXISTS (SELECT 1 FROM platforms x WHERE x.tenant_id = t.id AND x.name = p.name);
