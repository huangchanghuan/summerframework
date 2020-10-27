package com.bkjk.platform.webapi.misc;

import com.bkjk.platform.webapi.ApiController;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor;

import java.lang.annotation.Annotation;
import java.util.List;

public class AutoRequestBodyProcessor extends RequestResponseBodyMethodProcessor {
    public AutoRequestBodyProcessor(List<HttpMessageConverter<?>> converters) {
        super(converters);
    }

    public AutoRequestBodyProcessor(List<HttpMessageConverter<?>> converters,
        @Nullable ContentNegotiationManager manager, @Nullable List<Object> requestResponseBodyAdvice) {
        super(converters, manager, requestResponseBodyAdvice);
    }

    @Override
    protected boolean checkRequired(MethodParameter parameter) {
        if (AnnotatedElementUtils.hasAnnotation(parameter.getContainingClass(), ApiController.class)){
            ApiController ann = AnnotationUtils.findAnnotation(parameter.getContainingClass(), ApiController.class);
            if(ann.requestBody()&&!hasSpringAnnotation(parameter)){
                return true;
            }
        }
        RequestBody requestBody = parameter.getParameterAnnotation(RequestBody.class);
        return (requestBody != null && requestBody.required() && !parameter.isOptional());
    }

    /**
     * 使用ApiController默认实现含有@requestBody功能，不用每次都标识参数，方便使用
     * @param parameter
     * @return
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (AnnotatedElementUtils.hasAnnotation(parameter.getContainingClass(), ApiController.class)){
            ApiController ann = AnnotationUtils.findAnnotation(parameter.getContainingClass(), ApiController.class);
            //如果是一个标准 restful接口 ，即 HttpMethod 符合规范且 Controller 下方法参数是在 body 中以JSON格式传递，那么设置为true
            if(ann.requestBody()&&!hasSpringAnnotation(parameter)){
                return true;
            }
        }
        return super.supportsParameter(parameter);
    }

    private boolean hasSpringAnnotation(MethodParameter parameter){
        for(Annotation ann:parameter.getParameterAnnotations()){
            if(ann.annotationType().getPackage().getName().startsWith("org.springframework.web.bind.annotation")){
                return true;
            }
        }
        return false;
    }

    /**
     * 默认就是通过实体注解进行校验，不用每个方法都要配置，方便
     * todo 但是同一个实体，多个方法同时使用，不能使用原来的group分组校验？
     * @param binder
     * @param parameter
     */
    @Override
    protected void validateIfApplicable(WebDataBinder binder, MethodParameter parameter) {
        if (AnnotatedElementUtils.hasAnnotation(parameter.getContainingClass(), ApiController.class)) {
            binder.validate();
        } else {
            Annotation[] annotations = parameter.getParameterAnnotations();
            for (Annotation ann : annotations) {
                Validated validatedAnn = AnnotationUtils.getAnnotation(ann, Validated.class);
                if (validatedAnn != null || ann.annotationType().getSimpleName().startsWith("Valid")) {
                    Object hints = (validatedAnn != null ? validatedAnn.value() : AnnotationUtils.getValue(ann));
                    Object[] validationHints = (hints instanceof Object[] ? (Object[])hints : new Object[] {hints});
                    binder.validate(validationHints);
                    break;
                }
            }
        }
    }
}
