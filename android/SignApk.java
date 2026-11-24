import com.android.apksig.ApkSigner;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

public class SignApk {
    public static void main(String[] args) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        char[] password = System.getenv("CHAOS_SIGN_PASSWORD").toCharArray();
        try (FileInputStream input = new FileInputStream(args[0])) { store.load(input, password); }
        ApkSigner.SignerConfig config = new ApkSigner.SignerConfig.Builder("chaos", (PrivateKey)store.getKey("chaos", password), Collections.singletonList((X509Certificate)store.getCertificate("chaos"))).build();
        new ApkSigner.Builder(Collections.singletonList(config)).setInputApk(new File(args[1])).setOutputApk(new File(args[2])).setMinSdkVersion(23).setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true).build().sign();
        com.android.apksig.ApkVerifier.Result result = new com.android.apksig.ApkVerifier.Builder(new File(args[2])).setMinCheckedPlatformVersion(23).build().verify();
        if (!result.isVerified()) throw new IllegalStateException(result.getErrors().toString());
        System.out.println("APK signature verified; v1=" + result.isVerifiedUsingV1Scheme() + ", v2=" + result.isVerifiedUsingV2Scheme() + ", v3=" + result.isVerifiedUsingV3Scheme());
    }
}
