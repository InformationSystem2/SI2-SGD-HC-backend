package com.sgd_hc.documents.service;

import com.sgd_hc.documents.dto.OcrResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrClientService {

    private final RestTemplate restTemplate;

    @Value("${ocr.service.url:http://localhost:8001}")
    private String ocrServiceUrl;

    public OcrResultDto extract(byte[] fileBytes, String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));

        ByteArrayResource resource = new ByteArrayResource(fileBytes) {
            @Override public String getFilename() { return "file"; }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, fileHeaders));

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        log.info(">>> OcrClient: llamando a {}/ocr/extract", ocrServiceUrl);
        return restTemplate.postForObject(
                ocrServiceUrl + "/ocr/extract", request, OcrResultDto.class);
    }
}