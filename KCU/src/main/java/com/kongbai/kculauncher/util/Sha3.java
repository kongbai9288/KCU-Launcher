package com.kongbai.kculauncher.util;

/**
 * SHA3-256（Keccak-f[1600]，rate 1088，输出 256 位）。
 *
 * 自行实现而不用 MessageDigest.getInstance("SHA3-256")：
 * 该算法名由 Java 9 的 SUN provider 引入，Android 各版本的 MessageDigest
 * 支持情况不一致，直接调用在部分设备上会得到 NoSuchAlgorithmException。
 */
public final class Sha3 {

    private static final int RATE_BYTES = 136;
    private static final int OUTPUT_BYTES = 32;
    private static final int STATE_WORDS = 25;

    private static final long[] RC = {
            0x0000000000000001L, 0x0000000000008082L, 0x800000000000808AL, 0x8000000080008000L,
            0x000000000000808BL, 0x0000000080000001L, 0x8000000080008081L, 0x8000000000008009L,
            0x000000000000008AL, 0x0000000000000088L, 0x0000000080008009L, 0x000000008000000AL,
            0x000000008000808BL, 0x800000000000008BL, 0x8000000000008089L, 0x8000000000008003L,
            0x8000000000008002L, 0x8000000000000080L, 0x000000000000800AL, 0x800000008000000AL,
            0x8000000080008081L, 0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L,
    };

    /** rho 旋转偏移，按 state 下标 index = x + 5 * y 排列 */
    private static final int[] ROT = {
            0, 1, 62, 28, 27,
            36, 44, 6, 55, 20,
            3, 10, 43, 25, 39,
            41, 45, 15, 21, 8,
            18, 2, 61, 56, 14,
    };

    private Sha3() {
    }

    /** 计算 SHA3-256，返回 64 位小写十六进制。异常一律返回空串。 */
    public static String sha3256Hex(byte[] input) {
        if (input == null) {
            return "";
        }
        try {
            byte[] digest = sha3256(input);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                String h = Integer.toHexString(b & 0xFF);
                if (h.length() == 1) {
                    sb.append('0');
                }
                sb.append(h);
            }
            return sb.toString();
        } catch (Throwable e) {
            return "";
        }
    }

    public static byte[] sha3256(byte[] input) {
        long[] state = new long[STATE_WORDS];
        byte[] padded = pad(input);
        for (int off = 0; off < padded.length; off += RATE_BYTES) {
            for (int i = 0; i < RATE_BYTES / 8; i++) {
                state[i] ^= le64(padded, off + i * 8);
            }
            keccakF(state);
        }
        byte[] out = new byte[OUTPUT_BYTES];
        for (int i = 0; i < OUTPUT_BYTES / 8; i++) {
            putLe64(out, i * 8, state[i]);
        }
        return out;
    }

    /** SHA-3 填充：消息 || 0x06 || 0x00* || 末字节最高位置位 */
    private static byte[] pad(byte[] input) {
        int len = input.length;
        int padLen = RATE_BYTES - (len % RATE_BYTES);
        if (padLen < 2) {
            padLen += RATE_BYTES;
        }
        byte[] out = new byte[len + padLen];
        System.arraycopy(input, 0, out, 0, len);
        out[len] = 0x06;
        out[len + padLen - 1] = (byte) 0x80;
        return out;
    }

    private static long le64(byte[] b, int off) {
        long v = 0;
        for (int i = 7; i >= 0; i--) {
            v = (v << 8) | (b[off + i] & 0xFFL);
        }
        return v;
    }

    private static void putLe64(byte[] b, int off, long v) {
        for (int i = 0; i < 8; i++) {
            b[off + i] = (byte) (v >>> (8 * i));
        }
    }

    private static void keccakF(long[] a) {
        long[] rot = new long[STATE_WORDS];
        long[] b = new long[STATE_WORDS];
        long[] c = new long[5];
        long[] d = new long[5];

        for (int round = 0; round < 24; round++) {
            // theta
            for (int x = 0; x < 5; x++) {
                c[x] = a[x] ^ a[x + 5] ^ a[x + 10] ^ a[x + 15] ^ a[x + 20];
            }
            for (int x = 0; x < 5; x++) {
                d[x] = c[(x + 4) % 5] ^ rotl(c[(x + 1) % 5], 1);
            }
            for (int y = 0; y < 5; y++) {
                for (int x = 0; x < 5; x++) {
                    a[x + 5 * y] ^= d[x];
                }
            }

            // rho
            for (int i = 0; i < STATE_WORDS; i++) {
                rot[i] = rotl(a[i], ROT[i]);
            }

            // pi：B[y, (2x + 3y) % 5] = rot[x, y]
            for (int y = 0; y < 5; y++) {
                for (int x = 0; x < 5; x++) {
                    b[y + 5 * ((2 * x + 3 * y) % 5)] = rot[x + 5 * y];
                }
            }

            // chi：A[x, y] = B[x, y] ^ (~B[x+1, y] & B[x+2, y])
            for (int y = 0; y < 5; y++) {
                for (int x = 0; x < 5; x++) {
                    int idx = x + 5 * y;
                    long p1 = b[((x + 1) % 5) + 5 * y];
                    long p2 = b[((x + 2) % 5) + 5 * y];
                    a[idx] = b[idx] ^ ((~p1) & p2);
                }
            }

            // iota
            a[0] ^= RC[round];
        }
    }

    private static long rotl(long x, int n) {
        n = n % 64;
        if (n == 0) {
            return x;
        }
        return (x << n) | (x >>> (64 - n));
    }
}
