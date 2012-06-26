package brooklyn.enricher;

import java.util.Arrays;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.enricher.basic.AbstractCombiningEnricher;
import brooklyn.entity.LocallyManagedEntity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.TestUtils;
import brooklyn.util.MutableMap;

public class CombiningEnricherTest {

    public static final Logger log = LoggerFactory.getLogger(CombiningEnricherTest.class);
            
    private static final long TIMEOUT_MS = 10*1000;
//    private static final long SHORT_WAIT_MS = 250;
    
    AbstractApplication app;

    EntityLocal producer;
    AttributeSensor<Integer> intSensorA, intSensorB, intSensorC;
    AttributeSensor<Long> target;

    @BeforeMethod()
    public void before() {
        app = new AbstractApplication() {};
        producer = new LocallyManagedEntity(app);
        intSensorA = new BasicAttributeSensor<Integer>(Integer.class, "int.sensor.a");
        intSensorB = new BasicAttributeSensor<Integer>(Integer.class, "int.sensor.b");
        intSensorC = new BasicAttributeSensor<Integer>(Integer.class, "int.sensor.c");
        target = new BasicAttributeSensor<Long>(Long.class, "long.sensor.target");
        
        app.start(Arrays.asList(new SimulatedLocation()));
    }
    
    @AfterMethod(alwaysRun=true)
    public void after() {
        if (app!=null) app.stop();
    }
    
    @Test
    public void testEnrichersWithNoProducers() throws InterruptedException {
        final AbstractCombiningEnricher e1 = new AbstractCombiningEnricher<Long>(target) {
            int a, b, c;
            { 
                subscribe("a", intSensorA); 
                subscribe("b", intSensorB); 
                subscribe("c", intSensorC); 
            }
            public Long compute() {
                return (long)a+b+c;
            }
        };
        
        producer.setAttribute(intSensorA, 1);
        //ensure previous values get picked up
        producer.addEnricher(e1);
        producer.setAttribute(intSensorB, 2);
        Thread.sleep(10);
        Assert.assertEquals(producer.getAttribute(target), null);
        
        producer.setAttribute(intSensorC, 3);

        TestUtils.assertEventually(MutableMap.of("timeout", TIMEOUT_MS), 
                new Callable<Object>() { public Object call() {
                    Assert.assertEquals(producer.getAttribute(target), (Long)((long)6));
                    return null;
                }});

    }
}
