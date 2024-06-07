package tls.util;

import java.security.*;
import java.security.cert.Certificate;

public class FakeCertificate extends Certificate {
    protected FakeCertificate() {
        super("FakeCertificate");
    }

    @Override
    public byte[] getEncoded() {
        return "This is a fake certificate".getBytes();
    }

    @Override
    public void verify(PublicKey key) {
        throw new UnsupportedOperationException("Cannot verify FakeCertificate");
    }

    @Override
    public void verify(PublicKey key, String sigProvider){
        throw new UnsupportedOperationException("Cannot verify FakeCertificate");
    }

    @Override
    public String toString() {
        return "FakeCertificate{}";
    }

    @Override
    public PublicKey getPublicKey() {
        return null;
    }
}
