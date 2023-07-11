package org.acme.dev;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class HorreumDevServicesConfig {

    @ConfigItem(defaultValue = "true")
    public boolean enabled = true;

    @ConfigItem(defaultValue = "quay.io/keycloak/keycloak:21.0.2")
    public String keycloakImageName;

    @ConfigItem(defaultValue = "quay.io/keycloak/keycloak:21.0.2")
    public String postgresImageName;

    @ConfigItem(defaultValue = "quay.io/keycloak/keycloak:21.0.2")
    public Optional<File> importRealm;

    @ConfigItem()
    public Optional<File> databaseBackup;

    @ConfigItem(defaultValue = "horreum-dev-keycloak")
    public String keycloakContainerName;

    @ConfigItem
    public Optional<List<String>> realmPath;

    @ConfigItem
    public Optional<String> javaOpts;

    @ConfigItem
    public Optional<String> startCommand;

    @ConfigItem(defaultValue = "false")
    public boolean showLogs;

    @ConfigItem
    public OptionalInt keycloakPort;
    public OptionalInt horreumPort;
}
