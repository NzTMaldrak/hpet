package it.heron.hpet.database.tables;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import it.heron.hpet.main.PetPlugin;
import it.heron.hpet.modules.DatabaseModule;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@DatabaseTable(tableName = "DailyAbilityUsage")
@Data
@NoArgsConstructor
public class DailyAbilityUsage {

    @DatabaseField(generatedId = true)
    private int id;

    @DatabaseField(canBeNull = false, uniqueCombo = true)
    private UUID owner;

    @DatabaseField(canBeNull = false, uniqueCombo = true)
    private String petType;

    @DatabaseField(canBeNull = false, width = 64, uniqueCombo = true)
    private String abilityKey;

    @DatabaseField(canBeNull = false)
    private String usageDate;

    @DatabaseField(canBeNull = false)
    private long usedMillis;

    public void save() {
        try {
            dao().createOrUpdate(this);
        } catch (Exception exception) {
            PetPlugin.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not save daily ability usage for " + owner, exception);
        }
    }

    public boolean resetIfNewDay(LocalDate currentDate) {
        String date = currentDate.toString();
        if (date.equals(usageDate)) return false;
        usageDate = date;
        usedMillis = 0L;
        return true;
    }

    public static DailyAbilityUsage loadOrCreate(
            UUID owner, String petType, String abilityKey, LocalDate currentDate) {
        try {
            DailyAbilityUsage usage = dao().queryBuilder().where()
                    .eq("owner", owner)
                    .and()
                    .eq("petType", petType)
                    .and()
                    .eq("abilityKey", abilityKey)
                    .queryForFirst();
            if (usage == null) {
                usage = new DailyAbilityUsage();
                usage.owner = owner;
                usage.petType = petType;
                usage.abilityKey = abilityKey;
                usage.usageDate = currentDate.toString();
                usage.usedMillis = 0L;
                usage.save();
            } else if (usage.resetIfNewDay(currentDate)) {
                usage.save();
            }
            return usage;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load daily ability usage", exception);
        }
    }

    private static Dao<DailyAbilityUsage, Integer> dao() throws Exception {
        DatabaseModule module = (DatabaseModule) PetPlugin.getInstance()
                .getModulesHandler().moduleByName("database");
        if (module == null || module.getDatabase() == null
                || module.getDatabase().getConnectionSource() == null) {
            throw new IllegalStateException("HPET database is not available");
        }
        return DaoManager.createDao(module.getDatabase().getConnectionSource(), DailyAbilityUsage.class);
    }
}
