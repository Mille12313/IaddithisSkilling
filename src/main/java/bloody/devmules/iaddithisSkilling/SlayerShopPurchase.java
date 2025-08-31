package bloody.devmules.iaddithisSkilling;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SlayerShopPurchase {
    private SlayerShopPurchase(){}

    @SuppressWarnings("unchecked")
    public static boolean deliver(Player p, Map<String,Object> itemCfg) {
        Map<String,Object> give = (Map<String, Object>) itemCfg.getOrDefault("give", Map.of("type","commands"));
        String type = String.valueOf(give.getOrDefault("type", "commands")).toLowerCase();

        try {
            switch (type) {
                case "vanilla" -> {
                    String mat = String.valueOf(give.get("material"));
                    int amount = Integer.parseInt(String.valueOf(give.getOrDefault("amount", 1)));
                    ItemStack stack = new ItemStack(Material.valueOf(mat), amount);
                    HashMap<Integer, ItemStack> left = p.getInventory().addItem(stack);
                    if (!left.isEmpty()) p.getWorld().dropItemNaturally(p.getLocation(), left.values().iterator().next());
                    return true;
                }
                case "itemsadder" -> {
                    String ia = String.valueOf(give.get("item"));
                    int amount = Integer.parseInt(String.valueOf(give.getOrDefault("amount", 1)));
                    CustomStack cs = CustomStack.getInstance(ia);
                    if (cs == null) return false;
                    ItemStack stack = cs.getItemStack().clone();
                    stack.setAmount(amount);
                    HashMap<Integer, ItemStack> left = p.getInventory().addItem(stack);
                    if (!left.isEmpty()) p.getWorld().dropItemNaturally(p.getLocation(), left.values().iterator().next());
                    return true;
                }
                default -> {
                    List<String> cmds = (List<String>) give.getOrDefault("commands", List.of());
                    for (String raw : cmds) {
                        String cmd = raw.replace("%player%", p.getName());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    }
                    return true;
                }
            }
        } catch (Throwable t) {
            return false;
        }
    }
}
