package cn.gov.enterprise.modules.workflow.domain;

import static org.assertj.core.api.Assertions.*;

import cn.gov.enterprise.modules.workflow.domain.role.eligibility.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class RoleRealtimeEligibilityPersistenceCanonicalTest {
    private static final Instant NOW=Instant.parse("2026-08-20T01:02:03.004Z");
    private static final List<String> CAPS=List.of("ROLE_MEMBERSHIP","USER_STATUS","ORGANIZATION_MEMBERSHIP",
            "DATA_SCOPE","PLATFORM_SOD","BUSINESS_SOD","AUDIT","FEATURE_FLAG","KILL_SWITCH","CANARY");

    @Test void verifiesTwentySevenValidatorsTenCapabilitiesAndThreeHashLayers() {
        var bundle=bundle();
        assertThatCode(()->RoleRealtimeEligibilityPersistenceCanonical.verify(bundle)).doesNotThrowAnyException();
        assertThat(bundle.validators()).hasSize(27); assertThat(bundle.capabilities()).hasSize(10);
        assertThat(RoleRealtimeEligibilityPersistenceCanonical.validatorRoot(bundle.validators())).hasSize(64);
        assertThat(RoleRealtimeEligibilityPersistenceCanonical.capabilityRoot(bundle.capabilities())).hasSize(64);
        assertThat(RoleRealtimeEligibilityPersistenceCanonical.persistenceHash(bundle.header())).hasSize(64);
    }

    @Test void orderIndependentRootsButAnyEvidenceMutationChangesHash() {
        var bundle=bundle(); var reversed=new ArrayList<>(bundle.validators()); java.util.Collections.reverse(reversed);
        assertThat(RoleRealtimeEligibilityPersistenceCanonical.validatorRoot(reversed)).isEqualTo(bundle.header().validatorRootHash());
        var first=reversed.getFirst();
        var changed=new RoleRealtimeEligibilityPersistenceBundle.ValidatorEvidence(first.id(),first.evidenceRowId(),
                first.code(),first.order(),first.status(),"CHANGED",first.evidenceHash(),first.checkedAt());
        reversed.set(0,changed);
        assertThatThrownBy(()->RoleRealtimeEligibilityPersistenceCanonical.verify(new RoleRealtimeEligibilityPersistenceBundle(
                bundle.header(),reversed,bundle.capabilities(),bundle.initialEvent())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    public static RoleRealtimeEligibilityPersistenceBundle bundle() {
        long row=900L; var validators=new ArrayList<RoleRealtimeEligibilityPersistenceBundle.ValidatorEvidence>();
        for(int i=1;i<=27;i++){String code="VALIDATOR_"+i;String hash=RoleRealtimeEligibilityPersistenceCanonical
                .validatorHash(i,code,"PASS","OK",NOW);validators.add(new RoleRealtimeEligibilityPersistenceBundle.ValidatorEvidence(1000+i,row,code,i,"PASS","OK",hash,NOW));}
        var capabilities=new ArrayList<RoleRealtimeEligibilityPersistenceBundle.CapabilityEvidence>();
        for(int i=0;i<CAPS.size();i++){String cap=CAPS.get(i);String hash=RoleRealtimeEligibilityPersistenceCanonical
                .capabilityHash(cap,"VALIDATOR_"+(i+1),"PASS","PASS","PROVIDER_V1","POLICY_V1",NOW,NOW.plusSeconds(120));
            capabilities.add(new RoleRealtimeEligibilityPersistenceBundle.CapabilityEvidence(2000+i,row,cap,"VALIDATOR_"+(i+1),"PASS","PASS","PROVIDER_V1","POLICY_V1",hash,NOW,NOW.plusSeconds(120)));}
        String vr=RoleRealtimeEligibilityPersistenceCanonical.validatorRoot(validators);
        String cr=RoleRealtimeEligibilityPersistenceCanonical.capabilityRoot(capabilities); String hex="a".repeat(64);
        var draft=header(row,vr,cr,hex); String persistence=RoleRealtimeEligibilityPersistenceCanonical.persistenceHash(draft);
        var header=header(row,vr,cr,persistence);
        var eventDraft=new RoleRealtimeEligibilityPersistenceBundle.LifecycleEvent(3000,row,1,"PREPARED",null,"OK",null,hex,NOW,"system","idem-event");
        var event=new RoleRealtimeEligibilityPersistenceBundle.LifecycleEvent(3000,row,1,"PREPARED",null,"OK",null,
                RoleRealtimeEligibilityPersistenceCanonical.eventHash(eventDraft),NOW,"system","idem-event");
        return new RoleRealtimeEligibilityPersistenceBundle(header,validators,capabilities,event);
    }

    private static RoleRealtimeEligibilityPersistenceBundle.Header header(long row,String vr,String cr,String persistence){String h="a".repeat(64);
        return new RoleRealtimeEligibilityPersistenceBundle.Header(row,"E-1","REQ-1","CLAIM-REQ-1","IDEM-1",1,"CORR-1",
                1,2,3,4,5,6,7,8,9,10,11,"ROLE_A","ORG_A",h,h,h,"REV-1","REV-1",h,h,true,NOW,NOW,
                "ELIGIBLE",27,27,10,vr,cr,persistence,"POLICY_V1","CONTRACT_TEST",NOW,NOW,NOW.plusSeconds(60));}
}
