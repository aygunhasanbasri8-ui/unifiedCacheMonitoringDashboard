package com.nkolay.ucmd;

import com.nkolay.ucmd.service.SnapshotScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class UcmdApplicationTests {

    @MockitoBean
    private SnapshotScheduler snapshotScheduler;
    
	@Test
	void contextLoads() {
	}

}
