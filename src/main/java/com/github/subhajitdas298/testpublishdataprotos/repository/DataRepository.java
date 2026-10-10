package com.github.subhajitdas298.testpublishdataprotos.repository;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;

/**
 * Serves the precomputed protobuf-encoded datasets bundled in {@code data/dataset-<size>.bin}:
 * one {@code Root} message each, with a single day whose field {@code a} holds a synthetic ML
 * recall trend of {@code size} points (see scripts/generate_datasets.py).
 */
@Repository
public class DataRepository {

    public static final int FULL_SIZE = 10_000_000;
    /** Sample sizes a client may request; one bundled file each. */
    public static final Set<Integer> SAMPLE_SIZES = Set.of(10_000, 100_000, 1_000_000, FULL_SIZE);

    @Cacheable(value = "rawDataset", sync = true)
    public byte[] findData(int size) {
        String resource = "data/dataset-" + size + ".bin";
        try (var in = new ClassPathResource(resource).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load precomputed dataset: " + resource, e);
        }
    }
}
