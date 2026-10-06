package com.github.subhajitdas298.testpublishdataprotos.repository;

import com.github.subhajitdas298.testdataprotos.DataEntry;
import com.github.subhajitdas298.testdataprotos.DateRecord;
import com.github.subhajitdas298.testdataprotos.Root;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

import java.util.SplittableRandom;

@Repository
public class DataRepository {

    private static final int DAYS = 1;
    private static final int RECORDS_PER_FIELD_PER_DAY = 10_000_000;
    private static final long SEED = 0L;
    private static final double MAX_VALUE = 1000.0;

    @Cacheable(value = "rawDataset", sync = true)
    public Root findData() {
        SplittableRandom random = new SplittableRandom(SEED);

        DataEntry.Builder entryBuilder = DataEntry.newBuilder();
        for (int day = 0; day < DAYS; day++) {
            entryBuilder.addDates(generateDayRecord(random));
        }

        return Root.newBuilder().addData(entryBuilder.build()).build();
    }

    // Only field `a` is populated; the rest of the proto's fields are left empty.
    private DateRecord generateDayRecord(SplittableRandom random) {
        DateRecord.Builder recordBuilder = DateRecord.newBuilder();

        for (int i = 0; i < RECORDS_PER_FIELD_PER_DAY; i++) {
            recordBuilder.addA(random.nextDouble() * MAX_VALUE);
        }

        return recordBuilder.build();
    }
}
