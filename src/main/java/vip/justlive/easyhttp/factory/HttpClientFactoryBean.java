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

import java.lang.reflect.Proxy;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * factory bean of httpclient
 *
 * @param <T> 泛型
 * @author wubo
 */
public class HttpClientFactoryBean<T> implements FactoryBean<T>, EnvironmentAware {

  private final Class<T> clientInterface;
  private Environment environment;

  public HttpClientFactoryBean(Class<T> clientInterface) {
    this.clientInterface = clientInterface;
  }

  @Override
  public T getObject() {
    return clientInterface.cast(Proxy
        .newProxyInstance(clientInterface.getClassLoader(), new Class[]{clientInterface},
            new HttpClientProxy<>(clientInterface, environment)));
  }

  @Override
  public Class<T> getObjectType() {
    return clientInterface;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }
}
