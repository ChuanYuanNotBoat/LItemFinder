package dev.litemfinder.navigation.neoforge;

import dev.litemfinder.navigation.NavigationResult;
import dev.litemfinder.navigation.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LocalNavigationTest {
    @BeforeAll static void bootstrap() {Bootstrap.bootStrap();}
    @Test void nativeCaptureWorkerSearchAndFinalRevalidationProduceAReachableAdjacentGoal() throws Exception {
        try(var fixture=new Fixture()) {
            var result=fixture.await(fixture.navigation.routeToBlocks(fixture.origin,List.of(fixture.target)).toCompletableFuture());
            assertEquals(NavigationResult.Status.FOUND,result.status());
            assertEquals(1,result.verifiedStops());
            assertTrue(result.checkedAt().isPresent());
            assertTrue(result.legs().getFirst().points().getLast().distanceTo(fixture.target)<2.25);
            assertEquals(Set.of(Thread.currentThread().getId()),fixture.nativeThreads);
        }
    }
    @Test void unloadingAChunkInvalidatesPublishedEvidenceAndChangesTheProviderRevision() throws Exception {
        try(var fixture=new Fixture()) {
            assertEquals(NavigationResult.Status.FOUND,fixture.await(fixture.navigation.routeToBlocks(
                    fixture.origin,List.of(fixture.target)).toCompletableFuture()).status());
            long revision=fixture.navigation.revision();
            fixture.loaded.set(false);
            for(int tick=0;tick<200&&fixture.navigation.revision()==revision;tick++)fixture.pump();
            assertTrue(fixture.navigation.revision()>revision);
            assertEquals(NavigationResult.Status.UNKNOWN,fixture.await(fixture.navigation.routeToBlocks(
                    fixture.origin,List.of(fixture.target)).toCompletableFuture()).status());
        }
    }
    @Test void changedNativeCollisionAndReplacedWorldCannotKeepAnOldRouteAlive() throws Exception {
        try(var fixture=new Fixture()) {
            var pending=fixture.navigation.routeToBlocks(fixture.origin,List.of(fixture.target)).toCompletableFuture();
            fixture.game.level=mock(ClientLevel.class);
            fixture.pump();
            assertEquals(NavigationResult.Status.UNKNOWN,pending.get(1,TimeUnit.SECONDS).status());
            assertEquals(LocalNavigation.Reason.WORLD_CHANGED,pending.get().reason());
        }
    }
    @Test void aChangedBlockInvalidatesAPreviouslyPublishedRoute() throws Exception {
        try(var fixture=new Fixture()) {
            assertEquals(NavigationResult.Status.FOUND,fixture.await(fixture.navigation.routeToBlocks(
                    fixture.origin,List.of(fixture.target)).toCompletableFuture()).status());
            long before=fixture.navigation.revision();
            fixture.closed.set(true);
            for(int tick=0;tick<200&&fixture.navigation.revision()==before;tick++)fixture.pump();
            assertTrue(fixture.navigation.revision()>before);
        }
    }
    @Test void unloadedDestinationNeverBecomesAGoalThroughTheUnknownBarrierSentinel() throws Exception {
        try(var fixture=new Fixture()) {
            when(fixture.world.getChunkSource().getChunk(anyInt(),anyInt(),eq(ChunkStatus.FULL),eq(false)))
                    .thenAnswer(call->call.getArgument(0,Integer.class)>=1?null:mock(LevelChunk.class));
            var unknown=new Waypoint("test-scope","minecraft:overworld",17,1,0);
            var result=fixture.await(fixture.navigation.routeToBlocks(fixture.origin,List.of(unknown)).toCompletableFuture());
            assertNotEquals(NavigationResult.Status.FOUND,result.status());
            assertTrue(result.legs().stream().flatMap(leg->leg.points().stream()).allMatch(point->point.x()<16));
        }
    }
    @Test void noMatchingWorldAndUnsupportedMovementReturnUnknownWithoutBlockReads() throws Exception {
        try(var fixture=new Fixture()) {
            var wrong=new Waypoint("other","minecraft:overworld",.5,1,.5);
            assertEquals(LocalNavigation.Reason.NO_WORLD,fixture.navigation.route(wrong,List.of(fixture.target))
                    .toCompletableFuture().get().reason());
            fixture.game.player.getAbilities().flying=true;
            assertEquals(LocalNavigation.Reason.UNSUPPORTED,fixture.navigation.route(fixture.origin,List.of(fixture.target))
                    .toCompletableFuture().get().reason());
            assertTrue(fixture.nativeThreads.isEmpty());
        }
    }
    @Test void replacingARequestCompletesTheOldFutureAndDoesNotPublishUnverifiedLegs() throws Exception {
        try(var fixture=new Fixture()) {
            var old=fixture.navigation.routeToBlocks(fixture.origin,List.of(fixture.target)).toCompletableFuture();
            var replacement=fixture.navigation.routeToBlocks(fixture.origin,List.of(fixture.target)).toCompletableFuture();
            assertEquals(NavigationResult.Status.UNKNOWN,old.get().status());
            assertTrue(old.get().legs().isEmpty());
            assertEquals(NavigationResult.Status.FOUND,fixture.await(replacement).status());
        }
    }
    @Test void anOrdinaryWaypointCanFinishInAirAboveTheFloorWithoutAContainerToHit() throws Exception {
        try(var fixture=new Fixture()) {
            var destination=new Waypoint("test-scope","minecraft:overworld",6.5,1,.5);
            var result=fixture.await(fixture.navigation.route(fixture.origin,List.of(destination)).toCompletableFuture());
            assertEquals(NavigationResult.Status.FOUND,result.status());
            assertTrue(result.legs().getLast().points().getLast().distanceTo(destination)<=.8);
            assertTrue(fixture.world.getBlockState(BlockPos.containing(destination.x(),destination.y(),destination.z())).isAir());
        }
    }
    @Test void exhaustedObservationBudgetReturnsOnlyAVerifiedPrefix() throws Exception {
        try(var fixture=new Fixture(10)) {
            var destination=new Waypoint("test-scope","minecraft:overworld",200.5,1,.5);
            var result=fixture.await(fixture.navigation.route(fixture.origin,List.of(destination)).toCompletableFuture());
            assertEquals(NavigationResult.Status.PARTIAL,result.status(),result::toString);
            assertEquals(LocalNavigation.Reason.BUDGET,result.reason());
            assertFalse(result.legs().isEmpty());
            assertTrue(result.legs().stream().flatMap(leg->leg.points().stream())
                    .allMatch(point->Math.abs(point.x()-fixture.origin.x())<=96));
        }
    }
    @Test void aLaterStopInAnotherDimensionKeepsTheVerifiedFirstLegAndReportsTheGap() throws Exception {
        try(var fixture=new Fixture()) {
            var second=new Waypoint("test-scope","minecraft:the_nether",8,1,0);
            var result=fixture.await(fixture.navigation.routeToBlocks(fixture.origin,List.of(fixture.target,second)).toCompletableFuture());
            assertEquals(NavigationResult.Status.PARTIAL,result.status());
            assertEquals(LocalNavigation.Reason.OTHER_DIMENSION,result.reason());
            assertEquals(1,result.verifiedStops());
            assertTrue(result.checkedAt().isPresent());
            assertEquals(1,result.legs().size());
            assertTrue(result.legs().getFirst().points().stream().allMatch(point->point.dimension().equals("minecraft:overworld")));
        }
    }
    @Test void aChangeAfterWorkerSearchButBeforePublicationRejectsTheRoute() throws Exception {
        try(var fixture=new Fixture()) {
            var pending=fixture.navigation.routeToBlocks(fixture.origin,List.of(fixture.target)).toCompletableFuture();
            for(int tick=0;tick<220&&fixture.posted.isEmpty();tick++) {fixture.navigation.tick();Thread.sleep(1);}
            assertFalse(fixture.posted.isEmpty(),"worker must have returned a path for final validation");
            fixture.closed.set(true);
            var result=fixture.await(pending);
            assertEquals(NavigationResult.Status.UNKNOWN,result.status());
            assertEquals(LocalNavigation.Reason.WORLD_CHANGED,result.reason());
            assertTrue(result.legs().isEmpty());
            assertEquals(0,result.verifiedStops());
            assertTrue(result.checkedAt().isEmpty());
        }
    }
    @Test void changedMovementAbilitiesCancelPendingAndInvalidatePublishedRoutes() throws Exception {
        try(var fixture=new Fixture()) {
            var pending=fixture.navigation.routeToBlocks(fixture.origin,List.of(fixture.target)).toCompletableFuture();
            fixture.game.player.getAbilities().flying=true;
            fixture.pump();
            assertEquals(NavigationResult.Status.UNKNOWN,pending.get(1,TimeUnit.SECONDS).status());
            fixture.game.player.getAbilities().flying=false;
            assertEquals(NavigationResult.Status.FOUND,fixture.await(fixture.navigation.routeToBlocks(
                    fixture.origin,List.of(fixture.target)).toCompletableFuture()).status());
            long revision=fixture.navigation.revision();
            when(fixture.game.player.getAttributeValue(Attributes.JUMP_STRENGTH)).thenReturn(.6);
            fixture.pump();
            assertTrue(fixture.navigation.revision()>revision);
        }
    }
    @Test void closingCompletesThePendingFutureAndRejectsFurtherRequestsWithoutReads() throws Exception {
        try(var fixture=new Fixture()) {
            var pending=fixture.navigation.routeToBlocks(fixture.origin,List.of(fixture.target)).toCompletableFuture();
            fixture.navigation.close();
            assertEquals(NavigationResult.Status.UNKNOWN,pending.get(1,TimeUnit.SECONDS).status());
            assertTrue(pending.get().legs().isEmpty());
            assertEquals(LocalNavigation.Reason.WORLD_CHANGED,fixture.navigation.route(
                    fixture.origin,List.of(fixture.target)).toCompletableFuture().get(1,TimeUnit.SECONDS).reason());
            assertTrue(fixture.nativeThreads.isEmpty());
        }
    }
    private static final class Fixture implements AutoCloseable {
        final Minecraft game=mock(Minecraft.class);
        final ClientLevel world=mock(ClientLevel.class);
        final Thread owner=Thread.currentThread();
        final Queue<Runnable> posted=new ConcurrentLinkedQueue<>();
        final Set<Long> nativeThreads=ConcurrentHashMap.newKeySet();
        final AtomicBoolean loaded=new AtomicBoolean(true);
        final AtomicBoolean closed=new AtomicBoolean();
        final MockedStatic<NavigationScopes> scopes=mockStatic(NavigationScopes.class);
        final Waypoint origin=new Waypoint("test-scope","minecraft:overworld",.5,1,.5);
        final Waypoint target=new Waypoint("test-scope","minecraft:overworld",4,1,0);
        final LocalNavigation navigation;
        Fixture() {this(200);}
        Fixture(int captureTicks) {
            try {
                game.level=world;game.player=mock(LocalPlayer.class);
                when(game.isSameThread()).thenAnswer(ignored->Thread.currentThread()==owner);
                doAnswer(call->{posted.add(call.getArgument(0));return null;}).when(game).execute(any(Runnable.class));
                scopes.when(()->NavigationScopes.current(game)).thenReturn(Optional.of("test-scope"));
                when(game.player.getAbilities()).thenReturn(new Abilities());
                when(game.player.getDimensions(Pose.STANDING)).thenReturn(EntityDimensions.fixed(.6f,1.8f));
                when(game.player.maxUpStep()).thenReturn(.6f);
                when(game.player.getAttributeValue(Attributes.JUMP_STRENGTH)).thenReturn(.42);
                when(game.player.getAttributeValue(Attributes.GRAVITY)).thenReturn(.08);
                when(game.player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
                when(world.dimension()).thenReturn(Level.OVERWORLD);
                when(world.getWorldBorder()).thenReturn(new WorldBorder());
                var chunks=mock(ClientChunkCache.class);
                when(world.getChunkSource()).thenReturn(chunks);
                var chunk=mock(LevelChunk.class);
                when(chunks.getChunk(anyInt(),anyInt(),eq(ChunkStatus.FULL),eq(false))).thenAnswer(ignored->loaded.get()?chunk:null);
                when(world.getBlockState(any())).thenAnswer(call->{
                    nativeThreads.add(Thread.currentThread().getId());
                    if(Thread.currentThread()!=owner)throw new IllegalStateException("live world read on worker");
                    BlockPos pos=call.getArgument(0);
                    return pos.getY()==0||(closed.get()&&pos.getX()==1&&pos.getY()<=3)?Blocks.STONE.defaultBlockState():
                            pos.equals(new BlockPos(4,1,0))?Blocks.BARREL.defaultBlockState():Blocks.AIR.defaultBlockState();
                });
                navigation=new LocalNavigation(game,captureTicks);
            } catch(RuntimeException|Error failure) {scopes.close();throw failure;}
        }
        void pump() {
            for(Runnable task;(task=posted.poll())!=null;)task.run();
            navigation.tick();
        }
        LocalNavigation.Result await(CompletableFuture<LocalNavigation.Result> future) throws Exception {
            for(int tick=0;tick<350&&!future.isDone();tick++) {pump();Thread.sleep(1);}
            return future.get(1,TimeUnit.SECONDS);
        }
        @Override public void close() {navigation.close();scopes.close();}
    }
}
