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
package com.codenvy.api.workspace;

import com.codenvy.resource.api.exception.NoEnoughResourcesException;
import com.codenvy.resource.api.type.RamResourceType;
import com.codenvy.resource.api.type.RuntimeResourceType;
import com.codenvy.resource.api.type.WorkspaceResourceType;
import com.codenvy.resource.api.usage.ResourceUsageManager;
import com.codenvy.resource.api.usage.ResourcesLocks;
import com.codenvy.resource.api.usage.tracker.EnvironmentRamCalculator;
import com.codenvy.resource.model.Resource;
import com.codenvy.resource.spi.impl.ResourceImpl;
import com.codenvy.service.system.SystemRamInfoProvider;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

import org.eclipse.che.account.api.AccountManager;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.workspace.Environment;
import org.eclipse.che.api.core.model.workspace.Workspace;
import org.eclipse.che.api.core.model.workspace.WorkspaceConfig;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.machine.server.spi.SnapshotDao;
import org.eclipse.che.api.workspace.server.WorkspaceManager;
import org.eclipse.che.api.workspace.server.WorkspaceRuntimes;
import org.eclipse.che.api.workspace.server.WorkspaceSharedPool;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceImpl;
import org.eclipse.che.api.workspace.server.spi.WorkspaceDao;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.lang.Size;
import org.eclipse.che.commons.lang.concurrent.Unlocker;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.util.Collections.singletonList;

/**
 * Manager that checks limits and delegates all its operations to the {@link WorkspaceManager}.
 * Doesn't contain any logic related to start/stop or any kind of operations different from limits checks.
 *
 * @author Yevhenii Voevodin
 * @author Igor Vinokur
 * @author Sergii Leschenko
 */
@Singleton
public class LimitsCheckingWorkspaceManager extends WorkspaceManager {

    private final SystemRamInfoProvider    systemRamInfoProvider;
    private final EnvironmentRamCalculator environmentRamCalculator;
    private final ResourceUsageManager     resourceUsageManager;
    private final ResourcesLocks           resourcesLocks;
    private final AccountManager           accountManager;

    private final long maxRamPerEnvMB;

    @VisibleForTesting
    Semaphore startSemaphore;

    @Inject
    public LimitsCheckingWorkspaceManager(WorkspaceDao workspaceDao,
                                          WorkspaceRuntimes runtimes,
                                          EventService eventService,
                                          AccountManager accountManager,
                                          @Named("che.workspace.auto_snapshot") boolean defaultAutoSnapshot,
                                          @Named("che.workspace.auto_restore") boolean defaultAutoRestore,
                                          SnapshotDao snapshotDao,
                                          WorkspaceSharedPool sharedPool,
                                          //own injects
                                          @Named("limits.workspace.env.ram") String maxRamPerEnv,
                                          @Named("limits.workspace.start.throughput") int maxSameTimeStartWSRequests,
                                          SystemRamInfoProvider systemRamInfoProvider,
                                          EnvironmentRamCalculator environmentRamCalculator,
                                          ResourceUsageManager resourceUsageManager,
                                          ResourcesLocks resourcesLocks) {
        super(workspaceDao, runtimes, eventService, accountManager, defaultAutoSnapshot, defaultAutoRestore, snapshotDao, sharedPool);
        this.systemRamInfoProvider = systemRamInfoProvider;
        this.environmentRamCalculator = environmentRamCalculator;
        this.maxRamPerEnvMB = "-1".equals(maxRamPerEnv) ? -1 : Size.parseSizeToMegabytes(maxRamPerEnv);
        this.resourceUsageManager = resourceUsageManager;
        this.resourcesLocks = resourcesLocks;
        this.accountManager = accountManager;

        if (maxSameTimeStartWSRequests > 0) {
            this.startSemaphore = new Semaphore(maxSameTimeStartWSRequests);
        }
    }

    @Override
    public WorkspaceImpl createWorkspace(WorkspaceConfig config,
                                         String namespace) throws ServerException,
                                                                  ConflictException,
                                                                  NotFoundException {
        checkMaxEnvironmentRam(config);
        String accountId = accountManager.getByName(namespace).getId();
        try (@SuppressWarnings("unused") Unlocker u = resourcesLocks.lock(accountId)) {
            checkWorkspaceResourceAvailability(accountId);

            return super.createWorkspace(config, namespace);
        }
    }

    @Override
    public WorkspaceImpl createWorkspace(WorkspaceConfig config,
                                         String namespace,
                                         Map<String, String> attributes) throws ServerException,
                                                                                NotFoundException,
                                                                                ConflictException {
        checkMaxEnvironmentRam(config);
        String accountId = accountManager.getByName(namespace).getId();
        try (@SuppressWarnings("unused") Unlocker u = resourcesLocks.lock(accountId)) {
            checkWorkspaceResourceAvailability(accountId);

            return super.createWorkspace(config, namespace, attributes);
        }
    }

    @Override
    public WorkspaceImpl startWorkspace(String workspaceId,
                                        @Nullable String envName,
                                        @Nullable Boolean restore) throws NotFoundException,
                                                                          ServerException,
                                                                          ConflictException {
        WorkspaceImpl workspace = this.getWorkspace(workspaceId);
        String accountId = workspace.getAccount().getId();

        try (@SuppressWarnings("unused") Unlocker u = resourcesLocks.lock(accountId)) {
            checkRuntimeResourceAvailability(accountId);
            checkRamResourcesAvailability(accountId, workspace.getNamespace(), workspace.getConfig(), envName);

            return checkSystemRamLimitAndPropagateLimitedThroughputStart(() -> super.startWorkspace(workspaceId, envName, restore));
        }
    }

    @Override
    public WorkspaceImpl startWorkspace(WorkspaceConfig config,
                                        String namespace,
                                        boolean isTemporary) throws ServerException,
                                                                    NotFoundException,
                                                                    ConflictException {
        checkMaxEnvironmentRam(config);

        String accountId = accountManager.getByName(namespace).getId();
        try (@SuppressWarnings("unused") Unlocker u = resourcesLocks.lock(accountId)) {
            checkWorkspaceResourceAvailability(accountId);
            checkRuntimeResourceAvailability(accountId);
            checkRamResourcesAvailability(accountId, namespace, config, null);

            return checkSystemRamLimitAndPropagateLimitedThroughputStart(() -> super.startWorkspace(config, namespace, isTemporary));
        }
    }

    @Override
    public WorkspaceImpl updateWorkspace(String id, Workspace update) throws ConflictException,
                                                                             ServerException,
                                                                             NotFoundException {
        checkMaxEnvironmentRam(update.getConfig());

        WorkspaceImpl workspace = this.getWorkspace(id);
        String accountId = workspace.getAccount().getId();

        // Workspace must not be updated while the manager checks it's resources to allow start
        try (@SuppressWarnings("unused") Unlocker u = resourcesLocks.lock(accountId)) {
            return super.updateWorkspace(id, update);
        }
    }

    /**
     * Defines callback which should be called when all necessary checks are performed.
     * Helps to propagate actions to the super class.
     */
    @FunctionalInterface
    @VisibleForTesting
    interface WorkspaceCallback<T extends WorkspaceImpl> {
        T call() throws ConflictException, NotFoundException, ServerException;
    }

    /**
     * One of the checks in {@link #checkSystemRamLimitAndPropagateStart(WorkspaceCallback)}
     * is needed to deny starting workspace, if system RAM limit exceeded.
     * This check may be slow because it is based on request to swarm for memory amount allocated on all nodes, but it
     * can't be performed more than specified times at the same time, and the semaphore is used to control that.
     * The semaphore is a trade off between speed and risk to exceed system RAM limit.
     * In the worst case specified number of permits to start workspace can happen at the same time after the actually
     * system limit allows to start only one workspace, all permits will be allowed to start workspace.
     * If more than specified number of permits to start workspace happens, they will wait in a queue.
     * limits.workspace.start.throughput property configures how many permits can be handled at the same time.
     */
    @VisibleForTesting
    <T extends WorkspaceImpl> T checkSystemRamLimitAndPropagateLimitedThroughputStart(WorkspaceCallback<T> callback)
            throws ServerException, NotFoundException, ConflictException {
        if (startSemaphore == null) {
            return checkSystemRamLimitAndPropagateStart(callback);
        } else {
            try {
                startSemaphore.acquire();
                return checkSystemRamLimitAndPropagateStart(callback);
            } catch (InterruptedException e) {
                currentThread().interrupt();
                throw new ServerException(e.getMessage(), e);
            } finally {
                startSemaphore.release();
            }
        }
    }

    /**
     * Checks that starting workspace won't exceed system RAM limit.
     * Then, if previous check is passed, checks that starting workspace won't exceed user's started workspaces number limit.
     * Throws {@link LimitExceededException} in the case of constraints violation, otherwise
     * performs {@code callback.call()} and returns its result.
     */
    @VisibleForTesting
    <T extends WorkspaceImpl> T checkSystemRamLimitAndPropagateStart(WorkspaceCallback<T> callback)
            throws ServerException, NotFoundException, ConflictException {
        if (systemRamInfoProvider.getSystemRamInfo().isSystemRamLimitExceeded()) {
            throw new LimitExceededException("Low RAM. Your workspace cannot be started until the system has more RAM available.");
        }

        return callback.call();
    }

    @VisibleForTesting
    void checkMaxEnvironmentRam(WorkspaceConfig config) throws ServerException {
        if (maxRamPerEnvMB < 0) {
            return;
        }
        for (Map.Entry<String, ? extends Environment> envEntry : config.getEnvironments().entrySet()) {
            Environment env = envEntry.getValue();
            final long workspaceRam = environmentRamCalculator.calculate(env);
            if (workspaceRam > maxRamPerEnvMB) {
                throw new LimitExceededException(format("You are only allowed to use %d mb. RAM per workspace.", maxRamPerEnvMB),
                                                 ImmutableMap.of("environment_max_ram", Long.toString(maxRamPerEnvMB),
                                                                 "environment_max_ram_unit", "mb",
                                                                 "environment_ram", Long.toString(workspaceRam),
                                                                 "environment_ram_unit", "mb"));
            }
        }
    }

    @VisibleForTesting
    void checkRamResourcesAvailability(String accountId, String namespace, WorkspaceConfig config, @Nullable String envName)
            throws NotFoundException, ServerException, ConflictException {

        final Environment environment = config.getEnvironments().get(firstNonNull(envName, config.getDefaultEnv()));
        final ResourceImpl ramToUse = new ResourceImpl(RamResourceType.ID,
                                                       environmentRamCalculator.calculate(environment),
                                                       RamResourceType.UNIT);
        try {
            resourceUsageManager.checkResourcesAvailability(accountId, singletonList(ramToUse));
        } catch (NoEnoughResourcesException e) {
            final Resource requiredRam = e.getRequiredResources().get(0);// starting of workspace requires only RAM resource
            final Resource availableRam = getResourceOrDefault(e.getAvailableResources(),
                                                               RamResourceType.ID, 0, RamResourceType.UNIT);
            final Resource usedRam = getResourceOrDefault(resourceUsageManager.getUsedResources(accountId),
                                                          RamResourceType.ID, 0, RamResourceType.UNIT);

            throw new LimitExceededException(format("Workspace %s/%s needs %s to start. Your account has %s available and %s in use. " +
                                                    "The workspace can't be start. Stop other workspaces or grant more resources.",
                                                    namespace,
                                                    config.getName(),
                                                    printResourceInfo(requiredRam),
                                                    printResourceInfo(availableRam),
                                                    printResourceInfo(usedRam)));
        }
    }

    @VisibleForTesting
    void checkWorkspaceResourceAvailability(String accountId) throws NotFoundException, ServerException {
        try {
            resourceUsageManager.checkResourcesAvailability(accountId,
                                                            singletonList(new ResourceImpl(WorkspaceResourceType.ID,
                                                                                           1,
                                                                                           WorkspaceResourceType.UNIT)));
        } catch (NoEnoughResourcesException e) {
            throw new LimitExceededException("You are not allowed to create more workspaces.");
        }
    }

    @VisibleForTesting
    void checkRuntimeResourceAvailability(String accountId) throws NotFoundException, ServerException {
        try {
            resourceUsageManager.checkResourcesAvailability(accountId,
                                                            singletonList(new ResourceImpl(RuntimeResourceType.ID,
                                                                                           1,
                                                                                           RuntimeResourceType.UNIT)));
        } catch (NoEnoughResourcesException e) {
            throw new LimitExceededException("You are not allowed to start more workspaces.");
        }
    }

    /**
     * Returns resource with specified type from list or resource with specified default amount if list doesn't contain it
     */
    private Resource getResourceOrDefault(List<? extends Resource> resources, String resourceType, long defaultAmount, String defaultUnit) {
        Optional<? extends Resource> resource = getResource(resources, resourceType);
        if (resource.isPresent()) {
            return resource.get();
        } else {
            return new ResourceImpl(resourceType, defaultAmount, defaultUnit);
        }
    }

    /**
     * Returns resource with specified type from list
     */
    private Optional<? extends Resource> getResource(List<? extends Resource> resources, String resourceType) {
        return resources.stream()
                        .filter(r -> r.getType().equals(resourceType))
                        .findAny();
    }

    private String printResourceInfo(Resource resource) {
        return resource.getAmount() + resource.getUnit().toUpperCase();
    }
}
