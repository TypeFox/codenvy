/*
 * CODENVY CONFIDENTIAL
 * __________________
 *
 *  [2012] - [2015] Codenvy, S.A.
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */
package com.codenvy.api.account.subscription.saas;

import com.codenvy.api.account.AccountLocker;
import com.codenvy.api.account.billing.BillingPeriod;
import com.codenvy.api.account.billing.CreditCardDao;
import com.codenvy.api.account.impl.shared.dto.CreditCard;
import com.codenvy.api.account.metrics.MeterBasedStorage;
import com.codenvy.api.account.server.dao.Account;
import com.codenvy.api.account.server.dao.AccountDao;
import com.codenvy.api.account.server.dao.Subscription;
import com.codenvy.api.account.server.subscription.SubscriptionService;
import com.codenvy.api.account.shared.dto.UsedAccountResources;
import com.codenvy.api.account.shared.dto.WorkspaceResources;
import com.codenvy.api.account.subscription.SubscriptionEvent;
import com.codenvy.api.account.subscription.service.util.SubscriptionMailSender;
import com.codenvy.api.core.ApiException;
import com.codenvy.api.core.ConflictException;
import com.codenvy.api.core.ForbiddenException;
import com.codenvy.api.core.NotFoundException;
import com.codenvy.api.core.ServerException;
import com.codenvy.api.core.notification.EventService;
import com.codenvy.api.workspace.server.dao.Workspace;
import com.codenvy.api.workspace.server.dao.WorkspaceDao;
import com.codenvy.dto.server.DtoFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.codenvy.api.account.subscription.ServiceId.SAAS;

/**
 * @author Sergii Leschenko
 */
@Singleton
public class SaasSubscriptionService extends SubscriptionService {
    private static final Logger LOG = LoggerFactory.getLogger(SaasSubscriptionService.class);

    private final WorkspaceDao           workspaceDao;
    private final AccountDao             accountDao;
    private final MeterBasedStorage      meterBasedStorage;
    private final BillingPeriod          billingPeriod;
    private final AccountLocker          accountLocker;
    private final CreditCardDao          creditCardDao;
    private final EventService           eventService;
    private final SubscriptionMailSender mailSender;

    @Inject
    public SaasSubscriptionService(WorkspaceDao workspaceDao,
                                   AccountDao accountDao,
                                   MeterBasedStorage meterBasedStorage,
                                   BillingPeriod billingPeriod,
                                   AccountLocker accountLocker,
                                   CreditCardDao creditCardDao,
                                   EventService eventService,
                                   SubscriptionMailSender mailSender) {
        super(SAAS, SAAS);
        this.workspaceDao = workspaceDao;
        this.accountDao = accountDao;
        this.meterBasedStorage = meterBasedStorage;
        this.billingPeriod = billingPeriod;
        this.accountLocker = accountLocker;
        this.creditCardDao = creditCardDao;
        this.eventService = eventService;
        this.mailSender = mailSender;
    }

    @Override
    public void beforeCreateSubscription(Subscription subscription) throws ConflictException, ServerException, ForbiddenException {
        //TODO Add checking unpaid old subscription
        try {
            final Subscription activeSaasSubscription = accountDao.getActiveSubscription(subscription.getAccountId(), getServiceId());

            if (activeSaasSubscription != null && !"sas-community".equals(activeSaasSubscription.getPlanId())) {
                throw new ConflictException(SUBSCRIPTION_LIMIT_EXHAUSTED_MESSAGE);
            }
        } catch (NotFoundException e) {
            LOG.error(e.getLocalizedMessage(), e);
            throw new ServerException(e.getLocalizedMessage());
        }

        if (subscription.getUsePaymentSystem()) {//TODO Is need to checking of credit card if account will use trial?
            final List<CreditCard> cards = creditCardDao.getCards(subscription.getAccountId());
            if (cards.isEmpty()) {
                throw new ConflictException("You can't add subscription without credit card");
            }
        }
    }

    @Override
    public void afterCreateSubscription(Subscription subscription) throws ApiException {
        eventService.publish(SubscriptionEvent.subscriptionAddedEvent(subscription));

        accountLocker.unlockResources(subscription.getAccountId());
        mailSender.sendSaasSignupNotification(subscription.getAccountId());
    }

    @Override
    public void onRemoveSubscription(Subscription subscription) throws ApiException {
        eventService.publish(SubscriptionEvent.subscriptionRemovedEvent(subscription));
    }

    @Override
    public void onUpdateSubscription(Subscription oldSubscription, Subscription newSubscription) throws ApiException {

    }

    @Override
    public void onCheckSubscriptions() throws ApiException {
        //TODO Implement
    }

    @Override
    public UsedAccountResources getAccountResources(Subscription subscription) throws ServerException {
        final String accountId = subscription.getAccountId();
        final Map<String, Double> memoryUsedReport = meterBasedStorage.getMemoryUsedReport(accountId,
                                                                                           billingPeriod.getCurrent().getStartDate()
                                                                                                        .getTime(),
                                                                                           System.currentTimeMillis());
        List<Workspace> workspaces = workspaceDao.getByAccount(accountId);
        List<WorkspaceResources> result = new ArrayList<>();

        for (Workspace workspace : workspaces) {
            Double usedMemory = memoryUsedReport.get(workspace.getId());
            result.add(DtoFactory.getInstance().createDto(WorkspaceResources.class)
                                 .withWorkspaceId(workspace.getId())
                                 .withMemory(usedMemory != null ? usedMemory : 0.0));
        }

        return DtoFactory.getInstance().createDto(UsedAccountResources.class)
                         .withUsed(result);
    }

}
