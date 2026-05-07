// IN2011 Computer Networks
// Coursework 2024/2025
//
// Construct the hashID for a string

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class HashID {

    public static byte [] computeHashID(String s) throws Exception {
	// What this does and how it works is covered in a later lecture
	MessageDigest md = MessageDigest.getInstance("SHA-256");
	md.update(s.getBytes(StandardCharsets.UTF_8));
	return md.digest();
    }
    public static int getDistance(byte[] a, byte[] b) {

        int matchingBits = 0;

        for (int i = 0; i < a.length; i++) {

            int xor = (a[i] ^ b[i]) & 0xff;

            if (xor == 0) {

                matchingBits += 8;

            } else {

                for (int bit = 7; bit >= 0; bit--) {

                    if ((xor & (1 << bit)) == 0) {
                        matchingBits++;
                    } else {
                        return 256 - matchingBits;
                    }
                }
            }
        }

        return 0;
    }
}
