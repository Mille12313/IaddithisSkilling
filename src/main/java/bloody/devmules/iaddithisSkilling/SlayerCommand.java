package bloody.devmules.iaddithisSkilling;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class SlayerCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return true;
        }
        if (!p.hasPermission("iaddithisskilling.slayer.use")) {
            p.sendMessage(ChatColor.RED + "Je hebt geen toestemming voor dit command.");
            return true;
        }
        if (p.getGameMode() != GameMode.SURVIVAL) {
            p.sendMessage(ChatColor.RED + "Je moet in SURVIVAL zijn om Slayer taken te gebruiken.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(p, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "new", "neem", "start" -> doNew(p);
            case "task", "status" -> doStatus(p);
            case "skip" -> doSkip(p);
            case "cancel", "stop" -> doCancel(p);
            default -> sendHelp(p, label);
        }
        return true;
    }

    private void sendHelp(Player p, String label) {
        p.sendMessage(ChatColor.GOLD + "Slayer commands:");
        p.sendMessage(ChatColor.YELLOW + "/" + label + " new" + ChatColor.GRAY + " - Neem een willekeurige taak");
        p.sendMessage(ChatColor.YELLOW + "/" + label + " task" + ChatColor.GRAY + " - Bekijk je huidige taak");
        p.sendMessage(ChatColor.YELLOW + "/" + label + " skip" + ChatColor.GRAY + " - Skip je huidige taak (kost punten)");
        p.sendMessage(ChatColor.YELLOW + "/" + label + " cancel" + ChatColor.GRAY + " - Annuleer je huidige taak (geen punten terug)");
    }

    private void doNew(Player p) {
        UUID u = p.getUniqueId();
        var st = SlayerService.getState(u);
        if (st.id != null && st.remaining > 0) {
            p.sendMessage(ChatColor.RED + "Je hebt al een actieve taak: " + ChatColor.YELLOW + st.id +
                    ChatColor.GRAY + " (" + st.mob + ", nog " + st.remaining + ").");
            p.sendMessage(ChatColor.GRAY + "Gebruik " + ChatColor.YELLOW + "/slayer skip" + ChatColor.GRAY + " of " +
                    ChatColor.YELLOW + "/slayer cancel");
            return;
        }

        var task = SlayerService.rollRandomTask();
        if (task == null) {
            p.sendMessage(ChatColor.RED + "Er zijn geen Slayer tasks geconfigureerd (slayer.yml).");
            return;
        }

        st.id = task.id();
        st.mob = task.mythicMob();
        st.remaining = task.amount();
        // streak blijft staan; reset NIET bij new
        SlayerService.saveState(u, st);

        p.sendMessage(ChatColor.GREEN + "Nieuwe Slayer taak: " + ChatColor.YELLOW + st.id +
                ChatColor.GRAY + " — dood " + ChatColor.AQUA + st.remaining + ChatColor.GRAY +
                "x MythicMob " + ChatColor.AQUA + st.mob);
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            p.sendMessage(ChatColor.RED + "Let op: MythicMobs staat niet aan. Kills tellen dan niet.");
        }
    }

    private void doStatus(Player p) {
        var st = SlayerService.getState(p.getUniqueId());
        if (st.id == null || st.remaining <= 0) {
            p.sendMessage(ChatColor.YELLOW + "Je hebt momenteel geen actieve Slayer taak.");
            return;
        }
        p.sendMessage(ChatColor.GREEN + "Actieve Slayer taak:");
        p.sendMessage(ChatColor.GRAY + " - ID: " + ChatColor.YELLOW + st.id);
        p.sendMessage(ChatColor.GRAY + " - Mob: " + ChatColor.AQUA + st.mob);
        p.sendMessage(ChatColor.GRAY + " - Remaining: " + ChatColor.AQUA + st.remaining);
        p.sendMessage(ChatColor.GRAY + " - Streak: " + ChatColor.AQUA + st.streak);
        p.sendMessage(ChatColor.GRAY + " - Points: " + ChatColor.AQUA + st.points);
    }

    private void doSkip(Player p) {
        UUID u = p.getUniqueId();
        var st = SlayerService.getState(u);
        if (st.id == null || st.remaining <= 0) {
            p.sendMessage(ChatColor.RED + "Je hebt geen actieve taak om te skippen.");
            return;
        }

        int cost = SlayerService.skipCost();
        if (st.points < cost) {
            p.sendMessage(ChatColor.RED + "Onvoldoende Slayer points. Nodig: " + ChatColor.YELLOW + cost +
                    ChatColor.RED + ", je hebt " + ChatColor.YELLOW + st.points);
            return;
        }

        st.points -= cost;
        // Skip reset je streak (bonus is “zonder skippen”)
        st.streak = 0;
        // Clear huidige taak
        st.id = null;
        st.mob = null;
        st.remaining = 0;

        SlayerService.saveState(u, st);
        p.sendMessage(ChatColor.GREEN + "Je hebt je taak geskipt voor " + ChatColor.YELLOW + cost +
                ChatColor.GREEN + " points. Streak is gereset.");
    }

    private void doCancel(Player p) {
        UUID u = p.getUniqueId();
        var st = SlayerService.getState(u);
        if (st.id == null || st.remaining <= 0) {
            p.sendMessage(ChatColor.YELLOW + "Je had geen actieve taak.");
            return;
        }
        st.id = null;
        st.mob = null;
        st.remaining = 0;
        // Cancel reset GEEN points, maar ook geen streak bonus — je verliest wél je streak
        st.streak = 0;
        SlayerService.saveState(u, st);
        p.sendMessage(ChatColor.GREEN + "Je Slayer taak is geannuleerd. Streak is gereset.");
    }

    // ----- Tab complete -----
    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        if (!(s instanceof Player p)) return List.of();
        if (!p.hasPermission("iaddithisskilling.slayer.use")) return List.of();

        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String pref = args[0].toLowerCase(Locale.ROOT);
            for (String sub : new String[]{"new","task","skip","cancel"}) {
                if (sub.startsWith(pref)) out.add(sub);
            }
        }
        return out;
    }
}
