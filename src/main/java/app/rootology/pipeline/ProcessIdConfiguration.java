package app.rootology.pipeline;

import com.embabel.agent.spi.AgentProcessIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.UUID;

/**
 * Embabel injects its process id generator by interface type (#61): its default one draws a "moby" name
 * ("jolly_mclaren") from a few tens of thousands, with no uniqueness check. We key pending escalations,
 * notifications and history rows by the process id, so a repeated id would fail a save or link a row to
 * another question, possibly of another space. A random UUID never repeats.
 */
@Configuration
class ProcessIdConfiguration {

    @Bean
    @Primary
    AgentProcessIdGenerator agentProcessIdGenerator() {
        return (agent, processOptions) -> UUID.randomUUID().toString();
    }
}
