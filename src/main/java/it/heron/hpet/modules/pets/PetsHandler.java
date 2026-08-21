package it.heron.hpet.modules.pets;

import it.heron.hpet.database.tables.PetLevel;
import it.heron.hpet.database.tables.LastPet;
import it.heron.hpet.main.PetPlugin;
import it.heron.hpet.modules.abstracts.DefaultInstanceModule;
import org.bukkit.plugin.java.JavaPlugin;
import it.heron.hpet.modules.pets.pettypes.CustomModelPetType;
import it.heron.hpet.modules.pets.pettypes.HeadPetType;
import it.heron.hpet.modules.pets.pettypes.PetType;
import it.heron.hpet.modules.pets.pettypes.StackPetType;
import it.heron.hpet.modules.pets.pettypes.MobPetType;
import it.heron.hpet.modules.pets.userpets.MobUserPet;
import it.heron.hpet.modules.pets.userpets.HandUserPet;
import it.heron.hpet.modules.pets.userpets.StackUserPet;
import it.heron.hpet.modules.pets.userpets.abstracts.UserPet;
import it.heron.hpet.modules.pets.userpets.workloads.WorkloadRunnable;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PetsHandler extends DefaultInstanceModule {

    private int taskId = -1;

    private Set<UserPet> spawnedPets = new HashSet<>();

    public PetsHandler(JavaPlugin plugin) {
        super(plugin);
    }

    @Getter
    private WorkloadRunnable workloadRunnable = new WorkloadRunnable();

    @Override
    public String name() {
        return "PetsHandler";
    }

    @Override
    protected void onLoad() {
        startWorkloadRunnable();
    }

    @Override
    protected void onUnload() {
        endTask();
    }

    public Set<UserPet> userPets(UUID owner) {
        Set<UserPet> userPets = new HashSet<>();
        for(UserPet userPet : spawnedPets) {
            if(userPet.getOwner().equals(owner)) {
                userPets.add(userPet);
            }
        }
        return userPets;
    }

    public UserPet selectPet(Entity entity, PetType petType) {
        if (petType == null) {
            throw new IllegalArgumentException("Pet type cannot be null");
        }
        PetLevel petLevel = PetLevel.loadOrCreate(entity.getUniqueId(), petType.getName());
        for (UserPet current : new HashSet<>(userPets(entity.getUniqueId()))) {
            removePet(current);
        }
        UserPet userPet = null;
        if (petType instanceof MobPetType) {
            userPet = new MobUserPet((MobPetType) petType, entity, petLevel.getLevel());
        } else if(petType instanceof CustomModelPetType) {
            userPet = new StackUserPet((StackPetType)petType, entity, petLevel.getLevel());
        } else if(petType instanceof HeadPetType) {
            userPet = new HandUserPet((StackPetType) petType, entity, petLevel.getLevel());
        }

        if (userPet == null) {
            throw new IllegalArgumentException("Unsupported pet type: " + petType.getName());
        }
        LastPet lastPet = LastPet.load(entity.getUniqueId());
        String rememberedName = lastPet != null
                && petType.getName().equalsIgnoreCase(lastPet.getPetType())
                ? lastPet.getPetName()
                : null;
        if (rememberedName != null && !rememberedName.isBlank()) {
            userPet.rename(rememberedName);
        }
        userPet.spawn();
        registerPet(userPet);
        if (lastPet == null) lastPet = new LastPet();
        lastPet.setOwner(entity.getUniqueId());
        lastPet.setPetType(petType.getName());
        lastPet.setPetName(rememberedName);
        lastPet.save();
        return userPet;
    }

    public boolean isPetRegistered(UserPet userPet) {
        return this.spawnedPets.contains(userPet);
    }

    public Collection<UserPet> spawnedPets() {
        return spawnedPets;
    }

    public void removePet(UserPet userPet) {
        userPet.despawn();
        unregisterPet(userPet);
    }

    private void registerPet(UserPet userPet) {
        this.spawnedPets.add(userPet);
        this.workloadRunnable.addWorkload(new it.heron.hpet.modules.pets.userpets.workloads.UserPetsWorkload(userPet));
    }

    private void unregisterPet(UserPet userPet) {
        this.spawnedPets.remove(userPet);
    }


    private void endTask() {
        if(this.taskId == -1) return;
        Bukkit.getScheduler().cancelTask(this.taskId);
        this.taskId = -1;
    }

    private void startWorkloadRunnable() {
        this.taskId = Bukkit.getScheduler().runTaskTimer(PetPlugin.getInstance(), this.workloadRunnable, 1, 1).getTaskId();
    }

}
