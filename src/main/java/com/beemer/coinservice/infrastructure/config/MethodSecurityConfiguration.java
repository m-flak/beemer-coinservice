package com.beemer.coinservice.infrastructure.config;

import com.beemer.coinservice.infrastructure.auth.WalletBalance;
import com.beemer.coinservice.infrastructure.auth.WalletBalanceAuthorizationManager;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.Pointcuts;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Role;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.method.AuthorizationInterceptorsOrder;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableMethodSecurity
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class MethodSecurityConfiguration {

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	Advisor walletBalanceAdvisor(@Lazy WalletBalanceAuthorizationManager manager) {
		AnnotationMatchingPointcut methodPoint = new AnnotationMatchingPointcut(null, WalletBalance.class, true);
		AnnotationMatchingPointcut classPoint = new AnnotationMatchingPointcut(WalletBalance.class, true);
		AuthorizationManagerBeforeMethodInterceptor interceptor = new AuthorizationManagerBeforeMethodInterceptor(
				Pointcuts.union(methodPoint, classPoint), (AuthorizationManager<MethodInvocation>) manager);
		interceptor.setOrder(AuthorizationInterceptorsOrder.PRE_AUTHORIZE.getOrder() + 1);
		return interceptor;
	}
}
