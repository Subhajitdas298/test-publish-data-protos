package com.github.subhajitdas298.testpublishdataprotos.controller;

import com.github.subhajitdas298.testpublishdataprotos.repository.DataRepository;
import com.github.subhajitdas298.testpublishdataprotos.service.JsonDataService;
import com.github.subhajitdas298.testpublishdataprotos.service.ProtoDataService;
import com.google.protobuf.InvalidProtocolBufferException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@CrossOrigin(origins = "*", exposedHeaders = DataController.LENGTH_HEADER)
public class DataController {

    /** Uncompressed body size, readable cross-origin so the UI can show download progress. */
    static final String LENGTH_HEADER = "X-Data-Length";

    private final ProtoDataService protoDataService;
    private final JsonDataService jsonDataService;

    public DataController(ProtoDataService protoDataService, JsonDataService jsonDataService) {
        this.protoDataService = protoDataService;
        this.jsonDataService = jsonDataService;
    }

    @GetMapping(value = "/api/data", produces = "application/x-protobuf")
    public ResponseEntity<byte[]> getProtoData(@RequestParam(defaultValue = "10000000") int size) {
        byte[] body = protoDataService.getData(validSize(size));
        return ResponseEntity.ok().header(LENGTH_HEADER, String.valueOf(body.length)).body(body);
    }

    @GetMapping(value = "/api/data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getJsonData(@RequestParam(defaultValue = "10000000") int size)
            throws InvalidProtocolBufferException {
        String body = jsonDataService.getData(validSize(size));
        // The JSON is pure ASCII (numbers/field names), so chars == bytes.
        return ResponseEntity.ok().header(LENGTH_HEADER, String.valueOf(body.length())).body(body);
    }

    private static int validSize(int size) {
        if (!DataRepository.SAMPLE_SIZES.contains(size)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "size must be one of " + new java.util.TreeSet<>(DataRepository.SAMPLE_SIZES));
        }
        return size;
    }
}
