package it.heron.hpet.database;

import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.table.TableUtils;
import it.heron.hpet.database.tables.LastPet;
import it.heron.hpet.database.tables.PetLevel;
import it.heron.hpet.database.tables.DailyAbilityUsage;
import it.heron.hpet.database.tables.BoughtPets;
import it.heron.hpet.database.tables.PetActivationState;
import lombok.Getter;

public abstract class AbstractDatabase implements Database {

    @Getter
    protected ConnectionSource connectionSource;

    protected abstract String getDatabaseUrl();

    @Override
    public void load() {
        try {
            connectionSource = new JdbcConnectionSource(getDatabaseUrl());
            // Register table classes
            TableUtils.createTableIfNotExists(connectionSource, LastPet.class);
            TableUtils.createTableIfNotExists(connectionSource, PetLevel.class);
            TableUtils.createTableIfNotExists(connectionSource, DailyAbilityUsage.class);
            TableUtils.createTableIfNotExists(connectionSource, BoughtPets.class);
            TableUtils.createTableIfNotExists(connectionSource, PetActivationState.class);
        } catch (Exception e) {
            connectionSource = null;
            throw new IllegalStateException("Could not initialize the HPET database", e);
        }
    }

    @Override
    public void unload() {
        try {
            if (connectionSource != null) {
                connectionSource.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
