package dev.litemfinder.navigation.neoforge;

import dev.litemfinder.navigation.CollisionWalking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoadedCollisionViewTest {
    @BeforeAll static void bootstrap() {Bootstrap.bootStrap();}
    @Test void loadedGuardRunsBeforeAnyNativeBlockRead() {
        BlockGetter source=mock(BlockGetter.class);
        var view=new LoadedCollisionView(source,pos->false,100);
        assertTrue(view.cell(0,0,0).isEmpty());
        view.beginNativeQuery();view.getBlockState(BlockPos.ZERO);
        assertFalse(view.dependenciesKnown());
        verifyNoInteractions(source);
    }
    @Test void nativeSlabsStairsAndDangerousBlocksUseActualShapesAndPathTypes() {
        BlockGetter source=mock(BlockGetter.class);
        when(source.getBlockState(BlockPos.ZERO)).thenReturn(Blocks.STONE_SLAB.defaultBlockState());
        var slab=new LoadedCollisionView(source,pos->true,100).cell(0,0,0).orElseThrow();
        assertEquals(.5,slab.boxes().getFirst().maxY());
        when(source.getBlockState(BlockPos.ZERO)).thenReturn(Blocks.STONE_STAIRS.defaultBlockState());
        var stairs=new LoadedCollisionView(source,pos->true,100).cell(0,0,0).orElseThrow();
        assertTrue(stairs.boxes().size()>=2);
        when(source.getBlockState(BlockPos.ZERO)).thenReturn(Blocks.MAGMA_BLOCK.defaultBlockState());
        assertTrue(new LoadedCollisionView(source,pos->true,100).cell(0,0,0).orElseThrow().hazardous());
    }
    @Test void waterloggedFullSlabSeparatesSupportFromFluidBodyVolume() {
        BlockGetter source=mock(BlockGetter.class);
        when(source.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        when(source.getBlockState(BlockPos.ZERO)).thenReturn(Blocks.STONE_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE,SlabType.TOP).setValue(BlockStateProperties.WATERLOGGED,true));
        var cell=new LoadedCollisionView(source,pos->true,100).cell(0,0,0).orElseThrow();
        assertFalse(cell.hazardous());
        assertFalse(cell.hazards().isEmpty());
        assertEquals(1,cell.boxes().getFirst().maxY());
    }
    @Test void cachesArePerObservationAndARefreshDetectsActualGeometryChanges() {
        BlockGetter source=mock(BlockGetter.class);
        when(source.getBlockState(any())).thenReturn(Blocks.STONE.defaultBlockState());
        var captured=new LoadedCollisionView(source,pos->true,100);
        var before=captured.cell(0,0,0).orElseThrow();
        when(source.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        assertEquals(Optional.of(before),captured.cell(0,0,0));
        assertNotEquals(before,new LoadedCollisionView(source,pos->true,100).cell(0,0,0).orElseThrow());
        assertEquals(CollisionWalking.Cell.AIR,new LoadedCollisionView(source,pos->true,100).cell(0,0,0).orElseThrow());
    }
}
