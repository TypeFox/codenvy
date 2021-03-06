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
package com.codenvy.api.license.server;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import com.codenvy.api.license.SystemLicense;
import com.codenvy.api.license.SystemLicenseFeature;
import com.codenvy.api.license.exception.InvalidSystemLicenseException;
import com.codenvy.api.license.exception.SystemLicenseException;
import com.codenvy.api.license.exception.SystemLicenseNotFoundException;
import com.codenvy.api.license.shared.dto.LegalityDto;

import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.dto.server.JsonStringMapImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.String.valueOf;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.status;
import static org.eclipse.che.dto.server.DtoFactory.newDto;

/**
 * @author Anatoliy Bazko
 * @author Dmytro Nochevnov
 * @author Alexander Andrienko
 */
@Path("/license/system")
@Api(value = "license-system", description = "System License service")
public class SystemLicenseService {
    private static final Logger LOG                                 = LoggerFactory.getLogger(SystemLicenseService.class);
    public static final  String CODENVY_LICENSE_PROPERTY_IS_EXPIRED = "isExpired";

    private final SystemLicenseManager licenseManager;

    @Inject
    public SystemLicenseService(SystemLicenseManager licenseManager) {
        this.licenseManager = licenseManager;
    }

    @DELETE
    @ApiOperation(value = "Deletes system license")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "The license successfully removed"),
                           @ApiResponse(code = 500, message = "Server error"),
                           @ApiResponse(code = 404, message = "License not found")})
    public void deleteLicense() throws ApiException {
        try {
            licenseManager.remove();
        } catch (SystemLicenseNotFoundException e) {
            throw new NotFoundException(e.getMessage());
        } catch (SystemLicenseException e) {
            LOG.error(e.getMessage(), e);
            throw new ServerException(e.getMessage(), e);
        }
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @ApiOperation(value = "Gets system license")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"),
                           @ApiResponse(code = 409, message = "Invalid license"),
                           @ApiResponse(code = 404, message = "License not found"),
                           @ApiResponse(code = 500, message = "Server error")})
    public Response getLicense() throws ApiException {
        try {
            SystemLicense systemLicense = licenseManager.load();
            return status(OK).entity(systemLicense.getLicenseText()).build();
        } catch (InvalidSystemLicenseException e) {
            throw new ConflictException(e.getMessage());
        } catch (SystemLicenseNotFoundException e) {
            throw new NotFoundException(e.getMessage());
        } catch (SystemLicenseException e) {
            LOG.error(e.getMessage(), e);
            throw new ServerException(e.getMessage(), e);
        }
    }

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @ApiOperation(value = "Stores valid system license in the system")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "OK"),
                           @ApiResponse(code = 409, message = "Invalid license"),
                           @ApiResponse(code = 500, message = "Server error")})
    public Response storeLicense(String license) throws ApiException {
        try {
            licenseManager.store(license);
            return status(CREATED).build();
        } catch (InvalidSystemLicenseException e) {
            throw new ConflictException(e.getMessage());
        } catch (SystemLicenseException e) {
            LOG.error(e.getMessage(), e);
            throw new ServerException(e.getMessage(), e);
        }
    }

    @GET
    @Path("/properties")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Loads system license properties")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"),
                           @ApiResponse(code = 404, message = "License not found"),
                           @ApiResponse(code = 409, message = "Invalid license"),
                           @ApiResponse(code = 500, message = "Server error")})
    public Response getLicenseProperties() throws ApiException {
        try {
            SystemLicense systemLicense = licenseManager.load();
            Map<SystemLicenseFeature, String> features = systemLicense.getFeatures();

            Map<String, String> properties = features
                .entrySet()
                .stream()
                .collect(Collectors.toMap(entry -> entry.getKey().toString(), Map.Entry::getValue));

            boolean licenseExpired = systemLicense.isExpired();
            properties.put(CODENVY_LICENSE_PROPERTY_IS_EXPIRED, valueOf(licenseExpired));

            return status(OK).entity(new JsonStringMapImpl<>(properties)).build();
        } catch (SystemLicenseNotFoundException e) {
            throw new NotFoundException(e.getMessage());
        } catch (InvalidSystemLicenseException e) {
            throw new ConflictException(e.getMessage());
        } catch (SystemLicenseException e) {
            LOG.error(e.getMessage(), e);
            throw new ServerException(e.getMessage(), e);
        }
    }

    @GET
    @Path("/legality")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Checks if Codenvy usage matches system License constraints, or free usage rules.")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"),
                           @ApiResponse(code = 500, message = "Server error")})
    public LegalityDto isSystemUsageLegal() throws ApiException {
        try {
            return newDto(LegalityDto.class).withIsLegal(licenseManager.isSystemUsageLegal())
                                            .withIssues(licenseManager.getLicenseIssues());
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            throw new ServerException("Failed to check if Codenvy usage matches system License constraints.", e);
        }
    }

    @POST
    @Path("fair-source-license")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Acceptance of Codenvy Fair Source License")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK"),
                           @ApiResponse(code = 400, message = "Inappropriate accept request"),
                           @ApiResponse(code = 409, message = "Fair Source License has been already accepted"),
                           @ApiResponse(code = 500, message = "Server error")})
    public Response acceptFairSourceLicense() throws ApiException {
        licenseManager.acceptFairSourceLicense();
        return status(CREATED).build();
    }
}
