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

package org.kie.kogito.quarkus.common.deployment;

public class MissingRestCapabilityException extends RuntimeException {
    public MissingRestCapabilityException() {
        super("No REST capability detected! \n" +
                "Add the RestEasy (quarkus-resteasy) and RestEasy Jackson (quarkus-resteasy-jackson) extensions if you want " +
                "Kogito to generate REST endpoints automatically. \n" +
                "RestEasy Reactive extension is currently not supported: see https://issues.redhat.com/browse/KOGITO-6131 \n" +
                "You may also disable automated REST generation by setting `kogito.generate.rest = false`. \n" +
                "You may also override this notice by setting `kogito.generate.rest = true` ");
    }
}
