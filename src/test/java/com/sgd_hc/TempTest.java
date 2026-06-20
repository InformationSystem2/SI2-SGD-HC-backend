package com.sgd_hc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import com.sgd_hc.tenants.service.PlanService;
import com.sgd_hc.tenants.entity.Plan;
@SpringBootTest
public class TempTest {
    @Autowired PlanService planService;
    @Test
    public void test() {
        System.out.println("LIMITS: " + planService.getLimitsForPlan("PRO"));
        Plan p = planService.getPlanEntity("PRO");
        System.out.println("PLAN LIMITS SIZE: " + p.getLimits().size());
    }
}
