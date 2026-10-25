package dev.litemfinder.neoforge.capture;

import dev.litemfinder.neoforge.api.inventory.BlockInventory;
import dev.litemfinder.neoforge.identity.ResolvedContainerIdentity;

/** Client-thread copy of menu-independent storage and its confirmed world identity. */
public record BlockCaptureRequest(ResolvedContainerIdentity identity, BlockInventory inventory) { }
