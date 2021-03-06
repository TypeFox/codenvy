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
package com.codenvy.api.license.server.spi.tck;

import com.codenvy.api.license.server.jpa.JpaSystemLicenseActionDao;
import com.codenvy.api.license.server.model.impl.SystemLicenseActionImpl;
import com.codenvy.api.license.shared.model.Constants;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.commons.test.tck.TckListener;
import org.eclipse.che.commons.test.tck.repository.TckRepository;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import static com.codenvy.api.license.shared.model.Constants.Action.ACCEPTED;
import static com.codenvy.api.license.shared.model.Constants.Action.ADDED;
import static com.codenvy.api.license.shared.model.Constants.Action.EXPIRED;
import static com.codenvy.api.license.shared.model.Constants.PaidLicense.FAIR_SOURCE_LICENSE;
import static com.codenvy.api.license.shared.model.Constants.PaidLicense.PRODUCT_LICENSE;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * @author Anatolii Bazko
 */
@Listeners(TckListener.class)
@Test(suiteName = "systemLicenseActionDaoTck")
public class SystemLicenseActionDaoTest {
    public static final String LICENSE_ID = "licenseId1";

    private SystemLicenseActionImpl systemLicenseActions[];

    @Inject
    private TckRepository<SystemLicenseActionImpl> codenvyLicenseRepository;
    @Inject
    private EventService                           eventService;
    @Inject
    private JpaSystemLicenseActionDao              dao;

    @BeforeMethod
    public void setUp() throws Exception {
        systemLicenseActions = new SystemLicenseActionImpl[] {new SystemLicenseActionImpl(FAIR_SOURCE_LICENSE,
                                                                                          ACCEPTED,
                                                                                           System.currentTimeMillis(),
                                                                                           null,
                                                                                           ImmutableMap.of("prop1", "value1",
                                                                                                           "prop2", "value2",
                                                                                                           "prop3", "value2")),
                                                               new SystemLicenseActionImpl(PRODUCT_LICENSE,
                                                                                           ADDED,
                                                                                           System.currentTimeMillis(),
                                                                                           LICENSE_ID,
                                                                                           ImmutableMap.of("prop4", "value4")),
                                                               new SystemLicenseActionImpl(PRODUCT_LICENSE,
                                                                                           Constants.Action.EXPIRED,
                                                                                           System.currentTimeMillis(),
                                                                                           LICENSE_ID,
                                                                                           ImmutableMap.of())};
        codenvyLicenseRepository.createAll(asList(systemLicenseActions));
    }


    @AfterMethod
    public void cleanUp() throws Exception {
        codenvyLicenseRepository.removeAll();
    }

    @Test
    public void shouldInsertNewRecord() throws Exception {
        dao.insert(new SystemLicenseActionImpl(FAIR_SOURCE_LICENSE,
                                               EXPIRED,
                                               System.currentTimeMillis(),
                                               null,
                                               ImmutableMap.of("prop1", "value1")));

        SystemLicenseActionImpl action = dao.getByLicenseTypeAndAction(FAIR_SOURCE_LICENSE, EXPIRED);
        assertNotNull(action);
    }


    @Test(expectedExceptions = ConflictException.class)
    public void shouldThrowConflictExceptionOnInsertIfRecordExists() throws Exception {
        dao.insert(new SystemLicenseActionImpl(FAIR_SOURCE_LICENSE,
                                               ACCEPTED,
                                               System.currentTimeMillis(),
                                               null,
                                               ImmutableMap.of()));
    }

    @Test
    public void shouldFindRecordByLicenseTypeAndAction() throws Exception {
        SystemLicenseActionImpl systemLicenseAction = dao.getByLicenseTypeAndAction(FAIR_SOURCE_LICENSE, ACCEPTED);

        assertNotNull(systemLicenseAction);
        assertEquals(systemLicenseAction.getAttributes().size(), 3);
        assertEquals(systemLicenseAction.getAttributes().get("prop1"), "value1");
        assertEquals(systemLicenseAction.getAttributes().get("prop2"), "value2");
        assertEquals(systemLicenseAction.getAttributes().get("prop3"), "value2");
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void shouldThrowNotFoundExceptionIfRecordWithLicenseTypeAbsent() throws Exception {
        dao.getByLicenseTypeAndAction(FAIR_SOURCE_LICENSE, Constants.Action.EXPIRED);
    }

    @Test
    public void shouldFindRecordByLicenseIdAndAction() throws Exception {
        SystemLicenseActionImpl systemLicenseAction = dao.getByLicenseIdAndAction(LICENSE_ID, ADDED);

        assertNotNull(systemLicenseAction);
        assertEquals(systemLicenseAction.getAttributes().size(), 1);
        assertEquals(systemLicenseAction.getAttributes().get("prop4"), "value4");
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void shouldThrowNotFoundExceptionIfRecordWithLicenseIdAbsent() throws Exception {
        dao.getByLicenseIdAndAction("non-exists-id", Constants.Action.EXPIRED);
    }


    @Test(expectedExceptions = NotFoundException.class)
    public void shouldRemoveRecord() throws Exception {
        assertNotNull(dao.getByLicenseTypeAndAction(PRODUCT_LICENSE, ADDED));

        dao.remove(PRODUCT_LICENSE, ADDED);

        dao.getByLicenseTypeAndAction(PRODUCT_LICENSE, ADDED);
    }

    @Test
    public void shouldNotThrowExceptionOnRemoveIfRecordAbsent() throws Exception {
        dao.remove(PRODUCT_LICENSE, ADDED);
        dao.remove(PRODUCT_LICENSE, ADDED);
    }

    @Test
    public void shouldUpdaterRecord() throws Exception {
        SystemLicenseActionImpl action = dao.getByLicenseTypeAndAction(FAIR_SOURCE_LICENSE, ACCEPTED);
        assertEquals(action.getAttributes().get("prop1"), "value1");

        dao.upsert(new SystemLicenseActionImpl(FAIR_SOURCE_LICENSE,
                                               ACCEPTED,
                                               System.currentTimeMillis(),
                                               null,
                                               ImmutableMap.of("prop2", "value2")));

        action = dao.getByLicenseTypeAndAction(FAIR_SOURCE_LICENSE, ACCEPTED);
        assertEquals(action.getAttributes().size(), 1);
        assertEquals(action.getAttributes().get("prop2"), "value2");
    }
}
