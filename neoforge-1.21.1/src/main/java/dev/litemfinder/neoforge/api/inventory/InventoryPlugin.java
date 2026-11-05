package dev.litemfinder.neoforge.api.inventory;

/**
 * Optional client integration declared in litemfinder.plugins.json.
 * The entrypoint is loaded only after its declared mod/version requirements match.
 * No game objects may be retained by the plugin beyond a client-thread read.
 */
@FunctionalInterface
public interface InventoryPlugin {
    void register(InventoryPluginRegistration registration);
}
