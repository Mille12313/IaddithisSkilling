package bloody.devmules.iaddithisSkilling;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SkillXpCommand implements CommandExecutor {

    // Gebruik:
    // /skillxp <player> <SKILL> <amount>
    // Voorbeeld (MythicMobs):
    //   command{c="skillxp <trigger.name> MINING 40"} @self
    //
    // Deze command past automatisch ExpBoostService toe (double XP etc.)
    // en toont dezelfde actionbar-popup als de normale XP-flow.

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length < 3) {
            if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <player> <SKILL> <amount>");
            }
            return true;
        }

        String targetName = args[0];
        String skill = args[1].toUpperCase();
        double base;
        try {
            base = Double.parseDouble(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Amount must be a number.");
            return true;
        }
        if (base <= 0) {
            if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                sender.sendMessage(ChatColor.RED + "Amount must be > 0.");
            }
            return true;
        }

        Player p = Bukkit.getPlayerExact(targetName);
        if (p == null) {
            // probeer case-insensitive/partial
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().equalsIgnoreCase(targetName)) {
                    p = online; break;
                }
            }
        }
        if (p == null) {
            if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            }
            return true;
        }

        // apply global EXP boost
        double amount = ExpBoostService.apply(base);
        if (amount <= 0) return true;

        SkillManager.addXP(p, skill, amount);

        // Zelfde actionbar-stijl als SkillEvents
        boolean notif = IaddithisSkilling.getInstance().getData()
                .getBoolean(p.getUniqueId() + ".settings.xpNotifications", true);
        if (notif) {
            String suffix = ExpBoostService.isEnabled()
                    ? " (x" + (int)Math.round(ExpBoostService.currentMultiplier()) + ")"
                    : "";
            p.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent("✨ +" + (int) amount + " " + skill + " XP" + suffix)
            );
        }

        // alleen een feedback naar de uitvoerder als het geen MM/console spam wordt
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            sender.sendMessage(ChatColor.GREEN + "Gave " + (int)amount + " " + skill + " XP to " + p.getName()
                    + (ExpBoostService.isEnabled() ? ChatColor.GOLD + " (boost applied)" : ""));
        }
        return true;
    }
}
