package com.earthpol.betterDiscordSRV.util;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class SQLManager {

    private static Connection connection;

    private static AsyncScheduler asyncScheduler;

    // Cache mapping linking code to player's username.
    private static final Map<String, String> codeUsernameCache = new ConcurrentHashMap<>();

    public static void initialize(JavaPlugin plugin) {
        String host = plugin.getConfig().getString("database.host");
        int port = plugin.getConfig().getInt("database.port");
        String user = plugin.getConfig().getString("database.user");
        String password = plugin.getConfig().getString("database.password");
        String database = plugin.getConfig().getString("database.database");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false";

        asyncScheduler = plugin.getServer().getAsyncScheduler();

        try {
            connection = DriverManager.getConnection(url, user, password);
            plugin.getLogger().info("✔️ Database connected successfully");

            // Create necessary tables if they don't exist.
            createTables(plugin);

        } catch (SQLException e) {
            plugin.getLogger().severe("❌ Fatal: cannot connect to database: " + e.getMessage());
            // this will prevent any async tasks from ever running against a null connection
            plugin.getServer().getPluginManager().disablePlugin(plugin);
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

        // Create notification table for pending link completion notifications.
        String createNotificationTable = "CREATE TABLE IF NOT EXISTS discord_notification (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "discord VARCHAR(32) NOT NULL," +
                "uuid VARCHAR(36) NOT NULL," +
                "mc_username VARCHAR(50) NOT NULL," +
                "discord_username VARCHAR(64) NOT NULL," +
                "timestamp BIGINT NOT NULL" +
                ");";

        String createHistoryTable = "CREATE TABLE IF NOT EXISTS discord_accounts_history ("
                + "  id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "  discord_id VARCHAR(255) NOT NULL,"
                + "  uuid VARCHAR(255) NOT NULL,"
                + "  linked_at TIMESTAMP NOT NULL"
                + ");";

        stmt.executeUpdate(createNotificationTable);
        stmt.executeUpdate(createCodesTable);
        stmt.executeUpdate(createAccountsTable);
        stmt.executeUpdate(createHistoryTable);
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

        asyncScheduler.runNow(JavaPlugin.getProvidingPlugin(SQLManager.class), task -> {
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

        asyncScheduler.runNow(JavaPlugin.getProvidingPlugin(SQLManager.class), task -> {
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
        asyncScheduler.runNow(JavaPlugin.getProvidingPlugin(SQLManager.class), task -> {
            try {
                connection.setAutoCommit(false);

                // 1) Archive old link (if any) into history
                String backupSql =
                        "INSERT INTO discord_accounts_history (discord, uuid, linked_at) "
                                + "SELECT discord, uuid, CURRENT_TIMESTAMP "
                                + "  FROM discord_accounts "
                                + " WHERE discord = ? OR uuid = ?";
                try (PreparedStatement ps = connection.prepareStatement(backupSql)) {
                    ps.setString(1, discordId);
                    ps.setString(2, uuid);
                    ps.executeUpdate();
                }

                // 2) Delete the old row so we can re-insert
                String deleteSql =
                        "DELETE FROM discord_accounts "
                                + " WHERE discord = ? OR uuid = ?";
                try (PreparedStatement ps = connection.prepareStatement(deleteSql)) {
                    ps.setString(1, discordId);
                    ps.setString(2, uuid);
                    ps.executeUpdate();
                }

                // 3) Insert the fresh link
                String insertSql =
                        "INSERT INTO discord_accounts (discord, uuid) VALUES (?, ?)";
                try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                    ps.setString(1, discordId);
                    ps.setString(2, uuid);
                    ps.executeUpdate();
                }

                connection.commit();
            } catch (SQLException e) {
                try { connection.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
                e.printStackTrace();
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    // Check asynchronously if a Minecraft UUID is already linked.
    public static void isAlreadyLinked(String uuid, Consumer<Boolean> callback) {
        asyncScheduler.runNow(JavaPlugin.getProvidingPlugin(SQLManager.class), task -> {
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT * FROM discord_accounts WHERE uuid = ?");
                ps.setString(1, uuid);
                ResultSet rs = ps.executeQuery();
                boolean exists = rs.next();
                rs.close();
                ps.close();
                asyncScheduler.runNow(JavaPlugin.getProvidingPlugin(SQLManager.class), task1 -> {
                    callback.accept(exists);
                });
            } catch (SQLException e) {
                asyncScheduler.runNow(JavaPlugin.getProvidingPlugin(SQLManager.class), task1 -> {
                    callback.accept(false);
                });
            }
        });
    }

    // Query the discord_accounts table based on a given column (either "discord" or "uuid")
    // and return a result string via callback.
    public static void queryLinkedAccount(String column, String value, Consumer<String> callback) {
        asyncScheduler.runNow(JavaPlugin.getProvidingPlugin(SQLManager.class), task -> {
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

            asyncScheduler.runNow(JavaPlugin.getProvidingPlugin(SQLManager.class), task1 -> {
                callback.accept(finalResult);
            });
        });
    }

    public static class Notification {
        private final int id;
        private final String discord;
        private final String uuid;
        private final String mcUsername;
        private final String discordUsername;

        public Notification(int id, String discord, String uuid, String mcUsername, String discordUsername) {
            this.id = id;
            this.discord = discord;
            this.uuid = uuid;
            this.mcUsername = mcUsername;
            this.discordUsername = discordUsername;
        }

        public int getId() { return id; }
        public String getDiscord() { return discord; }
        public String getUuid() { return uuid; }
        public String getMcUsername() { return mcUsername; }
        public String getDiscordUsername() { return discordUsername; }
    }

    // Insert a notification into the table.
    public static void insertDiscordNotification(String discord, String uuid, String mcUsername, String discordUsername) {

        asyncScheduler.runNow(JavaPlugin.getProvidingPlugin(SQLManager.class), task -> {
            String query = "INSERT INTO discord_notification (discord, uuid, mc_username, discord_username, timestamp) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(query)) {
                ps.setString(1, discord);
                ps.setString(2, uuid);
                ps.setString(3, mcUsername);
                ps.setString(4, discordUsername);
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    // Get pending notifications using a callback that receives a list of Notification objects.
    public static void getPendingNotifications(Consumer<List<Notification>> callback) {

        asyncScheduler.runNow(JavaPlugin.getProvidingPlugin(SQLManager.class), task -> {
                List<Notification> notifications = new ArrayList<>();
                try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM discord_notification")) {
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String discord = rs.getString("discord");
                        String uuid = rs.getString("uuid");
                        String mcUsername = rs.getString("mc_username");
                        String discordUsername = rs.getString("discord_username");
                        notifications.add(new Notification(id, discord, uuid, mcUsername, discordUsername));
                    }
                    rs.close();
                } catch(SQLException e) {
                    e.printStackTrace();
                }

                asyncScheduler.runNow(JavaPlugin.getProvidingPlugin(SQLManager.class), task1 -> {
                    callback.accept(notifications);
                });
        });
    }

    // Delete a notification by its ID.
    public static void deleteNotification(int id) {
        asyncScheduler.runNow(JavaPlugin.getProvidingPlugin(SQLManager.class), task -> {
            String query = "DELETE FROM discord_notification WHERE id = ?";
            try (PreparedStatement ps = connection.prepareStatement(query)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public static void getLinkedDiscordByMinecraft(String uuid, Consumer<Optional<String>> callback) {
        asyncScheduler.runNow(JavaPlugin.getProvidingPlugin(SQLManager.class), task -> {
            Optional<String> result = Optional.empty();
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT discord FROM discord_accounts WHERE uuid = ?");
                ps.setString(1, uuid);
                ResultSet rs = ps.executeQuery();
                if(rs.next()){
                    result = Optional.of(rs.getString("discord"));
                }
                rs.close();
                ps.close();
            } catch(SQLException e) {
                e.printStackTrace();
            }
            // Switch back to main thread:
            Optional<String> finalResult = result;
            asyncScheduler.runNow(JavaPlugin.getProvidingPlugin(SQLManager.class), task1 -> {
                        callback.accept(finalResult);
                    });
        });
    }

    // Query: For a given Discord ID, get the linked Minecraft UUID (if any).
    public static void getLinkedMinecraftByDiscord(String discord, Consumer<Optional<String>> callback) {
        asyncScheduler.runNow(JavaPlugin.getProvidingPlugin(SQLManager.class), task -> {
            Optional<String> result = Optional.empty();
            try {
                PreparedStatement ps = connection.prepareStatement("SELECT uuid FROM discord_accounts WHERE discord = ?");
                ps.setString(1, discord);
                ResultSet rs = ps.executeQuery();
                if(rs.next()){
                    result = Optional.of(rs.getString("uuid"));
                }
                rs.close();
                ps.close();
            } catch(SQLException e) {
                e.printStackTrace();
            }
            Optional<String> finalResult = result;
            asyncScheduler.runNow(JavaPlugin.getProvidingPlugin(SQLManager.class), task1 -> {
                callback.accept(finalResult);
            });
        });
    }
}
