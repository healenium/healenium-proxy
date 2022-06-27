package com.epam.healenium.healenium_proxy.service;


import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

@Slf4j
@Component
public class HttpServletRequestService {


    /**
     * Get request body from request
     *
     * @param request
     * @return
     */
    public String getRequestBody(HttpServletRequest request) {
        String requestBody = Strings.EMPTY;
        try {
            requestBody = new BufferedReader(new InputStreamReader(request.getInputStream()))
                    .lines().collect(Collectors.joining(""));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return requestBody;
    }

    public String getCurrentSessionId(HttpServletRequest request) {
        String[] split = request.getRequestURI().split("/");
        return split.length > 1 && "session".equals(split[1])
                ? request.getRequestURI().split("/")[2]
                : null;
    }

}
