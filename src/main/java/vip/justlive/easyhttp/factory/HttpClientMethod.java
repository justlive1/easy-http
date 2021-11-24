/*
 * Copyright (C) 2019 justlive1
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License
 *  is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 *  or implied. See the License for the specific language governing permissions and limitations under
 *  the License.
 */

package vip.justlive.easyhttp.factory;

import com.alibaba.fastjson.JSON;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import vip.justlive.oxygen.core.exception.Exceptions;
import vip.justlive.oxygen.core.util.base.HttpHeaders;
import vip.justlive.oxygen.core.util.base.MoreObjects;
import vip.justlive.oxygen.core.util.base.SnowflakeId;
import vip.justlive.oxygen.core.util.base.Strings;
import vip.justlive.oxygen.core.util.io.FileCleaner;
import vip.justlive.oxygen.core.util.io.FileUtils;
import vip.justlive.oxygen.core.util.net.http.HttpMethod;
import vip.justlive.oxygen.core.util.net.http.HttpRequest;
import vip.justlive.oxygen.core.util.net.http.HttpRequestExecution;
import vip.justlive.oxygen.core.util.net.http.HttpRequestInterceptor;
import vip.justlive.oxygen.core.util.net.http.HttpResponse;

/**
 * method wrapper
 *
 * @author wubo
 */
public class HttpClientMethod {
  
  public static final String TRACE_ID = "trace-id";
  private static final Pattern REGEX_PATH_GROUP = Pattern.compile("\\{(\\w+)[}]");
  
  private final String root;
  private final Method method;
  private final Environment environment;
  private final HttpRequestExecution requestExecution;
  private final List<HttpRequestInterceptor> interceptors;
  private String url;
  private Type responseType;
  private RequestMethod httpMethod = RequestMethod.GET;
  private final Map<String, Integer> headers = new HashMap<>(2);
  private final Map<String, Integer> query = new HashMap<>(2);
  private boolean multipart = false;
  private int bodyIndex = -1;
  private boolean isHttpclient;
  private Map<String, Integer> pathVars;
  private final Map<String, String> baseHeaders = new HashMap<>(2);
  
  HttpClientMethod(String root, Method method, Environment environment,
      HttpRequestExecution requestExecution, List<HttpRequestInterceptor> interceptors) {
    this.root = root;
    this.method = method;
    this.environment = environment;
    this.requestExecution = requestExecution;
    this.interceptors = interceptors;
    parse();
  }
  
  Object execute(Object... args) throws IOException {
    if (!isHttpclient) {
      return null;
    }
    
    try (FileCleaner cleaner = new FileCleaner();
        HttpResponse response = buildRequest(cleaner, args).execute()) {
      if (responseType == Void.TYPE) {
        return null;
      }
      if (responseType == String.class) {
        return response.bodyAsString();
      }
      return JSON.parseObject(response.bodyAsString(), responseType);
    }
  }
  
  private void parse() {
    if (parseMapping() || parseGet() || parsePost() || parsePut() || parseDelete()
        || parsePatch()) {
      if (url == null) {
        return;
      }
      if (StringUtils.hasText(root)) {
        url = concatUrl(root, url);
      }
      this.isHttpclient = true;
      parsePathVars();
      parseParam();
    }
  }
  
  private String pathVarSeat(String name) {
    return String.format(" %s ", name);
  }
  
  private void parsePathVars() {
    Matcher matcher = REGEX_PATH_GROUP.matcher(url);
    int start = 0;
    if (matcher.find(start)) {
      pathVars = new HashMap<>(2);
      do {
        String name = matcher.group(1);
        url = url.replace(matcher.group(0), pathVarSeat(name));
        start = matcher.end();
      } while (matcher.find(start));
    }
  }
  
  private boolean parseMapping() {
    RequestMapping req = method.getAnnotation(RequestMapping.class);
    if (req != null) {
      parseMappingValue(req.value(), req.consumes(), req.produces(), req.headers());
      if (req.method().length > 0) {
        httpMethod = req.method()[0];
      }
      return true;
    }
    return false;
  }
  
  private boolean parseGet() {
    GetMapping req = method.getAnnotation(GetMapping.class);
    if (req != null) {
      parseMappingValue(req.value(), req.consumes(), req.produces(), req.headers());
      httpMethod = RequestMethod.GET;
      return true;
    }
    return false;
  }
  
  private boolean parsePost() {
    PostMapping req = method.getAnnotation(PostMapping.class);
    if (req != null) {
      parseMappingValue(req.value(), req.consumes(), req.produces(), req.headers());
      httpMethod = RequestMethod.POST;
      return true;
    }
    return false;
  }
  
  private boolean parsePut() {
    PutMapping req = method.getAnnotation(PutMapping.class);
    if (req != null) {
      parseMappingValue(req.value(), req.consumes(), req.produces(), req.headers());
      httpMethod = RequestMethod.PUT;
      return true;
    }
    return false;
  }
  
  private boolean parseDelete() {
    DeleteMapping req = method.getAnnotation(DeleteMapping.class);
    if (req != null) {
      parseMappingValue(req.value(), req.consumes(), req.produces(), req.headers());
      httpMethod = RequestMethod.DELETE;
      return true;
    }
    return false;
  }
  
  private boolean parsePatch() {
    PatchMapping req = method.getAnnotation(PatchMapping.class);
    if (req != null) {
      parseMappingValue(req.value(), req.consumes(), req.produces(), req.headers());
      httpMethod = RequestMethod.PATCH;
      return true;
    }
    return false;
  }
  
  private void parseMappingValue(String[] values, String[] consumes, String[] produces,
      String[] headers) {
    if (values.length > 0) {
      url = environment.resolvePlaceholders(values[0]);
    } else {
      url = Strings.EMPTY;
    }
    if (consumes.length > 0) {
      baseHeaders.put(HttpHeaders.CONTENT_TYPE, consumes[0]);
    }
    if (produces.length > 0) {
      baseHeaders.put(HttpHeaders.ACCEPT,
          StringUtils.arrayToDelimitedString(produces, Strings.COMMA));
    }
    if (headers.length > 0) {
      for (String header : headers) {
        String[] qs = header.split(Strings.EQUAL);
        baseHeaders.put(qs[0], qs[1]);
      }
    }
  }
  
  private void parseParam() {
    responseType = method.getGenericReturnType();
    Parameter[] parameters = method.getParameters();
    for (int index = 0, len = parameters.length; index < len; index++) {
      handlerParamAnnotations(parameters[index], index);
    }
  }
  
  private void handlerParamAnnotations(Parameter parameter, int index) {
    RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
    if (requestParam != null) {
      query.put(Strings.firstNonNull(requestParam.value(), parameter.getName()), index);
      return;
    }
    RequestHeader requestHeader = parameter.getAnnotation(RequestHeader.class);
    if (requestHeader != null) {
      headers.put(Strings.firstNonNull(requestHeader.value(), parameter.getName()), index);
      return;
    }
    if (parameter.isAnnotationPresent(RequestBody.class)) {
      bodyIndex = index;
      return;
    }
    PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
    if (pathVariable != null) {
      pathVars.put(Strings.firstNonNull(pathVariable.value(), parameter.getName()), index);
      return;
    }
    RequestPart requestPart = parameter.getAnnotation(RequestPart.class);
    if (requestPart != null) {
      multipart = true;
      query.put(Strings.firstNonNull(requestPart.value(), parameter.getName()), index);
    }
  }
  
  private HttpRequest buildRequest(FileCleaner cleaner, Object... args) {
    HttpRequest request = HttpRequest.url(buildRequestUrl(args))
        .method(HttpMethod.valueOf(httpMethod.name())).httpRequestExecution(requestExecution)
        .interceptors(interceptors);
    
    baseHeaders.forEach(request::addHeader);
    headers.forEach((k, v) -> request.addHeader(k, MoreObjects.safeToString(args[v])));
    String traceId = MDC.get(TRACE_ID);
    if (traceId != null) {
      request.addHeader(TRACE_ID, traceId);
    }
    
    Map<String, Object> qv = new HashMap<>(4);
    for (Map.Entry<String, Integer> entry : query.entrySet()) {
      Object value = args[entry.getValue()];
      if (value instanceof Map) {
        ((Map<?, ?>) value).forEach((k, v) -> qv.put(k.toString(), v));
      } else {
        qv.put(entry.getKey(), value);
      }
    }
    
    buildRequestQuery(qv, cleaner, request);
    
    if (bodyIndex > -1) {
      Object body = args[bodyIndex];
      if (body == null) {
        request.jsonBody("{}");
      } else {
        request.jsonBody(JSON.toJSONString(body));
      }
    }
    return request;
  }
  
  private String buildRequestUrl(Object... args) {
    String realUrl = url;
    if (pathVars != null) {
      for (Map.Entry<String, Integer> entry : pathVars.entrySet()) {
        realUrl = realUrl
            .replace(pathVarSeat(entry.getKey()), MoreObjects.safeToString(args[entry.getValue()]));
      }
    }
    return realUrl;
  }
  
  private void buildRequestQuery(Map<String, Object> qv, FileCleaner cleaner, HttpRequest request) {
    if (qv.isEmpty()) {
      return;
    }
    if (multipart) {
      qv.forEach((k, v) -> this.handleMultipart(cleaner, request, k, v));
    } else {
      Map<String, String> qvs = new HashMap<>(4);
      qv.forEach((k, v) -> qvs.put(k, MoreObjects.safeToString(v)));
      if (request.getMethod() == HttpMethod.GET) {
        request.queryParam(qvs);
      } else {
        request.formBody(qvs);
      }
    }
  }
  
  private void handleMultipart(FileCleaner cleaner, HttpRequest request, String key, Object value) {
    if (value instanceof MultipartFile) {
      MultipartFile mf = (MultipartFile) value;
      File tempFile = new File(FileUtils.tempBaseDir(),
          SnowflakeId.defaultNextId() + mf.getOriginalFilename());
      FileUtils.mkdirsForFile(tempFile);
      cleaner.track(tempFile);
      try {
        mf.transferTo(tempFile);
      } catch (IOException e) {
        throw Exceptions.wrap(e);
      }
      request.multipart(key, tempFile, mf.getOriginalFilename());
    } else if (value instanceof File) {
      request.multipart(key, (File) value);
    } else {
      request.multipart(key, MoreObjects.safeToString(value));
    }
  }
  
  private static String concatUrl(String parent, String child) {
    StringBuilder sb = new StringBuilder();
    boolean endWithSlash = false;
    if (parent != null && parent.length() > 0) {
      sb.append(parent);
      if (!parent.endsWith(Strings.SLASH)) {
        sb.append(Strings.SLASH);
      }
      endWithSlash = true;
    }
    if (child != null) {
      if (endWithSlash && child.startsWith(Strings.SLASH)) {
        sb.append(child.substring(1));
      } else {
        sb.append(child);
      }
    }
    return sb.toString();
  }
  
}