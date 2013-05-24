/*
 *    Copyright (C) 2013 Codenvy.
 *
 */
package com.codenvy.analytics.client.presenter;

import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/**
 * @author <a href="mailto:abazko@codenvy.com">Anatoliy Bazko</a>
 */
public class WorkspaceViewPresenter extends MainViewPresenter {

    public interface Display extends MainViewPresenter.Display {
        Label getLabel();

        Widget asWidget();
    }

    public WorkspaceViewPresenter(HandlerManager eventBus, Display view) {
        super(eventBus, view);
    }

    public void bind() {
        super.bind();
    }
}
