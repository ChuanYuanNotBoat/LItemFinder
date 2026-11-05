package dev.litemfinder.navigation;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CollisionWalkingTest {
    private static final CollisionWalking.Profile PLAYER=new CollisionWalking.Profile(.6,1.8,.6,.42,.08,1.25);
    private static final CollisionWalking.Cell AIR=CollisionWalking.Cell.AIR;
    private static final CollisionWalking.Cell SOLID=CollisionWalking.Cell.SOLID;
    private record Pos(int x,int y,int z) { }
    private static CollisionWalking world(Map<Pos,CollisionWalking.Cell> cells) {
        return new CollisionWalking((x,y,z)->Optional.of(cells.getOrDefault(new Pos(x,y,z),y==0?SOLID:AIR)),PLAYER);
    }
    @Test void ordinaryGroundHasFourCardinalMovesWithoutDiagonalCornerCutting() {
        var moves=world(Map.of()).neighbors(point(.5,1,.5));
        assertEquals(4,moves.size());
        assertTrue(moves.stream().allMatch(move->move.effectiveMeters()==1));
    }
    @Test void halfSlabsAndStairsUseActualFractionalSurfacesAndBodyClearance() {
        var slab=new CollisionWalking.Cell(List.of(new CollisionWalking.Box(0,0,0,1,.5,1)),false,1,1);
        var walking=world(Map.of(new Pos(1,1,0),slab));
        assertTrue(walking.neighbors(point(.5,1,.5)).stream().anyMatch(move->move.destination().equals(point(1.5,1.5,.5))));
        var stairs=new CollisionWalking.Cell(List.of(new CollisionWalking.Box(0,0,0,1,.5,1),
                new CollisionWalking.Box(.5,.5,0,1,1,1)),false,1,1);
        var steps=world(Map.of(new Pos(1,1,0),stairs));
        assertTrue(steps.neighbors(point(.5,1,.5)).stream().anyMatch(move->move.destination().equals(point(1.5,2,.5))));
        assertFalse(steps.neighbors(point(.5,1,.5)).stream().anyMatch(move->move.destination().equals(point(1.5,1.5,.5))));
    }
    @Test void cannotWalkThroughTallWallsOrJumpThroughALowCeiling() {
        var wall=world(Map.of(new Pos(1,1,0),SOLID,new Pos(1,2,0),SOLID));
        assertFalse(wall.neighbors(point(.5,1,.5)).stream().anyMatch(move->move.destination().x()>1));
        var ceiling=world(Map.of(new Pos(1,1,0),SOLID,new Pos(0,4,0),SOLID));
        assertFalse(ceiling.canMove(point(.5,1,.5),point(1.5,2,.5)));
    }
    @Test void missingCellsAreUnknownAndNeverAssumedToBeAir() {
        var walking=new CollisionWalking((x,y,z)->x>=2?Optional.empty():Optional.of(y==0?SOLID:AIR),PLAYER);
        assertFalse(walking.neighbors(point(.5,1,.5)).stream().anyMatch(move->move.destination().x()>1));
    }
    @Test void hazardousSurfacesAndFluidVolumesDoNotBecomeWalkableEmptySpace() {
        var fire=new CollisionWalking.Cell(List.of(),true,1,1);
        var walking=world(Map.of(new Pos(1,1,0),fire));
        assertFalse(walking.neighbors(point(.5,1,.5)).stream().anyMatch(move->move.destination().x()>1));
        var wetSlab=new CollisionWalking.Cell(List.of(new CollisionWalking.Box(0,0,0,1,.5,1)),false,1,1,
                List.of(new CollisionWalking.Box(0,0,0,1,.9,1)));
        assertFalse(world(Map.of(new Pos(1,1,0),wetSlab)).neighbors(point(.5,1,.5)).stream()
                .anyMatch(move->move.destination().x()>1));
        var wetFull=new CollisionWalking.Cell(SOLID.boxes(),false,1,1,
                List.of(new CollisionWalking.Box(0,0,0,1,.9,1)));
        assertTrue(world(Map.of(new Pos(1,1,0),wetFull)).neighbors(point(.5,1,.5)).stream()
                .anyMatch(move->move.destination().equals(point(1.5,2,.5))));
    }
    @Test void honeyLikeSurfacesChangeCostAndJumpAbilityRatherThanBeingAssumedNormal() {
        var honey=new CollisionWalking.Cell(SOLID.boxes(),false,.4,.5);
        var walking=world(Map.of(new Pos(0,0,0),honey,new Pos(1,1,0),SOLID));
        assertFalse(walking.canMove(point(.5,1,.5),point(1.5,2,.5)));
        assertTrue(walking.neighbors(point(.5,1,.5)).stream().allMatch(move->move.effectiveMeters()>=2.5));
    }
    @Test void movementDirectionsCanDifferAndExcessiveFallsAreRejected() {
        var weakJump=new CollisionWalking.Profile(.6,1.8,.6,.2,.08,1.25);
        var walking=new CollisionWalking((x,y,z)->Optional.of(y==0||(x==0&&y==1)?SOLID:AIR),weakJump);
        assertTrue(walking.canMove(point(.5,2,.5),point(1.5,1,.5)));
        assertFalse(walking.canMove(point(1.5,1,.5),point(.5,2,.5)));
        assertFalse(walking.canMove(point(.5,3,.5),point(1.5,1,.5)));
    }
    private static Waypoint point(double x,double y,double z) {return new Waypoint("scope","world",x,y,z);}
}
