package com.jonjomckay.operator.stolon.clusters;

import com.jonjomckay.operator.stolon.utils.Yaml;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.Map;

public class ClusterSentinelRepository {
    private final KubernetesClient kubernetesClient;

    public ClusterSentinelRepository(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    public void upsertSentinel(String namespace, String baseName, Cluster cluster) {
        Deployment deployment = Yaml.loadYaml(Deployment.class, "manifests/sentinel-deployment.yml");
        deployment.setMetadata(new ObjectMetaBuilder()
                .withName(String.format("%s-sentinel", baseName))
                .withNamespace(namespace)
                .build()
        );

        Map<String, String> labels = Map.ofEntries(
                Map.entry("component", "stolon-sentinel"),
                Map.entry("stolon-cluster", baseName)
        );

        var labelSelector = new LabelSelectorBuilder()
                .withMatchLabels(labels)
                .build();

        deployment.getSpec().setReplicas(cluster.getSpec().getSentinel().getReplicas());

        deployment.getSpec().setSelector(labelSelector);
        deployment.getSpec().getTemplate().getMetadata().setLabels(labels);

        deployment.getSpec().getTemplate().getSpec().setServiceAccountName(baseName);

        // Set the pod anti-affinity, so we try not to schedule on the same node as our other sentinels
        deployment.getSpec().getTemplate().getSpec().setAffinity(null);
//        deployment.getSpec().getTemplate().getSpec().getAffinity().getPodAntiAffinity().getRequiredDuringSchedulingIgnoredDuringExecution().get(0)
//                .setLabelSelector(labelSelector);

        deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(cluster.getSpec().getImage());

        // Update the sentinel's deployment
        kubernetesClient.apps().deployments()
                .inNamespace(namespace)
                .createOrReplace(deployment);
    }
}
