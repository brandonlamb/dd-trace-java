package datadog.trace.instrumentation.springsecurity;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.springsecurity.SpringSecurityDecorator.DECORATOR;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.*;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/* Instrumentation of
 org.springframework.security.authentication
Interface AuthenticationManager
Authentication authenticate(Authentication authentication)
throws AuthenticationException
*/
@AutoService(Instrumenter.class)
public final class AuthenticationManagerInstrumentation extends Instrumenter.Default {

  public AuthenticationManagerInstrumentation() {
    super("spring-security");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(
            safeHasSuperType(
                named("org.springframework.security.authentication.AuthenticationManager")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator", packageName + ".SpringSecurityDecorator"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("authenticate"))
            .and(takesArgument(0, named("org.springframework.security.core.Authentication"))),
        AuthenticateAdvice.class.getName());
  }

  public static class AuthenticateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope StartSpan(
        @Advice.Argument(0) final org.springframework.security.core.Authentication auth) {
      final Scope scope = GlobalTracer.get().buildSpan("authentication").startActive(true);
      Span span = scope.span();
      DECORATOR.afterStart(span);
      span = DECORATOR.setTagsFromAuth(span, auth);
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope,
        @Advice.Return org.springframework.security.core.Authentication auth,
        @Advice.Thrown final Throwable throwable) {
      Span span = scope.span();
      if (throwable != null) {
        Tags.ERROR.set(span, Boolean.TRUE);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      }

      scope.close();
    }
  }
}
