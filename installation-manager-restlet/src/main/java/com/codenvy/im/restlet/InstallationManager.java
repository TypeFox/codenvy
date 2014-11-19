/*
 * CODENVY CONFIDENTIAL
 * __________________
 *
 *  [2012] - [2014] Codenvy, S.A.
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
package com.codenvy.im.restlet;

import com.codenvy.commons.json.JsonParseException;
import com.codenvy.im.artifacts.Artifact;
import com.codenvy.im.installer.InstallOptions;
import com.codenvy.im.user.UserCredentials;
import com.codenvy.im.utils.Version;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.SortedMap;

/**
 * @author Anatoliy Bazko
 */
public interface InstallationManager {

    /**
     * Install the specific version of the artifact with version greater than installed.
     *
     * @param authToken
     *         the authentication token
     * @throws java.io.IOException
     *         if an I/O error occurred
     */
    void install(String authToken, Artifact artifact, Version version, @Nullable InstallOptions options) throws IOException;

    /**
     * Scans all installed artifacts and returns their versions.
     *
     * @param authToken
     *         the authentication token
     * @throws java.io.IOException
     *         if an I/O error occurred
     */
    Map<Artifact, Version> getInstalledArtifacts(String authToken) throws IOException;

    /**
     * @return downloaded artifacts from the local repository
     * @throws IOException
     *         if an I/O error occurs
     */
    Map<Artifact, SortedMap<Version, Path>> getDownloadedArtifacts() throws IOException;

    /**
     * @return set of downloaded into local repository versions of artifact
     * @throws IOException
     *         if an I/O error occurs
     */
    SortedMap<Version, Path> getDownloadedVersions(Artifact artifact) throws IOException;

    /**
     * @param authToken
     *         the authentication token
     * @return the list of the artifacts to update.
     * @throws java.io.IOException
     *         if an I/O error occurred
     */
    Map<Artifact, Version> getUpdates(String authToken) throws IOException;

    /**
     * @param authToken
     *         the authentication token
     * @return version of artifact to update.
     * @throws java.io.IOException
     *         if an I/O error occurred
     */
    Version getLatestVersionToDownload(String authToken, Artifact artifact) throws IOException;


    /**
     * Download the specific version of the artifact.
     *
     * @return path to downloaded artifact
     * @throws java.io.IOException
     *         if an I/O error occurred
     * @throws java.lang.IllegalStateException
     *         if the subscription is invalid or expired
     */
    Path download(UserCredentials userCredentials, Artifact artifact, Version version) throws
                                                                                      IOException,
                                                                                      IllegalStateException;

    /** Checks if FS has enough free space, for instance to download artifacts */
    void checkEnoughDiskSpace(long size) throws IOException;

    /** Checks connection to server is available */
    void checkIfConnectionIsAvailable() throws IOException;

    /** @return the configuration */
    Map<String, String> getConfig();

    /** Sets new configuration */
    void setConfig(InstallationManagerConfig config) throws IOException;

    /**
     * @return path to artifact into the local repository
     * @throws java.io.IOException
     *         if an I/O error occurred
     */
    Path getPathToBinaries(Artifact artifact, Version version) throws IOException;

    /**
     * @return size in bytes of the artifact
     * @throws java.io.IOException
     *         if an I/O error occurred
     */
    Long getBinariesSize(Artifact artifact, Version version) throws IOException;

    /** Filters what need to download, either all updates or a specific one. */
    Map<Artifact, Version> getUpdatesToDownload(@Nullable final Artifact artifact,
                                               @Nullable final Version version,
                                               String authToken) throws IOException;
}
