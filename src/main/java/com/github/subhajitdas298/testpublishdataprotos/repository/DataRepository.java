package com.github.subhajitdas298.testpublishdataprotos.repository;

import com.github.subhajitdas298.testdataprotos.DataEntry;
import com.github.subhajitdas298.testdataprotos.DateRecord;
import com.github.subhajitdas298.testdataprotos.Root;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.SplittableRandom;

@Repository
public class DataRepository {

    private static final int DAYS = 1;
    public static final int FULL_SIZE = 10_000_000;
    /** Sample sizes a client may request; bounded so the per-size caches stay small. */
    public static final Set<Integer> SAMPLE_SIZES = Set.of(10_000, 100_000, 1_000_000, FULL_SIZE);
    private static final long SEED = 0L;
    private static final double MAX_VALUE = 1000.0;

    @Cacheable(value = "rawDataset", sync = true)
    public Root findData(int size) {
        SplittableRandom random = new SplittableRandom(SEED);

        DataEntry.Builder entryBuilder = DataEntry.newBuilder();
        for (int day = 0; day < DAYS; day++) {
            entryBuilder.addDates(generateDayRecord(random, size));
        }

        return Root.newBuilder().addData(entryBuilder.build()).build();
    }

    // Only field `a` is populated; the rest of the proto's fields are left empty.
    // A sample is the first `size` values of the full seeded sequence.
    private DateRecord generateDayRecord(SplittableRandom random, int size) {
        DateRecord.Builder recordBuilder = DateRecord.newBuilder();

        for (int i = 0; i < size; i++) {
            recordBuilder.addA(random.nextDouble() * MAX_VALUE);
        }

        return recordBuilder.build();
    }
}
