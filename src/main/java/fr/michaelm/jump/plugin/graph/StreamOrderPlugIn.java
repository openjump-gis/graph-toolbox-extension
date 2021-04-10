package fr.michaelm.jump.plugin.graph;

import com.vividsolutions.jump.I18N;
import com.vividsolutions.jump.feature.*;
import com.vividsolutions.jump.task.TaskMonitor;
import com.vividsolutions.jump.util.CollectionUtil;
import com.vividsolutions.jump.workbench.model.Layer;
import com.vividsolutions.jump.workbench.model.StandardCategoryNames;
import com.vividsolutions.jump.workbench.plugin.*;
import com.vividsolutions.jump.workbench.ui.AttributeTypeFilter;
import com.vividsolutions.jump.workbench.ui.GUIUtil;
import com.vividsolutions.jump.workbench.ui.MenuNames;
import com.vividsolutions.jump.workbench.ui.MultiInputDialog;
import com.vividsolutions.jump.workbench.ui.renderer.style.*;
import fr.michaelm.jump.feature.jgrapht.FeatureAsEdge;
import fr.michaelm.jump.feature.jgrapht.GraphFactory;
import fr.michaelm.jump.feature.jgrapht.INode;
import org.jgrapht.graph.DirectedWeightedPseudograph;

import javax.swing.*;
import java.awt.*;
import java.util.*;

/**
 * Compute <a href="http://en.wikipedia.org/wiki/Strahler_number">Strahler Numbers</a>
 * on a graph edges.
 */
public class StreamOrderPlugIn extends ThreadedBasePlugIn {

    private static String LAYER;

    private static String STREAM_ORDER;
    private static String GRAPH_COMPUTATION;
    private static String OLD_ALGO;
    private static String LENGTH_ATTRIBUTE;

    private static final String STRAHLER          = "Strahler";
    private static String STRAHLER_TT;
    private static String OTHER_ORDERS;
    private static final String SEGMENT_ORIGIN    = "SegmentOrig";
    private static final String SHREVE            = "Shreve";
    private static String SHREVE_TT;

    private static String METRICS;
    private static final String FLOW_ACC          = "FlowAcc";
    private static final String MAX_DIST          = "MaxDist";
    private static final String HORTON            = "Horton";
    private static String HORTON_TT;
    private static final String HACK              = "Hack";
    private static String HACK_TT;
    private static final String HACK_DIST_ORDER   = "HackDistO";
    private static final String HACK_DIST         = "HackDist";
    private static final String HACK_FLOW_ORDER   = "HackFlowO";
    private static final String HACK_FLOW         = "HackFlow";
    private static final String HACK_DF_ORDER     = "HackDFO";
    private static final String HACK_DF           = "HackDF";
    private static final String MOUTH_DISTANCE    = "MouthDist";

    Layer layer;
    boolean old_algo = false;
    boolean shreve   = false;

    boolean metrics  = false;
    boolean horton   = false;
    boolean hack     = false;
    String lengthAttribute;
    int lengthAttributeIndex;
    boolean lengthAttributeIsGeometry;


    public String getName() {return "Graph nodes PlugIn";}

    @Override
    public void initialize(final PlugInContext context) {

        String GRAPH            = I18NPlug.getI18N("Graph");

        LAYER                   = I18N.get("ui.GenericNames.LAYER");
        GRAPH_COMPUTATION       = I18NPlug.getI18N("Graph-computation");
        STREAM_ORDER            = I18NPlug.getI18N("StreamOrderPlugIn.stream-order");
        STRAHLER_TT             = I18NPlug.getI18N("StreamOrderPlugIn.strahler-tt");
        OTHER_ORDERS            = I18NPlug.getI18N("StreamOrderPlugIn.other-orders");
        SHREVE_TT               = I18NPlug.getI18N("StreamOrderPlugIn.shreve-tt");
        OLD_ALGO                = I18NPlug.getI18N("StreamOrderPlugIn.old-algorithm");
        METRICS                 = I18NPlug.getI18N("StreamOrderPlugIn.metrics");
        LENGTH_ATTRIBUTE        = I18NPlug.getI18N("StreamOrderPlugIn.length-attribute");
        HORTON_TT               = I18NPlug.getI18N("StreamOrderPlugIn.horton-tt");
        HACK_TT                 = I18NPlug.getI18N("StreamOrderPlugIn.hack-tt");

        context.getFeatureInstaller().addMainMenuPlugin(
                this, new String[]{MenuNames.PLUGINS, GRAPH},
                STREAM_ORDER + "...",
                false, null, new MultiEnableCheck()
                        .add(context.getCheckFactory().createTaskWindowMustBeActiveCheck())
                        .add(context.getCheckFactory().createAtLeastNLayersMustExistCheck(1)));
    }

    @Override
    public boolean execute(PlugInContext context) {
        final MultiInputDialog dialog = initDialog(context);

                GUIUtil.centreOnWindow(dialog);
        dialog.setPreferredSize(new Dimension(480,480));
        dialog.setVisible(true);
        if (dialog.wasOKPressed()) {
            layer = dialog.getLayer(LAYER);
            old_algo = dialog.getBoolean(OLD_ALGO);
            shreve = dialog.getBoolean(SHREVE);
            metrics = dialog.getBoolean(METRICS);
            lengthAttribute = dialog.getText(LENGTH_ATTRIBUTE);
            lengthAttributeIndex = layer.getFeatureCollectionWrapper()
                    .getFeatureSchema().getAttributeIndex(lengthAttribute);
            lengthAttributeIsGeometry = lengthAttributeIndex ==
                    layer.getFeatureCollectionWrapper().getFeatureSchema().getGeometryIndex();
            hack = dialog.getBoolean(HACK);
            horton = dialog.getBoolean(HORTON);
            return true;
        }
        else return false;
    }

    public void run(TaskMonitor monitor, PlugInContext context) {
        monitor.allowCancellationRequests();
        monitor.report(GRAPH_COMPUTATION + "...");

        FeatureCollection sourceFC = layer.getFeatureCollectionWrapper();

        // Creates the schema for the output dataset (nodes)
        final FeatureSchema newSchema = sourceFC.getFeatureSchema().clone();
        newSchema.addAttribute(STRAHLER, AttributeType.INTEGER);
        newSchema.addAttribute(SEGMENT_ORIGIN, AttributeType.OBJECT);
        if (shreve) newSchema.addAttribute(SHREVE, AttributeType.DOUBLE);
        if (metrics) newSchema.addAttribute(MAX_DIST, AttributeType.DOUBLE);
        if (metrics) newSchema.addAttribute(FLOW_ACC, AttributeType.DOUBLE);
        if (hack) {
            newSchema.addAttribute(HACK_DIST_ORDER, AttributeType.INTEGER);
            newSchema.addAttribute(HACK_DIST, AttributeType.DOUBLE);
            newSchema.addAttribute(HACK_FLOW_ORDER, AttributeType.INTEGER);
            newSchema.addAttribute(HACK_FLOW, AttributeType.DOUBLE);
            newSchema.addAttribute(MOUTH_DISTANCE, AttributeType.DOUBLE);
            newSchema.addAttribute(HACK_DF_ORDER, AttributeType.INTEGER);
            newSchema.addAttribute(HACK_DF, AttributeType.DOUBLE);
        }
        if (horton) newSchema.addAttribute(HORTON, AttributeType.INTEGER);
        FeatureCollection resultFC = new FeatureDataset(newSchema);
        for (Object o : sourceFC.getFeatures()) {
            Feature f = (Feature)o;
            Feature bf = new BasicFeature(newSchema);
            for (int i = 0 ; i < f.getSchema().getAttributeCount() ; i++) {
                bf.setAttribute(i, f.getAttribute(i));
            }
            bf.setGeometry(f.getGeometry().copy());
            resultFC.add(bf);
        }
        DirectedWeightedPseudograph<INode,FeatureAsEdge> graph =
                GraphFactory.createDirectedGraph(resultFC.getFeatures(), false);

        int count = 0;
        int total = resultFC.size();
        for (FeatureAsEdge arc : graph.edgeSet()) {
            if (arc.getAttribute(STRAHLER) != null) continue;
            if (old_algo) computeLegacyStrahlerOrder(graph, arc);
            else computeNewStrahlerOrder(graph, arc);
            monitor.report(count++, total, " features processed (Strahler)");
        }
        // Change -1 (cycles or cycle successors) to null
        for (FeatureAsEdge arc : graph.edgeSet()) {
            Object order = arc.getAttribute(STRAHLER);
            if (order != null && (Integer)order == -1) {
                arc.setAttribute(STRAHLER,null);
            }
        }
        if (metrics) {
            int maxDistIdx = newSchema.getAttributeIndex(MAX_DIST);
            int flowAccIdx = newSchema.getAttributeIndex(FLOW_ACC);
            for (FeatureAsEdge arc : graph.edgeSet()) {
                if (arc.getAttribute(MAX_DIST) != null) continue;
                computeMaxLengthAndFlowAcc(graph, arc, maxDistIdx, flowAccIdx);
                monitor.report(count++, total, " features processed (max dist /flow accumulation)");
            }
            if (horton) {
                for (FeatureAsEdge arc : graph.edgeSet()) {
                    if (arc.getAttribute(HORTON) != null) continue;
                    computeHortonStreamOrder(graph, arc);
                    monitor.report(count++, total, " features processed (Horton)");
                }
            }
            if (hack) {
                for (FeatureAsEdge arc : graph.edgeSet()) {
                    if (arc.getAttribute(HACK_DIST_ORDER) != null) continue;
                    computeHackStreamOrder(graph, arc);
                    monitor.report(count++, total, " features processed (Hack)");
                }
            }
        }

        context.getLayerManager().addLayer(StandardCategoryNames.RESULT, layer.getName()+"-strahler",resultFC);
        Layer resultLayer = context.getLayerManager().getLayer(layer.getName() + "-strahler");
        // Styling
        layer.setVisible(false);
        resultLayer.getBasicStyle().setEnabled(false);
        resultLayer.addStyle(getColorThemingStyle());
    }


    /**
     * This version of Stream Order calculation is a re-implementation of the very first
     * version proposed in OpenJUMP which used too much memory. While the first version
     * was storing a list of all ancestors of each edge in a map, this one computes the
     * list of ancestors again and again. It is slower than the former version but can
     * terminate on a computer with less RAM.
     * The algorithm differs from the following (computeStreamOrder3) in the case where
     * an edge has two input streams a and b with the same StreamOrder :
     * In the following algorithm, if a and b have at least one common ancestor, the
     * stream order of the downstream edge will not be incremented.
     * In computeStreamOrder3, the downstream edge will not be incremented only if a and
     * b have the same segment head (see comments in computeStreamOrder3 method).
     * @param graph base graph
     * @param arc edge to compute
     */
    private void computeLegacyStrahlerOrder(DirectedWeightedPseudograph<INode,FeatureAsEdge> graph,
                                   FeatureAsEdge arc) {

        int maxOrder = 0;
        int occ = 0;
        FeatureAsEdge maxUpstream = null;
        for (FeatureAsEdge upstream : graph.incomingEdgesOf(graph.getEdgeSource(arc))) {
            Object att = upstream.getAttribute(STRAHLER);
            // Process current stream only if all upstreams are already processed
            if (att == null) return;
            int upstreamOrder = (Integer)att;
            // Case 1 : upstream order > all previous ones
            // (when the loop is finished : upstreamOrder > all others)
            if (upstreamOrder > maxOrder) {
                maxOrder = upstreamOrder;
                occ = 1; // there is only 1 upstream > all others
                maxUpstream = upstream;
            }
            // Case 2 : upstream order = max order of previous ones (ex aequo)
            else if (upstreamOrder == maxOrder) {
                if (!hasCommonAncestors(graph, upstream, maxUpstream)) {
                    occ++; // there are several upstreams = maxOrder
                }
            }
            // If upstream order is < max, it does not contribute to downstream
            // order calculation
            else;
        }
        // Head water of a stream (edge without predecessor) has order 1
        if (maxOrder == 0) {
            arc.setAttribute(STRAHLER, 1);
            if (shreve) arc.setAttribute(SHREVE, 1.0);
            //if (metrics) arc.setAttribute(FLOW_ACC, getLength(arc));
        }
        else {
            // Stream order of the current edge is incremented if it has 2 or more
            // predecessors = maxOrder
            arc.setAttribute(STRAHLER, occ>1?maxOrder+1:maxOrder);
            if (shreve) arc.setAttribute(SHREVE, calculateShreveNumber(graph, arc));
            //if (metrics) arc.setAttribute(FLOW_ACC, calculateFlowAccumulation(graph, arc));
        }
        // Try to compute stream order recursively on all downstream edges
        Set<FeatureAsEdge> downStreams = graph.outgoingEdgesOf(graph.getEdgeTarget(arc));

        for (FeatureAsEdge downStream : downStreams) {
            // In case of anastomosis, compute the downstream edge only once
            if (downStream.getAttribute(STRAHLER) == null) {
                computeLegacyStrahlerOrder(graph, downStream);
            }
        }
    }

    private double calculateShreveNumber(DirectedWeightedPseudograph<INode,FeatureAsEdge> graph,
                                  FeatureAsEdge arc) {
        INode n = graph.getEdgeSource(arc);
        double s = 0.0;
        int attIdx = arc.getSchema().getAttributeIndex(SHREVE);
        for (FeatureAsEdge e : graph.incomingEdgesOf(n)) {
            s += e.getDouble(attIdx);
        }
        return s / graph.outDegreeOf(graph.getEdgeSource(arc));
    }


    private Set<Integer> getAncestors(DirectedWeightedPseudograph<INode,FeatureAsEdge> graph,
                                   FeatureAsEdge arc, Set<Integer> ancestors) {
        for (FeatureAsEdge e : graph.incomingEdgesOf(graph.getEdgeSource(arc))) {
            if (ancestors.contains(e.getID())) continue;
            ancestors.add(e.getID());
            getAncestors(graph, e, ancestors);
        }
        return ancestors;
    }

    private boolean hasCommonAncestors(DirectedWeightedPseudograph<INode,FeatureAsEdge> graph,
                                       FeatureAsEdge arc1, FeatureAsEdge arc2) {
        Set<Integer> ancestors1 = getAncestors(graph, arc1, new HashSet<>());
        Set<Integer> ancestors2 = getAncestors(graph, arc2, new HashSet<>());
        int card1 = ancestors1.size();
        int card2 = ancestors2.size();
        ancestors1.addAll(ancestors2);
        return ancestors1.size() < card1 + card2;
    }

    // Implements algorithm described in
    // A FAST RECURSIVE GIS ALGORITHM FOR COMPUTING STRAHLER STREAM ORDER
    // IN BRAIDED AND NON BRAIDED NETWORKS
    // Alexander Gleyzer, Michael Denisyuk, Alon Rimmer, and Yigal Salingar (2004)
    private void computeNewStrahlerOrder(DirectedWeightedPseudograph<INode,FeatureAsEdge> graph,
                                     FeatureAsEdge arc) {

        Object strahlerOrder = arc.getAttribute(STRAHLER);
        // If arc already has a positive stream order, don't process it again
        // If its stream-order = -1, it means it belongs to a cycle, or it belongs
        // to an ancestor and has been pre-set to -1 to detect cycles
        // In all of these case, we don't want to process it
        if (strahlerOrder != null) return;
        // Flag current edge to be able to identify cycles while exploring ancestors
        // recursively
        arc.setAttribute(STRAHLER, -1);

        // Stream order of the current edge has not yet been computed.
        // Compute predecessors stream order first (recursion)
        Set<FeatureAsEdge> upStreams = graph.incomingEdgesOf(graph.getEdgeSource(arc));
        boolean cycle = false;
        for (FeatureAsEdge upStream : upStreams) {
            // Visit/compute ancestors recursively
            computeNewStrahlerOrder(graph, upStream);
            // Post order : check that current stream is not part of a cycle
            // if upstream == -1, it means it has already been initialized in the context
            // of this recursive process (cycle)
            Object upStreamOrder = upStream.getAttribute(STRAHLER);
            assert upStreamOrder != null; // stream order is initialized to -1 in pre-order
            if ((Integer)upStreamOrder == -1) {
                cycle = true;
            }
        }

        // If one of the upstream is part of a cycle, current edge cannot be computed
        if (cycle) {
            return;
        }
        // We are now in the normal situation of an edge which has all its ancestors
        // computed and which does not belong to a cycle nor have a cycle as ancestor
        int maxOrder = 0;
        INode maxOrderOrigin = null;
        int occ = 0;
        for (FeatureAsEdge upstream : graph.incomingEdgesOf(graph.getEdgeSource(arc))) {
            Object att1 = upstream.getAttribute(STRAHLER);
            assert att1 != null;

            int upstreamOrder = (Integer)att1;
            INode segmentOrigin = (INode)upstream.getAttribute(SEGMENT_ORIGIN);
            // Case 1 : upstream order > all previous ones
            if (upstreamOrder > maxOrder) {
                maxOrderOrigin = segmentOrigin;
                maxOrder = upstreamOrder;
                occ = 1;
            }
            // Case 2 : upstream order = max order of previous ones (ex aequo)
            else if (upstreamOrder == maxOrder) {
                if (!segmentOrigin.equals(maxOrderOrigin)) {
                    occ++;
                }
            }
        }
        // Set the stream order and segment origin of the current edge
        if (maxOrder == 0) {
            arc.setAttribute(STRAHLER, 1);
            arc.setAttribute(SEGMENT_ORIGIN, graph.getEdgeSource(arc));
            if (shreve) arc.setAttribute(SHREVE, 1.0);
            //if (flowAcc) arc.setAttribute(FLOW_ACC, getLength(arc));
        }
        else if (occ > 1) {
            arc.setAttribute(STRAHLER, maxOrder+1);
            arc.setAttribute(SEGMENT_ORIGIN, graph.getEdgeSource(arc));
            if (shreve) arc.setAttribute(SHREVE, calculateShreveNumber(graph, arc));
            //if (flowAcc) arc.setAttribute(FLOW_ACC, calculateFlowAccumulation(graph, arc));
        }
        else {
            arc.setAttribute(STRAHLER, maxOrder);
            arc.setAttribute(SEGMENT_ORIGIN, maxOrderOrigin);
            if (shreve) arc.setAttribute(SHREVE, calculateShreveNumber(graph, arc));
            //if (flowAcc) arc.setAttribute(FLOW_ACC, calculateFlowAccumulation(graph, arc));
        }

    }

    private void computeMaxLengthAndFlowAcc(DirectedWeightedPseudograph<INode,FeatureAsEdge> graph,
                                            FeatureAsEdge arc, int maxDistIdx, int flowAccIdx) {
        // Interrupt infinite recursion in case of cycle
        if (arc.getAttribute(MAX_DIST) != null) return;
        arc.setAttribute(MAX_DIST, getLength(arc));
        arc.setAttribute(FLOW_ACC, getLength(arc));
        // Compute predecessors stream order first (recursion)
        Set<FeatureAsEdge> upStreams = graph.incomingEdgesOf(graph.getEdgeSource(arc));
        // Visit/compute ancestors recursively
        for (FeatureAsEdge upStream : upStreams) {
            computeMaxLengthAndFlowAcc(graph, upStream, maxDistIdx, flowAccIdx);
        }
        // We are now in the normal situation of an edge whith all its ancestors computed
        double maxMaxDist = 0;
        double maxFlowAcc = 0;
        for (FeatureAsEdge upstream : graph.incomingEdgesOf(graph.getEdgeSource(arc))) {
            double maxDist = upstream.getDouble(maxDistIdx);
            double flowAcc = upstream.getDouble(flowAccIdx);
            if (maxDist > maxMaxDist) maxMaxDist = maxDist;
            if (flowAcc > maxFlowAcc) maxFlowAcc = flowAcc;
        }
        arc.setAttribute(MAX_DIST, arc.getDouble(maxDistIdx) + maxMaxDist);
        arc.setAttribute(FLOW_ACC, calculateFlowAccumulation(graph, arc));
    }

    private double getLength(FeatureAsEdge arc) {
        return lengthAttributeIsGeometry ?
                arc.getGeometry().getLength() : arc.getDouble(lengthAttributeIndex);
    }

    private double calculateFlowAccumulation(DirectedWeightedPseudograph<INode,FeatureAsEdge> graph,
                                             FeatureAsEdge arc) {
        INode n = graph.getEdgeSource(arc);
        double s = 0.0;
        for (FeatureAsEdge e : graph.incomingEdgesOf(n)) {
            s += (Double)e.getAttribute(FLOW_ACC);
        }
        return getLength(arc) +
                s / graph.outDegreeOf(graph.getEdgeSource(arc));
    }

    private void computeHortonStreamOrder(DirectedWeightedPseudograph<INode,FeatureAsEdge> graph,
                                       FeatureAsEdge arc) {
        // Horton's computation needs Strahler order
        if (arc.getAttribute(STRAHLER) == null) return;
        if (graph.outDegreeOf(graph.getEdgeTarget(arc)) == 0) {
            arc.setAttribute(HORTON, arc.getAttribute(STRAHLER));
        } else {
            Set<FeatureAsEdge> downStreams = graph.outgoingEdgesOf(graph.getEdgeTarget(arc));
            int maxDownStreamOrder = 0;
            for (FeatureAsEdge d : downStreams) {
                // Calculate Horton order of successors first
                if (d.getAttribute(HORTON) == null) {
                    computeHortonStreamOrder(graph, d);
                }
                Object h = d.getAttribute(HORTON);
                if (h == null) continue;
                if ((Integer)h > maxDownStreamOrder) maxDownStreamOrder = (Integer)h;
            }
            // When Horton order of all successors have been calculated,
            // iterate through all tributaries flowing this arc's successors
            // to find the main one
            int maxOrder = 0;
            double maxFlow = 0.0;
            FeatureAsEdge maxEdge = null;
            // Compare all incoming edges arriving at the same node as arc
            Set<FeatureAsEdge> upStreams = graph.incomingEdgesOf(graph.getEdgeTarget(arc));
            for (FeatureAsEdge e : upStreams) {
                if (e.getAttribute(STRAHLER) == null) continue;
                int order = (Integer)e.getAttribute(STRAHLER);
                double flow = (Double)e.getAttribute(FLOW_ACC);
                if (order > maxOrder || (order == maxOrder && flow > maxFlow)) {
                    maxOrder = order;
                    maxFlow = flow;
                    maxEdge = e;
                }
            }
            for (FeatureAsEdge e : upStreams) {
                if (e == maxEdge) {
                    e.setAttribute(HORTON, maxDownStreamOrder);
                } else {
                    e.setAttribute(HORTON, e.getAttribute(STRAHLER));
                }
            }
        }
    }

    private void computeHackStreamOrder(DirectedWeightedPseudograph<INode,FeatureAsEdge> graph,
                                          FeatureAsEdge arc) {
        arc.setAttribute(HACK_DIST_ORDER, Integer.MAX_VALUE);
        arc.setAttribute(HACK_FLOW_ORDER, Integer.MAX_VALUE);
        if (graph.outDegreeOf(graph.getEdgeTarget(arc)) == 0) {
            arc.setAttribute(HACK_DIST_ORDER, 1);
            arc.setAttribute(HACK_FLOW_ORDER, 1);
            arc.setAttribute(HACK_DF_ORDER, 1);
            arc.setAttribute(HACK_DIST, arc.getAttribute(MAX_DIST));
            arc.setAttribute(HACK_FLOW, arc.getAttribute(FLOW_ACC));
            arc.setAttribute(HACK_DF, (double)arc.getAttribute(FLOW_ACC)*(double)arc.getAttribute(MAX_DIST));
            arc.setAttribute(MOUTH_DISTANCE, getLength(arc));
        } else {
            Set<FeatureAsEdge> downStreams = graph.outgoingEdgesOf(graph.getEdgeTarget(arc));
            boolean cycle = false;
            for (FeatureAsEdge d : downStreams) {
                // Calculate Hack order of successors first
                if (d.getAttribute(HACK_DIST_ORDER) == null || d.getAttribute(HACK_FLOW_ORDER) == null) {
                    computeHackStreamOrder(graph, d);
                }
                // cycle detection
                Object hdo = d.getAttribute(HACK_DIST_ORDER);
                Object hfo = d.getAttribute(HACK_FLOW_ORDER);
                assert hdo != null && hfo != null;
                if ((Integer)hdo == -1 || (Integer)hfo == -1) {
                    cycle = true;
                }

            }
            FeatureAsEdge mainDownStreamD = null;
            FeatureAsEdge mainDownStreamF = null;
            FeatureAsEdge mainDownStreamDF = null;
            double maxMouthDist = 0.0;
            // In case of division, choose the downstream which is the farthest from the river mouth
            // to avoid little forks which do not throw into the sea
            double maxProductD = 0.0;
            double maxProductF = 0.0;
            double maxProductDF = 0.0;
            for (FeatureAsEdge d : downStreams) {
                //Object hackD = d.getAttribute(HACK_DIST_ORDER);
                //Object hackF = d.getAttribute(HACK_FLOW_ORDER);
                Object mouthDist = d.getAttribute(MOUTH_DISTANCE);
                Object hackDistOrder = d.getAttribute(HACK_DIST_ORDER);
                if (hackDistOrder != null && mouthDist != null  &&
                        (double)mouthDist / (int)hackDistOrder > maxProductD) {
                    maxProductD = (double)mouthDist / (int)hackDistOrder;
                    mainDownStreamD = d;
                }
                Object hackFlowOrder = d.getAttribute(HACK_FLOW_ORDER);
                if (hackFlowOrder != null && mouthDist != null &&
                        (double)mouthDist / (int)hackFlowOrder > maxProductF) {
                    maxProductF = (double)mouthDist / (int)hackFlowOrder;
                    mainDownStreamF = d;
                }
                Object hackDFOrder = d.getAttribute(HACK_DF_ORDER);
                if (hackDFOrder != null && mouthDist != null &&
                        (double)mouthDist / (int)hackDFOrder > maxProductDF) {
                    maxProductDF = (double)mouthDist / (int)hackDFOrder;
                    mainDownStreamDF = d;
                }
                if (mouthDist != null && (Double)mouthDist > maxMouthDist) {
                    maxMouthDist = (Double) d.getAttribute(MOUTH_DISTANCE);
                }
            }
            arc.setAttribute(MOUTH_DISTANCE, getLength(arc) + maxMouthDist);
            int downStreamHackDistanceOrder = 1;
            double downStreamDistance = 0.0;
            if (mainDownStreamD != null && mainDownStreamD.getAttribute(HACK_DIST_ORDER) != null) {
                downStreamHackDistanceOrder = (int)mainDownStreamD.getAttribute(HACK_DIST_ORDER);
                downStreamDistance = (double)mainDownStreamD.getAttribute(HACK_DIST);
            }
            int downStreamHackFlowOrder = 1;
            double downStreamFlow = 0.0;
            if (mainDownStreamF != null && mainDownStreamF.getAttribute(HACK_FLOW_ORDER) != null) {
                downStreamHackFlowOrder = (int)mainDownStreamF.getAttribute(HACK_FLOW_ORDER);
                downStreamFlow = (double)mainDownStreamF.getAttribute(HACK_FLOW);
            }
            int downStreamHackDFOrder = 1;
            double downStreamDF = 0.0;
            if (mainDownStreamDF != null && mainDownStreamDF.getAttribute(HACK_DF_ORDER) != null) {
                downStreamHackDFOrder = (int)mainDownStreamDF.getAttribute(HACK_DF_ORDER);
                downStreamDF = (double)mainDownStreamDF.getAttribute(HACK_DF);
            }
            //int downStreamHackD = 1;
            //int downStreamHackF = 1;
            //Object downStreamDist = arc.getAttribute(MAX_DIST);
            //Object downStreamFlow = arc.getAttribute(FLOW_ACC);
            //if (mainDownStreamD != null) {
            //    downStreamHackD = (int)mainDownStreamD.getAttribute(HACK_DIST_ORDER);
            //    downStreamHackF = (int)mainDownStreamF.getAttribute(HACK_FLOW_ORDER);
            //    downStreamDist = mainDownStreamD.getAttribute(HACK_DIST);
            //    downStreamFlow = mainDownStreamF.getAttribute(HACK_FLOW);
            //}
            // Process cycles as a stream start
            //if (minHackD == Integer.MAX_VALUE) {
            //    minHackD = 1;
            //    downStreamDist = (Double)arc.getAttribute(MAX_DIST);
            //}
            //if (minHackF == Integer.MAX_VALUE) {
            //    minHackF = 1;
            //    downStreamFlow = (Double)arc.getAttribute(FLOW_ACC);
            //}

            //maxDownStreamMouthDist = (Double)arc.getAttribute(MOUTH_DISTANCE);

            // When Horton order of all successors have been calculated,
            // search the main tributary
            double maxFlow = 0.0;
            double maxDist = 0.0;
            double maxDF = 0.0;
            FeatureAsEdge maxFlowEdge = null;
            FeatureAsEdge maxDistEdge = null;
            FeatureAsEdge maxDFEdge = null;
            // Compare all incoming edges arriving at the same node as arc
            Set<FeatureAsEdge> upStreams = graph.incomingEdgesOf(graph.getEdgeTarget(arc));
            for (FeatureAsEdge e : upStreams) {
                double flow = (Double)e.getAttribute(FLOW_ACC);
                double dist = (Double)e.getAttribute(MAX_DIST);
                double df = (Double)e.getAttribute(MAX_DIST)*(Double)e.getAttribute(FLOW_ACC);
                if (flow > maxFlow) {
                    maxFlow = flow;
                    maxFlowEdge = e;
                }
                if (dist > maxDist) {
                    maxDist = dist;
                    maxDistEdge = e;
                }
                if (df > maxDF) {
                    maxDF = df;
                    maxDFEdge = e;
                }
            }
            for (FeatureAsEdge e : upStreams) {
                if (e == maxFlowEdge) {
                    e.setAttribute(HACK_FLOW_ORDER, downStreamHackFlowOrder);
                    e.setAttribute(HACK_FLOW, downStreamFlow);
                } else {
                    e.setAttribute(HACK_FLOW_ORDER, downStreamHackFlowOrder + 1);
                    e.setAttribute(HACK_FLOW, e.getAttribute(FLOW_ACC));
                }
                if (e == maxDistEdge) {
                    e.setAttribute(HACK_DIST_ORDER, downStreamHackDistanceOrder);
                    e.setAttribute(HACK_DIST, downStreamDistance);
                } else {
                    e.setAttribute(HACK_DIST_ORDER, downStreamHackDistanceOrder + 1);
                    e.setAttribute(HACK_DIST, e.getAttribute(MAX_DIST));
                }
                if (e == maxDFEdge) {
                    e.setAttribute(HACK_DF_ORDER, downStreamHackDFOrder);
                    e.setAttribute(HACK_DF, downStreamDF);
                } else {
                    e.setAttribute(HACK_DF_ORDER, downStreamHackDFOrder + 1);
                    e.setAttribute(HACK_DF, (double)e.getAttribute(MAX_DIST)*(double)e.getAttribute(FLOW_ACC));
                }
                e.setAttribute(MOUTH_DISTANCE, getLength(e) + maxMouthDist);
            }
        }
    }


    public MultiInputDialog initDialog(final PlugInContext context) {
        final MultiInputDialog dialog = new MultiInputDialog(
                context.getWorkbenchFrame(), STREAM_ORDER, true);
        dialog.setSideBarImage(new ImageIcon(this.getClass().getResource("StrahlerNumber.png")));
        dialog.setSideBarDescription(I18NPlug.getI18N("StreamOrderPlugIn.description"));
        dialog.addLayerComboBox(LAYER, context.getCandidateLayer(0), null, context.getLayerManager());
        dialog.addSubTitle(STRAHLER);
        dialog.addCheckBox(OLD_ALGO, old_algo);
        dialog.addSubTitle(OTHER_ORDERS);
        dialog.addCheckBox(SHREVE, shreve, SHREVE_TT);

        // ---------- orders based on cumulative stream length flows ----------
        dialog.addSeparator();
        JCheckBox flowAccumulationCB = dialog.addCheckBox(METRICS, metrics);
        JComboBox<String> lengthAttributeCB = dialog.addAttributeComboBox(LENGTH_ATTRIBUTE, LAYER,
                new AttributeTypeFilter(AttributeTypeFilter.GEOMETRY + AttributeTypeFilter.DOUBLE),
                null);
        lengthAttributeCB.setEnabled(metrics);
        JCheckBox hortonCB = dialog.addCheckBox(HORTON, horton, HORTON_TT);
        hortonCB.setEnabled(metrics);
        JCheckBox hackCB = dialog.addCheckBox(HACK, hack, HACK_TT);
        hackCB.setEnabled(metrics);
        flowAccumulationCB.addActionListener(actionEvent -> {
            lengthAttributeCB.setEnabled(flowAccumulationCB.isSelected());
            hortonCB.setEnabled(flowAccumulationCB.isSelected());
            hackCB.setEnabled(flowAccumulationCB.isSelected());
        });
        return dialog;
    }


    @SuppressWarnings("unchecked")
    ColorThemingStyle getColorThemingStyle() {
        BasicStyle dbs = new BasicStyle();   dbs.setLineColor(new Color(255,0,0));      dbs.setLineWidth(2);
        BasicStyle bs1  = new BasicStyle();  bs1.setLineColor(new Color(120,240,255));  bs1.setLineWidth(1);
        BasicStyle bs2  = new BasicStyle();  bs2.setLineColor(new Color( 90,180,255));  bs2.setLineWidth(1);
        BasicStyle bs3  = new BasicStyle();  bs3.setLineColor(new Color( 60,120,255));  bs3.setLineWidth(2);
        BasicStyle bs4  = new BasicStyle();  bs4.setLineColor(new Color( 30, 60,255));  bs4.setLineWidth(3);
        BasicStyle bs5  = new BasicStyle();  bs5.setLineColor(new Color(  0,  0,255));  bs5.setLineWidth(4);
        BasicStyle bs6  = new BasicStyle();  bs6.setLineColor(new Color( 60,  0,255));  bs6.setLineWidth(5);
        BasicStyle bs7  = new BasicStyle();  bs7.setLineColor(new Color( 90,  0,255));  bs7.setLineWidth(7);
        BasicStyle bs8  = new BasicStyle();  bs8.setLineColor(new Color(120,  0,255));  bs8.setLineWidth(9);
        BasicStyle bs9  = new BasicStyle();  bs9.setLineColor(new Color(150,  0,255));  bs9.setLineWidth(11);
        BasicStyle bs10 = new BasicStyle(); bs10.setLineColor(new Color(180,  0,255)); bs10.setLineWidth(13);
        BasicStyle bs11 = new BasicStyle(); bs11.setLineColor(new Color(210,  0,255)); bs11.setLineWidth(15);
        BasicStyle bs12 = new BasicStyle(); bs12.setLineColor(new Color(240,  0,255)); bs12.setLineWidth(18);
        ColorThemingStyle cts =  new ColorThemingStyle(STRAHLER,
                CollectionUtil.createMap(new Object[]{
                    1, bs1,
                    2, bs2,
                    3, bs3,
                    4, bs4,
                    5, bs5,
                    6, bs6,
                    7, bs7,
                    8, bs8,
                    9, bs9,
                    10, bs10,
                    11, bs11,
                    12, bs12}), dbs);
        cts.setEnabled(true);
        return cts;
    }

}
