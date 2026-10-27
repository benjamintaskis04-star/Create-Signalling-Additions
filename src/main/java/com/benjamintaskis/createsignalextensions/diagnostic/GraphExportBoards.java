package com.benjamintaskis.createsignalextensions.diagnostic;

import com.benjamintaskis.createsignalextensions.content.speed.*;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.track.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.benjamintaskis.createsignalextensions.diagnostic.GraphExportSnapshot.*;

/** Capture explicit loaded board links, then verify against the frozen graph using CSA's criteria. */
public final class GraphExportBoards {
    private GraphExportBoards() {}
    public static Snapshot capture(MinecraftServer server,Snapshot snapshot,Budget budget) {
        List<Board> boards=new ArrayList<>();List<String> errors=new ArrayList<>();int levels=0;
        try {
            for(var level:server.getAllLevels()) {
                if(++levels>64)throw new Limit("loaded dimension limit reached");budget.check();
                int remaining=MAX_BOARDS-boards.size();
                var signs=SpeedRestrictionManager.trackLinkedSnapshot(level,remaining+1);
                if(signs.size()>remaining)errors.add("Loaded sign snapshot limit in "+level.dimension().location()+"; only registered signs are considered.");
                for(var linked:signs) {
                    budget.check();if(boards.size()==MAX_BOARDS)throw new Limit("board limit reached");
                    if(!(linked instanceof SpeedBoardBlockEntity board)||board.isRemoved()||board.getLevel()!=level)continue;
                    try {boards.add(board(level,board));}
                    catch(RuntimeException ex){
                        if(errors.size()<MAX_ERRORS)errors.add("Board "+board.getBlockPos()+": "+ex.getClass().getSimpleName());
                        boards.add(new Board(level.dimension().location()+":"+board.getBlockPos().toShortString(),level.dimension().location().toString(),
                                Point.of(Vec3.atCenterOf(board.getBlockPos())),board.getDefaultSpeedKmh(),board.isUnrestricted(),board.isTurnoutBoard(),
                                new Point(0,0,0),board.getTargetDirection(),new Link("INVALID",null,null,-1,null,null,null,null,null,"ASSOCIATION_CAPTURE_FAILED")));
                    }
                }
            }
        } catch(Limit ex){errors.add(ex.getMessage());}
        boards.sort(Comparator.comparing(Board::dimension).thenComparing(Board::id));
        return snapshot.withBoards(boards,errors);
    }
    private static Point block(net.minecraft.core.BlockPos p){return p==null?null:new Point(p.getX(),p.getY(),p.getZ());}
    private static Board board(ServerLevel level,SpeedBoardBlockEntity board) {
        Direction facing=board.getBlockState().getValue(SpeedBoardBlock.FACING);
        Link link;
        var anchor=board.getTargetTrack();
        if(anchor==null)link=new Link("UNLINKED",null,null,-1,null,null,null,null,null,"UNLINKED");
        else if(!level.hasChunkAt(anchor))link=new Link("UNLOADED",block(anchor),null,-1,null,null,null,null,null,"TRACK_UNLOADED");
        else {
            var state=level.getBlockState(anchor);
            if(!(state.getBlock() instanceof ITrackBlock track))link=new Link("MISSING",block(anchor),null,-1,null,null,null,null,null,"TRACK_REMOVED");
            else if(board.hasBezierTarget()) {
                var target=board.getBezierTarget();
                var curve=level.getBlockEntity(anchor) instanceof TrackBlockEntity be?be.getConnections().get(target.curveTarget()):null;
                link=new Link("CURVE",block(anchor),block(target.curveTarget()),target.segment(),null,null,null,null,
                        curve==null?null:RawCurve.of(curve),curve==null?"CURVE_MISSING":null);
            } else {
                var axes=track.getTrackAxes(level,anchor,state);
                Vec3 centre=Vec3.atBottomCenterOf(anchor).add(0,track.getElevationAtCenter(level,anchor,state),0);
                if(axes.size()!=1)link=new Link("STRAIGHT",block(anchor),null,-1,Point.of(centre),null,null,null,null,"AMBIGUOUS_TRACK_AXIS");
                else if(level.getBlockEntity(anchor) instanceof TrackBlockEntity be&&be.isTilted()) {
                    var a=centre.subtract(axes.getFirst().scale(.5));var b=centre.add(axes.getFirst().scale(.5));
                    var at=new TrackNodeLocation(a).in(level);at.yOffsetPixels=track.getYOffsetAt(level,anchor,state,a);
                    var bt=new TrackNodeLocation(b).in(level);bt.yOffsetPixels=track.getYOffsetAt(level,anchor,state,b);
                    link=new Link("TILTED",block(anchor),null,-1,Point.of(centre),Point.of(axes.getFirst()),Point.of(at.getLocation()),Point.of(bt.getLocation()),null,null);
                } else link=new Link("STRAIGHT",block(anchor),null,-1,Point.of(centre),Point.of(axes.getFirst()),null,null,null,null);
            }
        }
        String dimension=level.dimension().location().toString();
        return new Board(dimension+":"+board.getBlockPos().toShortString(),dimension,
                Point.of(Vec3.atCenterOf(board.getBlockPos())),board.getDefaultSpeedKmh(),board.isUnrestricted(),board.isTurnoutBoard(),
                new Point(facing.getStepX(),0,facing.getStepZ()),board.getTargetDirection(),link);
    }
    public record Association(String status,String graph,String edge,String from,String to,Double edgePosition,
                              Double geometryT,Integer direction,Point point,Point travelTangent) {
        static Association unresolved(String reason){return new Association(reason,null,null,null,null,null,null,null,null,null);}
    }
    private record Candidate(Graph graph,Edge edge,double position,double t,int direction,Point point) {}
    private record LineKey(long x,long y,long z,long across,long height) {}
    private static LineKey lineKey(Point point,Point axis) {
        Point d=axis.unit();if(d.x()< -1e-8||Math.abs(d.x())<1e-8&&d.z()<0)d=d.scale(-1);
        double h=Math.hypot(d.x(),d.z());if(h<1e-8)return null;
        return new LineKey(Math.round(d.x()*1e6),Math.round(d.y()*1e6),Math.round(d.z()*1e6),
                Math.round((-d.z()*point.x()+d.x()*point.z())/h*1e6),
                Math.round((point.y()-d.y()*(point.x()*d.x()+point.z()*d.z())/(h*h))*1e6));
    }
    public static Association resolve(Snapshot snapshot,Board board) {
        if(board.link()==null||board.link().reason()!=null)return Association.unresolved(board.link()==null?"NO_LINK":board.link().reason());
        if(!snapshot.topologyComplete())return Association.unresolved("INCOMPLETE_GRAPH_SNAPSHOT");
        List<Candidate> candidates=new ArrayList<>();boolean unknown=false;
        var link=board.link();
        if(link.kind().equals("CURVE")&&(link.worldCurve()==null||!Objects.equals(link.block(),link.worldCurve().blockA())
                ||!Objects.equals(link.curveEnd(),link.worldCurve().blockB())))return Association.unresolved("CURVE_ANCHOR_MISMATCH");
        for(var graph:snapshot.graphs()) {
            Map<String,Node> nodes=new HashMap<>();for(var node:graph.nodes())nodes.put(node.id(),node);
            for(var edge:graph.edges()) {
                if(!edge.fromDimension().equals(board.dimension())||!edge.toDimension().equals(board.dimension()))continue;
                double position=Double.NaN;
                if(link.kind().equals("CURVE")) {
                    if(edge.rawCurve()==null||link.worldCurve()==null)continue;
                    boolean direct=edge.rawCurve().equals(link.worldCurve());
                    boolean reverse=edge.rawCurve().equals(link.worldCurve().reverse());
                    if(!direct&&!reverse)continue;
                    if(edge.length()==null||!edge.geometry().available()){unknown=true;continue;}
                    double selected=(link.segment()+1)/2.0;
                    if(link.segment()<0||selected>edge.length())continue;
                    position=direct?selected:edge.length()-selected;
                } else {
                    if(edge.rawCurve()!=null||edge.length()==null||edge.length()<1e-6||!edge.geometry().available())continue;
                    if(link.kind().equals("TILTED")) {
                        var a=nodes.get(edge.from()).position();var b=nodes.get(edge.to()).position();
                        if(!(a.equals(link.tiltedA())&&b.equals(link.tiltedB())||a.equals(link.tiltedB())&&b.equals(link.tiltedA())))continue;
                        position=edge.length()/2;
                    } else {
                        Point a=edge.geometry().start(),d=edge.geometry().end().minus(a);
                        if(link.axis()==null||lineKey(link.centre(),link.axis())==null
                                ||!lineKey(link.centre(),link.axis()).equals(lineKey(a,d))
                                ||Math.abs(d.unit().dot(link.axis().unit()))<.999)continue;
                        double fraction=link.centre().minus(a).dot(d)/d.dot(d);
                        if(fraction< -1e-7||fraction>1.0000001)continue;fraction=Math.clamp(fraction,0,1);
                        if(a.plus(d.scale(fraction)).minus(link.centre()).dot(a.plus(d.scale(fraction)).minus(link.centre()))>1e-6)continue;
                        position=fraction*edge.length();
                    }
                }
                if(!Double.isFinite(position)||edge.length()==null||edge.length()<=0)continue;
                double t=position/edge.length();var geometry=edge.geometry();
                double dot=geometry.at(Math.min(1,t+.001)).minus(geometry.at(Math.max(0,t-.001))).dot(board.facing());
                if(Math.abs(dot)<1e-8)continue;int direction=dot>0?-1:1;
                // A reversed directed connection must actually exist before claiming its direction.
                if(direction<0&&!edge.reversePresent())continue;
                candidates.add(new Candidate(graph,edge,direction>0?position:edge.length()-position,t,direction,geometry.at(t)));
            }
        }
        if(unknown)return Association.unresolved("CURVE_GEOMETRY_UNAVAILABLE");
        if(candidates.stream().map(c->c.graph.id()).distinct().count()>1)return Association.unresolved("AMBIGUOUS_GRAPH");
        var outgoing=candidates.stream().filter(c->c.position<1e-7).toList();
        var usable=outgoing.isEmpty()?candidates:outgoing;
        if(usable.size()!=1)return Association.unresolved(usable.isEmpty()?"NO_UNAMBIGUOUS_EDGE":"AMBIGUOUS_EDGE");
        var c=usable.getFirst();return new Association("VERIFIED",c.graph.id(),c.edge.id(),c.direction>0?c.edge.from():c.edge.to(),
                c.direction>0?c.edge.to():c.edge.from(),c.position,c.t,c.direction,c.point,
                c.edge.geometry().at(Math.min(1,c.t+.001)).minus(c.edge.geometry().at(Math.max(0,c.t-.001))).unit().scale(c.direction));
    }
}
