/*******************************************************************************
 * Copyright (c) [2012] - [2017] Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 *******************************************************************************/
package com.codenvy.plugin.webhooks.vsts;

import com.google.common.collect.Sets;

import org.eclipse.che.commons.lang.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Wrapper that provides data for a configured VSTS 'pull request updated' webhook
 *
 * @author Stephane Tournie
 */
public class PullRequestUpdatedWebhook {

    private static final Logger LOG = LoggerFactory.getLogger(PullRequestUpdatedWebhook.class);

    private final String               id;
    private final String               host;
    private final String               account;
    private final String               collection;
    private final String               apiVersion;
    private final Pair<String, String> credentials;
    private final Set<String>          factoriesIds;

    public PullRequestUpdatedWebhook(final String host,
                                     final String account,
                                     final String collection,
                                     final String apiVersion,
                                     final Pair<String, String> credentials,
                                     final String... factoriesIds) {
        this.host = host;
        this.account = account;
        this.collection = collection;
        this.apiVersion = apiVersion;
        this.credentials = credentials;

        // No more than one webhook can be configured for a given combination of VSTS account/host/collection
        this.id = account + "-" + host + "-" + collection;

        if (factoriesIds != null && factoriesIds.length > 0) {
            this.factoriesIds = Sets.newHashSet(Arrays.asList(factoriesIds));
        } else {
            this.factoriesIds = Sets.newHashSet();
        }
    }

    public String getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public String getAccount() {
        return account;
    }

    public String getCollection() {
        return collection;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public Pair<String, String> getCredentials() {
        return credentials;
    }

    public Set<String> getFactoriesIds() {
        return factoriesIds;
    }

    public boolean addFactoryId(final String factoryId) {
        return this.factoriesIds.add(factoryId);
    }

    public boolean removeFactoryId(final String factoryId) {
        return this.factoriesIds.remove(factoryId);
    }
}
