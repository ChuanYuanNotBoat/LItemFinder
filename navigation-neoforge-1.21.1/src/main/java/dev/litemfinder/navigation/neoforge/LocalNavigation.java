package dev.litemfinder.navigation.neoforge;

import dev.litemfinder.navigation.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Standalone, read-only service. Native reads are incrementally performed by tick(), never by search workers. */
public final class LocalNavigation implements AutoCloseable {
    public enum Reason { NONE, NO_WORLD, OTHER_DIMENSION, UNOBSERVED, BUDGET, WORLD_CHANGED, UNSUPPORTED }
    public record Result(NavigationResult.Status status,List<WalkPath> legs,Reason reason,long revision,
                         int verifiedStops,Optional<Instant> checkedAt) {
        public Result(NavigationResult.Status status,List<WalkPath> legs,Reason reason,long revision) {
            this(status,legs,reason,revision,0,Optional.empty());
        }
        public Result { legs=List.copyOf(legs);Objects.requireNonNull(checkedAt);if(verifiedStops<0)throw new IllegalArgumentException("negative stop count"); }
        public double estimatedSeconds() {return legs.stream().mapToDouble(WalkPath::estimatedSeconds).sum();}
    }
    private static final int MAX_NODES=4096,MAX_CELLS=32768,MAX_TICKS=200;
    private final Minecraft game;
    private final int captureTicks;
    private final ExecutorService worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),task->{
        Thread thread=new Thread(task,"litemnavigation-search");thread.setDaemon(true);return thread;
    },new ThreadPoolExecutor.DiscardOldestPolicy());
    private final AtomicLong revision=new AtomicLong();
    private Job job;
    private ClientLevel level;
    private Map<BlockPos,CollisionWalking.Cell> watched=Map.of();
    private Iterator<Map.Entry<BlockPos,CollisionWalking.Cell>> checking=Collections.emptyIterator();
    private LoadedCollisionView watchReader;
    private CollisionWalking.Profile watchedProfile;
    private volatile boolean closed;

    public LocalNavigation(Minecraft game) {this(game,MAX_TICKS);}
    LocalNavigation(Minecraft game,int captureTicks) {
        this.game=Objects.requireNonNull(game);
        if(captureTicks<1||captureTicks>MAX_TICKS)throw new IllegalArgumentException("capture budget must be between 1 and "+MAX_TICKS);
        this.captureTicks=captureTicks;
    }
    public long revision() {return revision.get();}

    /** Ordinary waypoints identify a standing position, including empty space above a floor. */
    public CompletionStage<Result> route(Waypoint origin,List<Waypoint> stops) {
        return request(origin,stops,false);
    }

    /** Container targets identify a block; the route ends at a verified nearby interaction position. */
    public CompletionStage<Result> routeToBlocks(Waypoint origin,List<Waypoint> stops) {
        return request(origin,stops,true);
    }

    private CompletionStage<Result> request(Waypoint origin,List<Waypoint> stops,boolean blockTargets) {
        Objects.requireNonNull(origin);
        List<Waypoint> targets=List.copyOf(stops);
        CompletableFuture<Result> future=new CompletableFuture<>();
        if(closed) {future.complete(new Result(NavigationResult.Status.UNKNOWN,List.of(),Reason.WORLD_CHANGED,revision()));return future;}
        Runnable begin=()->begin(origin,targets,blockTargets,future);
        if(game.isSameThread())begin.run();else game.execute(begin);
        return future;
    }

    private void begin(Waypoint origin,List<Waypoint> stops,boolean blockTargets,CompletableFuture<Result> future) {
        if(closed) {future.complete(new Result(NavigationResult.Status.UNKNOWN,List.of(),Reason.WORLD_CHANGED,revision()));return;}
        if(job!=null) {job.legs.clear();finish(job,Reason.WORLD_CHANGED);}
        watched=Map.of();checking=Collections.emptyIterator();
        if(game.level==null||game.player==null||!NavigationScopes.current(game).filter(origin.scope()::equals).isPresent()) {
            future.complete(new Result(NavigationResult.Status.UNKNOWN,List.of(),Reason.NO_WORLD,revision()));return;
        }
        if(!game.level.dimension().location().toString().equals(origin.dimension())||stops.isEmpty()
                ||stops.stream().anyMatch(stop->!stop.scope().equals(origin.scope()))) {
            future.complete(new Result(NavigationResult.Status.UNKNOWN,List.of(),Reason.OTHER_DIMENSION,revision()));return;
        }
        if(game.player.getAbilities().flying||game.player.isInWater()||game.player.isInLava()) {
            future.complete(new Result(NavigationResult.Status.UNKNOWN,List.of(),Reason.UNSUPPORTED,revision()));return;
        }
        if(level!=null&&level!=game.level)revision.incrementAndGet();
        level=game.level;
        var profile=profile();
        if(profile==null) {future.complete(new Result(NavigationResult.Status.UNKNOWN,List.of(),Reason.UNSUPPORTED,revision()));return;}
        job=new Job(origin,stops,blockTargets,future,profile,level,revision());
        startLeg(job,origin);
    }

    private CollisionWalking.Profile profile() {
        if(game.player==null||game.player.getAbilities().flying||game.player.isInWater()||game.player.isInLava())return null;
        var size=game.player.getDimensions(Pose.STANDING);
        try {
            return new CollisionWalking.Profile(size.width(),size.height(),game.player.maxUpStep(),
                    Math.max(0,game.player.getAttributeValue(Attributes.JUMP_STRENGTH)+game.player.getJumpBoostPower()),
                    game.player.getAttributeValue(Attributes.GRAVITY),1.25);
        } catch(IllegalArgumentException unsupported) {return null;}
    }

    private void startLeg(Job current,Waypoint start) {
        if(current.index>=current.stops.size()) {verify(current);return;}
        Waypoint target=current.stops.get(current.index);
        if(!start.dimension().equals(target.dimension())) {finish(current,Reason.OTHER_DIMENSION);return;}
        current.start=start;current.target=target;current.graph=new PassabilityCache(MAX_NODES*6);
        current.view=view(current.world,current.origin);
        current.walking=new CollisionWalking(current.view,current.profile);
        current.open=new PriorityQueue<>(Comparator.comparingDouble((Waypoint point)->point.distanceTo(target))
                .thenComparingDouble(Waypoint::x).thenComparingDouble(Waypoint::y).thenComparingDouble(Waypoint::z));
        current.seen=new HashSet<>();current.expanded=new HashSet<>();current.proofs=new HashMap<>();
        current.goals=new HashSet<>();current.searching=false;current.seeding=true;
    }

    public void tick() {
        if(level!=null&&level!=game.level) {invalidate();level=game.level;}
        long deadline=System.nanoTime()+2_000_000;
        if(job==null) {checkPublished(deadline);return;}
        Job current=job;
        if(current.world!=game.level||game.player==null||!Objects.equals(current.profile,profile())) {invalidate();return;}
        if(++current.ticks>captureTicks&&!current.verifying&&!current.searching) {
            if(!current.seeding) {current.finishReason=Reason.BUDGET;search(current);}else finish(current,Reason.BUDGET);
            return;
        }
        if(current.verifying) {
            if(++current.verifyTicks>100) {current.legs.clear();finish(current,Reason.BUDGET);return;}
            verifyBatch(current,deadline);return;
        }
        if(current.searching)return;
        try {
            if(current.seeding) {
                current.view.beginTracking();
                var starts=current.walking.surfaces(current.start,Math.floor(current.start.x())+.5,Math.floor(current.start.z())+.5,
                        current.start.y()-.6,current.start.y()+.6);
                var seed=starts.stream().filter(point->current.walking.canMove(current.start,point))
                        .min(Comparator.comparingDouble(current.start::distanceTo));
                if(seed.isEmpty()) {finish(current,Reason.UNOBSERVED);return;}
                current.seed=seed.orElseThrow();current.graph.observe(current.start,Instant.now());
                if(!current.start.equals(current.seed))current.graph.observeDirectedConnection(current.start,current.seed,
                        current.start.distanceTo(current.seed),Instant.now());
                current.open.add(current.seed);current.seen.add(current.seed);
                current.proofs.put(current.start,current.view.tracked());current.seeding=false;
            }
            for(int count=0;count<8&&!current.open.isEmpty()&&System.nanoTime()<deadline;count++) {
                Waypoint point=current.open.remove();
                if(!current.expanded.add(point))continue;
                current.graph.observe(point,Instant.now());
                current.view.beginTracking();
                if(goal(current,point))current.goals.add(point);
                for(var move:current.walking.neighbors(point)) {
                    current.graph.observeDirectedConnection(point,move.destination(),move.effectiveMeters(),Instant.now());
                    if(current.seen.size()<MAX_NODES*4&&current.seen.add(move.destination()))current.open.add(move.destination());
                }
                current.proofs.put(point,current.view.tracked());
                if(!current.goals.isEmpty()||current.expanded.size()>=MAX_NODES)break;
            }
            if(!current.goals.isEmpty()||current.open.isEmpty()||current.expanded.size()>=MAX_NODES) search(current);
        } catch(RuntimeException|LinkageError optionalWorldFailure) {finish(current,Reason.UNOBSERVED);}
    }

    private boolean goal(Job current,Waypoint point) {
        var target=current.target;
        if(!current.blockTargets) {
            // The node is already proven to have support and body clearance; also check the last short approach.
            return point.distanceTo(target)<=.8&&current.walking.canMove(point,target);
        }
        if(point.distanceTo(target)>2.25||Math.abs(point.y()-target.y())>1.5)return false;
        Vec3 eye=new Vec3(point.x(),point.y()+Math.min(1.62,current.profile.height()*.9),point.z());
        current.view.beginNativeQuery();
        var hit=current.view.clip(new ClipContext(eye,new Vec3(target.x()+.5,target.y()+.5,target.z()+.5),
                ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,game.player));
        return current.view.dependenciesKnown()&&hit.getType()==HitResult.Type.BLOCK
                &&hit.getBlockPos().equals(BlockPos.containing(target.x(),target.y(),target.z()));
    }

    private void search(Job current) {
        current.searching=true;
        var snapshot=current.graph.copy();var start=current.start;var goals=Set.copyOf(current.goals);var hint=current.target;
        var walker=new TravelerProfile(Set.of(TravelMode.WALK),Map.of(TravelMode.WALK,4.317),Set.of());
        CompletableFuture.supplyAsync(()->new WalkAStar(snapshot).findAny(start,goals,hint,walker,
                Instant.now(),Duration.ofSeconds(30),MAX_NODES),worker).whenComplete((path,failure)->game.execute(()->{
            if(job!=current||current.world!=game.level||current.revision!=revision())return;
            if(failure!=null||path.points().isEmpty()) {finish(current,Reason.UNOBSERVED);return;}
            current.legs.add(path);
            Set<BlockPos> positions=new HashSet<>();
            path.points().forEach(point->positions.addAll(current.proofs.getOrDefault(point,Set.of())));
            current.observed.putAll(current.view.observed(positions));
            if(path.status()!=NavigationResult.Status.FOUND) {current.incomplete=true;verify(current);return;}
            current.index++;
            if(current.index<current.stops.size())startLeg(current,path.points().getLast());else verify(current);
        }));
    }

    private void verify(Job current) {
        current.verifying=true;current.verification=current.observed.entrySet().iterator();
        current.validationReader=view(current.world,current.origin);
    }

    private void verifyBatch(Job current,long deadline) {
        for(int count=0;count<256&&current.verification.hasNext()&&System.nanoTime()<deadline;count++) {
            var entry=current.verification.next();var pos=entry.getKey();
            if(!current.validationReader.cell(pos.getX(),pos.getY(),pos.getZ()).filter(entry.getValue()::equals).isPresent()) {
                revision.incrementAndGet();current.legs.clear();finish(current,Reason.WORLD_CHANGED);return;
            }
        }
        if(!current.verification.hasNext()) {
            watched=Map.copyOf(current.observed);checking=Collections.emptyIterator();watchedProfile=current.profile;
            current.verified=true;
            finish(current,current.finishReason!=Reason.NONE?current.finishReason:current.incomplete?Reason.UNOBSERVED:Reason.NONE);
        }
    }

    private void checkPublished(long deadline) {
        if(watched.isEmpty()||game.level==null)return;
        if(!Objects.equals(watchedProfile,profile())) {invalidate();return;}
        if(!checking.hasNext()) {checking=watched.entrySet().iterator();watchReader=view(level,null);}
        for(int count=0;count<256&&checking.hasNext()&&System.nanoTime()<deadline;count++) {
            var entry=checking.next();var pos=entry.getKey();
            if(!watchReader.cell(pos.getX(),pos.getY(),pos.getZ()).filter(entry.getValue()::equals).isPresent()) {invalidate();return;}
        }
    }

    private LoadedCollisionView view(ClientLevel world,Waypoint origin) {
        return new LoadedCollisionView(world,pos->!world.isOutsideBuildHeight(pos)
                &&(origin==null||(Math.abs(pos.getX()-origin.x())<=96&&Math.abs(pos.getZ()-origin.z())<=96
                &&Math.abs(pos.getY()-origin.y())<=32))
                &&world.getWorldBorder().getMinX()<=pos.getX()&&world.getWorldBorder().getMaxX()>=pos.getX()+1
                &&world.getWorldBorder().getMinZ()<=pos.getZ()&&world.getWorldBorder().getMaxZ()>=pos.getZ()+1
                &&world.getChunkSource().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false)!=null,MAX_CELLS);
    }

    public void invalidate() {
        revision.incrementAndGet();watched=Map.of();checking=Collections.emptyIterator();
        if(job!=null) {job.legs.clear();finish(job,Reason.WORLD_CHANGED);}
    }
    private void finish(Job current,Reason reason) {
        if(!current.legs.isEmpty()&&!current.verified&&reason!=Reason.WORLD_CHANGED) {
            current.incomplete=true;current.finishReason=reason;verify(current);return;
        }
        if(job==current)job=null;
        var status=reason==Reason.NONE?NavigationResult.Status.FOUND:
                current.legs.isEmpty()?NavigationResult.Status.UNKNOWN:NavigationResult.Status.PARTIAL;
        current.future.complete(new Result(status,List.copyOf(current.legs),reason,revision(),
                current.verified&&!current.legs.isEmpty()?current.index:0,
                current.verified&&!current.legs.isEmpty()?Optional.of(Instant.now()):Optional.empty()));
    }
    @Override public void close() {closed=true;invalidate();worker.shutdownNow();}

    private static final class Job {
        final Waypoint origin;final List<Waypoint> stops;final CompletableFuture<Result> future;
        final boolean blockTargets;
        final CollisionWalking.Profile profile;final ClientLevel world;final long revision;
        final List<WalkPath> legs=new ArrayList<>();final Map<BlockPos,CollisionWalking.Cell> observed=new HashMap<>();
        int index,ticks,verifyTicks;boolean seeding,searching,verifying,incomplete,verified;
        Reason finishReason=Reason.NONE;
        Waypoint start,seed,target;PassabilityCache graph;LoadedCollisionView view,validationReader;CollisionWalking walking;
        PriorityQueue<Waypoint> open;Set<Waypoint> seen,expanded,goals;Map<Waypoint,Set<BlockPos>> proofs;
        Iterator<Map.Entry<BlockPos,CollisionWalking.Cell>> verification;
        Job(Waypoint origin,List<Waypoint> stops,boolean blockTargets,CompletableFuture<Result> future,CollisionWalking.Profile profile,ClientLevel world,long revision) {
            this.origin=origin;this.stops=stops;this.blockTargets=blockTargets;this.future=future;this.profile=profile;this.world=world;this.revision=revision;
        }
    }
}
