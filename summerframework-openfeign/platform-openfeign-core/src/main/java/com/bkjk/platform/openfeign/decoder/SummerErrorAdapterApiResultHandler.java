package com.bkjk.platform.openfeign.decoder;

import com.bkjk.platform.openfeign.exception.RemoteServiceException;
import com.bkjk.platform.webapi.version.Constant;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URLDecoder;
import java.util.HashMap;

public class SummerErrorAdapterApiResultHandler implements ApiResultHandler {
    final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Object decode(Response response, Type type) throws IOException {
        String apiResultString = getFirstHeader(response, Constant.X_PLATFORM_SCHEMA_BODY).orElse("");
        if (!StringUtils.isEmpty(apiResultString)) {
            HashMap apiResult = mapper.readValue(URLDecoder.decode(apiResultString,"UTF-8"), HashMap.class);
            String errorMessage = getStringFromMap(apiResult, "message");
            throw new RemoteServiceException(getStringFromMap(apiResult, "code"),
                StringUtils.isEmpty(errorMessage) ? getStringFromMap(apiResult, "error") : errorMessage);
        }
        return null;
    }

    /**
     * 响应异常，并且不是同版本X_PLATFORM_SCHEMA_VERSION
     * @param response
     * @param type
     * @return
     */
    @Override
    public boolean support(Response response, Type type) {
        return response.headers().containsKey(Constant.X_PLATFORM_ERROR) && !Constant.VERSION_SUMMER2
            .equals(getFirstHeader(response, Constant.X_PLATFORM_SCHEMA_VERSION).orElse(""));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
