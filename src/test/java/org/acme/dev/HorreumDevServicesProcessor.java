package org.acme.dev;

import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.*;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.dev.devservices.GlobalDevServicesConfig;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;

import java.util.List;
import java.util.Optional;

@BuildSteps(onlyIfNot = IsNormal.class, onlyIf = { IsEnabled.class, GlobalDevServicesConfig.Enabled.class })
public class HorreumDevServicesProcessor {

    @BuildStep
    public DevServicesResultBuildItem startHorreumContainers(
            DockerStatusBuildItem dockerStatusBuildItem,
            BuildProducer<KeycloakDevServicesConfigBuildItem> keycloakBuildItemBuildProducer,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            KeycloakBuildTimeConfig config,
            CuratedApplicationShutdownBuildItem closeBuildItem,
            LaunchModeBuildItem launchMode,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            LoggingSetupBuildItem loggingSetupBuildItem,
            GlobalDevServicesConfig devServicesConfig) {
    }
}
