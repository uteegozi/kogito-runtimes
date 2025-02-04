/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
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
package org.kie.kogito.codegen.core.events;

import org.kie.kogito.codegen.api.template.TemplatedGenerator;

public abstract class AbstractEventResourceGenerator {

    public static final String TEMPLATE_EVENT_FOLDER = "/class-templates/events/";
    protected TemplatedGenerator generator;

    protected AbstractEventResourceGenerator(TemplatedGenerator generator) {
        this.generator = generator;
    }

    public final String getClassName() {
        return generator.targetTypeName();
    }

    public final String generatedFilePath() {
        return generator.generatedFilePath();
    }

    public abstract String generate();
}
