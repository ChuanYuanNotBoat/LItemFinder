package dev.litemfinder.neoforge.identity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Reads the active client connection and immediately reduces it to an opaque Core scope. */
public final class MinecraftScopeResolver {

    public Optional<String> resolve(Minecraft minecraft) {
        Objects.requireNonNull(minecraft, "minecraft must not be null");
        if (minecraft.hasSingleplayerServer()) {
            var server = minecraft.getSingleplayerServer();
            if (server == null) {
                return Optional.empty();
            }
            Path worldDirectory = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            return Optional.of(ScopeIdFactory.singleplayer(worldDirectory.toString()));
        }

        ServerData server = minecraft.getCurrentServer();
        if (server == null || !ServerAddress.isValidAddress(server.ip)) {
            return Optional.empty();
        }
        ServerAddress address = ServerAddress.parseString(server.ip);
        return Optional.of(ScopeIdFactory.multiplayer(address.getHost(), address.getPort()));
    }
}
