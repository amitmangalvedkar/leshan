/*
 * Copyright (c) 2013, Sierra Wireless
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice,
 *       this list of conditions and the following disclaimer in the documentation
 *       and/or other materials provided with the distribution.
 *     * Neither the name of {{ project }} nor the names of its contributors
 *       may be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package leshan.server.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import leshan.server.security.NonUniqueSecurityInfoException;
import leshan.server.security.SecurityInfo;
import leshan.server.security.SecurityRegistry;
import leshan.util.Validate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An in-memory security store.
 * <p>
 * This implementation serializes the registry content into a file to be able to re-load the security infos when the
 * server is restarted.
 * </p>
 */
public class SecurityRegistryImpl implements SecurityRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityRegistryImpl.class);

    // by client end-point
    private Map<String, SecurityInfo> securityByEp = new ConcurrentHashMap<>();

    // by PSK identity
    private Map<String, SecurityInfo> securityByIdentity = new ConcurrentHashMap<>();

    // the name of the file used to persist the registry content
    private final String filename;

    private PublicKey serverPublicKey;

    private PrivateKey serverPrivateKey;

    // default location for persistence
    private static final String DEFAULT_FILE = "data/security.data";

    public SecurityRegistryImpl() {
        this(DEFAULT_FILE, null, null);
    }

    public SecurityRegistryImpl(PrivateKey serverPrivateKey, PublicKey serverPublicKey) {
        this(DEFAULT_FILE, serverPrivateKey, serverPublicKey);
    }

    /**
     * @param file the file path to persist the registry
     */
    public SecurityRegistryImpl(String file, PrivateKey serverPrivateKey, PublicKey serverPublicKey) {
        Validate.notEmpty(file);

        this.filename = file;
        this.serverPrivateKey = serverPrivateKey;
        this.serverPublicKey = serverPublicKey;
        this.loadFromFile();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SecurityInfo getByEndpoint(String endpoint) {
        return securityByEp.get(endpoint);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SecurityInfo getByIdentity(String identity) {
        return securityByIdentity.get(identity);
    }

    @Override
    public Collection<SecurityInfo> getAll() {
        return Collections.unmodifiableCollection(securityByEp.values());
    }

    @Override
    public synchronized SecurityInfo add(SecurityInfo info) throws NonUniqueSecurityInfoException {
        String identity = info.getIdentity();
        if (identity != null) {
            SecurityInfo infoByIdentity = securityByIdentity.get(info.getIdentity());
            if (infoByIdentity != null && !info.getEndpoint().equals(infoByIdentity.getEndpoint())) {
                throw new NonUniqueSecurityInfoException("PSK Identity " + info.getIdentity() + " is already used");
            }

            securityByIdentity.put(info.getIdentity(), info);
        }

        SecurityInfo previous = securityByEp.put(info.getEndpoint(), info);

        this.saveToFile();

        return previous;
    }

    @Override
    public synchronized SecurityInfo remove(String endpoint) {
        SecurityInfo info = securityByEp.get(endpoint);
        if (info != null) {
            if (info.getIdentity() != null) {
                securityByIdentity.remove(info.getIdentity());
            }
            securityByEp.remove(endpoint);

            this.saveToFile();
        }
        return info;
    }

    // /////// File persistence

    private void loadFromFile() {
        try {
            File file = new File(filename);

            if (!file.exists()) {
                // create parents if needed
                File parent = file.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                file.createNewFile();

            } else {

                try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file));) {
                    SecurityInfo[] infos = (SecurityInfo[]) in.readObject();

                    if (infos != null) {
                        for (SecurityInfo info : infos) {
                            try {
                                this.add(info);
                            } catch (NonUniqueSecurityInfoException e) {
                                // ignore it (should not occur)
                            }
                        }
                    }

                    if (infos != null && infos.length > 0) {
                        LOG.info("{} security infos loaded", infos.length);
                    }
                } catch (Exception e) {
                    LOG.debug("Could not load security infos from file", e);
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not load security infos from file", e);
        }
    }

    private void saveToFile() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filename));) {
            out.writeObject(this.getAll().toArray(new SecurityInfo[0]));
        } catch (Exception e) {
            LOG.debug("Could not save security infos to file", e);
        }
    }

    @Override
    public PublicKey getServerPublicKey() {
        return serverPublicKey;
    }

    @Override
    public PrivateKey getServerPrivateKey() {
        return serverPrivateKey;
    }
}
