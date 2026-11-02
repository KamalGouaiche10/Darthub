package it.uniupo.pissir.darts.common.security;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Coppia (o solo metà pubblica) di una chiave RSA usata per firmare/verificare i JWT dell'Authorization Server.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param publicKey chiave pubblica RSA
 * @param privateKey chiave privata RSA
 * @param keyId identificativo della chiave crittografica
 * @see java.security.interfaces.RSAPublicKey
 *  
 */
public record RsaKeyMaterial(RSAPublicKey publicKey, RSAPrivateKey privateKey, String keyId) {

    /**
     * Indica se il materiale crittografico consente di firmare token.
     *
     * @return {@code true} se il materiale crittografico consente di firmare token; {@code false} altrimenti
     *  
     */
    public boolean canSign() {
        return privateKey != null;
    }
}
