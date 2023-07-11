package org.acme.dev;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot
public class HorreumBuildTimeConfig {

    @ConfigItem
    public HorreumDevServicesConfig devservices;

}
