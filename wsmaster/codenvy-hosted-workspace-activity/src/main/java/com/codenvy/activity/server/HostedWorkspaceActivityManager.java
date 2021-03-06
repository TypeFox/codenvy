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
package com.codenvy.activity.server;

import com.codenvy.resource.api.type.TimeoutResourceType;
import com.codenvy.resource.api.usage.ResourceUsageManager;
import com.codenvy.resource.model.Resource;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;

import org.eclipse.che.account.api.AccountManager;
import org.eclipse.che.account.shared.model.Account;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.workspace.Workspace;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.core.notification.EventSubscriber;
import org.eclipse.che.api.workspace.server.WorkspaceManager;
import org.eclipse.che.api.workspace.server.activity.WorkspaceActivityManager;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceImpl;
import org.eclipse.che.api.workspace.shared.dto.event.WorkspaceStatusEvent;
import org.eclipse.che.commons.schedule.ScheduleRate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.codenvy.activity.shared.Constants.ACTIVITY_CHECKER;
import static org.eclipse.che.api.workspace.shared.Constants.WORKSPACE_STOPPED_BY;

/**
 * Stops the inactive workspaces by given expiration time.
 *
 * <p>Note that the workspace is not stopped immediately, scheduler will stop the workspaces with one minute rate.
 * If workspace idle timeout is negative, then workspace would not be stopped automatically.
 *
 * @author Anton Korneta
 */
@Singleton
public class HostedWorkspaceActivityManager extends WorkspaceActivityManager {

    private static final Logger LOG = LoggerFactory.getLogger(HostedWorkspaceActivityManager.class);

    private final ResourceUsageManager resourceUsageManager;
    private final AccountManager       accountManager;

    @Inject
    public HostedWorkspaceActivityManager(ResourceUsageManager resourceUsageManager,
                                          AccountManager accountManager,
                                          WorkspaceManager workspaceManager,
                                          EventService eventService) {
        super(workspaceManager, eventService, -1);

        this.resourceUsageManager = resourceUsageManager;
        this.accountManager = accountManager;
    }

    @Override
    protected long getIdleTimeout(String workspaceId) throws NotFoundException, ServerException {
        WorkspaceImpl workspace = workspaceManager.getWorkspace(workspaceId);
        Account account = accountManager.getByName(workspace.getNamespace());
        List<? extends Resource> availableResources = resourceUsageManager.getAvailableResources(account.getId());
        Optional<? extends Resource> timeoutOpt = availableResources.stream()
                                                                    .filter(resource -> TimeoutResourceType.ID.equals(resource.getType()))
                                                                    .findAny();

        if (timeoutOpt.isPresent()) {
            return timeoutOpt.get().getAmount() * 60 * 1000;
        } else {
            return -1;
        }
    }
}
