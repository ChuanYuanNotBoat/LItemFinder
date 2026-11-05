package dev.litemfinder.navigation.neoforge;

import dev.litemfinder.core.model.WorldScopeId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.level.storage.LevelResource;

import java.util.Optional;

final class NavigationScopes {
    static Optional<String> current(Minecraft game) {
        if(game.hasSingleplayerServer()) {
            var server=game.getSingleplayerServer();
            return server==null?Optional.empty():Optional.of(WorldScopeId.singleplayer(
                    server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString()));
        }
        var server=game.getCurrentServer();
        if(server==null||!ServerAddress.isValidAddress(server.ip))return Optional.empty();
        var address=ServerAddress.parseString(server.ip);
        return Optional.of(WorldScopeId.multiplayer(address.getHost(),address.getPort()));
    }
}
