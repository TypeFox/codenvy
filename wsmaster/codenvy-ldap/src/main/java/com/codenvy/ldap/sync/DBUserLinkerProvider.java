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
package com.codenvy.ldap.sync;

import org.eclipse.che.api.user.server.spi.UserDao;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

/**
 * Provides {@link DBUserLinker} instances based on configuration.
 *
 * @author Yevhenii Voevodin
 */
public class DBUserLinkerProvider implements Provider<DBUserLinker> {

    @Inject
    private UserDao userDao;

    @Inject
    private DBHelper dbHelper;

    @com.google.inject.Inject(optional = true)
    @Named("ldap.sync.user_linking_attribute")
    private String linkAttr;

    @Override
    public DBUserLinker get() {
        if (linkAttr == null || linkAttr.equals("id")) {
            return DBUserLinker.newIdLinker(userDao, dbHelper);
        }
        if (linkAttr.equals("email")) {
            return DBUserLinker.newEmailLinker(userDao, dbHelper);
        }
        if (linkAttr.equals("name")) {
            return DBUserLinker.newNameLinker(userDao, dbHelper);
        }
        throw new IllegalStateException("Supported values for the property 'ldap.sync.user_linking_attribute' " +
                                        "are 'id', 'email' or 'name");
    }
}
