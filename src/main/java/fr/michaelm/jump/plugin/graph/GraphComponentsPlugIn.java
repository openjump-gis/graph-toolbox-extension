/*
 * (C) 2022 Micha&euml;l Michaud
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * For more information, contact:
 *
 * michael.michaud@free.fr
 *
 */

package fr.michaelm.jump.plugin.graph;

import com.vividsolutions.jump.I18N;
import com.vividsolutions.jump.feature.*;
import com.vividsolutions.jump.task.TaskMonitor;
import com.vividsolutions.jump.workbench.model.Layer;
import com.vividsolutions.jump.workbench.model.StandardCategoryNames;
import com.vividsolutions.jump.workbench.plugin.*;
import com.vividsolutions.jump.workbench.ui.GUIUtil;
import com.vividsolutions.jump.workbench.ui.MenuNames;
import com.vividsolutions.jump.workbench.ui.MultiInputDialog;
import fr.michaelm.jump.feature.jgrapht.*;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.Pseudograph;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;


/**
 * Find main components of each graph or subgraph (number of connected
 * subgraphs, length, number of order 1 nodes...).
 * @author Micha&euml;l Michaud
 * @version 0.1.2 (2011-07-16)
 */
//version 0.1.2 (2011-07-16) typos and comments
//version 0.1.1 (2010-04-22) first svn version
//version 0.1 (2010-04-22)
public class GraphComponentsPlugIn extends ThreadedBasePlugIn {

    private final I18N i18n = I18N.getInstance("fr.michaelm.jump.plugin.graph");

    private static final String P_LAYER                 = "Layer";
    private static final String P_ATTRIBUTE             = "Attribute";
    private static final String P_ATTRIBUTE_TYPE        = "AttributeType";
    private static final String P_GRAPH_3D              = "Graph3D";
    private static final String P_IGNORE_EMPTY          = "IgnoreEmpty";
    private static final String P_RETURNED_GEOM_TYPE    = "ReturnedGeometryType";

    {
        addParameter(P_LAYER,       null);
        addParameter(P_ATTRIBUTE,    null);
        addParameter(P_ATTRIBUTE_TYPE,    "STRING");
        addParameter(P_GRAPH_3D,    false);
        addParameter(P_IGNORE_EMPTY, false);
        addParameter(P_RETURNED_GEOM_TYPE, "POINT"); // POINT, MULTILINESTRING,
                                                          // OR SIMPLIFIED_MULTILINESTRING
    }


    String GRAPH;
    String CONNECTED_COMPONENTS;
    String LAYER;
    String USE_ATTRIBUTE;
    String USE_ATTRIBUTE_TOOLTIP;
    String ATTRIBUTE;
    String IGNORE_EMPTY;
    String IGNORE_EMPTY_TOOLTIP;
    String DIM3;
    String DIM3_TOOLTIP;
    String RETURN_GRAPHS_AS;
    String RETURN_GRAPHS_AS_TOOLTIP;
    String POINT, MULTILINESTRING, SIMPLIFIED_MULTILINESTRING;
    String GRAPH_ANALYSIS;
    String CONNECTED_SUBGRAPH;
    String CONNECTED_SUBGRAPHS;
    String FEATURES;
    String PENDANT_VERTICES;
    String LENGTH;
    String PROCESSED_GRAPHS;
    String GRAPHS;
    String SUBGRAPHS;
    String NO_GRAPH;

    private static final GeometryFactory DEFAULT_GEOMETRY_FACTORY = new GeometryFactory();

    public String getName() {return "Graph components PlugIn";}

    @Override
    public void initialize(final PlugInContext context) {
        
        GRAPH                      = i18n.get("Graph");
        CONNECTED_COMPONENTS       = i18n.get("GraphComponentsPlugIn.connected-components");
        LAYER                      = i18n.get("Layer");
        USE_ATTRIBUTE              = i18n.get("use-attribute");
        USE_ATTRIBUTE_TOOLTIP      = i18n.get("use-attribute-tooltip");
        ATTRIBUTE                  = i18n.get("Attribute");
        IGNORE_EMPTY               = i18n.get("ignore-empty");
        IGNORE_EMPTY_TOOLTIP       = i18n.get("ignore-empty-tooltip");
        DIM3                       = i18n.get("dim3");
        DIM3_TOOLTIP               = i18n.get("dim3-tooltip");
        RETURN_GRAPHS_AS           = i18n.get("GraphComponentsPlugIn.return-graphs-as");
        RETURN_GRAPHS_AS_TOOLTIP   = i18n.get("GraphComponentsPlugIn.return-graphs-as-tooltip");
        POINT                      = i18n.get("GraphComponentsPlugIn.point");
        MULTILINESTRING            = i18n.get("GraphComponentsPlugIn.multilinestring");
        SIMPLIFIED_MULTILINESTRING = i18n.get("GraphComponentsPlugIn.simplified-multilinestring");
        GRAPH_ANALYSIS             = i18n.get("GraphComponentsPlugIn.graph-analysis");
        CONNECTED_SUBGRAPH         = i18n.get("GraphComponentsPlugIn.connected-subgraph");
        CONNECTED_SUBGRAPHS        = i18n.get("GraphComponentsPlugIn.connected-subgraphs");
        FEATURES                   = i18n.get("Features");
        PENDANT_VERTICES           = i18n.get("GraphComponentsPlugIn.pendant-vertices");
        LENGTH                     = i18n.get("GraphComponentsPlugIn.longueur");
        PROCESSED_GRAPHS           = i18n.get("GraphComponentsPlugIn.processed-graphs");
        GRAPHS                     = i18n.get("GraphComponentsPlugIn.graphs");
        SUBGRAPHS                  = i18n.get("GraphComponentsPlugIn.subgraphs");
        NO_GRAPH                   = i18n.get("GraphComponentsPlugIn.no-graph");
        
        context.getFeatureInstaller().addMainMenuPlugin(
          this, new String[]{MenuNames.PLUGINS, GRAPH}, CONNECTED_COMPONENTS + "...",
          false, null, new MultiEnableCheck()
          .add(context.getCheckFactory().createTaskWindowMustBeActiveCheck())
          .add(context.getCheckFactory().createAtLeastNLayersMustExistCheck(1)));
    }

    @Override
    //@SuppressWarnings("unchecked")
    public boolean execute(PlugInContext context) {
        
        final MultiInputDialog dialog = new MultiInputDialog(
        context.getWorkbenchFrame(), GRAPH_ANALYSIS, true);

        boolean use_attribute = (getStringParam(P_ATTRIBUTE) != null);
        boolean ignore_empty = getBooleanParam(P_IGNORE_EMPTY);
        boolean dim3 = getBooleanParam(P_GRAPH_3D);
        
        final JComboBox<Layer> jcb_layer = dialog.addLayerComboBox(
            LAYER, context.getCandidateLayer(0), null, context.getLayerManager());
        
        final JCheckBox jcb_use_attribute = dialog.addCheckBox(USE_ATTRIBUTE, use_attribute, USE_ATTRIBUTE_TOOLTIP);
        
        List<String> list = getFieldsFromLayerWithoutGeometry(context.getCandidateLayer(0));
        String val = list.size()>0?list.iterator().next():null;
        final JComboBox<String> jcb_attribute = dialog.addComboBox(ATTRIBUTE, val, list, USE_ATTRIBUTE_TOOLTIP);
        jcb_attribute.setEnabled(false);
        
        final JCheckBox jcb_ignore_empty = dialog.addCheckBox(IGNORE_EMPTY, ignore_empty, IGNORE_EMPTY_TOOLTIP);
        jcb_ignore_empty.setEnabled(false);
        
        dialog.addSeparator();
        
        final JCheckBox jcb_3d = dialog.addCheckBox(DIM3, dim3, DIM3_TOOLTIP);
        jcb_3d.setEnabled(true);
        
        dialog.addComboBox(
            RETURN_GRAPHS_AS,
            //return_graphs_as,
            getStringParam(P_RETURNED_GEOM_TYPE),
            Arrays.asList(POINT, MULTILINESTRING, SIMPLIFIED_MULTILINESTRING), RETURN_GRAPHS_AS_TOOLTIP);
        
        dialog.addSeparator();
        
        jcb_layer.addActionListener(e -> {
            List<String> list1 = getFieldsFromLayerWithoutGeometry(dialog.getLayer(LAYER));
            if (list1.size() == 0) {
                jcb_attribute.setModel(new DefaultComboBoxModel<>(new String[0]));
                jcb_use_attribute.setEnabled(false);
                jcb_attribute.setEnabled(false);
                jcb_ignore_empty.setEnabled(false);
            }
            else {
                jcb_attribute.setModel(new DefaultComboBoxModel<>(list1.toArray(new String[0])));
            }
        });
        
        jcb_use_attribute.addActionListener(e -> {
            boolean _use_attribute = dialog.getBoolean(USE_ATTRIBUTE);
            jcb_attribute.setEnabled(_use_attribute);
            jcb_ignore_empty.setEnabled(_use_attribute);
        });
        
        GUIUtil.centreOnWindow(dialog);
        dialog.setVisible(true);
        if (dialog.wasOKPressed()) {
            Layer layer = dialog.getLayer(LAYER);
            use_attribute = dialog.getBoolean(USE_ATTRIBUTE);
            String attribute = use_attribute ?
                    dialog.getText(ATTRIBUTE) : null;
            AttributeType attType = use_attribute ?
                    layer.getFeatureCollectionWrapper()
                            .getFeatureSchema().getAttributeType(attribute)
                    :AttributeType.STRING;
            ignore_empty = dialog.getBoolean(IGNORE_EMPTY);
            dim3    = dialog.getBoolean(DIM3);
            String return_graphs_as = dialog.getText(RETURN_GRAPHS_AS);

            addParameter(P_LAYER,       layer.getName());
            addParameter(P_ATTRIBUTE,    attribute);
            addParameter(P_ATTRIBUTE_TYPE,    attType.toString());
            addParameter(P_GRAPH_3D,    dim3);
            addParameter(P_IGNORE_EMPTY, ignore_empty);
            addParameter(P_RETURNED_GEOM_TYPE, return_graphs_as);

            return true;
        }
        else return false;
        
    }

    @Override
    public void run(TaskMonitor monitor, PlugInContext context) {
        monitor.allowCancellationRequests();
        monitor.report(GRAPH_ANALYSIS + "...");
        
        FeatureSchema schema_graphs = new FeatureSchema();
        schema_graphs.addAttribute("GEOMETRY", AttributeType.GEOMETRY);

        Layer layer = context.getLayerManager().getLayer(getStringParam(P_LAYER));
        boolean use_attribute = (getStringParam(P_ATTRIBUTE) != null);
        String attribute = getStringParam(P_ATTRIBUTE);
        AttributeType attType = AttributeType.toAttributeType(getStringParam(P_ATTRIBUTE_TYPE));
        boolean ignore_empty = getBooleanParam(P_IGNORE_EMPTY);
        boolean dim3 = getBooleanParam(P_GRAPH_3D);
        String returnedType = getStringParam(P_RETURNED_GEOM_TYPE);

        if (use_attribute) schema_graphs.addAttribute(attribute, attType);
        schema_graphs.addAttribute(CONNECTED_SUBGRAPHS, AttributeType.INTEGER);
        schema_graphs.addAttribute(FEATURES, AttributeType.INTEGER);
        schema_graphs.addAttribute(PENDANT_VERTICES, AttributeType.INTEGER);
        schema_graphs.addAttribute(LENGTH, AttributeType.DOUBLE);
        FeatureCollection graphsFC = new FeatureDataset(schema_graphs);
        
        FeatureSchema schema_subgraphs = new FeatureSchema();
        schema_subgraphs.addAttribute("GEOMETRY", AttributeType.GEOMETRY);
        if (use_attribute) schema_subgraphs.addAttribute(attribute, attType);
        schema_subgraphs.addAttribute(CONNECTED_SUBGRAPH, AttributeType.STRING);
        schema_subgraphs.addAttribute(FEATURES, AttributeType.INTEGER);
        schema_subgraphs.addAttribute(PENDANT_VERTICES, AttributeType.INTEGER);
        schema_subgraphs.addAttribute(LENGTH, AttributeType.DOUBLE);
        FeatureCollection subgraphsFC = new FeatureDataset(schema_subgraphs);
        
        // Order features by attribute value in a map
        Map<Object,List<Feature>> map = new HashMap<>();
        Object key = "NO_ATTRIBUTE_USED";
        for (Feature f : layer.getFeatureCollectionWrapper().getFeatures()) {
            if (use_attribute) key = f.getAttribute(attribute);
            if (use_attribute && ignore_empty &&
                (key == null || key.toString().trim().length() == 0)) {continue;}
            else if (!map.containsKey(key)) {
                map.put(key, new ArrayList<>());
            }
            map.get(key).add(f);
        }
        
        int count = 1;
        // Loop through all graphs to analyze
        for (Object k : map.keySet()) {
            // Creates a undirected graph from the feature list
            Pseudograph<INode,FeatureAsEdge> graph =
                GraphFactory.createUndirectedGraph(map.get(k), dim3);

            //List of connected components
            List<Set<INode>> list = GraphUtil.createConnectedNodeSets(map.get(k), false, dim3);

            double graph_length = 0.0;
            //int connected_component_number = list.size();
            int total_feature_number = 0;
            //int graph_node1_number = 0;
            List<Geometry> graph_geometries = new ArrayList<>();

            for (int j = 0 ; j < list.size() ; j++) {
                Graph<INode,FeatureAsEdge> sg = new AsSubgraph<>(graph, list.get(j));
                Set<FeatureAsEdge> edges = sg.edgeSet();
                
                double subgraph_length = 0.0;
                int feature_number = edges.size();
                //int subgraph_node1_number = countOrder1Nodes(graph, list.get(j));
                List<Geometry> subgraph_geometries = new ArrayList<>();
                for (Feature feature : edges) {
                    Geometry g = feature.getGeometry();
                    double length = g.getLength();
                    subgraph_length += length;
                    if (returnedType.equals(POINT)) {
                        subgraph_geometries.add(g);
                    }
                    else if (returnedType.equals(MULTILINESTRING)) {
                        subgraph_geometries.add(g);
                    }
                    else if (returnedType.equals(SIMPLIFIED_MULTILINESTRING)) {
                        subgraph_geometries.add(DEFAULT_GEOMETRY_FACTORY
                                .createLineString(new Coordinate[]{
                                g.getCoordinates()[0],
                                g.getCoordinates()[g.getCoordinates().length-1]}));
                    }
                }
                graph_length += subgraph_length;
                graph_geometries.addAll(subgraph_geometries);
                total_feature_number += feature_number;
                
                Feature newf = new BasicFeature(schema_subgraphs);
                if (returnedType.equals(POINT)) {
                    newf.setGeometry(DEFAULT_GEOMETRY_FACTORY
                            .buildGeometry(subgraph_geometries).getInteriorPoint());
                }
                else {
                    newf.setGeometry(DEFAULT_GEOMETRY_FACTORY
                            .buildGeometry(subgraph_geometries));
                }
                if (use_attribute) newf.setAttribute(attribute, key);
                newf.setAttribute(CONNECTED_SUBGRAPH, ""+(j+1)+"/"+list.size());
                newf.setAttribute(FEATURES, edges.size());
                newf.setAttribute(PENDANT_VERTICES, countOrder1Nodes(graph, list.get(j)));
                newf.setAttribute(LENGTH, subgraph_length);
                subgraphsFC.add(newf);
            }
            
            Feature newf = new BasicFeature(schema_graphs);
            if (returnedType.equals(POINT)) {
                newf.setGeometry(DEFAULT_GEOMETRY_FACTORY
                        .buildGeometry(graph_geometries).getInteriorPoint());
            }
            else {
                newf.setGeometry(DEFAULT_GEOMETRY_FACTORY
                        .buildGeometry(graph_geometries));
            }
            if (use_attribute) newf.setAttribute(attribute, key);
            newf.setAttribute(CONNECTED_SUBGRAPHS, list.size());
            newf.setAttribute(FEATURES, total_feature_number);
            newf.setAttribute(PENDANT_VERTICES, countOrder1Nodes(graph, graph.vertexSet()));
            newf.setAttribute(LENGTH, graph_length);
            graphsFC.add(newf);
            
            count++;
            monitor.report(count, map.size(), PROCESSED_GRAPHS);
        }
        
        context.getLayerManager().addCategory(StandardCategoryNames.RESULT);
        if (graphsFC.size()>0) {
            context.addLayer(StandardCategoryNames.RESULT, layer.getName()+"-"+GRAPHS, graphsFC);
            context.addLayer(StandardCategoryNames.RESULT, layer.getName()+"-"+SUBGRAPHS, subgraphsFC);
        }
        else {
            context.getWorkbenchFrame().warnUser(NO_GRAPH);
        }
    }
    
    private int countOrder1Nodes(Pseudograph<INode,FeatureAsEdge> graph, Set<INode> nodes) {
        int nb = 0;
        for (INode node : nodes) {
            if (graph.degreeOf(node) == 1) nb++;
        }
        return nb;
    }
    
    private List<String> getFieldsFromLayerWithoutGeometry(Layer l) {
        List<String> fields = new ArrayList<>();
        FeatureSchema schema = l.getFeatureCollectionWrapper().getFeatureSchema();
        for (int i = 0 ; i < schema.getAttributeCount() ; i++) {
            if (schema.getAttributeType(i) != AttributeType.GEOMETRY) {
                fields.add(schema.getAttributeName(i));  
           }
        }
        return fields;
    }

}
