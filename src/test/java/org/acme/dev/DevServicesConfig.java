package org.acme.dev;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

import java.io.File;
import java.util.Optional;

@ConfigGroup
public class DevServicesConfig {

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

}
