package com.bkjk.platform.webapi.version;

import com.bkjk.platform.webapi.ApiController;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

public class ApiVersionHandlerMapping extends RequestMappingHandlerMapping {

    /**
     * 只注册Controller，不注册feign接口
     * @param beanType
     * @return
     */
    @Override
    protected boolean isHandler(Class<?> beanType) {
        return AnnotatedElementUtils.hasAnnotation(beanType, Controller.class);
    }

    /**
     * 注册controller的请求处理handler
     * @param handler
     * @param method
     * @param mapping
     */
    @Override
    protected void registerHandlerMethod(Object handler, Method method, RequestMappingInfo mapping) {
        Class<?> controllerClass = method.getDeclaringClass();

        if (AnnotatedElementUtils.hasAnnotation(controllerClass, ApiController.class)) {

            ApiVersion apiVersion = AnnotationUtils.findAnnotation(controllerClass, ApiVersion.class);

            ApiVersion methodAnnotation = AnnotationUtils.findAnnotation(method, ApiVersion.class);
            if (methodAnnotation != null) {
                apiVersion = methodAnnotation;
            }

            String[] urlPatterns = apiVersion == null ? new String[0] : apiVersion.value();

            PatternsRequestCondition apiPattern = new PatternsRequestCondition(urlPatterns);
            PatternsRequestCondition oldPattern = mapping.getPatternsCondition();
            PatternsRequestCondition updatedFinalPattern = apiPattern.combine(oldPattern);
            mapping = new RequestMappingInfo(mapping.getName(), updatedFinalPattern, mapping.getMethodsCondition(),
                mapping.getParamsCondition(), mapping.getHeadersCondition(), mapping.getConsumesCondition(),
                mapping.getProducesCondition(), mapping.getCustomCondition());
        }

        super.registerHandlerMethod(handler, method, mapping);
    }
}
