package bloody.devmules.iaddithisSkilling;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HighscoreWebServer {
    private final HttpServer server;

    public HighscoreWebServer(String bindAddress, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        server.createContext("/", new HighscoreHandler());
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    static class HighscoreHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getRawQuery();
            String skillParam = null;
            if (query != null && query.contains("skill=")) {
                for (String part : query.split("&")) {
                    if (part.startsWith("skill=")) {
                        skillParam = URLDecoder.decode(part.substring(6), "UTF-8").toUpperCase();
                    }
                }
            }

            String response = getHtml(skillParam);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            byte[] respBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, respBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(respBytes);
            }
        }

        private String getHtml(String skillParam) {
            FileConfiguration data = IaddithisSkilling.getInstance().getData();
            String[] skills = SkillsCommand.ALL_SKILLS;
            int maxLevel = IaddithisSkilling.getInstance().getConfig().getInt("max-level", 50);

            Map<String, String> uuidToName = new HashMap<>();
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op.getName() != null)
                    uuidToName.put(op.getUniqueId().toString(), op.getName());
            }

            Map<String, Double> uuidToTotalXp = new HashMap<>();
            Map<String, Integer> uuidToTotalLvl = new HashMap<>();
            Map<String, Map<String, Integer>> uuidToSkillLvls = new HashMap<>();
            Map<String, Map<String, Double>> uuidToSkillXps = new HashMap<>();

            for (String uuid : data.getKeys(false)) {
                int totalLvl = 0;
                double totalXp = 0;
                Map<String, Integer> skillLvls = new HashMap<>();
                Map<String, Double> skillXps = new HashMap<>();
                for (String skill : skills) {
                    int lvl = data.getInt(uuid + "." + skill + ".level", 1);
                    double xp = data.getDouble(uuid + "." + skill + ".xp", 0.0);
                    totalLvl += lvl;
                    totalXp += xp;
                    skillLvls.put(skill, lvl);
                    skillXps.put(skill, xp);
                }
                uuidToTotalLvl.put(uuid, totalLvl);
                uuidToTotalXp.put(uuid, totalXp);
                uuidToSkillLvls.put(uuid, skillLvls);
                uuidToSkillXps.put(uuid, skillXps);
            }

            List<String> sortedUuids = new ArrayList<>(data.getKeys(false));
            if (skillParam == null || skillParam.isEmpty() || skillParam.equalsIgnoreCase("total")) {
                sortedUuids.sort((a, b) -> {
                    int diff = uuidToTotalLvl.get(b) - uuidToTotalLvl.get(a);
                    if (diff != 0) return diff;
                    return Double.compare(uuidToTotalXp.get(b), uuidToTotalXp.get(a));
                });
            } else {
                sortedUuids.sort((a, b) -> {
                    int lvlA = uuidToSkillLvls.get(a).getOrDefault(skillParam, 1);
                    int lvlB = uuidToSkillLvls.get(b).getOrDefault(skillParam, 1);
                    if (lvlB != lvlA) return lvlB - lvlA;
                    double xpA = uuidToSkillXps.get(a).getOrDefault(skillParam, 0.0);
                    double xpB = uuidToSkillXps.get(b).getOrDefault(skillParam, 0.0);
                    return Double.compare(xpB, xpA);
                });
            }

            // ---- HTML OUTPUT ----
            StringBuilder sb = new StringBuilder();
            sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Skills Highscore</title>");
            sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
            // CSS: Perkament/OSRS/Wiki-style!
            sb.append("<style>");
            sb.append("body{background:#fffdf6;color:#3b2f2f;font-family:Georgia,serif;line-height:1.6;margin:0;padding:0;}");
            sb.append("a{color:#6b3e26;text-decoration:none;}");
            sb.append("a:visited{color:#1f3a5a;}");
            sb.append("a:hover{color:#4a78b0;text-decoration:underline;}");
            sb.append(".container{display:flex;min-height:100vh;}");
            sb.append(".sidebar{width:220px;background:#fbf7ee;border-right:1.5px solid #c2a877;padding:32px 16px 0 10px;}");
            sb.append(".sidebar h3{color:#3b2f2f;margin:0 0 16px 0;font-size:19px;}");
            sb.append(".sidebar a{display:block;padding:7px 0 7px 18px;margin:2px 0;border-radius:7px;text-decoration:none;color:#3b2f2f;font-size:15px;font-weight:normal;transition:background 0.08s;}");
            sb.append(".sidebar a.active,.sidebar a:hover{background:#f0e5ce;color:#6b3e26;}");
            sb.append(".main{flex:1;padding:40px 24px 30px 24px;}");
            sb.append("table{background:#fbf7ee;border:1.5px solid #c2a877;color:#3b2f2f;border-collapse:collapse;font-size:98%;width:100%;max-width:950px;margin:0 auto 2em auto;box-shadow:0 6px 38px #0002;}");
            sb.append("th,td{border:1px solid #c2a877;padding:7px 11px;text-align:center;}");
            sb.append("th{background:#f0e5ce;color:#3b2f2f;font-weight:bold;}");
            sb.append("td{background:#fffdf6;}");
            sb.append("tr:nth-child(even) td{background:#f9f3e7;}");
            sb.append(".player{display:flex;align-items:center;gap:9px;}");
            sb.append(".player img{width:23px;height:23px;border-radius:7px;box-shadow:0 1.5px 7px #0002;vertical-align:middle;border:1.2px solid #c2a877;background:#f0e5ce;}");
            sb.append(".rank1 td{background:#f0e5ce!important;color:#b2953c!important;font-weight:bold;}");
            sb.append(".rank2 td{background:#f9f3e7!important;color:#777!important;font-weight:600;}");
            sb.append(".rank3 td{background:#fbf7ee!important;color:#be822e!important;font-weight:600;}");
            sb.append("h2{font-family:Georgia,serif;font-size:2em;font-weight:bold;text-align:center;margin-bottom:30px;color:#3b2f2f;}");
            sb.append("</style></head><body>");
            sb.append("<div class='container'>");

            // Sidebar (skillswitch)
            sb.append("<div class='sidebar'><h3>Categories</h3>");
            sb.append("<a href='/?skill=total' " + (skillParam == null || skillParam.equals("TOTAL") ? "class='active'" : "") + ">Total Level</a>");
            for (String skill : skills) {
                sb.append("<a href='/?skill=").append(skill.toLowerCase()).append("'")
                        .append(skill.equalsIgnoreCase(skillParam) ? " class='active'" : "")
                        .append(">")
                        .append(skill.charAt(0) + skill.substring(1).toLowerCase())
                        .append("</a>");
            }
            sb.append("</div>");

            // Main content
            sb.append("<div class='main'>");
            sb.append("<h2>Iaddithis Skills Highscore</h2>");
            sb.append("<table class='wikitable'>");
            sb.append("<tr><th>#</th><th>Player</th>");
            if (skillParam == null || skillParam.equals("TOTAL")) {
                sb.append("<th>Total Level</th><th>Total XP</th>");
                for (String skill : skills) sb.append("<th>").append(skill.charAt(0) + skill.substring(1).toLowerCase()).append("</th>");
            } else {
                sb.append("<th>Level</th><th>XP</th>");
            }
            sb.append("</tr>");
            int rank = 1;
            for (String uuid : sortedUuids) {
                String name = uuidToName.getOrDefault(uuid, uuid);
                String rowClass = (rank == 1 ? "rank1" : (rank == 2 ? "rank2" : (rank == 3 ? "rank3" : "")));
                sb.append("<tr class='").append(rowClass).append("'><td>").append(rank).append("</td>");
                sb.append("<td class='player'><img src='https://crafatar.com/avatars/").append(uuid).append("?overlay' alt='head'/>")
                        .append("<span>").append(htmlEscape(name)).append("</span></td>");
                if (skillParam == null || skillParam.equals("TOTAL")) {
                    sb.append("<td>").append(uuidToTotalLvl.get(uuid)).append("</td>");
                    sb.append("<td>").append(String.format("%,.0f", uuidToTotalXp.get(uuid))).append("</td>");
                    for (String skill : skills) {
                        int lvl = uuidToSkillLvls.get(uuid).getOrDefault(skill, 1);
                        sb.append("<td>").append(lvl).append("</td>");
                    }
                } else {
                    int lvl = uuidToSkillLvls.get(uuid).getOrDefault(skillParam, 1);
                    double xp = uuidToSkillXps.get(uuid).getOrDefault(skillParam, 0.0);
                    sb.append("<td>").append(lvl).append("</td>");
                    sb.append("<td>").append(String.format("%,.0f", xp)).append("</td>");
                }
                sb.append("</tr>");
                rank++;
            }
            sb.append("</table>");
            sb.append("<p style='text-align:center;font-size:13px;color:#bfa368'>Last updated: ").append(new Date()).append("</p>");
            sb.append("</div></div></body></html>");
            return sb.toString();
        }

        private static String htmlEscape(String s) {
            return s == null ? "" : s.replace("&", "&amp;")
                    .replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\"", "&quot;").replace("'", "&#39;");
        }
    }
}
