// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.osm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openstreetmap.josm.TestUtils.getPrivateField;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Unit tests of {@link QuadBuckets}.
 */
class QuadBucketsTest {

    /**
     * Setup test.
     */
    @RegisterExtension
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules();

    @SuppressWarnings("unchecked")
    private void removeAllTest(DataSet ds) throws ReflectiveOperationException {
        List<Node> allNodes = new ArrayList<>(ds.getNodes());
        List<Way> allWays = new ArrayList<>(ds.getWays());
        List<Relation> allRelations = new ArrayList<>(ds.getRelations());

        QuadBucketPrimitiveStore<Node, Way, Relation> s = (QuadBucketPrimitiveStore<Node, Way, Relation>) getPrivateField(ds, "store");
        QuadBuckets<Node> nodes = (QuadBuckets<Node>) getPrivateField(s, "nodes");
        QuadBuckets<Way> ways = (QuadBuckets<Way>) getPrivateField(s, "ways");
        Collection<Relation> relations = (Collection<Relation>) getPrivateField(s, "relations");

        int expectedCount = allNodes.size();
        for (OsmPrimitive o: allNodes) {
            ds.removePrimitive(o);
            checkIterator(nodes, --expectedCount);
        }
        expectedCount = allWays.size();
        for (OsmPrimitive o: allWays) {
            ds.removePrimitive(o);
            checkIterator(ways, --expectedCount);
        }
        for (OsmPrimitive o: allRelations) {
            ds.removePrimitive(o);
        }
        assertTrue(nodes.isEmpty());
        assertTrue(ways.isEmpty());
        assertTrue(relations.isEmpty());
    }

    private void checkIterator(Iterable<? extends OsmPrimitive> col, int expectedCount) {
        int count = 0;
        for (OsmPrimitive ignored : col) {
            count++;
        }
        assertEquals(expectedCount, count);
    }

    /**
     * Test that all primitives can be removed from the Quad Buckets.
     * @throws Exception never
     */
    @Test
    void testRemove() throws Exception {
        ProjectionRegistry.setProjection(Projections.getProjectionByCode("EPSG:3857")); // Mercator
        try (InputStream fis = Files.newInputStream(Paths.get("nodist/data/restriction.osm"))) {
            DataSet ds = OsmReader.parseDataSet(fis, NullProgressMonitor.INSTANCE);
            removeAllTest(ds);
        }
    }

    /**
     * Test that all primitives can be removed from the Quad Buckets, even if moved before.
     * @throws Exception never
     */
    @Test
    void testMove() throws Exception {
        ProjectionRegistry.setProjection(Projections.getProjectionByCode("EPSG:3857")); // Mercator
        try (InputStream fis = Files.newInputStream(Paths.get("nodist/data/restriction.osm"))) {
            DataSet ds = OsmReader.parseDataSet(fis, NullProgressMonitor.INSTANCE);

            for (Node n: ds.getNodes()) {
                n.setCoor(new LatLon(10, 10));
            }

            removeAllTest(ds);
        }
    }

    /**
     * Test handling of objects with invalid bbox
     */
    @Test
    void testSpecialBBox() {
        QuadBuckets<Node> qbNodes = new QuadBuckets<>();
        QuadBuckets<Way> qbWays = new QuadBuckets<>();
        Way w1 = new Way(1);
        Way w2 = new Way(2);
        Way w3 = new Way(3);
        Node n1 = new Node(1);
        Node n2 = new Node(2); n2.setCoor(new LatLon(10, 20));
        Node n3 = new Node(3); n2.setCoor(new LatLon(20, 30));
        w2.setNodes(Collections.singletonList(n1));
        w3.setNodes(Arrays.asList(n1, n2, n3));

        qbNodes.add(n1);
        qbNodes.add(n2);
        assertEquals(2, qbNodes.size());
        assertTrue(qbNodes.contains(n1));
        assertTrue(qbNodes.contains(n2));
        assertFalse(qbNodes.contains(n3));
        qbNodes.remove(n1);
        assertEquals(1, qbNodes.size());
        assertFalse(qbNodes.contains(n1));
        assertTrue(qbNodes.contains(n2));
        qbNodes.remove(n2);
        assertEquals(0, qbNodes.size());
        assertFalse(qbNodes.contains(n1));
        assertFalse(qbNodes.contains(n2));

        qbNodes.addAll(Arrays.asList(n1, n2, n3));
        qbNodes.removeAll(Arrays.asList(n1, n3));
        assertEquals(1, qbNodes.size());
        assertTrue(qbNodes.contains(n2));

        qbWays.add(w1);
        qbWays.add(w2);
        qbWays.add(w3);
        assertEquals(3, qbWays.size());
        assertTrue(qbWays.contains(w1));
        assertTrue(qbWays.contains(w2));
        assertTrue(qbWays.contains(w3));
        qbWays.remove(w1);
        assertEquals(2, qbWays.size());
        assertFalse(qbWays.contains(w1));
        assertTrue(qbWays.contains(w2));
        assertTrue(qbWays.contains(w3));
        qbWays.remove(w2);
        assertEquals(1, qbWays.size());
        assertFalse(qbWays.contains(w1));
        assertFalse(qbWays.contains(w2));
        assertTrue(qbWays.contains(w3));
        qbWays.remove(w3);
        assertEquals(0, qbWays.size());
        assertFalse(qbWays.contains(w1));
        assertFalse(qbWays.contains(w2));
        assertFalse(qbWays.contains(w3));

        qbWays.clear();
        assertEquals(0, qbWays.size());
        List<Way> allWays = new ArrayList<>(Arrays.asList(w1, w2, w3));
        qbWays.addAll(allWays);
        assertEquals(3, qbWays.size());
        int count = 0;
        for (Way w : qbWays) {
            assertTrue(allWays.contains(w));
            count++;
        }
        assertEquals(3, count);
        // test remove with iterator
        Iterator<Way> iter = qbWays.iterator();
        while (iter.hasNext()) {
            iter.next();
            iter.remove();
            count--;
            assertEquals(count, qbWays.size());
        }
        assertEquals(0, qbWays.size());

    }

    /**
     *  Add more data so that quad buckets tree has a few leaves
     */
    @Test
    void testSplitsWithIncompleteData() {
        DataSet ds = new DataSet();
        long nodeId = 1;
        long wayId = 1;
        final int NUM_COMPLETE_WAYS = 300;
        final int NUM_INCOMPLETE_WAYS = 10;
        final int NUM_NODES_PER_WAY = 20;
        final int NUM_INCOMPLETE_NODES = 10;

        // force splits in quad buckets
        Random random = new SecureRandom();
        for (int i = 0; i < NUM_COMPLETE_WAYS; i++) {
            Way w = new Way(wayId++);
            List<Node> nodes = new ArrayList<>();
            double center = random.nextDouble() * 10;
            for (int j = 0; j < NUM_NODES_PER_WAY; j++) {
                Node n = new Node(nodeId++);
                double lat = random.nextDouble() * 0.001;
                double lon = random.nextDouble() * 0.001;
                n.setCoor(new LatLon(center + lat, center + lon));
                nodes.add(n);
                ds.addPrimitive(n);
            }
            w.setNodes(nodes);
            ds.addPrimitive(w);
        }
        assertEquals(NUM_COMPLETE_WAYS, ds.getWays().size());
        assertEquals(NUM_COMPLETE_WAYS * NUM_NODES_PER_WAY, ds.getNodes().size());

        // add some incomplete nodes
        for (int i = 0; i < NUM_INCOMPLETE_NODES; i++) {
            Node n = new Node(nodeId++);
            n.setIncomplete(true);
            ds.addPrimitive(n);
        }
        assertEquals(NUM_COMPLETE_WAYS * NUM_NODES_PER_WAY + NUM_INCOMPLETE_NODES, ds.getNodes().size());
        // add some incomplete ways
        List<Way> incompleteWays = new ArrayList<>();
        for (int i = 0; i < NUM_INCOMPLETE_WAYS; i++) {
            Way w = new Way(wayId++);
            incompleteWays.add(w);
            w.setIncomplete(true);
            ds.addPrimitive(w);
        }
        assertEquals(NUM_COMPLETE_WAYS + NUM_INCOMPLETE_WAYS, ds.getWays().size());

        BBox planet = new BBox(-180, -90, 180, 90);
        // incomplete ways should not be found with search
        assertEquals(NUM_COMPLETE_WAYS, ds.searchWays(planet).size());
        // incomplete ways are only retrieved via iterator or object reference
        for (Way w : incompleteWays) {
            assertTrue(ds.getWays().contains(w));
        }

        QuadBuckets<Way> qb = new QuadBuckets<>();
        qb.addAll(ds.getWays());
        int count = qb.size();
        assertEquals(count, ds.getWays().size());
        Iterator<Way> iter = qb.iterator();
        while (iter.hasNext()) {
            iter.next();
            iter.remove();
            count--;
            assertEquals(count, qb.size());
        }
        assertEquals(0, qb.size());
    }
}
