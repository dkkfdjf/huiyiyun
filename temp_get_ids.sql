-- 获取 doctor01 对应的正确 ID
SELECT u.id AS user_id, u.username, d.id AS doctor_id, d.name, d.institution_id
FROM `user` u
LEFT JOIN `doctor` d ON d.user_id = u.id
WHERE u.username = 'doctor01';

-- 获取药品 ID
SELECT id, name FROM drug ORDER BY id;
