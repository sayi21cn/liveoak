package io.liveoak.mongo;

import com.mongodb.*;
import com.mongodb.util.JSON;
import org.bson.types.ObjectId;
import io.liveoak.spi.*;
import io.liveoak.spi.resource.async.PropertySink;
import io.liveoak.spi.resource.async.ResourceSink;
import io.liveoak.spi.resource.async.Responder;
import io.liveoak.spi.state.ResourceState;

import java.util.*;

/**
 * @author Bob McWhirter
 */
class MongoCollectionResource extends MongoResource {

    String collectionName;

    MongoCollectionResource(MongoResource parent, String collectionName) {
        super(parent, collectionName);
        this.collectionName = collectionName;
    }

    @Override
    public void readMember(RequestContext ctx, String id, Responder responder) {

        DBObject dbObject = null;
        // check if its a mongo autogenerated id
        if (ObjectId.isValid(id)) {
            dbObject = this.parent.getDB().getCollection(this.collectionName).findOne(new BasicDBObject(MONGO_ID_FIELD, new ObjectId(id)));
        }

        // if its not a mongo autogenerated id, then check using just the id string
        if (dbObject == null) {
            dbObject = this.parent.getDB().getCollection(this.collectionName).findOne(new BasicDBObject(MONGO_ID_FIELD, id));
        }

        if (dbObject == null) {
            responder.noSuchResource(id);
            return;
        }

        responder.resourceRead(new MongoObjectResource(this, dbObject));
    }

    @Override
    public void delete(RequestContext ctx, Responder responder) {
        if (getDB().collectionExists(this.collectionName)) {
            getDB().getCollection(this.collectionName).drop();
            responder.resourceDeleted(this);
        } else {
            responder.noSuchResource(this.collectionName);
        }
    }

    @Override
    public void readMembers(RequestContext ctx, ResourceSink sink) {
        DBCollection c = this.parent.getDB().getCollection(this.collectionName);
        ResourceParams params = ctx.getResourceParams();
        DBCursor cursor;
        if (params != null && params.contains("q")) {
            String q = params.value("q");
            cursor = c.find((DBObject) JSON.parse(q));
        } else {
            cursor = c.find();
        }
        cursor.forEach((e) -> {
            sink.accept(new MongoObjectResource(this, e));
        });

        try {
            sink.close();
        } catch (Exception e) {
            e.printStackTrace();  //TODO: properly handle errors
        }
    }

    @Override
    public void createMember(RequestContext ctx, ResourceState state, Responder responder) {
        DBCollection dbCollection = this.parent.getDB().getCollection(this.id);

        BasicDBObject basicDBObject = null;
        try {
            basicDBObject = (BasicDBObject) createObject(state);
            dbCollection.insert(basicDBObject);
        } catch (Exception e) {
            e.printStackTrace();
        }

        responder.resourceCreated(new MongoObjectResource(this, basicDBObject));
    }

    public String toString() {
        return "[MongoCollectionResource: id=" + this.collectionName + "]";
    }

    protected Object createObject(ResourceState resourceState) {
        BasicDBObject basicDBObject = new BasicDBObject();
        // if the state already has an id set, use it here. Otherwise one will be autocreated on insert
        String rid = resourceState.id();
        if (rid != null) {
            basicDBObject.append(MONGO_ID_FIELD, rid);
        }

        Set<String> keys = resourceState.getPropertyNames();

        for (String key : keys) {
            if (!key.equals(MBAAS_ID_FIELD)) { //don't append the ID field again
                Object value = resourceState.getProperty( key );
                if ( value instanceof ResourceState ) {
                    value = createObject((ResourceState) value);
                }
                basicDBObject.append(key, value);
            }
        }

        return basicDBObject;
    }

    @Override
    public void readProperties(RequestContext ctx, PropertySink sink) {
        sink.accept("type", "collection");
        sink.close();
    }

}