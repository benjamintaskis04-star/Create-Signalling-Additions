package com.benjamintaskis.createsignalextensions.diagnostic;

import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.benjamintaskis.createsignalextensions.content.speed.SpeedBoardGraphResolver;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import com.google.gson.JsonParser;
import static com.benjamintaskis.createsignalextensions.diagnostic.GraphExportSnapshot.*;

public final class GraphExporterChecks {
    private static int checks;
    private static Snapshot curvedFixture;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static final class TestGraph extends TrackGraph {
        final Map<TrackNodeLocation,TrackNode> nodes=new LinkedHashMap<>();
        final Map<TrackNode,Map<TrackNode,TrackEdge>> links=new IdentityHashMap<>();
        TestGraph(UUID id){super(id);}
        @Override public Set<TrackNodeLocation> getNodes(){return Collections.unmodifiableSet(nodes.keySet());}
        @Override public TrackNode locateNode(TrackNodeLocation at){return nodes.get(at);}
        @Override public Map<TrackNode,TrackEdge> getConnectionsFrom(TrackNode node){return Collections.unmodifiableMap(links.getOrDefault(node,Map.of()));}
        TrackNode node(double x,double y,double z,net.minecraft.resources.ResourceKey<Level> dimension){
            var at=new TrackNodeLocation(new Vec3(x,y,z)).in(dimension);var n=new TrackNode(at,nodes.size()+1,new Vec3(0,1,0));nodes.put(at,n);return n;
        }
        TrackEdge edge(TrackNode a,TrackNode b,BezierConnection curve){
            var edge=new TrackEdge(a,b,curve,null);links.computeIfAbsent(a,k->new LinkedHashMap<>()).put(b,edge);
            links.computeIfAbsent(b,k->new LinkedHashMap<>()).put(a,new TrackEdge(b,a,curve==null?null:curve.secondary(),null));return edge;
        }
    }
    private static Snapshot snapshot(TestGraph... graphs){
        Map<UUID,TrackGraph> registry=new LinkedHashMap<>();for(var graph:graphs)registry.put(graph.id,graph);
        return GraphExportSnapshot.capture(registry,123,new Budget(()->0L,1,MAX_NODES,MAX_EDGES));
    }
    private static Board straightBoard(String name,Point centre,Point axis,Point face){
        return new Board(name,"minecraft:overworld",centre.plus(new Point(0,2,0)),20,false,true,face,false,
                new Link("STRAIGHT",centre,null,-1,centre,axis,null,null,null,null));
    }
    private static Object runtime(BezierConnection curve)throws Exception {
        var f=BezierConnection.class.getDeclaredField("lazyRuntime");f.setAccessible(true);return ((AtomicReference<?>)f.get(curve)).get();
    }
    private static Map<String,Object> counters(Class<?>... types)throws Exception {
        Map<String,Object> result=new TreeMap<>();for(var type:types)for(var field:type.getDeclaredFields())
            if(Modifier.isStatic(field.getModifiers())&&(field.getType()==int.class||field.getType()==long.class)){
                field.setAccessible(true);result.put(type.getName()+field.getName(),field.get(null));
            }return result;
    }
    private static void captureAndGeometry()throws Exception {
        var first=new TestGraph(new UUID(0,1));var a=first.node(0,64,0,Level.OVERWORLD);var b=first.node(10,64,0,Level.OVERWORLD);var edge=first.edge(a,b,null);
        var second=new TestGraph(new UUID(1,0));var p=second.node(0,70,.5,Level.OVERWORLD);var q=second.node(10,70,.5,Level.OVERWORLD);second.edge(p,q,null);
        var oldCounters=counters(TrackGraph.class,TrackNode.class);var before=new LinkedHashMap<>(first.links.get(a));
        var frozen=snapshot(second,first);
        check(frozen.graphs().size()==2&&frozen.topologyComplete(),"Enumerates disconnected registered graphs");
        check(frozen.graphs().getFirst().edges().size()==1,"Directed adjacency exported once per physical edge");
        check(!frozen.graphs().get(0).colour().equals(frozen.graphs().get(1).colour()),"Distinct colours even for UUID hash collisions");
        check(frozen.equals(snapshot(first,second)),"Deterministic UUID/node/edge ordering independent of registry order below cap");
        check(oldCounters.equals(counters(TrackGraph.class,TrackNode.class))&&before.equals(first.links.get(a)),"Capture preserves graph ID counters and authoritative adjacency");
        first.links.get(a).clear();check(frozen.graphs().getFirst().edges().size()==1,"Immutable DTO survives live graph mutation");first.links.get(a).put(b,edge);
        boolean immutable=false;try{frozen.graphs().clear();}catch(UnsupportedOperationException expected){immutable=true;}
        check(immutable,"Snapshot collections cannot be mutated by worker");
        var n=first.node(10,64,10,Level.NETHER);first.edge(b,n,null);var dimensions=snapshot(first);
        check(dimensions.graphs().getFirst().edges().stream().anyMatch(e->e.geometry().kind().equals("INTER_DIMENSION_LINK")&&!e.geometry().available()&&e.length()==0),"Portal links retain dimensions without inventing physical rail");
        var curveGraph=new TestGraph(new UUID(0,3));var c=curveGraph.node(0,63,0,Level.OVERWORLD);var d=curveGraph.node(10,65,10,Level.OVERWORLD);
        var curve=new BezierConnection(Couple.create(BlockPos.ZERO,new BlockPos(10,2,10)),
                Couple.create(new Vec3(0,63,0),new Vec3(10,65,10)),Couple.create(new Vec3(1,0,0),new Vec3(0,0,-1)),
                Couple.create(new Vec3(0,1,0),new Vec3(0,1,0)),true,false,null);
        var ce=curveGraph.edge(c,d,curve);var cold=snapshot(curveGraph);
        check(runtime(curve)==null&&cold.graphs().getFirst().edges().getFirst().geometry().unavailable().equals("COLD_CURVE_CACHE"),"Cold curve capture does not initialize live Runtime");
        ce.getPosition(curveGraph,.5);var warm=runtime(curve);var curved=snapshot(curveGraph);var geometry=curved.graphs().getFirst().edges().getFirst().geometry();
        check(geometry.available()&&runtime(curve)==warm,"Warm capture reuses controls without replacing live Runtime");
        for(double t:new double[]{.25,.5,.75})check(geometry.at(t).minus(Point.of(ce.getPosition(curveGraph,t))).length()<1e-5,"Captured cubic agrees with authoritative Create geometry at "+t);
        var samples=GraphExportFiles.sample(curved).getFirst().samples();check(samples.size()>2&&samples.get(samples.size()/2).y()>63,"Curved intermediate geometry retains elevation");
        var raw=curved.graphs().getFirst().edges().getFirst().rawCurve();
        var link=new Link("CURVE",raw.blockA(),raw.blockB(),0,null,null,null,null,raw,null);
        var curveBoard=new Board("curve-board","minecraft:overworld",new Point(0,65,0),20,false,true,new Point(-1,0,0),false,link);
        var curveAssociation=GraphExportBoards.resolve(curved,curveBoard);
        var csaCurve=SpeedBoardGraphResolver.curve(List.of(curveGraph),Level.OVERWORLD,BlockPos.ZERO,
                new com.simibubi.create.content.trains.track.BezierTrackPointLocation(new BlockPos(10,2,10),0),curve,new Vec3(-1,0,0));
        check(curveAssociation.status().equals("VERIFIED")&&csaCurve.location()!=null&&curveAssociation.edgePosition()==csaCurve.location().position(),
                "Curve association matches actual CSA saved-segment convention");
        var wrongLink=new Link("CURVE",new Point(1,0,0),raw.blockB(),0,null,null,null,null,raw,null);
        var wrongBoard=new Board("wrong-curve","minecraft:overworld",new Point(0,65,0),20,false,false,new Point(-1,0,0),false,wrongLink);
        check(!GraphExportBoards.resolve(curved,wrongBoard).status().equals("VERIFIED"),"Stale curve anchor cannot verify by geometry alone");
        curvedFixture=curved.withBoards(List.of(curveBoard),List.of());
        check(snapshot(new TestGraph(new UUID(0,4))).graphs().getFirst().nodes().isEmpty(),"Empty registered graph retained");
        Map<UUID,TrackGraph> corrupt=new LinkedHashMap<>();corrupt.put(first.id,first);corrupt.put(new UUID(0,9),null);
        var partial=GraphExportSnapshot.capture(corrupt,0,new Budget(()->0L,1,MAX_NODES,MAX_EDGES));
        check(partial.graphs().size()==1&&!partial.topologyComplete()&&!partial.errors().isEmpty(),"Invalid graph isolated without losing valid graphs");
        var limited=GraphExportSnapshot.capture(Map.of(first.id,first),0,new Budget(()->0L,1,1,1));
        check(!limited.topologyComplete()&&limited.graphs().isEmpty(),"Hard count budget rejects incomplete graph atomically");
        var clock=new java.util.concurrent.atomic.AtomicLong();var budget=new Budget(clock::get,1,10,10);clock.set(2_000_000);
        check(!GraphExportSnapshot.capture(Map.of(first.id,first),0,budget).topologyComplete(),"Soft deadline reports incomplete capture");
        boolean invalid=false;try{new RawCurve(new Point(0,0,0),new Point(1,0,1),new Point(0,0,0),new Point(1,0,1),new Point(Double.NaN,0,0),new Point(0,0,1));}catch(IllegalArgumentException expected){invalid=true;}
        check(invalid,"Invalid raw axes rejected before JSON serialization");
    }
    private static void associationAndProjection() {
        var graph=new TestGraph(new UUID(0,20));var a=graph.node(0,64,0,Level.OVERWORLD);var b=graph.node(10,64,0,Level.OVERWORLD);graph.edge(a,b,null);var captured=snapshot(graph);
        var board=straightBoard("board",new Point(5,64,0),new Point(1,0,0),new Point(-1,0,0));
        var association=GraphExportBoards.resolve(captured,board);check(association.status().equals("VERIFIED")&&association.edgePosition()==5,"Explicit board link resolves to verified edge position");
        var actual=SpeedBoardGraphResolver.straight(List.of(graph),Level.OVERWORLD,new Vec3(5,64,0),new Vec3(1,0,0),new Vec3(-1,0,0));
        check(actual.location()!=null&&actual.location().position()==association.edgePosition(),"Snapshot association agrees with actual CSA resolver");
        check(!GraphExportBoards.resolve(captured,straightBoard("parallel",new Point(5,64,.5),new Point(1,0,0),new Point(-1,0,0))).status().equals("VERIFIED"),"Nearby parallel board cannot attach by radius");
        var tilted=new TestGraph(new UUID(0,21));var c=tilted.node(0,64,-.05,Level.OVERWORLD);var d=tilted.node(10,64,.05,Level.OVERWORLD);tilted.edge(c,d,null);
        // TrackNodeLocation quantizes halves, so supply the precise DTO edge separately for this near-alignment counterexample.
        var wrong=new Edge("1--2","1","2","minecraft:overworld","minecraft:overworld",true,10.0005,null,
                new Geometry("LINE",new Point(0,64,-.05),new Point(10,64,.05),null,null,null));
        var skewed=new Snapshot(1,0,1,true,List.of(new Graph("skew","#aa0000",captured.graphs().getFirst().nodes(),List.of(wrong))),List.of(),List.of());
        check(!GraphExportBoards.resolve(skewed,board).status().equals("VERIFIED"),"CSA quantized line identity rejects nearly parallel midpoint coincidence");
        var diagonal=new TestGraph(new UUID(0,22));var x=diagonal.node(0,64,0,Level.OVERWORLD);var z=diagonal.node(10,64,10,Level.OVERWORLD);diagonal.edge(x,z,null);
        var travel=GraphExportBoards.resolve(snapshot(diagonal),straightBoard("diagonal",new Point(5,64,5),new Point(1,0,1),new Point(-1,0,0)));
        check(travel.status().equals("VERIFIED")&&travel.travelTangent().x()>.6&&travel.travelTangent().z()>.6,"Arrow uses graph tangent rather than cardinal sign facing");
        check(!GraphExportBoards.resolve(new Snapshot(1,0,2,false,captured.graphs(),List.of(),List.of("omitted graph")),board).status().equals("VERIFIED"),"Partial topology cannot establish unique board attachment");
        var unavailable=new Board("unlinked","minecraft:overworld",new Point(5,66,0),20,false,false,new Point(-1,0,0),false,
                new Link("UNLINKED",null,null,-1,null,null,null,null,null,"UNLINKED"));
        check(GraphExportBoards.resolve(captured,unavailable).status().equals("UNLINKED"),"Unresolved board preserves reason and physical position");
        var projection=GraphExportFiles.projection(new GraphExportFiles.Bounds(-10,-20,30,60),4);
        check(projection.x(30)>projection.x(-10)&&projection.z(60)>projection.z(-20),"East right and south down");
        check(Math.abs((projection.x(30)-projection.x(-10))/40-(projection.z(60)-projection.z(-20))/80)<1e-10,"Uniform X/Z spatial scale");
        check(Double.isFinite(GraphExportFiles.projection(new GraphExportFiles.Bounds(1,2,1,2),4).pixelsPerBlock()),"Zero-span bounds remain finite");
        check(GraphExportFiles.projection(new GraphExportFiles.Bounds(-30_000_000,-30_000_000,30_000_000,30_000_000),4).imageWidth()==1024,"Huge world bounds do not enlarge raster");
        List<Edge> manyCurves=new ArrayList<>();
        for(int i=0;i<600;i++)manyCurves.add(new Edge("e"+i,"a","b","minecraft:overworld","minecraft:overworld",true,1000.0,null,
                new Geometry("BEZIER",new Point(0,64,0),new Point(1000,64,1000),new Point(1000,64,0),new Point(0,64,1000),null)));
        var manySamples=GraphExportFiles.sample(new Snapshot(1,0,1,true,List.of(new Graph("large","#aa0000",List.of(),manyCurves)),List.of(),List.of()));
        check(manySamples.stream().mapToInt(e->e.samples().size()).sum()<=GraphExportFiles.MAX_SAMPLES&&manySamples.stream().anyMatch(GraphExportFiles.SampledEdge::samplesLimited),"Global intermediate-point budget is hard bounded with explicit limited flags");
        check(!GraphExportFiles.dimensionFolder("a:b/c").equals(GraphExportFiles.dimensionFolder("a:b_c"))&&!GraphExportFiles.dimensionFolder("../").contains("/"),"Dimension encoding is collision-safe and traversal-free");
    }
    private static void files()throws Exception {
        Path base=Path.of("build","graph-exporter-checks");Files.createDirectories(base);
        var graph=new TestGraph(new UUID(0,30));var a=graph.node(0,63,0,Level.OVERWORLD);var b=graph.node(10,63,10,Level.OVERWORLD);graph.edge(a,b,null);
        var c=graph.node(0,70,10,Level.OVERWORLD);var d=graph.node(10,70,0,Level.OVERWORLD);graph.edge(c,d,null);
        var n=graph.node(0,64,0,Level.NETHER);var m=graph.node(10,64,0,Level.NETHER);graph.edge(n,m,null);graph.edge(a,n,null);
        var captured=snapshot(graph);var board=straightBoard("board",new Point(5,63,5),new Point(1,0,1),new Point(-1,0,0));
        captured=captured.withBoards(List.of(board),List.of());var result=GraphExportFiles.write(captured,base,()->false);
        check(result.dimensions()==2&&result.images()>=2,"Multiple dimensions produce matching JSON and PNG sets");
        var folder=result.directory().resolve(GraphExportFiles.dimensionFolder("minecraft:overworld"));
        var json=JsonParser.parseString(Files.readString(folder.resolve("graph.json"))).getAsJsonObject();
        check(json.get("dimension").getAsString().equals("minecraft:overworld")&&json.getAsJsonArray("speedBoards").get(0).getAsJsonObject().getAsJsonObject("association").get("status").getAsString().equals("VERIFIED"),"JSON records dimension and actual verified board attachment");
        var nodes=json.getAsJsonArray("graphs").get(0).getAsJsonObject().getAsJsonArray("nodes");
        check(nodes.size()==4&&nodes.asList().stream().allMatch(node->node.getAsJsonObject().get("degree").getAsInt()<=2),"Projected elevated crossing adds no false graph node/junction");
        var edges=json.getAsJsonArray("graphs").get(0).getAsJsonObject().getAsJsonArray("edges");
        check(edges.asList().stream().anyMatch(edge->edge.getAsJsonObject().getAsJsonObject("edge").getAsJsonObject("geometry").get("kind").getAsString().equals("INTER_DIMENSION_LINK")),"JSON retains portal endpoint dimensions");
        for(var value:json.getAsJsonArray("images")) {
            var picture=value.getAsJsonObject();var image=ImageIO.read(folder.resolve(picture.get("file").getAsString()).toFile());
            check(image.getWidth()==picture.getAsJsonObject("projection").get("imageWidth").getAsInt()&&image.getHeight()==1024,"PNG dimensions match JSON projection");
            image.flush();
        }
        check(Files.readString(result.directory().resolve("SUMMARY.txt")).contains("crossings are NOT junctions"),"Summary explains connectivity and elevation limitations");
        var second=GraphExportFiles.write(captured,base,()->false);check(!second.directory().equals(result.directory()),"Repeated exports never overwrite earlier output");
        check(Files.readString(folder.resolve("graph.json")).equals(Files.readString(second.directory().resolve(GraphExportFiles.dimensionFolder("minecraft:overworld")).resolve("graph.json"))),
                "Identical snapshots produce byte-identical deterministic JSON");
        var empty=GraphExportFiles.write(snapshot(new TestGraph(new UUID(0,31))),base,()->false);
        check(empty.dimensions()==1&&empty.images()==1,"Empty graphs still produce a diagnostic overview/JSON");
        var curveFiles=GraphExportFiles.write(curvedFixture,base,()->false);
        var curveFolder=curveFiles.directory().resolve(GraphExportFiles.dimensionFolder("minecraft:overworld"));
        var curveJson=JsonParser.parseString(Files.readString(curveFolder.resolve("graph.json"))).getAsJsonObject();
        var transform=curveJson.getAsJsonArray("images").get(0).getAsJsonObject().getAsJsonObject("projection");
        var geometry=curvedFixture.graphs().getFirst().edges().getFirst().geometry();var point=geometry.at(.25);
        double chord=geometry.end().minus(geometry.start()).scale(.25).plus(geometry.start()).minus(point).length();
        int px=(int)Math.round(transform.get("originPixelX").getAsDouble()+(point.x()-transform.get("minX").getAsDouble())*transform.get("pixelsPerBlock").getAsDouble());
        int pz=(int)Math.round(transform.get("originPixelZ").getAsDouble()+(point.z()-transform.get("minZ").getAsDouble())*transform.get("pixelsPerBlock").getAsDouble());
        var curveImage=ImageIO.read(curveFolder.resolve("overview.png").toFile());var colour=java.awt.Color.decode(curvedFixture.graphs().getFirst().colour()).getRGB()&0xffffff;boolean painted=false;
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if((curveImage.getRGB(px+dx,pz+dz)&0xffffff)==colour)painted=true;
        curveImage.flush();check(chord>.1&&painted,"PNG paints authoritative curved path away from endpoint chord");
        var cancel=GraphExportFiles.write(captured,base,()->true);check(cancel.images()==0&&!cancel.errors().isEmpty(),"Cancellation is reported without accessing live graph");
        Path blocker=Files.createTempFile(base,"blocked-",".txt");boolean failed=false;
        try{GraphExportFiles.write(captured,blocker,()->false);}catch(java.io.IOException expected){failed=true;}
        check(failed,"Filesystem failure is surfaced without overwriting existing file");
        // Force each JSON failure after overview creation, exercising the hard output cap on failures.
        List<Node> many=new ArrayList<>();for(int i=0;i<20;i++)many.add(new Node("n"+i,i,"test:d"+i,new Point(0,64,0),0,0));
        var failureSnapshot=new Snapshot(1,0,1,true,List.of(new Graph("many","#aa0000",many,List.of())),List.of(),List.of());
        Path failureBase=Files.createTempDirectory(base,"json-failure-");
        var failures=GraphExportFiles.write(failureSnapshot,failureBase,()->{
            try(var roots=Files.list(failureBase)){for(var export:roots.toList())if(Files.isDirectory(export))try(var dirs=Files.list(export)){
                for(var dir:dirs.toList())if(Files.isDirectory(dir)&&Files.exists(dir.resolve("overview.png"))&&!Files.exists(dir.resolve("graph.json")))Files.createDirectory(dir.resolve("graph.json"));
            }}catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}return false;
        });
        try(var dirs=Files.list(failures.directory())){check(dirs.filter(Files::isDirectory).count()==16&&failures.images()<=32&&failures.dimensions()==0,"JSON failures cannot bypass dimension/image bounds");}
        System.out.println("Graph exporter image fixture: "+folder.resolve("overview.png").toAbsolutePath());
    }
    private static void command()throws Exception {
        var root=GraphExportCommand.command().build();
        var nonop=new net.minecraft.commands.CommandSourceStack(net.minecraft.commands.CommandSource.NULL,Vec3.ZERO,net.minecraft.world.phys.Vec2.ZERO,null,0,"test",net.minecraft.network.chat.Component.literal("test"),null,null);
        var op=nonop.withPermission(2);
        check(!root.getRequirement().test(nonop)&&root.getRequirement().test(op),"Operator permission level2 required");
        var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
        dispatcher.register(net.minecraft.commands.Commands.literal("csa"));dispatcher.register(GraphExportCommand.command());
        check(!dispatcher.getRoot().getChild("csa").getChild("graph").getChild("export").getRequirement().test(nonop),"Leaf remains restricted when root merges with public command");
        var handler=GraphExportCommand.class.getDeclaredMethod("export",net.minecraft.commands.CommandSourceStack.class);handler.setAccessible(true);
        check(handler.invoke(null,nonop).equals(0),"Handler independently rejects non-operators before any server/graph access");
        check(GraphExportCommand.acquire()&&!GraphExportCommand.acquire(),"Only one export job can be active");
        try{GraphExportCommand.runJob(()->{throw new IllegalStateException("simulated file failure");});}catch(IllegalStateException expected){}
        check(GraphExportCommand.acquire(),"Worker failure releases busy gate");GraphExportCommand.release();
    }
    public static void main(String[] args)throws Exception {
        captureAndGeometry();associationAndProjection();files();command();
        System.out.println("Graph exporter read-only/geometry/file checks passed: "+checks);
    }
}
