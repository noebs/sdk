package com.tuti.model;

import com.tuti.util.IPINBlockGenerator;

/**
 * @deprecated Use typed opaque-card request factories.
 */
@Deprecated
public class IPIN {

    /**
     * @deprecated Use the typed opaque-card request factories. Invalid keys and encryption failures
     * throw; this method never returns the clear IPIN.
     * @param ipin clear transient IPIN
     * @param publicKey canonical base64-encoded RSA public key
     * @param uuid canonical operation UUID
     * @return a base64-encoded RSA block
     */
    @Deprecated
    public static String getIPINBlock(String ipin,
                                      String publicKey, String uuid) {
        return IPINBlockGenerator.INSTANCE.getIPINBlock(ipin, publicKey, uuid);
    }
}
