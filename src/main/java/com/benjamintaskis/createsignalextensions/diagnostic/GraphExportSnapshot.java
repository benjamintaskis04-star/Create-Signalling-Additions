package com.benjamintaskis.createsignalextensions.diagnostic;

import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.track.BezierConnection;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/** Server-thread capture. These DTOs contain no Create, world, NBT or entity references. */
public final class GraphExportSnapshot {
    private GraphExportSnapshot() {}
    public static final int MAX_GRAPHS=64, MAX_NODES=8192, MAX_EDGES=16384, MAX_BOARDS=512, MAX_ERRORS=128;
    public record Point(double x,double y,double z) {
        public static Point of(Vec3 p) { return new Point(p.x,p.y,p.z); }
        public boolean finite() { return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Math.abs(x)<=30_000_001&&Math.abs(z)<=30_000_001&&Math.abs(y)<=1_000_000; }
        public Point plus(Point p) { return new Point(x+p.x,y+p.y,z+p.z); }
        public Point minus(Point p) { return new Point(x-p.x,y-p.y,z-p.z); }
        public Point scale(double n) { return new Point(x*n,y*n,z*n); }
        public double dot(Point p) { return x*p.x+y*p.y+z*p.z; }
        public double length() { return Math.sqrt(dot(this)); }
        public Point unit() { return length()<1e-12?new Point(0,0,0):scale(1/length()); }
    }
    public record RawCurve(Point blockA,Point blockB,Point startA,Point startB,Point axisA,Point axisB) {
        public RawCurve {
            for(var p:List.of(blockA,blockB,startA,startB,axisA,axisB))if(!p.finite())throw new IllegalArgumentException("invalid raw curve coordinates");
        }
        public RawCurve reverse() { return new RawCurve(blockB,blockA,startB,startA,axisB,axisA); }
        public static RawCurve of(BezierConnection c) {
            return new RawCurve(new Point(c.bePositions.getFirst().getX(),c.bePositions.getFirst().getY(),c.bePositions.getFirst().getZ()),
                    new Point(c.bePositions.getSecond().getX(),c.bePositions.getSecond().getY(),c.bePositions.getSecond().getZ()),
                    Point.of(c.starts.getFirst()),Point.of(c.starts.getSecond()),Point.of(c.axes.getFirst()),Point.of(c.axes.getSecond()));
        }
    }
    public record Geometry(String kind,Point start,Point end,Point controlA,Point controlB,String unavailable) {
        public boolean available() { return unavailable==null; }
        public Point at(double t) {
            if(!available()) throw new IllegalStateException(unavailable);
            t=Math.clamp(t,0,1); double u=1-t;
            return controlA==null?start.scale(u).plus(end.scale(t)):
                    start.scale(u*u*u).plus(controlA.scale(3*u*u*t)).plus(controlB.scale(3*u*t*t)).plus(end.scale(t*t*t));
        }
    }
    public record Node(String id,int createNetId,String dimension,Point position,int yOffsetPixels,int degree) {}
    public record Edge(String id,String from,String to,String fromDimension,String toDimension,boolean reversePresent,
                       Double length,RawCurve rawCurve,Geometry geometry) {}
    public record Graph(String id,String colour,List<Node> nodes,List<Edge> edges) {
        public Graph { nodes=List.copyOf(nodes); edges=List.copyOf(edges); }
    }
    public record Link(String kind,Point block,Point curveEnd,int segment,Point centre,Point axis,Point tiltedA,Point tiltedB,RawCurve worldCurve,String reason) {
        public Link {for(var p:new Point[]{block,curveEnd,centre,axis,tiltedA,tiltedB})if(p!=null&&!p.finite())throw new IllegalArgumentException("invalid link coordinates");}
    }
    public record Board(String id,String dimension,Point position,int speedKmh,boolean unrestricted,boolean cosmeticX,
                        Point facing,boolean savedTargetDirection,Link link) {}
    public record Snapshot(int schemaVersion,long capturedTick,int registeredGraphs,boolean topologyComplete,
                           List<Graph> graphs,List<Board> boards,List<String> errors) {
        public Snapshot { graphs=List.copyOf(graphs); boards=List.copyOf(boards); errors=List.copyOf(errors); }
        public Snapshot withBoards(List<Board> b,List<String> additionalErrors) {
            var messages=new ArrayList<>(errors); for(var e:additionalErrors)if(messages.size()<MAX_ERRORS)messages.add(e);
            return new Snapshot(schemaVersion,capturedTick,registeredGraphs,topologyComplete,graphs,b,messages);
        }
    }
    public static final class Budget {
        private final LongSupplier clock; private final long deadline;
        private final int nodeLimit,edgeLimit; int nodes,edges;
        public Budget(long millis) { this(System::nanoTime,millis,MAX_NODES,MAX_EDGES); }
        public Budget(LongSupplier clock,long millis,int nodes,int edges) { this.clock=clock; deadline=clock.getAsLong()+millis*1_000_000;nodeLimit=nodes;edgeLimit=edges; }
        public void check() { if(clock.getAsLong()>deadline)throw new Limit("capture deadline reached"); }
        void node() { check();if(++nodes>nodeLimit)throw new Limit("node limit reached"); }
        void edge() { check();if(++edges>edgeLimit)throw new Limit("edge limit reached"); }
    }
    public static final class Limit extends RuntimeException { public Limit(String reason){super(reason);} }

    public static Snapshot capture(Map<UUID,TrackGraph> registry,long tick,Budget budget) {
        List<Graph> graphs=new ArrayList<>(); List<String> errors=new ArrayList<>();
        List<Map.Entry<UUID,TrackGraph>> selected=new ArrayList<>();
        boolean complete=registry.size()<=MAX_GRAPHS;
        for(var e:registry.entrySet()) {
            if(selected.size()==MAX_GRAPHS)break;
            if(e.getKey()==null){complete=false;errors.add("Null registered graph ID");continue;}
            selected.add(new AbstractMap.SimpleImmutableEntry<>(e.getKey(),e.getValue()));
        }
        selected.sort(Map.Entry.comparingByKey());
        if(registry.size()>MAX_GRAPHS)errors.add("Graph count exceeds "+MAX_GRAPHS+"; selected bounded registry prefix, sorted by UUID. Associations are unresolved.");
        for(var entry:selected) {
            try { budget.check();graphs.add(graph(entry.getKey(),entry.getValue(),budget)); }
            catch(RuntimeException ex) { complete=false;if(errors.size()<MAX_ERRORS)errors.add("Graph "+entry.getKey()+": "+ex.getClass().getSimpleName()+": "+ex.getMessage()); }
        }
        for(int i=0;i<graphs.size();i++) {
            var graph=graphs.get(i);var colour=java.awt.Color.getHSBColor(i/(float)Math.max(1,graphs.size()),.72f,.65f);
            graphs.set(i,new Graph(graph.id(),String.format(Locale.ROOT,"#%06x",colour.getRGB()&0xffffff),graph.nodes(),graph.edges()));
        }
        return new Snapshot(1,tick,registry.size(),complete,graphs,List.of(),errors);
    }
    private static Graph graph(UUID registeredId,TrackGraph graph,Budget budget) {
        if(graph==null||!registeredId.equals(graph.id))throw new IllegalArgumentException("registry/graph ID mismatch");
        if(graph.getNodes().size()>MAX_NODES)throw new Limit("single graph node limit reached");
        List<TrackNode> nodes=new ArrayList<>();
        for(var at:graph.getNodes()) { budget.node();var n=graph.locateNode(at);if(n==null)throw new IllegalStateException("missing registered node");nodes.add(n); }
        nodes.sort(Comparator.comparingInt(TrackNode::getNetId));budget.check();
        Set<Integer> ids=new HashSet<>(); List<Node> copied=new ArrayList<>();
        for(var n:nodes) {
            if(!ids.add(n.getNetId()))throw new IllegalStateException("duplicate node net ID");
            var p=Point.of(n.getLocation().getLocation());if(!p.finite())throw new IllegalArgumentException("invalid node coordinates");
            var links=graph.getConnectionsFrom(n); if(links==null)throw new IllegalStateException("missing adjacency map");
            copied.add(new Node(Integer.toString(n.getNetId()),n.getNetId(),n.getLocation().dimension.location().toString(),p,n.getLocation().yOffsetPixels,links.size()));
        }
        List<Edge> edges=new ArrayList<>(); Set<String> visited=new HashSet<>();
        for(var n:nodes) {
            budget.check();var links=graph.getConnectionsFrom(n);
            if(links.size()>MAX_EDGES)throw new Limit("single adjacency limit reached");
            List<Map.Entry<TrackNode,TrackEdge>> ordered=new ArrayList<>(links.entrySet());
            ordered.sort(Comparator.comparingInt(e->e.getKey().getNetId()));
            for(var entry:ordered) {
                budget.check();var to=entry.getKey();var edge=entry.getValue();
                if(!ids.contains(to.getNetId())||graph.locateNode(to.getLocation())!=to)throw new IllegalStateException("edge endpoint not registered");
                String id=Math.min(n.getNetId(),to.getNetId())+"--"+Math.max(n.getNetId(),to.getNetId());
                if(!visited.add(id))continue;budget.edge();
                var geometry=geometry(graph,n,to,edge,budget);Double length;
                if(!n.getLocation().dimension.equals(to.getLocation().dimension))length=0.0;
                else if(edge.isTurn())length=warmCurve(edge.getTurn()).length;
                else length=Point.of(n.getLocation().getLocation()).minus(Point.of(to.getLocation().getLocation())).length();
                if(length!=null&&(!Double.isFinite(length)||length<0))length=null;
                edges.add(new Edge(id,Integer.toString(n.getNetId()),Integer.toString(to.getNetId()),
                        n.getLocation().dimension.location().toString(),to.getLocation().dimension.location().toString(),
                        graph.getConnectionsFrom(to).containsKey(n),length,edge.isTurn()?RawCurve.of(edge.getTurn()):null,geometry));
            }
        }
        edges.sort(Comparator.comparing(Edge::id));
        return new Graph(registeredId.toString(),"#000000",copied,edges);
    }
    private record Warm(Point controlA,Point controlB,Double length,String failure) {}
    /** Read the already-published runtime. Never call resolve/getLength on a cold live curve. */
    private static Warm warmCurve(BezierConnection curve) {
        try {
            var cache=BezierConnection.class.getDeclaredField("lazyRuntime");cache.setAccessible(true);
            var runtime=((AtomicReference<?>)cache.get(curve)).get();
            if(runtime==null)return new Warm(null,null,null,"COLD_CURVE_CACHE");
            var a=field(runtime,"finish1");var b=field(runtime,"finish2");var length=(Double)field(runtime,"length");
            var pa=Point.of((Vec3)a);var pb=Point.of((Vec3)b);
            if(!pa.finite()||!pb.finite()||!Double.isFinite(length)||length<=0)return new Warm(null,null,null,"INVALID_CURVE_RUNTIME");
            return new Warm(pa,pb,length,null);
        } catch(ReflectiveOperationException|RuntimeException ex) {return new Warm(null,null,null,"CURVE_RUNTIME_UNAVAILABLE");}
    }
    private static Object field(Object object,String name)throws ReflectiveOperationException {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private static Geometry geometry(TrackGraph graph,TrackNode from,TrackNode to,TrackEdge edge,Budget budget) {
        Point a=Point.of(from.getLocation().getLocation()),b=Point.of(to.getLocation().getLocation());
        if(!from.getLocation().dimension.equals(to.getLocation().dimension))return new Geometry("INTER_DIMENSION_LINK",a,b,null,null,"PORTAL_LINK_NOT_PHYSICAL_RAIL");
        if(edge.isTurn()) {
            var curve=edge.getTurn();var warm=warmCurve(curve);var raw=RawCurve.of(curve);
            if(!raw.startA.finite()||!raw.startB.finite())return new Geometry("BEZIER",a,b,null,null,"INVALID_CURVE_COORDINATES");
            return new Geometry("BEZIER",raw.startA,raw.startB,warm.controlA,warm.controlB,warm.failure);
        }
        if(from.getLocation().yOffsetPixels!=0||to.getLocation().yOffsetPixels!=0) {
            // TrackEdge.getPositionSmoothed selects the last adjacent turn's start at each end.
            Point first=smoothedStart(graph,from,budget),second=smoothedStart(graph,to,budget);
            if(first!=null&&second!=null) {a=first;b=second;}
        }
        if(!a.finite()||!b.finite())return new Geometry("LINE",a,b,null,null,"INVALID_GEOMETRY");
        return new Geometry("LINE",a,b,null,null,null);
    }
    private static Point smoothedStart(TrackGraph graph,TrackNode node,Budget budget) {
        Point found=null;for(var edge:graph.getConnectionsFrom(node).values()){budget.check();if(edge.isTurn())found=Point.of(edge.getTurn().starts.getFirst());}return found;
    }
}
