package it.heron.hpet.database.tables;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import it.heron.hpet.main.PetPlugin;
import it.heron.hpet.modules.DatabaseModule;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.util.logging.Level;

/** Persists whether an owner's last selected pet should be restored. */
@DatabaseTable(tableName = "PetActivationState")
@Data
@NoArgsConstructor
public class PetActivationState {

    @DatabaseField(id = true)
    private UUID owner;

    @DatabaseField(canBeNull = false)
    private boolean active;

    /** Existing users without a state row keep the legacy active behavior. */
    public static boolean isActive(UUID owner) {
        try {
            PetActivationState state = dao().queryForId(owner);
            return state == null || state.active;
        } catch (Exception exception) {
            PetPlugin.getInstance().getLogger().log(Level.SEVERE,
                    "Could not load pet activation state for " + owner, exception);
            return true;
        }
    }

    public static void setActive(UUID owner, boolean active) {
        try {
            Dao<PetActivationState, UUID> dao = dao();
            PetActivationState state = dao.queryForId(owner);
            if (state == null) {
                state = new PetActivationState();
                state.owner = owner;
            }
            state.active = active;
            dao.createOrUpdate(state);
        } catch (Exception exception) {
            PetPlugin.getInstance().getLogger().log(Level.SEVERE,
                    "Could not save pet activation state for " + owner, exception);
        }
    }

    private static Dao<PetActivationState, UUID> dao() throws Exception {
        DatabaseModule module = (DatabaseModule) PetPlugin.getInstance()
                .getModulesHandler().moduleByName("database");
        return DaoManager.createDao(
                module.getDatabase().getConnectionSource(), PetActivationState.class);
    }
}
