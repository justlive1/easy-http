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
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import vip.justlive.oxygen.core.constant.Constants;
import vip.justlive.oxygen.core.net.HttpMethod;
import vip.justlive.oxygen.core.net.HttpRequest;
import vip.justlive.oxygen.core.net.HttpResponse;
import vip.justlive.oxygen.core.util.MoreObjects;

/**
 * method wrapper
 *
 * @author wubo
 */
class HttpClientMethod {

  private static final Pattern REGEX_PATH_GROUP = Pattern.compile("\\{(\\w+)[}]");
  private static final String EMPTY_JSON = "{}";

  private final String root;
  private final Method method;
  private final Environment environment;
  private String url;
  private Type responseType;
  private RequestMethod httpMethod = RequestMethod.GET;
  private Map<String, Integer> headers = new HashMap<>(2);
  private Map<String, Integer> querys = new HashMap<>(2);
  private int bodyIndex = -1;
  private boolean isHttpclient;
  private Map<String, Integer> pathVars;
  private String contentType;

  HttpClientMethod(String root, Method method, Environment environment) {
    this.root = root;
    this.method = method;
    this.environment = environment;
    parse();
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
      parseMappingValue(req.value(), req.consumes());
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
      parseMappingValue(req.value(), req.consumes());
      httpMethod = RequestMethod.GET;
      return true;
    }
    return false;
  }

  private boolean parsePost() {
    PostMapping req = method.getAnnotation(PostMapping.class);
    if (req != null) {
      parseMappingValue(req.value(), req.consumes());
      httpMethod = RequestMethod.POST;
      return true;
    }
    return false;
  }

  private boolean parsePut() {
    PutMapping req = method.getAnnotation(PutMapping.class);
    if (req != null) {
      parseMappingValue(req.value(), req.consumes());
      httpMethod = RequestMethod.PUT;
      return true;
    }
    return false;
  }

  private boolean parseDelete() {
    DeleteMapping req = method.getAnnotation(DeleteMapping.class);
    if (req != null) {
      parseMappingValue(req.value(), req.consumes());
      httpMethod = RequestMethod.DELETE;
      return true;
    }
    return false;
  }

  private boolean parsePatch() {
    PatchMapping req = method.getAnnotation(PatchMapping.class);
    if (req != null) {
      parseMappingValue(req.value(), req.consumes());
      httpMethod = RequestMethod.PATCH;
      return true;
    }
    return false;
  }

  private void parseMappingValue(String[] values, String[] consumes) {
    if (values.length > 0) {
      url = environment.resolvePlaceholders(values[0]);
    } else {
      url = Constants.EMPTY;
    }
    if (consumes.length > 0) {
      contentType = consumes[0];
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
      querys.put(MoreObjects.firstNonEmpty(requestParam.value(), parameter.getName()), index);
      return;
    }
    RequestHeader requestHeader = parameter.getAnnotation(RequestHeader.class);
    if (requestHeader != null) {
      headers.put(MoreObjects.firstNonEmpty(requestHeader.value(), parameter.getName()), index);
      return;
    }
    if (parameter.isAnnotationPresent(RequestBody.class)) {
      bodyIndex = index;
      return;
    }
    PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
    if (pathVariable != null) {
      pathVars.put(MoreObjects.firstNonEmpty(pathVariable.value(), parameter.getName()), index);
    }
  }

  Object execute(Object... args) throws IOException {
    if (!isHttpclient) {
      return null;
    }

    try (HttpResponse response = buildRequest(args).execute()) {
      if (responseType == Void.TYPE) {
        return null;
      }
      String result = response.bodyAsString();
      if (responseType == String.class) {
        return result;
      }
      return JSON.parseObject(result, responseType);
    }
  }

  private HttpRequest buildRequest(Object... args) {
    HttpMethod hm = HttpMethod.valueOf(httpMethod.name());
    String realUrl = url;
    if (pathVars != null) {
      for (Map.Entry<String, Integer> entry : pathVars.entrySet()) {
        realUrl = realUrl
            .replace(pathVarSeat(entry.getKey()), safeToString(args[entry.getValue()]));
      }
    }
    HttpRequest request = HttpRequest.url(realUrl).method(hm).followRedirects(true);
    if (contentType != null) {
      request.addHeader(Constants.CONTENT_TYPE, contentType);
    }
    headers.forEach((k, v) -> request.addHeader(k, safeToString(args[v])));
    if (!querys.isEmpty()) {
      Map<String, String> qs = new HashMap<>(2);
      querys.forEach((k, v) -> qs.put(k, safeToString(args[v])));
      if (hm == HttpMethod.GET) {
        request.queryParam(qs);
      } else {
        request.formBody(qs);
      }
    }
    if (bodyIndex > -1) {
      Object body = args[bodyIndex];
      String json;
      if (body != null) {
        json = JSON.toJSONString(body);
      } else {
        json = EMPTY_JSON;
      }
      request.jsonBody(json);
    }
    return request;
  }

  private String safeToString(Object obj) {
    if (obj == null) {
      return Constants.EMPTY;
    }
    return obj.toString();
  }

  private static String concatUrl(String parent, String child) {
    StringBuilder sb = new StringBuilder();
    boolean endWithSlash = false;
    if (parent != null && parent.length() > 0) {
      sb.append(parent);
      if (!parent.endsWith(Constants.SLASH)) {
        sb.append(Constants.SLASH);
      }
      endWithSlash = true;
    }
    if (child != null) {
      if (endWithSlash && child.startsWith(Constants.SLASH)) {
        sb.append(child.substring(1));
      } else {
        sb.append(child);
      }
    }
    return sb.toString();
  }

}
