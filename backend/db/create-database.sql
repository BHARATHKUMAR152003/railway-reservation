-- Run once as a MySQL administrator; replace the password.
CREATE DATABASE IF NOT EXISTS railway CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'rail_app'@'localhost' IDENTIFIED BY 'change-me';
CREATE USER IF NOT EXISTS 'rail_app'@'127.0.0.1' IDENTIFIED BY 'change-me';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX, REFERENCES ON railway.* TO 'rail_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX, REFERENCES ON railway.* TO 'rail_app'@'127.0.0.1';
