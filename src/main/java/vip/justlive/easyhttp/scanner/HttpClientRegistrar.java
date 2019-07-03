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

package vip.justlive.easyhttp.scanner;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import vip.justlive.easyhttp.annotation.HttpClientScan;
import vip.justlive.oxygen.core.constant.Constants;

/**
 * httpclient registrar
 *
 * @author wubo
 */
public class HttpClientRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientRegistrar.class);

  private ResourceLoader resourceLoader;

  @Override
  public void setResourceLoader(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  @Override
  public void registerBeanDefinitions(AnnotationMetadata metadata,
      BeanDefinitionRegistry registry) {

    AnnotationAttributes attributes = AnnotationAttributes
        .fromMap(metadata.getAnnotationAttributes(HttpClientScan.class.getName()));
    if (attributes != null) {
      String[] basePackages = attributes.getStringArray("value");
      String basePackage = metadata.getClassName()
          .substring(0, metadata.getClassName().lastIndexOf(Constants.DOT));
      if (basePackages == null || basePackages.length == 0) {
        basePackages = new String[]{basePackage};
      } else {
        basePackages = Arrays.copyOf(basePackages, basePackages.length + 1);
        basePackages[basePackages.length - 1] = basePackage;
      }
      HttpClientScanner scanner = new HttpClientScanner(registry);
      scanner.setResourceLoader(resourceLoader);
      scanner.scan(basePackages);
      return;
    }
    LOGGER.warn("not found @HttpClientScan or has no value");
  }

}
