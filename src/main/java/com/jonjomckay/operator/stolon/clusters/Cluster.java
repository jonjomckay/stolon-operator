package com.jonjomckay.operator.stolon.clusters;

import io.fabric8.kubernetes.client.CustomResource;

public class Cluster extends CustomResource {
    private ClusterSpec spec;

    public ClusterSpec getSpec() {
        return spec;
    }

    public void setSpec(ClusterSpec spec) {
        this.spec = spec;
    }
}

