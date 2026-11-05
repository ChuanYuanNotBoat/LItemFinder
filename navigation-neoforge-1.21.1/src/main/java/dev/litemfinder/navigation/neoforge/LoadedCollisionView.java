package dev.litemfinder.navigation.neoforge;

import dev.litemfinder.navigation.CollisionWalking;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.*;
import java.util.function.Predicate;

/** Client-thread native conversion. Unknown dependencies are barriers to proof, never assumed empty air. */
public final class LoadedCollisionView implements CollisionWalking.World, BlockGetter {
    private final BlockGetter source;
    private final Predicate<BlockPos> loaded;
    private final int maxCells;
    private final Map<BlockPos,Optional<CollisionWalking.Cell>> cells=new LinkedHashMap<>();
    private final Map<BlockPos,BlockState> states=new HashMap<>();
    private final Set<BlockPos> tracked=new HashSet<>();
    private boolean unknownDependency;

    public LoadedCollisionView(BlockGetter source, Predicate<BlockPos> loaded, int maxCells) {
        this.source=source;this.loaded=loaded;this.maxCells=maxCells;
    }
    public void beginTracking() { tracked.clear(); }
    public void beginNativeQuery() { unknownDependency=false; }
    public boolean dependenciesKnown() { return !unknownDependency; }
    public Set<BlockPos> tracked() { return Set.copyOf(tracked); }
    public Map<BlockPos,CollisionWalking.Cell> observed(Collection<BlockPos> positions) {
        Map<BlockPos,CollisionWalking.Cell> result=new LinkedHashMap<>();
        for(var pos:positions) { var cell=cells.get(pos); if(cell!=null)cell.ifPresent(value->result.put(pos,value)); }
        return Map.copyOf(result);
    }
    @Override public Optional<CollisionWalking.Cell> cell(int x,int y,int z) {
        BlockPos pos=new BlockPos(x,y,z);tracked.add(pos);
        var cached=cells.get(pos);
        if(cached!=null)return cached;
        if(cells.size()>=maxCells||!loaded.test(pos))return Optional.empty();
        unknownDependency=false;
        Optional<CollisionWalking.Cell> result;
        try {
            BlockState state=getBlockState(pos);
            PathType type=Types.read(this,pos);
            var boxes=state.getCollisionShape(this,pos,CollisionContext.empty()).toAabbs().stream()
                    .map(box->new CollisionWalking.Box(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ)).toList();
            boolean unsupportedShape=boxes.stream().anyMatch(box->box.minX() < -1||box.minY() < -1||box.minZ() < -1
                    ||box.maxX()>2||box.maxY()>2||box.maxZ()>2);
            boolean danger=type.name().startsWith("DAMAGE_")||type.name().startsWith("DANGER_")
                    ||type==PathType.POWDER_SNOW;
            var fluid=state.getFluidState();
            var hazards=fluid.isEmpty()?List.<CollisionWalking.Box>of():List.of(new CollisionWalking.Box(0,0,0,1,
                    fluid.getHeight(this,pos),1));
            var value=new CollisionWalking.Cell(boxes,danger,state.getBlock().getSpeedFactor(),state.getBlock().getJumpFactor(),hazards);
            result=unknownDependency||unsupportedShape?Optional.empty():Optional.of(value);
        } catch(RuntimeException|LinkageError unreadable) { result=Optional.empty(); }
        cells.put(pos,result);return result;
    }
    @Override public BlockState getBlockState(BlockPos pos) {
        if(!loaded.test(pos)||states.size()>=maxCells*4) { unknownDependency=true;return Blocks.BARRIER.defaultBlockState(); }
        return states.computeIfAbsent(pos.immutable(),source::getBlockState);
    }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) {
        if(!loaded.test(pos)) {unknownDependency=true;return null;} return source.getBlockEntity(pos);
    }
    @Override public int getHeight() { return source.getHeight(); }
    @Override public int getMinBuildHeight() { return source.getMinBuildHeight(); }
    private static final class Types extends WalkNodeEvaluator {
        static PathType read(BlockGetter world,BlockPos pos) {return getPathTypeFromState(world,pos);}
    }
}
