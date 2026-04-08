import java.io.FileInputStream;
import java.security.MessageDigest;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Base64;

public class GetHash {
    public static void main(String[] args) throws Exception {
        FileInputStream is = new FileInputStream("C:\\Users\\user\\.android\\debug.keystore");
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        keystore.load(is, "android".toCharArray());
        Certificate cert = keystore.getCertificate("androiddebugkey");
        MessageDigest md = MessageDigest.getInstance("SHA");
        md.update(cert.getEncoded());
        System.out.println("=== KEY HASH CỦA BẠN LÀ: " + Base64.getEncoder().encodeToString(md.digest()) + " ===");
    }
}
