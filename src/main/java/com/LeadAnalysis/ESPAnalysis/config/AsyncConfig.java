
package com.LeadAnalysis.ESPAnalysis.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.DeferredResultProcessingInterceptor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Callable;

// Add this to your main Application class or create a separate configuration class
@Configuration
public class AsyncConfig implements WebMvcConfigurer {

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // Set timeout to 30 minutes (adjust based on your analysis duration)
        configurer.setDefaultTimeout(45 * 60 * 1000); // 45 minutes in milliseconds

        // Optional: Add timeout handlers
        configurer.registerCallableInterceptors(new TimeoutCallableProcessingInterceptor());
        configurer.registerDeferredResultInterceptors(new TimeoutDeferredResultProcessingInterceptor());
    }
}

// Optional: Custom timeout interceptor for better handling
 class TimeoutCallableProcessingInterceptor implements CallableProcessingInterceptor {
    @Override
    public <T> Object handleTimeout(NativeWebRequest request, Callable<T> task) throws Exception {
        System.err.println("Async request timed out for: " + request.getDescription(false));
        return CallableProcessingInterceptor.RESULT_NONE;
    }
}

 class TimeoutDeferredResultProcessingInterceptor implements DeferredResultProcessingInterceptor {
    @Override
    public <T> boolean handleTimeout(NativeWebRequest request, DeferredResult<T> deferredResult) throws Exception {
        System.err.println("Deferred result timed out for: " + request.getDescription(false));
        deferredResult.setErrorResult("Request timed out");
        return true;
    }
}