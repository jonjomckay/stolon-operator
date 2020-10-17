package com.jonjomckay.operator.stolon.clusters;

import com.jonjomckay.operator.stolon.utils.Yaml;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.List;
import java.util.Map;

public class ClusterKeeperRepository {
    private final KubernetesClient kubernetesClient;

    public ClusterKeeperRepository(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    public void upsertKeeper(String namespace, String baseName, Cluster cluster) {
        StatefulSet statefulSet = Yaml.loadYaml(StatefulSet.class, "manifests/keeper-statefulset.yml");
        statefulSet.setMetadata(new ObjectMetaBuilder()
                .withName(String.format("%s-keeper", baseName))
                .withNamespace(namespace)
                .build()
        );

        Map<String, String> labels = Map.ofEntries(
                Map.entry("component", "stolon-keeper"),
                Map.entry("stolon-cluster", baseName)
        );

        var labelSelector = new LabelSelectorBuilder()
                .withMatchLabels(labels)
                .build();

        statefulSet.getSpec().setServiceName(statefulSet.getMetadata().getName());

        statefulSet.getSpec().setReplicas(cluster.getSpec().getKeeper().getReplicas());

        statefulSet.getSpec().setSelector(labelSelector);
        statefulSet.getSpec().getTemplate().getMetadata().setLabels(labels);

        statefulSet.getSpec().getTemplate().getSpec().setServiceAccountName(baseName);

        // Set the pod anti-affinity, so we try not to schedule on the same node as our other proxies
        statefulSet.getSpec().getTemplate().getSpec().setAffinity(null);
//        deployment.getSpec().getTemplate().getSpec().getAffinity().getPodAntiAffinity().getRequiredDuringSchedulingIgnoredDuringExecution().get(0)
//                .setLabelSelector(labelSelector);

        statefulSet.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(cluster.getSpec().getImage());
//        statefulSet.getSpec().getTemplate().getSpec().getContainers().get(1).setImage(cluster.getSpec().getImage());

        // Set keeper container variables
        statefulSet.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv()
                .add(new EnvVar("STKEEPER_PG_SU_PASSWORD", null, new EnvVarSourceBuilder()
                        .withNewSecretKeyRef("password", baseName, false)
                        .build()
                ));

        // Set backup stuff
//        var s3Prefix = String.format("s3://%s%s",
//                cluster.getSpec().getBackups().getS3().getRegion(),
//                cluster.getSpec().getBackups().getS3().getPrefix());
//
//        statefulSet.getSpec().getTemplate().getSpec().getContainers().get(1).getEnv()
//                .add(new EnvVar("AWS_REGION", cluster.getSpec().getBackups().getS3().getRegion(), null));
//
//        statefulSet.getSpec().getTemplate().getSpec().getContainers().get(1).getEnv()
//                .add(new EnvVar("PGPASSWORD", null, new EnvVarSourceBuilder()
//                        .withNewSecretKeyRef("password", baseName, false)
//                        .build()
//                ));
//
//        statefulSet.getSpec().getTemplate().getSpec().getContainers().get(1).getEnv()
//                .add(new EnvVar("WALE_S3_PREFIX", s3Prefix, null));

        // Storage
        statefulSet.getSpec().getTemplate().getSpec().getVolumes().get(0)
                .setSecret(new SecretVolumeSourceBuilder()
                        .withSecretName(baseName)
                        .build()
                );

        statefulSet.getSpec().setVolumeClaimTemplates(List.of(
                new PersistentVolumeClaimBuilder()
                        .withNewMetadata()
                        .withName("data")
                        .endMetadata()

                        .withNewSpec()
                        .withAccessModes("ReadWriteOnce")
                        .withNewResources()
                        .addToRequests("storage", Quantity.parse(cluster.getSpec().getStorage().getSize()))
                        .endResources()
                        .endSpec()
                        .build()
        ));

        // Update the proxy's deployment
        kubernetesClient.apps().statefulSets()
                .inNamespace(namespace)
                .createOrReplace(statefulSet);
    }
}
