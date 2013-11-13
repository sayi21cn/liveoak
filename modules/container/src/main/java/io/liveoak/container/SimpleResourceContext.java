package io.liveoak.container;

import io.liveoak.spi.*;
import io.liveoak.spi.Container;
import org.vertx.java.core.Vertx;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class SimpleResourceContext implements ResourceContext {

    private final Vertx vertx;
    private final Container container;
    private final Config config;

    public SimpleResourceContext(Vertx vertx, Container container, Config config) {
        this.vertx = vertx;
        this.container = container;
        this.config = config;
    }

    @Override
    public Vertx vertx() {
        return vertx;
    }

    @Override
    public Container container() {
        return container;
    }

    @Override
    public Config config() {
        return config;
    }
}