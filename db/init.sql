CREATE DATABASE IF NOT EXISTS mcpdb;
USE mcpdb;

CREATE TABLE IF NOT EXISTS employees (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    department VARCHAR(50) NOT NULL,
    role VARCHAR(50) NOT NULL,
    joined_date DATE NOT NULL
);

INSERT INTO employees (name, department, role, joined_date) VALUES
    ('Arjun Kumar', 'Engineering', 'Backend Developer', '2023-06-01'),
    ('Priya Raman', 'Engineering', 'Frontend Developer', '2022-11-15'),
    ('Karthik S', 'Engineering', 'DevOps Engineer', '2021-04-20'),
    ('Divya Menon', 'Sales', 'Account Executive', '2023-01-10'),
    ('Vignesh R', 'Support', 'Support Engineer', '2024-02-05');

-- Dedicated read-only user for the MCP server's query_database tool.
-- Never point the MCP server at a user with write access.
CREATE USER IF NOT EXISTS 'mcp_readonly'@'%' IDENTIFIED BY 'readonly123';
GRANT SELECT ON mcpdb.* TO 'mcp_readonly'@'%';
FLUSH PRIVILEGES;
