/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.io.homekit.internal;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.hapjava.server.HomekitAuthInfo;
import io.github.hapjava.server.impl.HomekitServer;

/**
 * Provides a mechanism to store authenticated HomeKit client details inside the
 * ESH StorageService, by implementing HomekitAuthInfo.
 *
 * @author Andy Lintner - Initial contribution
 */
public class HomekitAuthInfoImpl implements HomekitAuthInfo {
    private final Logger logger = LoggerFactory.getLogger(HomekitAuthInfoImpl.class);

    private final Storage<String> storage;
    private String mac;
    private BigInteger salt;
    private byte[] privateKey;
    private final String pin;

    public HomekitAuthInfoImpl(final Storage storage, final String pin) throws InvalidAlgorithmParameterException {
        this.storage = storage;
        this.pin = pin;
        initializeStorage();
    }

    @Override
    public void createUser(String username, byte[] publicKey) {
        storage.put(createUserKey(username), Base64.getEncoder().encodeToString(publicKey));
    }

    @Override
    public String getMac() {
        return mac;
    }

    @Override
    public String getPin() {
        return pin;
    }

    @Override
    public byte[] getPrivateKey() {
        return privateKey;
    }

    @Override
    public BigInteger getSalt() {
        return salt;
    }

    @Override
    public byte[] getUserPublicKey(String username) {
        final String encodedKey = storage.get(createUserKey(username));
        if (encodedKey != null) {
            return Base64.getDecoder().decode(encodedKey);
        } else {
            return null;
        }
    }

    @Override
    public void removeUser(String username) {
        storage.remove(createUserKey(username));
    }

    @Override
    public boolean hasUser() {
        Collection<String> keys = storage.getKeys();
        return keys.stream().filter(k -> isUserKey(k)).count() > 0;
    }

    public void clear() {
        for (String key : new HashSet<>(storage.getKeys())) {
            if (isUserKey(key)) {
                storage.remove(key);
            }
        }
    }

    private String createUserKey(final String username) {
        return "user_" + username;
    }

    private boolean isUserKey(final String key) {
        return key.startsWith("user_");
    }

    private void initializeStorage() throws InvalidAlgorithmParameterException {
        mac = storage.get("mac");
        salt = new BigInteger(storage.get("salt"));
        privateKey = Base64.getDecoder().decode(storage.get("privateKey"));

        if (mac == null) {
            logger.warn(
                    "Could not find existing MAC in {}. Generating new MAC. This will require re-pairing of iOS devices.",
                    storage.getClass().getName());
            mac = HomekitServer.generateMac();
            storage.put("mac", mac);
        }
        if (salt == null) {
            salt = HomekitServer.generateSalt();
            storage.put("salt", salt.toString());
        }
        if (privateKey == null) {
            privateKey = HomekitServer.generateKey();
            storage.put("privateKey", Base64.getEncoder().encodeToString(privateKey));
        }
    }
}
