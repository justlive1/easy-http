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

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import vip.justlive.easyhttp.annotation.HttpClient;
import vip.justlive.easyhttp.factory.HttpClientFactoryBean;

/**
 * scan interfaces marked with @HttpClient
 *
 * @author wubo
 */
public class HttpClientScanner extends ClassPathBeanDefinitionScanner {
  
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientScanner.class);
  
  HttpClientScanner(BeanDefinitionRegistry registry) {
    super(registry, false);
    addIncludeFilter(new AnnotationTypeFilter(HttpClient.class));
  }
  
  @Override
  protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
    Set<BeanDefinitionHolder> beanDefinitions = super.doScan(basePackages);
    if (beanDefinitions.isEmpty()) {
      LOGGER.warn("No HttpClient was found in '{}' package. Please check your configuration.",
          (Object) basePackages);
    } else {
      processBeanDefinitions(beanDefinitions);
    }
    return beanDefinitions;
  }
  
  @Override
  protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
    return beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata()
        .isIndependent();
  }
  
  private void processBeanDefinitions(Set<BeanDefinitionHolder> beanDefinitions) {
    GenericBeanDefinition definition;
    
    for (BeanDefinitionHolder holder : beanDefinitions) {
      definition = (GenericBeanDefinition) holder.getBeanDefinition();
      
      String beanClassName = definition.getBeanClassName();
      if (beanClassName == null) {
        continue;
      }
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("creating HttpClientFactoryBean with name '{}' and '{}' interface",
            holder.getBeanName(), beanClassName);
      }
      
      definition.getConstructorArgumentValues().addGenericArgumentValue(beanClassName);
      definition.setBeanClass(HttpClientFactoryBean.class);
      definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
      
    }
  }
}
