package com.github.subhajitdas298.testpublishdataprotos.repository;

import com.github.subhajitdas298.testdataprotos.DataEntry;
import com.github.subhajitdas298.testdataprotos.DateRecord;
import com.github.subhajitdas298.testdataprotos.Root;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;

@Repository
public class DataRepository {

    private static final int DAYS = 10;
    private static final int RECORDS_PER_FIELD_PER_DAY = 10_000;
    private static final String FIELDS = "abcdefghijklmnopqrstuvwxyz";
    private static final String DATASET_RESOURCE = "data/dataset.bin";

    @Cacheable("rawDataset")
    public Root findData() {
        DoubleBuffer values = loadValues();

        DataEntry.Builder entryBuilder = DataEntry.newBuilder();
        for (int day = 0; day < DAYS; day++) {
            entryBuilder.addDates(generateDayRecord(values));
        }

        return Root.newBuilder().addData(entryBuilder.build()).build();
    }

    private DoubleBuffer loadValues() {
        try (var in = new ClassPathResource(DATASET_RESOURCE).getInputStream()) {
            return ByteBuffer.wrap(in.readAllBytes()).asDoubleBuffer();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load precomputed dataset: " + DATASET_RESOURCE, e);
        }
    }

    private DateRecord generateDayRecord(DoubleBuffer values) {
        DateRecord.Builder recordBuilder = DateRecord.newBuilder();

        for (char field : FIELDS.toCharArray()) {
            for (int i = 0; i < RECORDS_PER_FIELD_PER_DAY; i++) {
                addValue(recordBuilder, field, values.get());
            }
        }

        return recordBuilder.build();
    }

    private void addValue(DateRecord.Builder builder, char field, double value) {
        switch (field) {
            case 'a' -> builder.addA(value);
            case 'b' -> builder.addB(value);
            case 'c' -> builder.addC(value);
            case 'd' -> builder.addD(value);
            case 'e' -> builder.addE(value);
            case 'f' -> builder.addF(value);
            case 'g' -> builder.addG(value);
            case 'h' -> builder.addH(value);
            case 'i' -> builder.addI(value);
            case 'j' -> builder.addJ(value);
            case 'k' -> builder.addK(value);
            case 'l' -> builder.addL(value);
            case 'm' -> builder.addM(value);
            case 'n' -> builder.addN(value);
            case 'o' -> builder.addO(value);
            case 'p' -> builder.addP(value);
            case 'q' -> builder.addQ(value);
            case 'r' -> builder.addR(value);
            case 's' -> builder.addS(value);
            case 't' -> builder.addT(value);
            case 'u' -> builder.addU(value);
            case 'v' -> builder.addV(value);
            case 'w' -> builder.addW(value);
            case 'x' -> builder.addX(value);
            case 'y' -> builder.addY(value);
            case 'z' -> builder.addZ(value);
            default -> throw new IllegalArgumentException("Unknown field: " + field);
        }
    }
}
