package brooklyn.rest.core;

import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.geo.HostGeoInfo;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.rest.mock.RestMockApp;
import brooklyn.rest.mock.RestMockSimpleEntity;

public class EntityLocationUtilsTest {

    private static final Logger log = LoggerFactory.getLogger(EntityLocationUtilsTest.class);
    
    @Test
    public void testCount() {
        ManagementContext mgmt = new LocalManagementContext();
        RestMockApp app = new RestMockApp();
        @SuppressWarnings("unused")
        RestMockSimpleEntity r1 = new RestMockSimpleEntity(app);
        RestMockSimpleEntity r2 = new RestMockSimpleEntity(app);
        mgmt.manage(app);
        AbstractLocation l0 = new LocalhostMachineProvisioningLocation();
        l0.setHostGeoInfo(new HostGeoInfo("localhost", "localhost", 50, 0));
        
        Entities.start(app, Arrays.<Location>asList(l0));
        
        Entities.dumpInfo(app);
        
        log.info("r2loc: "+r2.getLocations());
        log.info("props: "+r2.getLocations().iterator().next().getLocationProperties());
        
        Map<Location, Integer> counts = new EntityLocationUtils(mgmt).countLeafEntitiesByLocatedLocations();
        log.info("count: "+counts);
        Assert.assertEquals(counts.size(), 1);
        Assert.assertEquals((int)counts.values().iterator().next(), 2);
        
    }
    
}
