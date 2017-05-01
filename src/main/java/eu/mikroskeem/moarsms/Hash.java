package eu.mikroskeem.moarsms;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author Mark Vainomaa
 */
final class Hash {
    private Hash() {}

    static String md5(String txt) {
        try {
            // http://stackoverflow.com/a/25251120
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] array = md.digest(txt.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte anArray : array) {
                sb.append(Integer.toHexString((anArray & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
