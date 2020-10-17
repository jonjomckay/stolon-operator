package com.jonjomckay.operator.stolon.utils;

import io.fabric8.kubernetes.client.utils.Serialization;

import java.io.IOException;
import java.io.InputStream;

public class Yaml {
    public static <T> T loadYaml(Class<T> clazz, String yaml) {
        try (InputStream is = Yaml.class.getClassLoader().getResourceAsStream(yaml)) {
            return Serialization.unmarshal(is, clazz);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot find yaml on classpath: " + yaml);
        }
    }
}
