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
package com.codenvy.resource.api;

import com.codenvy.resource.model.Resource;

import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;

import java.util.Optional;

/**
 * Tracks usage of resources of specified type.
 *
 * @author Sergii Leschenko
 */
public interface ResourceUsageTracker {
    /**
     * Returns used resource by given account.
     *
     * @param accountId
     *         account id to fetch used resource
     * @return used resource by given account
     * @throws NotFoundException
     *         when account with specified id was not found
     * @throws ServerException
     *         when some exception occurs on used resources fetching
     */
    Optional<Resource> getUsedResource(String accountId) throws NotFoundException, ServerException;
}
