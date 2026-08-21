package it.heron.hpet.modules;

import it.heron.hpet.database.*;
import it.heron.hpet.modules.abstracts.DefaultInstanceModule;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Locale;
import java.util.logging.Level;

public class DatabaseModule extends DefaultInstanceModule {

    @Getter
    private AbstractDatabase database;

    public DatabaseModule(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "Database";
    }

    @Override
    protected void onLoad() {
        loadDatabase();
    }

    @Override
    protected void onUnload() {
        unloadDatabase();
    }


    private Dictionary<String, AbstractDatabase> databaseDictionary() {
        Dictionary<String, AbstractDatabase> databaseDictionary = new Hashtable<>();
        databaseDictionary.put("mysql", new MySQLDatabase(plugin));
        databaseDictionary.put("mariadb", new MariaDBDatabase(plugin));
        databaseDictionary.put("postgre", new PostgreSQLDatabase(plugin));
        databaseDictionary.put("sqlite", new SQLiteDatabase(plugin));
        return databaseDictionary;
    }

    private void loadDatabase() {
        String configuredType = plugin.getConfig().getString("database.type");
        if (configuredType == null && plugin.getConfig().isString("database")) {
            configuredType = plugin.getConfig().getString("database");
        }
        String databaseType = configuredType == null
                ? "sqlite"
                : configuredType.trim().toLowerCase(Locale.ROOT);
        if (databaseType.equals("postgres") || databaseType.equals("postgresql")) {
            databaseType = "postgre";
        }

        this.database = databaseDictionary().get(databaseType);
        if (this.database == null) {
            plugin.getLogger().warning("Unknown database type '" + configuredType
                    + "'; using the local SQLite database instead.");
            this.database = new SQLiteDatabase(plugin);
        }

        try {
            this.database.load();
        } catch (RuntimeException exception) {
            if (this.database instanceof SQLiteDatabase) throw exception;
            plugin.getLogger().log(Level.SEVERE,
                    "Could not connect to the configured " + databaseType
                            + " database; HPET will use local SQLite instead.", exception);
            this.database = new SQLiteDatabase(plugin);
            this.database.load();
        }

        if (this.database.getConnectionSource() == null) {
            throw new IllegalStateException("HPET database initialized without a connection");
        }
    }

    private void unloadDatabase() {
        if (this.database != null) this.database.unload();
    }
}
