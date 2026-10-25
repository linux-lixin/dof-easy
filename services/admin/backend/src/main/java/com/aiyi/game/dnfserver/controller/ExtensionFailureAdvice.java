package com.aiyi.game.dnfserver.controller;
import com.aiyi.game.dnfserver.service.extension.ActivityConfigService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;
@RestControllerAdvice
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class ExtensionFailureAdvice {
 @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
 public ResponseEntity<Map<String,String>> unsupportedMethod(org.springframework.web.HttpRequestMethodNotSupportedException e){
  org.springframework.http.HttpHeaders headers=new org.springframework.http.HttpHeaders();
  if(e.getSupportedHttpMethods()!=null)headers.setAllow(e.getSupportedHttpMethods());
  return new ResponseEntity<>(Collections.singletonMap("message","此接口不支持该请求方法"),headers,org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED);
 }
 @ExceptionHandler(ActivityConfigService.Failure.class)
 public ResponseEntity<Map<String,String>> failure(ActivityConfigService.Failure e){return ResponseEntity.status(e.status).body(Collections.singletonMap("message",e.getMessage()));}
}
