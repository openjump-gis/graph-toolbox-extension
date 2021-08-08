package fr.michaelm.jump.plugin.graph;

import com.vividsolutions.jump.I18N;
import com.vividsolutions.jump.feature.*;
import com.vividsolutions.jump.task.TaskMonitor;
import com.vividsolutions.jump.workbench.model.Layer;
import com.vividsolutions.jump.workbench.model.StandardCategoryNames;
import com.vividsolutions.jump.workbench.plugin.MultiEnableCheck;
import com.vividsolutions.jump.workbench.plugin.PlugInContext;
import com.vividsolutions.jump.workbench.plugin.ThreadedBasePlugIn;
import com.vividsolutions.jump.workbench.ui.*;
import com.vividsolutions.jump.workbench.ui.renderer.style.ArrowLineStringSegmentStyle;
import com.vividsolutions.jump.workbench.ui.renderer.style.BasicStyle;
import com.vividsolutions.jump.workbench.ui.renderer.style.RingVertexStyle;
import fr.michaelm.jump.feature.jgrapht.FeatureAsEdge;
import fr.michaelm.jump.feature.jgrapht.GraphFactory;
import fr.michaelm.jump.feature.jgrapht.INode;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.alg.cycle.HawickJamesSimpleCycles;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DirectedPseudograph;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.List;

/**
 * PlugIn to detect or repair anomalies in a hydrographic network
 */
public class HydrographicNetworkAnalysisPlugIn extends ThreadedBasePlugIn {

    private final I18N i18n = I18N.getInstance("fr.michaelm.jump.plugin.graph");

    private static String LAYER;
    private static String GRAPH;
    private static String HYDROGRAPHIC_NETWORK_ANALYSIS;
    private static String GRAPH_COMPUTATION;

    private static String DETECT;
    private static String REPAIR;

    private static String FIND_CYCLES;
    private static String FIND_CYCLES_TT;
    private static String FIND_SOURCES;
    private static String FIND_SOURCES_TT;
    private static String FIND_SINKS;
    private static String FIND_SINKS_TT;
    private static String USE_Z;
    private static String USE_Z_TT;
    private static String TOL_Z;
    private static String TOL_Z_TT;

    private static String SOURCE;
    private static String SINK;
    private static String CYCLE;
    private static String UPWARD_EDGE;
    private static String Z_ANOMALY;
    private static String CYCLE_ANOMALY;
    private static String NODE_ANOMALY;
    private static String REVERSED_EDGES;

    private Layer layer;
    private boolean detect      = true;
    private boolean repair      = false;
    private boolean findCycles  = true;
    private boolean findSources = true; // we call source a node with indegree = 0 / outdegree > 1
    private boolean findSinks   = true; // we call well a node with outdegree = 0 / indegree > 1
    private boolean useZ        = true; // use z to find inverted edges
    private double tolZ         = 0;

    //public String getName() {return "Hydrographic network anomaly detection";}

    @Override
    public void initialize(final PlugInContext context) {

        LAYER                         = i18n.get("Layer");
        GRAPH                         = i18n.get("Graph");
        HYDROGRAPHIC_NETWORK_ANALYSIS = i18n.get("HydrographicNetworkAnalysisPlugIn");
        GRAPH_COMPUTATION             = i18n.get("Graph-computation");

        DETECT           = i18n.get("HydrographicNetworkAnalysisPlugIn.Detect");
        REPAIR           = i18n.get("HydrographicNetworkAnalysisPlugIn.Repair");

        FIND_CYCLES      = i18n.get("HydrographicNetworkAnalysisPlugIn.Cycles");
        FIND_CYCLES_TT   = i18n.get("HydrographicNetworkAnalysisPlugIn.Cycles-tooltip");
        FIND_SOURCES     = i18n.get("HydrographicNetworkAnalysisPlugIn.Sources-with-several-outcoming-edges");
        FIND_SOURCES_TT  = i18n.get("HydrographicNetworkAnalysisPlugIn.Sources-with-several-outcoming-edges");
        FIND_SINKS       = i18n.get("HydrographicNetworkAnalysisPlugIn.Sinks-with-several-incoming-edges");
        FIND_SINKS_TT    = i18n.get("HydrographicNetworkAnalysisPlugIn.Sinks-with-several-incoming-edges");
        USE_Z            = i18n.get("HydrographicNetworkAnalysisPlugIn.Use-z");
        USE_Z_TT         = i18n.get("HydrographicNetworkAnalysisPlugIn.Use-z-to-find-inverted-edges");
        TOL_Z            = i18n.get("HydrographicNetworkAnalysisPlugIn.Tolerance-for-Z");
        TOL_Z_TT         = i18n.get("HydrographicNetworkAnalysisPlugIn.Altitude-differences-less-than-the-tolerance-are-ignored");

        SOURCE           = i18n.get("HydrographicNetworkAnalysisPlugIn.Source");
        SINK             = i18n.get("HydrographicNetworkAnalysisPlugIn.Sink");
        CYCLE            = i18n.get("HydrographicNetworkAnalysisPlugIn.Cycle");
        UPWARD_EDGE      = i18n.get("HydrographicNetworkAnalysisPlugIn.Upward-edge");
        Z_ANOMALY        = i18n.get("HydrographicNetworkAnalysisPlugIn.z-anomaly");
        CYCLE_ANOMALY    = i18n.get("HydrographicNetworkAnalysisPlugIn.cycle-anomaly");
        NODE_ANOMALY     = i18n.get("HydrographicNetworkAnalysisPlugIn.node-anomaly");
        REVERSED_EDGES   = i18n.get("HydrographicNetworkAnalysisPlugIn.reversed-edge");

        context.getFeatureInstaller().addMainMenuPlugin(
                this, new String[]{MenuNames.PLUGINS, GRAPH},
                HYDROGRAPHIC_NETWORK_ANALYSIS + "...",
                false, null, new MultiEnableCheck()
                        .add(context.getCheckFactory().createWindowWithAssociatedTaskFrameMustBeActiveCheck())
                        .add(context.getCheckFactory().createAtLeastNLayersMustExistCheck(1)));
    }

    public boolean execute(PlugInContext context) {

        final MultiInputDialog dialog = new MultiInputDialog(
                context.getWorkbenchFrame(), HYDROGRAPHIC_NETWORK_ANALYSIS, true);
        dialog.setSideBarDescription(i18n.get("HydrographicNetworkAnalysisPlugIn.Description"));

        dialog.addLayerComboBox(LAYER,
                context.getCandidateLayer(0), null, context.getLayerManager());

        dialog.addRadioButton(DETECT, "action", detect, DETECT);
        dialog.addRadioButton(REPAIR, "action", repair, REPAIR);

        final JCheckBox jcb_use_z  = dialog.addCheckBox(USE_Z, useZ, USE_Z_TT);
        final JTextField jtf_tol_z = dialog.addDoubleField(TOL_Z, tolZ, 12, TOL_Z_TT);
        jtf_tol_z.setEnabled(jcb_use_z.isSelected());
        jcb_use_z.addActionListener(e -> jtf_tol_z.setEnabled(jcb_use_z.isSelected()));

        dialog.addCheckBox(FIND_SOURCES, findSources, FIND_SOURCES_TT);
        dialog.addCheckBox(FIND_SINKS, findSinks, FIND_SINKS_TT);
        dialog.addCheckBox(FIND_CYCLES, findCycles, FIND_CYCLES_TT);

        GUIUtil.centreOnWindow(dialog);
        dialog.setVisible(true);
        if (dialog.wasOKPressed()) {
            layer = dialog.getLayer(LAYER);
            detect      = dialog.getBoolean(DETECT);
            repair      = dialog.getBoolean(REPAIR);
            findCycles  = dialog.getBoolean(FIND_CYCLES);
            findSources = dialog.getBoolean(FIND_SOURCES);
            findSinks   = dialog.getBoolean(FIND_SINKS);
            useZ        = dialog.getBoolean(USE_Z);
            tolZ        = dialog.getDouble(TOL_Z);
            return true;
        }
        else return false;

    }

    @Override
    public void run(TaskMonitor monitor, PlugInContext context) {
        monitor.allowCancellationRequests();
        monitor.report(GRAPH_COMPUTATION + "...");
        FeatureCollection fc = layer.getFeatureCollectionWrapper();

        DirectedPseudograph<INode,FeatureAsEdge> graph =
                GraphFactory.createDirectedGraph(fc.getFeatures(), false);

        if (detect) {
            if (useZ) {
                Layer lyr = context.getLayerManager().addLayer(StandardCategoryNames.RESULT,
                        layer.getName() + "-" + Z_ANOMALY, getInversedEdges(fc));
                setInversionStyle(lyr);
            }
            if (findCycles) {
                Layer lyr = context.getLayerManager().addLayer(StandardCategoryNames.RESULT,
                        layer.getName() + "-" + CYCLE_ANOMALY, getCycles(graph));
                setCycleStyle(lyr);
            }
            if (findSources || findSinks) {
                Layer lyr = context.getLayerManager().addLayer(StandardCategoryNames.RESULT,
                        layer.getName() + "-" + NODE_ANOMALY, getSourcesAndSinks(graph));
                setNodeStyle(lyr);
            }
        }

        if (repair) {
            Set<Integer> set = new HashSet<>();
            if (useZ) {
                repairDownwardEdges(graph, set);
            }
            if (findSources) {
                repairSources(graph, set);
            }
            if (findSinks) {
                repairSinks(graph, set);
            }
            if (findCycles) {
                repairCycles(graph, set);
            }
            FeatureCollection reversedFeatures =
                    new FeatureDataset(layer.getFeatureCollectionWrapper().getFeatureSchema());

            EditTransaction transaction = new EditTransaction(new LinkedHashSet<Feature>(),
                    HYDROGRAPHIC_NETWORK_ANALYSIS, layer, true, true, context.getLayerViewPanel().getContext());
            for (Feature feature : fc.getFeatures()) {
                if (set.contains(feature.getID())) {
                    reversedFeatures.add(feature.clone(true, true));
                    transaction.modifyFeatureGeometry(feature, feature.getGeometry().reverse());
                }
            }
            Layer lyr = context.getLayerManager().addLayer(StandardCategoryNames.RESULT,
                    layer.getName() + "-" + REVERSED_EDGES, reversedFeatures);
            setReversedStyle(lyr);

            transaction.commit();
        }
    }

    private FeatureCollection getInversedEdges(FeatureCollection fc) {

        FeatureSchema anomalySchema = getAnomalySchema();
        FeatureCollection dataset = new FeatureDataset(anomalySchema);

        for (Feature feature : fc.getFeatures()) {
            Geometry geometry = feature.getGeometry();
            if (geometry.isEmpty()) continue;
            for (int i = 0 ; i < geometry.getNumGeometries() ; i++) {
                Geometry g = geometry.getGeometryN(i);
                if (g instanceof LineString) {
                    double z0 = ((LineString)g).getStartPoint().getCoordinate().z;
                    double z1 = ((LineString)g).getEndPoint().getCoordinate().z;
                    if (Double.isNaN(z0) || Double.isNaN(z1)) continue;
                    if (z0-z1 < -tolZ) {
                        Feature anomaly = new BasicFeature(anomalySchema);
                        anomaly.setGeometry(g);
                        anomaly.setAttribute("type", UPWARD_EDGE);
                        anomaly.setAttribute("comment", "[" + z0 + ";" + z1 + "]");
                        dataset.add(anomaly);
                    }
                }
            }
        }
        return dataset;
    }

    private FeatureCollection getCycles(DirectedPseudograph<INode,FeatureAsEdge> graph) {
        FeatureSchema anomalySchema = getAnomalySchema();
        FeatureCollection dataset = new FeatureDataset(anomalySchema);

        List<List<INode>> cycles = new HawickJamesSimpleCycles<>(graph).findSimpleCycles();
        for (List<INode> cycle : cycles) {
            Set<FeatureAsEdge> edgeSet = new AsSubgraph<>(
                    graph, new HashSet<>(cycle), null).edgeSet();
            for (FeatureAsEdge edge : edgeSet) {
                Feature feature = new BasicFeature(anomalySchema);
                feature.setGeometry(edge.getGeometry());
                feature.setAttribute("type", CYCLE);
                dataset.add(feature);
            }
        }
        return dataset;
    }

    private FeatureCollection getSourcesAndSinks(DirectedPseudograph<INode,FeatureAsEdge> graph) {
        FeatureSchema anomalySchema = getAnomalySchema();
        FeatureCollection dataset = new FeatureDataset(anomalySchema);

        for (INode node : graph.vertexSet()) {
            if (findSources && isSource(graph, node)) {
                Feature feature = new BasicFeature(anomalySchema);
                feature.setGeometry(node.getGeometry());
                feature.setAttribute("type", SOURCE);
                dataset.add(feature);
            } else if (findSinks && isSink(graph, node)) {
                Feature feature = new BasicFeature(anomalySchema);
                feature.setGeometry(node.getGeometry());
                feature.setAttribute("type", SINK);
                dataset.add(feature);
            }
        }
        return dataset;
    }

    private boolean isSource(Graph<INode,FeatureAsEdge> graph, INode node) {
        return graph.inDegreeOf(node) == 0 && graph.outDegreeOf(node) > 1;
    }

    private boolean isSink(Graph<INode,FeatureAsEdge> graph, INode node) {
        return graph.outDegreeOf(node) == 0 && graph.inDegreeOf(node) > 1;
    }

    private void repairDownwardEdges(DirectedPseudograph<INode,FeatureAsEdge> graph, Set<Integer> set) {
        for (FeatureAsEdge edge : new ArrayList<>(graph.edgeSet())) {
            Geometry geometry = edge.getFeature().getGeometry();
            if (!geometry.isEmpty() && geometry instanceof LineString) {
                double z1 = geometry.getCoordinates()[0].z;
                double z2 = geometry.getCoordinates()[geometry.getNumPoints()-1].z;
                if (!Double.isNaN(z1) && !Double.isNaN(z2) && (z1-z2) < -tolZ) {
                    reverseEdge(graph, edge);
                    set.add(edge.getFeature().getID());
                }
            }
        }
    }

    private void repairCycles(DirectedPseudograph<INode,FeatureAsEdge> graph, Set<Integer> set) {
        List<List<INode>> cycles = new HawickJamesSimpleCycles<>(graph).findSimpleCycles();
        for (List<INode> cycle : cycles) {
            Set<FeatureAsEdge> edgeSet =
                    new AsSubgraph<>(graph, new HashSet<>(cycle), null).edgeSet();
            double max = 0;
            FeatureAsEdge edgeToReverse = null;
            for (FeatureAsEdge edge : edgeSet) {
                double score = evaluateEdgeInversion(graph, edge);
                if (score > max) {
                    max = score;
                    edgeToReverse = edge;
                }
            }
            if (edgeToReverse != null) {
                reverseEdge(graph, edgeToReverse);
                set.add(edgeToReverse.getFeature().getID());
            }
        }
    }

    private void repairSources(DirectedPseudograph<INode,FeatureAsEdge> graph, Set<Integer> set) {
        for (INode node : graph.vertexSet()) {
            if (findSources && isSource(graph, node)) {
                BreadthFirstIterator<INode,FeatureAsEdge> it =
                        new BreadthFirstIterator<>(graph, node);
                INode stopNode = null;
                while (it.hasNext()) {
                    INode n = it.next();
                    // Reverse edges until the first node that has has at least one other incoming edge
                    // or a node that has no more outgoind edge (a sink)
                    if ((graph.incomingEdgesOf(n).size()>1 || graph.outgoingEdgesOf(n).size()==0)
                            // but only if we don't want to use Z or if Z are NaN
                            && (!useZ ||
                                Double.isNaN(node.getGeometry().getCoordinate().z) ||
                                Double.isNaN(n.getCoordinate().z) ||
                                // or if Z of the final node is higher than the the z of initial node
                                // so that reversing edges makes them go down
                                n.getCoordinate().z > (node.getGeometry().getCoordinate().z-tolZ))) {
                        stopNode = n;
                        break;
                    }
                }
                if (stopNode != null) {
                    reversePath(graph, node, stopNode, set);
                }
            }
        }
        //return new ArrayList<Integer>();
    }

    private void repairSinks(Graph<INode,FeatureAsEdge> graph, Set<Integer> set) {
        graph = new EdgeReversedGraph<>(graph);
        for (INode node : graph.vertexSet()) {
            if (findSources && isSource(graph, node)) {
                BreadthFirstIterator<INode,FeatureAsEdge> it =
                        new BreadthFirstIterator<>(graph, node);
                INode stopNode = null;
                while (it.hasNext()) {
                    INode n = it.next();
                    // Reverse edges until the first node that has has at least one other incoming edge
                    // or a node that has no more outgoind edge (a sink)
                    if ((graph.incomingEdgesOf(n).size()>1 || graph.outgoingEdgesOf(n).size()==0)
                            // but only if we don't want to use Z or if Z are NaN
                            && (!useZ ||
                            Double.isNaN(node.getGeometry().getCoordinate().z) ||
                            Double.isNaN(n.getCoordinate().z) ||
                            // or if Z of the final node is higher than the the z of initial node
                            // so that reversing edges makes them go down
                            n.getCoordinate().z < (node.getGeometry().getCoordinate().z-tolZ))) {
                        stopNode = n;
                        break;
                    }
                }
                if (stopNode != null) {
                    reversePath(graph, node, stopNode, set);
                }
            }
        }
    }

    private void reversePath(Graph<INode,FeatureAsEdge> graph, INode node1, INode node2, Set<Integer> set) {
        List<FeatureAsEdge> edges = DijkstraShortestPath.findPathBetween(graph, node1, node2).getEdgeList();
        Set<Integer> temp = new HashSet<>();
        for (FeatureAsEdge edge : edges) {
            reverseEdge(graph, edge);
            temp.add(edge.getFeature().getID());
        }
        // Before validation, check that we did not introduce cycles
        boolean hasCycle = false;
        CycleDetector<INode,FeatureAsEdge> cycleDetector = new CycleDetector<>(graph);
        if (cycleDetector.detectCyclesContainingVertex(node1)) hasCycle = true;
        else if (cycleDetector.detectCyclesContainingVertex(node2)) hasCycle = true;
        else {
            for (FeatureAsEdge edge : edges) {
                if (cycleDetector.detectCyclesContainingVertex(graph.getEdgeSource(edge))) {
                    hasCycle = true;
                    break;
                }
            }
        }
        // If a cycle has been found, we reverse the graph back to the previous situation
        if (hasCycle) {
            for (FeatureAsEdge edge : edges) {
                reverseEdge(graph, edge);
            }
            temp.clear();
        } else {
            set.addAll(temp);
        }
    }

    private void reverseEdge(Graph<INode,FeatureAsEdge> graph, FeatureAsEdge edge) {
        INode start = graph.getEdgeSource(edge);
        INode end   = graph.getEdgeTarget(edge);
        //edge.getFeature().setGeometry(edge.getGeometry().reverse());
        graph.removeEdge(edge);
        graph.addEdge(end, start, edge);
    }

    // Returns a score determining if the inversion of this edge may improve the graph
    // If the inversion of the edge creates sources or wells, return 0
    // else if, return 1, except if useZ is on
    // If useZ is on, returns 1 if z orientation is improved and 0.25 if it is degraded
    // return 0.5 if no useful z information is available
    private double evaluateEdgeInversion(DirectedPseudograph<INode,FeatureAsEdge> graph, FeatureAsEdge edge) {
        INode start = graph.getEdgeSource(edge);
        INode end   = graph.getEdgeTarget(edge);
        graph.removeEdge(edge);
        graph.addEdge(end, start, edge);
        double result;
        if (isSource(graph, start) || isSink(graph, start)) result = 0;
        else if (isSource(graph, end) || isSink(graph, end)) result = 0;
        else if (useZ) {
            double z0 = ((LineString)edge.getGeometry()).getStartPoint().getCoordinate().z;
            double z1 = ((LineString)edge.getGeometry()).getEndPoint().getCoordinate().z;
            if (!Double.isNaN(z0) && !Double.isNaN(z1)) {
                if (z0 - z1 < -tolZ) {
                    result = 1;
                } else {
                    result = 0.25;
                }
            } else {
                result = 0.5;
            }
        } else {
            result = 1;
        }
        graph.removeEdge(edge);
        graph.addEdge(start, end, edge);
        return result;
    }

    private FeatureSchema getAnomalySchema() {
        FeatureSchema schema = new FeatureSchema();
        schema.addAttribute("geometry", AttributeType.GEOMETRY);
        schema.addAttribute("type", AttributeType.STRING);
        schema.addAttribute("comment", AttributeType.STRING);
        return schema;
    }

    private void setInversionStyle(Layer layer) {
        BasicStyle style = layer.getBasicStyle();
        style.setLineColor(Color.ORANGE);
        style.setLineWidth(3);
        style.setAlpha(200);
        style.setFillColor(Color.LIGHT_GRAY);
        layer.addStyle(new ArrowLineStringSegmentStyle.Solid());
    }

    private void setCycleStyle(Layer layer) {
        layer.getBasicStyle().setFillColor(Color.LIGHT_GRAY);
        layer.getBasicStyle().setLineColor(Color.RED);
        layer.getBasicStyle().setLineWidth(3);
        layer.getBasicStyle().setAlpha(200);
    }

    private void setNodeStyle(Layer layer) {
        layer.getBasicStyle().setFillColor(Color.RED);
        layer.addStyle(new MyRingVertexStyle());
        layer.getStyle(MyRingVertexStyle.class).setEnabled(true);
    }

    private void setReversedStyle(Layer layer) {
        layer.getBasicStyle().setFillColor(Color.LIGHT_GRAY);
        layer.getBasicStyle().setLineColor(Color.BLUE);
        layer.getBasicStyle().setLineWidth(3);
        layer.getBasicStyle().setAlpha(200);
    }

    private static class MyRingVertexStyle extends RingVertexStyle {

        MyRingVertexStyle() {super();}

        public int getSize() {return 25;}

        public void paint(Feature f, Graphics2D g, Viewport viewport) throws Exception {
            if (f.getGeometry() instanceof org.locationtech.jts.geom.Point) {
                Coordinate coord = f.getGeometry().getCoordinate();
                paint(g, viewport.toViewPoint(new Point2D.Double(coord.x, coord.y)));
            }
        }

        protected void render(java.awt.Graphics2D g) {
            g.setStroke(new java.awt.BasicStroke(2.5f));
            g.setColor(Color.RED);
            g.draw(shape);
        }
    }
}
