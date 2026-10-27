package com.benjamintaskis.createsignalextensions.content.speed;

import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.benjamintaskis.createsignalextensions.config.CommonConfig;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.*;
import java.util.*;

/** Real manager begin/end, occupied-section state and SavedData, not an independent rules simulator. */
public final class BoundaryLifecycleChecks {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static final class Graph extends TrackGraph {
        final Map<TrackNodeLocation, TrackNode> nodes = new LinkedHashMap<>();
        final Map<TrackNode, Map<TrackNode, TrackEdge>> links = new IdentityHashMap<>();
        @Override public Set<TrackNodeLocation> getNodes() { return nodes.keySet(); }
        @Override public TrackNode locateNode(TrackNodeLocation at) { return nodes.get(at); }
        @Override public Map<TrackNode, TrackEdge> getConnectionsFrom(TrackNode n) { return links.getOrDefault(n, Map.of()); }
        TrackNode node(double x, double z) {
            var at = new TrackNodeLocation(new Vec3(x,0,z)).in(Level.OVERWORLD);
            var n = new TrackNode(at,nodes.size()+1,new Vec3(0,1,0)); nodes.put(at,n); return n;
        }
        TrackEdge edge(TrackNode a, TrackNode b) {
            var edge = new TrackEdge(a,b,null,null);
            links.computeIfAbsent(a,k->new LinkedHashMap<>()).put(b,edge);
            links.computeIfAbsent(b,k->new LinkedHashMap<>()).put(a,new TrackEdge(b,a,null,null)); return edge;
        }
    }
    private static final class World {
        Graph graph = new Graph(); TrackNode a = graph.node(0,0), b = graph.node(200,0);
        TrackEdge edge = graph.edge(a,b);
        SpeedRestrictionManager.TrainSpeedState state;
        double front, rear;
        List<SpeedRestrictionManager.BoundaryBoard> boards = List.of();
        World(Integer limit,double front,double rear) { state=SpeedRestrictionManager.restoreState(
                new SpeedRestrictionSavedData.Entry(limit,null,false,0,List.of())); this.front=front; this.rear=rear; }
        TravellingPoint point(double p) { return new TravellingPoint(a,b,edge,p,false); }
        SpeedRestrictionManager.BoundaryBoard board(int at,Integer limit,boolean reverse,boolean available) {
            var location = available ? reverse
                    ? new SpeedBoardGraphLocation(graph,b,a,graph.links.get(b).get(a),200-at)
                    : new SpeedBoardGraphLocation(graph,a,b,edge,at) : null;
            return new SpeedRestrictionManager.BoundaryBoard("board:"+at+":"+reverse,limit,new BlockPos(at,2,0),Level.OVERWORLD,
                    location,new Vec3(at,0,0),at+":"+reverse+":"+limit);
        }
        void move(double nextFront,double nextRear,boolean reverse) {
            SpeedRestrictionManager.beginBoundaryMovement(state,graph,point(front),point(rear),boards);
            SpeedRestrictionManager.finishBoundaryMovement(state,graph,point(nextFront),point(nextRear),reverse,
                    Math.max(Math.abs(front-nextFront),Math.abs(rear-nextRear))*2+1,boards);
            front=nextFront; rear=nextRear;
        }
        void cap(Integer expected,String reason) { check(Objects.equals(state.effectiveLimitKmh(),expected),reason+" got "+state.effectiveLimitKmh()); }
        void reload() throws Exception {
            var data=new SpeedRestrictionSavedData(); var id=UUID.randomUUID();
            SpeedRestrictionManager.saveState(data,id,state);
            state=SpeedRestrictionManager.restoreState(load(data.save(new CompoundTag(),RegistryAccess.EMPTY)).get(id));
        }
        void rebuild() {
            graph.links.get(a).clear(); graph.links.get(b).clear();
            graph=new Graph(); a=graph.node(0,0); b=graph.node(200,0); edge=graph.edge(a,b);
        }
    }
    private static SpeedRestrictionSavedData load(CompoundTag nbt) throws Exception {
        var method=SpeedRestrictionSavedData.class.getDeclaredMethod("load",CompoundTag.class,HolderLookup.Provider.class);
        method.setAccessible(true); return (SpeedRestrictionSavedData)method.invoke(null,nbt,RegistryAccess.EMPTY);
    }
    private static void f1() throws Exception {
        var w=new World(60,9.9,0); w.boards=List.of(w.board(10,20,false,true)); w.reload(); w.move(10.1,.2,false);
        w.cap(20,"Moving reload front applies lower crossing");
        w=new World(20,9,0); w.boards=List.of(w.board(10,60,false,true)); w.move(11,9.9,false); w.reload(); w.move(11.2,10.1,false);
        w.cap(60,"Moving reload rear releases pending"); check(w.state.pendingHigherBoundaries.isEmpty(),"Restored pending consumed");
        w=new World(20,8,0); w.boards=List.of(w.board(10,60,false,true)); w.move(9,1,false);
        check(w.state.lastRearGraph!=null,"Rear observation maintained with empty queue"); w.move(11,3,false); w.cap(20,"First increase waits");
        w.move(19,11,false); w.cap(60,"First increase releases after actual clearance");
        w=new World(60,9.9,0); w.boards=List.of(w.board(10,20,false,true)); w.move(9.9,0,false); w.reload();
        w.move(9.9,0,false); w.cap(60,"Stopped reload unchanged"); w.move(10.1,.2,false); w.cap(20,"Stopped reload resumes through lower");
        w=new World(20,9,0); w.boards=List.of(w.board(10,60,false,true)); w.move(11,2,false);
        w.move(9,0,true); w.cap(20,"Reversal cannot release forward boundary");
        w.move(12,3,false); w.move(20,11,false); w.cap(60,"Original direction resumes and clears");
        w=new World(60,9,0); w.boards=List.of(w.board(10,20,false,false)); w.move(11,2,false); w.cap(60,"Unknown association never invents crossing");
        w.boards=List.of(w.board(10,20,false,true)); w.move(12,3,false); w.cap(20,"Returning association verifies recorded front passage");
        w=new World(20,9,0); w.boards=List.of(w.board(10,60,false,true)); w.move(11,9,false);
        w.boards=List.of(); w.move(13,11,false); w.cap(60,"Frozen anchor clears while board unavailable");
        w=new World(20,9,0); w.boards=List.of(w.board(10,60,false,true)); w.move(11,9,false); w.reload();
        w.rebuild(); w.boards=List.of(); w.move(13,11,false); w.cap(60,"Saved exact anchor rebinds rebuilt graph");
        w=new World(20,9,0); w.boards=List.of(w.board(10,60,false,true)); w.move(11,9,false);
        w.graph.links.get(w.a).clear(); w.graph.links.get(w.b).clear(); w.boards=List.of(); w.move(13,11,false);
        w.cap(20,"Removed edge cannot authorize release");
        w.rebuild(); w.move(14,12,false); w.cap(60,"Equivalent reconstruction recovers recorded rear crossing");
        w=new World(60,9,0); var p=w.graph.node(0,.5); var q=w.graph.node(200,.5); var parallel=w.graph.edge(p,q);
        w.boards=List.of(new SpeedRestrictionManager.BoundaryBoard("parallel",20,new BlockPos(10,2,1),Level.OVERWORLD,
                new SpeedBoardGraphLocation(w.graph,p,q,parallel,10),new Vec3(10,0,.5),"parallel"));
        w.move(11,2,false); w.cap(60,"Parallel route never creates crossing");
        w=new World(60,9,0); w.boards=List.of(w.board(10,20,true,true)); w.move(11,2,false); w.cap(60,"Opposite-direction board ignored");
        w=new World(60,9,0); w.boards=List.of(w.board(10,20,false,false)); w.move(11,2,false);
        for(int i=0;i<140;i++)w.move(11,2,false);
        w.boards=List.of(w.board(10,20,false,true)); w.move(12,3,false); w.cap(20,"Stops do not evict movement evidence");
        w=new World(60,0,0); for(int i=1;i<150;i++)w.move(i,i,false);
        check(w.state.movements.size()==SpeedRestrictionManager.BOUNDARY_HISTORY,"Movement journal hard bound");
        w=new World(60,11,2); w.boards=List.of(w.board(10,20,false,true));
        SpeedRestrictionManager.finishBoundaryMovement(w.state,w.graph,w.point(11),w.point(2),false,2,w.boards);
        w.cap(60,"Post-only initial point cannot fabricate prior route");
    }
    private static void f2() throws Exception {
        var w=new World(20,9,0); w.boards=List.of(w.board(10,100,false,true),w.board(20,60,false,true));
        w.move(11,2,false); w.move(21,9,false); w.cap(20,"20 retained across three sections");
        w.reload(); w.move(23,11,false); w.cap(60,"Rear clearing 100 cannot override occupied 60");
        w.move(33,21,false); w.cap(60,"60 remains after final clearance"); check(w.state.pendingHigherBoundaries.isEmpty(),"All transitions cleared");
        w=new World(20,9,0); w.boards=List.of(w.board(10,40,false,true),w.board(20,80,false,true));
        w.move(21,9,false); w.cap(20,"Consecutive increases retain base"); w.move(23,11,false); w.cap(40,"First increase applies");
        w.move(33,21,false); w.cap(80,"Final increase applies");
        w=new World(20,9,0); w.boards=List.of(w.board(10,100,false,true),w.board(20,10,false,true),w.board(30,60,false,true));
        w.move(21,9,false); w.cap(10,"Lower applies without dropping required transitions");
        check(w.state.pendingHigherBoundaries.size()==2,"Reduction keeps earlier boundary represented");
        w.move(31,11,false); w.cap(10,"Earlier rear clearance retains lower section");
        w.move(41,21,false); w.cap(10,"Intermediate lower not released early"); w.move(51,31,false); w.cap(60,"Lower eventually releases");
        w=new World(20,9,0); w.boards=List.of(w.board(10,null,false,true)); w.move(11,9,false);
        w.cap(20,"Unrestricted waits for whole train"); w.reload(); w.move(13,11,false); w.cap(null,"Unrestricted releases");
        w=new World(20,40,31); w.boards=List.of(w.board(30,100,true,true),w.board(20,60,true,true));
        w.move(39,29,true); w.move(31,19,true); w.cap(20,"Reverse travel spans multiple increases");
        w.reload(); w.move(29,17,true); w.cap(60,"Reverse rear release respects occupied 60"); w.move(19,7,true); w.cap(60,"Reverse final clearance");
        w=new World(20,9,0); w.boards=List.of(w.board(10,100,false,true),w.board(20,60,false,true));
        w.move(21,9,false); w.move(21,9,false); w.reload(); w.rebuild();
        w.boards=List.of(w.board(10,100,false,true),w.board(20,60,false,true)); w.move(23,11,false); w.cap(60,"Stop/reload/rebuild preserves minimum");
        w=new World(20,9,0); w.boards=List.of(w.board(10,100,false,true),w.board(20,60,false,true),w.board(5,80,true,true));
        w.move(21,9,false); w.move(19,7,true); w.cap(20,"Reversal retains section constraints");
        w.move(15,3,true); w.cap(20,"Reverse increase waits for rear"); w.move(3,0,true); w.cap(80,"Reverse rear passage supersedes older direction");
        w=new World(20,9,0); var coincident=w.board(10,100,false,true);
        w.boards=List.of(coincident,new SpeedRestrictionManager.BoundaryBoard("other-sign",60,new BlockPos(11,2,0),Level.OVERWORLD,
                coincident.location(),coincident.legacyTarget(),"other-sign"));
        w.move(11,9,false); w.reload(); w.move(13,11,false);
        w.cap(60,"Coincident boards use restrictive tie semantics across reload");
        check(w.state.pendingHigherBoundaries.isEmpty(),"Coincident instructions clear as one physical boundary");
        for (int lateLimit : new int[]{60,100}) {
            w=new World(20,9,0); var early=w.board(10,lateLimit==60?100:60,false,true);
            var missing=new SpeedRestrictionManager.BoundaryBoard("late-sign",lateLimit,new BlockPos(11,2,0),Level.OVERWORLD,null,null,"late");
            w.boards=List.of(early,missing); w.move(11,9,false); w.move(13,11,false);
            w.boards=List.of(early,new SpeedRestrictionManager.BoundaryBoard("late-sign",lateLimit,new BlockPos(11,2,0),Level.OVERWORLD,
                    early.location(),early.legacyTarget(),"late"));
            w.move(14,12,false); w.cap(60,"Late verified coincident instruction preserves restrictive tie for "+lateLimit);
        }
    }
    private static void persistence() throws Exception {
        var w=new World(20,9,0); w.boards=List.of(w.board(10,100,false,true),w.board(20,60,false,true)); w.move(21,9,false);
        var data=new SpeedRestrictionSavedData(); var id=UUID.randomUUID(); SpeedRestrictionManager.saveState(data,id,w.state);
        var entry=data.get(id); check(data.isDirty(),"SavedData dirtied on change"); data.setDirty(false);
        SpeedRestrictionManager.saveState(data,id,w.state); check(!data.isDirty(),"Unchanged actual SavedData remains clean");
        var encoded=data.save(new CompoundTag(),RegistryAccess.EMPTY); var decoded=load(encoded);
        check(entry.equals(decoded.get(id)),"Actual SavedData round-trip includes ordered sections and anchors");
        check(decoded.get(id).boundaries().getFirst().anchor()!=null,"Anchor survives NBT");
        var legacy=encoded.copy(); var pending=legacy.getList("Trains",Tag.TAG_COMPOUND).getCompound(0).getList("PendingBoundaries",Tag.TAG_COMPOUND);
        for(int i=0;i<pending.size();i++)pending.getCompound(i).remove("GraphAnchor");
        var old=load(legacy); check(old.get(id).boundaries().size()==2 && old.get(id).boundaries().getFirst().anchor()==null,"Old format remains readable");
        w.state=SpeedRestrictionManager.restoreState(old.get(id)); w.move(23,11,false); w.cap(60,"Legacy links upgrade and release");
        var corrupt=encoded.copy(); var train=corrupt.getList("Trains",Tag.TAG_COMPOUND).getCompound(0);
        train.putInt("Posted",-1); var list=train.getList("PendingBoundaries",Tag.TAG_COMPOUND);
        list.getCompound(0).putString("Dimension","invalid dimension"); list.getCompound(1).putInt("Limit",0);
        var validated=load(corrupt).get(id); check(validated.postedLimit()==null && validated.boundaries().isEmpty(),"Actual load validates limits and dimensions");
        corrupt=encoded.copy(); var anchor=corrupt.getList("Trains",Tag.TAG_COMPOUND).getCompound(0)
                .getList("PendingBoundaries",Tag.TAG_COMPOUND).getCompound(0).getCompound("GraphAnchor"); anchor.putDouble("Position",Double.NaN);
        check(load(corrupt).get(id).boundaries().getFirst().anchor()==null,"Malformed anchor does not drop valid boundary");
        var kept=UUID.randomUUID(); data.update(kept,40,null,false,0,List.of()); data.setDirty(false);
        data.retainTrains(Set.of(kept)); check(data.get(id)==null && data.get(kept)!=null && data.isDirty(),"Deleted cleanup retains registered virtual/graphless UUID");
        data.setDirty(false); data.retainTrains(Set.of(kept)); check(!data.isDirty(),"Unchanged cleanup is clean");
    }
    private static void integration() throws Exception {
        var w=new World(60,16,11); w.boards=List.of(w.board(10,20,true,true));
        SpeedRestrictionManager.beginBoundaryMovement(w.state,w.graph,w.point(16),w.point(11),w.boards);
        SpeedRestrictionManager.captureMovementCommand(w.state,-3);
        SpeedRestrictionManager.captureMovementCommand(w.state,0); // second carriage / stopped train cannot overwrite it
        check(SpeedRestrictionManager.movementReversed(w.state,false),"Captured movement direction survives stop at RETURN");
        check(SpeedRestrictionManager.movementBudget(w.state,0)==7,"Captured speed preserves crossing budget after stop");
        SpeedRestrictionManager.finishBoundaryMovement(w.state,w.graph,w.point(13),w.point(8),
                SpeedRestrictionManager.movementReversed(w.state,false),SpeedRestrictionManager.movementBudget(w.state,0),w.boards);
        w.cap(20,"Reverse crossing remains effective when station zeros speed");
        var n=w.graph.node(0,1); var m=w.graph.node(200,1);
        // Re-key graph nodes after setting their real dimension, as Create does on loading.
        w.graph.nodes.remove(n.getLocation()); w.graph.nodes.remove(m.getLocation());
        n.getLocation().dimension=Level.NETHER; m.getLocation().dimension=Level.NETHER;
        w.graph.nodes.put(n.getLocation(),n); w.graph.nodes.put(m.getLocation(),m);
        var e=w.graph.edge(n,m); var netherFront=new TravellingPoint(n,m,e,9,false);
        check(SpeedRestrictionManager.boundaryDimensions(netherFront,netherFront).equals(Set.of(Level.NETHER)),
                "Dispatch uses actual Nether track dimension, not Train.tick overworld argument");
        check(SpeedRestrictionManager.boundaryDimensions(netherFront,w.point(0)).equals(Set.of(Level.NETHER,Level.OVERWORLD)),
                "Portal-spanning ends dispatch both actual dimensions");

        var graph=new Graph(); var a=graph.node(0,0); var b=graph.node(10,0); var c=graph.node(20,0); var d=graph.node(30,0);
        var first=graph.edge(a,b); graph.edge(b,c); var last=graph.edge(c,d);
        var state=SpeedRestrictionManager.restoreState(new SpeedRestrictionSavedData.Entry(60,null,false,0,List.of()));
        var unavailable=new SpeedRestrictionManager.BoundaryBoard("changed-route",20,BlockPos.ZERO,Level.OVERWORLD,null,null,"link");
        SpeedRestrictionManager.beginBoundaryMovement(state,graph,new TravellingPoint(a,b,first,9,false),new TravellingPoint(a,b,first,8,false),List.of(unavailable));
        SpeedRestrictionManager.finishBoundaryMovement(state,graph,new TravellingPoint(c,d,last,1,false),new TravellingPoint(c,d,last,0,false),false,30,List.of(unavailable));
        graph.links.get(b).remove(c); graph.links.get(c).remove(b);
        var detour=graph.node(15,1); var newEdge=graph.edge(b,detour); graph.edge(detour,c);
        var newBoard=new SpeedRestrictionManager.BoundaryBoard("changed-route",20,BlockPos.ZERO,Level.OVERWORLD,
                new SpeedBoardGraphLocation(graph,b,detour,newEdge,2),null,"link");
        SpeedRestrictionManager.beginBoundaryMovement(state,graph,new TravellingPoint(c,d,last,1,false),new TravellingPoint(c,d,last,0,false),List.of(newBoard));
        SpeedRestrictionManager.finishBoundaryMovement(state,graph,new TravellingPoint(c,d,last,2,false),new TravellingPoint(c,d,last,1,false),false,3,List.of(newBoard));
        check(state.effectiveLimitKmh()==60,"Historical endpoints cannot invent a crossing on a rebuilt intermediate route");

        var curved=new World(20,0,0); curved.graph=new Graph(); curved.a=curved.graph.node(0,0); curved.b=curved.graph.node(10,10);
        var curve=new com.simibubi.create.content.trains.track.BezierConnection(
                net.createmod.catnip.data.Couple.create(BlockPos.ZERO,new BlockPos(10,0,10)),
                net.createmod.catnip.data.Couple.create(Vec3.ZERO,new Vec3(10,0,10)),
                net.createmod.catnip.data.Couple.create(new Vec3(1,0,0),new Vec3(0,0,-1)),
                net.createmod.catnip.data.Couple.create(new Vec3(0,1,0),new Vec3(0,1,0)),true,false,null);
        curved.edge=new TrackEdge(curved.a,curved.b,curve,null);
        curved.graph.links.put(curved.a,new LinkedHashMap<>(Map.of(curved.b,curved.edge)));
        curved.graph.links.put(curved.b,new LinkedHashMap<>(Map.of(curved.a,new TrackEdge(curved.b,curved.a,curve.secondary(),null))));
        double position=curved.edge.getLength()*.75;
        var binding=new SpeedBoardGraphLocation(curved.graph,curved.a,curved.b,curved.edge,position);
        var target=curved.edge.getPosition(curved.graph,.75); var sign=new BlockPos(5,2,5);
        var data=new SpeedRestrictionSavedData(); var id=UUID.randomUUID();
        data.update(id,20,null,false,0,List.of(new SpeedRestrictionSavedData.Boundary("curve",60,sign,
                Level.OVERWORLD.location().toString(),true,0,target,new Vec3(0,0,1))));
        curved.state=SpeedRestrictionManager.restoreState(load(data.save(new CompoundTag(),RegistryAccess.EMPTY)).get(id));
        curved.front=position+1; curved.rear=position-.1;
        curved.boards=List.of(new SpeedRestrictionManager.BoundaryBoard("curve",60,sign,Level.OVERWORLD,binding,target,"curve"));
        curved.move(position+1.2,position+.1,false); curved.cap(60,"Curved legacy SavedData migrates using board-local tangent");
        check(SpeedBoundaryAnchor.read(SpeedBoundaryAnchor.capture(binding).write()).resolve(curved.graph)!=null,
                "Curved exact geometry anchor round-trips without material/world access");

        // Inspect actual compiled Create method and mixin targets without launching Minecraft.
        var train=new org.objectweb.asm.tree.ClassNode();
        try(var in=com.simibubi.create.content.trains.entity.Train.class.getResourceAsStream("Train.class")) {
            new org.objectweb.asm.ClassReader(in).accept(train,0);
        }
        var mixin=new org.objectweb.asm.tree.ClassNode();
        try(var in=BoundaryLifecycleChecks.class.getResourceAsStream("/com/benjamintaskis/createsignalextensions/mixin/TrainBoundaryMixin.class")) {
            new org.objectweb.asm.ClassReader(in).accept(mixin,0);
        }
        var tick=train.methods.stream().filter(method->method.name.equals("tick") && method.desc.equals("(Lnet/minecraft/world/level/Level;)V")).findFirst().orElseThrow();
        String travel=null;
        for(var instruction:tick.instructions) if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                && call.owner.endsWith("/Carriage") && call.name.equals("travel")) travel="L"+call.owner+";"+call.name+call.desc;
        check(travel!=null,"Pinned Create Train.tick actually invokes Carriage.travel");
        int hooked=0; boolean movementTarget=false;
        for(var method:mixin.methods) if(method.visibleAnnotations!=null) for(var annotation:method.visibleAnnotations)
            if(annotation.desc.endsWith("/Inject;")) {
                hooked++;
                @SuppressWarnings("unchecked") var at=(List<org.objectweb.asm.tree.AnnotationNode>)annotation.values.get(annotation.values.indexOf("at")+1);
                for(var point:at) if(point.values.contains(travel))movementTarget=true;
            }
        check(hooked==3 && movementTarget,"Compiled observation hooks include exact installed Create movement descriptor");
        try(var in=BoundaryLifecycleChecks.class.getResourceAsStream("/create_signal_extensions.mixins.json")) {
            check(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).contains("TrainBoundaryMixin"),
                    "Actual bundled resource enables the observation mixin");
        }
    }
    private static void configure() throws Exception {
        check(!CommonConfig.SPEED_DIAGNOSTICS.getDefault(),"Final Alpha 1 speed diagnostics default off");
        check(!CommonConfig.SPEED_GRAPH_DIAGNOSTICS.getDefault(),"Graph diagnostics remain opt-in");
        // Headless fixtures do not start NeoForge's server/config lifecycle. Suppress only logging.
        var cached=CommonConfig.SPEED_DIAGNOSTICS.getClass().getSuperclass().getDeclaredField("cachedValue");
        cached.setAccessible(true); cached.set(CommonConfig.SPEED_DIAGNOSTICS,false);
    }
    public static void main(String[] args) throws Exception {
        configure(); f1(); f2(); persistence(); integration();
        System.out.println("Boundary lifecycle manager/SavedData checks passed: "+checks);
    }
}
