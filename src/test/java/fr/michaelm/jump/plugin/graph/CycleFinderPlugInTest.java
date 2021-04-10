package fr.michaelm.jump.plugin.graph;

import com.vividsolutions.jump.feature.FeatureCollection;
import com.vividsolutions.jump.feature.FeatureDataset;
import com.vividsolutions.jump.task.DummyTaskMonitor;
import fr.michaelm.jump.feature.jgrapht.FeatureAsEdge;
import fr.michaelm.jump.feature.jgrapht.INode;
import org.jgrapht.Graph;
import org.junit.Test;

import java.util.Map;
import java.util.function.BiConsumer;

public class CycleFinderPlugInTest extends GraphToolboxTest {

  @Test
  public void test1() {
    Graph<INode, FeatureAsEdge> graph = createUndirected2dGraph();
    add2d(graph, 0, 0, 10, 10);
    add2d(graph, 10, 10, 20, 0);
    add2d(graph, 0, 0, 10, -10);
    add2d(graph, 10, -10, 20, 0);
    //LayerManager manager = new LayerManager();
    FeatureCollection fc = graph.edgeSet().stream().map(e->e.getFeature()).collect(
        () -> new FeatureDataset(SCHEMA),
        (featureCollection, feature) -> featureCollection.add(feature),
        (BiConsumer<FeatureCollection, FeatureCollection>) (fc1, fc2) -> fc1.addAll(fc2.getFeatures())
    );
    //manager.addLayer("test", layer);
    CycleFinderPlugIn pi = new CycleFinderPlugIn();
    pi.addParameter(CycleFinderPlugIn.P_DATASET_NAME,"test");
    pi.addParameter(CycleFinderPlugIn.P_DATASET,            fc);
    pi.addParameter(CycleFinderPlugIn.P_GRAPH_3D,         false);
    pi.addParameter(CycleFinderPlugIn.P_MIN_FEATURES,     2);
    pi.addParameter(CycleFinderPlugIn.P_MAX_FEATURE,      10);
    pi.addParameter(CycleFinderPlugIn.P_MAX_LENGTH,       100.0);
    pi.addParameter(CycleFinderPlugIn.P_DEGREE_FILTER,    1);
    pi.addParameter(CycleFinderPlugIn.P_ATTRIBUTE,        null);
    pi.addParameter(CycleFinderPlugIn.P_IGNORE_EMPTY,     false);
    pi.addParameter(CycleFinderPlugIn.P_FIND_ALL_HETEROGENEOUS,  true);
    pi.addParameter(CycleFinderPlugIn.P_FIND_AAB_ABB,     false);
    Map<String,FeatureCollection> map = pi.run(new DummyTaskMonitor());
    for (Map.Entry<String,FeatureCollection> e : map.entrySet()) {
      System.out.println(e.getKey());
      System.out.println(e.getValue().getFeatures());
    }
  }

}
