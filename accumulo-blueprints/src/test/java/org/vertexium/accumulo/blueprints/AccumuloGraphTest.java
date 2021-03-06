package org.vertexium.accumulo.blueprints;

import org.vertexium.accumulo.blueprints.util.AccumuloBlueprintsGraphTestHelper;
import org.vertexium.blueprints.VertexiumBlueprintsGraphTestBase;

public class AccumuloGraphTest extends VertexiumBlueprintsGraphTestBase {
    public AccumuloGraphTest() {
        super(new AccumuloBlueprintsGraphTestHelper());
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ((AccumuloBlueprintsGraphTestHelper) this.graphTest).setUp();
    }
}
