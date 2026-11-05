package dev.litemfinder.navigation.neoforge;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.litemfinder.navigation.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

import java.util.List;

/** Independent client Mod. Requests describe walking routes and never move or control the player. */
@Mod(value="litemnavigation",dist=Dist.CLIENT)
public final class LItemNavigationClient {
    private static LocalNavigation navigation;
    private boolean probeReady,probeExiting;
    private long commandRequest;
    public LItemNavigationClient(IEventBus modBus) {
        navigation=new LocalNavigation(Minecraft.getInstance());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event)->navigation.tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event)->navigation.invalidate());
        NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent event)->{
            navigation.close();
            if(probeReady)LogUtils.getLogger().info("NAVIGATION STANDALONE PROBE SHUTDOWN");
        });
        NeoForge.EVENT_BUS.addListener(this::commands);
        if(Boolean.getBoolean("litemnavigation.standaloneProbe")) {
            modBus.addListener(this::probe);
            NeoForge.EVENT_BUS.addListener(this::probeFrame);
        }
    }
    public static LocalNavigation service() {
        if(navigation==null)throw new IllegalStateException("navigation client is not initialized");
        return navigation;
    }
    private void probe(FMLLoadCompleteEvent event) {
        event.enqueueWork(()->{
            if(ModList.get().isLoaded("litemfinder"))throw new IllegalStateException("standalone probe contains inventory Mod");
            var point=new Waypoint("probe","minecraft:overworld",0,64,0);
            var result=service().route(point,List.of(point)).toCompletableFuture().getNow(null);
            if(result==null||result.reason()!=LocalNavigation.Reason.NO_WORLD)throw new IllegalStateException("standalone API unavailable");
            probeReady=true;
            LogUtils.getLogger().info("NAVIGATION STANDALONE PROBE PASSED: no inventory Mod, standalone waypoint API ready");
        });
    }
    private void probeFrame(RenderFrameEvent.Post event) {
        Minecraft game=Minecraft.getInstance();
        if(probeReady&&!probeExiting&&game.screen instanceof TitleScreen&&game.getOverlay()==null) {
            probeExiting=true;
            LogUtils.getLogger().info("NAVIGATION STANDALONE PROBE MAIN MENU READY");
            game.stop();
        }
    }
    private void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("litemnav")
                .then(Commands.literal("to").then(Commands.argument("x",IntegerArgumentType.integer())
                .then(Commands.argument("y",IntegerArgumentType.integer()).then(Commands.argument("z",IntegerArgumentType.integer())
                .executes(context->{
                    Minecraft game=Minecraft.getInstance();
                    var scope=NavigationScopes.current(game);
                    if(game.player==null||game.level==null||scope.isEmpty())return 0;
                    String dimension=game.level.dimension().location().toString();
                    var pos=game.player.position();
                    var origin=new Waypoint(scope.orElseThrow(),dimension,pos.x,pos.y,pos.z);
                    var target=new Waypoint(scope.orElseThrow(),dimension,IntegerArgumentType.getInteger(context,"x"),
                            IntegerArgumentType.getInteger(context,"y"),IntegerArgumentType.getInteger(context,"z"));
                    var requestedWorld=game.level;
                    long request=++commandRequest;
                    context.getSource().sendSuccess(()->Component.translatable("litemnavigation.searching"),false);
                    service().route(origin,List.of(target)).thenAccept(result->game.execute(()->{
                        if(request==commandRequest&&game.level==requestedWorld&&game.player!=null)game.player.displayClientMessage(Component.translatable("litemnavigation.result",
                                Component.translatable("litemnavigation.status."+result.status().name().toLowerCase(java.util.Locale.ROOT)),
                                Math.round(result.estimatedSeconds()),
                                Component.translatable("litemnavigation.reason."+result.reason().name().toLowerCase(java.util.Locale.ROOT))),false);
                    }));
                    return 1;
                }))))));
    }
}
