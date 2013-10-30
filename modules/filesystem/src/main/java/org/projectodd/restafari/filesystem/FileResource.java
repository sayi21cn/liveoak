package org.projectodd.restafari.filesystem;

import org.projectodd.restafari.spi.MediaType;
import org.projectodd.restafari.spi.resource.Resource;
import org.projectodd.restafari.spi.resource.async.BinaryContentSink;
import org.projectodd.restafari.spi.resource.async.BinaryResource;
import org.projectodd.restafari.spi.resource.async.Responder;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.file.AsyncFile;

import java.io.File;

/**
 * @author Bob McWhirter
 */
public class FileResource implements FSResource, BinaryResource {

    public FileResource(FSResource parent, File file) {
        this.parent = parent;
        this.file = file;
    }

    @Override
    public Vertx vertx() {
        return this.parent.vertx();
    }

    @Override
    public Resource parent() {
        return this.parent;
    }

    public File file() {
        return this.file;
    }

    @Override
    public MediaType mediaType() {
        String name = this.file.getName();
        int lastDotLoc = name.lastIndexOf('.');
        MediaType mediaType = null;
        if (lastDotLoc > 0) {
            mediaType = MediaType.lookup(name.substring(lastDotLoc + 1));
        }

        if (mediaType == null) {
            mediaType = MediaType.OCTET_STREAM;
        }
        return mediaType;
    }

    @Override
    public String id() {
        return this.file.getName();
    }

    @Override
    public void read(String id, Responder responder) {
        responder.readNotSupported(this);
    }

    @Override
    public void readContent(BinaryContentSink sink) {
        vertx().fileSystem().open(file.getPath(), (result) -> {
            if (result.succeeded()) {
                AsyncFile asyncFile = result.result();
                asyncFile.dataHandler((buffer) -> {
                    sink.accept(buffer.getByteBuf());
                });
                asyncFile.endHandler((end) -> {
                    sink.close();
                });
            } else {
                sink.close();
            }
        });
    }


    @Override
    public void delete(Responder responder) {
        responder.deleteNotSupported(this);
    }

    public String toString() {
        return "[FileResource: file=" + this.file + "]";
    }

    private FSResource parent;
    private File file;
}