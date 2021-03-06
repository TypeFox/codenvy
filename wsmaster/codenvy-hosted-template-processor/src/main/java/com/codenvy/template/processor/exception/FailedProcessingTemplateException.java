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
package com.codenvy.template.processor.exception;

/**
 * Should be thrown when unable to process HTML template.
 *
 * @author Anton Korneta
 */
public class FailedProcessingTemplateException extends Exception {

    public FailedProcessingTemplateException(String message) {
        super(message);
    }

}
