/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.kogito.serverless.workflow;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.kie.kogito.serverless.workflow.parser.util.ServerlessWorkflowUtils;
import org.kie.kogito.serverless.workflow.parser.util.WorkflowAppContext;

import io.serverlessworkflow.api.Workflow;
import io.serverlessworkflow.api.events.EventDefinition;
import io.serverlessworkflow.api.functions.FunctionDefinition;
import io.serverlessworkflow.api.mapper.BaseObjectMapper;
import io.serverlessworkflow.api.mapper.JsonObjectMapper;
import io.serverlessworkflow.api.mapper.YamlObjectMapper;
import io.serverlessworkflow.api.states.DefaultState;
import io.serverlessworkflow.api.workflow.Events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class WorkflowUtilsTest {

    private static final Workflow eventDefOnlyWorkflow = new Workflow().withEvents(
            new Events(Collections.singletonList(new EventDefinition().withName("sampleEvent").withSource("sampleSource").withType("sampleType"))));

    @Test
    public void testGetObjectMapper() {
        BaseObjectMapper objectMapper = ServerlessWorkflowUtils.getObjectMapper("json");
        assertNotNull(objectMapper);
        assertThat(objectMapper).isInstanceOf(JsonObjectMapper.class);

        objectMapper = ServerlessWorkflowUtils.getObjectMapper("yml");
        assertNotNull(objectMapper);
        assertThat(objectMapper).isInstanceOf(YamlObjectMapper.class);

        assertThrows(IllegalArgumentException.class, () -> ServerlessWorkflowUtils.getObjectMapper("unsupported"));

        assertThrows(IllegalArgumentException.class, () -> ServerlessWorkflowUtils.getObjectMapper(null));
    }

    @Test
    public void testGetWorkflowEventFor() {
        assertThat(ServerlessWorkflowUtils.getWorkflowEventFor(eventDefOnlyWorkflow, "sampleEvent")).isNotNull();
        assertThat(ServerlessWorkflowUtils.getWorkflowEventFor(eventDefOnlyWorkflow, "sampleEvent")).isInstanceOf(EventDefinition.class);
    }

    @Test
    public void testResolveFunctionMetadata() {
        FunctionDefinition function = new FunctionDefinition().withName("testfunction1").withMetadata(Collections.singletonMap("testprop1", "customtestprop1val"));
        String testProp1Val = ServerlessWorkflowUtils.resolveFunctionMetadata(function, "testprop1",
                WorkflowAppContext.ofAppResources());
        assertThat(testProp1Val).isNotNull().isEqualTo("customtestprop1val");

        String testProp2Val = ServerlessWorkflowUtils.resolveFunctionMetadata(function, "testprop2",
                WorkflowAppContext.ofAppResources());
        assertThat(testProp2Val).isNotNull().isEqualTo("testprop2val");
    }

    @Test
    public void testResolveEvenDefinitiontMetadata() {
        EventDefinition eventDefinition = new EventDefinition().withName("testevent1").withMetadata(Collections.singletonMap("testprop1", "customtestprop1val"));

        String testProp1Val = ServerlessWorkflowUtils.resolveEvenDefinitiontMetadata(eventDefinition, "testprop1",
                WorkflowAppContext.ofAppResources());
        assertThat(testProp1Val).isNotNull().isEqualTo("customtestprop1val");

        String testProp2Val = ServerlessWorkflowUtils.resolveEvenDefinitiontMetadata(eventDefinition, "testprop2",
                WorkflowAppContext.ofAppResources());
        assertThat(testProp2Val).isNotNull().isEqualTo("testprop2val");
    }

    @Test
    public void testResolveStatetMetadata() {
        DefaultState defaultState = new DefaultState().withName("teststate1").withMetadata(Collections.singletonMap("testprop1", "customtestprop1val"));

        String testProp1Val = ServerlessWorkflowUtils.resolveStatetMetadata(defaultState, "testprop1",
                WorkflowAppContext.ofAppResources());
        assertThat(testProp1Val).isNotNull().isEqualTo("customtestprop1val");

        String testProp2Val = ServerlessWorkflowUtils.resolveStatetMetadata(defaultState, "testprop2",
                WorkflowAppContext.ofAppResources());
        assertThat(testProp2Val).isNotNull().isEqualTo("testprop2val");
    }

    @Test
    public void testResolveWorkflowMetadata() {
        Workflow workflow = new Workflow().withId("workflowid1").withMetadata(Collections.singletonMap("testprop1", "customtestprop1val"));

        String testProp1Val = ServerlessWorkflowUtils.resolveWorkflowMetadata(workflow, "testprop1",
                WorkflowAppContext.ofAppResources());
        assertThat(testProp1Val).isNotNull().isEqualTo("customtestprop1val");

        String testProp2Val = ServerlessWorkflowUtils.resolveWorkflowMetadata(workflow, "testprop2",
                WorkflowAppContext.ofAppResources());
        assertThat(testProp2Val).isNotNull().isEqualTo("testprop2val");
    }
}
