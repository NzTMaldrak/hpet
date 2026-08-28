package it.heron.hpet.main.commands;

import io.github.jwdeveloper.spigot.commands.api.annotations.FCommand;
import io.github.jwdeveloper.spigot.commands.api.data.events.ArgumentSuggestionEvent;

import it.heron.hpet.api.PetAPI;
import it.heron.hpet.main.PetPlugin;
import it.heron.hpet.modules.messages.MessagesHandler;
import it.heron.hpet.modules.pets.pettypes.PetType;
import it.heron.hpet.modules.pets.userpets.abstracts.UserPet;
import it.heron.hpet.database.tables.LastPet;

import org.bukkit.command.CommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import org.bukkit.Sound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public final class PetCommand implements CommandExecutor, TabCompleter {

    private final PetAPI petAPI;
    private volatile MessagesHandler messagesHandler;

    public PetCommand() {
        this.petAPI = PetPlugin.getApi();
        this.messagesHandler = (MessagesHandler) PetPlugin.getInstance().getModulesHandler().moduleByName("Messages");
    }

    private MessagesHandler getMessagesHandler() {
        MessagesHandler current = (MessagesHandler) PetPlugin.getInstance()
                .getModulesHandler().moduleByName("Messages");
        if (current != null) messagesHandler = current;
        return current == null ? messagesHandler : current;
    }

    // --- Helper methods for Suggestions ---

    private Collection<String> getEnabledPetTypeNames(CommandSender sender) {
        if (petAPI == null) return List.of();
        return petAPI.enabledPetTypes().stream()
                .filter(type -> !(sender instanceof Player player)
                        || (type.canSee(player) && type.isUnlocked(player)))
                .map(PetType::getName)
                .collect(Collectors.toList());
    }

    private List<String> suggestPetTypes(ArgumentSuggestionEvent event) {
        return new ArrayList<>(petAPI.enabledPetTypes().stream().map(PetType::getName).toList());
    }

    private List<String> suggestBuyablePetTypes(ArgumentSuggestionEvent event) {
        // TODO: Implement logic to only suggest pet types the sender doesn't own yet and/or can afford.
        return new ArrayList<>(petAPI.enabledPetTypes().stream().map(PetType::getName).toList());
    }

    // --- Helper methods for Command Logic ---

    private void sendColoredMessage(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    private void sendMessageToSender(CommandSender sender, String messageSubpath, Map<String, String> placeholders) {
        MessagesHandler handler = getMessagesHandler();
        if (handler == null) {
            sendColoredMessage(sender, "&cError: Messages system not available.");
            return;
        }

        if (sender instanceof Player) {
            handler.sendMessage((Player) sender, messageSubpath, placeholders);
        } else {
            // Console sender needs manual placeholder replacement
            String rawMsg = handler.getRawString(messageSubpath);
            if (rawMsg != null) {
                String consoleMsg = rawMsg.replace("{player}", "Console"); // Default console placeholder
                if (placeholders != null) {
                    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                        consoleMsg = consoleMsg.replace(entry.getKey(), entry.getValue());
                    }
                }
                sendColoredMessage(sender, consoleMsg);
            } else {
                sendColoredMessage(sender, "&cMessage subpath '" + messageSubpath + "' not found in locale!");
            }
        }
    }

    private Player getTargetPlayer(CommandSender sender, Player targetArg) {
        if (targetArg != null) return targetArg;
        if (sender instanceof Player) return (Player) sender;

        sendMessageToSender(sender, "error.specify_target", null);
        return null;
    }

    private UserPet getUserPet(CommandSender sender, Player target) {
        if (target == null) return null;

        UserPet userPet = petAPI.userPet(target);
        if (userPet == null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("{player}", target.getName());
            sendMessageToSender(sender, "error.no_active_pet", placeholders);
            return null;
        }
        return userPet;
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        sendMessageToSender(sender, "error.no_permission", Map.of("{permission}", permission));
        return false;
    }

    private boolean requireTargetPermission(
            CommandSender sender, Player target, String selfPermission, String othersPermission) {
        return requirePermission(sender, sender.equals(target) ? selfPermission : othersPermission);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("reload")) {
            reloadCommand(sender);
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (args.length == 0 && sender instanceof Player player) {
                PetPlugin.getInstance().getPetGui().openHome(player);
            } else {
                sendHelpMessage(sender);
            }
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (!PetPlugin.getInstance().isPacketEventsAvailable()
                && !subcommand.equals("help")
                && !subcommand.equals("reload")) {
            sendMessageToSender(sender, "error.packet_events", null);
            return true;
        }
        try {
            switch (subcommand) {
                case "select" -> {
                    if (args.length < 2) return false;
                    selectPetCommand(sender, args[1], target(args, 2));
                }
                case "remove" -> removePetCommand(sender, target(args, 1));
                case "update" -> updatePetCommand(sender, target(args, 1));
                case "buy" -> {
                    if (args.length < 2) return false;
                    buyPetCommand(sender, args[1], target(args, 2));
                }
                case "addlevel" -> {
                    if (args.length < 2) return false;
                    addLevelCommand(sender, Double.parseDouble(args[1]), target(args, 2));
                }
                case "removelevel" -> {
                    if (args.length < 2) return false;
                    removeLevelCommand(sender, Double.parseDouble(args[1]), target(args, 2));
                }
                case "setlevel" -> {
                    if (args.length < 2) return false;
                    setLevelCommand(sender, Double.parseDouble(args[1]), target(args, 2));
                }
                case "level" -> showLevelCommand(sender, target(args, 1));
                case "reload" -> reloadCommand(sender);
                case "rename" -> {
                    if (!(sender instanceof Player player)) {
                        sendMessageToSender(sender, "command.hpet.rename.only_player", null);
                    } else if (args.length < 2) {
                        sendMessageToSender(sender, "command.hpet.rename.usage", null);
                    } else {
                        renamePetCommand(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                    }
                }
                default -> sendHelpMessage(sender);
            }
        } catch (NumberFormatException ignored) {
            sendMessageToSender(sender, "error.invalid_number", null);
        }
        return true;
    }

    private Player target(String[] args, int index) {
        return args.length > index ? Bukkit.getPlayerExact(args[index]) : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>(List.of("help"));
            if (sender.hasPermission("pet.remove")) subcommands.add("remove");
            if (sender.hasPermission("pet.update")) subcommands.add("update");
            if (sender.hasPermission("pet.buy")) subcommands.add("buy");
            if (sender.hasPermission("pet.addlevel")) subcommands.add("addlevel");
            if (sender.hasPermission("pet.removelevel")) subcommands.add("removelevel");
            if (sender.hasPermission("pet.setlevel")) subcommands.add("setlevel");
            if (sender.hasPermission("pet.level")) subcommands.add("level");
            if (sender.hasPermission("pet.rename")) subcommands.add("rename");
            if (sender.hasPermission("pet.reload")) subcommands.add("reload");
            if (!(sender instanceof Player player) || petAPI.enabledPetTypes().stream()
                    .anyMatch(type -> type.isUnlocked(player))) subcommands.add("select");
            return filterPrefix(subcommands, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("select") || args[0].equalsIgnoreCase("buy"))) {
            return filterPrefix(new ArrayList<>(getEnabledPetTypeNames(sender)), args[1]);
        }
        return List.of();
    }

    private List<String> filterPrefix(Collection<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted()
                .collect(Collectors.toList());
    }


    /*
     * /hpet - main command, opens gui, pet.command
     */
    @FCommand(
        pattern = "/hpet",
        permission = "pet.command", 
        description = "Main HPET command.", 
        usageMessage = "/hpet [subcommand]"
    )
    public void onHpetCommand(CommandSender sender) {
        sendHelpMessage(sender);
    }

    @FCommand(
        pattern = "/hpet help",
        permission = "pet.command", 
        description = "Shows help for HPET commands"
    )
    public void onHelpCommand(CommandSender sender) {
        sendHelpMessage(sender);
    }

    private void sendHelpMessage(CommandSender sender) {
        List<String> helpLines = new ArrayList<>();
        helpLines.add("command.hpet.help.header");
        helpLines.add("command.hpet.help.help");
        if (!(sender instanceof Player player) || petAPI.enabledPetTypes().stream().anyMatch(type -> type.isUnlocked(player)))
            helpLines.add("command.hpet.help.select");
        if (sender.hasPermission("pet.remove")) helpLines.add("command.hpet.help.remove");
        if (sender.hasPermission("pet.update")) helpLines.add("command.hpet.help.update");
        if (sender.hasPermission("pet.buy")) helpLines.add("command.hpet.help.buy");
        if (sender.hasPermission("pet.addlevel")) helpLines.add("command.hpet.help.addlevel");
        if (sender.hasPermission("pet.removelevel")) helpLines.add("command.hpet.help.removelevel");
        if (sender.hasPermission("pet.setlevel")) helpLines.add("command.hpet.help.setlevel");
        if (sender.hasPermission("pet.level")) helpLines.add("command.hpet.help.level");
        if (sender.hasPermission("pet.rename")) helpLines.add("command.hpet.help.rename");
        if (sender.hasPermission("pet.reload")) {
            helpLines.add("command.hpet.help.reload");
        }

        helpLines.forEach(path -> sendMessageToSender(sender, path, null));
    }

    private void reloadCommand(CommandSender sender) {
        if (!sender.hasPermission("pet.reload")) {
            sendMessageToSender(sender, "command.hpet.reload.no_permission", null);
            return;
        }

        sendMessageToSender(sender, "command.hpet.reload.start", null);
        if (PetPlugin.getInstance().reloadAll()) {
            sendMessageToSender(sender, "command.hpet.reload.success", null);
        } else {
            sendMessageToSender(sender, "command.hpet.reload.failed", null);
        }
    }


    /*
     * /hpet select <petType> [target:Player] - select a pet, pet.use.
     */
    @FCommand(
        pattern = "/hpet select <petType:Text(s:suggestPetTypes)> <target:Player?>",
        permission = "pet.use",
        description = "Selects a pet.",
        usageMessage = "/hpet select <petType> [player]"
    )
    public void selectPetCommand(CommandSender sender, String petType, Player target) {
        Player targetPlayer = getTargetPlayer(sender, target);
        if (targetPlayer == null) return;

        PetType type = petAPI.petType(petType);
        if (type == null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("{petType}", petType);
            sendMessageToSender(sender, "command.hpet.select.error.not_found", placeholders);
            return;
        }
        if (sender.equals(targetPlayer)) {
            if (!requirePermission(sender, "pet.use." + type.getName())) return;
        } else if (!requirePermission(sender, "pet.select.others")) {
            return;
        }
        if (!PetPlugin.getInstance().isPetWorldAllowed(targetPlayer)) {
            sendMessageToSender(sender, "error.world_disabled",
                    Map.of("{world}", targetPlayer.getWorld().getName()));
            return;
        }
        var combatModule = PetPlugin.getInstance().getModulesHandler().moduleByName("CombatLogX");
        if (combatModule instanceof it.heron.hpet.modules.combat.CombatLogXHook combatLogX
                && combatLogX.isLoaded() && combatLogX.preventsPetSelection(targetPlayer)) {
            sendMessageToSender(sender, "error.combat", null);
            return;
        }

        UserPet selectedPet = petAPI.selectPet(targetPlayer, type);

        if (selectedPet != null) {
            if (sender.equals(targetPlayer)) {
                sendMessageToSender(sender, "command.hpet.select.success.self", null);
            } else {
                Map<String, String> senderPlaceholders = new HashMap<>();
                senderPlaceholders.put("{player}", targetPlayer.getName());
                senderPlaceholders.put("{petType}", type.getName());
                sendMessageToSender(sender, "command.hpet.select.success.other", senderPlaceholders);

                messagesHandler.sendMessage(targetPlayer, "command.hpet.select.success.received",
                        Map.of("{sender}", sender.getName(), "{petType}", type.getName()));
            }
        } else {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("{petType}", petType);
            placeholders.put("{player}", targetPlayer.getName());
            sendMessageToSender(sender, "command.hpet.select.error.failed", placeholders);
        }
    }

    public void renamePetCommand(Player player, String requestedName) {
        if (!player.hasPermission("pet.rename")) {
            sendMessageToSender(player, "command.hpet.rename.no_permission", null);
            return;
        }
        UserPet userPet = getUserPet(player, player);
        if (userPet == null) return;

        String name = requestedName == null ? "" : requestedName.trim();
        if (!player.hasPermission("pet.rename.color")) {
            name = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', name));
        }
        int maxLength = Math.max(1, PetPlugin.getInstance().getConfig().getInt("nametags.maxlength", 20));
        name = truncateVisibleLegacyName(name, maxLength);
        for (String invalid : PetPlugin.getInstance().getConfig().getStringList("nametags.invalidnames")) {
            if (invalid == null || invalid.isBlank()) continue;
            name = name.replaceAll("(?i)" + Pattern.quote(invalid), "*");
        }
        if (ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', name)).isBlank()) {
            sendMessageToSender(player, "command.hpet.rename.empty", null);
            return;
        }

        userPet.rename(name);
        LastPet lastPet = LastPet.load(player.getUniqueId());
        if (lastPet == null) lastPet = new LastPet();
        lastPet.setOwner(player.getUniqueId());
        lastPet.setPetType(userPet.getPetType().getName());
        lastPet.setPetName(name);
        lastPet.save();
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
        sendMessageToSender(player, "command.hpet.rename.success", Map.of("{name}", name));
    }

    private String truncateVisibleLegacyName(String input, int maxVisibleLength) {
        StringBuilder result = new StringBuilder();
        int visible = 0;
        for (int index = 0; index < input.length() && visible < maxVisibleLength; index++) {
            char current = input.charAt(index);
            if (current == '&' && index + 1 < input.length()
                    && "0123456789abcdefklmnorxABCDEFKLMNORX".indexOf(input.charAt(index + 1)) >= 0) {
                result.append(current).append(input.charAt(++index));
                continue;
            }
            result.append(current);
            visible++;
        }
        return result.toString();
    }

    /*
     * /hpet remove [target:Player] - remove the current pet, pet.remove
     */
    @FCommand(
        pattern = "/hpet remove <target:Player?>",
        permission = "pet.remove",
        description = "Removes the current pet.",
        usageMessage = "/hpet remove [player]"
    )
    public void removePetCommand(CommandSender sender, Player target) {
        Player targetPlayer = getTargetPlayer(sender, target);
        if (targetPlayer == null) return;
        if (!requireTargetPermission(sender, targetPlayer, "pet.remove", "pet.remove.others")) return;

        UserPet userPet = getUserPet(sender, targetPlayer);
        if (userPet == null) return;

        petAPI.deselectPet(userPet);

        if (sender.equals(targetPlayer)) {
            sendMessageToSender(sender, "command.hpet.remove.success.self", null);
        } else {
            Map<String, String> senderPlaceholders = new HashMap<>();
            senderPlaceholders.put("{player}", targetPlayer.getName());
            sendMessageToSender(sender, "command.hpet.remove.success.other", senderPlaceholders);

            messagesHandler.sendMessage(targetPlayer, "command.hpet.remove.success.received",
                    Map.of("{sender}", sender.getName()));
        }
    }

    /*
     * /hpet update [target:Player] - respawn your pet, pet.update
     */
    @FCommand(
        pattern = "/hpet update <target:Player?>",
        permission = "pet.update",
        description = "Respawn your current pet.",
        usageMessage = "/hpet update [player]"
    )
    public void updatePetCommand(CommandSender sender, Player target) {
        Player targetPlayer = getTargetPlayer(sender, target);
        if (targetPlayer == null) return;
        if (!requireTargetPermission(sender, targetPlayer, "pet.update", "pet.update.others")) return;

        UserPet userPet = getUserPet(sender, targetPlayer);
        if (userPet == null) return;

        PetType currentType = userPet.getPetType();
        petAPI.removePet(userPet);
        UserPet newPet = petAPI.selectPet(targetPlayer, currentType);

        if (newPet != null) {
            if (sender.equals(targetPlayer)) {
                sendMessageToSender(sender, "command.hpet.update.success.self", null);
            } else {
                Map<String, String> senderPlaceholders = new HashMap<>();
                senderPlaceholders.put("{player}", targetPlayer.getName());
                sendMessageToSender(sender, "command.hpet.update.success.other", senderPlaceholders);

                messagesHandler.sendMessage(targetPlayer, "command.hpet.update.success.received",
                        Map.of("{sender}", sender.getName()));
            }
        } else {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("{player}", targetPlayer.getName());
            sendMessageToSender(sender, "command.hpet.update.error.failed", placeholders);
        }
    }

    /*
     * /hpet buy <petType:Text> [target:Player] - buy a pet you don't have, pet.see.
     */
    @FCommand(
        pattern = "/hpet buy <petType:Text(s:suggestBuyablePetTypes)> <target:Player?>",
        permission = "pet.see",
        description = "Allows a player to buy a pet.",
        usageMessage = "/hpet buy <petType> [player]"
    )
    public void buyPetCommand(CommandSender sender, String petType, Player target) {
        Player targetPlayer = getTargetPlayer(sender, target);
        if (targetPlayer == null) return;
        if (!requireTargetPermission(sender, targetPlayer, "pet.buy", "pet.buy.others")) return;

        PetType type = petAPI.petType(petType);
        if (type == null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("{petType}", petType);
            sendMessageToSender(sender, "command.hpet.buy.error.not_found", placeholders);
            return;
        }

        // TODO: Implement logic for buying the pet:
        // 1. Check if the targetPlayer already owns this pet type.
        // 2. Check if the targetPlayer has enough money (requires economy integration).
        // 3. If checks pass, add the pet to the player's owned pets list.
        // 4. Send success/failure messages using messagesHandler.

        Map<String, String> wipPlaceholders = new HashMap<>();
        wipPlaceholders.put("{petType}", petType);
        wipPlaceholders.put("{player}", targetPlayer.getName());
        sendMessageToSender(sender, "command.hpet.buy.wip", wipPlaceholders);

        boolean success = true; // Simulate success/failure based on TODOs above

        if (success) {
            if (sender.equals(targetPlayer)) {
                Map<String, String> selfPlaceholders = new HashMap<>();
                selfPlaceholders.put("{petType}", petType);
                sendMessageToSender(sender, "command.hpet.buy.success.self", selfPlaceholders);
            } else {
                Map<String, String> senderPlaceholders = new HashMap<>();
                senderPlaceholders.put("{petType}", petType);
                senderPlaceholders.put("{player}", targetPlayer.getName());
                sendMessageToSender(sender, "command.hpet.buy.success.other", senderPlaceholders);
                // MessagesHandler buildDictionary should include sender name as {player}
                messagesHandler.sendMessage(targetPlayer, "command.hpet.buy.success.received",
                        Map.of("{sender}", sender.getName(), "{petType}", petType));
            }
        } else {
            Map<String, String> failedPlaceholders = new HashMap<>();
            failedPlaceholders.put("{petType}", petType);
            failedPlaceholders.put("{player}", targetPlayer.getName());
            sendMessageToSender(sender, "command.hpet.buy.error.failed", failedPlaceholders);
        }
    }

    /*
     * /hpet addlevel <amount:Integer> [target:Player] - add pet level, pet.addlevel
     */
    @FCommand(
        pattern = "/hpet addlevel <amount:Number> <target:Player?>",
        permission = "pet.addlevel",
        description = "Adds levels to a pet.",
        usageMessage = "/hpet addlevel <amount> [player]"
    )
    public void addLevelCommand(CommandSender sender, double amount, Player target) {
        Player targetPlayer = getTargetPlayer(sender, target);
        if (targetPlayer == null) return;
        if (!requireTargetPermission(sender, targetPlayer, "pet.addlevel", "pet.addlevel.others")) return;

        UserPet userPet = getUserPet(sender, targetPlayer);
        if (userPet == null) return;

        if (amount <= 0) {
            sendMessageToSender(sender, "command.hpet.level.add.error.invalid_amount", null);
            return;
        }

        int currentLevel = userPet.getLevel();
        int newLevel = currentLevel + (int)Math.round(amount);
        userPet.setLevel(newLevel);

        Map<String, String> levelPlaceholders = new HashMap<>();
        levelPlaceholders.put("{amount}", String.valueOf(amount));
        levelPlaceholders.put("{level}", String.valueOf(newLevel));


        if (sender.equals(targetPlayer)) {
            sendMessageToSender(sender, "command.hpet.level.add.success.self", levelPlaceholders);
        } else {
            levelPlaceholders.put("{player}", targetPlayer.getName());
            sendMessageToSender(sender, "command.hpet.level.add.success.other", levelPlaceholders);

            messagesHandler.sendMessage(targetPlayer, "command.hpet.level.add.success.received",
                    Map.of("{sender}", sender.getName(), "{amount}", String.valueOf(amount),
                            "{level}", String.valueOf(newLevel)));
        }
    }


    /*
     * /hpet removelevel <amount:Integer> [target:Player] - decrease pet level, pet.removelevel
     */
    @FCommand(
        pattern = "/hpet removelevel <amount:Number> <target:Player?>",
        permission = "pet.removelevel",
        description = "Decreases a pet's level.",
        usageMessage = "/hpet removelevel <amount> [player]"
    )
    public void removeLevelCommand(CommandSender sender, double amount, Player target) {
        Player targetPlayer = getTargetPlayer(sender, target);
        if (targetPlayer == null) return;
        if (!requireTargetPermission(sender, targetPlayer, "pet.removelevel", "pet.removelevel.others")) return;

        UserPet userPet = getUserPet(sender, targetPlayer);
        if (userPet == null) return;

        if (amount <= 0) {
            sendMessageToSender(sender, "command.hpet.level.remove.error.invalid_amount", null);
            return;
        }

        int currentLevel = userPet.getLevel();
        int newLevel = Math.max(0, currentLevel - (int)Math.round(amount));
        userPet.setLevel(newLevel);

        Map<String, String> levelPlaceholders = new HashMap<>();
        levelPlaceholders.put("{amount}", String.valueOf(amount));
        levelPlaceholders.put("{level}", String.valueOf(newLevel));

        if (sender.equals(targetPlayer)) {
            sendMessageToSender(sender, "command.hpet.level.remove.success.self", levelPlaceholders);
        } else {
            levelPlaceholders.put("{player}", targetPlayer.getName());
            sendMessageToSender(sender, "command.hpet.level.remove.success.other", levelPlaceholders);

            messagesHandler.sendMessage(targetPlayer, "command.hpet.level.remove.success.received",
                    Map.of("{sender}", sender.getName(), "{amount}", String.valueOf(amount),
                            "{level}", String.valueOf(newLevel)));
        }
    }


    /*
     * /hpet setlevel <level:Integer> [target:Player] - set a pet level, pet.setlevel
     */
    @FCommand(
        pattern = "/hpet setlevel <level:Number> <target:Player?>",
        permission = "pet.setlevel",
        description = "Sets a pet's level.",
        usageMessage = "/hpet setlevel <level> [player]"
    )
    public void setLevelCommand(CommandSender sender, double level, Player target) {
        Player targetPlayer = getTargetPlayer(sender, target);
        if (targetPlayer == null) return;
        if (!requireTargetPermission(sender, targetPlayer, "pet.setlevel", "pet.setlevel.others")) return;

        UserPet userPet = getUserPet(sender, targetPlayer);
        if (userPet == null) return;

        if (level < 0) {
            sendMessageToSender(sender, "command.hpet.level.set.error.negative_level", null);
            return;
        }

        userPet.setLevel((int)Math.round(level));

        Map<String, String> levelPlaceholders = new HashMap<>();
        levelPlaceholders.put("{level}", String.valueOf(level));

        if (sender.equals(targetPlayer)) {
            sendMessageToSender(sender, "command.hpet.level.set.success.self", levelPlaceholders);
        } else {
            levelPlaceholders.put("{player}", targetPlayer.getName());
            sendMessageToSender(sender, "command.hpet.level.set.success.other", levelPlaceholders);

            messagesHandler.sendMessage(targetPlayer, "command.hpet.level.set.success.received",
                    Map.of("{sender}", sender.getName(), "{level}", String.valueOf(level)));
        }
    }

    /*
     * /hpet level [target:Player] - shows current pet level, pet.level
     */
    @FCommand(
        pattern = "/hpet level <target:Player?>",
        permission = "pet.level",
        description = "Shows your current pet's level.",
        usageMessage = "/hpet level [player]"
    )
    public void showLevelCommand(CommandSender sender, Player target) {
        Player targetPlayer = getTargetPlayer(sender, target);
        if (targetPlayer == null) return;
        if (!requireTargetPermission(sender, targetPlayer, "pet.level", "pet.level.others")) return;

        UserPet userPet = getUserPet(sender, targetPlayer);
        if (userPet == null) return;

        int level = userPet.getLevel();

        Map<String, String> levelPlaceholders = new HashMap<>();
        levelPlaceholders.put("{level}", String.valueOf(level));


        if (sender.equals(targetPlayer)) {
            sendMessageToSender(sender, "command.hpet.level.show.self", levelPlaceholders);
        } else {
            levelPlaceholders.put("{player}", targetPlayer.getName());
            sendMessageToSender(sender, "command.hpet.level.show.other", levelPlaceholders);

            messagesHandler.sendMessage(targetPlayer, "command.hpet.level.show.received",
                    Map.of("{sender}", sender.getName(), "{level}", String.valueOf(level)));
        }
    }
}
