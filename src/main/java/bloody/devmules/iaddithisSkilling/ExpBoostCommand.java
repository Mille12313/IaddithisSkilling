package bloody.devmules.iaddithisSkilling;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ExpBoostCommand implements CommandExecutor {

    // /expboost on [multiplier] [minutes]
    // /expboost off
    // /expboost set <multiplier> [minutes]
    // /expboost status
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("iaddithisskilling.expboost")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();

        try {
            switch (sub) {
                case "on": {
                    double mult = (args.length >= 2) ? Double.parseDouble(args[1]) : 2.0;
                    long mins   = (args.length >= 3) ? Long.parseLong(args[2]) : 0L;
                    if (mult < 1.0) mult = 1.0;
                    ExpBoostService.set(mult, mins);
                    sender.sendMessage(ChatColor.GREEN + "EXP Boost enabled: x" + mult +
                            (mins > 0 ? " for " + mins + " minutes." : " (no end)"));
                    break;
                }
                case "off": {
                    ExpBoostService.setEnabled(false);
                    sender.sendMessage(ChatColor.YELLOW + "EXP Boost disabled.");
                    break;
                }
                case "set": {
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "Usage: /" + label + " set <multiplier> [minutes]");
                        break;
                    }
                    double mult = Double.parseDouble(args[1]);
                    long mins   = (args.length >= 3) ? Long.parseLong(args[2]) : 0L;
                    if (mult < 1.0) mult = 1.0;
                    ExpBoostService.set(mult, mins);
                    sender.sendMessage(ChatColor.GREEN + "EXP Boost set to x" + mult +
                            (mins > 0 ? " for " + mins + " minutes." : " (no end)"));
                    break;
                }
                case "status": {
                    boolean on = ExpBoostService.isEnabled();
                    double mult = ExpBoostService.currentMultiplier();
                    long until = ExpBoostService.getUntilEpochMs();
                    if (on) {
                        if (until > 0) {
                            long minsLeft = Math.max(0, (until - System.currentTimeMillis()) / 60000);
                            sender.sendMessage(ChatColor.AQUA + "EXP Boost: ON x" + mult + " (" + minsLeft + " min left)");
                        } else {
                            sender.sendMessage(ChatColor.AQUA + "EXP Boost: ON x" + mult + " (no end)");
                        }
                    } else {
                        sender.sendMessage(ChatColor.GRAY + "EXP Boost: OFF");
                    }
                    break;
                }
                default:
                    sendHelp(sender, label);
            }
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Numbers expected (multiplier/minutes).");
        }

        return true;
    }

    private void sendHelp(CommandSender s, String label) {
        s.sendMessage(ChatColor.GOLD + "EXP Boost Commands:");
        s.sendMessage(ChatColor.YELLOW + "/" + label + " on [multiplier] [minutes]" + ChatColor.GRAY + "  — enable (default x2, infinite)");
        s.sendMessage(ChatColor.YELLOW + "/" + label + " off" + ChatColor.GRAY + "                      — disable");
        s.sendMessage(ChatColor.YELLOW + "/" + label + " set <multiplier> [minutes]" + ChatColor.GRAY + " — set value/time");
        s.sendMessage(ChatColor.YELLOW + "/" + label + " status" + ChatColor.GRAY + "                   — current status");
    }
}
