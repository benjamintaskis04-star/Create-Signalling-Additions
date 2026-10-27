package com.benjamintaskis.createsignalextensions.diagnostic;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import static com.benjamintaskis.createsignalextensions.diagnostic.GraphExportSnapshot.*;

/** Geometry and actual PNG/JSON output checks; no live world or graph mutation. */
public final class GraphElevationLabelChecks {
    private static final String DIM = "minecraft:overworld";
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static Point p(double x, double y, double z) { return new Point(x,y,z); }
    private static Geometry line(Point a, Point b) { return new Geometry("LINE",a,b,null,null,null); }
    private static Geometry curve(Point a, Point b, Point c, Point d) { return new Geometry("BEZIER",a,d,b,c,null); }
    private static Edge edge(String id, String a, String b, Geometry geometry) {
        return new Edge(id,a,b,DIM,DIM,true,40.0,null,geometry);
    }
    private static Graph graph(String id, Edge... edges) {
        Map<String,Point> points = new TreeMap<>(); Map<String,Integer> degrees = new TreeMap<>();
        for (var edge : edges) {
            points.put(edge.from(),edge.geometry().start()); points.put(edge.to(),edge.geometry().end());
            degrees.merge(edge.from(),1,Integer::sum); degrees.merge(edge.to(),1,Integer::sum);
        }
        List<Node> nodes = new ArrayList<>();
        for (var entry : points.entrySet()) nodes.add(new Node(entry.getKey(),nodes.size()+1,DIM,entry.getValue(),0,degrees.get(entry.getKey())));
        return new Graph(id,"#cc2222",nodes,List.of(edges));
    }
    private static Snapshot snapshot(Graph... graphs) { return new Snapshot(1,456,graphs.length,true,List.of(graphs),List.of(),List.of()); }
    private static List<GraphElevationLabels.Label> labels(Snapshot snapshot) {
        return GraphElevationLabels.find(snapshot,DIM,GraphExportFiles.sample(snapshot));
    }
    private static boolean has(List<GraphElevationLabels.Label> labels, Point point) {
        return labels.stream().anyMatch(l -> l.point().minus(point).length() < 1e-7);
    }
    private static void straight() {
        var a=p(0,64,0); var b=p(10,64,0); var c=p(20,64,0);
        check(labels(snapshot(graph("flat",edge("a","0","1",line(a,b)),edge("b","1","2",line(b,c))))).isEmpty(),
                "Level track has no Y labels, regardless of graph-node count");
        b=p(10,65,0); c=p(20,66,0);
        var slope=labels(snapshot(graph("slope",edge("a","0","1",line(a,b)))));
        check(slope.size()==2&&has(slope,a)&&has(slope,b),"Straight slope labels exact geometry endpoints, never the midpoint");
        var continuous=labels(snapshot(graph("continuous",edge("a","0","1",line(a,b)),edge("b","2","1",line(c,b)))));
        check(continuous.size()==2&&has(continuous,a)&&has(continuous,c),"Reversed edge orientation does not turn a continuous slope into a boundary");
        c=p(20,67,0);
        var transition=labels(snapshot(graph("change",edge("a","0","1",line(a,b)),edge("b","1","2",line(b,c)))));
        check(transition.size()==3&&has(transition,b),"A meaningful change from 10% to 20% gradient is labelled once");
        c=p(20,64,0);
        var crest=labels(snapshot(graph("crest",edge("a","0","1",line(a,b)),edge("b","1","2",line(b,c)))));
        check(crest.size()==3&&has(crest,b),"A straight uphill/downhill crest is a shared slope boundary");
        var levelSlope=labels(snapshot(graph("level-slope-level",edge("a","0","1",line(p(-10,64,0),a)),
                edge("b","1","2",line(a,b)),edge("c","2","3",line(b,p(20,65,0))))));
        check(levelSlope.size()==2&&has(levelSlope,a)&&has(levelSlope,b),"Level-to-slope and slope-to-level endpoints only");
        var rounded=labels(snapshot(graph("rounding",edge("a","0","1",line(a,b)),edge("b","1","2",line(b,p(20,66.0001,0))))));
        check(rounded.size()==2,"Insignificant gradient rounding does not clutter a continuous slope");
    }
    private static void curves() {
        var monotonic=curve(p(0,64,0),p(10,64,10),p(20,68,-10),p(30,68,0));
        var simple=labels(snapshot(graph("curved",edge("a","0","1",monotonic))));
        check(simple.size()==2&&has(simple,monotonic.start())&&has(simple,monotonic.end()),
                "Continuous curved gradient has endpoint labels even with horizontal endpoint tangents");
        var flat=curve(p(0,64,0),p(10,64,10),p(20,64,-10),p(30,64,0));
        check(labels(snapshot(graph("curved-flat",edge("a","0","1",flat)))).isEmpty(),"Curved level track has no Y labels");
        var hump=curve(p(0,64,0),p(10,68,10),p(20,68,-10),p(30,64,0));
        var crest=labels(snapshot(graph("hump",edge("a","0","1",hump))));
        check(crest.size()==3&&has(crest,p(15,67,0)),"Bezier crest uses the analytic dy/dt root and exact cubic XYZ, not its chord");
        var two=curve(p(0,64,0),p(10,68,10),p(20,60,-10),p(30,64,0));
        var both=labels(snapshot(graph("two-extrema",edge("a","0","1",two))));
        check(both.size()==4&&has(both,two.at((3-Math.sqrt(3))/6))&&has(both,two.at((3+Math.sqrt(3))/6)),
                "Both genuine interior Bezier slope reversals are retained");
        var reversed=curve(two.end(),two.controlB(),two.controlA(),two.start());
        var reversedLabels=labels(snapshot(graph("reversed-curve",edge("a","1","0",reversed))));
        check(reversedLabels.size()==both.size()&&both.stream().allMatch(l->has(reversedLabels,l.point())),
                "Reversing an entire cubic and its controls preserves all world-coordinate slope boundaries");
        var pause=curve(p(0,64,0),p(10,65,10),p(20,64,-10),p(30,65,0));
        check(labels(snapshot(graph("stationary-tangent",edge("a","0","1",pause)))).size()==2,
                "A double dy/dt root on a continuous climb is not a crest or extra boundary");
        var eased=labels(snapshot(graph("eased",edge("a","0","1",line(p(-10,64,0),monotonic.start())),
                edge("b","1","2",monotonic),edge("c","2","3",line(monotonic.end(),p(40,68,0))))));
        check(eased.size()==2,"Flat-to-curved-slope transitions are found even where dy/dt equals zero exactly at the join");
        var next=curve(monotonic.end(),p(40,68,10),p(50,72,-10),p(60,72,0));
        var connected=labels(snapshot(graph("connected-curves",edge("a","0","1",monotonic),edge("b","1","2",next))));
        check(connected.size()==2&&!has(connected,monotonic.end()),"Connected curved climbs with matching zero tangents remain one continuous slope");
        var nextSteep=curve(monotonic.end(),p(40,70,0),p(50,72,0),p(60,74,0));
        check(labels(snapshot(graph("curved-transition",edge("a","0","1",monotonic),edge("b","1","2",nextSteep)))).size()==3,
                "A real gradient discontinuity between curved edges is labelled");
        var degenerate=curve(p(0,64,0),p(0,64,0),p(10,65,0),p(20,66,0));
        check(labels(snapshot(graph("coincident-controls",edge("a","0","1",degenerate)))).size()==2,
                "Coincident Bezier endpoint controls use the limiting geometric tangent");
    }
    private static void topologyAndUnknown() {
        var a=p(0,64,0); var b=p(10,65,0); var c=p(20,66,0);
        var unknown=new Geometry("BEZIER",b,c,null,null,"COLD_CURVE_CACHE");
        check(labels(snapshot(graph("unknown",edge("a","0","1",unknown)))).isEmpty(),"Unavailable geometry invents no slope or transition");
        var mixed=labels(snapshot(graph("mixed",edge("a","0","1",line(a,b)),edge("b","1","2",unknown))));
        check(mixed.size()==1&&has(mixed,a),"An unavailable continuation does not falsely terminate a known slope at its node");
        var mismatch=labels(snapshot(graph("smoothed-mismatch",edge("a","0","1",line(a,b)),
                edge("b","1","2",line(p(10,65.1,0),c)))));
        check(mismatch.size()==2&&!has(mismatch,b),"Shared graph IDs do not invent a transition where captured endpoints do not meet");
        var portal=new Edge("portal","1","2",DIM,"minecraft:the_nether",true,0.0,null,
                new Geometry("INTER_DIMENSION_LINK",b,c,null,null,"PORTAL_LINK_NOT_PHYSICAL_RAIL"));
        var portalLabels=labels(snapshot(graph("portal",edge("a","0","1",line(a,b)),portal)));
        check(portalLabels.size()==1&&has(portalLabels,a),"A dimension link does not fabricate a physical slope endpoint");
        check(labels(snapshot(graph("vertical",edge("a","0","1",line(a,p(0,65,0)))))).isEmpty(),
                "Degenerate horizontal geometry does not invent a running gradient");
        var junction=labels(snapshot(graph("junction",edge("a","0","1",line(a,b)),
                edge("b","1","2",line(b,p(20,67,5))),edge("c","1","3",line(b,p(20,67,-5))))));
        check(junction.size()==4&&junction.stream().filter(l->l.point().equals(b)).count()==1,
                "Three connected edges share a single gradient-transition label");
        double branchY=65+Math.hypot(10,5)*.1;
        var branching=labels(snapshot(graph("same-grade-junction",edge("a","0","1",line(a,b)),
                edge("b","1","2",line(b,p(20,branchY,5))),edge("c","1","3",line(b,p(20,branchY,-5))))));
        check(branching.size()==3&&!has(branching,b),"Two outgoing branches on the same grade are not mistaken for a crest");
        var missing=new Graph("missing","#cc2222",List.of(new Node("0",1,DIM,a,0,2),new Node("1",2,DIM,b,0,1)),
                List.of(edge("a","0","1",line(a,b))));
        check(labels(snapshot(missing)).size()==1,"Incomplete incident geometry cannot establish a terminal slope boundary");
        var low=graph("low",edge("a","0","1",line(a,b)));
        var high=graph("high",edge("a","0","1",line(p(0,74,0),p(10,75,0))));
        var overpass=labels(snapshot(low,high));
        check(overpass.size()==4&&has(overpass,a)&&has(overpass,p(0,74,0)),"Overlapping projections preserve distinct graph and Y identities");
        check(labels(snapshot(graph("flat-low",edge("a","0","1",line(a,p(10,64,10)))),
                graph("flat-high",edge("a","0","1",line(p(0,74,10),p(10,74,0)))))).isEmpty(),
                "Flat tracks crossing at different elevations do not acquire any slope labels");
        check(labels(snapshot(low,graph("separate",edge("a","0","1",line(a,b))))).size()==4,
                "Unconnected graphs at identical coordinates are not silently merged");
    }
    private static void layoutAndOutput() throws Exception {
        var slope=graph("00000000-0000-0000-0000-000000000001",edge("slope","a","b",line(p(0,64,0),p(10,65,0))));
        var high=graph("00000000-0000-0000-0000-000000000002",edge("slope","a","b",line(p(0,74,0),p(10,75,0))));
        var captured=snapshot(slope,high);
        var projection=GraphExportFiles.projection(new GraphExportFiles.Bounds(-2,-2,12,2),32);
        var canvas=new BufferedImage(1024,1024,BufferedImage.TYPE_INT_RGB); var graphics=canvas.createGraphics();
        graphics.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12)); var map=new Rectangle(80,64,880,720);
        var placed=GraphElevationLabels.place(labels(captured),projection,graphics.getFontMetrics(),map);
        check(placed.size()==4,"Basic collision avoidance retains nearby slope labels at different elevations");
        boolean overlaps=false;
        for(int i=0;i<placed.size();i++) for(int j=i+1;j<placed.size();j++) overlaps|=placed.get(i).box().intersects(placed.get(j).box());
        check(!overlaps&&placed.stream().allMatch(l->map.contains(l.box())),"Slope label boxes do not overlap or leave the map");
        check(placed.equals(GraphElevationLabels.place(labels(captured),projection,graphics.getFontMetrics(),map)),"Collision placement is deterministic");
        List<GraphElevationLabels.Label> crowded=new ArrayList<>();
        for(int i=0;i<200;i++) crowded.add(new GraphElevationLabels.Label("graph","node"+i,p(i%20,64,i/20)));
        var crowd=GraphElevationLabels.place(crowded,projection,graphics.getFontMetrics(),map);
        check(crowd.size()<=64,"Crowded networks have bounded label counts and placement searches");
        var tiny=new Rectangle(80,64,graphics.getFontMetrics().stringWidth("Y=0.00")+16,60);
        var tinyProjection=new GraphExportFiles.Projection(0,0,1,1,1,tiny.getCenterX(),tiny.getCenterY(),1024,1024);
        List<GraphElevationLabels.Label> unsuccessful=new ArrayList<>(Collections.nCopies(GraphElevationLabels.MAX_PLACEMENT_ATTEMPTS,
                new GraphElevationLabels.Label("graph","wide",p(0,1_000_000,0))));
        unsuccessful.add(new GraphElevationLabels.Label("graph","short",p(0,0,0)));
        check(GraphElevationLabels.place(unsuccessful,tinyProjection,graphics.getFontMetrics(),tiny).isEmpty()
                &&!GraphElevationLabels.place(List.of(unsuccessful.getLast()),tinyProjection,graphics.getFontMetrics(),tiny).isEmpty(),
                "Failed candidate placements stop at the bounded attempt budget, even before reaching the label-count cap");
        graphics.dispose();canvas.flush();
        var rendered=GraphExportFiles.render(captured,DIM,GraphExportFiles.sample(captured),List.of(),projection);
        check(placed.stream().allMatch(l->darkPixels(rendered,l.box())>0),"Actual PNG renderer paints the placed slope label text");rendered.flush();
        var box=placed.getFirst().box();
        var markerPoint=p(projection.minX()+(box.getCenterX()-projection.originPixelX())/projection.pixelsPerBlock(),64,
                projection.minZ()+(box.getCenterY()-projection.originPixelZ())/projection.pixelsPerBlock());
        var marker=new Board("marker",DIM,markerPoint,20,false,false,p(-1,0,0),false,null);
        var withMarker=GraphExportFiles.render(captured,DIM,GraphExportFiles.sample(captured),
                List.of(new GraphExportFiles.LocatedBoard(marker,GraphExportBoards.Association.unresolved("UNLINKED"))),projection);
        check((withMarker.getRGB((int)projection.x(markerPoint.x())+2,(int)projection.z(markerPoint.z()))&0xffffff)
                ==(new Color(130,30,160).getRGB()&0xffffff),"A label box cannot hide the physical speed-board marker beneath it");
        withMarker.flush();
        var flat=snapshot(graph("00000000-0000-0000-0000-000000000003",edge("flat","a","b",line(p(0,64,0),p(10,64,0)))));
        var flatImage=GraphExportFiles.render(flat,DIM,GraphExportFiles.sample(flat),List.of(),projection);
        check(darkPixels(flatImage,new Rectangle(81,65,878,718))==0,"Actual level-track PNG contains no elevation text in its map");flatImage.flush();
        var geometry=curve(p(0,64,0),p(10,68,10),p(20,68,-10),p(30,64,0));
        var curved=graph("00000000-0000-0000-0000-000000000004",edge("curve","a","b",geometry));
        var board=new Board("board",DIM,p(1,66,0),20,false,true,p(-1,0,0),false,
                new Link("UNLINKED",null,null,-1,null,null,null,null,null,"UNLINKED"));
        var flatLow=graph("00000000-0000-0000-0000-000000000005",edge("flat","a","b",line(p(0,70,0),p(10,70,10))));
        var flatHigh=graph("00000000-0000-0000-0000-000000000006",edge("flat","a","b",line(p(0,80,10),p(10,80,0))));
        var data=new Snapshot(1,456,5,true,List.of(slope,high,curved,flatLow,flatHigh),List.of(board),List.of());
        var before=new Gson().toJson(data);var samples=GraphExportFiles.sample(data);
        Path base=Path.of("build","elevation-label-checks");
        var output=GraphExportFiles.write(data,base,()->false);
        var folder=output.directory().resolve(GraphExportFiles.dimensionFolder(DIM));
        var json=JsonParser.parseString(Files.readString(folder.resolve("graph.json"))).getAsJsonObject();
        var gson=new Gson();
        check(output.errors().isEmpty()&&output.dimensions()==1,"Slope annotations integrate with actual PNG/JSON export without errors");
        var jsonGraphs=json.getAsJsonArray("graphs");
        check(jsonGraphs.get(0).getAsJsonObject().getAsJsonArray("nodes").equals(gson.toJsonTree(slope.nodes())),
                "JSON retains exact node XYZ, IDs, degrees and topology");
        check(jsonGraphs.get(3).getAsJsonObject().getAsJsonArray("nodes").equals(gson.toJsonTree(flatLow.nodes()))
                &&jsonGraphs.get(4).getAsJsonObject().getAsJsonArray("nodes").equals(gson.toJsonTree(flatHigh.nodes())),
                "Unlabelled flat overpasses retain both exact elevations and independent connectivity in JSON");
        var curveJson=jsonGraphs.get(2).getAsJsonObject().getAsJsonArray("edges").get(0).getAsJsonObject();
        check(curveJson.getAsJsonObject("edge").equals(new com.google.gson.GsonBuilder().serializeNulls().create().toJsonTree(curved.edges().getFirst()))
                &&curveJson.getAsJsonArray("samples").equals(gson.toJsonTree(samples.get(2).samples())),
                "JSON retains exact curve controls, full XYZ samples and elevation values including unlabelled points");
        var association=json.getAsJsonArray("speedBoards").get(0).getAsJsonObject().getAsJsonObject("association");
        check(association.equals(new com.google.gson.GsonBuilder().serializeNulls().create().toJsonTree(GraphExportBoards.resolve(data,board))),
                "PNG annotations do not change speed-board associations");
        check(before.equals(gson.toJson(data))&&samples.equals(GraphExportFiles.sample(data)),"Label finding, placement and rendering leave captured graph data unchanged");
        check(Files.readString(output.directory().resolve("SUMMARY.txt")).contains("slope boundaries only"),"Summary explains the presentation rule");
        System.out.println("Elevation label visual fixture: "+folder.resolve("overview.png").toAbsolutePath());
    }
    private static int darkPixels(BufferedImage image,Rectangle box) {
        int count=0;
        for(int x=box.x;x<box.x+box.width;x++)for(int y=box.y;y<box.y+box.height;y++)
            if((image.getRGB(x,y)&0xffffff)==(Color.DARK_GRAY.getRGB()&0xffffff))count++;
        return count;
    }
    public static void main(String[] args) throws Exception {
        straight();curves();topologyAndUnknown();layoutAndOutput();
        System.out.println("Graph elevation label checks passed: "+checks);
    }
}
