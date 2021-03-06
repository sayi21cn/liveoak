/*
 * Copyright 2014 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at http://www.eclipse.org/legal/epl-v10.html
 */

package io.liveoak.security.policy.acl.impl;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import io.liveoak.common.codec.DefaultResourceState;
import io.liveoak.common.security.AuthzDecision;
import io.liveoak.spi.RequestContext;
import io.liveoak.spi.RequestType;
import io.liveoak.spi.ResourcePath;
import io.liveoak.spi.ResourceResponse;
import io.liveoak.spi.security.SecurityContext;
import io.liveoak.spi.state.ResourceState;
import org.jboss.logging.Logger;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class AclPolicy {

    private static final Logger log = Logger.getLogger(AclPolicy.class);

    private final AtomicReference<AclPolicyConfig> policyConfig = new AtomicReference<>();
    private DBCollection aclCollection;

    public static final String ACE_REALM = "realm";
    public static final String ACE_USER_ID = "userId";
    public static final String ACE_ROLE_NAME = "roleName";
    public static final String ACE_RESOURCE_PATH = "resourcePath";
    public static final String ACE_ACTIONS = "actions";
    public static final String ACE_PERMITTED = "permitted";

    public AclPolicy(DBCollection aclCollection) {
        this.aclCollection = aclCollection;
    }

    public void setPolicyConfig(AclPolicyConfig policyConfig) {
        this.policyConfig.set(policyConfig);
    }

    public ResourceState autocreateAce(ResourceResponse createdResourceResponse) {
        ResourceState createdEntries = new DefaultResourceState("createdEntries");

        ResourcePath parentResourcePath = createdResourceResponse.inReplyTo().resourcePath();
        String parentResourceURI = parentResourcePath.toString();
        String createdResourceURI = parentResourceURI + "/" + createdResourceResponse.resource().id();

        List<AutoRuleConfig> autoRules = this.policyConfig.get().getAutoRules();
        autoRules.stream().filter((autoRule) -> {
            // We want exact matching (like "/storage/todos"), no support for wildcards for now
            return autoRule.getResourcePath().equals(parentResourceURI);
        }).forEach((autoRule) -> {
            ResourceState createdAceState = createACE(createdResourceURI, createdResourceResponse.inReplyTo().requestContext().securityContext(), autoRule);
            createdEntries.addMember(createdAceState);
        });

        return createdEntries;
    }

    public ResourceState deleteAce(ResourceResponse deletedResourceResponse) {
        ResourceState deletedEntries = new DefaultResourceState("deletedEntries");

        ResourcePath deletedResourcePath = deletedResourceResponse.inReplyTo().resourcePath();

        // Delete all ACE entries for this resource
        DBObject query = new BasicDBObject();
        query.put(ACE_RESOURCE_PATH, deletedResourcePath.toString());
        this.aclCollection.remove(query);

        log.debugf("Deleted ACEs for path: %s", deletedResourcePath);
        return deletedEntries;
    }

    private ResourceState createACE(String createdResourceURI, SecurityContext securityContext, AutoRuleConfig autoRuleConfig) {
        DBObject dbObject = new BasicDBObject();
        dbObject.put(ACE_REALM, securityContext.getRealm());
        dbObject.put(ACE_USER_ID, securityContext.getSubject());
        dbObject.put(ACE_RESOURCE_PATH, createdResourceURI);
        dbObject.put(ACE_ACTIONS, autoRuleConfig.getAutoAddedOwnerPermissions().toArray());
        dbObject.put(ACE_PERMITTED, true);
        this.aclCollection.insert(dbObject);

        log.debug("Created ACE: " + dbObject);

        ResourceState createdState = new DefaultResourceState();
        for (String key : dbObject.keySet()) {
            createdState.putProperty(key, dbObject.get(key));
        }
        return createdState;
    }

    public AuthzDecision isAuthorized(RequestContext req) {
        RequestType reqType = req.requestType();
        ResourcePath resourcePath = req.resourcePath();
        SecurityContext securityContext = req.securityContext();

        BasicDBObject query = new BasicDBObject();
        query.put(ACE_REALM, securityContext.getRealm());
        query.put(ACE_RESOURCE_PATH, resourcePath.toString());
        query.put(ACE_ACTIONS, reqType.toString());

        // Pass if we find rule for either "userId" or some of his roles
        List<DBObject> userRolesCondition = new LinkedList<>();
        userRolesCondition.add(new BasicDBObject(ACE_USER_ID, securityContext.getSubject()));
        if (securityContext.getRoles() != null) {
            for (String role : securityContext.getRoles()) {
                userRolesCondition.add(new BasicDBObject(ACE_ROLE_NAME, role));
            }
        }
        query.put("$or", userRolesCondition);

        if (log.isTraceEnabled()) {
            log.trace("Sending ACE query: " + query);
        }

        DBCursor results = this.aclCollection.find(query);

        AuthzDecision decision = AuthzDecision.IGNORE;
        for (DBObject result : results) {
            boolean currentDec = (Boolean) result.get(ACE_PERMITTED);

            // For now, always merge. No rule priorities...
            AuthzDecision currentDecision = currentDec ? AuthzDecision.ACCEPT : AuthzDecision.REJECT;
            decision = decision.mergeDecision(currentDecision);

            if (log.isTraceEnabled()) {
                log.trace("Found result: " + result);
            }
        }
        return decision;
    }
}
