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

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.jboss.logging.Logger;
import org.kie.kogito.codegen.api.GeneratedFile;
import org.kie.kogito.codegen.api.GeneratedFileType;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.core.utils.ApplicationGeneratorDiscovery;

import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ArchiveRootBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.index.IndexingUtil;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.maven.dependency.ResolvedDependency;

import static org.kie.kogito.quarkus.common.deployment.KogitoQuarkusResourceUtils.*;

/**
 * Main class of the Kogito extension
 */
public class KogitoAssetsProcessor {

    private static final Logger LOGGER = Logger.getLogger(KogitoAssetsProcessor.class);

    @Inject
    ArchiveRootBuildItem root;
    @Inject
    LiveReloadBuildItem liveReload;
    @Inject
    CurateOutcomeBuildItem curateOutcomeBuildItem;
    @Inject
    CombinedIndexBuildItem combinedIndexBuildItem;

    /**
     * Main entry point of the Quarkus extension
     */
    @BuildStep
    public List<KogitoGeneratedClassesBuildItem> generateModel(
            Capabilities capabilities,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans,
            BuildProducer<NativeImageResourceBuildItem> resource,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<GeneratedResourceBuildItem> genResBI) throws IOException {

        // configure the application generator
        KogitoBuildContext context = kogitoBuildContext(root.getPaths(), combinedIndexBuildItem.getIndex(), curateOutcomeBuildItem.getApplicationModel().getAppArtifact());

        validateAvailableCapabilities(context, capabilities);

        Collection<GeneratedFile> generatedFiles = generateFiles(context);

        // The HotReloadSupportClass has to be generated only during the first model generation
        // During actual hot reloads it will be regenrated by the compilation providers in order to retrigger this build step
        if (!liveReload.isLiveReload()) {
            generatedFiles.add(new GeneratedFile(GeneratedFileType.SOURCE, HOT_RELOAD_SUPPORT_PATH + ".java", getHotReloadSupportSource()));
        }

        // dump files to disk
        dumpFilesToDisk(context.getAppPaths(), generatedFiles);

        // build Java source code and register the generated beans
        Optional<KogitoGeneratedClassesBuildItem> optionalIndex = compileAndIndexJavaSources(
                context,
                generatedFiles,
                generatedBeans,
                liveReload.isLiveReload());

        registerDataEventsForReflection(optionalIndex.map(KogitoGeneratedClassesBuildItem::getIndexedClasses), context, reflectiveClass);

        registerResources(generatedFiles, resource, genResBI);

        return optionalIndex
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    void validateAvailableCapabilities(KogitoBuildContext context, Capabilities capabilities) {
        boolean hasOptaPlannerCapability = capabilities.isCapabilityWithPrefixPresent("org.optaplanner");
        boolean hasRestCapabilities = capabilities.isPresent(Capability.RESTEASY) && capabilities.isPresent(Capability.RESTEASY_JSON_JACKSON);

        // disable REST if OptaPlanner capability is available but REST is not (user can override via property)
        if (hasOptaPlannerCapability && !hasRestCapabilities &&
                kogitoGenerateRest(context).isEmpty()) {
            context.setApplicationProperty(KogitoBuildContext.KOGITO_GENERATE_REST, "false");
            LOGGER.info("Disabling Kogito REST generation because OptaPlanner extension is available, specify `kogito.generate.rest = true` to re-enable it");
        }

        if (!hasRestCapabilities && kogitoGenerateRest(context).orElse(true)) {
            throw new MissingRestCapabilityException();
        }
    }

    private Optional<Boolean> kogitoGenerateRest(KogitoBuildContext context) {
        return context.getApplicationProperty(KogitoBuildContext.KOGITO_GENERATE_REST)
                .map("true"::equalsIgnoreCase);
    }

    private Collection<GeneratedFile> generateFiles(KogitoBuildContext context) {
        return ApplicationGeneratorDiscovery
                .discover(context)
                .generate();
    }

    private Optional<KogitoGeneratedClassesBuildItem> compileAndIndexJavaSources(
            KogitoBuildContext context,
            Collection<GeneratedFile> generatedFiles,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans,
            boolean useDebugSymbols) throws IOException {

        Collection<ResolvedDependency> dependencies = curateOutcomeBuildItem.getApplicationModel().getRuntimeDependencies();

        Collection<GeneratedBeanBuildItem> generatedBeanBuildItems =
                compileGeneratedSources(context, dependencies, generatedFiles, useDebugSymbols);
        generatedBeanBuildItems.forEach(generatedBeans::produce);
        return Optional.of(indexBuildItems(context, generatedBeanBuildItems));
    }

    private void registerDataEventsForReflection(Optional<IndexView> optionalIndex, KogitoBuildContext context, BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.event.AbstractDataEvent"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.services.event.AbstractProcessDataEvent"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.services.event.ProcessInstanceDataEvent"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.services.event.VariableInstanceDataEvent"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.services.event.impl.ProcessInstanceEventBody"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.services.event.impl.NodeInstanceEventBody"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.services.event.impl.ProcessErrorEventBody"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.services.event.impl.VariableInstanceEventBody"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.services.event.UserTaskInstanceDataEvent"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.services.event.impl.UserTaskInstanceEventBody"));
        reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, "org.kie.kogito.services.event.UserTaskDeadlineDataEvent"));
        reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, "org.kie.kogito.services.event.impl.UserTaskDeadlineEventBody"));

        if (context.getAddonsConfig().useMonitoring()) {
            registerMonitoringAddonClasses(reflectiveClass);
        }

        if (context.getAddonsConfig().useTracing()) {
            registerTracingAddonClasses(reflectiveClass);
        }

        if (context.getAddonsConfig().useExplainability()) {
            registerExplainabilityAddonClasses(reflectiveClass);
        }

        optionalIndex.ifPresent(index -> {
            // not sure there is any generated class directly inheriting from AbstractDataEvent, keeping just in case
            addChildrenClasses(index, "org.kie.kogito.event.AbstractDataEvent", reflectiveClass);
            addChildrenClasses(index, "org.kie.kogito.services.event.AbstractProcessDataEvent", reflectiveClass);
        });
    }

    private void registerMonitoringAddonClasses(BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.HdrHistogram.Histogram"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.HdrHistogram.ConcurrentHistogram"));
    }

    private void registerTracingAddonClasses(BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.decision.event.trace.TraceEvent"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.decision.event.trace.TraceHeader"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.decision.event.trace.TraceEventType"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.decision.event.trace.TraceResourceId"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.decision.event.message.Message"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.decision.event.message.MessageLevel"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.decision.event.message.MessageCategory"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.decision.event.message.MessageFEELEvent"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.decision.event.message.MessageFEELEventSeverity"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.decision.event.message.MessageExceptionField"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.decision.event.message.MessageFEELEventSeverity"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.decision.event.message.MessageFEELEventSeverity"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.decision.event.message.MessageFEELEventSeverity"));

        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.decision.event.trace.TraceInputValue"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.decision.event.trace.TraceOutputValue"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.typedvalue.BaseTypedValue"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.typedvalue.TypedValue"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.typedvalue.UnitValue"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.typedvalue.CollectionValue"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.typedvalue.StructureValue"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.decision.event.trace.TraceExecutionStep"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.decision.event.trace.TraceExecutionStepType"));

        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.tracing.decision.event.model.ModelEvent"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.KogitoGAV"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.decision.DecisionModelType"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.decision.DecisionModelMetadata"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.decision.DecisionModelMetadata$Type"));
    }

    private void registerExplainabilityAddonClasses(BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.explainability.model.PredictOutput"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.explainability.model.PredictInput"));
        reflectiveClass.produce(
                new ReflectiveClassBuildItem(true, true, "org.kie.kogito.explainability.model.ModelIdentifier"));
    }

    private void addChildrenClasses(IndexView index,
            String superClass,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        index.getAllKnownSubclasses(DotName.createSimple(superClass))
                .forEach(c -> reflectiveClass.produce(
                        new ReflectiveClassBuildItem(true, true, c.name().toString())));
    }

    private KogitoGeneratedClassesBuildItem indexBuildItems(KogitoBuildContext context, Collection<GeneratedBeanBuildItem> buildItems) {
        Indexer kogitoIndexer = new Indexer();
        Set<DotName> kogitoIndex = new HashSet<>();

        for (GeneratedBeanBuildItem generatedBeanBuildItem : buildItems) {
            IndexingUtil.indexClass(
                    generatedBeanBuildItem.getName(),
                    kogitoIndexer,
                    combinedIndexBuildItem.getIndex(),
                    kogitoIndex,
                    context.getClassLoader(),
                    generatedBeanBuildItem.getData());
        }

        Map<String, byte[]> generatedClasses = buildItems.stream().collect(Collectors.toMap(GeneratedBeanBuildItem::getName, GeneratedBeanBuildItem::getData));

        return new KogitoGeneratedClassesBuildItem(kogitoIndexer.complete(), generatedClasses);
    }
}
