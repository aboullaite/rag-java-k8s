package me.aboullaite.rag.common.embedding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class DeterministicEmbedding {

    private static final int DIMENSIONS = 8;

    private DeterministicEmbedding() {
    }

    public static double[] embed(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.toLowerCase().trim().getBytes(StandardCharsets.UTF_8));
            double[] vector = new double[DIMENSIONS];
            ByteBuffer buffer = ByteBuffer.wrap(hash).order(ByteOrder.BIG_ENDIAN);
            for (int i = 0; i < DIMENSIONS; i++) {
                int value = buffer.getInt();
                vector[i] = value / (double) Integer.MAX_VALUE;
            }
            normalize(vector);
            return vector;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to compute embedding", e);
        }
    }

    private static void normalize(double[] vector) {
        double norm = 0d;
        for (double v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm == 0) {
            return;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }
    }
}
