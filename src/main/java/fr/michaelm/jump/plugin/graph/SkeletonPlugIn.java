package fr.michaelm.jump.plugin.graph;

import com.vividsolutions.jump.feature.*;
import com.vividsolutions.jump.geom.Angle;
import com.vividsolutions.jump.task.TaskMonitor;
import com.vividsolutions.jump.workbench.WorkbenchContext;
import com.vividsolutions.jump.workbench.model.LayerManager;
import com.vividsolutions.jump.workbench.model.StandardCategoryNames;
import com.vividsolutions.jump.workbench.plugin.EnableCheckFactory;
import com.vividsolutions.jump.workbench.plugin.MultiEnableCheck;
import com.vividsolutions.jump.workbench.plugin.PlugInContext;
import com.vividsolutions.jump.workbench.ui.GUIUtil;
import com.vividsolutions.jump.workbench.ui.MenuNames;
import com.vividsolutions.jump.workbench.ui.MultiInputDialog;
import com.vividsolutions.jump.workbench.ui.plugin.FeatureInstaller;
import fr.michaelm.jump.feature.jgrapht.GraphFactory;
import org.jgrapht.graph.Pseudograph;
import org.jgrapht.graph.WeightedPseudograph;
import org.locationtech.jts.algorithm.MinimumDiameter;
import org.locationtech.jts.algorithm.distance.DistanceToPoint;
import org.locationtech.jts.algorithm.distance.PointPairDistance;
import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator;
import org.locationtech.jts.densify.Densifier;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.LineStringExtracter;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;
import org.openjump.core.ui.plugin.AbstractThreadedUiPlugIn;

import fr.michaelm.jump.feature.jgrapht.FeatureAsEdge;
import fr.michaelm.jump.feature.jgrapht.INode;

import javax.swing.*;
import java.util.*;

/**
 * PlugIn to skeletonize polygons
 * See https://github.com/orbisgis/h2gis/wiki/3.1-Extract-central-skeleton
 */
public class SkeletonPlugIn extends AbstractThreadedUiPlugIn {

    private static final String P_LAYER_NAME               = "LayerName";
    private static final String P_AUTO_WIDTH               = "AutoWidth";
    private static final String P_MIN_WIDTH                = "MinWidth";
    private static final String P_MIN_FORK_LENGTH          = "MinForkLength";
    private static final String P_RELATIVE_MIN_FORK_LENGTH = "MinForkLengthRelative";
    private static final String P_SNAP_TO_BOUNDARY         = "SnapToBoundary";
    private static final String P_MEAN_WIDTH               = "MeanWidth"; // Internal intermediate result
    private static final String P_MAX_ITERATION            = "MaxIteration";

    private static final String GRAPH                   = I18NPlug.getI18N("Graph");
    private static final String CENTRAL_SKELETON        = I18NPlug.getI18N("SkeletonPlugIn");
    private static final String SKELETONIZE             = I18NPlug.getI18N("SkeletonPlugIn.skeletonize");
    private static final String SOURCE_LAYER            = I18NPlug.getI18N("SkeletonPlugIn.source-layer");
    private static final String AUTO_WIDTH_PARAMETER    = I18NPlug.getI18N("SkeletonPlugIn.auto-width-parameter");
    private static final String AUTO_WIDTH_PARAMETER_TT = I18NPlug.getI18N("SkeletonPlugIn.auto-width-parameter-tooltip");
    private static final String MIN_WIDTH               = I18NPlug.getI18N("SkeletonPlugIn.min-width");
    private static final String MIN_WIDTH_TT            = I18NPlug.getI18N("SkeletonPlugIn.min-width-tooltip");
    private static final String MIN_FORK_LENGTH_FROM_MEAN_WIDTH    = I18NPlug.getI18N("SkeletonPlugIn.min-fork-length-from-mean-width");
    private static final String MIN_FORK_LENGTH_FROM_MEAN_WIDTH_TT = I18NPlug.getI18N("SkeletonPlugIn.min-fork-length-from-mean-width-tooltip");
    private static final String MIN_FORK_LENGTH         = I18NPlug.getI18N("SkeletonPlugIn.min-fork-length");
    private static final String MIN_FORK_LENGTH_TT      = I18NPlug.getI18N("SkeletonPlugIn.min-fork-length-tooltip");
    private static final String SNAP_TO_BOUNDARY        = I18NPlug.getI18N("SkeletonPlugIn.snap-to_boundary");
    private static final String SNAP_TO_BOUNDARY_TT     = I18NPlug.getI18N("SkeletonPlugIn.snap-to_boundary-tooltip");
    private static final String DISPLAY_VORONOI_EDGES   = I18NPlug.getI18N("SkeletonPlugIn.display-voronoi-edges");
    private static final String DESCRIPTION             = I18NPlug.getI18N("SkeletonPlugIn.description");

    double SQRT2 = Math.sqrt(2.0);

    // default external parameters
    {
      addParameter(P_LAYER_NAME, null);
      addParameter(P_AUTO_WIDTH,       true);
      addParameter(P_MIN_WIDTH,        1.0);
      addParameter(P_MIN_FORK_LENGTH,  10.0);
      addParameter(P_SNAP_TO_BOUNDARY, true);
      addParameter(P_RELATIVE_MIN_FORK_LENGTH, false);
      addParameter(P_MAX_ITERATION, 8192); // default value
    }

    // internal parameters
    private double  simplification        = 0.2;   // default simplification factor is minWidth/5
    private double  densification         = 0.5;   // default densification factor is minWidth/2
    //private double  meanWidth             = 2.0;   // mean width of the polygon
    //private int     maxIterations         = 8192;  // maximum iteration to eliminate forks
    private boolean displayVoronoiEdges   = false;

    public void initialize(PlugInContext context) {

        workbenchContext = context.getWorkbenchContext();
        FeatureInstaller featureInstaller = new FeatureInstaller(workbenchContext);
        featureInstaller.addMainMenuPlugin(
                this,
                new String[] {MenuNames.PLUGINS, GRAPH},
                CENTRAL_SKELETON + "...",
                false,			//checkbox
                new ImageIcon(this.getClass().getResource("Skeleton.png")),
                createEnableCheck(context.getWorkbenchContext()));
    }

    @Override
    public String getName() {
        // Otherwise, I18N class looks for SkeletonPlugIn key in the main OpenJUMP resource file
        return I18NPlug.getI18N("SkeletonPlugIn");
    }

    private static MultiEnableCheck createEnableCheck(WorkbenchContext workbenchContext) {
        EnableCheckFactory checkFactory = new EnableCheckFactory(workbenchContext);
        return new MultiEnableCheck().add(checkFactory.createAtLeastNLayersMustExistCheck(1));
    }


    public boolean execute(PlugInContext context) {
        this.reportNothingToUndoYet(context);

        MultiInputDialog dialog = new MultiInputDialog(
                context.getWorkbenchFrame(), getName(), true);
        setDialogValues(dialog, context);
        GUIUtil.centreOnWindow(dialog);
        dialog.setVisible(true);
        if (! dialog.wasOKPressed()) { return false; }
        getDialogValues(dialog);
        return true;
    }

    private void setDialogValues(MultiInputDialog dialog, PlugInContext context) {
        dialog.setSideBarDescription(DESCRIPTION);
        if (getStringParam(P_LAYER_NAME) == null ||
                context.getLayerManager().getLayer(getStringParam(P_LAYER_NAME)) == null) {
            addParameter(P_LAYER_NAME, context.getCandidateLayer(0).getName());
        }
        dialog.addLayerComboBox(SOURCE_LAYER,
                context.getLayerManager().getLayer(getStringParam(P_LAYER_NAME)),
                null, context.getLayerManager());

        final JCheckBox autoWidthJcb = dialog.addCheckBox(AUTO_WIDTH_PARAMETER,
                getBooleanParam(P_AUTO_WIDTH), AUTO_WIDTH_PARAMETER_TT);
        final JTextField minWidthTF = dialog.addDoubleField(MIN_WIDTH,
                getDoubleParam(P_MIN_WIDTH), 12, MIN_WIDTH_TT);
        minWidthTF.setEnabled(!getBooleanParam(P_AUTO_WIDTH));

        dialog.addDoubleField(MIN_FORK_LENGTH,
                getDoubleParam(P_MIN_FORK_LENGTH), 12, MIN_FORK_LENGTH_TT);
        dialog.addCheckBox(MIN_FORK_LENGTH_FROM_MEAN_WIDTH,
                getBooleanParam(P_RELATIVE_MIN_FORK_LENGTH), MIN_FORK_LENGTH_FROM_MEAN_WIDTH_TT);

        dialog.addCheckBox(SNAP_TO_BOUNDARY,
                getBooleanParam(P_SNAP_TO_BOUNDARY), SNAP_TO_BOUNDARY_TT);
        dialog.addCheckBox(DISPLAY_VORONOI_EDGES, displayVoronoiEdges);

        autoWidthJcb.addActionListener(e -> minWidthTF.setEnabled(!autoWidthJcb.isSelected()));
    }

    private void getDialogValues(MultiInputDialog dialog) {
        addParameter(P_LAYER_NAME, dialog.getLayer(SOURCE_LAYER).getName());
        addParameter(P_AUTO_WIDTH, dialog.getBoolean(AUTO_WIDTH_PARAMETER));
        addParameter(P_MIN_WIDTH, dialog.getDouble(MIN_WIDTH));
        addParameter(P_RELATIVE_MIN_FORK_LENGTH, dialog.getBoolean(MIN_FORK_LENGTH_FROM_MEAN_WIDTH));
        addParameter(P_MIN_FORK_LENGTH, dialog.getDouble(MIN_FORK_LENGTH));
        addParameter(P_SNAP_TO_BOUNDARY, dialog.getBoolean(SNAP_TO_BOUNDARY));
        displayVoronoiEdges = dialog.getBoolean(DISPLAY_VORONOI_EDGES);
    }

    private double getMinForkLength() {
        return getBooleanParam(P_RELATIVE_MIN_FORK_LENGTH) ?
                getDoubleParam(P_MIN_FORK_LENGTH) * getDoubleParam(P_MEAN_WIDTH)
                : getDoubleParam(P_MIN_FORK_LENGTH);
    }

    public void run(TaskMonitor monitor, PlugInContext context) {
        monitor.report(SKELETONIZE);
        LayerManager layerManager = context.getLayerManager();
        FeatureCollection inputFC = layerManager.getLayer(getStringParam(P_LAYER_NAME)).getFeatureCollectionWrapper();
        FeatureSchema schema = inputFC.getFeatureSchema().clone();
        schema.addAttribute("mean_width", AttributeType.DOUBLE);
        schema.addAttribute("min_width", AttributeType.DOUBLE);
        schema.addAttribute("min_fork_length", AttributeType.DOUBLE);
        schema.addAttribute("iteration_number", AttributeType.INTEGER);
        schema.addAttribute("duration_ms", AttributeType.INTEGER);
        schema.addAttribute("comment", AttributeType.STRING);
        //schema.addAttribute("snap_ends", AttributeType.BOOLEAN);
        FeatureCollection outputFC = new FeatureDataset(schema);
        int count = 0;
        // edges = list to collect original edges from the voronoi diagram
        List<Geometry> edges = new ArrayList<>();
        for (Feature feature : inputFC.getFeatures()) {
            monitor.report(count++, inputFC.size(), "Feature");
            if (feature.getGeometry().getDimension() == 2) {
                Geometry geom = feature.getGeometry();
                if (!geom.isValid()) geom = geom.buffer(0);
                for (int i = 0 ; i < geom.getNumGeometries() ; i++) {
                    //Feature newFeature = feature.clone(false, false);
                    long t0 = System.currentTimeMillis();
                    Feature newFeature = new BasicFeature(schema);
                    Object[] objects = new Object[schema.getAttributeCount()];
                    System.arraycopy(feature.getAttributes(), 0, objects, 0, feature.getSchema().getAttributeCount());
                    newFeature.setAttributes(objects);
                    Geometry g = geom.getGeometryN(i);
                    try {
                        g = computeParams(g);
                    } catch (Exception e) {
                        context.getWorkbenchFrame().warnUser(e.getMessage());
                    }
                    newFeature.setAttribute("mean_width", getDoubleParam(P_MEAN_WIDTH));
                    newFeature.setAttribute("min_width",  getDoubleParam(P_MIN_WIDTH));
                    newFeature.setAttribute("min_fork_length", getMinForkLength());
                    g = skeletonize(g, edges);
                    newFeature.setGeometry(g);
                    newFeature.setAttribute("iteration_number", ((Object[])g.getUserData())[0]);
                    newFeature.setAttribute("duration_ms", (int)(System.currentTimeMillis()-t0));
                    newFeature.setAttribute("comment", ((Object[])g.getUserData())[1]);
                    g.setUserData(null);
                    outputFC.add(newFeature);
                }
            } else {
                Feature newFeature = feature.clone(false, false);
                newFeature.setGeometry(feature.getGeometry());
                outputFC.add(newFeature);
            }
        }
        layerManager.addLayer(StandardCategoryNames.RESULT, getStringParam(P_LAYER_NAME) + " - skeletonized", outputFC);
        if (edges.size() > 0) {
            layerManager.addLayer(StandardCategoryNames.RESULT, getStringParam(P_LAYER_NAME) + " - voronoi-edges",
                    FeatureDatasetFactory.createFromGeometry(edges));
        }
    }

    // Compute meanWidth from the geometry
    // Compute minWidth from meanWidth if autoWidth = true
    // Compute minForkLength if fromMeanWidth = true
    // This method must be called only once per object
    private Geometry computeParams(Geometry geometry) {
        double meanWidth = getMeanWidth(geometry);
        if (meanWidth==0) meanWidth = geometry.getLength()/geometry.getNumPoints()/5;
        if (getBooleanParam(P_AUTO_WIDTH)) {
            // Iterative function to find the minimal width of the polygon
            // A buffer < -minWidth/2 will split the polygon into two polygons
            // (except in polygons with holes where)
            int numGeometries = geometry.getNumGeometries();
            double semiMinWidth = meanWidth*0.5/64;
            double originalLength = geometry.getLength();
            Geometry buffer = geometry.buffer(-semiMinWidth);
            // Use semiMinWidth as the minimalWidth if
            // - semiMinWidth split the geometry into several parts or
            // - semiMinWidth reduces boundary length by more than 10%
            if (buffer.getNumGeometries() > numGeometries || buffer.getLength()/originalLength <= 0.9) {
                // polygon is very thin : it has been split or reduced by more than 10%
                // after a negative buffer of only meanWidth*0.5/64
                // ==> we create a buffer around the polygon to be able to calculate the
                // skeleton with a reasonable point densification
                geometry = geometry.buffer(meanWidth/32);
                semiMinWidth = meanWidth/32;
            } else {
                while (buffer.getNumGeometries() == numGeometries && buffer.getLength() / originalLength > 0.9) {
                    semiMinWidth = semiMinWidth * SQRT2;
                    buffer = geometry.buffer(-semiMinWidth);
                }
            }
            addParameter(P_MIN_WIDTH, semiMinWidth);
        }
        addParameter(P_MEAN_WIDTH, meanWidth);
        //if (getBooleanParam(P_RELATIVE_MIN_FORK_LENGTH)) {
        //    addParameter(P_MIN_FORK_LENGTH, getDoubleParam(P_MIN_FORK_LENGTH)*meanWidth);
        //}
        simplification = getDoubleParam(P_MIN_WIDTH)/5.0;
        densification = getDoubleParam(P_MIN_WIDTH)/2.0;
        return geometry;
    }

    // Computes the mean width of a polygon
    private Double getMeanWidth(Geometry g) {
        if (g.getDimension() == 2) {
            double length = g.getLength();
            double area = g.getArea();
            double val = (( length * length ) / 4.0 )-( 4.0 * area );
            if (val >= 0.0) {
                //calcul normal sur surface allongée
                return ((length / 2.0) - Math.sqrt(val)) / 2.0;
            } else {
                //diamètre du disque de même surface, sur une surface ramassée
                //return 2.0 * Math.sqrt(area / Math.PI);
                //improve width calculation for compact geometries
                MinimumDiameter diameter = new MinimumDiameter(g);
                Geometry minRectangle = diameter.getMinimumRectangle();
                double ratio = Math.sqrt(minRectangle.getArea()/g.getArea());
                return diameter.getLength()/ratio;
            }
        } else return 0d;
    }

    // Simplification/densification of input geometry
    private Geometry preprocess(Geometry geometry, int iteration) {
        if (iteration > 0) {
            geometry = geometry.buffer(getDoubleParam(P_MIN_WIDTH)/2);
            simplification = getDoubleParam(P_MIN_WIDTH)/5.0;
            densification = getDoubleParam(P_MIN_WIDTH)/2.0;
        }
        geometry = TopologyPreservingSimplifier.simplify(geometry, simplification);
        if (!geometry.isEmpty() && !Double.isNaN(getDoubleParam(P_MIN_WIDTH))) {
            geometry = Densifier.densify(geometry, densification);
        }
        return geometry;
    }

    // Build a voronoi diagram based on geometry vertices
    // (geometry must have been densified before)
    private Geometry getVoronoiDiagram(Geometry geometry, int iteration) {
        if (geometry.isEmpty()) return geometry;
        if (iteration > 32) return geometry.getFactory().createMultiPolygon(new Polygon[0]);
        VoronoiDiagramBuilder voronoiBuilder = new VoronoiDiagramBuilder();
        voronoiBuilder.setTolerance(Math.sqrt(geometry.getArea())/1000000);
        voronoiBuilder.setSites(geometry.getFactory().createMultiPointFromCoords(geometry.getCoordinates()));
        Envelope env = geometry.getEnvelopeInternal();
        env.expandBy(env.getWidth()/3, env.getHeight()/3);
        try {
            voronoiBuilder.setClipEnvelope(env);
            Geometry voronoi = voronoiBuilder.getDiagram(geometry.getFactory());
            if (iteration > 0) {
              voronoi.setUserData("Voronoi calculation problem");
            }
            return voronoi;
        } catch(Exception e) {
            Geometry newGeometry = preprocess(geometry, iteration);
            return getVoronoiDiagram(newGeometry, ++iteration);
        }
    }

    // Get single line segments from the voronoi diagram
    private void getEdges(Geometry geometry, Set<LineString> edges) {
        for (int i = 0 ; i < geometry.getNumGeometries() ; i++) {
            Geometry g = geometry.getGeometryN(i);
            if (g instanceof GeometryCollection) {
                getEdges(g, edges);
            }
            else if (g.getDimension() == 1) {
                addSegments((LineString)g, edges);
            }
            else if (g.getDimension() == 2){
                Polygon p = (Polygon)g;
                addSegments(p.getExteriorRing(), edges);
                for (int j = 0 ; j < p.getNumInteriorRing() ; j++) {
                    addSegments(p.getInteriorRingN(j), edges);
                }
            }
            // do nothing for geometries of dimension 0 (points)
        }
    }

    // Add every single segment of lineString to the set
    private void addSegments(LineString lineString, Set<LineString> set) {
        Coordinate[] cc = lineString.getCoordinates();
        for (int i = 1 ; i < cc.length ; i++) {
            LineString s = lineString.getFactory().createLineString(new Coordinate[]{cc[i-1], cc[i]});
            s = (LineString)s.copy();
            s.normalize();
            set.add(s);
        }
    }

    // Keep only segments entirely located inside the geometry
    // This method is faster than a simple geometry.contains test.
    // We consider that edges issued from the voronoi diagram are simple line segments
    // which are entirely included in the geometry if both ends are included in the
    // polygon (which is only true if the geometry has been appropriately densified).
    // From this consideration, we can replace contains by pointInRing tests
    // On complex polygons (2800 pts) filter is by far the longest operation of
    // skeletisation and this method save up to 75% time.
    private Set<LineString> filter(Geometry geometry, Set<LineString> list) {
        Set<LineString> result = new HashSet<>();
        if (geometry instanceof Polygon) {
            Polygon poly = (Polygon)geometry;
            for (LineString line : list) {
                Coordinate start = line.getCoordinateN(0);
                Coordinate end = line.getCoordinateN(line.getNumPoints()-1);
                IndexedPointInAreaLocator ipial =
                        new IndexedPointInAreaLocator(poly.getExteriorRing());
                //MCPointInRing mcpir = new MCPointInRing((LinearRing)poly.getExteriorRing());
                if (ipial.locate(start) == Location.INTERIOR && ipial.locate(end) == Location.INTERIOR) {
                    boolean inside = true;
                    for (int i = 0 ; i < poly.getNumInteriorRing() ; i++) {
                        ipial = new IndexedPointInAreaLocator(poly.getInteriorRingN(i));
                        if (ipial.locate(start) == Location.INTERIOR || ipial.locate(end) == Location.INTERIOR) {
                            inside = false;
                            break;
                        }
                    }
                    if (inside) result.add(line);
                }
            }
            return result;
        } else throw new IllegalArgumentException(geometry.getGeometryType() + " argument is not authorized");
    }


    private Geometry skeletonize(Geometry geometry, List<Geometry> list) {

        Object[] userData = new Object[2];
        int maxIterations = getIntegerParam(P_MAX_ITERATION);

        // 1 - Build voronoi diagram and extract the edges
        Set<LineString> edges = new HashSet<>();
        Geometry voronoi = getVoronoiDiagram(preprocess(geometry, 0), 0);
        userData[1] = voronoi.getUserData();
        getEdges(voronoi, edges);

        // 2 - Filter voronoi edges strictly included in the geometry
        edges = filter(geometry, edges);

        // 3 - Merge filtered edges
        LineMerger merger = new LineMerger();
        merger.add(edges);
        edges = new HashSet<>(merger.getMergedLineStrings());

        if (displayVoronoiEdges) list.addAll(edges);

        // 4 - Compute the graph and iterate to eliminate edges
        Pseudograph<INode,FeatureAsEdge> graph = getGraph(edges);

        // 5 - Simplify the graph iteratively
        // In each loop, the method eliminates the shortest branch of each fork
        int i;
        Geometry boundary = geometry.getBoundary();
        for (i = 0; i < maxIterations ; i++) {
            int edgeNumber = graph.edgeSet().size();
            graph = simplify(graph, boundary, true);
            // if simplify() does not remove edges any more, break the loop
            if (graph.edgeSet().size() == edgeNumber) break;
        }
        graph = simplify(graph, boundary, false);
        i++;

        // 6 - Beautify ends
        List<FeatureAsEdge> finalEdges = new ArrayList<>(graph.edgeSet());
        for (FeatureAsEdge f : finalEdges) {
            f.setGeometry(TopologyPreservingSimplifier.simplify(f.getGeometry(), getDoubleParam(P_MIN_WIDTH)/5));
            EdgeNodes nodes = new EdgeNodes(graph, f);
            if (nodes.srcDegree == 1) {
                try {
                    f.setGeometry(snapStart(f.getGeometry(), geometry.getBoundary(),
                            getBooleanParam(P_SNAP_TO_BOUNDARY)));
                } catch(Exception e) {
                    userData[1] = "Snapping error";
                }
            }
            if (nodes.tgtDegree == 1) {
                try {
                    f.setGeometry(snapEnd(f.getGeometry(), geometry.getBoundary(),
                            getBooleanParam(P_SNAP_TO_BOUNDARY)));
                } catch(Exception e) {
                    userData[1] = "Snapping error";
                }
            }
        }

        //System.out.println(String.format("simplify graph : %d", (System.currentTimeMillis() - t0) / 1000));

        // 7 - Retourner une geometrie
        Geometry geom = graph2geometry(graph);
        geom = TopologyPreservingSimplifier.simplify(geom, getDoubleParam(P_MIN_WIDTH)/5);
        userData[0] = i;
        if (geom.isEmpty()) userData[1] = "Empty";
        geom.setUserData(userData); // set the number of iteration used
        return geom;
    }

    private Geometry snapStart(Geometry edge, Geometry boundary, boolean snap) throws Exception {
        CoordinateList cl = new CoordinateList(edge.getCoordinates());
        Coordinate c0 = cl.getCoordinate(0);
        Coordinate c1 = cl.getCoordinate(1);
        double segmentLength = c0.distance(c1);

        PointPairDistance ppd = new PointPairDistance();
        DistanceToPoint.computeDistance(boundary, c1, ppd);
        double localWidth = ppd.getDistance();

        // Last segment c0-c1 is short compared to local mean width (distance between c1 and the boundary)
        if (segmentLength < localWidth*2 && edge.getNumPoints() > 2) {
            Coordinate c2 = cl.getCoordinate(2);
            //Returns the unoriented smallest angle between two vectors.
            double angle = Angle.toDegrees(Angle.angleBetween(c2, c1, c0));
            // angle between two last segments on a square buffer is typically 135°
            if (snap && angle < 145) {
                cl.set(0, extendTo(c2, c1, boundary, ppd.getDistance()/3));
            } else if (angle < 145) {
                cl.remove(0);
            }
        } else if (snap){
            cl.add(0, extendTo(c1, c0, boundary, ppd.getDistance()/3));
        }
        return edge.getFactory().createLineString(cl.toCoordinateArray());
    }

    private Geometry snapEnd(Geometry edge, Geometry boundary, boolean snap) throws Exception {
        CoordinateList cl = new CoordinateList(edge.getCoordinates());
        Coordinate c_0 = cl.getCoordinate(cl.size()-1);
        Coordinate c_1 = cl.getCoordinate(cl.size()-2);
        double segmentLength = c_0.distance(c_1);

        PointPairDistance ppd = new PointPairDistance();
        DistanceToPoint.computeDistance(boundary, c_1, ppd);
        double localWidth = ppd.getDistance();
        // Last segment c_0-c_1 is short compared to local mean width (distance between c1 and the boundary)
        if (segmentLength < localWidth*2 && edge.getNumPoints() > 2) {
            Coordinate c_2 = cl.getCoordinate(cl.size()-3);
            double angle = Angle.toDegrees(Angle.angleBetween(c_2, c_1, c_0));
            // angle between two last segments on a square buffer is typically 135°
            if (snap && angle < 145) {
                cl.set(cl.size()-1, extendTo(c_2, c_1, boundary, ppd.getDistance()/3));
            } else if (angle < 145){
                cl.remove(cl.size()-1);
            }
        } else if (snap) {
            cl.add(extendTo(c_1, c_0, boundary, ppd.getDistance()/3));
        }
        return edge.getFactory().createLineString(cl.toCoordinateArray());
    }

    // Extends c0-c1 segment until it crosses boundary.
    // Returns the first intersection or a coordinate of the boundary close to this intersection
    // if distance to the intersection is  < tol
    private Coordinate extendTo(Coordinate c0, Coordinate c1, Geometry boundary, double tol) throws Exception {
        Envelope env = boundary.getEnvelopeInternal();
        double length = Math.max(env.getWidth(), env.getHeight());
        double segmentLength = c0.distance(c1);
        Coordinate c2 = new Coordinate(
                c1.x + (c1.x-c0.x)*length/segmentLength,
                c1.y + (c1.y-c0.y)*length/segmentLength);
        Geometry intersection = boundary.getFactory().createLineString(new Coordinate[]{c1, c2}).intersection(boundary);
        if (intersection.isEmpty()) {
            throw new Exception("Extension of segment " + c0 + " - " + c1 + " does not intersect the geometry boundary");
        }
        Coordinate c3 = intersection.getCoordinate();
        if (intersection.getNumPoints() > 1) {
            double min = c1.distance(c3);
            for (Coordinate c : intersection.getCoordinates()) {
                double d = c1.distance(c);
                if (d < min) {
                    min = d;
                    c3 = c;
                }
            }
        }
        return snap(c3, boundary.getCoordinates(), tol);
    }

    // Snap c to one of cc if one of cc is close enough
    private Coordinate snap(Coordinate c, Coordinate[] cc, double tol) {
        double max2 = tol*tol;
        Coordinate result = c;
        for (Coordinate v : cc) {
            double d2 = (c.x-v.x)*(c.x-v.x) + (c.y-v.y)*(c.y-v.y);
            if (d2 < max2) {
                max2 = d2;
                result = v;
            }
        }
        return result;
    }

    private Geometry graph2geometry(Pseudograph<INode,FeatureAsEdge> graph) {
        Collection<Geometry> lines = new ArrayList<>();
        for (FeatureAsEdge f : graph.edgeSet()) {
            lines.add(f.getGeometry());
        }
        LineMerger merger = new LineMerger();
        merger.add(lines);
        lines = merger.getMergedLineStrings();
        GeometryFactory gf = lines.size()>0 ?
                lines.iterator().next().getFactory() : new GeometryFactory();
        return gf.buildGeometry(lines);
    }


    private WeightedPseudograph<INode,FeatureAsEdge> getGraph(Collection<LineString> edges) {
        // Build the graph (with jgrapht library)
        FeatureSchema schema = new FeatureSchema();
        schema.addAttribute("geometry", AttributeType.GEOMETRY);
        List<Feature> features = new ArrayList<>();
        for (LineString line : edges) {
            Feature feature = new BasicFeature(schema);
            feature.setGeometry(line);
            features.add(feature);
        }
        return GraphFactory.createUndirectedGraph(features, false);
    }



    // Main method for simplification of the skeleton
    private Pseudograph<INode,FeatureAsEdge> simplify(
            Pseudograph<INode,FeatureAsEdge> graph, Geometry boundary, boolean iterative) {
        if (graph.edgeSet().size()==1) return graph;
        Set<FeatureAsEdge> featuresToremove = new HashSet<>();
        // Traverse all nodes but skip nodes with at least two incident edges linked
        // to another edges
        for (INode node : graph.vertexSet()) {
            if (graph.degreeOf(node) > 2) { // skip nodes of degree 1
                FeatureAsEdge candidateForRemoval = null;
                // We'll analyze terminal segments and try to eliminate the one with the worst coefficient
                double worst = 0;
                // We need to count nonTerminal adjacent segments to skip nodes
                // located in the middle of the graph
                int nonTerminalSegmentNumber = 0;
                // Iterates through incident edges
                for (FeatureAsEdge e : graph.edgesOf(node)) {
                    EdgeNodes nodes = new EdgeNodes(graph, e);
                    if (nodes.nbOfDegreeN()==2) nonTerminalSegmentNumber++;
                    if (nonTerminalSegmentNumber>1 && iterative) break;
                    if (nodes.nbOfDegree1() != 1) continue;
                    if (e.getGeometry().getLength() > getMinForkLength()) continue;
                    PointPairDistance ppd = new PointPairDistance();
                    DistanceToPoint.computeDistance(boundary, nodes.getDegree1().getCoordinate(), ppd);
                    double coeff = ppd.getDistance() / e.getGeometry().getLength();
                    if (coeff > worst) {
                        worst = coeff;
                        candidateForRemoval = e;
                    }
                }
                if (candidateForRemoval != null && (!iterative || nonTerminalSegmentNumber < 2)) {
                    featuresToremove.add(candidateForRemoval);
                    //debugGeometries.add(candidateForRemoval.getGeometry());
                }
            }
        }
        graph.removeAllEdges(featuresToremove);

        // merge lines after simplification
        Geometry geom = graph2geometry(graph);
        List<LineString> edges = LineStringExtracter.getLines(geom);
        LineMerger merger = new LineMerger();
        merger.add(edges);
        edges = (List<LineString>)merger.getMergedLineStrings();
        return getGraph(edges);
    }

    // A class wrapping both nodes of a edge with their degree
    public static class EdgeNodes {
        INode src;
        INode tgt;
        int srcDegree;
        int tgtDegree;
        EdgeNodes(Pseudograph<INode,FeatureAsEdge> graph, FeatureAsEdge edge) {
            src = graph.getEdgeSource(edge);
            tgt = graph.getEdgeTarget(edge);
            srcDegree = graph.degreeOf(src);
            tgtDegree = graph.degreeOf(tgt);
        }
        int nbOfDegree1() {
            return (srcDegree==1?1:0) + (tgtDegree==1?1:0);
        }
        INode getDegree1() {
            if (srcDegree == 1) return src;
            else if (tgtDegree == 1) return tgt;
            else return null;
        }
        int nbOfDegreeN() {
            return (srcDegree>1?1:0) + (tgtDegree>1?1:0);
        }
    }

}
