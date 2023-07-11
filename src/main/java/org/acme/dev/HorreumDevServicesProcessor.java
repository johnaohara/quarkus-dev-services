package org.acme.dev;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.jboss.logging.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.*;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.console.StartupLogCompressor;
import io.quarkus.deployment.dev.devservices.GlobalDevServicesConfig;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;
import io.quarkus.devservices.common.ConfigureUtil;
import io.quarkus.runtime.LaunchMode;
import io.vertx.core.Vertx;

//@BuildSteps(onlyIfNot = IsNormal.class, onlyIf = { HorreumDevServicesProcessor.IsEnabled.class, GlobalDevServicesConfig.Enabled.class })
@BuildSteps()
public class HorreumDevServicesProcessor {

    static volatile Vertx vertxInstance;
    private static final Logger LOG = Logger.getLogger(HorreumDevServicesProcessor.class);

    private static volatile DevServicesResultBuildItem.RunningDevService devService;

    static volatile HorreumDevServicesConfig devServicesConfiguration;

    private static final boolean keycloakX = true;
    private static final int KEYCLOAK_PORT = 8080;

    private static final String KEYCLOAK_QUARKUS_HOSTNAME = "KC_HOSTNAME";
    private static final String KEYCLOAK_QUARKUS_ADMIN_PROP = "KEYCLOAK_ADMIN";
    private static final String KEYCLOAK_QUARKUS_ADMIN_PASSWORD_PROP = "KEYCLOAK_ADMIN_PASSWORD";
    private static final String KEYCLOAK_QUARKUS_START_CMD = "start --storage=chm --http-enabled=true --hostname-strict=false --hostname-strict-https=false";

    private static final String KEYCLOAK_CONTAINER_NAME = "keycloak";
    private static final String KEYCLOAK_ADMIN_USER = "admin";
    private static final String KEYCLOAK_ADMIN_PASSWORD = "admin";

    // Properties recognized by Wildfly-powered Keycloak
    private static final String KEYCLOAK_WILDFLY_FRONTEND_URL = "KEYCLOAK_FRONTEND_URL";
    private static final String KEYCLOAK_WILDFLY_USER_PROP = "KEYCLOAK_USER";
    private static final String KEYCLOAK_WILDFLY_PASSWORD_PROP = "KEYCLOAK_PASSWORD";
    private static final String KEYCLOAK_WILDFLY_DB_VENDOR = "H2";
    private static final String KEYCLOAK_WILDFLY_VENDOR_PROP = "DB_VENDOR";

    private static final String JAVA_OPTS = "JAVA_OPTS";
    private static final String OIDC_USERS = "oidc.users";
    private static final String KEYCLOAK_REALMS = "keycloak.realms";

    private static final String DEV_SERVICE_LABEL = "quarkus-dev-service-horreum";

    @BuildStep(onlyIf = { IsDevelopment.class })
    public DevServicesResultBuildItem startHorreumContainers(
            DockerStatusBuildItem dockerStatusBuildItem,
            BuildProducer<HorreumDevServicesConfigBuildItem> horreumBuildItemBuildProducer,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            HorreumBuildTimeConfig config,
            CuratedApplicationShutdownBuildItem closeBuildItem,
            LaunchModeBuildItem launchMode,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            LoggingSetupBuildItem loggingSetupBuildItem,
            GlobalDevServicesConfig devServicesConfig) {

        if (vertxInstance == null) {
            vertxInstance = Vertx.vertx();
        }
        devServicesConfiguration = config.devservices;

        StartupLogCompressor compressor = new StartupLogCompressor(
                (launchMode.isTest() ? "(test) " : "") + "Horreum Dev Services Starting:",
                consoleInstalledBuildItem, loggingSetupBuildItem);

        try {
            List<String> errors = new ArrayList<>();

            DevServicesResultBuildItem.RunningDevService newDevService = startContainer(dockerStatusBuildItem,
                    horreumBuildItemBuildProducer,
                    !devServicesSharedNetworkBuildItem.isEmpty(),
                    devServicesConfig.timeout,
                    errors);
            if (newDevService == null) {
                if (errors.isEmpty()) {
                    compressor.close();
                } else {
                    compressor.closeAndDumpCaptured();
                }
                return null;
            }

            devService = newDevService;

            Runnable closeTask = () -> {
                if (devService != null) {
                    try {
                        devService.close();
                    } catch (Throwable t) {
                        LOG.error("Failed to stop Keycloak container", t);
                    }
                }
                if (vertxInstance != null) {
                    try {
                        vertxInstance.close();
                    } catch (Throwable t) {
                        LOG.error("Failed to close Vertx instance", t);
                    }
                }
                devService = null;
                devServicesConfiguration = null;
                vertxInstance = null;
            };
            closeBuildItem.addCloseTask(closeTask, true);

            //            capturedRealmFileLastModifiedDate = getRealmFileLastModifiedDate(capturedDevServicesConfiguration.realmPath);
            if (devService != null && errors.isEmpty()) {
                compressor.close();
            } else {
                compressor.closeAndDumpCaptured();
            }
        } catch (Throwable t) {
            compressor.closeAndDumpCaptured();
            throw new RuntimeException(t);
        }
        LOG.info("Dev Services for Keycloak started.");

        return devService.toBuildItem();

    }

    private DevServicesResultBuildItem.RunningDevService startContainer(DockerStatusBuildItem dockerStatusBuildItem,
            BuildProducer<HorreumDevServicesConfigBuildItem> horreumBuildItemBuildProducer,
            boolean useSharedNetwork, Optional<Duration> timeout,
            List<String> errors) {
        if (!dockerStatusBuildItem.isDockerAvailable()) {
            LOG.warn("Please configure 'quarkus.oidc.auth-server-url' or get a working docker instance");
            return null;
        }

        //        final Optional<ContainerAddress> maybeContainerAddress = keycloakDevModeContainerLocator.locateContainer(
        //                capturedDevServicesConfiguration.serviceName,
        //                capturedDevServicesConfiguration.shared,
        //                LaunchMode.current());

        String keycloakImageName = devServicesConfiguration.keycloakImageName;
        String postrgesImageName = devServicesConfiguration.postgresImageName;

        DockerImageName keycloakDockerImageName = DockerImageName.parse(keycloakImageName)
                .asCompatibleSubstituteFor(keycloakImageName);
        DockerImageName postgresDockerImageName = DockerImageName.parse(postrgesImageName)
                .asCompatibleSubstituteFor(postrgesImageName);

        final Supplier<DevServicesResultBuildItem.RunningDevService> defaultKeycloakContainerSupplier = () -> {

            QuarkusKeyloakContainer oidcContainer = new QuarkusKeyloakContainer(keycloakDockerImageName,
                    devServicesConfiguration.keycloakPort,
                    useSharedNetwork,
                    devServicesConfiguration.realmPath.orElse(List.of()),
                    devServicesConfiguration.keycloakContainerName,
                    devServicesConfiguration.javaOpts,
                    devServicesConfiguration.startCommand,
                    devServicesConfiguration.showLogs,
                    errors);

            timeout.ifPresent(oidcContainer::withStartupTimeout);
            oidcContainer.start();

            String internalUrl = startURL(oidcContainer.getHost(), oidcContainer.getPort());
            String hostUrl = oidcContainer.useSharedNetwork
                    // we need to use auto-detected host and port, so it works when docker host != localhost
                    ? startURL(oidcContainer.getSharedNetworkExternalHost(), oidcContainer.getSharedNetworkExternalPort())
                    : null;

            Map<String, String> configs = prepareConfiguration(horreumBuildItemBuildProducer, internalUrl, hostUrl,
                    null,
                    true,
                    errors);
            return new DevServicesResultBuildItem.RunningDevService(KEYCLOAK_CONTAINER_NAME, oidcContainer.getContainerId(),
                    oidcContainer::close, configs);
        };

        return defaultKeycloakContainerSupplier.get();
    }

    private Map<String, String> prepareConfiguration(
            BuildProducer<HorreumDevServicesConfigBuildItem> keycloakBuildItemBuildProducer, String internalURL,
            String hostURL, List<Object> realmReps,
            boolean keycloakX, List<String> errors) {

        Map<String, String> configProperties = new HashMap<>();
        //        configProperties.put(KEYCLOAK_URL_KEY, internalURL);
        //        configProperties.put(AUTH_SERVER_URL_CONFIG_KEY, authServerInternalUrl);
        //        configProperties.put(CLIENT_AUTH_SERVER_URL_CONFIG_KEY, clientAuthServerUrl);
        //        configProperties.put(APPLICATION_TYPE_CONFIG_KEY, oidcApplicationType);
        //        configProperties.put(CLIENT_ID_CONFIG_KEY, oidcClientId);
        //        configProperties.put(CLIENT_SECRET_CONFIG_KEY, oidcClientSecret);
        //        configProperties.put(OIDC_USERS, users.entrySet().stream()
        //                .map(e -> e.toString()).collect(Collectors.joining(",")));
        //        configProperties.put(KEYCLOAK_REALMS, realmNames.stream().collect(Collectors.joining(",")));
        //
        //        keycloakBuildItemBuildProducer
        //                .produce(new KeycloakDevServicesConfigBuildItem(configProperties,
        //                        Map.of(OIDC_USERS, users, KEYCLOAK_REALMS, realmNames), true));

        return configProperties;
    }

    private String startURL(String host, Integer port) {
        return "http://" + host + ":" + port + "/auth";
    }

    public static class IsEnabled implements BooleanSupplier {
        HorreumBuildTimeConfig config;

        public boolean getAsBoolean() {
            return config.devservices.enabled;
        }
    }

    private static class QuarkusKeyloakContainer extends GenericContainer<QuarkusKeyloakContainer> {
        private final OptionalInt fixedExposedPort;
        private final boolean useSharedNetwork;
        private final List<String> realmPaths;
        private final String containerLabelValue;
        private final Optional<String> javaOpts;
        private String hostName;
        private final Optional<String> startCommand;
        private final boolean showLogs;
        private final List<String> errors;

        public QuarkusKeyloakContainer(DockerImageName dockerImageName, OptionalInt fixedExposedPort, boolean useSharedNetwork,
                List<String> realmPaths, String containerLabelValue,
                Optional<String> javaOpts, Optional<String> startCommand, boolean showLogs,
                List<String> errors) {
            super(dockerImageName);

            this.useSharedNetwork = useSharedNetwork;
            this.realmPaths = realmPaths;
            this.containerLabelValue = containerLabelValue;
            this.javaOpts = javaOpts;

            if (useSharedNetwork && fixedExposedPort.isEmpty()) {
                // We need to know the port we are exposing when using the shared network, in order to be able to tell
                // Keycloak what the client URL is. This is necessary in order for Keycloak to create the proper 'issuer'
                // when creating tokens
                fixedExposedPort = OptionalInt.of(findRandomPort());
            }

            this.fixedExposedPort = fixedExposedPort;
            this.startCommand = startCommand;
            this.showLogs = showLogs;
            this.errors = errors;

            super.setWaitStrategy(Wait.forLogMessage(".*Keycloak.*started.*", 1));
        }

        @Override
        protected void configure() {
            super.configure();

            if (useSharedNetwork) {
                hostName = ConfigureUtil.configureSharedNetwork(this, "keycloak");
                addEnv(KEYCLOAK_WILDFLY_FRONTEND_URL, "http://localhost:" + fixedExposedPort.getAsInt());
            }

            if (fixedExposedPort.isPresent()) {
                addFixedExposedPort(fixedExposedPort.getAsInt(), KEYCLOAK_PORT);
                if (useSharedNetwork) {
                    // expose random port for which we are able to ask Testcontainers for the actual mapped port at runtime
                    // as from the host's perspective Testcontainers actually expose container ports on random host port
                    addExposedPort(KEYCLOAK_PORT);
                }
            } else {
                addExposedPort(KEYCLOAK_PORT);
            }

            if (LaunchMode.current() == LaunchMode.DEVELOPMENT) {
                withLabel(DEV_SERVICE_LABEL, containerLabelValue);
            }

            if (javaOpts.isPresent()) {
                addEnv(JAVA_OPTS, javaOpts.get());
            }

            addEnv(KEYCLOAK_WILDFLY_USER_PROP, KEYCLOAK_ADMIN_USER);
            addEnv(KEYCLOAK_WILDFLY_PASSWORD_PROP, KEYCLOAK_ADMIN_PASSWORD);
            addEnv(KEYCLOAK_WILDFLY_VENDOR_PROP, KEYCLOAK_WILDFLY_DB_VENDOR);

            /*
             * for (String realmPath : realmPaths) {
             * URL realmPathUrl = null;
             * if ((realmPathUrl = Thread.currentThread().getContextClassLoader().getResource(realmPath)) != null) {
             * readRealmFile(realmPathUrl, realmPath, errors).ifPresent(realmRep -> realmReps.add(realmRep));
             * } else {
             * Path filePath = Paths.get(realmPath);
             * if (Files.exists(filePath)) {
             * readRealmFile(filePath.toUri(), realmPath, errors).ifPresent(realmRep -> realmReps.add(realmRep));
             * } else {
             * errors.add(String.format("Realm %s resource is not available", realmPath));
             * LOG.debugf("Realm %s resource is not available", realmPath);
             * }
             * }
             *
             * }
             */

            if (showLogs) {
                super.withLogConsumer(t -> {
                    LOG.info("Keycloak: " + t.getUtf8String());
                });
            }

            LOG.infof("Using %s powered Keycloak distribution", keycloakX ? "Quarkus" : "WildFly");
        }

        private Integer findRandomPort() {
            try (ServerSocket socket = new ServerSocket(0)) {
                return socket.getLocalPort();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /*
         * private Optional<RealmRepresentation> readRealmFile(URI uri, String realmPath, List<String> errors) {
         * try {
         * return readRealmFile(uri.toURL(), realmPath, errors);
         * } catch (MalformedURLException ex) {
         * // Will not happen as this method is called only when it is confirmed the file exists
         * throw new RuntimeException(ex);
         * }
         * }
         *
         * private Optional<RealmRepresentation> readRealmFile(URL url, String realmPath, List<String> errors) {
         * try {
         * try (InputStream is = url.openStream()) {
         * return Optional.of(JsonSerialization.readValue(is, RealmRepresentation.class));
         * }
         * } catch (IOException ex) {
         * errors.add(String.format("Realm %s resource can not be opened: %s", realmPath, ex.getMessage()));
         *
         * LOG.errorf("Realm %s resource can not be opened: %s", realmPath, ex.getMessage());
         * }
         * return Optional.empty();
         * }
         */

        @Override
        public String getHost() {
            if (useSharedNetwork) {
                return hostName;
            }
            return super.getHost();
        }

        /**
         * Host name used for calls from outside of docker when {@code useSharedNetwork} is true.
         *
         * @return host name
         */
        private String getSharedNetworkExternalHost() {
            return super.getHost();
        }

        /**
         * Host port used for calls from outside of docker when {@code useSharedNetwork} is true.
         *
         * @return port
         */
        private int getSharedNetworkExternalPort() {
            return getFirstMappedPort();
        }

        public int getPort() {
            if (useSharedNetwork) {
                return KEYCLOAK_PORT;
            }
            if (fixedExposedPort.isPresent()) {
                return fixedExposedPort.getAsInt();
            }
            return getFirstMappedPort();
        }
    }

}
