package com.jonjomckay.operator.stolon;

import com.github.containersolutions.operator.OperatorException;
import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import com.github.containersolutions.operator.processing.EventDispatcher;
import com.github.containersolutions.operator.processing.EventScheduler;
import com.github.containersolutions.operator.processing.retry.GenericRetry;
import com.github.containersolutions.operator.processing.retry.Retry;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static com.github.containersolutions.operator.ControllerUtils.getCustomResourceDoneableClass;

/**
 * This is just a copy of the Operator class from Operator Framework, modified to work with v1 CRDs. Once support for v1
 * CRDs has been merged, this class can be removed and we can rely only on the framework.
 */
public class Operator extends com.github.containersolutions.operator.Operator {
    private final static Logger LOGGER = LoggerFactory.getLogger(Operator.class);

    private final KubernetesClient k8sClient;

    public Operator(KubernetesClient k8sClient) {
        super(k8sClient);
        this.k8sClient = k8sClient;
    }

    public <R extends CustomResource> void registerControllerForAllNamespaces(ResourceController<R> controller) throws OperatorException {
        registerController(controller, true, GenericRetry.defaultLimitedExponentialRetry());
    }

    @SuppressWarnings("rawtypes")
    private <R extends CustomResource> void registerController(ResourceController<R> controller,
                                                               boolean watchAllNamespaces, Retry retry, String... targetNamespaces) throws OperatorException {
        Class<R> resClass = getCustomResourceClass(controller);
        CustomResourceDefinition crd = getCustomResourceDefinitionForController(controller);
        KubernetesDeserializer.registerCustomKind(getApiVersion(crd), getKind(crd), resClass);
        String finalizer = getDefaultFinalizer(controller);

        CustomResourceDefinitionContext customResourceDefinitionContext = new CustomResourceDefinitionContext.Builder()
                .withGroup(crd.getSpec().getGroup())
                .withVersion(getApiVersion(crd))
                .withScope(crd.getSpec().getScope())
                .withName(crd.getMetadata().getName())
                .withPlural(crd.getSpec().getNames().getPlural())
                .withKind(crd.getSpec().getNames().getKind())
                .build();

        MixedOperation client = k8sClient.customResources(customResourceDefinitionContext, resClass, CustomResourceList.class, getCustomResourceDoneableClass(controller));
        EventDispatcher eventDispatcher = new EventDispatcher(controller,
                finalizer, new EventDispatcher.CustomResourceFacade(client), getGenerationEventProcessing(controller));
        EventScheduler eventScheduler = new EventScheduler(eventDispatcher, retry);
        registerWatches(controller, client, resClass, watchAllNamespaces, targetNamespaces, eventScheduler);
    }


    private <R extends CustomResource> void registerWatches(ResourceController<R> controller, MixedOperation client,
                                                            Class<R> resClass,
                                                            boolean watchAllNamespaces, String[] targetNamespaces, EventScheduler eventScheduler) {

        CustomResourceOperationsImpl crClient = (CustomResourceOperationsImpl) client;
        if (watchAllNamespaces) {
            crClient.inAnyNamespace().watch(eventScheduler);
        } else if (targetNamespaces.length == 0) {
            client.watch(eventScheduler);
        } else {
            for (String targetNamespace : targetNamespaces) {
                crClient.inNamespace(targetNamespace).watch(eventScheduler);
                LOGGER.debug("Registered controller for namespace: {}", targetNamespace);
            }
        }
        getCustomResourceClients().put(resClass, (CustomResourceOperationsImpl) client);
        LOGGER.info("Registered Controller: '{}' for CRD: '{}' for namespaces: {}", controller.getClass().getSimpleName(),
                resClass, targetNamespaces.length == 0 ? "[all/client namespace]" : Arrays.toString(targetNamespaces));
    }

    private CustomResourceDefinition getCustomResourceDefinitionForController(ResourceController controller) {
        String crdName = getCrdName(controller);
        CustomResourceDefinition customResourceDefinition = k8sClient.apiextensions().v1().customResourceDefinitions().withName(crdName).get();
        if (customResourceDefinition == null) {
            throw new OperatorException("Cannot find Custom Resource Definition with name: " + crdName);
        }
        return customResourceDefinition;
    }

    private static Controller getAnnotation(ResourceController controller) {
        return controller.getClass().getAnnotation(Controller.class);
    }

    static <R extends CustomResource> Class<R> getCustomResourceClass(ResourceController controller) {
        return (Class<R>) getAnnotation(controller).customResourceClass();
    }

    static String getCrdName(ResourceController controller) {
        return getAnnotation(controller).crdName();
    }

    static String getDefaultFinalizer(ResourceController controller) {
        return getAnnotation(controller).finalizerName();
    }

    static boolean getGenerationEventProcessing(ResourceController controller) {
        return getAnnotation(controller).generationAwareEventProcessing();
    }

    private String getKind(CustomResourceDefinition crd) {
        return crd.getSpec().getNames().getKind();
    }

    private String getApiVersion(CustomResourceDefinition crd) {
        return crd.getSpec().getGroup() + "/" + crd.getSpec().getVersions().get(0).getName();
    }
}
