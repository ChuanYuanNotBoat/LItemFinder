package dev.litemfinder.navigation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/** Native collision cells are supplied by the loader; body clearance and movement rules remain game-independent. */
public final class CollisionWalking {
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Box {
            if (!Double.isFinite(minX + minY + minZ + maxX + maxY + maxZ)
                    || minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("invalid collision box");
        }
        public Box moved(double x, double y, double z) { return new Box(minX+x, minY+y, minZ+z, maxX+x, maxY+y, maxZ+z); }
        public boolean intersects(Box other) {
            return minX < other.maxX && maxX > other.minX && minY < other.maxY && maxY > other.minY
                    && minZ < other.maxZ && maxZ > other.minZ;
        }
        public Box union(Box other) {
            return new Box(Math.min(minX,other.minX),Math.min(minY,other.minY),Math.min(minZ,other.minZ),
                    Math.max(maxX,other.maxX),Math.max(maxY,other.maxY),Math.max(maxZ,other.maxZ));
        }
    }

    /** Boxes use local block coordinates. Fluids/dangerous path types are not ordinary walking space. */
    public record Cell(List<Box> boxes, boolean hazardous, double speedFactor, double jumpFactor, List<Box> hazards) {
        public Cell {
            boxes = List.copyOf(boxes);
            hazards = List.copyOf(hazards);
            if (!Double.isFinite(speedFactor) || speedFactor <= 0 || !Double.isFinite(jumpFactor) || jumpFactor < 0) {
                throw new IllegalArgumentException("invalid surface factors");
            }
        }
        public Cell(List<Box> boxes, boolean hazardous, double speedFactor, double jumpFactor) {
            this(boxes,hazardous,speedFactor,jumpFactor,List.of());
        }
        public static final Cell AIR = new Cell(List.of(),false,1,1);
        public static final Cell SOLID = new Cell(List.of(new Box(0,0,0,1,1,1)),false,1,1);
    }
    @FunctionalInterface public interface World { Optional<Cell> cell(int x, int y, int z); }

    public record Profile(double width, double height, double step, double jumpVelocity, double gravity, double maxDrop) {
        public Profile {
            if (!Double.isFinite(width+height+step+jumpVelocity+gravity+maxDrop) || width<=0 || width>4
                    || height<=0 || height>8 || step<0 || step>3 || jumpVelocity<0 || gravity<=0 || maxDrop<0 || maxDrop>3) {
                throw new IllegalArgumentException("invalid walking profile");
            }
        }
        public double jumpHeight(double factor) {
            double velocity = jumpVelocity * factor, height = 0;
            for (int tick=0; tick<200 && velocity>0; tick++) {
                height+=velocity;
                if(!Double.isFinite(height)||height>16)throw new IllegalArgumentException("jump exceeds the observed walking model");
                velocity=(velocity-gravity)*0.98;
            }
            return height;
        }
    }
    public record Move(Waypoint destination, double effectiveMeters) { }
    private static final double EPSILON=1E-6;
    private final World world;
    private final Profile profile;

    public CollisionWalking(World world, Profile profile) {
        this.world=Objects.requireNonNull(world); this.profile=Objects.requireNonNull(profile);
    }

    public List<Waypoint> surfaces(Waypoint reference, double x, double z, double minY, double maxY) {
        TreeSet<Double> heights=new TreeSet<>();
        double half=profile.width()/2;
        for(int bx=floor(x-half);bx<=floor(x+half);bx++)
            for(int bz=floor(z-half);bz<=floor(z+half);bz++)
                for(int by=floor(minY)-1;by<=floor(maxY);by++) {
                    var cell=world.cell(bx,by,bz);
                    if(cell.isEmpty() || cell.orElseThrow().hazardous()) continue;
                    for(var box:cell.orElseThrow().boxes()) {
                        double top=by+box.maxY();
                        if(top>=minY-EPSILON && top<=maxY+EPSILON && bx+box.minX()<x+half && bx+box.maxX()>x-half
                                && bz+box.minZ()<z+half && bz+box.maxZ()>z-half) heights.add(top);
                    }
                }
        return heights.stream().map(y->new Waypoint(reference.scope(),reference.dimension(),x,y,z))
                .filter(point->free(body(point))).toList();
    }

    public List<Move> neighbors(Waypoint from) {
        List<Move> moves=new ArrayList<>();
        double rise=Math.max(profile.step(),Math.min(3,profile.jumpHeight(surfaceFactor(from,true))));
        for(int[] direction:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            for(Waypoint to:surfaces(from,from.x()+direction[0],from.z()+direction[1],from.y()-profile.maxDrop(),from.y()+rise)) {
                if(canMove(from,to)) moves.add(new Move(to,
                        from.distanceTo(to)/Math.min(1,Math.min(surfaceFactor(from,false),surfaceFactor(to,false)))
                        +Math.max(0,to.y()-from.y())*2+Math.max(0,from.y()-to.y())*.5));
            }
        }
        moves.sort(Comparator.comparingDouble((Move move)->move.destination().x())
                .thenComparingDouble(move->move.destination().y()).thenComparingDouble(move->move.destination().z()));
        return List.copyOf(moves);
    }

    /** Vertical/horizontal swept volumes validate steps and jump clearance without breaking or placing blocks. */
    public boolean canMove(Waypoint from, Waypoint to) {
        if(!from.scope().equals(to.scope()) || !from.dimension().equals(to.dimension())
                || Math.hypot(to.x()-from.x(),to.z()-from.z())>1.01 || to.y()<from.y()-profile.maxDrop()) return false;
        double rise=to.y()-from.y();
        double jump=profile.jumpHeight(surfaceFactor(from,true));
        if(rise>Math.max(profile.step(),Math.min(3,jump))+EPSILON) return false;
        double top=rise>profile.step()+EPSILON ? from.y()+jump : Math.max(from.y(),to.y());
        var raisedFrom=new Waypoint(from.scope(),from.dimension(),from.x(),top,from.z());
        var raisedTo=new Waypoint(to.scope(),to.dimension(),to.x(),top,to.z());
        return free(body(from).union(body(raisedFrom))) && free(body(raisedFrom).union(body(raisedTo)))
                && free(body(raisedTo).union(body(to)));
    }

    public boolean free(Box volume) {
        // Adjacent cells can have vertically extending native shapes (fences, walls or modded blocks).
        for(int x=floor(volume.minX())-1;x<=floor(volume.maxX())+1;x++)
            for(int y=floor(volume.minY())-1;y<=floor(volume.maxY())+1;y++)
                for(int z=floor(volume.minZ())-1;z<=floor(volume.maxZ())+1;z++) {
                    var cell=world.cell(x,y,z);
                    if(cell.isEmpty()) return false;
                    if(cell.orElseThrow().hazardous() && new Box(x,y,z,x+1,y+1,z+1).intersects(volume)) return false;
                    for(Box hazard:cell.orElseThrow().hazards()) if(hazard.moved(x,y,z).intersects(volume))return false;
                    for(Box box:cell.orElseThrow().boxes()) if(box.moved(x,y,z).intersects(volume)) return false;
                }
        return true;
    }

    private Box body(Waypoint point) {
        double half=profile.width()/2;
        return new Box(point.x()-half,point.y()+EPSILON,point.z()-half,
                point.x()+half,point.y()+profile.height()-EPSILON,point.z()+half);
    }

    private double surfaceFactor(Waypoint point, boolean jump) {
        double factor=1, half=profile.width()/2;
        for(int x=floor(point.x()-half);x<=floor(point.x()+half);x++)
            for(int z=floor(point.z()-half);z<=floor(point.z()+half);z++)
                for(int y=floor(point.y())-2;y<=floor(point.y());y++) {
                    var cell=world.cell(x,y,z);
                    if(cell.isEmpty()) continue;
                    for(Box box:cell.orElseThrow().boxes()) if(Math.abs(y+box.maxY()-point.y())<EPSILON
                            &&x+box.minX()<point.x()+half&&x+box.maxX()>point.x()-half
                            &&z+box.minZ()<point.z()+half&&z+box.maxZ()>point.z()-half) {
                        factor=Math.min(factor,jump?cell.orElseThrow().jumpFactor():cell.orElseThrow().speedFactor());
                    }
                }
        return factor;
    }
    private static int floor(double value) { return (int)Math.floor(value); }
}
