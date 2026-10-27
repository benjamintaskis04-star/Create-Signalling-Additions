package com.benjamintaskis.createsignalextensions.diagnostic;

import com.simibubi.create.Create;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GraphExportCommand {
    private GraphExportCommand() {}
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private static volatile AtomicBoolean cancellation;
    private static final ExecutorService WORKER=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),task->{
        Thread thread=new Thread(task,"CSA graph exporter");thread.setDaemon(true);return thread;
    },new ThreadPoolExecutor.AbortPolicy());

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(command());
    }
    static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("csa").requires(source->source.hasPermission(2))
                .then(Commands.literal("graph").then(Commands.literal("export").requires(source->source.hasPermission(2))
                        .executes(context->export(context.getSource()))));
    }
    public static void stopped(ServerStoppedEvent ignored) {var token=cancellation;if(token!=null)token.set(true);}
    static boolean acquire(){return BUSY.compareAndSet(false,true);}
    static void release(){BUSY.set(false);}
    static void runJob(Runnable action){try{action.run();}finally{release();}}

    private static int export(CommandSourceStack source) {
        // Also enforce here: Brigadier merges requirements when another mod owns the same path.
        if(!source.hasPermission(2)){source.sendFailure(Component.literal("Operator permission level 2 is required."));return 0;}
        if(!acquire()){source.sendFailure(Component.literal("A CSA graph export is already running."));return 0;}
        var server=source.getServer();var token=new AtomicBoolean();cancellation=token;
        try {
            // The command runs on the server thread. Only immutable DTOs cross this boundary.
            var budget=new GraphExportSnapshot.Budget(50);
            var snapshot=GraphExportSnapshot.capture(Create.RAILWAYS.trackNetworks,server.getTickCount(),budget);
            snapshot=GraphExportBoards.capture(server,snapshot,budget);
            var captured=snapshot;
            var output=server.getWorldPath(LevelResource.ROOT).resolve("csa-graph-exports");
            WORKER.execute(()->runJob(()->{
                try {
                    var result=GraphExportFiles.write(captured,output,token::get);
                    if(!token.get())server.execute(()->source.sendSuccess(()->Component.literal("CSA graph export: "+result.directory().toAbsolutePath()
                            +" ("+result.images()+" PNG, "+result.dimensions()+" JSON; see SUMMARY.txt for omissions)"),false));
                }catch(Exception ex){
                    if(!token.get())server.execute(()->source.sendFailure(Component.literal("CSA graph export failed: "+ex.getClass().getSimpleName()+": "+ex.getMessage())));
                }
            }));
            source.sendSuccess(()->Component.literal("CSA graph snapshot captured. Writing diagnostic files asynchronously."),false);
            return 1;
        }catch(RuntimeException ex){release();source.sendFailure(Component.literal("CSA graph capture failed: "+ex.getClass().getSimpleName()+": "+ex.getMessage()));return 0;}
    }
}
