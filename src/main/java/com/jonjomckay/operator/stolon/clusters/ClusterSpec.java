package com.jonjomckay.operator.stolon.clusters;

public class ClusterSpec {
    private ClusterSpecBackups backups;
    private String image;
    private ClusterSpecKeeper keeper;
    private ClusterSpecProxy proxy;
    private ClusterSpecSentinel sentinel;
    private ClusterSpecStorage storage;

    public ClusterSpecBackups getBackups() {
        return backups;
    }

    public void setBackups(ClusterSpecBackups backups) {
        this.backups = backups;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public ClusterSpecKeeper getKeeper() {
        return keeper;
    }

    public void setKeeper(ClusterSpecKeeper keeper) {
        this.keeper = keeper;
    }

    public ClusterSpecProxy getProxy() {
        return proxy;
    }

    public void setProxy(ClusterSpecProxy proxy) {
        this.proxy = proxy;
    }

    public ClusterSpecSentinel getSentinel() {
        return sentinel;
    }

    public void setSentinel(ClusterSpecSentinel sentinel) {
        this.sentinel = sentinel;
    }

    public ClusterSpecStorage getStorage() {
        return storage;
    }

    public void setStorage(ClusterSpecStorage storage) {
        this.storage = storage;
    }
}
