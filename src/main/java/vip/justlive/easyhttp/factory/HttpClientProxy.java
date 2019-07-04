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

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * proxy
 *
 * @param <T> 泛型
 * @author wubo
 */
public class HttpClientProxy<T> implements InvocationHandler {

  private final Class<T> clientInterface;
  private final Environment environment;
  private final Map<Method, HttpClientMethod> cache = new ConcurrentHashMap<>(4);
  private String root;

  HttpClientProxy(Class<T> clientInterface, Environment environment) {
    this.clientInterface = clientInterface;
    this.environment = environment;
    this.init();
  }

  private void init() {
    RequestMapping req = this.clientInterface.getAnnotation(RequestMapping.class);
    if (req != null && req.value().length > 0) {
      root = this.environment.resolvePlaceholders(req.value()[0]);
    }
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

    if (Object.class.equals(method.getDeclaringClass())) {
      return method.invoke(this, args);
    }

    if (isDefaultMethod(method)) {
      return invokeDefaultMethod(proxy, method, args);
    }

    HttpClientMethod methodCache = cache.get(method);
    if (methodCache == null) {
      cache.putIfAbsent(method, new HttpClientMethod(root, method, environment));
      methodCache = cache.get(method);
    }
    return methodCache.execute(args);
  }

  private boolean isDefaultMethod(Method method) {
    return (method.getModifiers() & (Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC))
        == Modifier.PUBLIC && method.getDeclaringClass().isInterface();
  }

  private Object invokeDefaultMethod(Object proxy, Method method, Object[] args) throws Throwable {
    final Constructor<MethodHandles.Lookup> constructor = MethodHandles.Lookup.class
        .getDeclaredConstructor(Class.class, int.class);
    if (!constructor.isAccessible()) {
      constructor.setAccessible(true);
    }
    final Class<?> declaringClass = method.getDeclaringClass();
    return constructor.newInstance(declaringClass,
        MethodHandles.Lookup.PRIVATE | MethodHandles.Lookup.PROTECTED | MethodHandles.Lookup.PACKAGE
            | MethodHandles.Lookup.PUBLIC).unreflectSpecial(method, declaringClass).bindTo(proxy)
        .invokeWithArguments(args);
  }

}
