package dev.litemfinder.neoforge.identity;

/** Backward-compatible loader facade; shared scope normalization belongs to Core. */
public final class ScopeIdFactory {
    private ScopeIdFactory() { }
    public static String singleplayer(String worldIdentity) {
        return dev.litemfinder.core.model.WorldScopeId.singleplayer(worldIdentity);
    }
    public static String multiplayer(String host, int port) {
        return dev.litemfinder.core.model.WorldScopeId.multiplayer(host, port);
    }
}
