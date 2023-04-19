package org.av360.maverick.graph.feature.applications.config;

import org.av360.maverick.graph.feature.applications.decorators.DelegatingAnonymousIdentifierTransformer;
import org.av360.maverick.graph.feature.applications.decorators.DelegatingExternalIdentifierTransformer;
import org.av360.maverick.graph.services.transformers.replaceIdentifiers.ReplaceAnonymousIdentifiers;
import org.av360.maverick.graph.services.transformers.replaceIdentifiers.ReplaceExternalIdentifiers;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DefaultBeansConfiguration implements BeanPostProcessor {
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        /*if(bean instanceof IdentifierFactory delegate) {
            return new DelegatingIdentifierFactory(delegate);
        }*/

        if(bean instanceof ReplaceExternalIdentifiers delegate) {
            return new DelegatingExternalIdentifierTransformer(delegate);
        }

        else if(bean instanceof ReplaceAnonymousIdentifiers delegate) {
            return new DelegatingAnonymousIdentifierTransformer(delegate);
        }


        return bean;
    }
}


