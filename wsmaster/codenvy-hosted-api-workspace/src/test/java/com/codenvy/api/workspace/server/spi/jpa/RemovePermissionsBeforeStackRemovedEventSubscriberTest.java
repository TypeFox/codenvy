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
package com.codenvy.api.workspace.server.spi.jpa;

import com.codenvy.api.machine.server.recipe.RecipePermissionsImpl;
import com.codenvy.api.workspace.server.jpa.OnPremisesJpaWorkspaceModule;
import com.codenvy.api.workspace.server.spi.jpa.JpaStackPermissionsDao.RemovePermissionsBeforeStackRemovedEventSubscriber;
import com.codenvy.api.workspace.server.stack.StackPermissionsImpl;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.persist.jpa.JpaPersistModule;

import org.eclipse.che.api.machine.server.recipe.RecipeImpl;
import org.eclipse.che.api.user.server.model.impl.UserImpl;
import org.eclipse.che.api.workspace.server.jpa.JpaStackDao;
import org.eclipse.che.api.workspace.server.model.impl.stack.StackImpl;
import org.eclipse.che.commons.test.db.H2TestHelper;
import org.eclipse.che.core.db.DBInitializer;
import org.eclipse.che.core.db.schema.SchemaInitializer;
import org.eclipse.che.core.db.schema.impl.flyway.FlywaySchemaInitializer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.persistence.EntityManager;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.eclipse.che.commons.test.db.H2TestHelper.inMemoryDefault;
import static org.testng.Assert.assertEquals;

/**
 * Tests for {@link RemovePermissionsBeforeStackRemovedEventSubscriber}
 *
 * @author Sergii Leschenko
 */
public class RemovePermissionsBeforeStackRemovedEventSubscriberTest {
    private EntityManager          manager;
    private JpaStackDao            stackDao;
    private JpaStackPermissionsDao stackPermissionsDao;

    private RemovePermissionsBeforeStackRemovedEventSubscriber subscriber;

    private StackImpl              stack;
    private UserImpl[]             users;
    private StackPermissionsImpl[] stackPermissions;

    @BeforeClass
    public void setupEntities() throws Exception {
        stack = StackImpl.builder()
                         .setId("stack123")
                         .setName("defaultStack")
                         .build();
        users = new UserImpl[3];
        for (int i = 0; i < 3; i++) {
            users[i] = new UserImpl("user" + i, "user" + i + "@test.com", "username" + i);
        }
        stackPermissions = new StackPermissionsImpl[3];
        for (int i = 0; i < 3; i++) {
            stackPermissions[i] = new StackPermissionsImpl(users[i].getId(), stack.getId(), asList("read", "update"));
        }

        Injector injector = Guice.createInjector(new OnPremisesJpaWorkspaceModule(), new TestModule());

        manager = injector.getInstance(EntityManager.class);
        stackDao = injector.getInstance(JpaStackDao.class);
        stackPermissionsDao = injector.getInstance(JpaStackPermissionsDao.class);

        subscriber = injector.getInstance(RemovePermissionsBeforeStackRemovedEventSubscriber.class);
        subscriber.subscribe();
    }

    @BeforeMethod
    public void setUp() throws Exception {
        manager.getTransaction().begin();
        manager.persist(stack);
        Stream.of(users).forEach(manager::persist);
        Stream.of(stackPermissions).forEach(manager::persist);
        manager.getTransaction().commit();
    }

    @AfterMethod
    public void cleanup() {
        manager.getTransaction().begin();
        manager.createQuery("SELECT recipePermissions FROM RecipePermissions recipePermissions", RecipePermissionsImpl.class)
               .getResultList()
               .forEach(manager::remove);
        manager.createQuery("SELECT recipe FROM Recipe recipe", RecipeImpl.class)
               .getResultList()
               .forEach(manager::remove);
        manager.createQuery("SELECT usr FROM Usr usr", UserImpl.class)
               .getResultList()
               .forEach(manager::remove);
        manager.getTransaction().commit();
    }

    @AfterClass
    public void shutdown() throws Exception {
        subscriber.unsubscribe();
        manager.getEntityManagerFactory().close();
        H2TestHelper.shutdownDefault();
    }

    @Test
    public void shouldRemoveAllRecipePermissionsWhenRecipeIsRemoved() throws Exception {
        stackDao.remove(stack.getId());

        assertEquals(stackPermissionsDao.getByInstance(stack.getId(), 1, 0).getTotalItemsCount(), 0);
    }

    @Test
    public void shouldRemoveAllRecipePermissionsWhenPageSizeEqualsToOne() throws Exception {
        subscriber.removeStackPermissions(stack.getId(), 1);

        assertEquals(stackPermissionsDao.getByInstance(stack.getId(), 1, 0).getTotalItemsCount(), 0);
    }

    private static class TestModule extends AbstractModule {
        @Override
        protected void configure() {
            install(new JpaPersistModule("main"));
            bind(SchemaInitializer.class).toInstance(new FlywaySchemaInitializer(inMemoryDefault(), "che-schema", "codenvy-schema"));
            bind(DBInitializer.class).asEagerSingleton();
        }
    }
}
