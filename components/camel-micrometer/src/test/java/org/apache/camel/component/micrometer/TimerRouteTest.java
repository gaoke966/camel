/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.micrometer;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.javaconfig.SingleRouteCamelConfiguration;
import org.apache.camel.test.spring.CamelSpringDelegatingTestContextLoader;
import org.apache.camel.test.spring.CamelSpringRunner;
import org.apache.camel.test.spring.MockEndpoints;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import java.util.concurrent.TimeUnit;

import static org.apache.camel.component.micrometer.MicrometerComponent.METRICS_REGISTRY;
import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_METRIC_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_TIMER_ACTION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

@RunWith(CamelSpringRunner.class)
@ContextConfiguration(
        classes = { TimerRouteTest.TestConfig.class },
        loader = CamelSpringDelegatingTestContextLoader.class)
@MockEndpoints
public class TimerRouteTest {

    @EndpointInject(uri = "mock:out")
    private MockEndpoint endpoint;

    @Produce(uri = "direct:in-1")
    private ProducerTemplate producer1;

    @Produce(uri = "direct:in-2")
    private ProducerTemplate producer2;

    @Produce(uri = "direct:in-3")
    private ProducerTemplate producer3;

    private MeterRegistry registry;

    @Configuration
    public static class TestConfig extends SingleRouteCamelConfiguration {

        @Bean
        @Override
        public RouteBuilder route() {
            return new RouteBuilder() {

                @Override
                public void configure() throws Exception {
                    from("direct:in-1")
                            .setHeader(HEADER_METRIC_NAME, constant("B"))
                            .to("micrometer:timer:A?action=start")
                            .delay(100L)
                            .setHeader(HEADER_METRIC_NAME, constant("B"))
                            .to("micrometer:timer:A?action=stop")
                            .to("mock:out");

                    from("direct:in-2")
                            .setHeader(HEADER_TIMER_ACTION, constant(MicrometerTimerAction.start))
                            .to("micrometer:timer:A")
                            .delay(100L)
                            .setHeader(HEADER_TIMER_ACTION, constant(MicrometerTimerAction.stop))
                            .to("micrometer:timer:A")
                            .to("mock:out");

                    from("direct:in-3")
                            .to("micrometer:timer:C?action=start")
                            .delay(100L)
                            .to("micrometer:timer:C?action=stop")
                            .to("mock:out");
                }
            };
        }

        @Bean(name = METRICS_REGISTRY)
        public MeterRegistry getMetricRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Before
    public void setup() {
        registry = endpoint.getCamelContext().getRegistry().lookupByNameAndType(METRICS_REGISTRY, MeterRegistry.class);
    }

    @After
    public void tearDown() {
        endpoint.reset();
    }

    @Test
    public void testOverrideMetricsName() throws Exception {
        Object body = new Object();
        endpoint.expectedBodiesReceived(body);
        producer1.sendBody(body);
        Timer timer = registry.find("B").timer();
        assertEquals(1L, timer.count());
        assertTrue(timer.max(TimeUnit.MILLISECONDS) > 0.0D);
        endpoint.assertIsSatisfied();
    }


    @Test
    public void testOverrideNoAction() throws Exception {
        Object body = new Object();
        endpoint.expectedBodiesReceived(body);
        producer2.sendBody(body);
        Timer timer = registry.find(MicrometerConstants.HEADER_PREFIX + "." + "A").timer();
        assertEquals(1L, timer.count());
        assertTrue(timer.max(TimeUnit.MILLISECONDS) > 0.0D);
        endpoint.assertIsSatisfied();
    }

    @Test
    public void testNormal() throws Exception {
        Object body = new Object();
        endpoint.expectedBodiesReceived(body);
        producer3.sendBody(body);
        Timer timer = registry.find(MicrometerConstants.HEADER_PREFIX + "." + "A").timer();
        assertEquals(1L, timer.count());
        assertTrue(timer.max(TimeUnit.MILLISECONDS) > 0.0D);
        endpoint.assertIsSatisfied();
    }
}