package com.example.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.logging.Logger;

public class MigrationExecutor {

    private static final Logger LOGGER = Logger.getLogger(MigrationExecutor.class.getName());

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public MigrationExecutor(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public void executeMigration(String scriptPath) throws Exception {
        LOGGER.info("Demarrage de la migration de base de donnees via le script : " + scriptPath);
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             InputStream is = getClass().getClassLoader().getResourceAsStream(scriptPath)) {

            if (is == null) {
                LOGGER.warning("Script introuvable dans le classpath : " + scriptPath);
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                 Statement stmt = conn.createStatement()) {

                StringBuilder sql = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("--")) {
                        continue;
                    }
                    sql.append(line).append(" ");
                    if (line.endsWith(";")) {
                        stmt.execute(sql.toString());
                        sql.setLength(0);
                    }
                }
                if (sql.length() > 0) {
                    stmt.execute(sql.toString());
                }
            }
            LOGGER.info("Migration appliquee avec succes !");
        }
    }
}
