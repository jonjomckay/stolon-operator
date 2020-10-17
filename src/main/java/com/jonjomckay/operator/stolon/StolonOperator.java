package com.jonjomckay.operator.stolon;

import com.jonjomckay.operator.stolon.clusters.ClusterController;
import com.jonjomckay.operator.stolon.utils.Yaml;
import com.sun.net.httpserver.HttpServer;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;

public class StolonOperator {
    private final static Logger LOGGER = LoggerFactory.getLogger(StolonOperator.class);

    public static void main(String[] args) throws IOException {
        LOGGER.info("Stolon operator starting");

        var crd = Yaml.loadYaml(CustomResourceDefinition.class, "manifests/crd.yml");

        KubernetesClient kubernetesClient = new DefaultKubernetesClient();
        kubernetesClient.apiextensions().v1().customResourceDefinitions().createOrReplace(crd);

        Operator operator = new Operator(kubernetesClient);
        operator.registerControllerForAllNamespaces(new ClusterController(kubernetesClient));

        // Start an HTTP server on port 8080 so we can tell Kube we're healthy
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(8080), 0);
        httpServer.createContext("/health", httpExchange -> {
            httpExchange.sendResponseHeaders(200, 0);
            httpExchange.close();
        });
        httpServer.start();
    }
}
