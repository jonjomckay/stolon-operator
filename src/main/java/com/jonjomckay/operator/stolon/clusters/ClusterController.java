package com.jonjomckay.operator.stolon.clusters;

import com.github.containersolutions.operator.api.Context;
import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import com.github.containersolutions.operator.api.UpdateControl;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.Base64;

@Controller(crdName = "clusters.stolon.io", customResourceClass = Cluster.class)
public class ClusterController implements ResourceController<Cluster> {
    private final KubernetesClient kubernetesClient;
    private final ClusterKeeperRepository keeperRepository;
    private final ClusterProxyRepository proxyRepository;
    private final ClusterSentinelRepository sentinelRepository;

    public ClusterController(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
        this.keeperRepository = new ClusterKeeperRepository(kubernetesClient);
        this.proxyRepository = new ClusterProxyRepository(kubernetesClient);
        this.sentinelRepository = new ClusterSentinelRepository(kubernetesClient);
    }

    @Override
    public boolean deleteResource(Cluster cluster, Context<Cluster> context) {
        var namespace = cluster.getMetadata().getNamespace();
        var baseName = String.format("stolon-%s", cluster.getMetadata().getName());

        // TODO: These selectors aren't precise enough
        kubernetesClient.rbac().roles().inNamespace(namespace).withName(baseName).delete();
        kubernetesClient.rbac().roleBindings().inNamespace(namespace).withName(baseName).delete();
        kubernetesClient.serviceAccounts().inNamespace(namespace).withName(baseName).delete();
        kubernetesClient.secrets().inNamespace(namespace).withName(baseName).delete();
        kubernetesClient.apps().deployments().inNamespace(namespace).withName(String.format("%s-proxy", baseName)).delete();
        kubernetesClient.apps().deployments().inNamespace(namespace).withName(String.format("%s-sentinel", baseName)).delete();
        kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(String.format("%s-keeper", baseName)).delete();

        return true;
    }

    @Override
    public UpdateControl<Cluster> createOrUpdateResource(Cluster cluster, Context<Cluster> context) {
        // TODO: Validation

        var namespace = cluster.getMetadata().getNamespace();
        var baseName = String.format("stolon-%s", cluster.getMetadata().getName());

        // Create all the RBAC stuff
        var role = kubernetesClient.rbac().roles()
                .inNamespace(namespace)
                .createOrReplaceWithNew()

                .withNewMetadata()
                .withName(baseName)
                .endMetadata()

                .addNewRule()
                .addNewApiGroup("")
                .addNewResource("configmaps")
                .addNewResource("events")
                .addNewResource("pods")
                .addNewVerb("*")
                .endRule()

                .done();

        var serviceAccount = kubernetesClient.serviceAccounts()
                .inNamespace(namespace)
                .createOrReplaceWithNew()

                .withNewMetadata()
                .withName(baseName)
                .endMetadata()

                .done();

        kubernetesClient.rbac().roleBindings()
                .inNamespace(namespace)
                .createOrReplaceWithNew()

                .withNewMetadata()
                .withName(baseName)
                .endMetadata()

                .withNewRoleRef("rbac.authorization.k8s.io", role.getKind(), role.getMetadata().getName())
                .addNewSubject("", serviceAccount.getKind(), serviceAccount.getMetadata().getName(), serviceAccount.getMetadata().getNamespace())

                .done();

        // Create secrets
        kubernetesClient.secrets()
                .inNamespace(namespace)
                .createOrReplaceWithNew()

                .withNewMetadata()
                .withName(baseName)
                .endMetadata()

                .withType("Opaque")
                .addToStringData("password", Base64.getEncoder().encodeToString("password".getBytes()))

                .done();

        keeperRepository.upsertKeeper(namespace, baseName, cluster);
        proxyRepository.upsertProxy(namespace, baseName, cluster);
        sentinelRepository.upsertSentinel(namespace, baseName, cluster);

        return UpdateControl.noUpdate();
    }
}
