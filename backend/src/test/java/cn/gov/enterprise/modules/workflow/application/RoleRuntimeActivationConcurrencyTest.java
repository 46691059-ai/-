package cn.gov.enterprise.modules.workflow.application;
import static org.assertj.core.api.Assertions.assertThat;
import cn.gov.enterprise.modules.workflow.application.command.RoleRuntimeActivationResult;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
class RoleRuntimeActivationConcurrencyTest{
 @Test void concurrentRequestsProduceOneEventAndControlledReplay() throws Exception{var f=RoleRuntimeActivationTestSupport.fixture();var s=f.service();try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);Callable<RoleRuntimeActivationResult> c=()->{start.await();return s.activate(RoleRuntimeActivationTestSupport.command());};var a=pool.submit(c);var b=pool.submit(c);start.countDown();assertThat(java.util.Set.of(a.get(),b.get())).contains(RoleRuntimeActivationResult.ACTIVATED,RoleRuntimeActivationResult.ALREADY_ACTIVE);assertThat(f.events().countByExactScope(RoleRuntimeActivationTestSupport.SCOPE)).isEqualTo(1);}}
}
