package com.earthpol.betterDiscordSRV.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class SQLManager {

    private static Connection connection;

    // Cache mapping linking code to player's username.
    private static final Map<String, String> codeUsernameCache = new ConcurrentHashMap<>();

    public static void initialize(JavaPlugin plugin) {
        String host = plugin.getConfig().getString("database.host");
        int port = plugin.getConfig().getInt("database.port");
        String user = plugin.getConfig().getString("database.user");
        String password = plugin.getConfig().getString("database.password");
        String database = plugin.getConfig().getString("database.database");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false";

        try {
            connection = DriverManager.getConnection(url, user, password);
            plugin.getLogger().info("Database connected successfully!");

            // Create necessary tables if they don't exist.
            createTables(plugin);

        } catch (SQLException e) {
            plugin.getLogger().severe("Could not connect to the database: " + e.getMessage());
        }
    }

    private static void createTables(JavaPlugin plugin) throws SQLException {
        Statement stmt = connection.createStatement();

        // Enforce unique uuid for pending codes.
        String createCodesTable = "CREATE TABLE IF NOT EXISTS discord_codes (" +
                "code CHAR(6) PRIMARY KEY," +
                "uuid VARCHAR(36) NOT NULL UNIQUE," +
                "expiration BIGINT NOT NULL" +
                ");";

        // Ensure that each discord id and each minecraft uuid appears only once.
        String createAccountsTable = "CREATE TABLE IF NOT EXISTS discord_accounts (" +
                "link INT AUTO_INCREMENT PRIMARY KEY," +
                "discord VARCHAR(32) NOT NULL UNIQUE," +
                "uuid VARCHAR(36) NOT NULL UNIQUE" +
                ");";

        stmt.executeUpdate(createCodesTable);
        stmt.executeUpdate(createAccountsTable);
        stmt.close();

        plugin.getLogger().info("Verified/created required SQL tables.");
    }

    public static Connection getConnection() {
        return connection;
    }

    // Cache functions
    public static void cacheCode(String code, String username) {
        codeUsernameCache.put(code, username);
    }

    public static String getCachedUsername(String code) {
        return codeUsernameCache.get(code);
    }

    public static void removeCachedCode(String code) {
        codeUsernameCache.remove(code);
    }

    public static void insertDiscordCode(String code, String uuid, long expiration) {
        Bukkit.getScheduler().runTaskAsynchronously(JavaPlugin.getProvidingPlugin(SQLManager.class), () -> {
            String query = "INSERT INTO discord_codes (code, uuid, expiration) VALUES (?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(query)) {
                ps.setString(1, code);
                ps.setString(2, uuid);
                ps.setLong(3, expiration);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    // Only return the code if it has not expired.
    public static ResultSet getDiscordCode(String code) {
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT * FROM discord_codes WHERE code = ? AND expiration > ?");
            ps.setString(1, code);
            ps.setLong(2, System.currentTimeMillis());
            return ps.executeQuery();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Delete the code so it can't be reused.
    public static void deleteDiscordCode(String code) {
        Bukkit.getScheduler().runTaskAsynchronously(JavaPlugin.getProvidingPlugin(SQLManager.class), () -> {
            String query = "DELETE FROM discord_codes WHERE code = ?";
            try (PreparedStatement ps = connection.prepareStatement(query)) {
                ps.setString(1, code);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    // Insert the final linking into discord_accounts.
    public static void insertDiscordAccount(String discordId, String uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(JavaPlugin.getProvidingPlugin(SQLManager.class), () -> {
            String query = "INSERT INTO discord_accounts (discord, uuid) VALUES (?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(query)) {
                ps.setString(1, discordId);
                ps.setString(2, uuid);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    // Check asynchronously if a Minecraft UUID is already linked.
    public static void isAlreadyLinked(String uuid, Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(JavaPlugin.getProvidingPlugin(SQLManager.class), () -> {
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT * FROM discord_accounts WHERE uuid = ?");
                ps.setString(1, uuid);
                ResultSet rs = ps.executeQuery();
                boolean exists = rs.next();
                rs.close();
                ps.close();
                Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(SQLManager.class), () -> callback.accept(exists));
            } catch (SQLException e) {
                Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(SQLManager.class), () -> callback.accept(false));
            }
        });
    }

    // Query the discord_accounts table based on a given column (either "discord" or "uuid")
    // and return a result string via callback.
    public static void queryLinkedAccount(String column, String value, Consumer<String> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(JavaPlugin.getProvidingPlugin(SQLManager.class), () -> {
            String resultMessage = "";
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT * FROM discord_accounts WHERE " + column + " = ?");
                ps.setString(1, value);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String discord = rs.getString("discord");
                    String uuid = rs.getString("uuid");
                    resultMessage = "Linked: Discord ID: " + discord + " <-> Minecraft UUID: " + uuid;
                } else {
                    resultMessage = "No link found for " + column + " = " + value;
                }
                rs.close();
                ps.close();
            } catch(SQLException e) {
                resultMessage = "Database error: " + e.getMessage();
            }
            final String finalResult = resultMessage;
            Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(SQLManager.class), () -> callback.accept(finalResult));
        });
    }
}
