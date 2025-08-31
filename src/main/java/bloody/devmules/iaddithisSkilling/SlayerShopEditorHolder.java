package bloody.devmules.iaddithisSkilling;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marker holder to reliably identify the Slayer Shop Editor GUI. */
public final class SlayerShopEditorHolder implements InventoryHolder {
    @Override
    public Inventory getInventory() {
        return null; // unused
    }
}
