package com.benjamintaskis.createsignalextensions.diagnostic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.function.BooleanSupplier;
import com.benjamintaskis.createsignalextensions.diagnostic.GraphExportSnapshot.Point;
import static com.benjamintaskis.createsignalextensions.diagnostic.GraphExportSnapshot.*;

/** Worker-only rendering/file output from immutable values; no Minecraft/Create access. */
public final class GraphExportFiles {
    private GraphExportFiles() {}
    public static final int WIDTH=1024,HEIGHT=1024,MAX_IMAGES=32,MAX_DIMENSIONS=16,MAX_TILES_PER_DIMENSION=4,MAX_SAMPLES=131072;
    private static final int LEFT=80,TOP=64,MAP_W=880,MAP_H=720,TILE_BLOCKS=256;
    private static final Gson JSON=new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    public record Bounds(double minX,double minZ,double maxX,double maxZ) {}
    public record Projection(double minX,double minZ,double maxX,double maxZ,double pixelsPerBlock,
                             double originPixelX,double originPixelZ,int imageWidth,int imageHeight) {
        public double x(double x){return originPixelX+(x-minX)*pixelsPerBlock;}
        public double z(double z){return originPixelZ+(z-minZ)*pixelsPerBlock;}
    }
    public record Picture(String file,String type,Projection projection) {}
    public record SampledEdge(String graph,Edge edge,List<Point> samples,boolean samplesLimited) {
        public SampledEdge {samples=List.copyOf(samples);}
    }
    public record LocatedBoard(Board board,GraphExportBoards.Association association) {}
    public record Result(Path directory,int images,int dimensions,List<String> errors) { public Result{errors=List.copyOf(errors);} }
    private record Tile(long x,long z) implements Comparable<Tile> {
        @Override public int compareTo(Tile b){int order=Long.compare(x,b.x);return order!=0?order:Long.compare(z,b.z);}
    }
    public static Projection projection(Bounds raw,double maximumScale) {
        double minX=raw.minX,maxX=raw.maxX,minZ=raw.minZ,maxZ=raw.maxZ;
        if(!Double.isFinite(minX)||!Double.isFinite(maxX)||!Double.isFinite(minZ)||!Double.isFinite(maxZ)||maxX<minX||maxZ<minZ)
            throw new IllegalArgumentException("invalid projection bounds");
        if(maxX-minX<1){minX-=.5;maxX+=.5;}if(maxZ-minZ<1){minZ-=.5;maxZ+=.5;}
        double scale=Math.min(maximumScale,Math.min(MAP_W/(maxX-minX),MAP_H/(maxZ-minZ)));
        if(!Double.isFinite(scale)||scale<=0)throw new IllegalArgumentException("invalid scale");
        return new Projection(minX,minZ,maxX,maxZ,scale,LEFT+(MAP_W-(maxX-minX)*scale)/2,
                TOP+(MAP_H-(maxZ-minZ)*scale)/2,WIDTH,HEIGHT);
    }
    public static String dimensionFolder(String dimension) {
        StringBuilder name=new StringBuilder("dim-");
        for(byte value:dimension.getBytes(StandardCharsets.UTF_8)) {
            int c=value&255;if(c>='a'&&c<='z'||c>='A'&&c<='Z'||c>='0'&&c<='9'||c=='_'||c=='-'||c=='.')name.append((char)c);
            else name.append(String.format(Locale.ROOT,"%%%02X",c));
        }
        return name.toString();
    }
    public static List<SampledEdge> sample(Snapshot snapshot) {
        List<SampledEdge> result=new ArrayList<>();int remaining=MAX_SAMPLES;
        for(var graph:snapshot.graphs())for(var edge:graph.edges()) {
            List<Point> samples=new ArrayList<>();boolean limited=false;
            if(edge.geometry().available()) {
                int wanted=edge.geometry().controlA()==null?2:Math.min(257,Math.max(2,(int)Math.ceil(Math.min(128,edge.length()==null?128:edge.length())*2)+1));
                int count=Math.min(wanted,remaining);limited=count<wanted;
                if(count>=2)for(int i=0;i<count;i++)samples.add(edge.geometry().at(i/(double)(count-1)));
                else limited=true;
                remaining-=samples.size();
            }
            result.add(new SampledEdge(graph.id(),edge,samples,limited));
        }
        return List.copyOf(result);
    }
    public static Result write(Snapshot snapshot,Path base,BooleanSupplier cancelled)throws IOException {
        if(Files.isSymbolicLink(base))throw new IOException("export base is a symbolic link");
        Files.createDirectories(base);
        String stamp=DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC).format(Instant.now());
        Path directory=null;
        for(int i=0;i<100;i++)try {directory=Files.createDirectory(base.resolve(stamp+(i==0?"":"-"+i)));break;}catch(FileAlreadyExistsException ignored){}
        if(directory==null)throw new IOException("unable to reserve unique export directory");
        var sampled=sample(snapshot);List<LocatedBoard> located=new ArrayList<>();
        for(var board:snapshot.boards())located.add(new LocatedBoard(board,GraphExportBoards.resolve(snapshot,board)));
        Set<String> dimensions=new TreeSet<>();
        for(var graph:snapshot.graphs())for(var node:graph.nodes())dimensions.add(node.dimension());
        for(var board:snapshot.boards())dimensions.add(board.dimension());
        if(dimensions.isEmpty())dimensions.add("minecraft:overworld");
        List<String> errors=new ArrayList<>(snapshot.errors());int images=0,writtenDimensions=0,attemptedDimensions=0;
        if(dimensions.size()>MAX_DIMENSIONS)errors.add("Dimension output limited to "+MAX_DIMENSIONS);
        int detailBudget=MAX_IMAGES-Math.min(MAX_DIMENSIONS,dimensions.size());
        for(var dimension:dimensions) {
            if(attemptedDimensions==MAX_DIMENSIONS||cancelled.getAsBoolean()){errors.add("Remaining dimensions omitted (limit/cancelled)");break;}
            attemptedDimensions++;
            try {
                Path folder=Files.createDirectory(directory.resolve(dimensionFolder(dimension)));
                var edges=sampled.stream().filter(e->e.edge.fromDimension().equals(dimension)||e.edge.toDimension().equals(dimension)).toList();
                var boards=located.stream().filter(b->b.board.dimension().equals(dimension)).toList();
                List<Picture> pictures=new ArrayList<>();
                var overview=projection(bounds(snapshot,dimension,boards),32);
                pictures.add(picture(folder,"overview.png","overview",snapshot,dimension,edges,boards,overview));images++;
                var tiles=tiles(snapshot,dimension,edges,boards);
                int selected=0;
                for(var tile:tiles) {
                    if(selected==MAX_TILES_PER_DIMENSION||detailBudget==0||cancelled.getAsBoolean())break;
                    var projection=projection(new Bounds(tile.x*TILE_BLOCKS,tile.z*TILE_BLOCKS,(tile.x+1)*TILE_BLOCKS,(tile.z+1)*TILE_BLOCKS),2);
                    String name="tile_x"+tile.x+"_z"+tile.z+".png";
                    try {pictures.add(picture(folder,name,"detail",snapshot,dimension,edges,boards,projection));images++;}
                    catch(IOException|RuntimeException ex){errors.add(dimension+" "+name+": "+ex.getClass().getSimpleName());}
                    selected++;detailBudget--;
                }
                if(tiles.size()>selected)errors.add(dimension+": detail tiles limited; overview and JSON retain captured geometry");
                writeJson(folder.resolve("graph.json"),snapshot,dimension,edges,boards,pictures);
                writtenDimensions++;
            }catch(IOException|RuntimeException ex){errors.add(dimension+": "+ex.getClass().getSimpleName()+": "+ex.getMessage());}
        }
        StringBuilder summary=new StringBuilder("CSA Alpha 1 track graph export\nSchema: 1\nCaptured tick: ").append(snapshot.capturedTick())
                .append("\nRegistered graphs: ").append(snapshot.registeredGraphs()).append("\nCaptured graphs: ").append(snapshot.graphs().size())
                .append("\nTopology complete: ").append(snapshot.topologyComplete()).append("\nPNG files: ").append(images)
                .append("\nJSON dimensions: ").append(writtenDimensions).append("\n\nRead-only registered graphs and loaded boards; no chunk loading.\n")
                .append("+X east/right, +Z south/down, north is -Z/up. Uniform X/Z scale. Full Y in JSON.\n")
                .append("Projected crossings are NOT junctions. Portal links are JSON-only.\n")
                .append("Graph-coloured lines; node circles; junction rings; purple physical board squares; green verified graph diamonds/arrows; orange unresolved X.\n")
                .append("Dashed green board association lines are NOT rails. Elevation dashes unchanged; Y labels mark verified slope boundaries only.\n")
                .append("Y labels: at most 64 collision-spaced labels per image; unknown geometry/continuations are not guessed. Full XYZ remains in JSON.\n")
                .append("Curves use captured Create runtime controls; cold/unavailable geometry is omitted, never guessed.\n")
                .append("Geometry t is normalized Bezier parameter, not guaranteed arc length. Board edgePosition uses Create edge units.\n")
                .append("Limits: 64 graphs, 8192 nodes, 16384 edges, 512 loaded boards, 131072 samples, 16 dimensions, 32 PNGs, 1024x1024 pixels each.\n")
                .append("Detail tiles: up to 4 per dimension, 256 blocks at 2 pixels/block, subject to global image budget.\n\nFindings/omissions:\n");
        for(var error:errors)summary.append("- ").append(error).append('\n');
        for(var edge:sampled)if(!edge.edge.geometry().available()||edge.samplesLimited)
            summary.append("- ").append(edge.graph).append('/').append(edge.edge.id()).append(": ")
                    .append(edge.samplesLimited?"sample list limited; full controls retained":edge.edge.geometry().unavailable()).append('\n');
        Files.writeString(directory.resolve("SUMMARY.txt"),summary.toString(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        return new Result(directory,images,writtenDimensions,errors);
    }
    private static Bounds bounds(Snapshot snapshot,String dimension,List<LocatedBoard> boards) {
        List<Point> points=new ArrayList<>();
        for(var graph:snapshot.graphs()) {
            for(var node:graph.nodes())if(node.dimension().equals(dimension))points.add(node.position());
            for(var edge:graph.edges())if(edge.fromDimension().equals(dimension)&&edge.toDimension().equals(dimension)&&edge.geometry().available()) {
                points.add(edge.geometry().start());points.add(edge.geometry().end());
                if(edge.geometry().controlA()!=null){points.add(edge.geometry().controlA());points.add(edge.geometry().controlB());}
            }
        }
        for(var board:boards){points.add(board.board.position());if(board.association.point()!=null)points.add(board.association.point());}
        if(points.isEmpty())return new Bounds(-1,-1,1,1);
        double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxZ=maxX;
        for(var p:points){minX=Math.min(minX,p.x());minZ=Math.min(minZ,p.z());maxX=Math.max(maxX,p.x());maxZ=Math.max(maxZ,p.z());}
        return new Bounds(minX-2,minZ-2,maxX+2,maxZ+2);
    }
    private static SortedSet<Tile> tiles(Snapshot snapshot,String dimension,List<SampledEdge> edges,List<LocatedBoard> boards) {
        SortedSet<Tile> tiles=new TreeSet<>();
        for(var graph:snapshot.graphs())for(var node:graph.nodes())if(node.dimension().equals(dimension))addTile(tiles,node.position());
        for(var edge:edges)if(edge.edge.geometry().available()) {
            for(var point:edge.samples)addTile(tiles,point);
            addTile(tiles,edge.edge.geometry().at(.5));
        }
        for(var board:boards)addTile(tiles,board.board.position());return tiles;
    }
    private static void addTile(Set<Tile> tiles,Point point){if(tiles.size()<128)tiles.add(new Tile((long)Math.floor(point.x()/TILE_BLOCKS),(long)Math.floor(point.z()/TILE_BLOCKS)));}
    private static Picture picture(Path directory,String name,String type,Snapshot snapshot,String dimension,
                                   List<SampledEdge> edges,List<LocatedBoard> boards,Projection projection)throws IOException {
        BufferedImage image=render(snapshot,dimension,edges,boards,projection);
        try(var out=Files.newOutputStream(directory.resolve(name),StandardOpenOption.CREATE_NEW);
            var stream=new MemoryCacheImageOutputStream(out)) {
            var writers=ImageIO.getImageWritersByFormatName("png");if(!writers.hasNext())throw new IOException("PNG writer unavailable");
            var writer=writers.next();try{writer.setOutput(stream);writer.write(image);}finally{writer.dispose();}
        }finally{image.flush();}
        return new Picture(name,type,projection);
    }
    public static BufferedImage render(Snapshot snapshot,String dimension,List<SampledEdge> edges,List<LocatedBoard> boards,Projection p) {
        BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        try {
            g.setColor(Color.WHITE);g.fillRect(0,0,WIDTH,HEIGHT);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));g.setColor(Color.DARK_GRAY);
            g.drawString("CSA registered track graph: "+dimension,LEFT,24);g.drawString("+X east/right | +Z south/down | tick "+snapshot.capturedTick(),LEFT,43);
            double step=Math.max(1,Math.pow(10,Math.ceil(Math.log10(Math.max(p.maxX-p.minX,p.maxZ-p.minZ)/8))));
            for(double x=Math.ceil(p.minX/step)*step;x<=p.maxX;x+=step){int at=(int)p.x(x);g.setColor(new Color(230,230,230));g.drawLine(at,TOP,at,TOP+MAP_H);g.setColor(Color.GRAY);g.drawString("X "+format(x),at+2,TOP+MAP_H+18);}
            for(double z=Math.ceil(p.minZ/step)*step;z<=p.maxZ;z+=step){int at=(int)p.z(z);g.setColor(new Color(230,230,230));g.drawLine(LEFT,at,LEFT+MAP_W,at);g.setColor(Color.GRAY);g.drawString("Z "+format(z),3,at);}
            g.setClip(LEFT,TOP,MAP_W,MAP_H);
            Map<String,String> colours=new HashMap<>();for(var graph:snapshot.graphs())colours.put(graph.id(),graph.colour());
            for(var item:edges) {
                var edge=item.edge;if(!edge.geometry().available())continue;var geometry=edge.geometry();
                if(!edge.fromDimension().equals(dimension)||!edge.toDimension().equals(dimension))continue;
                g.setColor(Color.decode(colours.get(item.graph)));double elevation=geometry.at(.5).y();
                g.setStroke(Math.floorMod((long)Math.floor(elevation),2)==0?new BasicStroke(2):new BasicStroke(2,BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND,10,new float[]{6,3},0));
                Path2D path=new Path2D.Double();path.moveTo(p.x(geometry.start().x()),p.z(geometry.start().z()));
                if(geometry.controlA()==null)path.lineTo(p.x(geometry.end().x()),p.z(geometry.end().z()));
                else path.curveTo(p.x(geometry.controlA().x()),p.z(geometry.controlA().z()),p.x(geometry.controlB().x()),p.z(geometry.controlB().z()),p.x(geometry.end().x()),p.z(geometry.end().z()));
                g.draw(path);
            }
            var elevationLabels=GraphElevationLabels.place(GraphElevationLabels.find(snapshot,dimension,edges),p,
                    g.getFontMetrics(),new Rectangle(LEFT,TOP,MAP_W,MAP_H));
            g.setStroke(new BasicStroke(1));
            for(var label:elevationLabels) {
                var box=label.box();g.setColor(new Color(100,100,100));
                int targetX=Math.clamp(label.anchorX(),box.x,box.x+box.width);
                int targetZ=Math.clamp(label.anchorZ(),box.y,box.y+box.height);
                g.drawLine(label.anchorX(),label.anchorZ(),targetX,targetZ);
            }
            for(var label:elevationLabels) {
                var box=label.box();g.setColor(Color.WHITE);g.fillRect(box.x,box.y,box.width,box.height);
                g.setColor(Color.DARK_GRAY);g.drawString(label.text(),box.x+3,box.y+1+g.getFontMetrics().getAscent());
            }
            for(var graph:snapshot.graphs())for(var node:graph.nodes())if(node.dimension().equals(dimension)) {
                int x=(int)p.x(node.position().x()),z=(int)p.z(node.position().z());g.setColor(Color.decode(graph.colour()));
                int radius=node.degree()>2?5:2;g.drawOval(x-radius,z-radius,radius*2,radius*2);
                if(node.degree()>2){g.setColor(Color.BLACK);g.drawOval(x-7,z-7,14,14);}
            }
            for(var item:boards) {
                var board=item.board;int x=(int)p.x(board.position().x()),z=(int)p.z(board.position().z());
                g.setColor(new Color(130,30,160));g.fillRect(x-3,z-3,6,6);
                if(item.association.point()!=null) {
                    var point=item.association.point();int ax=(int)p.x(point.x()),az=(int)p.z(point.z());g.setColor(new Color(0,140,60));
                    g.setStroke(new BasicStroke(1,BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND,10,new float[]{2,3},0));g.drawLine(x,z,ax,az);g.setStroke(new BasicStroke(1));
                    g.drawPolygon(new int[]{ax,ax+5,ax,ax-5},new int[]{az-5,az,az+5,az},4);
                    var direction=item.association.travelTangent();double horizontal=Math.hypot(direction.x(),direction.z());
                    double dx=horizontal<1e-8?0:direction.x()/horizontal,dz=horizontal<1e-8?0:direction.z()/horizontal;
                    int tx=ax+(int)(dx*12),tz=az+(int)(dz*12);
                    g.drawLine(ax,az,tx,tz);g.fillOval(tx-2,tz-2,4,4);
                } else {g.setColor(new Color(220,110,0));g.drawLine(x-6,z-6,x+6,z+6);g.drawLine(x-6,z+6,x+6,z-6);}
            }
            g.setClip(null);g.setStroke(new BasicStroke(1));g.setColor(Color.DARK_GRAY);g.drawRect(LEFT,TOP,MAP_W,MAP_H);
            int y=830;String[] legend={"Legend: coloured rails = registered edges; circles = nodes; rings = actual junctions.",
                    "Purple square = physical speed board; green diamond/arrow = verified graph attachment/direction.",
                    "Green dotted association lines are NOT rails. Orange X = unresolved loaded board (reason in JSON).",
                    "X/Z crossings are NOT junctions. Y labels: slope boundaries only; elevation dashes/full XYZ in JSON.",
                    "Portal links and unavailable curve geometry are NOT drawn. Nodes are NOT every track block.",
                    "Uniform scale: "+String.format(Locale.ROOT,"%.6g",p.pixelsPerBlock)+" pixels/block; north (-Z) is up.",
                    "Capture topology complete: "+snapshot.topologyComplete()+"; inspect SUMMARY.txt for limits/errors."};
            for(String line:legend){g.drawString(line,LEFT,y);y+=20;}
            int count=0;for(var graph:snapshot.graphs()){if(count++==8)break;g.setColor(Color.decode(graph.colour()));g.drawString(graph.id().substring(0,8),LEFT+(count-1)*100,990);}
        }finally{g.dispose();}
        return image;
    }
    private static String format(double n){return String.format(Locale.ROOT,"%.2f",Math.abs(n)<1e-9?0:n);}
    private static void writeJson(Path file,Snapshot snapshot,String dimension,List<SampledEdge> edges,List<LocatedBoard> boards,List<Picture> pictures)throws IOException {
        try(var output=Files.newBufferedWriter(file,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);var json=new JsonWriter(output)) {
            json.setIndent("  ");json.beginObject();json.name("schemaVersion").value(1);json.name("dimension").value(dimension);
            json.name("capturedTick").value(snapshot.capturedTick());json.name("topologyComplete").value(snapshot.topologyComplete());
            json.name("registeredGraphCount").value(snapshot.registeredGraphs());json.name("capturedGraphCount").value(snapshot.graphs().size());
            json.name("orientation").value("+X right/east; +Z down/south; full Y retained; projected crossing is not connectivity");
            json.name("geometryParameter").value("normalized t, not guaranteed arc-length fraction; association edgePosition uses Create units");
            json.name("limits").beginObject();json.name("graphs").value(MAX_GRAPHS);json.name("nodes").value(MAX_NODES);
            json.name("edges").value(MAX_EDGES);json.name("boards").value(MAX_BOARDS);json.name("samples").value(MAX_SAMPLES);
            json.name("images").value(MAX_IMAGES);json.endObject();
            json.name("captureErrors");JSON.toJson(snapshot.errors(),List.class,json);
            json.name("graphs");json.beginArray();
            for(var graph:snapshot.graphs()) {
                var nodes=graph.nodes().stream().filter(n->n.dimension().equals(dimension)).toList();
                if(nodes.isEmpty()&&!graph.nodes().isEmpty())continue;
                json.beginObject();json.name("id").value(graph.id());json.name("colour").value(graph.colour());json.name("nodes");JSON.toJson(nodes,List.class,json);
                json.name("edges");JSON.toJson(edges.stream().filter(e->e.graph.equals(graph.id())).toList(),List.class,json);json.endObject();
            }
            json.endArray();json.name("speedBoards");JSON.toJson(boards,List.class,json);json.name("images");JSON.toJson(pictures,List.class,json);json.endObject();
        }
    }
}
