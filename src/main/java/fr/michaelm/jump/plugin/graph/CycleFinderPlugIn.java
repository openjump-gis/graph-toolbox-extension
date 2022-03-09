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
import fr.michaelm.jump.feature.jgrapht.FeatureAsEdge;
import fr.michaelm.jump.feature.jgrapht.GraphFactory;
import fr.michaelm.jump.feature.jgrapht.INode;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.Pseudograph;
import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.operation.polygonize.Polygonizer;

import java.util.*;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;


/**
 * Find cycles in a network graph.
 * Result is not defined if the Line network is not a Planar Graph
 * This Class uses both JTS Polygonizer to find the faces of the Planar Linework
 * and JGraphT to analyze the graph around theses faces
 * @author Micha&euml;l Michaud
 * @version 0.1.2 (2011-07-16)
 */
//version 0.1.2 (2011-07-16) typo and comments
//version 0.1.1 (2010-04-22) first svn version
//version 0.1 (2008-02-02)
public class CycleFinderPlugIn extends ThreadedBasePlugIn {

    private final I18N i18n = I18N.getInstance("fr.michaelm.jump.plugin.graph");

    static final String P_DATASET_NAME  = "DatasetName";
    static final String P_DATASET       = "Dataset";
    static final String P_GRAPH_3D      = "Graph3D";

    static final String P_MIN_FEATURES  = "MinFeatures";
    static final String P_MAX_FEATURE   = "MaxFeatures";
    static final String P_MAX_LENGTH    = "MaxLength";

    // FilterCycleDegree (integer)
    // <= 0 : all homogeneous cycles
    // 1 (2 ^ 0) = cycles with degree 0 (no incident edge : isolated cycle)
    // 2 (2 ^ 1) = cycles with degree 1 (1 incident edge : end cycle)
    // 4 (2 ^ 2) = cycles with degree 2 (2 incident edges : junction cycle)
    // 8 (2 ^ 3) = cycles with degree 3 or more (3+ incident edges : forking cycle)
    // For values > 0, values can be combined. Example :
    // 10 = cycles of degree 1 or 3+
    static final String P_DEGREE_FILTER = "FilterCycleDegree";

    static final String P_ATTRIBUTE     = "Attribute";
    static final String P_IGNORE_EMPTY  = "IgnoreEmpty";
    static final String P_FIND_ALL_HETEROGENEOUS = "FindAllHeterogeneous";
    static final String P_FIND_AAB_ABB  = "FindAAB_ABB";

    {
        addParameter(P_DATASET_NAME,null);
        addParameter(P_DATASET,null);
        addParameter(P_GRAPH_3D,false);
        addParameter(P_MIN_FEATURES,1);
        addParameter(P_MAX_FEATURE,12);
        addParameter(P_MAX_LENGTH,      Double.MAX_VALUE);
        addParameter(P_DEGREE_FILTER,0);
        addParameter(P_ATTRIBUTE,null);
        addParameter(P_IGNORE_EMPTY,false);
        addParameter(P_FIND_ALL_HETEROGENEOUS,false);
        addParameter(P_FIND_AAB_ABB,false);
    }


    public String getName() {return "Cycle Finder PlugIn";}

    @Override
    public void initialize(final PlugInContext context) {
        
        final String GRAPH          = i18n.get("Graph");
        final String CYCLE_FINDING  = i18n.get("CycleFinderPlugIn.cycle-finding");
        
        context.getFeatureInstaller().addMainMenuPlugin(
          this, new String[]{MenuNames.PLUGINS, GRAPH}, CYCLE_FINDING + "...",
          false, null, new MultiEnableCheck()
          .add(context.getCheckFactory().createTaskWindowMustBeActiveCheck())
          .add(context.getCheckFactory().createAtLeastNLayersMustExistCheck(1)));
    }

    //@SuppressWarnings("unchecked")
    public boolean execute(PlugInContext context) {
        
        final String CYCLE_FINDING                    = i18n.get("CycleFinderPlugIn.cycle-finding");
        final String LAYER                            = i18n.get("Layer");
        final String USE_ATTRIBUTE                    = i18n.get("CycleFinderPlugIn.use-attribute");
        final String USE_ATTRIBUTE_TOOLTIP            = i18n.get("CycleFinderPlugIn.use-attribute-tooltip");
        final String ATTRIBUTE                        = i18n.get("Attribute");
        final String IGNORE_EMPTY                     = i18n.get("ignore-empty");
        final String IGNORE_EMPTY_TOOLTIP             = i18n.get("ignore-empty-tooltip");
        final String DIM3                             = i18n.get("dim3");
        final String DIM3_TOOLTIP                     = i18n.get("dim3-tooltip");
        
        final String MIN_FEATURES                     = i18n.get("CycleFinderPlugIn.min-features");
        final String MIN_FEATURES_TOOLTIP             = i18n.get("CycleFinderPlugIn.min-features-tooltip");
        final String MAX_FEATURES                     = i18n.get("CycleFinderPlugIn.max-features");
        final String MAX_FEATURES_TOOLTIP             = i18n.get("CycleFinderPlugIn.max-features-tooltip");
        final String MAX_LENGTH                       = i18n.get("CycleFinderPlugIn.max-length");
        final String MAX_LENGTH_TOOLTIP               = i18n.get("CycleFinderPlugIn.max-length-tooltip");
        
        final String ALL_HOMOGENEOUS_CYCLES           = i18n.get("CycleFinderPlugIn.all-homogeneous-cycles");
        final String ALL_HOMOGENEOUS_CYCLES_TOOLTIP   = i18n.get("CycleFinderPlugIn.all-homogeneous-cycles-tooltip");
        final String ISOLATED_CYCLES                  = i18n.get("CycleFinderPlugIn.isolated-cycles");
        final String ISOLATED_CYCLES_TOOLTIP          = i18n.get("CycleFinderPlugIn.isolated-cycles-tooltip");
        final String PENDANT_CYCLES                   = i18n.get("CycleFinderPlugIn.pendant-cycles");
        final String PENDANT_CYCLES_TOOLTIP           = i18n.get("CycleFinderPlugIn.pendant-cycles-tooltip");
        final String JUNCTION_CYCLES                  = i18n.get("CycleFinderPlugIn.junction-cycles");
        final String JUNCTION_CYCLES_TOOLTIP          = i18n.get("CycleFinderPlugIn.junction-cycles-tooltip");
        final String FORK_CYCLES                      = i18n.get("CycleFinderPlugIn.fork-cycles");
        final String FORK_CYCLES_TOOLTIP              = i18n.get("CycleFinderPlugIn.fork-cycles-tooltip");
        
        final String ALL_HETEROGENEOUS_CYCLES         = i18n.get("CycleFinderPlugIn.all-heterogeneous-cycles");
        final String ALL_HETEROGENEOUS_CYCLES_TOOLTIP = i18n.get("CycleFinderPlugIn.all-heterogeneous-cycles-tooltip");
        final String AAB_ABB_CYCLES_ONLY              = i18n.get("CycleFinderPlugIn.aab-abb-cycles-only");
        final String AAB_ABB_CYCLES_ONLY_TOOLTIP      = i18n.get("CycleFinderPlugIn.aab-abb-cycles-only-tooltip");

        // Variable used for the UI
        boolean all_homogeneous_cycles = getIntegerParam(P_DEGREE_FILTER) <= 0;
        boolean isolated_cycles = (getIntegerParam(P_DEGREE_FILTER) & 1) == 1;
        boolean pendant_cycles = (getIntegerParam(P_DEGREE_FILTER) & 2) == 2;
        boolean junction_cycles = (getIntegerParam(P_DEGREE_FILTER) & 4) == 4;
        boolean fork_cycles = (getIntegerParam(P_DEGREE_FILTER) & 8) == 8;

        boolean use_attribute = (getStringParam(P_ATTRIBUTE) != null);
        //String attribute = getStringParam(P_ATTRIBUTE);
        boolean ignore_empty = getBooleanParam(P_IGNORE_EMPTY);
        boolean all_heterogeneous_cycles = getBooleanParam(P_FIND_ALL_HETEROGENEOUS);
        boolean aab_abb_cycles_only = getBooleanParam(P_FIND_AAB_ABB);


        final MultiInputDialog dialog = new MultiInputDialog(
        context.getWorkbenchFrame(), CYCLE_FINDING, true);
        
        final JComboBox<Layer> jcb_layer = dialog.addLayerComboBox(
            LAYER, context.getCandidateLayer(0), null, context.getLayerManager());

        dialog.addCheckBox(DIM3, false, DIM3_TOOLTIP);

        dialog.addSeparator();

        dialog.addIntegerField(MIN_FEATURES, 1, 6, MIN_FEATURES_TOOLTIP);
        dialog.addIntegerField(MAX_FEATURES, 10, 6, MAX_FEATURES_TOOLTIP);
        dialog.addDoubleField(MAX_LENGTH, 500, 6, MAX_LENGTH_TOOLTIP);

        dialog.addSeparator();
        
        final JCheckBox jcb_all_homogeneous_cycles = dialog.addCheckBox(ALL_HOMOGENEOUS_CYCLES, all_homogeneous_cycles, ALL_HOMOGENEOUS_CYCLES_TOOLTIP);
        final JCheckBox jcb_isolated_cycles = dialog.addCheckBox(ISOLATED_CYCLES, isolated_cycles, ISOLATED_CYCLES_TOOLTIP);
        jcb_isolated_cycles.setEnabled(!all_homogeneous_cycles);
        final JCheckBox jcb_pendant_cycles = dialog.addCheckBox(PENDANT_CYCLES, pendant_cycles, PENDANT_CYCLES_TOOLTIP);
        jcb_pendant_cycles.setEnabled(!all_homogeneous_cycles);
        final JCheckBox jcb_junction_cycles = dialog.addCheckBox(JUNCTION_CYCLES, junction_cycles, JUNCTION_CYCLES_TOOLTIP);
        jcb_junction_cycles.setEnabled(!all_homogeneous_cycles);
        final JCheckBox jcb_fork_cycles = dialog.addCheckBox(FORK_CYCLES, fork_cycles, FORK_CYCLES_TOOLTIP);
        jcb_fork_cycles.setEnabled(!all_homogeneous_cycles);
        
        dialog.addSeparator();
        
        final JCheckBox jcb_use_attribute = dialog.addCheckBox(USE_ATTRIBUTE, use_attribute, USE_ATTRIBUTE_TOOLTIP);
        List<String> list = getFieldsFromLayerWithoutGeometry(context.getCandidateLayer(0));
        String val = list.size()>0?list.iterator().next():null;
        final JComboBox<String> jcb_attribute = dialog.addComboBox(ATTRIBUTE, val, list, USE_ATTRIBUTE_TOOLTIP);
        jcb_attribute.setEnabled(use_attribute);
        final JCheckBox jcb_ignore_empty = dialog.addCheckBox(IGNORE_EMPTY, ignore_empty, IGNORE_EMPTY_TOOLTIP);
        jcb_ignore_empty.setEnabled(use_attribute);
        
        final JCheckBox jcb_all_heterogeneous_cycles = dialog.addCheckBox(ALL_HETEROGENEOUS_CYCLES, all_heterogeneous_cycles, ALL_HETEROGENEOUS_CYCLES_TOOLTIP);
        jcb_all_heterogeneous_cycles.setEnabled(use_attribute);
        final JCheckBox jcb_aab_abb_cycles_only = dialog.addCheckBox(AAB_ABB_CYCLES_ONLY, aab_abb_cycles_only, AAB_ABB_CYCLES_ONLY_TOOLTIP);
        jcb_aab_abb_cycles_only.setEnabled(use_attribute && !all_heterogeneous_cycles);

        jcb_layer.addActionListener(e -> {
            List<String> list1 = getFieldsFromLayerWithoutGeometry(dialog.getLayer(LAYER));
            if (list1.size() == 0) {
                jcb_attribute.setModel(new DefaultComboBoxModel<>(new String[0]));
                jcb_use_attribute.setEnabled(false);
                jcb_attribute.setEnabled(false);
                jcb_ignore_empty.setEnabled(false);
                jcb_all_heterogeneous_cycles.setEnabled(false);
                jcb_aab_abb_cycles_only.setEnabled(false);
                //attribute = null;
            }
            else {
                jcb_attribute.setModel(new DefaultComboBoxModel<>(list1.toArray(new String[0])));
            }
        });
        
        jcb_use_attribute.addActionListener(e -> {
            boolean _use_attribute = dialog.getBoolean(USE_ATTRIBUTE);
            jcb_attribute.setEnabled(_use_attribute);
            jcb_ignore_empty.setEnabled(_use_attribute);
            jcb_all_heterogeneous_cycles.setEnabled(_use_attribute);
            jcb_aab_abb_cycles_only.setEnabled(_use_attribute);
        });

        jcb_all_homogeneous_cycles.addActionListener(e -> {
            boolean _all_homogeneous_cycles = dialog.getBoolean(ALL_HOMOGENEOUS_CYCLES);
            jcb_isolated_cycles.setEnabled(!_all_homogeneous_cycles);
            jcb_pendant_cycles.setEnabled(!_all_homogeneous_cycles);
            jcb_junction_cycles.setEnabled(!_all_homogeneous_cycles);
            jcb_fork_cycles.setEnabled(!_all_homogeneous_cycles);
        });
        
        jcb_all_heterogeneous_cycles.addActionListener(e -> {
            boolean _all_heterogeneous_cycles = dialog.getBoolean(ALL_HETEROGENEOUS_CYCLES);
            jcb_aab_abb_cycles_only.setEnabled(!_all_heterogeneous_cycles);
        });
        
        GUIUtil.centreOnWindow(dialog);
        dialog.setVisible(true);
        if (dialog.wasOKPressed()) {
            Layer layer = dialog.getLayer(LAYER);
            boolean dim3        = dialog.getBoolean(DIM3);
            int min_features    = dialog.getInteger(MIN_FEATURES);
            int max_features    = dialog.getInteger(MAX_FEATURES);
            double max_length   = dialog.getDouble(MAX_LENGTH);
            use_attribute = dialog.getBoolean(USE_ATTRIBUTE);
            String attribute = use_attribute? dialog.getText(ATTRIBUTE) : null;
            ignore_empty    = dialog.getBoolean(IGNORE_EMPTY);

            //all_homogeneous_cycles = dialog.getBoolean(ALL_HOMOGENEOUS_CYCLES);
            isolated_cycles = dialog.getBoolean(ISOLATED_CYCLES);
            pendant_cycles  = dialog.getBoolean(PENDANT_CYCLES);
            junction_cycles = dialog.getBoolean(JUNCTION_CYCLES);
            fork_cycles     = dialog.getBoolean(FORK_CYCLES);
            all_heterogeneous_cycles = dialog.getBoolean(ALL_HETEROGENEOUS_CYCLES);
            aab_abb_cycles_only = dialog.getBoolean(AAB_ABB_CYCLES_ONLY);

            int degree_filter = 0;
            if (isolated_cycles) degree_filter += 1;
            if (pendant_cycles)  degree_filter += 2;
            if (junction_cycles) degree_filter += 4;
            if (fork_cycles)     degree_filter += 8;

            addParameter(P_DATASET_NAME,    layer.getName());
            addParameter(P_DATASET,         layer.getFeatureCollectionWrapper());
            addParameter(P_GRAPH_3D,        dim3);
            addParameter(P_MIN_FEATURES,    min_features);
            addParameter(P_MAX_FEATURE,     max_features);
            addParameter(P_MAX_LENGTH,      max_length);
            addParameter(P_DEGREE_FILTER,   degree_filter);
            addParameter(P_ATTRIBUTE,       use_attribute?attribute:null);
            addParameter(P_IGNORE_EMPTY,    ignore_empty);
            addParameter(P_FIND_ALL_HETEROGENEOUS, all_heterogeneous_cycles);
            addParameter(P_FIND_AAB_ABB,    aab_abb_cycles_only);

            return true;
        }
        else return false;
        
    }

    @Override
    public void run(TaskMonitor monitor, PlugInContext context) {

        Map<String,FeatureCollection> map = run(monitor);

        String HOMOGENEOUS_CYCLES    = i18n.get("CycleFinderPlugIn.homogeneous-cycles");
        String HETEROGENEOUS_CYCLES  = i18n.get("CycleFinderPlugIn.heterogeneous-cycles");
        String NO_CYCLE_FOUND        = i18n.get("CycleFinderPlugIn.no-cycle-found");
        //Layer layer = context.getLayerManager().getLayer(getStringParam(P_DATASET_NAME));
        context.getLayerManager().addCategory(StandardCategoryNames.RESULT);
        if (map.get(HOMOGENEOUS_CYCLES).size()>0) {
            context.addLayer(StandardCategoryNames.RESULT,
                getStringParam(P_DATASET_NAME)+"-Cycles",
                map.get(HOMOGENEOUS_CYCLES));
        }
        if (getBooleanParam(P_ATTRIBUTE) != null && map.get(HETEROGENEOUS_CYCLES).size()>0) {
            context.addLayer(StandardCategoryNames.RESULT,
                getStringParam(P_DATASET_NAME)+"-HeterogeneousCycles", map.get(HETEROGENEOUS_CYCLES));
        }
        if (map.get(HOMOGENEOUS_CYCLES).size() == 0 && map.get(HETEROGENEOUS_CYCLES).size() == 0) {
            context.getWorkbenchFrame().warnUser(NO_CYCLE_FOUND);
        }
    }

    @SuppressWarnings("unchecked")
    Map<String, FeatureCollection> run(TaskMonitor monitor) {

        Map<String,FeatureCollection> map = new LinkedHashMap<>();

        String INDEXATION_OF         = i18n.get("CycleFinderPlugIn.indexation-of");
        String POLYGONIZATION_OF     = i18n.get("CycleFinderPlugIn.polygonization-of");
        String ANALYSIS_OF           = i18n.get("CycleFinderPlugIn.analysis-of");
        String PROCESSED_CYCLES      = i18n.get("CycleFinderPlugIn.processed-cycles");
        String NB_OF_EDGES           = i18n.get("CycleFinderPlugIn.number-of-edges");
        String LENGTH                = i18n.get("CycleFinderPlugIn.length");
        String AREA                  = i18n.get("CycleFinderPlugIn.area");
        String CONVEXITY             = i18n.get("CycleFinderPlugIn.convexity");
        String CONVEX                = i18n.get("CycleFinderPlugIn.convex");
        String CONCAVE               = i18n.get("CycleFinderPlugIn.concave");
        String CIRCULARITY           = i18n.get("CycleFinderPlugIn.circularity");
        String CYCLE_HOMOGENEITY     = i18n.get("CycleFinderPlugIn.cycle-homogeneity");
        String HOMOGENEOUS           = i18n.get("CycleFinderPlugIn.homogeneous");
        String ISOLATED              = i18n.get("CycleFinderPlugIn.isolated");
        String PENDANT               = i18n.get("CycleFinderPlugIn.pendant");
        String JUNCTION              = i18n.get("CycleFinderPlugIn.junction");
        String FORK                  = i18n.get("CycleFinderPlugIn.fork");
        String HETEROGENEOUS         = i18n.get("CycleFinderPlugIn.heterogeneous");
        String AABABB                = i18n.get("CycleFinderPlugIn.aababb");
        String COMMENT               = i18n.get("CycleFinderPlugIn.comment");

        //String NO_CYCLE_FOUND        = I18NPlug.getI18N("CycleFinderPlugIn.no-cycle-found");
        String HOMOGENEOUS_CYCLES    = i18n.get("CycleFinderPlugIn.homogeneous-cycles");
        String HETEROGENEOUS_CYCLES  = i18n.get("CycleFinderPlugIn.heterogeneous-cycles");

        boolean dim3 = getBooleanParam(P_GRAPH_3D);
        int min_features = getIntegerParam(P_MIN_FEATURES);
        int max_features = getIntegerParam(P_MAX_FEATURE);
        double max_length = getDoubleParam(P_MAX_LENGTH);

        boolean all_homogeneous_cycles = getIntegerParam(P_DEGREE_FILTER) <= 0;
        boolean isolated_cycles = (getIntegerParam(P_DEGREE_FILTER) & 1) == 1;
        boolean pendant_cycles = (getIntegerParam(P_DEGREE_FILTER) & 2) == 2;
        boolean junction_cycles = (getIntegerParam(P_DEGREE_FILTER) & 4) == 4;
        boolean fork_cycles = (getIntegerParam(P_DEGREE_FILTER) & 8) == 8;

        boolean use_attribute = (getStringParam(P_ATTRIBUTE) != null);
        String attribute = getStringParam(P_ATTRIBUTE);
        boolean ignore_empty = getBooleanParam(P_IGNORE_EMPTY);
        boolean all_heterogeneous_cycles = getBooleanParam(P_FIND_ALL_HETEROGENEOUS);
        boolean aab_abb_cycles_only = getBooleanParam(P_FIND_AAB_ABB);

        monitor.allowCancellationRequests();
        monitor.report(INDEXATION_OF + getStringParam(P_DATASET_NAME + "..."));

        FeatureSchema schema = new FeatureSchema();
        schema.addAttribute("GEOMETRY", AttributeType.GEOMETRY);
        schema.addAttribute(NB_OF_EDGES, AttributeType.INTEGER);
        schema.addAttribute(LENGTH, AttributeType.DOUBLE);
        schema.addAttribute(AREA, AttributeType.DOUBLE);
        schema.addAttribute(CONVEXITY, AttributeType.STRING);
        schema.addAttribute(CIRCULARITY, AttributeType.DOUBLE);
        schema.addAttribute(CYCLE_HOMOGENEITY, AttributeType.STRING);
        schema.addAttribute(COMMENT, AttributeType.STRING);
        FeatureCollection homogeneous_cycles_FC = new FeatureDataset(schema);
        FeatureCollection heterogeneous_cycles_FC = new FeatureDataset(schema);
        map.put(HOMOGENEOUS_CYCLES, homogeneous_cycles_FC);
        map.put(HETEROGENEOUS_CYCLES, heterogeneous_cycles_FC);

        // Filter and index input layer features
        // Do not eliminate long features too early,
        // because they can be used as incident edges
        FeatureCollection filteredFC = (FeatureCollection)getParameters().get(P_DATASET);
        if (use_attribute && ignore_empty) {
            filteredFC = new FeatureDataset(filteredFC.getFeatureSchema());
            for (Feature f : ((FeatureCollection)getParameters().get(P_DATASET)).getFeatures()) {
                if (f.getAttribute(attribute)!=null &&
                    f.getAttribute(attribute).toString().trim().length()>0) {
                    filteredFC.add(f);
                }
            }
        }

        IndexedFeatureCollection ifc =
            new IndexedFeatureCollection(filteredFC, new STRtree());

        // Eliminate features with length < max before polygonization
        // WARNING : a long feature can cut a short cycle into 2 long cycles
        // ==> eliminating long features can produce small non-simple cycles
        Collection<Geometry> geoms = new ArrayList<>();
        // [2013-01-15] Eliminate line duplicates
        Collection<Geometry> lines = new HashSet<>();
        for (Feature f : filteredFC.getFeatures()) {
            Geometry geom = f.getGeometry();
            if (geom.getLength()<=max_length) {
                if (geom.getDimension() == 1) lines.add(geom.norm());
                else geoms.add(geom);
            }
        }
        geoms.addAll(lines);

        monitor.report(POLYGONIZATION_OF + getStringParam(P_DATASET_NAME) + "...");
        // Polygonisation + selection of polygons with length < threshold
        Polygonizer polygonizer = new Polygonizer();
        polygonizer.add(geoms);
        Collection<Geometry> pols = polygonizer.getPolygons();
        geoms.clear();
        for (Geometry p : pols) {
            if ((p).getLength() <= max_length) geoms.add(p);
        }

        monitor.report(ANALYSIS_OF + getStringParam(P_DATASET_NAME) + "...");
        int count = 0;
        // Loop over polygons representing cycles
        for (Geometry g : geoms) {
            // Select edges of the cycle and edges incident to the cycle
            List<Feature> list = ifc.query(g.getEnvelopeInternal());
            for (int i = list.size()-1 ; i>=0 ; i--) {
                Geometry gfeature = (list.get(i)).getGeometry();
                if (g.disjoint(gfeature)) list.remove(i);
            }

            // Creates the graph with features intersecting the polygon
            Pseudograph<INode, FeatureAsEdge> graph =
                GraphFactory.createUndirectedGraph(list, dim3);
            // Graph node set and graph edges set
            Set<INode> nodeSet = graph.vertexSet();
            Set<FeatureAsEdge> edgeSet = graph.edgeSet();

            // Subgraph containing only the cycle
            Set<INode> cycleNodeSet = new HashSet<>();
            for (INode n : nodeSet) {
                if (n.getGeometry().intersects(g)) cycleNodeSet.add(n);
            }
            Graph<INode, FeatureAsEdge> cycle = new AsSubgraph<>(graph, cycleNodeSet);
            Set<FeatureAsEdge> cycleEdgeSet = cycle.edgeSet();

            // Eliminate too long ot too short cycles
            if (cycleEdgeSet.size() < min_features || cycleEdgeSet.size() > max_features) continue;

            String shape = g.equals(new ConvexHull(g).getConvexHull())?CONVEX:CONCAVE;
            double area = g.getArea();
            double perimeter = g.getLength();
            // Circularity (100 = circular / 0 = linear)
            double circularity = Math.floor(100.0*area*4.0*Math.PI/perimeter/perimeter);
            // Build one feature for each cycle under/over size thresholds
            Feature newf = new BasicFeature(schema);
            newf.setGeometry(g);
            newf.setAttribute(NB_OF_EDGES, cycleEdgeSet.size());
            newf.setAttribute(CONVEXITY, shape);
            newf.setAttribute(CIRCULARITY, circularity);
            newf.setAttribute(LENGTH, perimeter);
            newf.setAttribute(AREA, area);

            // feature attributes found in cycle
            Set<Object> attributeSet = new HashSet<>();
            String NOATT = "DO_NOT_USE_ATTRIBUTE";
            for (FeatureAsEdge e : cycleEdgeSet) {
                if (use_attribute) attributeSet.add(e.getAttribute(attribute));
                else attributeSet.add(NOATT);
            }

            // Case 0 : no attribute defined for cycle homogeneity
            if (!use_attribute) {
                newf.setAttribute(CYCLE_HOMOGENEITY, HOMOGENEOUS);
                int incident_edges = edgeSet.size() - cycleEdgeSet.size();
                if (incident_edges == 0 && (all_homogeneous_cycles || isolated_cycles)) {
                    newf.setAttribute(CYCLE_HOMOGENEITY, HOMOGENEOUS);
                    newf.setAttribute(COMMENT, ISOLATED);
                    homogeneous_cycles_FC.add(newf);
                }
                else if (incident_edges == 1 && (all_homogeneous_cycles || pendant_cycles)) {
                    newf.setAttribute(CYCLE_HOMOGENEITY, HOMOGENEOUS);
                    newf.setAttribute(COMMENT, PENDANT);
                    homogeneous_cycles_FC.add(newf);
                }
                else if (incident_edges == 2 && (all_homogeneous_cycles || junction_cycles)) {
                    newf.setAttribute(CYCLE_HOMOGENEITY, HOMOGENEOUS);
                    newf.setAttribute(COMMENT, JUNCTION);
                    homogeneous_cycles_FC.add(newf);
                }
                else if (incident_edges > 2 && (all_homogeneous_cycles || fork_cycles)) {
                    newf.setAttribute(CYCLE_HOMOGENEITY, HOMOGENEOUS);
                    newf.setAttribute(COMMENT, FORK);
                    homogeneous_cycles_FC.add(newf);
                }
            }

            // Case 1 : only one attribute value or !use_attribute
            else if (attributeSet.size() == 1) {
                newf.setAttribute(CYCLE_HOMOGENEITY, HOMOGENEOUS);
                Object atth = attributeSet.iterator().next();
                int count_homogeneous_edges = 0;
                for (FeatureAsEdge e : edgeSet) {
                    Object atte = use_attribute? e.getAttribute(attribute): NOATT;
                    if (atte.equals(atth)) count_homogeneous_edges++;
                }
                int incident_edges = count_homogeneous_edges - cycleEdgeSet.size();
                if (incident_edges == 0 && (all_homogeneous_cycles || isolated_cycles)) {
                    newf.setAttribute(CYCLE_HOMOGENEITY, HOMOGENEOUS);
                    newf.setAttribute(COMMENT, ISOLATED);
                    homogeneous_cycles_FC.add(newf);
                }
                else if (incident_edges == 1 && (all_homogeneous_cycles || pendant_cycles)) {
                    newf.setAttribute(CYCLE_HOMOGENEITY, HOMOGENEOUS);
                    newf.setAttribute(COMMENT, PENDANT);
                    homogeneous_cycles_FC.add(newf);
                }
                else if (incident_edges == 2 && (all_homogeneous_cycles || junction_cycles)) {
                    newf.setAttribute(CYCLE_HOMOGENEITY, HOMOGENEOUS);
                    newf.setAttribute(COMMENT, JUNCTION);
                    homogeneous_cycles_FC.add(newf);
                }
                else if (incident_edges > 2 && (all_homogeneous_cycles || fork_cycles)) {
                    newf.setAttribute(CYCLE_HOMOGENEITY, HOMOGENEOUS);
                    newf.setAttribute(COMMENT, FORK);
                    homogeneous_cycles_FC.add(newf);
                }

            }

            // Case 2 : use_attribute and several attribute values
            else if (attributeSet.size() > 1 && (all_heterogeneous_cycles || aab_abb_cycles_only)) {
                newf.setAttribute(CYCLE_HOMOGENEITY, HETEROGENEOUS);
                //Object atth = attributeSet.iterator().next();
                INode[] nodeArray = cycleNodeSet.toArray(new INode[0]);
                if (all_heterogeneous_cycles) heterogeneous_cycles_FC.add(newf);
                else { // aab_abb_cycles_only
                    for (int i = 0 ; i < nodeArray.length ; i++) {
                        //Map<Object,Integer> edgeMapI = new HashMap<Object,Integer>();
                        if (graph.edgesOf(nodeArray[i]).size() != 3) continue;
                        Map<Object,Integer> edgeMapI = incidentEdgeValues(nodeArray[i], graph, attribute);
                        if (edgeMapI.size() != 2) continue;
                        for (int j = 0 ; j < nodeArray.length ; j++) {
                            if (i==j || graph.edgesOf(nodeArray[j]).size() != 3) continue;
                            Map<Object,Integer> edgeMapJ = incidentEdgeValues(nodeArray[j], graph, attribute);
                            if (edgeMapI.keySet().equals(edgeMapJ.keySet()) && !edgeMapI.equals(edgeMapJ)) {
                                newf.setAttribute(COMMENT, AABABB);
                                heterogeneous_cycles_FC.add(newf);
                            }
                        }
                    }
                }
            }
            count++;
            monitor.report(count, geoms.size(), PROCESSED_CYCLES);
        }
        return map;
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
    
    // Returns a map counting distinct attribute values of incident edges
    private Map<Object,Integer> incidentEdgeValues(INode node,
                Pseudograph<INode,FeatureAsEdge> graph, String attribute) {
        Map<Object,Integer> edgeMap = new HashMap<>();
        Set<FeatureAsEdge> edges = graph.edgesOf(node);
        for (FeatureAsEdge edge : edges) {
            Object val = edge.getAttribute(attribute);
            if (edgeMap.containsKey(val)) {
                edgeMap.put(val, edgeMap.get(val)+1);
            }
            else edgeMap.put(val, 1);
        }
        return edgeMap;
    }

}
