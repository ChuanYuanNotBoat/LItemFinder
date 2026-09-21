package dev.litemfinder.neoforge.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopeIdFactoryTest {

    @Test
    void normalizesEquivalentServerHostsAndHidesAddress() {
        String first = ScopeIdFactory.multiplayer("EXAMPLE.com.", 25565);
        String second = ScopeIdFactory.multiplayer("example.com", 25565);

        assertEquals(first, second);
        assertTrue(first.matches("scope:v1:[0-9a-f]{64}"));
        assertFalse(first.contains("example"));
    }

    @Test
    void separatesPortsAndScopeKinds() {
        String server = ScopeIdFactory.multiplayer("localhost", 25565);
        String otherPort = ScopeIdFactory.multiplayer("localhost", 25566);
        String world = ScopeIdFactory.singleplayer("localhost/25565");

        assertNotEquals(server, otherPort);
        assertNotEquals(server, world);
    }

    @Test
    void normalizesWorldPathSeparatorsWithoutExposingIdentity() {
        String windows = ScopeIdFactory.singleplayer(".\\saves\\My World\\");
        String portable = ScopeIdFactory.singleplayer("saves/My World");

        assertEquals(windows, portable);
        assertFalse(windows.contains("My World"));
    }
}
