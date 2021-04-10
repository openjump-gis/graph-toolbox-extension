package fr.michaelm.jump.plugin.graph;

import com.vividsolutions.jump.feature.BasicFeature;
import com.vividsolutions.jump.feature.Feature;
import com.vividsolutions.jump.feature.FeatureSchema;
import com.vividsolutions.jump.feature.AttributeType;
import fr.michaelm.jump.feature.jgrapht.*;
import org.jgrapht.Graph;
import org.junit.Assert;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import java.io.*;
import java.util.*;

public class GraphToolboxTest {

    static final Random RDM = new Random();
    static final GeometryFactory FACTORY = new GeometryFactory();
    static final String[] RANDCHAR = {"A","B","C","D","E","F"};
    static final WKTReader WKT_READER = new WKTReader();
    static final FeatureSchema SCHEMA = new FeatureSchema();
    static final String NAME = "name";
    static {
        SCHEMA.addAttribute("geometry", AttributeType.GEOMETRY);
        SCHEMA.addAttribute(NAME, AttributeType.STRING);
    }

    /**
     * Creates an undirected 2D graph where each edge is a simple segment
     * made of two points
     */
    public static Graph<INode, FeatureAsEdge> createUndirected2dGraph(double...coords) {
        return GraphFactory.createUndirectedGraph(Collections.emptyList(), false);
    }

    /**
     * Creates an undirected 3D graph where each edge is a simple segment
     * made of two points
     */
    public static Graph<INode, FeatureAsEdge> createUndirected3dGraph(double...coords) {
        return GraphFactory.createUndirectedGraph(Collections.emptyList(), true);
    }

    /**
     * Creates a directed 2D graph where each edge is a simple segment
     * made of two points
     */
    public static Graph<INode, FeatureAsEdge> createDirected2dGraph(double...coords) {
        return GraphFactory.createDirectedGraph(Collections.emptyList(), false);
    }

    /**
     * Creates a directed 3D graph where each edge is a simple segment
     * made of two points
     */
    public static Graph<INode, FeatureAsEdge> createDirected3dGraph(double...coords) {
        return GraphFactory.createDirectedGraph(Collections.emptyList(), true);
    }

    static Graph<INode, FeatureAsEdge> add2d(Graph<INode, FeatureAsEdge> graph,
                                                   double x1, double y1, double x2, double y2) {
        Coordinate c1 = new Coordinate(x1, y1);
        Coordinate c2 = new Coordinate(x2, y2);
        INode n1 = new Node2D(c1);
        INode n2 = new Node2D(c2);
        Feature f = new BasicFeature(SCHEMA);
        f.setGeometry(FACTORY.createLineString(new Coordinate[]{c1, c2}));
        graph.addVertex(n1);
        graph.addVertex(n2);
        graph.addEdge(n1, n2, new FeatureAsEdge(f));
        return graph;
    }

    static Graph<INode, FeatureAsEdge> add3d(Graph<INode, FeatureAsEdge> graph,
                                           double x1, double y1, double z1, double x2, double y2, double z2) {
        Coordinate c1 = new Coordinate(x1, y1, z1);
        Coordinate c2 = new Coordinate(x2, y2, z2);
        INode n1 = new Node3D(c1);
        INode n2 = new Node3D(c2);
        Feature f = new BasicFeature(SCHEMA);
        f.setGeometry(FACTORY.createLineString(new Coordinate[]{c1, c2}));
        graph.addVertex(n1);
        graph.addVertex(n2);
        graph.addEdge(n1, n2, new FeatureAsEdge(f));
        return graph;
    }

    /**
     * Creates an undirected 3D graph where each edge is a simple segment
     * made of two points
     */
    public static Graph<INode, FeatureAsEdge> create2dUndirectedGraph(double...coords) {
        if (coords.length < 6) throw new RuntimeException("Needs at least 6 ordinates");
        Collection<Feature> features = new ArrayList<>();
        for (int i = 0 ; i < coords.length/6 ; i++) {
            LineString line = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(coords[4*i], coords[4*i+1]),
                new Coordinate(coords[4*i+2], coords[4*i+3]),
            });
            Feature f = new BasicFeature(SCHEMA);
            f.setGeometry(line);
            features.add(f);
        }
        return GraphFactory.createUndirectedGraph(features, false);
    }

    /**
     * Create a schema including a geometry attribute optional added attributes
     * defined by pair of strings : the first string is the attribute name and
     * the second the attribute type.
     * <p>Example :</p>
     * <code>createSchema("id","STRING","weight","DOUBLE")</code>
     * <p>will create a FeatureSchema made of</p>
     * <ul>
     *     <li>geometry GEOMETRY</li>
     *     <li>id STRING</li>
     *     <li>weight DOUBLE</li>
     * </ul>
     * @return a FeatureSchema
     */
    static FeatureSchema createSchema(String...args) {
        FeatureSchema schema = new FeatureSchema();
        schema.addAttribute("geometry", AttributeType.GEOMETRY);
        for (int i = 0 ; i < args.length/2 ; i++) {
            schema.addAttribute(args[i*2], AttributeType.toAttributeType(args[i*2+1].toUpperCase()));
        }
        return schema;
    }

    static LineString createLineString(Coordinate start, Coordinate end, int numberOfPoints) {
        assert numberOfPoints > 1 : "Number of points must be > 1";
        Coordinate[] array = new Coordinate[numberOfPoints];
        double dx = (end.x - start.x)/(numberOfPoints-1);
        double dy = (end.y - start.y)/(numberOfPoints-1);
        array[0] = start;
        for (int i = 1 ; i < numberOfPoints-1 ; i++) {
            double newx = start.x + dx*i + (RDM.nextDouble()-0.5)*dx;
            double newy = start.y + dy*i + (RDM.nextDouble()-0.5)*dy;
            array[i] = new Coordinate(newx, newy);
        }
        array[numberOfPoints-1] = end;
        return FACTORY.createLineString(array);
    }

    static Object getStaticValue(AttributeType type) {
        switch(type.getName()) {
            case "STRING" : return "A";
            case "BOOLEAN" : return false;
            case "INTEGER" : return 1;
            case "LONG" : return 1L;
            case "DOUBLE" : return 3.14;
            default:  return null;
        }
    }

    static Object getRandomValue(AttributeType type) {
        switch(type.getName()) {
            case "STRING" : return RANDCHAR[RDM.nextInt(RANDCHAR.length)];
            case "BOOLEAN" : return RDM.nextDouble() < 0.5;
            case "INTEGER" : return RDM.nextInt(6);
            case "LONG" : return RDM.nextLong();
            case "DOUBLE" : return RDM.nextDouble();
            default:  return null;
        }
    }

    static Collection<Feature> createConnectedFeatures(
            Coordinate start, Coordinate end, int numberOfLines) {
        return createConnectedFeatures(start, end, numberOfLines, 2, 2,
                createSchema("label","STRING"), true);
    }

    static Collection<Feature> createConnectedFeatures(Coordinate start, Coordinate end,
                                                int numberOfLines,
                                                int minPoints, int maxPoints,
                                                FeatureSchema schema, boolean homogeneous) {
        List<Feature> list = new ArrayList<>();
        double dx = (end.x - start.x)/(numberOfLines);
        double dy = (end.y - start.y)/(numberOfLines);
        double startx = start.x;
        double starty = start.y;
        for (int i = 1 ; i < numberOfLines ; i++) {
            double endx = start.x + dx*i + (RDM.nextDouble()-0.5)*dx;
            double endy = start.y + dy*i + (RDM.nextDouble()-0.5)*dy;
            LineString line = createLineString(
                    new Coordinate(startx, starty),
                    new Coordinate(endx, endy),
                    minPoints + RDM.nextInt(maxPoints+1-minPoints));
            Feature feature = new BasicFeature(schema);
            feature.setGeometry(line);
            for (String name : schema.getAttributeNames()) {
                AttributeType type = schema.getAttributeType(name);
                if (type == AttributeType.GEOMETRY) continue;
                feature.setAttribute(name, homogeneous ? getStaticValue(type) :
                        getRandomValue(type));
            }
            list.add(feature);
            startx = endx;
            starty = endy;
        }
        LineString line = createLineString(
                new Coordinate(startx, starty),
                new Coordinate(end.x, end.y),
                minPoints + RDM.nextInt(maxPoints+1-minPoints));
        Feature feature = new BasicFeature(schema);
        feature.setGeometry(line);
        for (String name : schema.getAttributeNames()) {
            AttributeType type = schema.getAttributeType(name);
            if (type == AttributeType.GEOMETRY) continue;
            feature.setAttribute(name, homogeneous ? getStaticValue(type) :
                    getRandomValue(type));
        }
        list.add(feature);
        return list;
    }

    static FeatureSchema createSchemaFromHeader(String...header) {
        FeatureSchema schema = new FeatureSchema();
        for (String h : header) {
            String hm = h.toLowerCase();
            if (hm.startsWith("geom") || hm.contains("wkt") ||
                    hm.startsWith("g_") || hm.equals("shape")) {
                schema.addAttribute(h, AttributeType.GEOMETRY);
            } else if (hm.startsWith("i_") || hm.startsWith("n_") ||
                    hm.startsWith("nb") || hm.contains("number") || hm.startsWith("#")) {
                schema.addAttribute(h, AttributeType.INTEGER);
            } else if (hm.startsWith("d_") || hm.startsWith("weight")) {
                schema.addAttribute(h, AttributeType.DOUBLE);
            } else if (hm.startsWith("b_")) {
                schema.addAttribute(h, AttributeType.BOOLEAN);
            } else {
                schema.addAttribute(h, AttributeType.STRING);
            }
        }
        return schema;
    }

    public static Collection<Feature> parseCsv(InputStream input) throws IOException, ParseException {
        List<Feature> features = new ArrayList<>();
        try (Reader reader = new InputStreamReader(input);
             BufferedReader breader = new BufferedReader(reader)) {
            String[] header = breader.readLine().split("[\t;]");
            FeatureSchema schema = createSchemaFromHeader(header);
            while (breader.ready()) {
                String[] fields = breader.readLine().split("[\t;]");
                if (fields.length > 0) {
                    Feature f = new BasicFeature(schema);
                    for (int i = 0 ; i < schema.getAttributeCount() ; i++) {
                        AttributeType type = schema.getAttributeType(i);
                        if (type == AttributeType.GEOMETRY)
                            f.setGeometry(WKT_READER.read(fields[i]));
                        else if (type == AttributeType.BOOLEAN)
                            f.setAttribute(header[i], Boolean.parseBoolean(fields[i]));
                        else if (type == AttributeType.INTEGER)
                            f.setAttribute(header[i], Integer.parseInt(fields[i]));
                        else if (type == AttributeType.DOUBLE)
                            f.setAttribute(header[i], Double.parseDouble(fields[i]));
                        else if (type == AttributeType.STRING)
                            f.setAttribute(header[i], fields[i]);
                        else
                            throw new IOException("Type " + type + " not handled");
                    }
                    features.add(f);
                }
            }
        } catch(IOException | ParseException e) {
            throw e;
        }
        return features;
    }

    private static class Link {
        String source;
        String target;
        double length = 1.0;
        String label = null;
    }

    /**
     * Returns a Coordinate which is at distance dist from A and from B
     * @param A
     * @param B
     * @param dist
     * @return
     */
    private static Coordinate equidistantPoint(Coordinate A, Coordinate B, double dist) {
        double dx = B.x-A.x;
        double dy = B.y-A.y;
        double semi = A.distance(B)/2.0;
        Coordinate midpoint = new Coordinate((A.x+B.x)/2.0, (A.y+B.y)/2.0);
        double c = Math.sqrt((dist*dist-semi*semi)/(dx*dx+dy*dy));
        return new Coordinate(midpoint.x - c*dy, midpoint.y + c*dx, (A.z+B.z)/2);
    }

    /**
     * Create a features from a file describing relations between feature nodes.
     * Coordinate of nodes are set randomly.
     * The graph format makes it possible to specify distance between nodes as well
     * as a string label.
     * Example describing a round about with a circumference of 40 m with two incident edges:
     * A -> RP1 ; 20.0 ; main
     * RP1 -> RP2 ; 10.0 ; main
     * RP2 -> RP3 ; 10.0 ; main
     * RP3 -> RP4 ; 10.0 ; main
     * RP4 -> RP1 ; 10.0 ; main
     * RP3 -> B ; 20.0 ; secondary
     * @param input
     * @return
     */
    public static Collection<Feature> parseGraph(InputStream input, boolean dim3) throws IOException {
        FeatureSchema schema = new FeatureSchema();
        schema.addAttribute("geometry", AttributeType.GEOMETRY);
        schema.addAttribute("label", AttributeType.STRING);
        List<Feature> features = new ArrayList<>();
        List<Link> links = new ArrayList<>();
        Map<String,Coordinate> nodes = new HashMap<>();
        try (Reader reader = new InputStreamReader(input);
             BufferedReader breader = new BufferedReader(reader)) {
            while (breader.ready()) {
                String[] fields = breader.readLine().split(";");
                if (fields.length > 0) {
                    Link link = new Link();
                    String[] f1 = fields[0].split("->");
                    if (f1.length == 2) {
                        nodes.put(f1[0].trim(), new Coordinate(
                                RDM.nextDouble(),
                                RDM.nextDouble(),
                                dim3? RDM.nextDouble():Double.NaN)
                        );
                        nodes.put(f1[1].trim(), new Coordinate(
                                RDM.nextDouble(),
                                RDM.nextDouble(),
                                dim3? RDM.nextDouble():Double.NaN)
                        );
                    }
                    else continue;
                    link.source = f1[0].trim();
                    link.target = f1[1].trim();
                    if (fields.length > 1 && fields[1].trim().length() > 0)
                        link.length = Double.parseDouble(fields[1].trim());
                    if (fields.length > 2 && fields[2].trim().length() > 0)
                        link.label = fields[2].trim();
                    links.add(link);
                }
            }
            for (Link link : links) {
                Feature f = new BasicFeature(schema);
                f.setGeometry(FACTORY.createLineString(new Coordinate[]{
                        nodes.get(link.source),
                        equidistantPoint(nodes.get(link.source), nodes.get(link.target), link.length),
                        nodes.get(link.target)
                }));
                f.setAttribute("label", link.label);
                features.add(f);
            }
        } catch (IOException e) {
            throw e;
        }
        return features;
    }

    public static void main(String[] args) {
        Collection<Feature> features = new GraphToolboxTest().createConnectedFeatures(
                new Coordinate(0,0), new Coordinate(1000,1000),
                10, 2, 10,
                createSchema("id", "INTEGER", "weight", "DOUBLE"), false
        );
        System.out.println(features.iterator().next().getSchema());
        for (Feature f : features) {
            System.out.println(f.getGeometry());
            //System.out.println(f.getID() + "\t" + f.getAttribute("id") + "\t" + f.getAttribute("weight"));
        }
    }

    @Test
    public void createSchema1() {
        FeatureSchema schema = createSchema("name", "STRING",
                "value", "DOUBLE", "nb", "integer", "valid", "BOOLEAN");
        Assert.assertEquals(5, schema.getAttributeCount());
        Assert.assertSame(schema.getAttributeType("name"), AttributeType.STRING);
        Assert.assertSame(schema.getAttributeType("value"), AttributeType.DOUBLE);
        Assert.assertSame(schema.getAttributeType("nb"), AttributeType.INTEGER);
        Assert.assertSame(schema.getAttributeType("valid"), AttributeType.BOOLEAN);
    }

    @Test
    public void createSchema2() {
        FeatureSchema schema = createSchema("name", "STRING", "value");
        Assert.assertEquals(2, schema.getAttributeCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createSchema3() {
        FeatureSchema schema = createSchema("name", "unknown");
        Assert.assertEquals(2, schema.getAttributeCount());
    }

    @Test
    public void createLineString() {
        for (int i = 0 ; i < 10 ; i++) {
            Coordinate A = new Coordinate(RDM.nextDouble(), RDM.nextDouble());
            Coordinate B = new Coordinate(RDM.nextDouble(), RDM.nextDouble());
            double dist = A.distance(B);
            int nb = 2 + RDM.nextInt(10);
            LineString line = createLineString(A,B,nb);
            Assert.assertEquals(nb, line.getNumPoints());
            Assert.assertEquals(A, line.getCoordinates()[0]);
            Assert.assertEquals(B, line.getCoordinates()[line.getNumPoints()-1]);
            Assert.assertTrue(line.getLength() >= dist);
            Assert.assertTrue(line.getLength() < dist*2);
        }
    }
}
