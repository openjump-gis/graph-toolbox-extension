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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;

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
import org.jgrapht.graph.DirectedWeightedPseudograph;

/**
 * Creates a graph from a linear layer with JGraphT and returns degree 1 nodes
 * (network dead-end), degree 2 nodes, degree 3+ nodes (intersection) or all
 * the nodes with their degree as attribute.
 * @author Micha&euml;l Michaud
 * @version 0.5.0 (2018-06-17)
 */
//version 0.5.0 (2018-06-17) use parameters to ease usage in beanshell
//version 0.4.0 (2017-01-17) directedGraph support
//version 0.1.2 (2011-07-16) typos and comments
//version 0.1.1 (2010-04-22) first svn version
//version 0.1 (2010-04-22)
public class GraphNodesPlugIn extends ThreadedBasePlugIn {

    private final I18N i18n = I18N.getInstance("fr.michaelm.jump.plugin.graph");

    private static final String P_LAYER       = "Layer";

    private static final String P_ATTRIBUTE   = "Attribute";
    private static final String P_IGNORE_NULL = "IgnoreNull";

    private static final String P_GRAPH_3D    = "Graph3D";

    private static final String P_DEGREE_0     = "Degree0";
    private static final String P_DEGREE_1     = "Degree1";
    private static final String P_DEGREE_2     = "Degree2";
    private static final String P_DEGREE_3P    = "Degree3P";
    private static final String P_IN_DEGREE_0  = "InDegree0";
    private static final String P_OUT_DEGREE_0 = "OutDegree0";

    private static String LAYER;

    private static String GRAPH;
    private static String NODES;
    private static String DEGREE;
    private static String IN_DEGREE;
    private static String OUT_DEGREE;
    private static String GRAPH_NODES;
    private static String GRAPH_COMPUTATION;
    
    private static String USE_ATTRIBUTE;
    private static String USE_ATTRIBUTE_TOOLTIP;
    
    private static String ATTRIBUTE;
    
    private static String IGNORE_EMPTY;
    private static String IGNORE_EMPTY_TOOLTIP;
    
    private static String DIM3;
    private static String DIM3_TOOLTIP;

    private static String DEGREE0;
    private static String DEGREE0_TOOLTIP;
    private static String DEGREE1;
    private static String DEGREE1_TOOLTIP;
    private static String DEGREE2;
    private static String DEGREE2_TOOLTIP;
    private static String DEGREE3P;
    private static String DEGREE3P_TOOLTIP;
    private static String DIRECTED_AND;
    private static String IN_DEGREE0;
    private static String OUT_DEGREE0;

    private static String NO_NODE_FOUND;
    
    private Layer layer;
    private String attribute;
    private boolean use_attribute;
    private boolean ignore_empty;
    private boolean dim3;
    private boolean indegree0  = false;
    private boolean outdegree0 = false;
    private boolean degree0    = false;
    private boolean degree1    = true;
    private boolean degree2    = false;
    private boolean degree3p   = false;

    {
        addParameter(P_LAYER,       null);
        addParameter(P_ATTRIBUTE,   null);
        addParameter(P_IGNORE_NULL, false);
        addParameter(P_GRAPH_3D,    false);
        addParameter(P_DEGREE_0,    false);
        addParameter(P_DEGREE_1,    true);
        addParameter(P_DEGREE_2,    false);
        addParameter(P_DEGREE_3P,   false);
        addParameter(P_IN_DEGREE_0, false);
        addParameter(P_OUT_DEGREE_0,false);
    }

    public String getName() {return "Graph nodes PlugIn";}

    @Override
    public void initialize(final PlugInContext context) {

        LAYER                 = i18n.get("Layer");
        GRAPH                 = i18n.get("Graph");
        NODES                 = i18n.get("Nodes");
        DEGREE                = i18n.get("Degree");
        IN_DEGREE             = i18n.get("InDegree");
        OUT_DEGREE            = i18n.get("OutDegree");
        GRAPH_COMPUTATION     = i18n.get("Graph-computation");
        GRAPH_NODES           = i18n.get("GraphNodesPlugIn.graph-nodes");
        USE_ATTRIBUTE         = i18n.get("use-attribute");
        USE_ATTRIBUTE_TOOLTIP = i18n.get("use-attribute-tooltip");
        ATTRIBUTE             = i18n.get("Attribute");
        IGNORE_EMPTY          = i18n.get("ignore-empty");
        IGNORE_EMPTY_TOOLTIP  = i18n.get("ignore-empty-tooltip");
        DIM3                  = i18n.get("dim3");
        DIM3_TOOLTIP          = i18n.get("dim3-tooltip");
        DEGREE0               = i18n.get("GraphNodesPlugIn.degree0");
        DEGREE0_TOOLTIP       = i18n.get("GraphNodesPlugIn.degree0-tooltip");
        DEGREE1               = i18n.get("GraphNodesPlugIn.degree1");
        DEGREE1_TOOLTIP       = i18n.get("GraphNodesPlugIn.degree1-tooltip");
        DEGREE2               = i18n.get("GraphNodesPlugIn.degree2");
        DEGREE2_TOOLTIP       = i18n.get("GraphNodesPlugIn.degree2-tooltip");
        DEGREE3P              = i18n.get("GraphNodesPlugIn.degree3p");
        DEGREE3P_TOOLTIP      = i18n.get("GraphNodesPlugIn.degree3p-tooltip");
        DIRECTED_AND          = i18n.get("GraphNodesPlugIn.directed-and");
        IN_DEGREE0            = i18n.get("GraphNodesPlugIn.in-degree0");
        OUT_DEGREE0           = i18n.get("GraphNodesPlugIn.out-degree0");
        NO_NODE_FOUND         = i18n.get("GraphNodesPlugIn.no-node-found");
        
        context.getFeatureInstaller().addMainMenuPlugin(
          this, new String[]{MenuNames.PLUGINS, GRAPH},
          GRAPH_NODES + "...",
          false, null, new MultiEnableCheck()
          .add(context.getCheckFactory().createTaskWindowMustBeActiveCheck())
          .add(context.getCheckFactory().createAtLeastNLayersMustExistCheck(1)));
    }

    @Override
    //@SuppressWarnings("unchecked")
    public boolean execute(PlugInContext context) {
        
        final MultiInputDialog dialog = new MultiInputDialog(
        context.getWorkbenchFrame(), GRAPH_NODES, true);
        
        final JComboBox<Layer> jcb_layer = dialog.addLayerComboBox(
            LAYER, context.getCandidateLayer(0), null, context.getLayerManager());
        
        final JCheckBox jcb_use_attribute = dialog.addCheckBox(USE_ATTRIBUTE, false, USE_ATTRIBUTE_TOOLTIP);
        
        List<String> list = getFieldsFromLayerWithoutGeometry(context.getCandidateLayer(0));
        String val = list.size()>0?list.iterator().next():null;
        final JComboBox<String> jcb_attribute = dialog.addComboBox(ATTRIBUTE, val, list, USE_ATTRIBUTE_TOOLTIP);
        jcb_attribute.setEnabled(false);
        
        final JCheckBox jcb_ignore_empty = dialog.addCheckBox(IGNORE_EMPTY, false, IGNORE_EMPTY_TOOLTIP);
        jcb_ignore_empty.setEnabled(false);
        
        dialog.addSeparator();

        dialog.addCheckBox(DIM3, dim3, DIM3_TOOLTIP);

        dialog.addSeparator();

        dialog.addCheckBox(DEGREE0,      degree0,    DEGREE0_TOOLTIP);
        dialog.addCheckBox(DEGREE1,      degree1,    DEGREE1_TOOLTIP);
        dialog.addCheckBox(DEGREE2,      degree2,    DEGREE2_TOOLTIP);
        dialog.addCheckBox(DEGREE3P,     degree3p,   DEGREE3P_TOOLTIP);

        dialog.addSubTitle(DIRECTED_AND);

        dialog.addCheckBox(IN_DEGREE0,   indegree0,  IN_DEGREE0);
        dialog.addCheckBox(OUT_DEGREE0,  outdegree0, OUT_DEGREE0);
        
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
            use_attribute = dialog.getBoolean(USE_ATTRIBUTE);
            jcb_attribute.setEnabled(use_attribute);
            jcb_ignore_empty.setEnabled(use_attribute);
        });
        
        GUIUtil.centreOnWindow(dialog);
        dialog.setVisible(true);
        if (dialog.wasOKPressed()) {
            layer = dialog.getLayer(LAYER);
            use_attribute = dialog.getBoolean(USE_ATTRIBUTE);
            attribute = dialog.getText(ATTRIBUTE);
            ignore_empty = dialog.getBoolean(IGNORE_EMPTY);
            dim3       = dialog.getBoolean(DIM3);
            indegree0  = dialog.getBoolean(IN_DEGREE0);
            outdegree0 = dialog.getBoolean(OUT_DEGREE0);
            degree0    = dialog.getBoolean(DEGREE0);
            degree1    = dialog.getBoolean(DEGREE1);
            degree2    = dialog.getBoolean(DEGREE2);
            degree3p   = dialog.getBoolean(DEGREE3P);

            addParameter(P_LAYER,       layer.getName());
            addParameter(P_ATTRIBUTE,   (use_attribute ? attribute : null));
            addParameter(P_IGNORE_NULL, ignore_empty);
            addParameter(P_GRAPH_3D,    dim3);
            addParameter(P_DEGREE_0,    degree0);
            addParameter(P_DEGREE_1,    degree1);
            addParameter(P_DEGREE_2,    degree2);
            addParameter(P_DEGREE_3P,   degree3p);
            addParameter(P_IN_DEGREE_0, indegree0);
            addParameter(P_OUT_DEGREE_0,outdegree0);

            return true;
        }
        else return false;
        
    }
    
    public void run(TaskMonitor monitor, PlugInContext context) throws Exception {
        monitor.allowCancellationRequests();
        monitor.report(GRAPH_COMPUTATION + "...");

        if (getStringParam(P_LAYER) == null) throw new Exception("Layer parameter is undefined");
        Layer layer = context.getLayerManager().getLayer(getStringParam(P_LAYER));
        if (layer == null) throw new Exception("Layer " + getStringParam(P_LAYER) + " has not been found");
        FeatureCollection fc = layer.getFeatureCollectionWrapper();
        
        // Creates the schema for the output dataset (nodes)
        FeatureSchema schemaNodes = new FeatureSchema();
        schemaNodes.addAttribute("GEOMETRY", AttributeType.GEOMETRY);
        String attribute = getStringParam(P_ATTRIBUTE);
        if (attribute != null && !fc.getFeatureSchema().hasAttribute(attribute)) {
            throw new Exception("Layer " + layer.getName() + " has no attribute named " + attribute);
        }
        if (attribute != null) {
            schemaNodes.addAttribute(attribute, fc.getFeatureSchema().getAttributeType(attribute));
        }
        schemaNodes.addAttribute(IN_DEGREE, AttributeType.INTEGER);
        schemaNodes.addAttribute(OUT_DEGREE, AttributeType.INTEGER);
        schemaNodes.addAttribute(DEGREE, AttributeType.INTEGER);

        FeatureCollection resultNodes = new FeatureDataset(schemaNodes);        
        
        // Order features by attribute value in a map
        Map<Object,List<Feature>> map = new HashMap<>();
        Object key = "NO_ATTRIBUTE_USED";
        for (Feature f : fc.getFeatures()) {
            if (attribute != null) key = f.getAttribute(attribute);
            if (attribute != null && getBooleanParam(P_IGNORE_NULL) &&
                (key == null || key.toString().trim().length() == 0)) {continue;}
            else if (!map.containsKey(key)) {map.put(key, new ArrayList<>());}
            map.get(key).add(f);
        }
        
        //int count = 1;
        degree0    = getBooleanParam(P_DEGREE_0);
        degree1    = getBooleanParam(P_DEGREE_1);
        degree2    = getBooleanParam(P_DEGREE_2);
        degree3p   = getBooleanParam(P_DEGREE_3P);
        indegree0  = getBooleanParam(P_IN_DEGREE_0);
        outdegree0 = getBooleanParam(P_OUT_DEGREE_0);
        for (Object k : map.keySet()) {
            monitor.report(GRAPH_COMPUTATION + " (" + k + ")");
            DirectedWeightedPseudograph<INode,FeatureAsEdge> graph =
                    GraphFactory.createDirectedGraph(map.get(k), dim3);

            for (INode node : graph.vertexSet()) {
                int indegree =  graph.inDegreeOf(node);
                int outdegree = graph.outDegreeOf(node);
                int degree = indegree + outdegree;
                if (degree0 && degree == 0 ||
                        degree1 && degree == 1 ||
                        degree2 && degree == 2 ||
                        degree3p && degree > 2) {
                    if (indegree0 && indegree != 0 && !outdegree0) continue;
                    if (outdegree0 && outdegree != 0 && !indegree0) continue;
                    // indegree0 && outdegree0 checked means one OR the other
                    // -> continue if neither indegree nor outdegree are 0
                    if (indegree0 && outdegree0 && indegree != 0 && outdegree !=0) continue;
                    //if (outdegree0 && outdegree != 0) continue;
                    Feature bf = new BasicFeature(schemaNodes);
                    bf.setGeometry(node.getGeometry());
                    if (use_attribute) bf.setAttribute(attribute, k);
                    bf.setAttribute(IN_DEGREE, indegree);
                    bf.setAttribute(OUT_DEGREE, outdegree);
                    bf.setAttribute(DEGREE, degree);
                    resultNodes.add(bf);
                }
            }
        }
        context.getLayerManager().addCategory(StandardCategoryNames.RESULT);
        if (resultNodes.size()>0) {
            context.addLayer(StandardCategoryNames.RESULT, layer.getName()+"-" + NODES, resultNodes);
        } else {
            context.getWorkbenchFrame().warnUser(NO_NODE_FOUND);
        }
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
