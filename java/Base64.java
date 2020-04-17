import java.util.Arrays;
//import java.util.Base64;
class Base64 {
    static final byte[] chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".getBytes();
    public static String encode(byte[] b) {
        int i = 0;
        int l = 0;
        byte[] b64 = new byte[((b.length + 2) / 3 * 4)];
        Arrays.fill(b64, (byte) '=');
        while (i < b.length) {
            int k = Math.min(3, b.length - i);
            int bits = 0;
            int shift = 16;
            for (int j = 0; j < k; j++) {
                bits += ((b[i++] & 0xFF) << shift);
                shift -= 8;
            }
	    shift = 18;
            for (int j = 0; j <= k; j++) {
                b64[l++] = chars[(bits >> shift) & 0x3F];
                shift -= 6;
            }
        }
        return new String(b64);
    }
    public static void main(String args[]) {
	String s = "dgdgdhhrtjhrtjvngfn";
        System.out.println(encode(s.getBytes()));
        System.out.println(java.util.Base64.getEncoder().encodeToString(s.getBytes()));
    }
}